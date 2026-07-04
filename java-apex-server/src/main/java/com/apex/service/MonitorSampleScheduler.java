package com.apex.service;

import com.apex.entity.MonitorCustomMetric;
import com.apex.entity.MonitorHistory;
import com.apex.entity.MonitorMachine;
import com.apex.entity.MonitorSampleTask;
import com.apex.mapper.MonitorCustomMetricMapper;
import com.apex.mapper.MonitorHistoryMapper;
import com.apex.mapper.MonitorMachineMapper;
import com.apex.mapper.MonitorSampleTaskMapper;
import com.apex.model.CustomMetricVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;

/**
 * 采样任务守护调度器。
 * 每 5 秒扫描任务表，管理 WAITING → RUNNING → FINISHED 状态流转，
 * 为每个 RUNNING 任务维护独立的采集线程，按定制指标动态采集。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MonitorSampleScheduler {

    private final MonitorSampleTaskMapper sampleTaskMapper;
    private final MonitorMachineMapper machineMapper;
    private final MonitorHistoryMapper historyMapper;
    private final MonitorCustomMetricMapper customMetricMapper;
    private final MonitorService monitorService;
    private final ObjectMapper objectMapper;

    @Value("${apex.monitor.scheduler.enabled:true}")
    private boolean schedulerEnabled;

    /**
     * 管理运行中的采集任务。key = taskId, value = ScheduledFuture
     */
    private final ConcurrentHashMap<Integer, ScheduledFuture<?>> runningTasks = new ConcurrentHashMap<>();

    private ScheduledExecutorService executorService;

    @PostConstruct
    public void init() {
        if (!schedulerEnabled) {
            log.info("监控采样调度器已禁用 (apex.monitor.scheduler.enabled=false)");
            return;
        }

        executorService = Executors.newScheduledThreadPool(
                Runtime.getRuntime().availableProcessors(),
                Thread.ofVirtual().name("monitor-sample-", 0).factory());

        // 重启恢复：将已过期的 RUNNING 任务批量改为 FINISHED
        recoverRunningTasks();

        log.info("监控采样调度器已启动");
    }

    @PreDestroy
    public void destroy() {
        if (executorService != null) {
            runningTasks.values().forEach(f -> f.cancel(false));
            runningTasks.clear();
            executorService.shutdownNow();
        }
    }

    /**
     * 应用重启时恢复任务状态。
     */
    private void recoverRunningTasks() {
        LocalDateTime now = LocalDateTime.now();
        List<MonitorSampleTask> runningList = sampleTaskMapper.selectList(
                new LambdaQueryWrapper<MonitorSampleTask>()
                        .eq(MonitorSampleTask::getStatus, "RUNNING"));

        for (MonitorSampleTask task : runningList) {
            if (task.getEndTime().isBefore(now) || task.getEndTime().isEqual(now)) {
                // 已过期 → 标记为 FINISHED
                task.setStatus("FINISHED");
                sampleTaskMapper.updateById(task);
                log.info("重启恢复：任务 {} 已过期，标记为 FINISHED", task.getId());
            } else if (task.getStartTime().isBefore(now)) {
                // 仍在时间范围内 → 重新提交采集线程
                submitCollectionTask(task);
                log.info("重启恢复：任务 {} 仍在采集中，重新提交采集线程", task.getId());
            }
        }
    }

    /**
     * 核心定时扫描 — 每 5 秒执行一次。
     */
    @Scheduled(fixedRate = 5000)
    public void scanTasks() {
        if (!schedulerEnabled) return;

        try {
            LocalDateTime now = LocalDateTime.now();

            // 1. 扫描 WAITING 任务 — 到达开始时间则启动
            List<MonitorSampleTask> waitingTasks = sampleTaskMapper.selectList(
                    new LambdaQueryWrapper<MonitorSampleTask>()
                            .eq(MonitorSampleTask::getStatus, "WAITING"));
            for (MonitorSampleTask task : waitingTasks) {
                if (!task.getStartTime().isAfter(now)) {
                    task.setStatus("RUNNING");
                    sampleTaskMapper.updateById(task);
                    submitCollectionTask(task);
                    log.info("任务 {} ({}) 已启动采集", task.getId(), task.getTaskName());
                }
            }

            // 2. 扫描 RUNNING 任务 — 检查是否到期
            List<MonitorSampleTask> runningList = sampleTaskMapper.selectList(
                    new LambdaQueryWrapper<MonitorSampleTask>()
                            .eq(MonitorSampleTask::getStatus, "RUNNING"));
            for (MonitorSampleTask task : runningList) {
                if (!task.getEndTime().isAfter(now)) {
                    // 到期 → 取消线程，标记 FINISHED
                    ScheduledFuture<?> future = runningTasks.remove(task.getId());
                    if (future != null) {
                        future.cancel(false);
                    }
                    task.setStatus("FINISHED");
                    sampleTaskMapper.updateById(task);
                    log.info("任务 {} ({}) 已结束采集", task.getId(), task.getTaskName());
                }
            }
        } catch (Exception e) {
            log.error("采样调度扫描异常", e);
        }
    }

    /**
     * 为指定任务提交独立的周期性采集线程。
     */
    private void submitCollectionTask(MonitorSampleTask task) {
        // 防止重复提交
        if (runningTasks.containsKey(task.getId())) {
            return;
        }

        ScheduledFuture<?> future = executorService.scheduleWithFixedDelay(() -> {
            try {
                collectSample(task);
            } catch (Exception e) {
                log.error("任务 {} 采集异常", task.getId(), e);
            }
        }, 0, task.getCollectInterval(), TimeUnit.SECONDS);

        runningTasks.put(task.getId(), future);
    }

    /**
     * 执行一次采样 — 按任务关联的定制指标 metricKey 动态采集并写入 history。
     * 支持系统内置虚拟指标（负数ID）和数据库定制指标（正数ID）混合采集。
     */
    private void collectSample(MonitorSampleTask task) {
        MonitorMachine machine = machineMapper.selectById(task.getMachineId());
        if (machine == null) {
            log.warn("任务 {} 关联的机器 {} 不存在，跳过采集", task.getId(), task.getMachineId());
            return;
        }

        // 解析任务关联的指标ID列表，拆分为系统内置（负数）和DB定制（正数）
        List<Integer> allMetricIds = parseMetricIds(task.getMetricIds());
        if (allMetricIds.isEmpty()) {
            log.warn("任务 {} 未关联任何定制指标，跳过采集", task.getId());
            return;
        }

        List<Integer> positiveIds = allMetricIds.stream().filter(id -> id > 0).toList();
        List<Integer> negativeIds = allMetricIds.stream().filter(MonitorService::isSysMetricId).toList();

        // 查询DB定制指标记录
        List<MonitorCustomMetric> dbMetrics = positiveIds.isEmpty()
                ? List.of()
                : customMetricMapper.selectBatchIds(positiveIds);

        try {
            String metricsText = monitorService.fetchMetrics(machine);
            String osType = machine.getOsType();

            // 解析全量指标
            List<MonitorService.ParsedMetric> allMetrics = monitorService.parseAllMetrics(metricsText);

            // 按 metricKey 构建快速查找表
            Map<String, String> valueMap = new HashMap<>();
            for (MonitorService.ParsedMetric pm : allMetrics) {
                valueMap.put(pm.metricKey(), pm.value());
            }

            // ===== 1. 计算系统内置指标 + 网络收发 =====
            double cpuUsage = monitorService.parseCpuUsage(metricsText, osType);
            double memUsage = monitorService.parseMemUsage(metricsText, osType);
            double diskUsage = monitorService.parseDiskUsage(metricsText, osType);
            long[] netBytes = monitorService.parseNetworkBytes(metricsText, osType);
            long uptime = monitorService.parseUptimeSeconds(metricsText, osType);

            Map<String, Double> values = new LinkedHashMap<>();

            // 按需填充系统内置指标
            if (negativeIds.contains(-1)) values.put("__sys_cpu_usage", cpuUsage);
            if (negativeIds.contains(-2)) values.put("__sys_mem_usage", memUsage);
            if (negativeIds.contains(-3)) values.put("__sys_disk_usage", diskUsage);
            if (negativeIds.contains(-4)) values.put("__sys_net_rx_bytes", (double) netBytes[0]);
            if (negativeIds.contains(-5)) values.put("__sys_net_tx_bytes", (double) netBytes[1]);
            if (negativeIds.contains(-6)) values.put("__sys_uptime_seconds", (double) uptime);

            // ===== 2. 匹配DB定制指标值 =====
            for (MonitorCustomMetric cm : dbMetrics) {
                String raw = valueMap.get(cm.getMetricKey());
                double val = -1;
                if (raw != null) {
                    try {
                        val = Double.parseDouble(raw);
                    } catch (NumberFormatException e) {
                        val = -1;
                    }
                }
                values.put(cm.getMetricKey(), val);
            }

            MonitorHistory history = new MonitorHistory();
            history.setTaskId(task.getId());
            history.setRecordTime(LocalDateTime.now());
            history.setMetricValues(objectMapper.writeValueAsString(values));
            historyMapper.insert(history);

            log.debug("任务 {} 采集完成: {} 个指标 (DB: {}, 系统: {})",
                    task.getId(), values.size(), dbMetrics.size(), negativeIds.size());
        } catch (Exception e) {
            log.warn("任务 {} 采集 Exporter 失败: {}", task.getId(), e.getMessage());

            // 写入 -1 表示采集失败
            Map<String, Double> failValues = new LinkedHashMap<>();
            for (Integer nid : negativeIds) {
                CustomMetricVO sys = MonitorService.getSysMetric(nid);
                if (sys != null) failValues.put(sys.metricKey(), -1.0);
            }
            for (MonitorCustomMetric cm : dbMetrics) {
                failValues.put(cm.getMetricKey(), -1.0);
            }
            MonitorHistory history = new MonitorHistory();
            history.setTaskId(task.getId());
            try {
                history.setMetricValues(objectMapper.writeValueAsString(failValues));
            } catch (Exception ex) {
                log.error("序列化失败值异常", ex);
            }
            history.setRecordTime(LocalDateTime.now());
            historyMapper.insert(history);
        }
    }

    /**
     * 反序列化 metricIds JSON 字符串。
     */
    private List<Integer> parseMetricIds(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<Integer>>() {});
        } catch (Exception e) {
            log.warn("解析 metricIds JSON 失败: {}", json, e);
            return List.of();
        }
    }
}

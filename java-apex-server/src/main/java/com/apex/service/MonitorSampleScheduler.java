package com.apex.service;

import com.apex.entity.MonitorCustomMetric;
import com.apex.entity.MonitorHistory;
import com.apex.entity.MonitorMachine;
import com.apex.entity.MonitorSampleTask;
import com.apex.mapper.MonitorCustomMetricMapper;
import com.apex.mapper.MonitorHistoryMapper;
import com.apex.mapper.MonitorMachineMapper;
import com.apex.mapper.MonitorSampleTaskMapper;
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
     */
    private void collectSample(MonitorSampleTask task) {
        MonitorMachine machine = machineMapper.selectById(task.getMachineId());
        if (machine == null) {
            log.warn("任务 {} 关联的机器 {} 不存在，跳过采集", task.getId(), task.getMachineId());
            return;
        }

        // 解析任务关联的指标ID列表
        List<Integer> metricIds = parseMetricIds(task.getMetricIds());
        if (metricIds.isEmpty()) {
            log.warn("任务 {} 未关联任何定制指标，跳过采集", task.getId());
            return;
        }

        // 查询对应的定制指标记录
        List<MonitorCustomMetric> metrics = customMetricMapper.selectBatchIds(metricIds);
        if (metrics.isEmpty()) {
            log.warn("任务 {} 关联的定制指标均已删除，跳过采集", task.getId());
            return;
        }

        try {
            String metricsText = monitorService.fetchMetrics(machine);

            // 解析全量指标
            List<MonitorService.ParsedMetric> allMetrics = monitorService.parseAllMetrics(metricsText);

            // 按 metricKey 构建快速查找表
            Map<String, String> valueMap = new HashMap<>();
            for (MonitorService.ParsedMetric pm : allMetrics) {
                valueMap.put(pm.metricKey(), pm.value());
            }

            // 按定制指标列表匹配值
            Map<String, Double> values = new LinkedHashMap<>();
            for (MonitorCustomMetric cm : metrics) {
                String raw = valueMap.get(cm.getMetricKey());
                double val = -1; // -1 表示未找到
                if (raw != null) {
                    try {
                        val = Double.parseDouble(raw);
                    } catch (NumberFormatException e) {
                        // 非数值型指标，用 -1 标记
                        val = -1;
                    }
                }
                values.put(cm.getMetricKey(), val);
            }

            // 同时写入旧列兼容
            Float cpu = null, mem = null, disk = null;
            if (values.containsKey("cpu_usage")) cpu = values.get("cpu_usage").floatValue();
            if (values.containsKey("mem_usage")) mem = values.get("mem_usage").floatValue();
            if (values.containsKey("disk_usage")) disk = values.get("disk_usage").floatValue();

            MonitorHistory history = new MonitorHistory();
            history.setTaskId(task.getId());
            history.setCpuUsage(cpu);
            history.setMemUsage(mem);
            history.setDiskUsage(disk);
            history.setMetricValues(objectMapper.writeValueAsString(values));
            history.setRecordTime(LocalDateTime.now());
            historyMapper.insert(history);

            log.debug("任务 {} 采集完成: {} 个指标", task.getId(), values.size());
        } catch (Exception e) {
            log.warn("任务 {} 采集 Exporter 失败: {}", task.getId(), e.getMessage());
            // 写入 -1 表示采集失败
            MonitorHistory history = new MonitorHistory();
            history.setTaskId(task.getId());
            history.setCpuUsage(-1f);
            history.setMemUsage(-1f);
            history.setDiskUsage(-1f);
            Map<String, Double> failValues = new LinkedHashMap<>();
            for (MonitorCustomMetric cm : metrics) {
                failValues.put(cm.getMetricKey(), -1.0);
            }
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

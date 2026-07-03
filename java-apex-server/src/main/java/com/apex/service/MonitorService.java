package com.apex.service;

import com.apex.config.MetricDictionary;
import com.apex.entity.*;
import com.apex.mapper.*;
import com.apex.model.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 监控核心业务服务。
 * 负责机器 CRUD、Exporter 全量指标解析、实时查询、指标定制、采样任务管理等。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MonitorService {

    private final MonitorMachineMapper machineMapper;
    private final MonitorCustomMetricMapper customMetricMapper;
    private final MonitorSampleTaskMapper sampleTaskMapper;
    private final MonitorHistoryMapper historyMapper;

    private final ObjectMapper objectMapper;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    // =============================================
    // 机器管理
    // =============================================

    public List<MonitorMachine> listMachines() {
        return machineMapper.selectList(new LambdaQueryWrapper<MonitorMachine>()
                .orderByAsc(MonitorMachine::getId));
    }

    @Transactional
    public MonitorMachine addMachine(MonitorMachineDTO dto) {
        MonitorMachine machine = new MonitorMachine();
        machine.setMachineName(dto.getMachineName());
        machine.setIp(dto.getIp());
        machine.setOsType(dto.getOsType().toUpperCase());
        machine.setExporterPort(dto.getExporterPort());
        machine.setRefreshInterval(dto.getRefreshInterval());
        machine.setIsEnabled(true);
        machineMapper.insert(machine);
        return machine;
    }

    @Transactional
    public void updateMachine(MonitorMachineDTO dto) {
        MonitorMachine machine = machineMapper.selectById(dto.getId());
        if (machine == null) {
            throw new com.apex.common.BusinessException(404, "机器不存在");
        }
        machine.setMachineName(dto.getMachineName());
        machine.setIp(dto.getIp());
        machine.setOsType(dto.getOsType().toUpperCase());
        machine.setExporterPort(dto.getExporterPort());
        machine.setRefreshInterval(dto.getRefreshInterval());
        machineMapper.updateById(machine);
    }

    @Transactional
    public void deleteMachine(Integer id) {
        // 级联删除关联的定制指标
        customMetricMapper.delete(new LambdaQueryWrapper<MonitorCustomMetric>()
                .eq(MonitorCustomMetric::getMachineId, id));
        // 级联删除关联的采样任务及其历史数据
        List<MonitorSampleTask> tasks = sampleTaskMapper.selectList(
                new LambdaQueryWrapper<MonitorSampleTask>()
                        .eq(MonitorSampleTask::getMachineId, id));
        for (MonitorSampleTask task : tasks) {
            historyMapper.delete(new LambdaQueryWrapper<MonitorHistory>()
                    .eq(MonitorHistory::getTaskId, task.getId()));
        }
        sampleTaskMapper.delete(new LambdaQueryWrapper<MonitorSampleTask>()
                .eq(MonitorSampleTask::getMachineId, id));
        machineMapper.deleteById(id);
    }

    @Transactional
    public void toggleMachine(Integer id) {
        MonitorMachine machine = machineMapper.selectById(id);
        if (machine == null) {
            throw new com.apex.common.BusinessException(404, "机器不存在");
        }
        machine.setIsEnabled(!machine.getIsEnabled());
        machineMapper.updateById(machine);
    }

    // =============================================
    // Exporter HTTP 请求
    // =============================================

    /**
     * 拉取 Exporter 返回的 Prometheus Text 格式指标文本。
     */
    public String fetchMetrics(MonitorMachine machine) throws IOException, InterruptedException {
        String url = String.format("http://%s:%d/metrics", machine.getIp(), machine.getExporterPort());
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("Exporter returned HTTP " + response.statusCode());
        }
        return response.body();
    }

    // =============================================
    // 全量指标解析 + 中文翻译 + 分类
    // =============================================

    /**
     * 将原始 Prometheus Text 逐行解析为结构化指标列表。
     * 跳过 HELP/TYPE 注释行、空行、以 # 开头的行。
     */
    public List<ParsedMetric> parseAllMetrics(String metricsText) {
        List<ParsedMetric> result = new ArrayList<>();
        // Prometheus 数据行格式: metric_name{labels} value [timestamp]
        Pattern dataLinePattern = Pattern.compile("^(\\w+)(\\{[^}]*\\})?\\s+([\\d.eE+\\-]+(?:\\s+\\d+)?)$");
        String[] lines = metricsText.split("\\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
            Matcher m = dataLinePattern.matcher(trimmed);
            if (m.matches()) {
                String metricName = m.group(1);
                String labels = m.group(2) != null ? m.group(2) : "";
                String value = m.group(3).split("\\s+")[0]; // 去掉可能的时间戳
                String metricKey = labels.isEmpty() ? metricName : metricName + labels;
                result.add(new ParsedMetric(metricKey, metricName, labels, value));
            }
        }
        return result;
    }

    /**
     * 根据指标名查找中文翻译。委托给 MetricDictionary。
     */
    public String getChineseName(String metricName) {
        return MetricDictionary.getChineseName(metricName);
    }

    /**
     * 根据指标名前缀推断分类。委托给 MetricDictionary。
     */
    public String inferCategory(String metricName) {
        return MetricDictionary.inferCategory(metricName);
    }

    /**
     * 分类名中文翻译。委托给 MetricDictionary。
     */
    public String getCategoryChineseName(String categoryKey) {
        return MetricDictionary.getCategoryChineseName(categoryKey);
    }

    // =============================================
    // 解析的数据结构
    // =============================================

    /**
     * 一行 Parsed 指标。
     */
    public record ParsedMetric(
            String metricKey,
            String metricName,
            String labels,
            String value
    ) {}

    // =============================================
    // 全量指标 API
    // =============================================

    /**
     * 获取单台机器的全量 Exporter 指标（含分类、翻译、定制匹配、丢失检测）。
     */
    public MonitorFullMetricsVO getFullMetrics(Integer machineId) {
        MonitorMachine machine = machineMapper.selectById(machineId);
        if (machine == null) {
            throw new com.apex.common.BusinessException(404, "机器不存在");
        }

        // 查询该机器已定制的指标（无论 Exporter 是否可达都需要）
        List<MonitorCustomMetric> customized = customMetricMapper.selectList(
                new LambdaQueryWrapper<MonitorCustomMetric>()
                        .eq(MonitorCustomMetric::getMachineId, machineId)
                        .eq(MonitorCustomMetric::getIsVisible, true));

        try {
            String metricsText = fetchMetrics(machine);
            List<ParsedMetric> allMetrics = parseAllMetrics(metricsText);

            // 构建 metricKey → 定制记录 的快速查找表
            Map<String, MonitorCustomMetric> customizedMap = customized.stream()
                    .collect(Collectors.toMap(
                            MonitorCustomMetric::getMetricKey,
                            cm -> cm,
                            (a, b) -> a));

            // 标记哪些已定制指标在本次返回中匹配到了
            Set<String> matchedKeys = new HashSet<>();

            // 按分类组织指标
            Map<String, List<MonitorMetricItem>> categorizedMap = new LinkedHashMap<>();

            for (ParsedMetric pm : allMetrics) {
                String category = inferCategory(pm.metricName());
                boolean cust = customizedMap.containsKey(pm.metricKey());
                if (cust) {
                    matchedKeys.add(pm.metricKey());
                }
                MonitorMetricItem item = new MonitorMetricItem(
                        pm.metricKey(),
                        pm.metricName(),
                        pm.labels(),
                        getChineseName(pm.metricName()),
                        pm.value(),
                        "",  // description 暂空
                        cust,
                        cust ? customizedMap.get(pm.metricKey()).getId() : null
                );
                categorizedMap.computeIfAbsent(category, k -> new ArrayList<>()).add(item);
            }

            // 构建分类列表
            List<MonitorMetricCategory> categories = new ArrayList<>();
            List<String> categoryOrder = MetricDictionary.MetricCategory.orderedKeys();
            for (String cat : categoryOrder) {
                if (categorizedMap.containsKey(cat)) {
                    categories.add(new MonitorMetricCategory(cat, getCategoryChineseName(cat), categorizedMap.get(cat)));
                }
            }

            // 找出已定制但未匹配到的（丢失指标）
            List<MonitorMetricItem> orphaned = new ArrayList<>();
            for (MonitorCustomMetric cm : customized) {
                if (!matchedKeys.contains(cm.getMetricKey())) {
                    orphaned.add(new MonitorMetricItem(
                            cm.getMetricKey(),
                            cm.getMetricName(),
                            "",  // 丢失指标无 labels 信息
                            cm.getDisplayName(),
                            "—",
                            "该指标在本次 Exporter 返回中缺失",
                            true,
                            cm.getId()
                    ));
                }
            }

            return new MonitorFullMetricsVO(machineId, true, null, categories, orphaned);

        } catch (Exception e) {
            log.warn("无法连接 Exporter {}:{} — {}", machine.getIp(), machine.getExporterPort(), e.getMessage());

            // Exporter 不可达时全部标记为丢失
            List<MonitorMetricItem> orphaned = customized.stream()
                    .map(cm -> new MonitorMetricItem(
                            cm.getMetricKey(),
                            cm.getMetricName(),
                            "",  // 丢失指标无 labels 信息
                            cm.getDisplayName(),
                            "—",
                            "无法连接 Exporter",
                            true,
                            cm.getId()))
                    .collect(Collectors.toList());

            return new MonitorFullMetricsVO(machineId, false,
                    "无法连接 " + machine.getIp() + ":" + machine.getExporterPort() + " — " + e.getMessage(),
                    List.of(), orphaned);
        }
    }

    // =============================================
    // 指标定制管理
    // =============================================

    /**
     * 查询某台机器已定制的指标列表。
     */
    public List<CustomMetricVO> getCustomizedMetrics(Integer machineId) {
        List<MonitorCustomMetric> list = customMetricMapper.selectList(
                new LambdaQueryWrapper<MonitorCustomMetric>()
                        .eq(MonitorCustomMetric::getMachineId, machineId)
                        .eq(MonitorCustomMetric::getIsVisible, true));
        return list.stream()
                .map(cm -> new CustomMetricVO(
                        cm.getId(),
                        cm.getMachineId(),
                        cm.getMetricKey(),
                        cm.getMetricName(),
                        cm.getDisplayName(),
                        cm.getCategory(),
                        cm.getIsVisible(),
                        cm.getCreateTime() != null ? cm.getCreateTime().toString() : null))
                .collect(Collectors.toList());
    }

    /**
     * 定制一个指标。
     */
    @Transactional
    public void addCustomMetric(Integer machineId, MonitorCustomMetricDTO dto) {
        // 检查是否已存在相同 key
        MonitorCustomMetric existing = customMetricMapper.selectOne(
                new LambdaQueryWrapper<MonitorCustomMetric>()
                        .eq(MonitorCustomMetric::getMachineId, machineId)
                        .eq(MonitorCustomMetric::getMetricKey, dto.getMetricKey()));
        if (existing != null) {
            throw new com.apex.common.BusinessException(400, "该指标已定制");
        }

        MonitorCustomMetric metric = new MonitorCustomMetric();
        metric.setMachineId(machineId);
        metric.setMetricKey(dto.getMetricKey());
        metric.setMetricName(dto.getMetricName());
        metric.setDisplayName(dto.getDisplayName());
        metric.setCategory(dto.getCategory() != null && !dto.getCategory().isBlank()
                ? dto.getCategory()
                : inferCategory(dto.getMetricName()));
        metric.setIsVisible(true);
        customMetricMapper.insert(metric);
    }

    /**
     * 取消定制一个指标（物理删除）。
     */
    @Transactional
    public void removeCustomMetric(Integer machineId, Integer metricId) {
        customMetricMapper.delete(new LambdaQueryWrapper<MonitorCustomMetric>()
                .eq(MonitorCustomMetric::getMachineId, machineId)
                .eq(MonitorCustomMetric::getId, metricId));
    }

    // =============================================
    // CPU / 内存 / 磁盘 使用率解析（保留原逻辑）
    // =============================================

    public double parseCpuUsage(String metricsText, String osType) {
        if ("LINUX".equalsIgnoreCase(osType)) {
            return parseLinuxCpuUsage(metricsText);
        } else {
            return parseWindowsCpuUsage(metricsText);
        }
    }

    private double parseLinuxCpuUsage(String text) {
        Pattern cpuLinePattern = Pattern.compile("node_cpu_seconds_total\\{cpu=\"(\\d+)\",mode=\"(\\w+)\"}\\s+([\\d.e+-]+)");
        Matcher m = cpuLinePattern.matcher(text);

        Map<String, Double> totalsByCpu = new HashMap<>();
        Map<String, Double> idleByCpu = new HashMap<>();
        while (m.find()) {
            String cpu = m.group(1);
            String mode = m.group(2);
            double val = Double.parseDouble(m.group(3));
            if ("idle".equals(mode)) {
                idleByCpu.put(cpu, val);
            }
            totalsByCpu.merge(cpu, val, Double::sum);
        }

        double totalIdle = idleByCpu.values().stream().mapToDouble(Double::doubleValue).sum();
        double totalAll = totalsByCpu.values().stream().mapToDouble(Double::doubleValue).sum();

        if (totalAll == 0) return 0;
        return Math.max(0, Math.min(100, (1 - totalIdle / totalAll) * 100));
    }

    private double parseWindowsCpuUsage(String text) {
        Pattern idlePattern = Pattern.compile("windows_cpu_time_total\\{[^}]*mode=\"idle\"[^}]*}\\s+([\\d.e+-]+)");
        Pattern allPattern = Pattern.compile("windows_cpu_time_total\\{[^}]*mode=\"(?!idle)[^\"]*\"[^}]*}\\s+([\\d.e+-]+)");

        double idleSum = 0;
        double nonIdleSum = 0;

        Matcher idleM = idlePattern.matcher(text);
        while (idleM.find()) {
            idleSum += Double.parseDouble(idleM.group(1));
        }

        Matcher allM = allPattern.matcher(text);
        while (allM.find()) {
            nonIdleSum += Double.parseDouble(allM.group(1));
        }

        double total = idleSum + nonIdleSum;
        if (total == 0) return 0;
        return Math.max(0, Math.min(100, (nonIdleSum / total) * 100));
    }

    public double parseMemUsage(String metricsText, String osType) {
        if ("LINUX".equalsIgnoreCase(osType)) {
            return parseLinuxMemUsage(metricsText);
        } else {
            return parseWindowsMemUsage(metricsText);
        }
    }

    private double parseLinuxMemUsage(String text) {
        double total = extractMetricValue(text, "node_memory_MemTotal_bytes");
        double available = extractMetricValue(text, "node_memory_MemAvailable_bytes");
        if (total == 0) return 0;
        return Math.max(0, Math.min(100, (total - available) / total * 100));
    }

    private double parseWindowsMemUsage(String text) {
        double total = extractMetricValue(text, "windows_memory_physical_total_bytes");
        double free = extractMetricValue(text, "windows_memory_physical_free_bytes");
        if (total == 0) return 0;
        return Math.max(0, Math.min(100, (total - free) / total * 100));
    }

    public double parseDiskUsage(String metricsText, String osType) {
        if ("LINUX".equalsIgnoreCase(osType)) {
            return parseLinuxDiskUsage(metricsText);
        } else {
            return parseWindowsDiskUsage(metricsText);
        }
    }

    private double parseLinuxDiskUsage(String text) {
        double size = extractLabeledMetricValue(text, "node_filesystem_size_bytes", "mountpoint", "/");
        double free = extractLabeledMetricValue(text, "node_filesystem_free_bytes", "mountpoint", "/");
        if (size == 0) return 0;
        return Math.max(0, Math.min(100, (size - free) / size * 100));
    }

    private double parseWindowsDiskUsage(String text) {
        double size = extractLabeledMetricValue(text, "windows_logical_disk_size_bytes", "volume", "C:");
        double free = extractLabeledMetricValue(text, "windows_logical_disk_free_bytes", "volume", "C:");
        if (size == 0) return 0;
        return Math.max(0, Math.min(100, (size - free) / size * 100));
    }

    // =============================================
    // 网络 / 运行时间 / 负载
    // =============================================

    public long[] parseNetworkBytes(String metricsText, String osType) {
        if ("LINUX".equalsIgnoreCase(osType)) {
            return parseLinuxNetworkBytes(metricsText);
        } else {
            return parseWindowsNetworkBytes(metricsText);
        }
    }

    private long[] parseLinuxNetworkBytes(String text) {
        String primaryDevice = "eth0";
        Pattern ifacePattern = Pattern.compile(
                "node_network_info\\{.*device=\"(eth\\d+|ens\\d+|enp\\d+s\\d+)\".*operstate=\"up\".*}");
        Matcher ifaceM = ifacePattern.matcher(text);
        if (ifaceM.find()) {
            primaryDevice = ifaceM.group(1);
        }

        long rx = (long) extractLabeledMetricValue(text, "node_network_receive_bytes_total", "device", primaryDevice);
        long tx = (long) extractLabeledMetricValue(text, "node_network_transmit_bytes_total", "device", primaryDevice);
        return new long[]{rx, tx};
    }

    private long[] parseWindowsNetworkBytes(String text) {
        Pattern rxPattern = Pattern.compile(
                Pattern.quote("windows_net_bytes_received_total") + "\\{[^}]*}\\s+([\\d.e+-]+)");
        Pattern txPattern = Pattern.compile(
                Pattern.quote("windows_net_bytes_sent_total") + "\\{[^}]*}\\s+([\\d.e+-]+)");
        long rx = 0, tx = 0;
        Matcher m = rxPattern.matcher(text);
        if (m.find()) rx = (long) Double.parseDouble(m.group(1));
        m = txPattern.matcher(text);
        if (m.find()) tx = (long) Double.parseDouble(m.group(1));
        return new long[]{rx, tx};
    }

    public long parseUptimeSeconds(String metricsText, String osType) {
        if ("LINUX".equalsIgnoreCase(osType)) {
            double bootTime = extractMetricValue(metricsText, "node_boot_time_seconds");
            if (bootTime == 0) return 0;
            return (long) (System.currentTimeMillis() / 1000.0 - bootTime);
        } else {
            double bootTimestamp = extractMetricValue(metricsText, "windows_system_boot_time_timestamp");
            if (bootTimestamp == 0) return 0;
            return (long) (System.currentTimeMillis() / 1000.0 - bootTimestamp);
        }
    }

    public double[] parseLoadAvg(String metricsText, String osType) {
        if ("LINUX".equalsIgnoreCase(osType)) {
            double l1 = extractMetricValue(metricsText, "node_load1");
            double l5 = extractMetricValue(metricsText, "node_load5");
            double l15 = extractMetricValue(metricsText, "node_load15");
            return new double[]{l1, l5, l15};
        }
        return new double[]{0, 0, 0};
    }

    // =============================================
    // 实时指标查询（容量监控卡片用）
    // =============================================

    /**
     * 获取单台机器的实时指标（CPU/内存/磁盘 + 已定制指标及其值）。
     */
    public MonitorRealtimeVO getRealtimeMetrics(Integer machineId) {
        MonitorMachine machine = machineMapper.selectById(machineId);
        if (machine == null) {
            throw new com.apex.common.BusinessException(404, "机器不存在");
        }

        // 查询已定制的指标（无论 Exporter 是否可达都需要）
        List<MonitorCustomMetric> customizedMetrics = customMetricMapper.selectList(
                new LambdaQueryWrapper<MonitorCustomMetric>()
                        .eq(MonitorCustomMetric::getMachineId, machineId)
                        .eq(MonitorCustomMetric::getIsVisible, true));

        try {
            String metricsText = fetchMetrics(machine);
            String osType = machine.getOsType();

            double cpu = parseCpuUsage(metricsText, osType);
            double mem = parseMemUsage(metricsText, osType);
            double disk = parseDiskUsage(metricsText, osType);
            long[] net = parseNetworkBytes(metricsText, osType);
            long uptime = parseUptimeSeconds(metricsText, osType);
            double[] load = parseLoadAvg(metricsText, osType);

            // 解析全量指标，匹配定制项
            List<ParsedMetric> allMetrics = parseAllMetrics(metricsText);
            Set<String> actualKeys = allMetrics.stream()
                    .map(ParsedMetric::metricKey)
                    .collect(Collectors.toSet());

            List<PortStatusVO> portStatusList = new ArrayList<>();
            for (MonitorCustomMetric cm : customizedMetrics) {
                boolean matched = actualKeys.contains(cm.getMetricKey());
                String currentValue = "—";
                if (matched) {
                    currentValue = allMetrics.stream()
                            .filter(pm -> pm.metricKey().equals(cm.getMetricKey()))
                            .findFirst()
                            .map(ParsedMetric::value)
                            .orElse("—");
                }
                portStatusList.add(new PortStatusVO(
                        cm.getId(),
                        cm.getDisplayName(),
                        currentValue,
                        true,
                        matched));
            }

            return new MonitorRealtimeVO(machineId, true, null,
                    cpu, mem, disk,
                    net[0], net[1], uptime,
                    load[0], load[1], load[2],
                    portStatusList);

        } catch (Exception e) {
            log.warn("无法连接 Exporter {}:{} — {}", machine.getIp(), machine.getExporterPort(), e.getMessage());

            List<PortStatusVO> portStatusList = customizedMetrics.stream()
                    .map(cm -> new PortStatusVO(cm.getId(), cm.getDisplayName(), "已丢失", true, false))
                    .collect(Collectors.toList());

            return new MonitorRealtimeVO(machineId, false,
                    "无法连接 " + machine.getIp() + ":" + machine.getExporterPort() + " — " + e.getMessage(),
                    0, 0, 0,
                    0, 0, 0,
                    0, 0, 0,
                    portStatusList);
        }
    }

    // =============================================
    // 采样任务管理
    // =============================================

    public List<SampleTaskVO> listSampleTasks() {
        List<MonitorSampleTask> tasks = sampleTaskMapper.selectList(
                new LambdaQueryWrapper<MonitorSampleTask>()
                        .orderByDesc(MonitorSampleTask::getCreateTime));

        // 一次性加载所有机器和定制指标
        Map<Integer, String> machineNameMap = machineMapper.selectList(null).stream()
                .collect(Collectors.toMap(MonitorMachine::getId, MonitorMachine::getMachineName));

        // 收集所有任务涉及的 metricIds，批量查询
        Set<Integer> allMetricIds = new HashSet<>();
        for (MonitorSampleTask t : tasks) {
            List<Integer> ids = parseMetricIds(t.getMetricIds());
            allMetricIds.addAll(ids);
        }
        Map<Integer, MonitorCustomMetric> metricMap = Collections.emptyMap();
        if (!allMetricIds.isEmpty()) {
            metricMap = customMetricMapper.selectBatchIds(allMetricIds).stream()
                    .collect(Collectors.toMap(MonitorCustomMetric::getId, cm -> cm));
        }

        final Map<Integer, MonitorCustomMetric> finalMetricMap = metricMap;

        return tasks.stream()
                .map(t -> {
                    List<Integer> ids = parseMetricIds(t.getMetricIds());
                    List<MetricInfo> infos = ids.stream()
                            .filter(finalMetricMap::containsKey)
                            .map(id -> {
                                MonitorCustomMetric cm = finalMetricMap.get(id);
                                return new MetricInfo(cm.getId(), cm.getMetricKey(),
                                        cm.getDisplayName(), cm.getCategory());
                            })
                            .collect(Collectors.toList());
                    return new SampleTaskVO(
                            t.getId(),
                            t.getMachineId(),
                            machineNameMap.getOrDefault(t.getMachineId(), "未知"),
                            t.getTaskName(),
                            t.getStartTime(),
                            t.getEndTime(),
                            t.getCollectInterval(),
                            t.getStatus(),
                            ids,
                            infos);
                })
                .collect(Collectors.toList());
    }

    @Transactional
    public void createSampleTask(MonitorSampleTaskDTO dto) {
        if (dto.getEndTime().isBefore(dto.getStartTime())) {
            throw new com.apex.common.BusinessException(400, "结束时间不能早于开始时间");
        }

        MonitorMachine machine = machineMapper.selectById(dto.getMachineId());
        if (machine == null) {
            throw new com.apex.common.BusinessException(404, "机器不存在");
        }

        // 校验所有 metricId 都属于该机器且可见
        List<MonitorCustomMetric> metrics = customMetricMapper.selectList(
                new LambdaQueryWrapper<MonitorCustomMetric>()
                        .eq(MonitorCustomMetric::getMachineId, dto.getMachineId())
                        .eq(MonitorCustomMetric::getIsVisible, true)
                        .in(MonitorCustomMetric::getId, dto.getMetricIds()));
        if (metrics.size() != dto.getMetricIds().size()) {
            throw new com.apex.common.BusinessException(400, "部分指标无效或不属于该机器");
        }

        MonitorSampleTask task = new MonitorSampleTask();
        task.setMachineId(dto.getMachineId());
        task.setTaskName(dto.getTaskName());
        task.setStartTime(dto.getStartTime());
        task.setEndTime(dto.getEndTime());
        task.setCollectInterval(dto.getCollectInterval());
        task.setStatus("WAITING");
        task.setMetricIds(serializeMetricIds(dto.getMetricIds()));
        sampleTaskMapper.insert(task);
    }

    @Transactional
    public void deleteSampleTask(Integer id) {
        historyMapper.delete(new LambdaQueryWrapper<MonitorHistory>()
                .eq(MonitorHistory::getTaskId, id));
        sampleTaskMapper.deleteById(id);
    }

    public List<MonitorHistoryVO> getTaskHistory(Integer taskId) {
        List<MonitorHistory> historyList = historyMapper.selectList(
                new LambdaQueryWrapper<MonitorHistory>()
                        .eq(MonitorHistory::getTaskId, taskId)
                        .orderByAsc(MonitorHistory::getRecordTime));

        return historyList.stream()
                .map(h -> new MonitorHistoryVO(
                        h.getId(),
                        parseValues(h),
                        h.getRecordTime()))
                .collect(Collectors.toList());
    }

    public MonitorHistoryVO getTaskHistoryLatest(Integer taskId) {
        List<MonitorHistory> historyList = historyMapper.selectList(
                new LambdaQueryWrapper<MonitorHistory>()
                        .eq(MonitorHistory::getTaskId, taskId)
                        .orderByDesc(MonitorHistory::getRecordTime)
                        .last("LIMIT 1"));

        if (historyList.isEmpty()) {
            return null;
        }
        MonitorHistory h = historyList.get(0);
        return new MonitorHistoryVO(h.getId(), parseValues(h), h.getRecordTime());
    }

    // =============================================
    // 指标ID序列化/反序列化工具
    // =============================================

    /**
     * 将 metricIds JSON 字符串反序列化为 List。
     */
    List<Integer> parseMetricIds(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<Integer>>() {});
        } catch (Exception e) {
            log.warn("解析 metricIds JSON 失败: {}", json, e);
            return List.of();
        }
    }

    /**
     * 将 List<Integer> 序列化为 JSON 字符串。
     */
    String serializeMetricIds(List<Integer> ids) {
        try {
            return objectMapper.writeValueAsString(ids);
        } catch (Exception e) {
            log.error("序列化 metricIds 失败", e);
            return "[]";
        }
    }

    /**
     * 从 MonitorHistory 解析指标值 Map。
     * 优先使用 metricValues JSON，兼容旧数据的 cpu/mem/disk 固定列。
     */
    private Map<String, Double> parseValues(MonitorHistory h) {
        Map<String, Double> values = new LinkedHashMap<>();
        String json = h.getMetricValues();
        if (json != null && !json.isBlank()) {
            try {
                values.putAll(objectMapper.readValue(json, new TypeReference<Map<String, Double>>() {}));
                return values;
            } catch (Exception e) {
                log.warn("解析 metricValues JSON 失败: {}", json, e);
            }
        }
        // 兼容旧数据：从固定列中读取
        if (h.getCpuUsage() != null) values.put("cpu_usage", (double) h.getCpuUsage());
        if (h.getMemUsage() != null) values.put("mem_usage", (double) h.getMemUsage());
        if (h.getDiskUsage() != null) values.put("disk_usage", (double) h.getDiskUsage());
        return values;
    }

    // =============================================
    // 工具方法
    // =============================================

    private double extractMetricValue(String text, String metricName) {
        Pattern pattern = Pattern.compile("^" + Pattern.quote(metricName) + "\\s+([\\d.e+-]+)", Pattern.MULTILINE);
        Matcher m = pattern.matcher(text);
        if (m.find()) {
            return Double.parseDouble(m.group(1));
        }
        return 0;
    }

    private double extractLabeledMetricValue(String text, String metricName, String labelKey, String labelValue) {
        Pattern pattern = Pattern.compile(
                Pattern.quote(metricName) + "\\{[^}]*" + Pattern.quote(labelKey) + "=\"" + Pattern.quote(labelValue) + "\"[^}]*}\\s+([\\d.e+-]+)");
        Matcher m = pattern.matcher(text);
        if (m.find()) {
            return Double.parseDouble(m.group(1));
        }
        return 0;
    }
}

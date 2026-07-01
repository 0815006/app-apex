package com.apex.service;

import com.apex.entity.*;
import com.apex.mapper.*;
import com.apex.model.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    // =============================================
    // 指标名 → 中文翻译映射（覆盖 Linux + Windows 两大族系）
    // =============================================

    private static final Map<String, String> CHINESE_NAME_MAP = new LinkedHashMap<>();
    private static final Map<String, String> METRIC_CATEGORY_MAP = new LinkedHashMap<>();

    static {
        // -- CPU --
        put("node_cpu_seconds_total", "CPU累计秒数", "cpu");
        put("node_cpu_guest_seconds_total", "CPU虚拟客户机秒数", "cpu");
        put("node_schedstat_running_seconds_total", "CPU调度运行秒数", "cpu");
        put("node_schedstat_timeslices_total", "CPU调度时间片总数", "cpu");
        put("node_schedstat_waiting_seconds_total", "CPU调度等待秒数", "cpu");
        put("node_load1", "1分钟平均负载", "cpu");
        put("node_load5", "5分钟平均负载", "cpu");
        put("node_load15", "15分钟平均负载", "cpu");
        put("node_procs_blocked", "阻塞进程数", "cpu");
        put("node_procs_running", "运行中进程数", "cpu");

        // -- Memory --
        put("node_memory_MemTotal_bytes", "物理内存总量(字节)", "memory");
        put("node_memory_MemFree_bytes", "物理空闲内存(字节)", "memory");
        put("node_memory_MemAvailable_bytes", "物理可用内存(字节)", "memory");
        put("node_memory_Buffers_bytes", "缓冲区内存(字节)", "memory");
        put("node_memory_Cached_bytes", "缓存内存(字节)", "memory");
        put("node_memory_SwapCached_bytes", "Swap缓存(字节)", "memory");
        put("node_memory_Active_bytes", "活跃内存(字节)", "memory");
        put("node_memory_Inactive_bytes", "非活跃内存(字节)", "memory");
        put("node_memory_AnonPages_bytes", "匿名页内存(字节)", "memory");
        put("node_memory_Mapped_bytes", "映射内存(字节)", "memory");
        put("node_memory_Shmem_bytes", "共享内存(字节)", "memory");
        put("node_memory_Slab_bytes", "Slab内核内存(字节)", "memory");
        put("node_memory_SReclaimable_bytes", "可回收Slab(字节)", "memory");
        put("node_memory_SUnreclaim_bytes", "不可回收Slab(字节)", "memory");
        put("node_memory_KernelStack_bytes", "内核栈内存(字节)", "memory");
        put("node_memory_PageTables_bytes", "页表内存(字节)", "memory");
        put("node_memory_SwapTotal_bytes", "Swap总量(字节)", "memory");
        put("node_memory_SwapFree_bytes", "Swap空闲(字节)", "memory");
        put("node_memory_Dirty_bytes", "脏页(字节)", "memory");
        put("node_memory_Writeback_bytes", "回写页(字节)", "memory");
        put("node_memory_HugePages_Total", "大页总数", "memory");
        put("node_memory_HugePages_Free", "大页空闲", "memory");
        put("node_memory_HugePages_Rsvd", "大页预留", "memory");
        put("node_memory_HugePages_Surp", "大页盈余", "memory");
        put("node_memory_Hugepagesize_bytes", "大页尺寸(字节)", "memory");
        put("node_memory_VmallocTotal_bytes", "Vmalloc总量(字节)", "memory");
        put("node_memory_VmallocUsed_bytes", "Vmalloc使用(字节)", "memory");
        put("node_memory_VmallocChunk_bytes", "Vmalloc最大块(字节)", "memory");

        // -- Disk IO --
        put("node_disk_read_bytes_total", "磁盘读取字节总数", "disk");
        put("node_disk_written_bytes_total", "磁盘写入字节总数", "disk");
        put("node_disk_reads_completed_total", "磁盘读取完成次数", "disk");
        put("node_disk_writes_completed_total", "磁盘写入完成次数", "disk");
        put("node_disk_read_time_seconds_total", "磁盘读取耗时(秒)", "disk");
        put("node_disk_write_time_seconds_total", "磁盘写入耗时(秒)", "disk");
        put("node_disk_io_time_seconds_total", "磁盘IO耗时(秒)", "disk");
        put("node_disk_discard_time_seconds_total", "磁盘discard耗时", "disk");
        put("node_disk_flush_requests_time_seconds_total", "磁盘flush耗时", "disk");
        put("node_disk_reads_merged_total", "磁盘读合并数", "disk");
        put("node_disk_writes_merged_total", "磁盘写合并数", "disk");
        put("node_disk_info", "磁盘设备信息", "disk");

        // -- Filesystem --
        put("node_filesystem_size_bytes", "文件系统总容量(字节)", "disk");
        put("node_filesystem_free_bytes", "文件系统空闲(字节)", "disk");
        put("node_filesystem_avail_bytes", "文件系统可用(字节)", "disk");
        put("node_filesystem_files", "文件系统inode总数", "disk");
        put("node_filesystem_files_free", "文件系统inode空闲", "disk");
        put("node_filesystem_readonly", "文件系统只读", "disk");
        put("node_filesystem_device_error", "文件系统设备错误", "disk");

        // -- Network --
        put("node_network_receive_bytes_total", "网络接收字节总数", "network");
        put("node_network_transmit_bytes_total", "网络发送字节总数", "network");
        put("node_network_receive_packets_total", "网络接收包总数", "network");
        put("node_network_transmit_packets_total", "网络发送包总数", "network");
        put("node_network_receive_errs_total", "网络接收错误数", "network");
        put("node_network_transmit_errs_total", "网络发送错误数", "network");
        put("node_network_receive_drop_total", "网络接收丢包数", "network");
        put("node_network_transmit_drop_total", "网络发送丢包数", "network");
        put("node_network_speed_bytes", "网卡速率(字节/秒)", "network");
        put("node_network_mtu_bytes", "网卡MTU", "network");
        put("node_network_info", "网卡信息", "network");
        put("node_network_carrier", "网卡载波", "network");
        put("node_network_iface_id", "网卡接口ID", "network");

        // -- Netstat --
        put("node_netstat_Icmp_InMsgs", "ICMP入站消息", "network");
        put("node_netstat_Icmp_OutMsgs", "ICMP出站消息", "network");
        put("node_netstat_Tcp_CurrEstab", "TCP当前连接数", "network");
        put("node_netstat_Tcp_InSegs", "TCP入站段数", "network");
        put("node_netstat_Tcp_OutSegs", "TCP出站段数", "network");
        put("node_netstat_Tcp_RetransSegs", "TCP重传段数", "network");
        put("node_netstat_Udp_InDatagrams", "UDP入站数据报", "network");
        put("node_netstat_Udp_OutDatagrams", "UDP出站数据报", "network");
        put("node_netstat_Ip_Forwarding", "IP转发", "network");

        // -- System --
        put("node_boot_time_seconds", "系统启动时间戳", "system");
        put("node_time_seconds", "当前系统时间戳", "system");
        put("node_context_switches_total", "上下文切换总数", "system");
        put("node_intr_total", "中断总数", "system");
        put("node_forks_total", "fork总数", "system");
        put("node_entropy_available_bits", "熵池可用位", "system");
        put("node_filefd_allocated", "已分配文件描述符", "system");
        put("node_filefd_maximum", "文件描述符上限", "system");
        put("node_nf_conntrack_entries", "连接跟踪条目数", "system");
        put("node_nf_conntrack_entries_limit", "连接跟踪上限", "system");
        put("node_arp_entries", "ARP表条目数", "system");
        put("node_os_info", "操作系统信息", "system");
        put("node_uname_info", "系统uname信息", "system");
        put("node_selinux_enabled", "SELinux状态", "system");
        put("node_time_zone_offset_seconds", "时区偏移(秒)", "system");

        // -- VMStat / Pressure --
        put("node_vmstat_oom_kill", "OOM Kill次数", "system");
        put("node_vmstat_pgfault", "页面错误次数", "system");
        put("node_vmstat_pgmajfault", "主页面错误次数", "system");
        put("node_vmstat_pgpgin", "页换入次数", "system");
        put("node_vmstat_pgpgout", "页换出次数", "system");
        put("node_vmstat_pswpin", "换入页数", "system");
        put("node_vmstat_pswpout", "换出页数", "system");
        put("node_pressure_cpu_waiting_seconds_total", "CPU压力等待秒数", "system");
        put("node_pressure_io_stalled_seconds_total", "IO压力停滞秒数", "system");
        put("node_pressure_memory_stalled_seconds_total", "内存压力停滞秒数", "system");

        // -- Sockstat / Softnet --
        put("node_sockstat_TCP_inuse", "TCP套接字使用中", "network");
        put("node_sockstat_UDP_inuse", "UDP套接字使用中", "network");
        put("node_sockstat_sockets_used", "已使用套接字", "network");
        put("node_softnet_processed_total", "软中断处理数", "network");
        put("node_softnet_dropped_total", "软中断丢包数", "network");

        // -- Windows CPU --
        put("windows_cpu_time_total", "CPU时间总计", "cpu");
        put("windows_cpu_clock_interrupts_total", "CPU时钟中断总数", "cpu");
        put("windows_cpu_interrupts_total", "CPU硬件中断总数", "cpu");
        put("windows_cpu_dpcs_total", "CPU DPC调用总数", "cpu");
        put("windows_cpu_idle_break_events_total", "CPU空闲唤醒次数", "cpu");
        put("windows_cpu_core_frequency_mhz", "CPU核心频率(MHz)", "cpu");
        put("windows_cpu_cstate_seconds_total", "CPU C状态秒数", "cpu");
        put("windows_cpu_parking_status", "CPU Parking状态", "cpu");
        put("windows_cpu_processor_mp", "CPU MPerf", "cpu");
        put("windows_cpu_processor_performance", "CPU性能百分比", "cpu");
        put("windows_cpu_processor_utility", "CPU实用率", "cpu");
        put("windows_cpu_logical_processor", "逻辑处理器数", "cpu");

        // -- Windows Memory --
        put("windows_memory_available_bytes", "可用内存(字节)", "memory");
        put("windows_memory_cache_bytes", "缓存内存(字节)", "memory");
        put("windows_memory_committed_bytes", "已提交内存(字节)", "memory");
        put("windows_memory_commit_limit", "提交内存上限", "memory");
        put("windows_memory_pool_paged_bytes", "分页池(字节)", "memory");
        put("windows_memory_pool_nonpaged_bytes", "非分页池(字节)", "memory");
        put("windows_memory_physical_total_bytes", "物理内存总量(字节)", "memory");
        put("windows_memory_physical_free_bytes", "物理空闲内存(字节)", "memory");
        put("windows_memory_page_faults_total", "页面错误总数", "memory");
        put("windows_memory_standby_cache_bytes", "备用缓存(字节)", "memory");
        put("windows_memory_modified_bytes", "已修改页(字节)", "memory");
        put("windows_memory_swap_page_reads_total", "Swap页读取总数", "memory");
        put("windows_memory_swap_page_writes_total", "Swap页写入总数", "memory");

        // -- Windows Disk --
        put("windows_logical_disk_size_bytes", "逻辑磁盘总容量(字节)", "disk");
        put("windows_logical_disk_free_bytes", "逻辑磁盘空闲(字节)", "disk");
        put("windows_logical_disk_read_bytes_total", "逻辑磁盘读取字节", "disk");
        put("windows_logical_disk_write_bytes_total", "逻辑磁盘写入字节", "disk");
        put("windows_logical_disk_reads_total", "逻辑磁盘读取次数", "disk");
        put("windows_logical_disk_writes_total", "逻辑磁盘写入次数", "disk");
        put("windows_logical_disk_read_seconds_total", "逻辑磁盘读取耗时", "disk");
        put("windows_logical_disk_write_seconds_total", "逻辑磁盘写入耗时", "disk");
        put("windows_logical_disk_idle_seconds_total", "逻辑磁盘空闲秒数", "disk");
        put("windows_logical_disk_split_ios_total", "逻辑磁盘拆分IO数", "disk");
        put("windows_logical_disk_queue_length", "逻辑磁盘队列长度", "disk");
        put("windows_physical_disk_size_bytes", "物理磁盘总容量", "disk");
        put("windows_physical_disk_read_bytes_total", "物理磁盘读取字节", "disk");
        put("windows_physical_disk_write_bytes_total", "物理磁盘写入字节", "disk");
        put("windows_physical_disk_reads_total", "物理磁盘读取次数", "disk");
        put("windows_physical_disk_writes_total", "物理磁盘写入次数", "disk");
        put("windows_physical_disk_read_seconds_total", "物理磁盘读取耗时", "disk");
        put("windows_physical_disk_write_seconds_total", "物理磁盘写入耗时", "disk");
        put("windows_physical_disk_idle_seconds_total", "物理磁盘空闲秒数", "disk");
        put("windows_physical_disk_queue_length", "物理磁盘队列长度", "disk");

        // -- Windows Network --
        put("windows_net_bytes_received_total", "网络接收字节总数", "network");
        put("windows_net_bytes_sent_total", "网络发送字节总数", "network");
        put("windows_net_packets_received_total", "网络接收包总数", "network");
        put("windows_net_packets_sent_total", "网络发送包总数", "network");
        put("windows_net_packets_received_errors_total", "网络接收错误", "network");
        put("windows_net_packets_outbound_errors_total", "网络发送错误", "network");
        put("windows_net_packets_received_discarded_total", "网络接收丢弃", "network");
        put("windows_net_packets_outbound_discarded_total", "网络发送丢弃", "network");
        put("windows_net_current_bandwidth", "网卡带宽(bps)", "network");
        put("windows_net_output_queue_length", "网卡输出队列", "network");

        // -- Windows Service --
        put("windows_service_info", "服务信息", "service");
        put("windows_service_state", "服务运行状态", "service");
        put("windows_service_start_mode", "服务启动模式", "service");
        put("windows_service_process", "服务进程信息", "service");

        // -- Windows OS --
        put("windows_os_info", "操作系统信息", "system");
        put("windows_os_hostname", "主机名", "system");
        put("windows_system_boot_time_timestamp", "系统启动时间戳", "system");
        put("windows_system_context_switches_total", "上下文切换总数", "system");
    }

    private static void put(String key, String chineseName, String category) {
        CHINESE_NAME_MAP.put(key, chineseName);
        METRIC_CATEGORY_MAP.put(key, category);
    }

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
     * 根据指标名查找中文翻译。
     */
    public String getChineseName(String metricName) {
        return CHINESE_NAME_MAP.getOrDefault(metricName, metricName);
    }

    /**
     * 根据指标名前缀推断分类。
     */
    public String inferCategory(String metricName) {
        // 优先精确匹配
        if (METRIC_CATEGORY_MAP.containsKey(metricName)) {
            return METRIC_CATEGORY_MAP.get(metricName);
        }
        // 前缀推断
        if (metricName.startsWith("node_cpu_") || metricName.startsWith("windows_cpu_")
                || metricName.startsWith("node_schedstat_") || metricName.startsWith("node_load")
                || metricName.startsWith("node_procs_")) {
            return "cpu";
        }
        if (metricName.startsWith("node_memory_") || metricName.startsWith("windows_memory_")) {
            return "memory";
        }
        if (metricName.startsWith("node_disk_") || metricName.startsWith("windows_logical_disk_")
                || metricName.startsWith("windows_physical_disk_")
                || metricName.startsWith("node_filesystem_")) {
            return "disk";
        }
        if (metricName.startsWith("node_network_") || metricName.startsWith("windows_net_")
                || metricName.startsWith("node_netstat_") || metricName.startsWith("node_sockstat_")
                || metricName.startsWith("node_softnet_")) {
            return "network";
        }
        if (metricName.startsWith("windows_service_")) {
            return "service";
        }
        if (metricName.startsWith("node_") || metricName.startsWith("windows_os_")
                || metricName.startsWith("windows_system_")) {
            return "system";
        }
        // Go运行时指标
        if (metricName.startsWith("go_") || metricName.startsWith("process_")) {
            return "runtime";
        }
        return "other";
    }

    /**
     * 分类名中文翻译。
     */
    public String getCategoryChineseName(String categoryKey) {
        return switch (categoryKey) {
            case "cpu" -> "CPU";
            case "memory" -> "内存";
            case "disk" -> "磁盘/文件系统";
            case "network" -> "网络";
            case "service" -> "服务";
            case "system" -> "系统";
            case "runtime" -> "Go运行时";
            default -> "其他";
        };
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
            List<String> categoryOrder = List.of("cpu", "memory", "disk", "network", "service", "system", "runtime", "other");
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

        Map<Integer, String> machineNameMap = machineMapper.selectList(null).stream()
                .collect(Collectors.toMap(MonitorMachine::getId, MonitorMachine::getMachineName));

        return tasks.stream()
                .map(t -> new SampleTaskVO(
                        t.getId(),
                        t.getMachineId(),
                        machineNameMap.getOrDefault(t.getMachineId(), "未知"),
                        t.getTaskName(),
                        t.getStartTime(),
                        t.getEndTime(),
                        t.getCollectInterval(),
                        t.getStatus()))
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

        MonitorSampleTask task = new MonitorSampleTask();
        task.setMachineId(dto.getMachineId());
        task.setTaskName(dto.getTaskName());
        task.setStartTime(dto.getStartTime());
        task.setEndTime(dto.getEndTime());
        task.setCollectInterval(dto.getCollectInterval());
        task.setStatus("WAITING");
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
                        h.getCpuUsage(),
                        h.getMemUsage(),
                        h.getDiskUsage(),
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
        return new MonitorHistoryVO(
                h.getId(), h.getCpuUsage(), h.getMemUsage(), h.getDiskUsage(), h.getRecordTime());
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

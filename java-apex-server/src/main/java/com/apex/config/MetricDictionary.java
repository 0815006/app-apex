package com.apex.config;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 指标字典 — 集中管理所有 Prometheus 指标的中文翻译、分类归属与前缀推断规则。
 * <p>
 * 日常维护只需编辑 {@link #ENTRIES} 静态初始化块中的 entry() 调用即可，
 * 分类用 {@link MetricCategory} 枚举，IDE 自动补全，编译时校验。
 * </p>
 */
public final class MetricDictionary {

    private MetricDictionary() {}

    // =============================================
    // 1. 指标分类枚举（固定封闭集）
    // =============================================

    /** 指标大类。新增分类只需在此添加枚举值。 */
    public enum MetricCategory {
        CPU("cpu", "CPU"),
        MEMORY("memory", "内存"),
        DISK("disk", "磁盘/文件系统"),
        NETWORK("network", "网络"),
        SERVICE("service", "服务"),
        PORT("port", "端口"),
        PROCESS("process", "进程"),
        SYSTEM("system", "系统"),
        RUNTIME("runtime", "Go运行时"),
        OTHER("other", "其他");

        private final String key;
        private final String chineseName;

        MetricCategory(String key, String chineseName) {
            this.key = key;
            this.chineseName = chineseName;
        }

        public String getKey() { return key; }
        public String getChineseName() { return chineseName; }

        /** 按业务约定顺序返回所有分类 key。 */
        public static List<String> orderedKeys() {
            return List.of("cpu", "memory", "disk", "network", "service", "port", "process", "system", "runtime", "other");
        }

        /** key → 枚举 反向查找。 */
        public static MetricCategory fromKey(String key) {
            for (MetricCategory c : values()) {
                if (c.key.equals(key)) return c;
            }
            return OTHER;
        }
    }

    // =============================================
    // 2. 单条指标定义 Record
    // =============================================

    /** 一条指标定义：Prometheus 指标名 → 中文翻译 + 分类。 */
    public record MetricEntry(String metricName, String chineseName, MetricCategory category) {}

    // =============================================
    // 3. 前缀推断规则（配置式，顺序敏感，必须在 ENTRIES 之前初始化）
    // =============================================

    /** 前缀 → 分类 映射，按匹配优先级排列。先匹配先生效。 */
    private static final LinkedHashMap<String, MetricCategory> PREFIX_RULES = new LinkedHashMap<>();

    static {
        // CPU
        PREFIX_RULES.put("node_cpu_",             MetricCategory.CPU);
        PREFIX_RULES.put("windows_cpu_",          MetricCategory.CPU);
        PREFIX_RULES.put("node_schedstat_",       MetricCategory.CPU);
        PREFIX_RULES.put("node_load",             MetricCategory.CPU);
        PREFIX_RULES.put("node_procs_",           MetricCategory.CPU);

        // Memory
        PREFIX_RULES.put("node_memory_",          MetricCategory.MEMORY);
        PREFIX_RULES.put("windows_memory_",       MetricCategory.MEMORY);

        // Disk
        PREFIX_RULES.put("node_disk_",            MetricCategory.DISK);
        PREFIX_RULES.put("windows_logical_disk_", MetricCategory.DISK);
        PREFIX_RULES.put("windows_physical_disk_",MetricCategory.DISK);
        PREFIX_RULES.put("node_filesystem_",      MetricCategory.DISK);

        // Port (Linux TCP 前缀放前面，优先于 node_netstat_ / node_sockstat_ 通用规则)
        PREFIX_RULES.put("node_netstat_Tcp_",     MetricCategory.PORT);
        PREFIX_RULES.put("node_sockstat_TCP_",    MetricCategory.PORT);
        PREFIX_RULES.put("windows_tcp_",          MetricCategory.PORT);
        // Textfile Collector 注入的监听端口指标
        PREFIX_RULES.put("node_listening_port",   MetricCategory.PORT);
        PREFIX_RULES.put("windows_listening_port",MetricCategory.PORT);

        // Network
        PREFIX_RULES.put("node_network_",         MetricCategory.NETWORK);
        PREFIX_RULES.put("windows_net_",          MetricCategory.NETWORK);
        PREFIX_RULES.put("node_netstat_",         MetricCategory.NETWORK);
        PREFIX_RULES.put("node_sockstat_",        MetricCategory.NETWORK);
        PREFIX_RULES.put("node_softnet_",         MetricCategory.NETWORK);

        // Service
        PREFIX_RULES.put("windows_service_",      MetricCategory.SERVICE);

        // Process (放在 windows_os_/windows_system_ 之前，避免被 SYSTEM 兜底)
        PREFIX_RULES.put("windows_process_",       MetricCategory.PROCESS);

        // System (node_ 兜底要在最后)
        PREFIX_RULES.put("windows_os_",           MetricCategory.SYSTEM);
        PREFIX_RULES.put("windows_system_",       MetricCategory.SYSTEM);
        PREFIX_RULES.put("node_",                 MetricCategory.SYSTEM);  // 兜底

        // Exporter 自监控
        PREFIX_RULES.put("windows_exporter_",      MetricCategory.RUNTIME);
        PREFIX_RULES.put("promhttp_",              MetricCategory.RUNTIME);

        // Runtime
        PREFIX_RULES.put("go_",                   MetricCategory.RUNTIME);
        PREFIX_RULES.put("process_",              MetricCategory.RUNTIME);
    }

    /**
     * 纯前缀推断（不查精确匹配表）。供 entry() 构建时使用，
     * 避免构建期循环依赖。
     */
    private static MetricCategory inferCategoryByPrefix(String metricName) {
        for (Map.Entry<String, MetricCategory> rule : PREFIX_RULES.entrySet()) {
            if (metricName.startsWith(rule.getKey())) {
                return rule.getValue();
            }
        }
        return MetricCategory.OTHER;
    }

    /** 便捷录入方法 — 自动归类。 */
    private static void entry(List<MetricEntry> list, String metricName, String chineseName) {
        MetricCategory cat = inferCategoryByPrefix(metricName);
        list.add(new MetricEntry(metricName, chineseName, cat));
    }

    // =============================================
    // 4. ★ 集中编辑区 — 日常只需在这里增删改指标定义 ★
    // =============================================

    /** 所有已知指标的完整定义列表。按分类分区组织，便于阅读维护。 */
    public static final List<MetricEntry> ENTRIES;

    static {
        List<MetricEntry> list = new ArrayList<>();

        // ==================== CPU ====================
        entry(list, "node_cpu_seconds_total",              "CPU累计秒数");
        entry(list, "node_cpu_guest_seconds_total",        "CPU虚拟客户机秒数");
        entry(list, "node_schedstat_running_seconds_total","CPU调度运行秒数");
        entry(list, "node_schedstat_timeslices_total",     "CPU调度时间片总数");
        entry(list, "node_schedstat_waiting_seconds_total","CPU调度等待秒数");
        entry(list, "node_load1",                          "1分钟平均负载");
        entry(list, "node_load5",                          "5分钟平均负载");
        entry(list, "node_load15",                         "15分钟平均负载");
        entry(list, "node_procs_blocked",                  "阻塞进程数");
        entry(list, "node_procs_running",                  "运行中进程数");

        // ==================== Memory ====================
        entry(list, "node_memory_MemTotal_bytes",      "物理内存总量(字节)");
        entry(list, "node_memory_MemFree_bytes",       "物理空闲内存(字节)");
        entry(list, "node_memory_MemAvailable_bytes",  "物理可用内存(字节)");
        entry(list, "node_memory_Buffers_bytes",       "缓冲区内存(字节)");
        entry(list, "node_memory_Cached_bytes",        "缓存内存(字节)");
        entry(list, "node_memory_SwapCached_bytes",    "Swap缓存(字节)");
        entry(list, "node_memory_Active_bytes",        "活跃内存(字节)");
        entry(list, "node_memory_Inactive_bytes",      "非活跃内存(字节)");
        entry(list, "node_memory_AnonPages_bytes",     "匿名页内存(字节)");
        entry(list, "node_memory_Mapped_bytes",        "映射内存(字节)");
        entry(list, "node_memory_Shmem_bytes",         "共享内存(字节)");
        entry(list, "node_memory_Slab_bytes",          "Slab内核内存(字节)");
        entry(list, "node_memory_SReclaimable_bytes",  "可回收Slab(字节)");
        entry(list, "node_memory_SUnreclaim_bytes",    "不可回收Slab(字节)");
        entry(list, "node_memory_KernelStack_bytes",   "内核栈内存(字节)");
        entry(list, "node_memory_PageTables_bytes",    "页表内存(字节)");
        entry(list, "node_memory_SwapTotal_bytes",     "Swap总量(字节)");
        entry(list, "node_memory_SwapFree_bytes",      "Swap空闲(字节)");
        entry(list, "node_memory_Dirty_bytes",         "脏页(字节)");
        entry(list, "node_memory_Writeback_bytes",     "回写页(字节)");
        entry(list, "node_memory_HugePages_Total",     "大页总数");
        entry(list, "node_memory_HugePages_Free",      "大页空闲");
        entry(list, "node_memory_HugePages_Rsvd",      "大页预留");
        entry(list, "node_memory_HugePages_Surp",      "大页盈余");
        entry(list, "node_memory_Hugepagesize_bytes",  "大页尺寸(字节)");
        entry(list, "node_memory_VmallocTotal_bytes",  "Vmalloc总量(字节)");
        entry(list, "node_memory_VmallocUsed_bytes",   "Vmalloc使用(字节)");
        entry(list, "node_memory_VmallocChunk_bytes",  "Vmalloc最大块(字节)");
        entry(list, "node_memory_Active_anon_bytes",    "活跃匿名页(字节)");
        entry(list, "node_memory_Active_file_bytes",    "活跃文件页(字节)");
        entry(list, "node_memory_AnonHugePages_bytes",  "匿名大页(字节)");
        entry(list, "node_memory_Bounce_bytes",          "Bounce缓冲区(字节)");
        entry(list, "node_memory_CommitLimit_bytes",     "提交内存上限(字节)");
        entry(list, "node_memory_Committed_AS_bytes",    "已提交地址空间(字节)");
        entry(list, "node_memory_DirectMap1G_bytes",     "DirectMap 1G页(字节)");
        entry(list, "node_memory_DirectMap2M_bytes",     "DirectMap 2M页(字节)");
        entry(list, "node_memory_DirectMap4k_bytes",     "DirectMap 4K页(字节)");
        entry(list, "node_memory_HardwareCorrupted_bytes","硬件损坏内存(字节)");
        entry(list, "node_memory_Inactive_anon_bytes",   "非活跃匿名页(字节)");
        entry(list, "node_memory_Inactive_file_bytes",   "非活跃文件页(字节)");
        entry(list, "node_memory_Mlocked_bytes",          "锁定内存(字节)");
        entry(list, "node_memory_NFS_Unstable_bytes",     "NFS不稳定页(字节)");
        entry(list, "node_memory_Percpu_bytes",           "Per-CPU分配(字节)");
        entry(list, "node_memory_ShmemHugePages_bytes",   "共享内存大页(字节)");
        entry(list, "node_memory_ShmemPmdMapped_bytes",   "共享内存PMD映射(字节)");
        entry(list, "node_memory_Unevictable_bytes",      "不可回收内存(字节)");
        entry(list, "node_memory_WritebackTmp_bytes",     "临时回写页(字节)");

        // ==================== Disk IO ====================
        entry(list, "node_disk_read_bytes_total",              "磁盘读取字节总数");
        entry(list, "node_disk_written_bytes_total",           "磁盘写入字节总数");
        entry(list, "node_disk_reads_completed_total",         "磁盘读取完成次数");
        entry(list, "node_disk_writes_completed_total",        "磁盘写入完成次数");
        entry(list, "node_disk_read_time_seconds_total",       "磁盘读取耗时(秒)");
        entry(list, "node_disk_write_time_seconds_total",      "磁盘写入耗时(秒)");
        entry(list, "node_disk_io_time_seconds_total",         "磁盘IO耗时(秒)");
        entry(list, "node_disk_discard_time_seconds_total",    "磁盘discard耗时");
        entry(list, "node_disk_flush_requests_time_seconds_total","磁盘flush耗时");
        entry(list, "node_disk_reads_merged_total",            "磁盘读合并数");
        entry(list, "node_disk_writes_merged_total",           "磁盘写合并数");
        entry(list, "node_disk_info",                          "磁盘设备信息");
        entry(list, "node_disk_discarded_sectors_total",       "磁盘废弃扇区总数");
        entry(list, "node_disk_discards_completed_total",      "磁盘废弃操作完成次数");
        entry(list, "node_disk_discards_merged_total",         "磁盘废弃合并数");
        entry(list, "node_disk_flush_requests_total",          "磁盘Flush请求总数");
        entry(list, "node_disk_io_now",                        "磁盘当前IO数");
        entry(list, "node_disk_io_time_weighted_seconds_total","磁盘IO加权耗时(秒)");

        // ==================== Filesystem ====================
        entry(list, "node_filesystem_size_bytes",    "文件系统总容量(字节)");
        entry(list, "node_filesystem_free_bytes",    "文件系统空闲(字节)");
        entry(list, "node_filesystem_avail_bytes",   "文件系统可用(字节)");
        entry(list, "node_filesystem_files",         "文件系统inode总数");
        entry(list, "node_filesystem_files_free",    "文件系统inode空闲");
        entry(list, "node_filesystem_readonly",      "文件系统只读");
        entry(list, "node_filesystem_device_error",  "文件系统设备错误");
        entry(list, "node_filesystem_mount_info",    "文件系统挂载信息");
        entry(list, "node_filesystem_purgeable_bytes","文件系统可清除(字节)");

        // ==================== Network ====================
        entry(list, "node_network_receive_bytes_total",    "网络接收字节总数");
        entry(list, "node_network_transmit_bytes_total",   "网络发送字节总数");
        entry(list, "node_network_receive_packets_total",  "网络接收包总数");
        entry(list, "node_network_transmit_packets_total", "网络发送包总数");
        entry(list, "node_network_receive_errs_total",     "网络接收错误数");
        entry(list, "node_network_transmit_errs_total",    "网络发送错误数");
        entry(list, "node_network_receive_drop_total",     "网络接收丢包数");
        entry(list, "node_network_transmit_drop_total",    "网络发送丢包数");
        entry(list, "node_network_speed_bytes",            "网卡速率(字节/秒)");
        entry(list, "node_network_mtu_bytes",              "网卡MTU");
        entry(list, "node_network_info",                   "网卡信息");
        entry(list, "node_network_carrier",                "网卡载波");
        entry(list, "node_network_iface_id",               "网卡接口ID");
        entry(list, "node_network_carrier_changes_total",     "载波变化总数");
        entry(list, "node_network_carrier_down_changes_total","载波断开次数");
        entry(list, "node_network_carrier_up_changes_total",  "载波连接次数");
        entry(list, "node_network_device_id",                 "网卡设备ID");
        entry(list, "node_network_dormant",                   "网卡休眠状态");
        entry(list, "node_network_flags",                     "网卡标志位");
        entry(list, "node_network_iface_link",                "网卡链路状态");
        entry(list, "node_network_iface_link_mode",           "网卡链路模式");
        entry(list, "node_network_name_assign_type",          "网卡名称分配类型");
        entry(list, "node_network_net_dev_group",             "网卡设备组");
        entry(list, "node_network_protocol_type",              "网卡协议类型");
        entry(list, "node_network_receive_compressed_total",   "网络接收压缩包总数");
        entry(list, "node_network_receive_fifo_total",         "网络接收FIFO错误");
        entry(list, "node_network_receive_frame_total",        "网络接收帧对齐错误");
        entry(list, "node_network_receive_multicast_total",    "网络接收多播包总数");
        entry(list, "node_network_transmit_carrier_total",     "网络发送载波错误");
        entry(list, "node_network_transmit_compressed_total",  "网络发送压缩包总数");
        entry(list, "node_network_transmit_fifo_total",        "网络发送FIFO错误");
        entry(list, "node_network_address_assign_type",         "网卡地址分配类型");
        entry(list, "node_network_receive_nohandler_total",     "网络接收无处理器丢包");
        entry(list, "node_network_transmit_colls_total",        "网络发送冲突总数");
        entry(list, "node_network_transmit_queue_length",      "网络发送队列长度");
        entry(list, "node_network_up",                         "网卡启用状态");

        // ==================== Netstat ====================
        entry(list, "node_netstat_Icmp_InMsgs",     "ICMP入站消息");
        entry(list, "node_netstat_Icmp_OutMsgs",    "ICMP出站消息");
        entry(list, "node_netstat_Udp_InDatagrams", "UDP入站数据报");
        entry(list, "node_netstat_Udp_OutDatagrams","UDP出站数据报");
        entry(list, "node_netstat_Ip_Forwarding",   "IP转发");
        // -- ICMP6/ICMP --
        entry(list, "node_netstat_Icmp6_InErrors",   "ICMPv6入站错误");
        entry(list, "node_netstat_Icmp6_InMsgs",     "ICMPv6入站消息");
        entry(list, "node_netstat_Icmp6_OutMsgs",    "ICMPv6出站消息");
        entry(list, "node_netstat_Icmp_InErrors",    "ICMP入站错误");
        // -- IP6/IpExt --
        entry(list, "node_netstat_Ip6_InOctets",     "IPv6入站字节");
        entry(list, "node_netstat_Ip6_OutOctets",    "IPv6出站字节");
        entry(list, "node_netstat_IpExt_InOctets",   "IP扩展入站字节");
        entry(list, "node_netstat_IpExt_OutOctets",  "IP扩展出站字节");
        // -- TcpExt --
        entry(list, "node_netstat_TcpExt_ListenDrops",     "TCP监听丢弃");
        entry(list, "node_netstat_TcpExt_ListenOverflows", "TCP监听溢出");
        entry(list, "node_netstat_TcpExt_SyncookiesFailed","TCP Syncookie失败");
        entry(list, "node_netstat_TcpExt_SyncookiesRecv",  "TCP Syncookie接收");
        entry(list, "node_netstat_TcpExt_SyncookiesSent",  "TCP Syncookie发送");
        entry(list, "node_netstat_TcpExt_TCPOFOQueue",     "TCP乱序队列");
        entry(list, "node_netstat_TcpExt_TCPRcvQDrop",     "TCP接收队列丢弃");
        entry(list, "node_netstat_TcpExt_TCPSynRetrans",   "TCP SYN重传");
        entry(list, "node_netstat_TcpExt_TCPTimeouts",     "TCP超时次数");
        // -- Tcp --
        entry(list, "node_netstat_Tcp_ActiveOpens",  "TCP主动打开数");
        entry(list, "node_netstat_Tcp_InErrs",       "TCP入站错误");
        entry(list, "node_netstat_Tcp_OutRsts",      "TCP出站RST");
        entry(list, "node_netstat_Tcp_PassiveOpens", "TCP被动打开数");
        // -- UDP6 --
        entry(list, "node_netstat_Udp6_InDatagrams",  "UDPv6入站数据报");
        entry(list, "node_netstat_Udp6_InErrors",     "UDPv6入站错误");
        entry(list, "node_netstat_Udp6_NoPorts",      "UDPv6无端口");
        entry(list, "node_netstat_Udp6_OutDatagrams", "UDPv6出站数据报");
        entry(list, "node_netstat_Udp6_RcvbufErrors", "UDPv6接收缓冲错误");
        entry(list, "node_netstat_Udp6_SndbufErrors", "UDPv6发送缓冲错误");
        // -- UDP --
        entry(list, "node_netstat_Udp_InErrors",     "UDP入站错误");
        entry(list, "node_netstat_Udp_NoPorts",      "UDP无端口");
        entry(list, "node_netstat_Udp_RcvbufErrors", "UDP接收缓冲错误");
        entry(list, "node_netstat_Udp_SndbufErrors", "UDP发送缓冲错误");
        // -- UDP-Lite --
        entry(list, "node_netstat_UdpLite6_InErrors", "UDP-Litev6入站错误");
        entry(list, "node_netstat_UdpLite_InErrors",  "UDP-Lite入站错误");

        // ==================== System ====================
        entry(list, "node_boot_time_seconds",            "系统启动时间戳");
        entry(list, "node_time_seconds",                 "当前系统时间戳");
        entry(list, "node_context_switches_total",       "上下文切换总数");
        entry(list, "node_intr_total",                   "中断总数");
        entry(list, "node_forks_total",                  "fork总数");
        entry(list, "node_entropy_available_bits",       "熵池可用位");
        entry(list, "node_filefd_allocated",             "已分配文件描述符");
        entry(list, "node_filefd_maximum",               "文件描述符上限");
        entry(list, "node_nf_conntrack_entries",         "连接跟踪条目数");
        entry(list, "node_nf_conntrack_entries_limit",   "连接跟踪上限");
        entry(list, "node_arp_entries",                  "ARP表条目数");
        entry(list, "node_os_info",                      "操作系统信息");
        entry(list, "node_uname_info",                   "系统uname信息");
        entry(list, "node_os_version",                   "操作系统版本号");
        entry(list, "node_selinux_enabled",              "SELinux状态");
        entry(list, "node_time_zone_offset_seconds",     "时区偏移(秒)");
        entry(list, "node_cooling_device_cur_state",     "散热设备当前状态");
        entry(list, "node_cooling_device_max_state",     "散热设备最大状态");
        entry(list, "node_dmi_info",                     "DMI硬件信息");
        entry(list, "node_entropy_pool_size_bits",       "熵池大小(位)");
        entry(list, "node_exporter_build_info",          "Node Exporter构建信息");
        entry(list, "node_processes_max_processes",      "进程数上限");
        entry(list, "node_processes_max_threads",        "线程数上限");
        entry(list, "node_processes_pids",               "当前PID数");
        entry(list, "node_processes_state",              "进程状态分布");
        entry(list, "node_processes_threads",            "线程总数");
        entry(list, "node_processes_threads_state",      "线程状态分布");
        entry(list, "node_scrape_collector_duration_seconds","Exporter采集器耗时(秒)");
        entry(list, "node_scrape_collector_success",     "Exporter采集器成功状态");
        entry(list, "node_tcp_connection_states",        "TCP连接状态分布");
        entry(list, "node_textfile_scrape_error",        "Textfile抓取错误");
        entry(list, "node_time_clocksource_available_info","可用时钟源信息");
        entry(list, "node_time_clocksource_current_info", "当前时钟源信息");

        // ==================== VMStat / Pressure ====================
        entry(list, "node_vmstat_oom_kill",                      "OOM Kill次数");
        entry(list, "node_vmstat_pgfault",                       "页面错误次数");
        entry(list, "node_vmstat_pgmajfault",                    "主页面错误次数");
        entry(list, "node_vmstat_pgpgin",                        "页换入次数");
        entry(list, "node_vmstat_pgpgout",                       "页换出次数");
        entry(list, "node_vmstat_pswpin",                        "换入页数");
        entry(list, "node_vmstat_pswpout",                       "换出页数");
        entry(list, "node_pressure_cpu_waiting_seconds_total",   "CPU压力等待秒数");
        entry(list, "node_pressure_io_stalled_seconds_total",    "IO压力停滞秒数");
        entry(list, "node_pressure_io_waiting_seconds_total",    "IO压力等待秒数");
        entry(list, "node_pressure_memory_stalled_seconds_total","内存压力停滞秒数");
        entry(list, "node_pressure_memory_waiting_seconds_total","内存压力等待秒数");
        // -- Timex --
        entry(list, "node_timex_estimated_error_seconds",        "时间同步估计误差(秒)");
        entry(list, "node_timex_frequency_adjustment_ratio",     "时间同步频率调整比率");
        entry(list, "node_timex_loop_time_constant",             "时间同步环路时间常数");
        entry(list, "node_timex_maxerror_seconds",               "时间同步最大误差(秒)");
        entry(list, "node_timex_offset_seconds",                 "时间同步偏移(秒)");
        entry(list, "node_timex_pps_calibration_total",          "PPS校准总数");
        entry(list, "node_timex_pps_error_total",                "PPS错误总数");
        entry(list, "node_timex_pps_frequency_hertz",            "PPS频率(Hz)");
        entry(list, "node_timex_pps_jitter_seconds",             "PPS抖动(秒)");
        entry(list, "node_timex_pps_jitter_total",               "PPS抖动总数");
        entry(list, "node_timex_pps_shift_seconds",              "PPS偏移(秒)");
        entry(list, "node_timex_pps_stability_exceeded_total",   "PPS稳定性超限总数");
        entry(list, "node_timex_pps_stability_hertz",            "PPS稳定性(Hz)");
        entry(list, "node_timex_status",                         "时间同步状态码");
        entry(list, "node_timex_sync_status",                    "时间同步状态");
        entry(list, "node_timex_tai_offset_seconds",             "TAI时间偏移(秒)");
        entry(list, "node_timex_tick_seconds",                   "时钟滴答(秒)");
        // -- UDP Queues --
        entry(list, "node_udp_queues",                           "UDP队列长度");

        // ==================== Sockstat / Softnet ====================
        entry(list, "node_sockstat_UDP_inuse",       "UDP套接字使用中");
        entry(list, "node_sockstat_sockets_used",    "已使用套接字");
        entry(list, "node_sockstat_FRAG6_inuse",     "IPv6分片套接字使用中");
        entry(list, "node_sockstat_FRAG6_memory",    "IPv6分片套接字内存");
        entry(list, "node_sockstat_FRAG_inuse",      "IPv4分片套接字使用中");
        entry(list, "node_sockstat_FRAG_memory",     "IPv4分片套接字内存");
        entry(list, "node_sockstat_RAW6_inuse",      "IPv6 RAW套接字使用中");
        entry(list, "node_sockstat_RAW_inuse",       "IPv4 RAW套接字使用中");
        entry(list, "node_sockstat_TCP6_inuse",      "TCPv6套接字使用中");
        entry(list, "node_sockstat_UDP6_inuse",      "UDPv6套接字使用中");
        entry(list, "node_sockstat_UDPLITE6_inuse",  "UDP-Litev6套接字使用中");
        entry(list, "node_sockstat_UDPLITE_inuse",   "UDP-Lite套接字使用中");
        entry(list, "node_sockstat_UDP_mem",         "UDP套接字内存(页)");
        entry(list, "node_sockstat_UDP_mem_bytes",   "UDP套接字内存(字节)");
        entry(list, "node_softnet_processed_total",  "软中断处理数");
        entry(list, "node_softnet_dropped_total",    "软中断丢包数");
        entry(list, "node_softnet_backlog_len",       "软中断backlog长度");
        entry(list, "node_softnet_cpu_collision_total","软中断CPU冲突总数");
        entry(list, "node_softnet_flow_limit_count_total","软中断流控限制总数");
        entry(list, "node_softnet_received_rps_total","软中断RPS接收总数");
        entry(list, "node_softnet_times_squeezed_total","软中断挤压次数");

        // ==================== Port (TCP 连接与传输) ====================
        // -- Linux --
        entry(list, "node_netstat_Tcp_CurrEstab",           "TCP当前连接数");
        entry(list, "node_netstat_Tcp_InSegs",              "TCP入站段数");
        entry(list, "node_netstat_Tcp_OutSegs",             "TCP出站段数");
        entry(list, "node_netstat_Tcp_RetransSegs",         "TCP重传段数");
        entry(list, "node_sockstat_TCP_inuse",               "TCP套接字使用中");
        entry(list, "node_sockstat_TCP_alloc",               "TCP套接字已分配");
        entry(list, "node_sockstat_TCP_mem",                 "TCP套接字内存(页)");
        entry(list, "node_sockstat_TCP_mem_bytes",           "TCP套接字内存(字节)");
        entry(list, "node_sockstat_TCP_orphan",              "TCP孤儿套接字");
        entry(list, "node_sockstat_TCP_tw",                  "TCP TIME_WAIT套接字");
        // -- Windows --
        entry(list, "windows_tcp_connection_failures_total", "TCP连接失败总数");
        entry(list, "windows_tcp_connections_active_total",  "TCP主动连接总数");
        entry(list, "windows_tcp_connections_established",   "TCP已建立连接数");
        entry(list, "windows_tcp_connections_passive_total", "TCP被动连接总数");
        entry(list, "windows_tcp_connections_reset_total",   "TCP连接复位总数");
        entry(list, "windows_tcp_connections_state_count",   "TCP连接状态计数");
        entry(list, "windows_tcp_segments_received_total",   "TCP段接收总数");
        entry(list, "windows_tcp_segments_retransmitted_total","TCP段重传总数");
        entry(list, "windows_tcp_segments_sent_total",       "TCP段发送总数");
        entry(list, "windows_tcp_segments_total",            "TCP段总数");

        // ==================== Windows CPU ====================
        entry(list, "windows_cpu_time_total",              "CPU时间总计");
        entry(list, "windows_cpu_clock_interrupts_total",  "CPU时钟中断总数");
        entry(list, "windows_cpu_interrupts_total",        "CPU硬件中断总数");
        entry(list, "windows_cpu_dpcs_total",              "CPU DPC调用总数");
        entry(list, "windows_cpu_idle_break_events_total", "CPU空闲唤醒次数");
        entry(list, "windows_cpu_core_frequency_mhz",      "CPU核心频率(MHz)");
        entry(list, "windows_cpu_cstate_seconds_total",    "CPU C状态秒数");
        entry(list, "windows_cpu_parking_status",                   "CPU Parking状态");
        entry(list, "windows_cpu_processor_mperf_total",            "CPU MPerf效率比");
        entry(list, "windows_cpu_processor_performance_total",      "CPU性能百分比");
        entry(list, "windows_cpu_processor_privileged_utility_total","CPU特权实用率");
        entry(list, "windows_cpu_processor_rtc_total",              "CPU RTC中断总数");
        entry(list, "windows_cpu_processor_utility_total",          "CPU实用率");
        entry(list, "windows_cpu_logical_processor",                "逻辑处理器数");

        // ==================== Windows Memory ====================
        entry(list, "windows_memory_available_bytes",              "可用内存(字节)");
        entry(list, "windows_memory_cache_bytes",                  "缓存内存(字节)");
        entry(list, "windows_memory_cache_bytes_peak",             "缓存峰值(字节)");
        entry(list, "windows_memory_cache_faults_total",           "缓存错误总数");
        entry(list, "windows_memory_committed_bytes",              "已提交内存(字节)");
        entry(list, "windows_memory_commit_limit",                 "提交内存上限");
        entry(list, "windows_memory_demand_zero_faults_total",     "按需零填充错误总数");
        entry(list, "windows_memory_free_and_zero_page_list_bytes","空闲零页列表(字节)");
        entry(list, "windows_memory_free_system_page_table_entries","空闲系统页表项");
        entry(list, "windows_memory_modified_bytes",               "已修改页(字节)");
        entry(list, "windows_memory_modified_page_list_bytes",     "已修改页列表(字节)");
        entry(list, "windows_memory_page_faults_total",            "页面错误总数");
        entry(list, "windows_memory_physical_total_bytes",         "物理内存总量(字节)");
        entry(list, "windows_memory_physical_free_bytes",          "物理空闲内存(字节)");
        entry(list, "windows_memory_pool_nonpaged_allocs_total",   "非分页池分配总数");
        entry(list, "windows_memory_pool_nonpaged_bytes",          "非分页池(字节)");
        entry(list, "windows_memory_pool_paged_allocs_total",      "分页池分配总数");
        entry(list, "windows_memory_pool_paged_bytes",             "分页池(字节)");
        entry(list, "windows_memory_pool_paged_resident_bytes",    "分页池驻留(字节)");
        entry(list, "windows_memory_process_memory_limit_bytes",   "进程内存上限(字节)");
        entry(list, "windows_memory_standby_cache_bytes",          "备用缓存(字节)");
        entry(list, "windows_memory_standby_cache_core_bytes",     "备用缓存核心(字节)");
        entry(list, "windows_memory_standby_cache_normal_priority_bytes","备用缓存正常优先级(字节)");
        entry(list, "windows_memory_standby_cache_reserve_bytes",  "备用缓存保留(字节)");
        entry(list, "windows_memory_swap_page_operations_total",   "Swap页操作总数");
        entry(list, "windows_memory_swap_page_reads_total",        "Swap页读取总数");
        entry(list, "windows_memory_swap_page_writes_total",       "Swap页写入总数");
        entry(list, "windows_memory_swap_pages_read_total",        "Swap页读取(复数指标)");
        entry(list, "windows_memory_swap_pages_written_total",     "Swap页写入(复数指标)");
        entry(list, "windows_memory_system_cache_resident_bytes",  "系统缓存驻留(字节)");
        entry(list, "windows_memory_system_code_resident_bytes",   "系统代码驻留(字节)");
        entry(list, "windows_memory_system_code_total_bytes",      "系统代码总量(字节)");
        entry(list, "windows_memory_system_driver_resident_bytes", "系统驱动驻留(字节)");
        entry(list, "windows_memory_system_driver_total_bytes",    "系统驱动总量(字节)");
        entry(list, "windows_memory_transition_faults_total",      "转换错误总数");
        entry(list, "windows_memory_transition_pages_repurposed_total","转换页重映射总数");
        entry(list, "windows_memory_write_copies_total",           "写时复制总数");

        // ==================== Windows Disk ====================
        entry(list, "windows_logical_disk_avg_read_requests_queued",       "逻辑磁盘平均读请求队列");
        entry(list, "windows_logical_disk_avg_write_requests_queued",      "逻辑磁盘平均写请求队列");
        entry(list, "windows_logical_disk_free_bytes",                     "逻辑磁盘空闲(字节)");
        entry(list, "windows_logical_disk_idle_seconds_total",             "逻辑磁盘空闲秒数");
        entry(list, "windows_logical_disk_info",                           "逻辑磁盘设备信息");
        entry(list, "windows_logical_disk_queue_length",                   "逻辑磁盘队列长度");
        entry(list, "windows_logical_disk_read_bytes_total",               "逻辑磁盘读取字节");
        entry(list, "windows_logical_disk_read_latency_seconds_total",     "逻辑磁盘读延迟秒数");
        entry(list, "windows_logical_disk_read_write_latency_seconds_total","逻辑磁盘读写延迟秒数");
        entry(list, "windows_logical_disk_read_seconds_total",             "逻辑磁盘读取耗时");
        entry(list, "windows_logical_disk_reads_total",                    "逻辑磁盘读取次数");
        entry(list, "windows_logical_disk_requests_queued",                "逻辑磁盘请求队列");
        entry(list, "windows_logical_disk_size_bytes",                     "逻辑磁盘总容量(字节)");
        entry(list, "windows_logical_disk_split_ios_total",                "逻辑磁盘拆分IO数");
        entry(list, "windows_logical_disk_write_bytes_total",              "逻辑磁盘写入字节");
        entry(list, "windows_logical_disk_write_latency_seconds_total",    "逻辑磁盘写延迟秒数");
        entry(list, "windows_logical_disk_write_seconds_total",            "逻辑磁盘写入耗时");
        entry(list, "windows_logical_disk_writes_total",                   "逻辑磁盘写入次数");
        entry(list, "windows_physical_disk_idle_seconds_total",            "物理磁盘空闲秒数");
        entry(list, "windows_physical_disk_queue_length",                  "物理磁盘队列长度");
        entry(list, "windows_physical_disk_read_bytes_total",              "物理磁盘读取字节");
        entry(list, "windows_physical_disk_read_latency_seconds_total",    "物理磁盘读延迟秒数");
        entry(list, "windows_physical_disk_read_write_latency_seconds_total","物理磁盘读写延迟秒数");
        entry(list, "windows_physical_disk_read_seconds_total",            "物理磁盘读取耗时");
        entry(list, "windows_physical_disk_reads_total",                   "物理磁盘读取次数");
        entry(list, "windows_physical_disk_requests_queued",               "物理磁盘请求队列");
        entry(list, "windows_physical_disk_size_bytes",                    "物理磁盘总容量");
        entry(list, "windows_physical_disk_split_ios_total",               "物理磁盘拆分IO数");
        entry(list, "windows_physical_disk_write_bytes_total",             "物理磁盘写入字节");
        entry(list, "windows_physical_disk_write_latency_seconds_total",   "物理磁盘写延迟秒数");
        entry(list, "windows_physical_disk_write_seconds_total",           "物理磁盘写入耗时");
        entry(list, "windows_physical_disk_writes_total",                  "物理磁盘写入次数");

        // ==================== Windows Network ====================
        entry(list, "windows_net_bytes_received_total",                "网络接收字节总数");
        entry(list, "windows_net_bytes_sent_total",                    "网络发送字节总数");
        entry(list, "windows_net_bytes_total",                         "网络总字节数");
        entry(list, "windows_net_current_bandwidth",                   "网卡带宽(bps)");
        entry(list, "windows_net_current_bandwidth_bytes",             "网卡带宽(字节)");
        entry(list, "windows_net_nic_address_info",                    "网卡地址信息");
        entry(list, "windows_net_nic_info",                            "网卡接口信息");
        entry(list, "windows_net_nic_operation_status",                "网卡运行状态");
        entry(list, "windows_net_output_queue_length",                 "网卡输出队列");
        entry(list, "windows_net_output_queue_length_packets",         "网卡输出队列(包)");
        entry(list, "windows_net_packets_outbound_discarded_total",    "网络发送丢弃");
        entry(list, "windows_net_packets_outbound_errors_total",       "网络发送错误");
        entry(list, "windows_net_packets_received_discarded_total",    "网络接收丢弃");
        entry(list, "windows_net_packets_received_errors_total",       "网络接收错误");
        entry(list, "windows_net_packets_received_total",              "网络接收包总数");
        entry(list, "windows_net_packets_received_unknown_total",      "网络接收未知包总数");
        entry(list, "windows_net_packets_sent_total",                  "网络发送包总数");
        entry(list, "windows_net_packets_total",                       "网络包总数");

        // ==================== Windows Service ====================
        entry(list, "windows_service_info",       "服务信息");
        entry(list, "windows_service_state",      "服务运行状态");
        entry(list, "windows_service_start_mode", "服务启动模式");
        entry(list, "windows_service_process",    "服务进程信息");

        // ==================== Windows OS / System ====================
        entry(list, "windows_os_info",                       "操作系统信息");
        entry(list, "windows_os_hostname",                   "主机名");
        entry(list, "windows_system_boot_time_timestamp",     "系统启动时间戳");
        entry(list, "windows_system_context_switches_total",  "上下文切换总数");
        entry(list, "windows_system_exception_dispatches_total","异常派发总数");
        entry(list, "windows_system_processes",               "进程数");
        entry(list, "windows_system_processes_limit",         "进程数上限");
        entry(list, "windows_system_processor_queue_length",  "处理器队列长度");
        entry(list, "windows_system_system_calls_total",      "系统调用总数");
        entry(list, "windows_system_threads",                 "线程数");

        // ==================== Go Runtime / Process ====================
        entry(list, "go_build_info",                         "Go构建信息");
        entry(list, "go_gc_duration_seconds",                "GC耗时(秒)");
        entry(list, "go_gc_duration_seconds_sum",            "GC总耗时(秒)");
        entry(list, "go_gc_duration_seconds_count",          "GC次数");
        entry(list, "go_gc_gogc_percent",                    "GC触发百分比");
        entry(list, "go_gc_gomemlimit_bytes",                "Go软内存限制(字节)");
        entry(list, "go_goroutines",                         "Goroutine数量");
        entry(list, "go_info",                               "Go环境信息");
        entry(list, "go_memstats_alloc_bytes",               "已分配堆内存(字节)");
        entry(list, "go_memstats_alloc_bytes_total",         "累计分配内存(字节)");
        entry(list, "go_memstats_buck_hash_sys_bytes",       "Bucket哈希表系统内存(字节)");
        entry(list, "go_memstats_frees_total",               "累计释放对象数");
        entry(list, "go_memstats_gc_sys_bytes",              "GC元数据系统内存(字节)");
        entry(list, "go_memstats_heap_alloc_bytes",          "堆内存已分配(字节)");
        entry(list, "go_memstats_heap_idle_bytes",           "堆空闲内存(字节)");
        entry(list, "go_memstats_heap_inuse_bytes",          "堆使用中内存(字节)");
        entry(list, "go_memstats_heap_objects",              "堆内对象数");
        entry(list, "go_memstats_heap_released_bytes",       "堆释放回OS内存(字节)");
        entry(list, "go_memstats_heap_sys_bytes",            "堆系统内存(字节)");
        entry(list, "go_memstats_last_gc_time_seconds",      "上次GC时间戳");
        entry(list, "go_memstats_mallocs_total",             "累计分配对象数");
        entry(list, "go_memstats_mcache_inuse_bytes",        "mcache使用中(字节)");
        entry(list, "go_memstats_mcache_sys_bytes",          "mcache系统内存(字节)");
        entry(list, "go_memstats_mspan_inuse_bytes",         "mspan使用中(字节)");
        entry(list, "go_memstats_mspan_sys_bytes",           "mspan系统内存(字节)");
        entry(list, "go_memstats_next_gc_bytes",             "下次GC触发阈值(字节)");
        entry(list, "go_memstats_other_sys_bytes",           "其他系统内存(字节)");
        entry(list, "go_memstats_stack_inuse_bytes",         "栈使用中(字节)");
        entry(list, "go_memstats_stack_sys_bytes",           "栈系统内存(字节)");
        entry(list, "go_memstats_sys_bytes",                 "Go运行时总系统内存(字节)");
        entry(list, "go_sched_gomaxprocs_threads",           "GOMAXPROCS线程数");
        entry(list, "go_threads",                            "Go运行时线程数");
        entry(list, "process_cpu_seconds_total",             "进程CPU秒数");
        entry(list, "process_max_fds",                       "进程最大文件描述符数");
        entry(list, "process_open_fds",                      "进程打开文件描述符数");
        entry(list, "process_resident_memory_bytes",         "进程常驻内存(字节)");
        entry(list, "process_start_time_seconds",            "进程启动时间戳");
        entry(list, "process_virtual_memory_bytes",          "进程虚拟内存(字节)");
        entry(list, "process_virtual_memory_max_bytes",      "进程虚拟内存上限(字节)");
        entry(list, "process_network_receive_bytes_total",   "进程网络接收字节总数");
        entry(list, "process_network_transmit_bytes_total",  "进程网络发送字节总数");

        // ==================== Windows Process ====================
        entry(list, "windows_process_cpu_time_total",             "进程CPU时间总计");
        entry(list, "windows_process_handles",                    "进程句柄数");
        entry(list, "windows_process_info",                       "进程信息");
        entry(list, "windows_process_io_bytes_total",             "进程IO字节总数");
        entry(list, "windows_process_io_operations_total",        "进程IO操作总数");
        entry(list, "windows_process_page_faults_total",          "进程页面错误总数");
        entry(list, "windows_process_page_file_bytes",            "进程页面文件(字节)");
        entry(list, "windows_process_pool_bytes",                 "进程池内存(字节)");
        entry(list, "windows_process_priority_base",              "进程优先级基数");
        entry(list, "windows_process_private_bytes",              "进程私有内存(字节)");
        entry(list, "windows_process_start_time_seconds_timestamp","进程启动时间戳");
        entry(list, "windows_process_threads",                    "进程线程数");
        entry(list, "windows_process_virtual_bytes",              "进程虚拟内存(字节)");
        entry(list, "windows_process_working_set_bytes",          "进程工作集(字节)");
        entry(list, "windows_process_working_set_peak_bytes",     "进程工作集峰值(字节)");
        entry(list, "windows_process_working_set_private_bytes",  "进程私有工作集(字节)");

        // ==================== Exporter 自监控 ====================
        entry(list, "promhttp_metric_handler_errors_total",      "Prometheus HTTP处理器错误总数");
        entry(list, "promhttp_metric_handler_requests_in_flight", "Prometheus HTTP处理中请求数");
        entry(list, "promhttp_metric_handler_requests_total",    "Prometheus HTTP处理器请求总数");
        entry(list, "windows_exporter_build_info",               "Exporter构建信息");
        entry(list, "windows_exporter_collector_duration_seconds","Exporter采集耗时(秒)");
        entry(list, "windows_exporter_collector_success",        "Exporter采集成功状态");
        entry(list, "windows_exporter_collector_timeout",        "Exporter采集超时状态");
        entry(list, "windows_exporter_scrape_duration_seconds",  "Exporter抓取耗时(秒)");

        ENTRIES = Collections.unmodifiableList(list);
    }

    // =============================================
    // 5. 自动构建的快速查找表（私有，无需手改）
    // =============================================

    /** metricName → MetricEntry 精确匹配。 */
    private static final Map<String, MetricEntry> NAME_TO_ENTRY;

    static {
        NAME_TO_ENTRY = ENTRIES.stream()
                .collect(Collectors.toMap(
                        MetricEntry::metricName,
                        e -> e,
                        (a, b) -> a,
                        LinkedHashMap::new));
    }

    // =============================================
    // 6. 公共 API
    // =============================================

    /**
     * 根据指标名获取中文翻译。无匹配时返回原指标名。
     */
    public static String getChineseName(String metricName) {
        MetricEntry entry = NAME_TO_ENTRY.get(metricName);
        return entry != null ? entry.chineseName() : metricName;
    }

    /**
     * 推断指标分类：优先精确匹配，命中前缀规则则按规则归类，否则返回 OTHER。
     *
     * @return 分类 key（如 "cpu"）
     */
    public static String inferCategory(String metricName) {
        // 精确匹配优先
        MetricEntry entry = NAME_TO_ENTRY.get(metricName);
        if (entry != null) {
            return entry.category().getKey();
        }
        // 前缀推断兜底
        return inferCategoryByPrefix(metricName).getKey();
    }

    /**
     * 根据分类 key 获取中文显示名。
     */
    public static String getCategoryChineseName(String categoryKey) {
        return MetricCategory.fromKey(categoryKey).getChineseName();
    }
}

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
        CONNECTION("connection", "连接"),
        QUERY("query", "查询"),
        INNODB("innodb", "InnoDB"),
        THREAD("thread", "线程"),
        TABLE_OP("table_op", "表操作"),
        HANDLER("handler", "处理器"),
        CONFIG("config", "配置"),
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
            return List.of("cpu", "memory", "disk", "network", "service", "port", "process", "system",
                    "connection", "query", "innodb", "thread", "table_op", "handler", "config",
                    "runtime", "other");
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

        // MySQL 连接相关（具体子前缀优先）
        PREFIX_RULES.put("mysql_global_status_aborted_",      MetricCategory.CONNECTION);
        PREFIX_RULES.put("mysql_global_status_connection_",   MetricCategory.CONNECTION);
        // MySQL 线程
        PREFIX_RULES.put("mysql_global_status_threads_",      MetricCategory.THREAD);
        // MySQL 网络传输
        PREFIX_RULES.put("mysql_global_status_bytes_",         MetricCategory.NETWORK);
        // MySQL 查询
        PREFIX_RULES.put("mysql_global_status_commands_",      MetricCategory.QUERY);
        PREFIX_RULES.put("mysql_global_status_slow_queries",   MetricCategory.QUERY);
        PREFIX_RULES.put("mysql_global_status_queries",        MetricCategory.QUERY);
        PREFIX_RULES.put("mysql_global_status_questions",      MetricCategory.QUERY);
        PREFIX_RULES.put("mysql_global_status_select_",        MetricCategory.QUERY);
        PREFIX_RULES.put("mysql_global_status_sort_",          MetricCategory.QUERY);
        PREFIX_RULES.put("mysql_global_status_com_",            MetricCategory.QUERY);
        PREFIX_RULES.put("mysql_global_status_max_used_connections", MetricCategory.CONNECTION);
        // MySQL 处理器
        PREFIX_RULES.put("mysql_global_status_handler_",       MetricCategory.HANDLER);
        PREFIX_RULES.put("mysql_global_status_created_tmp_",   MetricCategory.HANDLER);
        // MySQL 表操作
        PREFIX_RULES.put("mysql_global_status_table_open_cache_", MetricCategory.TABLE_OP);
        PREFIX_RULES.put("mysql_global_status_table_locks_",   MetricCategory.TABLE_OP);
        PREFIX_RULES.put("mysql_global_status_open_tables",     MetricCategory.TABLE_OP);
        PREFIX_RULES.put("mysql_global_status_opened_tables",   MetricCategory.TABLE_OP);
        // MySQL InnoDB (大量子前缀)
        PREFIX_RULES.put("mysql_global_status_innodb_buffer_pool_", MetricCategory.INNODB);
        PREFIX_RULES.put("mysql_global_status_innodb_rows_",    MetricCategory.INNODB);
        PREFIX_RULES.put("mysql_global_status_innodb_data_",    MetricCategory.INNODB);
        PREFIX_RULES.put("mysql_global_status_innodb_os_log_",  MetricCategory.INNODB);
        PREFIX_RULES.put("mysql_global_status_innodb_log_",     MetricCategory.INNODB);
        PREFIX_RULES.put("mysql_global_status_innodb_pages_",   MetricCategory.INNODB);
        PREFIX_RULES.put("mysql_global_status_innodb_deadlocks",MetricCategory.INNODB);
        PREFIX_RULES.put("mysql_global_status_innodb_dblwr_",   MetricCategory.INNODB);
        PREFIX_RULES.put("mysql_global_status_innodb_redo_log_",MetricCategory.INNODB);
        PREFIX_RULES.put("mysql_global_status_innodb_num_open_files",MetricCategory.INNODB);
        PREFIX_RULES.put("mysql_global_status_innodb_",         MetricCategory.INNODB);
        // MySQL Buffer Pool 8.0+ (无 innodb_ 前缀)
        PREFIX_RULES.put("mysql_global_status_buffer_pool_",    MetricCategory.INNODB);
        // MySQL TC Log (InnoDB 事务协调器)
        PREFIX_RULES.put("mysql_global_status_tc_log_",         MetricCategory.INNODB);
        // MySQL X Protocol 状态
        PREFIX_RULES.put("mysql_global_status_mysqlx_",         MetricCategory.NETWORK);
        // MySQL SSL
        PREFIX_RULES.put("mysql_global_status_ssl_",            MetricCategory.CONNECTION);
        // MySQL Binlog 状态
        PREFIX_RULES.put("mysql_global_status_binlog_",         MetricCategory.QUERY);
        // MySQL Key 缓存 (MyISAM)
        PREFIX_RULES.put("mysql_global_status_key_",            MetricCategory.TABLE_OP);
        // MySQL 事务隔离级别
        PREFIX_RULES.put("mysql_transaction_isolation",         MetricCategory.CONFIG);
        // MySQL 状态兜底 (放在 innodb 具体规则之后)
        PREFIX_RULES.put("mysql_global_status_",                 MetricCategory.OTHER);
        // MySQL 配置变量
        PREFIX_RULES.put("mysql_global_variables_",              MetricCategory.CONFIG);
        // MySQL Exporter 自监控
        PREFIX_RULES.put("mysql_exporter_",                     MetricCategory.RUNTIME);
        PREFIX_RULES.put("mysqld_exporter_",                   MetricCategory.RUNTIME);
        // MySQL 存活与版本
        PREFIX_RULES.put("mysql_up",                            MetricCategory.SYSTEM);
        PREFIX_RULES.put("mysql_version_",                      MetricCategory.SYSTEM);
        PREFIX_RULES.put("mysql_info_schema_",                  MetricCategory.INNODB);

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

        // ========== CPU ==========
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

        // ========== Memory ==========
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

        // ========== Disk IO ==========
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
        entry(list, "node_disk_filesystem_info",               "磁盘文件系统信息");

        // ========== Filesystem ==========
        entry(list, "node_filesystem_size_bytes",    "文件系统总容量(字节)");
        entry(list, "node_filesystem_free_bytes",    "文件系统空闲(字节)");
        entry(list, "node_filesystem_avail_bytes",   "文件系统可用(字节)");
        entry(list, "node_filesystem_files",         "文件系统inode总数");
        entry(list, "node_filesystem_files_free",    "文件系统inode空闲");
        entry(list, "node_filesystem_readonly",      "文件系统只读");
        entry(list, "node_filesystem_device_error",  "文件系统设备错误");
        entry(list, "node_filesystem_mount_info",    "文件系统挂载信息");
        entry(list, "node_filesystem_purgeable_bytes","文件系统可清除(字节)");

        // ========== Network ==========
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

        // ========== Netstat ==========
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

        // ========== System ==========
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

        // ========== VMStat / Pressure ==========
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

        // ========== Sockstat / Softnet ==========
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

        // ========== Port (TCP 连接与传输) ==========
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
        entry(list, "node_listening_port",                   "Linux监听端口");
        entry(list, "windows_listening_port",                "Windows监听端口");

        // ========== Windows CPU ==========
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

        // ========== Windows Memory ==========
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

        // ========== Windows Disk ==========
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

        // ========== Windows Network ==========
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

        // ========== Windows Service ==========
        entry(list, "windows_service_info",       "服务信息");
        entry(list, "windows_service_state",      "服务运行状态");
        entry(list, "windows_service_start_mode", "服务启动模式");
        entry(list, "windows_service_process",    "服务进程信息");

        // ========== Windows OS / System ==========
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

        // ========== Go Runtime / Process ==========
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

        // ========== Windows Process ==========
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

        // ========== MySQL 连接 ==========
        entry(list, "mysql_global_status_threads_connected",       "当前连接数");
        entry(list, "mysql_global_status_threads_running",         "活跃线程数");
        entry(list, "mysql_global_status_threads_cached",          "缓存线程数");
        entry(list, "mysql_global_status_threads_created",         "累计创建线程数");
        entry(list, "mysql_global_status_max_used_connections",    "历史最大连接数");
        entry(list, "mysql_global_status_max_used_connections_time","历史最大连接时间戳");
        entry(list, "mysql_global_status_aborted_clients",         "客户端异常断开数");
        entry(list, "mysql_global_status_aborted_connects",        "连接失败数");
        entry(list, "mysql_global_status_connection_errors_total", "连接错误总数");
        entry(list, "mysql_global_status_connection_errors_internal","内部连接错误数");
        entry(list, "mysql_global_status_connection_errors_max_connections","超最大连接拒绝数");
        entry(list, "mysql_global_status_connection_errors_accept","连接accept错误数");
        entry(list, "mysql_global_status_connection_errors_peer_address","地址解析错误数");
        entry(list, "mysql_global_status_connection_errors_select","连接select错误数");
        entry(list, "mysql_global_status_connection_errors_tcpwrap","TCP包装器错误数");
        entry(list, "mysql_global_status_connections",              "累计连接尝试数");

        // ========== MySQL 查询 ==========
        entry(list, "mysql_global_status_queries",                  "查询总数");
        entry(list, "mysql_global_status_questions",                "语句执行总数");
        entry(list, "mysql_global_status_slow_queries",             "慢查询数");
        entry(list, "mysql_global_status_com_select",               "SELECT 次数");
        entry(list, "mysql_global_status_com_insert",               "INSERT 次数");
        entry(list, "mysql_global_status_com_update",               "UPDATE 次数");
        entry(list, "mysql_global_status_com_delete",               "DELETE 次数");
        entry(list, "mysql_global_status_com_replace",              "REPLACE 次数");
        entry(list, "mysql_global_status_com_commit",               "COMMIT 次数");
        entry(list, "mysql_global_status_com_rollback",             "ROLLBACK 次数");
        entry(list, "mysql_global_status_com_begin",                "BEGIN 次数");
        entry(list, "mysql_global_status_com_set_option",           "SET OPTION 次数");
        entry(list, "mysql_global_status_com_show_status",          "SHOW STATUS 次数");
        entry(list, "mysql_global_status_com_show_variables",       "SHOW VARIABLES 次数");
        entry(list, "mysql_global_status_com_show_databases",       "SHOW DATABASES 次数");
        entry(list, "mysql_global_status_com_show_tables",          "SHOW TABLES 次数");
        entry(list, "mysql_global_status_com_show_fields",          "SHOW FIELDS 次数");
        entry(list, "mysql_global_status_com_show_keys",            "SHOW KEYS 次数");
        entry(list, "mysql_global_status_select_full_join",         "全表 JOIN 次数");
        entry(list, "mysql_global_status_select_full_range_join",   "全范围 JOIN 次数");
        entry(list, "mysql_global_status_select_range",             "范围扫描次数");
        entry(list, "mysql_global_status_select_range_check",       "范围扫描检查次数");
        entry(list, "mysql_global_status_select_scan",              "全表扫描次数");
        entry(list, "mysql_global_status_sort_merge_passes",        "排序合并次数");
        entry(list, "mysql_global_status_sort_range",               "范围排序次数");
        entry(list, "mysql_global_status_sort_rows",                "排序行数");
        entry(list, "mysql_global_status_sort_scan",                "扫描排序次数");
        entry(list, "mysql_global_status_bytes_received",           "接收字节数");
        entry(list, "mysql_global_status_bytes_sent",               "发送字节数");

        // ========== MySQL InnoDB ==========
        entry(list, "mysql_global_status_innodb_buffer_pool_read_requests",    "缓冲池读请求数");
        entry(list, "mysql_global_status_innodb_buffer_pool_reads",             "缓冲池物理读次数");
        entry(list, "mysql_global_status_innodb_buffer_pool_read_ahead",        "缓冲池预读次数");
        entry(list, "mysql_global_status_innodb_buffer_pool_read_ahead_evicted","缓冲池预读淘汰数");
        entry(list, "mysql_global_status_innodb_buffer_pool_read_ahead_rnd",   "缓冲池随机预读次数");
        entry(list, "mysql_global_status_innodb_buffer_pool_pages_total",       "缓冲池总页数");
        entry(list, "mysql_global_status_innodb_buffer_pool_pages_data",        "缓冲池数据页数");
        entry(list, "mysql_global_status_innodb_buffer_pool_pages_dirty",       "缓冲池脏页数");
        entry(list, "mysql_global_status_innodb_buffer_pool_pages_free",        "缓冲池空闲页数");
        entry(list, "mysql_global_status_innodb_buffer_pool_pages_flushed",     "缓冲池刷新页数");
        entry(list, "mysql_global_status_innodb_buffer_pool_pages_misc",        "缓冲池杂项页数");
        entry(list, "mysql_global_status_innodb_buffer_pool_wait_free",         "缓冲池等待空闲次数");
        entry(list, "mysql_global_status_innodb_buffer_pool_write_requests",    "缓冲池写请求数");
        entry(list, "mysql_global_status_innodb_rows_inserted",  "InnoDB 插入行数");
        entry(list, "mysql_global_status_innodb_rows_read",       "InnoDB 读取行数");
        entry(list, "mysql_global_status_innodb_rows_updated",    "InnoDB 更新行数");
        entry(list, "mysql_global_status_innodb_rows_deleted",    "InnoDB 删除行数");
        entry(list, "mysql_global_status_innodb_data_reads",      "InnoDB 数据文件读次数");
        entry(list, "mysql_global_status_innodb_data_writes",     "InnoDB 数据文件写次数");
        entry(list, "mysql_global_status_innodb_data_read",        "InnoDB 数据读取字节");
        entry(list, "mysql_global_status_innodb_data_written",     "InnoDB 数据写入字节");
        entry(list, "mysql_global_status_innodb_data_fsyncs",      "InnoDB 数据fsync次数");
        entry(list, "mysql_global_status_innodb_data_pending_reads","InnoDB 挂起读操作");
        entry(list, "mysql_global_status_innodb_data_pending_writes","InnoDB 挂起写操作");
        entry(list, "mysql_global_status_innodb_data_pending_fsyncs","InnoDB 挂起fsync");
        entry(list, "mysql_global_status_innodb_log_waits",        "InnoDB 日志等待次数");
        entry(list, "mysql_global_status_innodb_log_writes",        "InnoDB 日志写次数");
        entry(list, "mysql_global_status_innodb_log_write_requests","InnoDB 日志写请求数");
        entry(list, "mysql_global_status_innodb_os_log_written",    "InnoDB 日志写入字节");
        entry(list, "mysql_global_status_innodb_os_log_fsyncs",     "InnoDB 日志fsync次数");
        entry(list, "mysql_global_status_innodb_os_log_pending_fsyncs","InnoDB 日志挂起fsync");
        entry(list, "mysql_global_status_innodb_os_log_pending_writes","InnoDB 日志挂起写");
        entry(list, "mysql_global_status_innodb_pages_created",     "InnoDB 创建页数");
        entry(list, "mysql_global_status_innodb_pages_read",        "InnoDB 读取页数");
        entry(list, "mysql_global_status_innodb_pages_written",     "InnoDB 写入页数");
        entry(list, "mysql_global_status_innodb_deadlocks",         "死锁次数");
        entry(list, "mysql_global_status_innodb_row_lock_current_waits","当前行锁等待数");
        entry(list, "mysql_global_status_innodb_row_lock_time",     "行锁等待总时间(ms)");
        entry(list, "mysql_global_status_innodb_row_lock_time_avg",  "行锁平均等待时间(ms)");
        entry(list, "mysql_global_status_innodb_row_lock_time_max",  "行锁最大等待时间(ms)");
        entry(list, "mysql_global_status_commands_total",             "命令执行总数");
        entry(list, "mysql_global_status_innodb_buffer_pool_bytes_data",     "缓冲池数据字节数");
        entry(list, "mysql_global_status_innodb_buffer_pool_bytes_dirty",    "缓冲池脏页字节数");
        entry(list, "mysql_global_status_innodb_buffer_pool_resize_status_code",   "缓冲池调整状态码");
        entry(list, "mysql_global_status_innodb_buffer_pool_resize_status_progress","缓冲池调整进度");
        entry(list, "mysql_global_status_innodb_dblwr_pages_written",    "双写页写入数");
        entry(list, "mysql_global_status_innodb_dblwr_writes",           "双写操作次数");
        entry(list, "mysql_global_status_innodb_num_open_files",         "InnoDB打开文件数");
        entry(list, "mysql_global_status_innodb_redo_log_capacity_resized",       "重做日志调整后容量(字节)");
        entry(list, "mysql_global_status_innodb_redo_log_checkpoint_lsn",         "重做日志检查点LSN");
        entry(list, "mysql_global_status_innodb_redo_log_current_lsn",            "重做日志当前LSN");
        entry(list, "mysql_global_status_innodb_redo_log_enabled",                "重做日志启用状态");
        entry(list, "mysql_global_status_innodb_redo_log_flushed_to_disk_lsn",    "重做日志已刷盘LSN");
        entry(list, "mysql_global_status_innodb_redo_log_logical_size",           "重做日志逻辑大小(字节)");
        entry(list, "mysql_global_status_innodb_redo_log_physical_size",          "重做日志物理大小(字节)");
        entry(list, "mysql_global_status_innodb_redo_log_read_only",              "重做日志只读状态");
        entry(list, "mysql_global_status_innodb_redo_log_uuid",                   "重做日志UUID");
        entry(list, "mysql_global_status_innodb_row_lock_waits",           "行锁等待次数");
        entry(list, "mysql_global_status_innodb_row_ops_total",            "行操作总数");
        entry(list, "mysql_global_status_innodb_sampled_pages_read",       "采样页读取数");
        entry(list, "mysql_global_status_innodb_sampled_pages_skipped",    "采样页跳过数");
        entry(list, "mysql_global_status_innodb_system_rows_deleted",      "系统表DELETE行数");
        entry(list, "mysql_global_status_innodb_system_rows_inserted",    "系统表INSERT行数");
        entry(list, "mysql_global_status_innodb_system_rows_read",         "系统表READ行数");
        entry(list, "mysql_global_status_innodb_system_rows_updated",      "系统表UPDATE行数");
        entry(list, "mysql_global_status_innodb_truncated_status_writes",  "状态截断写入次数");
        entry(list, "mysql_global_status_innodb_undo_tablespaces_active",   "活跃Undo表空间数");
        entry(list, "mysql_global_status_innodb_undo_tablespaces_explicit", "显式创建Undo表空间数");
        entry(list, "mysql_global_status_innodb_undo_tablespaces_implicit", "隐式创建Undo表空间数");
        entry(list, "mysql_global_status_innodb_undo_tablespaces_total",    "Undo表空间总数");

        // ========== MySQL InnoDB 压缩 (information_schema) ==========
        entry(list, "mysql_info_schema_innodb_cmp_compress_ops_ok_total",           "压缩操作成功次数");
        entry(list, "mysql_info_schema_innodb_cmp_compress_ops_total",               "压缩操作总次数");
        entry(list, "mysql_info_schema_innodb_cmp_compress_time_seconds_total",      "压缩操作耗时(秒)");
        entry(list, "mysql_info_schema_innodb_cmp_uncompress_ops_total",             "解压操作总次数");
        entry(list, "mysql_info_schema_innodb_cmp_uncompress_time_seconds_total",    "解压操作耗时(秒)");
        entry(list, "mysql_info_schema_innodb_cmpmem_pages_free_total",              "压缩页空闲数");
        entry(list, "mysql_info_schema_innodb_cmpmem_pages_used_total",              "压缩页使用数");
        entry(list, "mysql_info_schema_innodb_cmpmem_relocation_ops_total",          "压缩页重分配操作数");
        entry(list, "mysql_info_schema_innodb_cmpmem_relocation_time_seconds_total", "压缩页重分配耗时(秒)");

        // ========== MySQL 表操作 ==========
        entry(list, "mysql_global_status_open_tables",              "当前打开表数");
        entry(list, "mysql_global_status_opened_tables",            "累计打开表数");
        entry(list, "mysql_global_status_table_locks_immediate",    "立即获得表锁次数");
        entry(list, "mysql_global_status_table_locks_waited",       "等待表锁次数");
        entry(list, "mysql_global_status_table_open_cache_hits",    "表缓存命中数");
        entry(list, "mysql_global_status_table_open_cache_misses",   "表缓存未命中数");
        entry(list, "mysql_global_status_table_open_cache_overflows","表缓存溢出数");

        // ========== MySQL 处理器 ==========
        entry(list, "mysql_global_status_handler_commit",           "内部 COMMIT 次数");
        entry(list, "mysql_global_status_handler_rollback",         "内部 ROLLBACK 次数");
        entry(list, "mysql_global_status_handler_read_first",       "读第一个条目次数");
        entry(list, "mysql_global_status_handler_read_key",         "通过键读取次数");
        entry(list, "mysql_global_status_handler_read_next",        "读下一个条目次数");
        entry(list, "mysql_global_status_handler_read_prev",        "读上一个条目次数");
        entry(list, "mysql_global_status_handler_read_rnd",         "随机读取次数");
        entry(list, "mysql_global_status_handler_read_rnd_next",    "随机读下一个次数");
        entry(list, "mysql_global_status_handler_write",            "内部写次数");
        entry(list, "mysql_global_status_handler_update",           "内部更新次数");
        entry(list, "mysql_global_status_handler_delete",           "内部删除次数");
        entry(list, "mysql_global_status_handler_discover",         "表发现次数");
        entry(list, "mysql_global_status_created_tmp_tables",       "创建临时表数");
        entry(list, "mysql_global_status_created_tmp_disk_tables",  "创建磁盘临时表数");
        entry(list, "mysql_global_status_created_tmp_files",        "创建临时文件数");

        // ========== MySQL 配置 (通用) ==========
        entry(list, "mysql_global_variables_activate_all_roles_on_login",             "登录时激活所有角色");
        entry(list, "mysql_global_variables_admin_port",                              "管理端口");
        entry(list, "mysql_global_variables_auto_generate_certs",                     "自动生成证书");
        entry(list, "mysql_global_variables_auto_increment_increment",                "自增步长");
        entry(list, "mysql_global_variables_auto_increment_offset",                   "自增偏移量");
        entry(list, "mysql_global_variables_autocommit",                              "自动提交");
        entry(list, "mysql_global_variables_automatic_sp_privileges",                 "自动存储过程权限");
        entry(list, "mysql_global_variables_avoid_temporal_upgrade",                  "避免时间类型升级");
        entry(list, "mysql_global_variables_back_log",                                "连接等待队列");
        entry(list, "mysql_global_variables_big_tables",                              "大表支持");
        entry(list, "mysql_global_variables_binlog_cache_size",                       "Binlog缓存大小");
        entry(list, "mysql_global_variables_binlog_direct_non_transactional_updates", "Binlog直接非事务更新");
        entry(list, "mysql_global_variables_binlog_encryption",                       "Binlog加密");
        entry(list, "mysql_global_variables_binlog_expire_logs_auto_purge",           "Binlog自动清理");
        entry(list, "mysql_global_variables_binlog_expire_logs_seconds",              "Binlog过期时间(秒)");
        entry(list, "mysql_global_variables_binlog_group_commit_sync_delay",          "Binlog组提交同步延迟");
        entry(list, "mysql_global_variables_binlog_group_commit_sync_no_delay_count", "Binlog组提交无延迟计数");
        entry(list, "mysql_global_variables_binlog_gtid_simple_recovery",             "Binlog GTID简单恢复");
        entry(list, "mysql_global_variables_binlog_max_flush_queue_time",             "Binlog最大刷新队列时间");
        entry(list, "mysql_global_variables_binlog_order_commits",                    "Binlog顺序提交");
        entry(list, "mysql_global_variables_binlog_rotate_encryption_master_key_at_startup","Binlog启动时轮换加密主密钥");
        entry(list, "mysql_global_variables_binlog_row_event_max_size",               "Binlog行事件最大大小");
        entry(list, "mysql_global_variables_binlog_rows_query_log_events",            "Binlog记录行查询事件");
        entry(list, "mysql_global_variables_binlog_stmt_cache_size",                  "Binlog语句缓存大小");
        entry(list, "mysql_global_variables_binlog_transaction_compression",          "Binlog事务压缩");
        entry(list, "mysql_global_variables_binlog_transaction_compression_level_zstd","Binlog事务Zstd压缩级别");
        entry(list, "mysql_global_variables_binlog_transaction_dependency_history_size","Binlog事务依赖历史大小");
        entry(list, "mysql_global_variables_bulk_insert_buffer_size",                 "批量插入缓冲区大小");
        entry(list, "mysql_global_variables_caching_sha2_password_auto_generate_rsa_keys","SHA2密码自动生成RSA密钥");
        entry(list, "mysql_global_variables_caching_sha2_password_digest_rounds",     "SHA2密码摘要轮次");
        entry(list, "mysql_global_variables_check_proxy_users",                       "检查代理用户");
        entry(list, "mysql_global_variables_connect_timeout",                          "连接超时(秒)");
        entry(list, "mysql_global_variables_connection_memory_chunk_size",             "连接内存块大小");
        entry(list, "mysql_global_variables_connection_memory_limit",                  "连接内存限制");
        entry(list, "mysql_global_variables_core_file",                                "Core文件");
        entry(list, "mysql_global_variables_create_admin_listener_thread",             "创建管理监听线程");
        entry(list, "mysql_global_variables_cte_max_recursion_depth",                  "CTE最大递归深度");
        entry(list, "mysql_global_variables_default_password_lifetime",                "默认密码有效期");
        entry(list, "mysql_global_variables_default_table_encryption",                 "默认表加密");
        entry(list, "mysql_global_variables_default_week_format",                      "默认星期格式");
        entry(list, "mysql_global_variables_delay_key_write",                          "延迟键写入");
        entry(list, "mysql_global_variables_delayed_insert_limit",                     "延迟插入限制");
        entry(list, "mysql_global_variables_delayed_insert_timeout",                   "延迟插入超时");
        entry(list, "mysql_global_variables_delayed_queue_size",                       "延迟插入队列大小");
        entry(list, "mysql_global_variables_disconnect_on_expired_password",           "密码过期断开连接");
        entry(list, "mysql_global_variables_div_precision_increment",                  "除法精度增量");
        entry(list, "mysql_global_variables_end_markers_in_json",                      "JSON结束标记");
        entry(list, "mysql_global_variables_enforce_gtid_consistency",                 "强制GTID一致性");
        entry(list, "mysql_global_variables_eq_range_index_dive_limit",                "等值范围索引下探限制");
        entry(list, "mysql_global_variables_event_scheduler",                          "事件调度器");
        entry(list, "mysql_global_variables_expire_logs_days",                         "日志过期天数");
        entry(list, "mysql_global_variables_explicit_defaults_for_timestamp",          "时间戳显式默认值");
        entry(list, "mysql_global_variables_flush",                                    "刷写标记");
        entry(list, "mysql_global_variables_flush_time",                               "刷写时间间隔");
        entry(list, "mysql_global_variables_foreign_key_checks",                       "外键检查");
        entry(list, "mysql_global_variables_ft_max_word_len",                          "全文最大词长度");
        entry(list, "mysql_global_variables_ft_min_word_len",                          "全文最小词长度");
        entry(list, "mysql_global_variables_ft_query_expansion_limit",                 "全文查询扩展限制");
        entry(list, "mysql_global_variables_general_log",                              "通用日志开关");
        entry(list, "mysql_global_variables_generated_random_password_length",         "生成随机密码长度");
        entry(list, "mysql_global_variables_global_connection_memory_limit",            "全局连接内存限制");
        entry(list, "mysql_global_variables_global_connection_memory_tracking",         "全局连接内存追踪");
        entry(list, "mysql_global_variables_group_concat_max_len",                      "GROUP_CONCAT最大长度");
        entry(list, "mysql_global_variables_gtid_executed_compression_period",          "GTID执行压缩周期");
        entry(list, "mysql_global_variables_gtid_mode",                                 "GTID模式");
        entry(list, "mysql_global_variables_have_compress",                             "支持压缩");
        entry(list, "mysql_global_variables_have_dynamic_loading",                      "支持动态加载");
        entry(list, "mysql_global_variables_have_geometry",                             "支持空间类型");
        entry(list, "mysql_global_variables_have_openssl",                              "支持OpenSSL");
        entry(list, "mysql_global_variables_have_profiling",                            "支持性能分析");
        entry(list, "mysql_global_variables_have_query_cache",                          "支持查询缓存");
        entry(list, "mysql_global_variables_have_rtree_keys",                           "支持R树索引");
        entry(list, "mysql_global_variables_have_ssl",                                  "支持SSL");
        entry(list, "mysql_global_variables_have_statement_timeout",                    "支持语句超时");
        entry(list, "mysql_global_variables_have_symlink",                              "支持符号链接");
        entry(list, "mysql_global_variables_histogram_generation_max_mem_size",         "直方图生成最大内存");
        entry(list, "mysql_global_variables_host_cache_size",                           "主机缓存大小");
        entry(list, "mysql_global_variables_information_schema_stats_expiry",            "信息模式统计过期时间(秒)");
        entry(list, "mysql_global_variables_join_buffer_size",                           "JOIN缓冲区大小");
        entry(list, "mysql_global_variables_keep_files_on_create",                       "CREATE时保留文件");
        entry(list, "mysql_global_variables_key_buffer_size",                            "键缓冲区大小");
        entry(list, "mysql_global_variables_key_cache_age_threshold",                    "键缓存老化阈值");
        entry(list, "mysql_global_variables_key_cache_block_size",                       "键缓存块大小");
        entry(list, "mysql_global_variables_key_cache_division_limit",                   "键缓存分界线");
        entry(list, "mysql_global_variables_keyring_operations",                         "密钥环操作开关");
        entry(list, "mysql_global_variables_large_files_support",                        "大文件支持");
        entry(list, "mysql_global_variables_large_page_size",                            "大页大小");
        entry(list, "mysql_global_variables_large_pages",                                "大页开关");
        entry(list, "mysql_global_variables_local_infile",                               "本地文件加载");
        entry(list, "mysql_global_variables_lock_wait_timeout",                          "锁等待超时(秒)");
        entry(list, "mysql_global_variables_locked_in_memory",                           "锁定在内存中");
        entry(list, "mysql_global_variables_log_bin",                                    "启用二进制日志");
        entry(list, "mysql_global_variables_log_bin_trust_function_creators",            "信任函数创建者");
        entry(list, "mysql_global_variables_log_bin_use_v1_row_events",                  "使用V1行事件");
        entry(list, "mysql_global_variables_log_error_verbosity",                        "错误日志详细度");
        entry(list, "mysql_global_variables_log_queries_not_using_indexes",              "记录未使用索引查询");
        entry(list, "mysql_global_variables_log_raw",                                    "记录原始语句");
        entry(list, "mysql_global_variables_log_replica_updates",                        "记录从库更新");
        entry(list, "mysql_global_variables_log_slave_updates",                          "记录从库更新(旧)");
        entry(list, "mysql_global_variables_log_slow_admin_statements",                  "慢查询记录管理语句");
        entry(list, "mysql_global_variables_log_slow_extra",                             "慢查询额外信息");
        entry(list, "mysql_global_variables_log_slow_replica_statements",                "记录慢从库语句");
        entry(list, "mysql_global_variables_log_slow_slave_statements",                  "记录慢从库语句(旧)");
        entry(list, "mysql_global_variables_log_statements_unsafe_for_binlog",           "记录不安全语句");
        entry(list, "mysql_global_variables_log_throttle_queries_not_using_indexes",     "限流记录未使用索引查询");
        entry(list, "mysql_global_variables_long_query_time",                            "慢查询阈值(秒)");
        entry(list, "mysql_global_variables_low_priority_updates",                       "低优先级更新");
        entry(list, "mysql_global_variables_lower_case_file_system",                     "文件系统大小写");
        entry(list, "mysql_global_variables_lower_case_table_names",                     "表名大小写");
        entry(list, "mysql_global_variables_master_verify_checksum",                     "主库校验和验证");
        entry(list, "mysql_global_variables_max_allowed_packet",                         "最大允许数据包");
        entry(list, "mysql_global_variables_max_binlog_cache_size",                      "最大Binlog缓存大小");
        entry(list, "mysql_global_variables_max_binlog_size",                            "最大Binlog大小");
        entry(list, "mysql_global_variables_max_binlog_stmt_cache_size",                 "最大Binlog语句缓存大小");
        entry(list, "mysql_global_variables_max_connect_errors",                         "最大连接错误数");
        entry(list, "mysql_global_variables_max_connections",                            "最大连接数配置");
        entry(list, "mysql_global_variables_max_delayed_threads",                        "最大延迟插入线程数");
        entry(list, "mysql_global_variables_max_digest_length",                          "最大摘要长度");
        entry(list, "mysql_global_variables_max_error_count",                            "最大错误计数");
        entry(list, "mysql_global_variables_max_execution_time",                         "最大执行时间");
        entry(list, "mysql_global_variables_max_heap_table_size",                        "最大堆表大小");
        entry(list, "mysql_global_variables_max_insert_delayed_threads",                 "最大延迟插入线程数");
        entry(list, "mysql_global_variables_max_join_size",                              "最大JOIN大小");
        entry(list, "mysql_global_variables_max_length_for_sort_data",                   "排序数据最大长度");
        entry(list, "mysql_global_variables_max_points_in_geometry",                     "几何类型最大点数");
        entry(list, "mysql_global_variables_max_prepared_stmt_count",                    "最大预处理语句数");
        entry(list, "mysql_global_variables_max_relay_log_size",                         "最大中继日志大小");
        entry(list, "mysql_global_variables_max_seeks_for_key",                          "键查找最大搜索次数");
        entry(list, "mysql_global_variables_max_sort_length",                            "最大排序长度");
        entry(list, "mysql_global_variables_max_sp_recursion_depth",                     "最大存储过程递归深度");
        entry(list, "mysql_global_variables_max_user_connections",                       "最大用户连接数");
        entry(list, "mysql_global_variables_max_write_lock_count",                       "最大写锁计数");
        entry(list, "mysql_global_variables_min_examined_row_limit",                     "最小检查行限制");
        entry(list, "mysql_global_variables_myisam_data_pointer_size",                   "MyISAM数据指针大小");
        entry(list, "mysql_global_variables_myisam_max_sort_file_size",                  "MyISAM最大排序文件大小");
        entry(list, "mysql_global_variables_myisam_mmap_size",                           "MyISAM内存映射大小");
        entry(list, "mysql_global_variables_myisam_recover_options",                     "MyISAM恢复选项");
        entry(list, "mysql_global_variables_myisam_sort_buffer_size",                    "MyISAM排序缓冲区大小");
        entry(list, "mysql_global_variables_myisam_use_mmap",                            "MyISAM使用内存映射");
        entry(list, "mysql_global_variables_mysql_native_password_proxy_users",          "原生密码代理用户");
        entry(list, "mysql_global_variables_net_buffer_length",                          "网络缓冲区长度");
        entry(list, "mysql_global_variables_net_read_timeout",                           "网络读超时(秒)");
        entry(list, "mysql_global_variables_net_retry_count",                            "网络重试次数");
        entry(list, "mysql_global_variables_net_write_timeout",                          "网络写超时(秒)");
        entry(list, "mysql_global_variables_new",                                        "使用新版语法");
        entry(list, "mysql_global_variables_ngram_token_size",                           "Ngram词元大小");
        entry(list, "mysql_global_variables_offline_mode",                               "离线模式");
        entry(list, "mysql_global_variables_old",                                        "使用旧版语法");
        entry(list, "mysql_global_variables_old_alter_table",                            "旧版ALTER TABLE");
        entry(list, "mysql_global_variables_open_files_limit",                           "打开文件数限制");
        entry(list, "mysql_global_variables_optimizer_max_subgraph_pairs",               "优化器最大子图对数");
        entry(list, "mysql_global_variables_optimizer_prune_level",                      "优化器剪枝级别");
        entry(list, "mysql_global_variables_optimizer_search_depth",                     "优化器搜索深度");
        entry(list, "mysql_global_variables_optimizer_trace_limit",                      "优化器跟踪限制");
        entry(list, "mysql_global_variables_optimizer_trace_max_mem_size",               "优化器跟踪最大内存");
        entry(list, "mysql_global_variables_optimizer_trace_offset",                     "优化器跟踪偏移");
        entry(list, "mysql_global_variables_parser_max_mem_size",                        "解析器最大内存");
        entry(list, "mysql_global_variables_partial_revokes",                            "部分权限撤销");
        entry(list, "mysql_global_variables_password_history",                           "密码历史");
        entry(list, "mysql_global_variables_password_require_current",                   "修改密码需验证当前密码");
        entry(list, "mysql_global_variables_password_reuse_interval",                    "密码重用间隔");
        entry(list, "mysql_global_variables_persist_sensitive_variables_in_plaintext",   "明文持久化敏感变量");
        entry(list, "mysql_global_variables_persisted_globals_load",                     "加载持久化全局变量");
        entry(list, "mysql_global_variables_port",                                       "MySQL 端口");
        entry(list, "mysql_global_variables_preload_buffer_size",                        "预加载缓冲区大小");
        entry(list, "mysql_global_variables_print_identified_with_as_hex",               "以十六进制打印标识");
        entry(list, "mysql_global_variables_profiling",                                  "性能分析开关");
        entry(list, "mysql_global_variables_profiling_history_size",                     "性能分析历史大小");
        entry(list, "mysql_global_variables_protocol_version",                           "协议版本");
        entry(list, "mysql_global_variables_query_alloc_block_size",                     "查询分配块大小");
        entry(list, "mysql_global_variables_query_cache_size",                           "查询缓存大小");
        entry(list, "mysql_global_variables_query_cache_type",                           "查询缓存类型");
        entry(list, "mysql_global_variables_query_prealloc_size",                        "查询预分配大小");
        entry(list, "mysql_global_variables_range_alloc_block_size",                     "范围分配块大小");
        entry(list, "mysql_global_variables_range_optimizer_max_mem_size",               "范围优化器最大内存");
        entry(list, "mysql_global_variables_read_buffer_size",                           "读缓冲区大小");
        entry(list, "mysql_global_variables_read_only",                                  "只读模式");
        entry(list, "mysql_global_variables_read_rnd_buffer_size",                       "随机读缓冲区大小");
        entry(list, "mysql_global_variables_regexp_stack_limit",                         "正则表达式栈限制");
        entry(list, "mysql_global_variables_regexp_time_limit",                          "正则表达式时间限制");
        entry(list, "mysql_global_variables_relay_log_purge",                            "中继日志自动清理");
        entry(list, "mysql_global_variables_relay_log_recovery",                         "中继日志恢复");
        entry(list, "mysql_global_variables_relay_log_space_limit",                      "中继日志空间限制");
        entry(list, "mysql_global_variables_replica_allow_batching",                     "从库允许批量提交");
        entry(list, "mysql_global_variables_replica_checkpoint_group",                   "从库检查点组");
        entry(list, "mysql_global_variables_replica_checkpoint_period",                  "从库检查点周期");
        entry(list, "mysql_global_variables_replica_compressed_protocol",                "从库压缩协议");
        entry(list, "mysql_global_variables_replica_max_allowed_packet",                 "从库最大允许数据包");
        entry(list, "mysql_global_variables_replica_net_timeout",                        "从库网络超时");
        entry(list, "mysql_global_variables_replica_parallel_workers",                   "从库并行工作线程数");
        entry(list, "mysql_global_variables_replica_pending_jobs_size_max",              "从库最大待处理作业大小");
        entry(list, "mysql_global_variables_replica_preserve_commit_order",              "从库保持提交顺序");
        entry(list, "mysql_global_variables_replica_skip_errors",                        "从库跳过错误");
        entry(list, "mysql_global_variables_replica_sql_verify_checksum",                "从库SQL校验验证");
        entry(list, "mysql_global_variables_replica_transaction_retries",                "从库事务重试次数");
        entry(list, "mysql_global_variables_replication_optimize_for_static_plugin_config","复制优化静态插件配置");
        entry(list, "mysql_global_variables_replication_sender_observe_commit_only",     "复制发送者仅观察提交");
        entry(list, "mysql_global_variables_report_port",                                "报告端口");
        entry(list, "mysql_global_variables_require_secure_transport",                   "要求安全传输");
        entry(list, "mysql_global_variables_rpl_read_size",                              "复制读取大小");
        entry(list, "mysql_global_variables_rpl_stop_replica_timeout",                   "停止从库超时");
        entry(list, "mysql_global_variables_rpl_stop_slave_timeout",                     "停止从库超时(旧)");
        entry(list, "mysql_global_variables_schema_definition_cache",                    "模式定义缓存");
        entry(list, "mysql_global_variables_secondary_engine_cost_threshold",            "辅助引擎成本阈值");
        entry(list, "mysql_global_variables_select_into_buffer_size",                    "SELECT INTO缓冲区大小");
        entry(list, "mysql_global_variables_select_into_disk_sync",                      "SELECT INTO磁盘同步");
        entry(list, "mysql_global_variables_select_into_disk_sync_delay",                "SELECT INTO磁盘同步延迟");
        entry(list, "mysql_global_variables_server_id",                                  "服务器ID");
        entry(list, "mysql_global_variables_server_id_bits",                             "服务器ID位数");
        entry(list, "mysql_global_variables_session_track_gtids",                        "会话跟踪GTID");
        entry(list, "mysql_global_variables_session_track_schema",                       "会话跟踪模式");
        entry(list, "mysql_global_variables_session_track_state_change",                 "会话跟踪状态变更");
        entry(list, "mysql_global_variables_session_track_transaction_info",             "会话跟踪事务信息");
        entry(list, "mysql_global_variables_sha256_password_auto_generate_rsa_keys",     "SHA256密码自动生成RSA密钥");
        entry(list, "mysql_global_variables_sha256_password_proxy_users",                "SHA256密码代理用户");
        entry(list, "mysql_global_variables_show_create_table_verbosity",                "SHOW CREATE TABLE详细度");
        entry(list, "mysql_global_variables_show_gipk_in_create_table_and_information_schema","显示隐藏主键");
        entry(list, "mysql_global_variables_show_old_temporals",                         "显示旧时间类型");
        entry(list, "mysql_global_variables_skip_external_locking",                      "跳过外部锁定");
        entry(list, "mysql_global_variables_skip_name_resolve",                          "跳过名称解析");
        entry(list, "mysql_global_variables_skip_networking",                            "跳过网络连接");
        entry(list, "mysql_global_variables_skip_replica_start",                         "跳过从库启动");
        entry(list, "mysql_global_variables_skip_show_database",                         "跳过SHOW DATABASE");
        entry(list, "mysql_global_variables_skip_slave_start",                           "跳过从库启动(旧)");
        entry(list, "mysql_global_variables_slave_allow_batching",                       "从库允许批量提交(旧)");
        entry(list, "mysql_global_variables_slave_checkpoint_group",                     "从库检查点组(旧)");
        entry(list, "mysql_global_variables_slave_checkpoint_period",                    "从库检查点周期(旧)");
        entry(list, "mysql_global_variables_slave_compressed_protocol",                  "从库压缩协议(旧)");
        entry(list, "mysql_global_variables_slave_max_allowed_packet",                   "从库最大允许数据包(旧)");
        entry(list, "mysql_global_variables_slave_net_timeout",                          "从库网络超时(旧)");
        entry(list, "mysql_global_variables_slave_parallel_workers",                     "从库并行工作线程数(旧)");
        entry(list, "mysql_global_variables_slave_pending_jobs_size_max",                "从库最大待处理作业(旧)");
        entry(list, "mysql_global_variables_slave_preserve_commit_order",                "从库保持提交顺序(旧)");
        entry(list, "mysql_global_variables_slave_skip_errors",                          "从库跳过错误(旧)");
        entry(list, "mysql_global_variables_slave_sql_verify_checksum",                  "从库SQL校验验证(旧)");
        entry(list, "mysql_global_variables_slave_transaction_retries",                  "从库事务重试次数(旧)");
        entry(list, "mysql_global_variables_slow_launch_time",                           "慢启动阈值(秒)");
        entry(list, "mysql_global_variables_slow_query_log",                             "慢查询日志开关");
        entry(list, "mysql_global_variables_sort_buffer_size",                           "排序缓冲区大小");
        entry(list, "mysql_global_variables_source_verify_checksum",                     "源校验和验证");
        entry(list, "mysql_global_variables_sql_auto_is_null",                           "SQL自动IS NULL");
        entry(list, "mysql_global_variables_sql_big_selects",                            "SQL大查询允许");
        entry(list, "mysql_global_variables_sql_buffer_result",                          "SQL缓冲结果");
        entry(list, "mysql_global_variables_sql_generate_invisible_primary_key",         "SQL生成隐藏主键");
        entry(list, "mysql_global_variables_sql_log_off",                                "SQL日志关闭");
        entry(list, "mysql_global_variables_sql_notes",                                  "SQL笔记");
        entry(list, "mysql_global_variables_sql_quote_show_create",                      "SQL引用SHOW CREATE");
        entry(list, "mysql_global_variables_sql_replica_skip_counter",                   "SQL从库跳过计数器");
        entry(list, "mysql_global_variables_sql_require_primary_key",                    "SQL要求主键");
        entry(list, "mysql_global_variables_sql_safe_updates",                           "SQL安全更新");
        entry(list, "mysql_global_variables_sql_select_limit",                           "SQL SELECT限制");
        entry(list, "mysql_global_variables_sql_slave_skip_counter",                     "SQL从库跳过计数器(旧)");
        entry(list, "mysql_global_variables_sql_warnings",                               "SQL警告");
        entry(list, "mysql_global_variables_ssl_fips_mode",                              "SSL FIPS模式");
        entry(list, "mysql_global_variables_ssl_session_cache_mode",                     "SSL会话缓存模式");
        entry(list, "mysql_global_variables_ssl_session_cache_timeout",                  "SSL会话缓存超时");
        entry(list, "mysql_global_variables_stored_program_cache",                       "存储程序缓存");
        entry(list, "mysql_global_variables_stored_program_definition_cache",            "存储程序定义缓存");
        entry(list, "mysql_global_variables_super_read_only",                            "超级只读模式");
        entry(list, "mysql_global_variables_sync_binlog",                                "同步Binlog");
        entry(list, "mysql_global_variables_sync_master_info",                           "同步主库信息(旧)");
        entry(list, "mysql_global_variables_sync_relay_log",                             "同步中继日志");
        entry(list, "mysql_global_variables_sync_relay_log_info",                        "同步中继日志信息");
        entry(list, "mysql_global_variables_sync_source_info",                           "同步源信息");
        entry(list, "mysql_global_variables_table_definition_cache",                     "表定义缓存");
        entry(list, "mysql_global_variables_table_encryption_privilege_check",           "表加密权限检查");
        entry(list, "mysql_global_variables_table_open_cache",                           "表打开缓存");
        entry(list, "mysql_global_variables_table_open_cache_instances",                 "表打开缓存实例数");
        entry(list, "mysql_global_variables_tablespace_definition_cache",                "表空间定义缓存");
        entry(list, "mysql_global_variables_temptable_max_mmap",                         "临时表最大内存映射");
        entry(list, "mysql_global_variables_temptable_max_ram",                          "临时表最大内存");
        entry(list, "mysql_global_variables_temptable_use_mmap",                         "临时表使用内存映射");
        entry(list, "mysql_global_variables_thread_cache_size",                          "线程缓存大小");
        entry(list, "mysql_global_variables_thread_stack",                               "线程栈大小");
        entry(list, "mysql_global_variables_tmp_table_size",                             "临时表大小");
        entry(list, "mysql_global_variables_transaction_alloc_block_size",               "事务分配块大小");
        entry(list, "mysql_global_variables_transaction_prealloc_size",                  "事务预分配大小");
        entry(list, "mysql_global_variables_transaction_read_only",                      "事务只读");
        entry(list, "mysql_global_variables_unique_checks",                              "唯一性检查");
        entry(list, "mysql_global_variables_updatable_views_with_limit",                 "可更新视图带LIMIT");
        entry(list, "mysql_global_variables_version",                                    "MySQL 版本号");
        entry(list, "mysql_global_variables_wait_timeout",                               "连接等待超时(秒)");
        entry(list, "mysql_global_variables_windowing_use_high_precision",               "窗口函数高精度");

        // ========== MySQL 配置 (X Protocol / mysqlx) ==========
        entry(list, "mysql_global_variables_mysqlx_connect_timeout",                     "X协议连接超时(秒)");
        entry(list, "mysql_global_variables_mysqlx_deflate_default_compression_level",   "X协议Deflate默认压缩级别");
        entry(list, "mysql_global_variables_mysqlx_deflate_max_client_compression_level","X协议Deflate最大客户端压缩级别");
        entry(list, "mysql_global_variables_mysqlx_document_id_unique_prefix",           "X协议文档ID唯一前缀");
        entry(list, "mysql_global_variables_mysqlx_enable_hello_notice",                 "X协议启用Hello通知");
        entry(list, "mysql_global_variables_mysqlx_idle_worker_thread_timeout",          "X协议空闲工作线程超时");
        entry(list, "mysql_global_variables_mysqlx_interactive_timeout",                 "X协议交互式超时(秒)");
        entry(list, "mysql_global_variables_mysqlx_lz4_default_compression_level",       "X协议LZ4默认压缩级别");
        entry(list, "mysql_global_variables_mysqlx_lz4_max_client_compression_level",    "X协议LZ4最大客户端压缩级别");
        entry(list, "mysql_global_variables_mysqlx_max_allowed_packet",                  "X协议最大允许数据包");
        entry(list, "mysql_global_variables_mysqlx_max_connections",                     "X协议最大连接数");
        entry(list, "mysql_global_variables_mysqlx_min_worker_threads",                  "X协议最小工作线程");
        entry(list, "mysql_global_variables_mysqlx_port",                                "X协议端口");
        entry(list, "mysql_global_variables_mysqlx_port_open_timeout",                   "X协议端口打开超时");
        entry(list, "mysql_global_variables_mysqlx_read_timeout",                        "X协议读超时(秒)");
        entry(list, "mysql_global_variables_mysqlx_wait_timeout",                        "X协议等待超时(秒)");
        entry(list, "mysql_global_variables_mysqlx_write_timeout",                       "X协议写超时(秒)");
        entry(list, "mysql_global_variables_mysqlx_zstd_default_compression_level",      "X协议Zstd默认压缩级别");
        entry(list, "mysql_global_variables_mysqlx_zstd_max_client_compression_level",   "X协议Zstd最大客户端压缩级别");

        // ========== MySQL 配置 (Performance Schema) ==========
        entry(list, "mysql_global_variables_performance_schema",                                   "性能模式开关");
        entry(list, "mysql_global_variables_performance_schema_accounts_size",                     "性能模式账户表大小");
        entry(list, "mysql_global_variables_performance_schema_digests_size",                      "性能模式摘要表大小");
        entry(list, "mysql_global_variables_performance_schema_error_size",                        "性能模式错误表大小");
        entry(list, "mysql_global_variables_performance_schema_events_stages_history_long_size",   "性能模式阶段历史长表大小");
        entry(list, "mysql_global_variables_performance_schema_events_stages_history_size",        "性能模式阶段历史表大小");
        entry(list, "mysql_global_variables_performance_schema_events_statements_history_long_size","性能模式语句历史长表大小");
        entry(list, "mysql_global_variables_performance_schema_events_statements_history_size",    "性能模式语句历史表大小");
        entry(list, "mysql_global_variables_performance_schema_events_transactions_history_long_size","性能模式事务历史长表大小");
        entry(list, "mysql_global_variables_performance_schema_events_transactions_history_size",  "性能模式事务历史表大小");
        entry(list, "mysql_global_variables_performance_schema_events_waits_history_long_size",    "性能模式等待历史长表大小");
        entry(list, "mysql_global_variables_performance_schema_events_waits_history_size",         "性能模式等待历史表大小");
        entry(list, "mysql_global_variables_performance_schema_hosts_size",                        "性能模式主机表大小");
        entry(list, "mysql_global_variables_performance_schema_max_cond_classes",                  "性能模式最大条件类数");
        entry(list, "mysql_global_variables_performance_schema_max_cond_instances",                "性能模式最大条件实例数");
        entry(list, "mysql_global_variables_performance_schema_max_digest_length",                 "性能模式最大摘要长度");
        entry(list, "mysql_global_variables_performance_schema_max_digest_sample_age",             "性能模式摘要最大采样年龄");
        entry(list, "mysql_global_variables_performance_schema_max_file_classes",                  "性能模式最大文件类数");
        entry(list, "mysql_global_variables_performance_schema_max_file_handles",                  "性能模式最大文件句柄数");
        entry(list, "mysql_global_variables_performance_schema_max_file_instances",                "性能模式最大文件实例数");
        entry(list, "mysql_global_variables_performance_schema_max_index_stat",                    "性能模式最大索引统计");
        entry(list, "mysql_global_variables_performance_schema_max_memory_classes",                "性能模式最大内存类数");
        entry(list, "mysql_global_variables_performance_schema_max_metadata_locks",                "性能模式最大元数据锁数");
        entry(list, "mysql_global_variables_performance_schema_max_mutex_classes",                 "性能模式最大互斥锁类数");
        entry(list, "mysql_global_variables_performance_schema_max_mutex_instances",               "性能模式最大互斥锁实例数");
        entry(list, "mysql_global_variables_performance_schema_max_prepared_statements_instances", "性能模式最大预处理语句实例数");
        entry(list, "mysql_global_variables_performance_schema_max_program_instances",             "性能模式最大程序实例数");
        entry(list, "mysql_global_variables_performance_schema_max_rwlock_classes",                "性能模式最大读写锁类数");
        entry(list, "mysql_global_variables_performance_schema_max_rwlock_instances",              "性能模式最大读写锁实例数");
        entry(list, "mysql_global_variables_performance_schema_max_socket_classes",                "性能模式最大套接字类数");
        entry(list, "mysql_global_variables_performance_schema_max_socket_instances",              "性能模式最大套接字实例数");
        entry(list, "mysql_global_variables_performance_schema_max_sql_text_length",               "性能模式最大SQL文本长度");
        entry(list, "mysql_global_variables_performance_schema_max_stage_classes",                 "性能模式最大阶段类数");
        entry(list, "mysql_global_variables_performance_schema_max_statement_classes",             "性能模式最大语句类数");
        entry(list, "mysql_global_variables_performance_schema_max_statement_stack",               "性能模式最大语句栈深度");
        entry(list, "mysql_global_variables_performance_schema_max_table_handles",                 "性能模式最大表句柄数");
        entry(list, "mysql_global_variables_performance_schema_max_table_instances",               "性能模式最大表实例数");
        entry(list, "mysql_global_variables_performance_schema_max_table_lock_stat",               "性能模式最大表锁统计");
        entry(list, "mysql_global_variables_performance_schema_max_thread_classes",                "性能模式最大线程类数");
        entry(list, "mysql_global_variables_performance_schema_max_thread_instances",              "性能模式最大线程实例数");
        entry(list, "mysql_global_variables_performance_schema_session_connect_attrs_size",        "性能模式会话连接属性大小");
        entry(list, "mysql_global_variables_performance_schema_setup_actors_size",                 "性能模式角色设置表大小");
        entry(list, "mysql_global_variables_performance_schema_setup_objects_size",                "性能模式对象设置表大小");
        entry(list, "mysql_global_variables_performance_schema_show_processlist",                  "性能模式显示进程列表");
        entry(list, "mysql_global_variables_performance_schema_users_size",                        "性能模式用户表大小");

        // ========== MySQL 配置 (InnoDB) ==========
        entry(list, "mysql_global_variables_innodb_adaptive_flushing",                  "InnoDB自适应刷新");
        entry(list, "mysql_global_variables_innodb_adaptive_flushing_lwm",              "InnoDB自适应刷新低水位(%)");
        entry(list, "mysql_global_variables_innodb_adaptive_hash_index",                "InnoDB自适应哈希索引");
        entry(list, "mysql_global_variables_innodb_adaptive_hash_index_parts",          "InnoDB自适应哈希索引分区数");
        entry(list, "mysql_global_variables_innodb_adaptive_max_sleep_delay",           "InnoDB自适应最大睡眠延迟(μs)");
        entry(list, "mysql_global_variables_innodb_api_bk_commit_interval",             "InnoDB API后台提交间隔");
        entry(list, "mysql_global_variables_innodb_api_disable_rowlock",                "InnoDB API禁用行锁");
        entry(list, "mysql_global_variables_innodb_api_enable_binlog",                  "InnoDB API启用Binlog");
        entry(list, "mysql_global_variables_innodb_api_enable_mdl",                     "InnoDB API启用MDL");
        entry(list, "mysql_global_variables_innodb_api_trx_level",                      "InnoDB API事务级别");
        entry(list, "mysql_global_variables_innodb_autoextend_increment",               "InnoDB自动扩展增量(MB)");
        entry(list, "mysql_global_variables_innodb_autoinc_lock_mode",                  "InnoDB自增锁模式");
        entry(list, "mysql_global_variables_innodb_buffer_pool_chunk_size",             "InnoDB缓冲池块大小");
        entry(list, "mysql_global_variables_innodb_buffer_pool_dump_at_shutdown",       "InnoDB关闭时转储缓冲池");
        entry(list, "mysql_global_variables_innodb_buffer_pool_dump_now",               "InnoDB立即转储缓冲池");
        entry(list, "mysql_global_variables_innodb_buffer_pool_dump_pct",               "InnoDB缓冲池转储百分比");
        entry(list, "mysql_global_variables_innodb_buffer_pool_in_core_file",           "InnoDB缓冲池记录到Core文件");
        entry(list, "mysql_global_variables_innodb_buffer_pool_instances",              "InnoDB 缓冲池实例数");
        entry(list, "mysql_global_variables_innodb_buffer_pool_load_abort",             "InnoDB缓冲池加载中止");
        entry(list, "mysql_global_variables_innodb_buffer_pool_load_at_startup",        "InnoDB启动时加载缓冲池");
        entry(list, "mysql_global_variables_innodb_buffer_pool_load_now",               "InnoDB立即加载缓冲池");
        entry(list, "mysql_global_variables_innodb_buffer_pool_size",                   "InnoDB 缓冲池大小");
        entry(list, "mysql_global_variables_innodb_change_buffer_max_size",             "InnoDB变更缓冲最大大小(%)");
        entry(list, "mysql_global_variables_innodb_cmp_per_index_enabled",              "InnoDB每索引压缩启用");
        entry(list, "mysql_global_variables_innodb_commit_concurrency",                 "InnoDB提交并发度");
        entry(list, "mysql_global_variables_innodb_compression_failure_threshold_pct",  "InnoDB压缩失败阈值(%)");
        entry(list, "mysql_global_variables_innodb_compression_level",                  "InnoDB压缩级别");
        entry(list, "mysql_global_variables_innodb_compression_pad_pct_max",            "InnoDB压缩填充最大百分比");
        entry(list, "mysql_global_variables_innodb_concurrency_tickets",                "InnoDB并发票据数");
        entry(list, "mysql_global_variables_innodb_ddl_buffer_size",                    "InnoDB DDL缓冲区大小");
        entry(list, "mysql_global_variables_innodb_ddl_threads",                        "InnoDB DDL线程数");
        entry(list, "mysql_global_variables_innodb_deadlock_detect",                    "InnoDB死锁检测");
        entry(list, "mysql_global_variables_innodb_dedicated_server",                   "InnoDB专用服务器模式");
        entry(list, "mysql_global_variables_innodb_disable_sort_file_cache",            "InnoDB禁用排序文件缓存");
        entry(list, "mysql_global_variables_innodb_doublewrite",                        "InnoDB双写");
        entry(list, "mysql_global_variables_innodb_doublewrite_batch_size",             "InnoDB双写批量大小");
        entry(list, "mysql_global_variables_innodb_doublewrite_files",                  "InnoDB双写文件数");
        entry(list, "mysql_global_variables_innodb_doublewrite_pages",                  "InnoDB双写页数");
        entry(list, "mysql_global_variables_innodb_extend_and_initialize",              "InnoDB扩展并初始化");
        entry(list, "mysql_global_variables_innodb_fast_shutdown",                      "InnoDB快速关闭");
        entry(list, "mysql_global_variables_innodb_file_per_table",                     "InnoDB每表独立表空间");
        entry(list, "mysql_global_variables_innodb_fill_factor",                        "InnoDB填充因子(%)");
        entry(list, "mysql_global_variables_innodb_flush_log_at_timeout",               "InnoDB日志刷新超时(秒)");
        entry(list, "mysql_global_variables_innodb_flush_log_at_trx_commit",            "InnoDB 刷盘策略");
        entry(list, "mysql_global_variables_innodb_flush_method",                       "InnoDB 刷盘方式");
        entry(list, "mysql_global_variables_innodb_flush_neighbors",                    "InnoDB刷新邻接页");
        entry(list, "mysql_global_variables_innodb_flush_sync",                         "InnoDB同步刷新");
        entry(list, "mysql_global_variables_innodb_flushing_avg_loops",                 "InnoDB刷新平均循环次数");
        entry(list, "mysql_global_variables_innodb_force_load_corrupted",               "InnoDB强制加载损坏页");
        entry(list, "mysql_global_variables_innodb_force_recovery",                     "InnoDB强制恢复");
        entry(list, "mysql_global_variables_innodb_fsync_threshold",                    "InnoDB fsync阈值");
        entry(list, "mysql_global_variables_innodb_ft_cache_size",                      "InnoDB全文索引缓存大小");
        entry(list, "mysql_global_variables_innodb_ft_enable_diag_print",               "InnoDB全文诊断打印");
        entry(list, "mysql_global_variables_innodb_ft_enable_stopword",                 "InnoDB全文启用停用词");
        entry(list, "mysql_global_variables_innodb_ft_max_token_size",                  "InnoDB全文最大词元大小");
        entry(list, "mysql_global_variables_innodb_ft_min_token_size",                  "InnoDB全文最小词元大小");
        entry(list, "mysql_global_variables_innodb_ft_num_word_optimize",               "InnoDB全文优化词数");
        entry(list, "mysql_global_variables_innodb_ft_result_cache_limit",              "InnoDB全文结果缓存限制");
        entry(list, "mysql_global_variables_innodb_ft_sort_pll_degree",                 "InnoDB全文排序并行度");
        entry(list, "mysql_global_variables_innodb_ft_total_cache_size",                "InnoDB全文总缓存大小");
        entry(list, "mysql_global_variables_innodb_idle_flush_pct",                     "InnoDB空闲刷新百分比");
        entry(list, "mysql_global_variables_innodb_io_capacity",                        "InnoDB IO容量");
        entry(list, "mysql_global_variables_innodb_io_capacity_max",                    "InnoDB IO最大容量");
        entry(list, "mysql_global_variables_innodb_lock_wait_timeout",                  "InnoDB锁等待超时(秒)");
        entry(list, "mysql_global_variables_innodb_log_buffer_size",                    "InnoDB日志缓冲区大小");
        entry(list, "mysql_global_variables_innodb_log_checksums",                      "InnoDB日志校验和");
        entry(list, "mysql_global_variables_innodb_log_compressed_pages",               "InnoDB日志压缩页");
        entry(list, "mysql_global_variables_innodb_log_file_size",                      "InnoDB 日志文件大小");
        entry(list, "mysql_global_variables_innodb_log_files_in_group",                 "InnoDB日志文件组数");
        entry(list, "mysql_global_variables_innodb_log_spin_cpu_abs_lwm",               "InnoDB日志自旋CPU绝对低水位");
        entry(list, "mysql_global_variables_innodb_log_spin_cpu_pct_hwm",               "InnoDB日志自旋CPU百分比高水位");
        entry(list, "mysql_global_variables_innodb_log_wait_for_flush_spin_hwm",        "InnoDB日志等待刷新自旋高水位");
        entry(list, "mysql_global_variables_innodb_log_write_ahead_size",               "InnoDB日志预写大小");
        entry(list, "mysql_global_variables_innodb_log_writer_threads",                 "InnoDB日志写入线程数");
        entry(list, "mysql_global_variables_innodb_lru_scan_depth",                     "InnoDB LRU扫描深度");
        entry(list, "mysql_global_variables_innodb_max_dirty_pages_pct",                "InnoDB最大脏页百分比");
        entry(list, "mysql_global_variables_innodb_max_dirty_pages_pct_lwm",            "InnoDB最大脏页低水位(%)");
        entry(list, "mysql_global_variables_innodb_max_purge_lag",                      "InnoDB最大清除延迟");
        entry(list, "mysql_global_variables_innodb_max_purge_lag_delay",                "InnoDB最大清除延迟时间(μs)");
        entry(list, "mysql_global_variables_innodb_max_undo_log_size",                  "InnoDB最大Undo日志大小");
        entry(list, "mysql_global_variables_innodb_old_blocks_pct",                     "InnoDB旧块百分比");
        entry(list, "mysql_global_variables_innodb_old_blocks_time",                    "InnoDB旧块时间(ms)");
        entry(list, "mysql_global_variables_innodb_online_alter_log_max_size",          "InnoDB在线DDL日志最大大小");
        entry(list, "mysql_global_variables_innodb_open_files",                         "InnoDB打开文件数");
        entry(list, "mysql_global_variables_innodb_optimize_fulltext_only",             "InnoDB仅优化全文索引");
        entry(list, "mysql_global_variables_innodb_page_cleaners",                      "InnoDB页面清理线程数");
        entry(list, "mysql_global_variables_innodb_page_size",                          "InnoDB页大小");
        entry(list, "mysql_global_variables_innodb_parallel_read_threads",              "InnoDB并行读线程数");
        entry(list, "mysql_global_variables_innodb_print_all_deadlocks",                "InnoDB打印所有死锁");
        entry(list, "mysql_global_variables_innodb_print_ddl_logs",                     "InnoDB打印DDL日志");
        entry(list, "mysql_global_variables_innodb_purge_batch_size",                   "InnoDB清除批量大小");
        entry(list, "mysql_global_variables_innodb_purge_rseg_truncate_frequency",      "InnoDB清除回滚段截断频率");
        entry(list, "mysql_global_variables_innodb_purge_threads",                      "InnoDB清除线程数");
        entry(list, "mysql_global_variables_innodb_random_read_ahead",                  "InnoDB随机预读");
        entry(list, "mysql_global_variables_innodb_read_ahead_threshold",               "InnoDB预读阈值");
        entry(list, "mysql_global_variables_innodb_read_io_threads",                    "InnoDB 读IO线程数");
        entry(list, "mysql_global_variables_innodb_read_only",                          "InnoDB只读");
        entry(list, "mysql_global_variables_innodb_redo_log_capacity",                  "InnoDB重做日志容量");
        entry(list, "mysql_global_variables_innodb_redo_log_encrypt",                   "InnoDB重做日志加密");
        entry(list, "mysql_global_variables_innodb_replication_delay",                  "InnoDB复制延迟(ms)");
        entry(list, "mysql_global_variables_innodb_rollback_on_timeout",                "InnoDB超时回滚");
        entry(list, "mysql_global_variables_innodb_rollback_segments",                  "InnoDB回滚段数");
        entry(list, "mysql_global_variables_innodb_segment_reserve_factor",             "InnoDB段保留因子");
        entry(list, "mysql_global_variables_innodb_sort_buffer_size",                   "InnoDB排序缓冲区大小");
        entry(list, "mysql_global_variables_innodb_spin_wait_delay",                    "InnoDB自旋等待延迟");
        entry(list, "mysql_global_variables_innodb_spin_wait_pause_multiplier",         "InnoDB自旋等待暂停倍数");
        entry(list, "mysql_global_variables_innodb_stats_auto_recalc",                  "InnoDB统计信息自动重算");
        entry(list, "mysql_global_variables_innodb_stats_include_delete_marked",        "InnoDB统计包含删除标记");
        entry(list, "mysql_global_variables_innodb_stats_on_metadata",                  "InnoDB元数据统计");
        entry(list, "mysql_global_variables_innodb_stats_persistent",                   "InnoDB持久统计信息");
        entry(list, "mysql_global_variables_innodb_stats_persistent_sample_pages",      "InnoDB持久统计采样页数");
        entry(list, "mysql_global_variables_innodb_stats_transient_sample_pages",       "InnoDB临时统计采样页数");
        entry(list, "mysql_global_variables_innodb_status_output",                      "InnoDB状态输出");
        entry(list, "mysql_global_variables_innodb_status_output_locks",                "InnoDB状态输出锁信息");
        entry(list, "mysql_global_variables_innodb_strict_mode",                        "InnoDB严格模式");
        entry(list, "mysql_global_variables_innodb_sync_array_size",                    "InnoDB同步数组大小");
        entry(list, "mysql_global_variables_innodb_sync_spin_loops",                    "InnoDB同步自旋循环数");
        entry(list, "mysql_global_variables_innodb_table_locks",                        "InnoDB表锁");
        entry(list, "mysql_global_variables_innodb_thread_concurrency",                 "InnoDB线程并发度");
        entry(list, "mysql_global_variables_innodb_thread_sleep_delay",                 "InnoDB线程睡眠延迟(μs)");
        entry(list, "mysql_global_variables_innodb_undo_log_encrypt",                   "InnoDB Undo日志加密");
        entry(list, "mysql_global_variables_innodb_undo_log_truncate",                  "InnoDB Undo日志截断");
        entry(list, "mysql_global_variables_innodb_undo_tablespaces",                   "InnoDB Undo表空间数");
        entry(list, "mysql_global_variables_innodb_use_fdatasync",                      "InnoDB使用fdatasync");
        entry(list, "mysql_global_variables_innodb_use_native_aio",                     "InnoDB使用原生AIO");
        entry(list, "mysql_global_variables_innodb_validate_tablespace_paths",          "InnoDB验证表空间路径");
        entry(list, "mysql_global_variables_innodb_write_io_threads",                   "InnoDB 写IO线程数");

        // ========== MySQL 状态 (ACL / Binlog / Buffer Pool 8.0+) ==========
        entry(list, "mysql_global_status_acl_cache_items_count",                         "ACL缓存条目数");
        entry(list, "mysql_global_status_binlog_cache_disk_use",                         "Binlog缓存磁盘使用次数");
        entry(list, "mysql_global_status_binlog_cache_use",                              "Binlog缓存使用次数");
        entry(list, "mysql_global_status_binlog_stmt_cache_disk_use",                    "Binlog语句缓存磁盘使用次数");
        entry(list, "mysql_global_status_binlog_stmt_cache_use",                         "Binlog语句缓存使用次数");
        entry(list, "mysql_global_status_buffer_pool_dirty_pages",                       "缓冲池脏页数(8.0+ 全局)");
        entry(list, "mysql_global_status_buffer_pool_page_changes_total",                "缓冲池页变更总数");
        entry(list, "mysql_global_status_buffer_pool_pages",                             "缓冲池页数(8.0+ 全局)");

        // ========== MySQL 状态 (延迟插入 / 错误日志 / Flush) ==========
        entry(list, "mysql_global_status_delayed_errors",                                "延迟插入错误数");
        entry(list, "mysql_global_status_delayed_insert_threads",                        "延迟插入线程数");
        entry(list, "mysql_global_status_delayed_writes",                                "延迟写入次数");
        entry(list, "mysql_global_status_deprecated_use_i_s_processlist_count",          "已废弃IS进程列表使用次数");
        entry(list, "mysql_global_status_deprecated_use_i_s_processlist_last_timestamp", "已废弃IS进程列表最后使用时间戳");
        entry(list, "mysql_global_status_error_log_buffered_bytes",                      "错误日志缓冲字节");
        entry(list, "mysql_global_status_error_log_buffered_events",                     "错误日志缓冲事件数");
        entry(list, "mysql_global_status_error_log_expired_events",                      "错误日志过期事件数");
        entry(list, "mysql_global_status_error_log_latest_write",                        "错误日志最近写入时间戳");
        entry(list, "mysql_global_status_flush_commands",                                "FLUSH命令次数");
        entry(list, "mysql_global_status_global_connection_memory",                      "全局连接内存");
        entry(list, "mysql_global_status_handlers_total",                                "处理器操作总数(8.0+)");

        // ========== MySQL 状态 (Key 缓存 / MyISAM) ==========
        entry(list, "mysql_global_status_key_blocks_not_flushed",                        "键缓存未刷新块数");
        entry(list, "mysql_global_status_key_blocks_unused",                             "键缓存未使用块数");
        entry(list, "mysql_global_status_key_blocks_used",                               "键缓存已使用块数");
        entry(list, "mysql_global_status_key_read_requests",                             "键缓存读请求次数");
        entry(list, "mysql_global_status_key_reads",                                     "键缓存物理读次数");
        entry(list, "mysql_global_status_key_write_requests",                            "键缓存写请求次数");
        entry(list, "mysql_global_status_key_writes",                                    "键缓存物理写次数");
        entry(list, "mysql_global_status_locked_connects",                               "锁定连接数");
        entry(list, "mysql_global_status_max_execution_time_exceeded",                   "超最大执行时间次数");
        entry(list, "mysql_global_status_max_execution_time_set",                        "设置最大执行时间次数");
        entry(list, "mysql_global_status_max_execution_time_set_failed",                 "设置最大执行时间失败次数");

        // ========== MySQL 状态 (X Protocol 连接/传输) ==========
        entry(list, "mysql_global_status_mysqlx_aborted_clients",                        "X协议客户端异常断开");
        entry(list, "mysql_global_status_mysqlx_bytes_received",                         "X协议接收字节数");
        entry(list, "mysql_global_status_mysqlx_bytes_received_compressed_payload",      "X协议接收压缩载荷字节");
        entry(list, "mysql_global_status_mysqlx_bytes_received_uncompressed_frame",      "X协议接收解压帧字节");
        entry(list, "mysql_global_status_mysqlx_bytes_sent",                             "X协议发送字节数");
        entry(list, "mysql_global_status_mysqlx_bytes_sent_compressed_payload",          "X协议发送压缩载荷字节");
        entry(list, "mysql_global_status_mysqlx_bytes_sent_uncompressed_frame",          "X协议发送解压帧字节");
        entry(list, "mysql_global_status_mysqlx_connection_accept_errors",               "X协议接受连接错误数");
        entry(list, "mysql_global_status_mysqlx_connection_errors",                      "X协议连接错误数");
        entry(list, "mysql_global_status_mysqlx_connections_accepted",                   "X协议接受连接数");
        entry(list, "mysql_global_status_mysqlx_connections_closed",                     "X协议关闭连接数");
        entry(list, "mysql_global_status_mysqlx_connections_rejected",                   "X协议拒绝连接数");

        // ========== MySQL 状态 (X Protocol CRUD / 游标) ==========
        entry(list, "mysql_global_status_mysqlx_crud_create_view",                       "X协议CRUD创建视图");
        entry(list, "mysql_global_status_mysqlx_crud_delete",                            "X协议CRUD删除");
        entry(list, "mysql_global_status_mysqlx_crud_drop_view",                         "X协议CRUD删除视图");
        entry(list, "mysql_global_status_mysqlx_crud_find",                              "X协议CRUD查找");
        entry(list, "mysql_global_status_mysqlx_crud_insert",                            "X协议CRUD插入");
        entry(list, "mysql_global_status_mysqlx_crud_modify_view",                       "X协议CRUD修改视图");
        entry(list, "mysql_global_status_mysqlx_crud_update",                            "X协议CRUD更新");
        entry(list, "mysql_global_status_mysqlx_cursor_close",                           "X协议游标关闭");
        entry(list, "mysql_global_status_mysqlx_cursor_fetch",                           "X协议游标提取");
        entry(list, "mysql_global_status_mysqlx_cursor_open",                            "X协议游标打开");
        entry(list, "mysql_global_status_mysqlx_errors_sent",                            "X协议发送错误数");
        entry(list, "mysql_global_status_mysqlx_errors_unknown_message_type",            "X协议未知消息类型错误数");
        entry(list, "mysql_global_status_mysqlx_expect_close",                           "X协议期望关闭");
        entry(list, "mysql_global_status_mysqlx_expect_open",                            "X协议期望打开");
        entry(list, "mysql_global_status_mysqlx_init_error",                             "X协议初始化错误");
        entry(list, "mysql_global_status_mysqlx_messages_sent",                          "X协议发送消息数");

        // ========== MySQL 状态 (X Protocol 通知 / 预处理 / 会话 / SSL) ==========
        entry(list, "mysql_global_status_mysqlx_notice_global_sent",                     "X协议全局通知发送数");
        entry(list, "mysql_global_status_mysqlx_notice_other_sent",                      "X协议其他通知发送数");
        entry(list, "mysql_global_status_mysqlx_notice_warning_sent",                    "X协议警告通知发送数");
        entry(list, "mysql_global_status_mysqlx_notified_by_group_replication",          "X协议组复制通知数");
        entry(list, "mysql_global_status_mysqlx_port",                                   "X协议端口");
        entry(list, "mysql_global_status_mysqlx_prep_deallocate",                        "X协议预处理释放");
        entry(list, "mysql_global_status_mysqlx_prep_execute",                           "X协议预处理执行");
        entry(list, "mysql_global_status_mysqlx_prep_prepare",                           "X协议预处理准备");
        entry(list, "mysql_global_status_mysqlx_rows_sent",                              "X协议发送行数");
        entry(list, "mysql_global_status_mysqlx_sessions",                               "X协议会话数");
        entry(list, "mysql_global_status_mysqlx_sessions_accepted",                      "X协议接受会话数");
        entry(list, "mysql_global_status_mysqlx_sessions_closed",                        "X协议关闭会话数");
        entry(list, "mysql_global_status_mysqlx_sessions_fatal_error",                   "X协议会话致命错误数");
        entry(list, "mysql_global_status_mysqlx_sessions_killed",                        "X协议会话杀死数");
        entry(list, "mysql_global_status_mysqlx_sessions_rejected",                      "X协议拒绝会话数");
        entry(list, "mysql_global_status_mysqlx_ssl_accepts",                            "X协议SSL接受数");
        entry(list, "mysql_global_status_mysqlx_ssl_ctx_verify_depth",                   "X协议SSL上下文验证深度");
        entry(list, "mysql_global_status_mysqlx_ssl_ctx_verify_mode",                    "X协议SSL上下文验证模式");
        entry(list, "mysql_global_status_mysqlx_ssl_finished_accepts",                   "X协议SSL完成接受数");
        entry(list, "mysql_global_status_mysqlx_ssl_server_not_after",                   "X协议SSL证书到期时间");
        entry(list, "mysql_global_status_mysqlx_ssl_server_not_before",                  "X协议SSL证书生效时间");

        // ========== MySQL 状态 (X Protocol 语句 / 工作线程) ==========
        entry(list, "mysql_global_status_mysqlx_stmt_create_collection",                 "X协议创建集合");
        entry(list, "mysql_global_status_mysqlx_stmt_create_collection_index",           "X协议创建集合索引");
        entry(list, "mysql_global_status_mysqlx_stmt_disable_notices",                   "X协议禁用通知");
        entry(list, "mysql_global_status_mysqlx_stmt_drop_collection",                   "X协议删除集合");
        entry(list, "mysql_global_status_mysqlx_stmt_drop_collection_index",             "X协议删除集合索引");
        entry(list, "mysql_global_status_mysqlx_stmt_enable_notices",                    "X协议启用通知");
        entry(list, "mysql_global_status_mysqlx_stmt_ensure_collection",                 "X协议确保集合存在");
        entry(list, "mysql_global_status_mysqlx_stmt_execute_mysqlx",                    "X协议执行MySQLx语句");
        entry(list, "mysql_global_status_mysqlx_stmt_execute_sql",                       "X协议执行SQL语句");
        entry(list, "mysql_global_status_mysqlx_stmt_execute_xplugin",                   "X协议执行XPlugin语句");
        entry(list, "mysql_global_status_mysqlx_stmt_get_collection_options",            "X协议获取集合选项");
        entry(list, "mysql_global_status_mysqlx_stmt_kill_client",                       "X协议终止客户端");
        entry(list, "mysql_global_status_mysqlx_stmt_list_clients",                      "X协议列出客户端");
        entry(list, "mysql_global_status_mysqlx_stmt_list_notices",                      "X协议列出通知");
        entry(list, "mysql_global_status_mysqlx_stmt_list_objects",                      "X协议列出对象");
        entry(list, "mysql_global_status_mysqlx_stmt_modify_collection_options",         "X协议修改集合选项");
        entry(list, "mysql_global_status_mysqlx_stmt_ping",                              "X协议Ping");
        entry(list, "mysql_global_status_mysqlx_worker_threads",                         "X协议工作线程数");
        entry(list, "mysql_global_status_mysqlx_worker_threads_active",                  "X协议活跃工作线程数");

        // ========== MySQL 状态 (文件/表/事务) ==========
        entry(list, "mysql_global_status_not_flushed_delayed_rows",                      "未刷新延迟行数");
        entry(list, "mysql_global_status_ongoing_anonymous_transaction_count",           "进行中匿名事务数");
        entry(list, "mysql_global_status_open_files",                                    "打开文件数");
        entry(list, "mysql_global_status_open_streams",                                  "打开流数");
        entry(list, "mysql_global_status_open_table_definitions",                        "打开表定义数");
        entry(list, "mysql_global_status_opened_files",                                  "累计打开文件数");
        entry(list, "mysql_global_status_opened_table_definitions",                      "累计打开表定义数");

        // ========== MySQL 状态 (Performance Schema 丢失计数) ==========
        entry(list, "mysql_global_status_performance_schema_lost_total",                 "性能模式丢失计数总数");
        entry(list, "mysql_global_status_prepared_stmt_count",                           "预处理语句数");
        entry(list, "mysql_global_status_replica_open_temp_tables",                      "从库打开临时表数");
        entry(list, "mysql_global_status_resource_group_supported",                      "资源组支持");
        entry(list, "mysql_global_status_secondary_engine_execution_count",              "辅助引擎执行次数");
        entry(list, "mysql_global_status_slave_open_temp_tables",                        "从库打开临时表数(旧)");
        entry(list, "mysql_global_status_slow_launch_threads",                           "慢启动线程数");

        // ========== MySQL 状态 (SSL) ==========
        entry(list, "mysql_global_status_ssl_accept_renegotiates",                       "SSL接受重协商数");
        entry(list, "mysql_global_status_ssl_accepts",                                   "SSL接受连接数");
        entry(list, "mysql_global_status_ssl_callback_cache_hits",                       "SSL回调缓存命中数");
        entry(list, "mysql_global_status_ssl_client_connects",                           "SSL客户端连接数");
        entry(list, "mysql_global_status_ssl_connect_renegotiates",                      "SSL连接重协商数");
        entry(list, "mysql_global_status_ssl_ctx_verify_depth",                          "SSL上下文验证深度");
        entry(list, "mysql_global_status_ssl_ctx_verify_mode",                           "SSL上下文验证模式");
        entry(list, "mysql_global_status_ssl_default_timeout",                           "SSL默认超时");
        entry(list, "mysql_global_status_ssl_finished_accepts",                          "SSL完成接受数");
        entry(list, "mysql_global_status_ssl_finished_connects",                         "SSL完成连接数");
        entry(list, "mysql_global_status_ssl_server_not_after",                          "SSL证书到期时间");
        entry(list, "mysql_global_status_ssl_server_not_before",                         "SSL证书生效时间");
        entry(list, "mysql_global_status_ssl_session_cache_hits",                        "SSL会话缓存命中数");
        entry(list, "mysql_global_status_ssl_session_cache_misses",                      "SSL会话缓存未命中数");
        entry(list, "mysql_global_status_ssl_session_cache_overflows",                   "SSL会话缓存溢出数");
        entry(list, "mysql_global_status_ssl_session_cache_size",                        "SSL会话缓存大小");
        entry(list, "mysql_global_status_ssl_session_cache_timeout",                     "SSL会话缓存超时");
        entry(list, "mysql_global_status_ssl_session_cache_timeouts",                    "SSL会话缓存超时次数");
        entry(list, "mysql_global_status_ssl_sessions_reused",                           "SSL会话复用数");
        entry(list, "mysql_global_status_ssl_used_session_cache_entries",                "SSL已用会话缓存条目数");
        entry(list, "mysql_global_status_ssl_verify_depth",                              "SSL验证深度");
        entry(list, "mysql_global_status_ssl_verify_mode",                               "SSL验证模式");

        // ========== MySQL 状态 (TC Log / 遥测 / 事务隔离级别) ==========
        entry(list, "mysql_global_status_tc_log_max_pages_used",                         "TC日志最大使用页数");
        entry(list, "mysql_global_status_tc_log_page_size",                              "TC日志页大小");
        entry(list, "mysql_global_status_tc_log_page_waits",                             "TC日志页等待次数");
        entry(list, "mysql_global_status_telemetry_traces_supported",                    "遥测追踪支持");
        entry(list, "mysql_transaction_isolation",                                       "当前事务隔离级别");

        // ========== MySQL Exporter 自监控 / 系统 ==========
        entry(list, "mysql_up",                                              "MySQL 存活状态");
        entry(list, "mysql_version_info",                                    "MySQL 版本信息");
        entry(list, "mysql_global_status_uptime",                            "MySQL 运行时间(秒)");
        entry(list, "mysql_global_status_uptime_since_flush_status",         "自上次FLUSH STATUS时间(秒)");
        entry(list, "mysql_exporter_collector_duration_seconds",             "MySQL采集器耗时(秒)");
        entry(list, "mysql_exporter_collector_success",                      "MySQL采集器成功状态");
        entry(list, "mysql_exporter_last_scrape_error",                      "MySQL最后抓取错误");
        entry(list, "mysql_exporter_scrapes_total",                          "MySQL抓取总数");

        // ========== Exporter 自监控 ==========
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

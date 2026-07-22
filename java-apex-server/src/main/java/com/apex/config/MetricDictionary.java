package com.apex.config;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 指标字典 — 集中管理所有 Prometheus 指标的中文翻译、分类归属与前缀推断规则。
 * <p>
 * 指标定义按业务类别拆分到同包下的子文件（通过 {@code contribute()} 聚合）：
 * <ul>
 *   <li>{@link MetricLinuxDict} — Linux 节点指标 (node_exporter)</li>
 *   <li>{@link MetricWinDict} — Windows 节点指标 (windows_exporter)</li>
 *   <li>{@link MetricMysqlDict} — MySQL 指标 (mysqld_exporter)</li>
 * </ul>
 * 跨平台通用指标（Go Runtime / Process / promhttp）保留在本文件的 {@code ENTRIES} 块中维护。
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

    /**
     * 便捷录入方法 — 自动归类。
     * <p>package-private：供同包下的 {@code Metric*Dict} 子文件调用。</p>
     */
    static void entry(List<MetricEntry> list, String metricName, String chineseName) {
        MetricCategory cat = inferCategoryByPrefix(metricName);
        list.add(new MetricEntry(metricName, chineseName, cat));
    }

    // =============================================
    // 4. 指标定义汇编区 — 由各子字典 contribute() 聚合
    // =============================================
    //   日常新增指标：
    //   - node_*     → 编辑 MetricLinuxDict.java
    //   - windows_*  → 编辑 MetricWinDict.java
    //   - mysql_*    → 编辑 MetricMysqlDict.java
    //   - go_* / process_* / promhttp_* → 在本文件静态块末尾追加
    // =============================================

    /** 所有已知指标的完整定义列表。由各子字典 contribute() 聚合后不可变。 */
    public static final List<MetricEntry> ENTRIES;

    static {
        List<MetricEntry> list = new ArrayList<>();

        // 1. Linux 节点指标 (node_exporter)
        MetricLinuxDict.contribute(list);

        // 2. Windows 节点指标 (windows_exporter)
        MetricWinDict.contribute(list);

        // 3. MySQL 指标 (mysqld_exporter)
        MetricMysqlDict.contribute(list);

        // 4. 跨平台通用指标 (Go Runtime / Process / Exporter 自监控)
        //    所有 Prometheus exporter 均由 Go 编写，以下 go_* / process_* / promhttp_* 指标在各平台通用
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
        entry(list, "promhttp_metric_handler_errors_total",      "Prometheus HTTP处理器错误总数");
        entry(list, "promhttp_metric_handler_requests_in_flight", "Prometheus HTTP处理中请求数");
        entry(list, "promhttp_metric_handler_requests_total",    "Prometheus HTTP处理器请求总数");

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

package com.apex.config;

import java.util.List;

/**
 * JVM 指标字典 — Actuator / JMX Exporter 通用，包含所有 JVM 运行时指标的中文翻译。
 * <p>
 * 通过 {@link #contribute(List)} 向主字典 {@link MetricDictionary} 注册条目。
 * </p>
 */
public final class MetricJvmDict {

    private MetricJvmDict() {}

    /**
     * 向指定列表注入所有 JVM 指标定义。
     *
     * @param list 目标条目列表
     */
    public static void contribute(List<MetricDictionary.MetricEntry> list) {
        // ===== JVM 内存 =====
        MetricDictionary.entry(list, "jvm_memory_used_bytes", "JVM 内存已使用");
        MetricDictionary.entry(list, "jvm_memory_committed_bytes", "JVM 内存已提交");
        MetricDictionary.entry(list, "jvm_memory_max_bytes", "JVM 内存最大值");
        MetricDictionary.entry(list, "jvm_buffer_total_capacity_bytes", "JVM 缓冲区总容量");
        MetricDictionary.entry(list, "jvm_buffer_count_buffers", "JVM 缓冲区数量");
        MetricDictionary.entry(list, "jvm_buffer_memory_used_bytes", "JVM 缓冲区已使用");

        MetricDictionary.entry(list, "jvm_memory_usage_after_gc", "GC后内存使用率");

        // ===== JVM GC =====
        MetricDictionary.entry(list, "jvm_gc_pause_seconds_count", "GC 次数");
        MetricDictionary.entry(list, "jvm_gc_pause_seconds_sum", "GC 累计耗时");
        MetricDictionary.entry(list, "jvm_gc_pause_seconds_max", "GC 最大暂停时间");
        MetricDictionary.entry(list, "jvm_gc_memory_allocated_bytes_total", "GC 后内存分配总量");
        MetricDictionary.entry(list, "jvm_gc_memory_promoted_bytes_total", "GC 晋升内存总量");
        MetricDictionary.entry(list, "jvm_gc_live_data_size_bytes", "GC 存活数据大小");
        MetricDictionary.entry(list, "jvm_gc_max_data_size_bytes", "GC最大数据大小");
        MetricDictionary.entry(list, "jvm_gc_overhead", "GC开销占比");
        MetricDictionary.entry(list, "jvm_gc_concurrent_phase_time_seconds_count", "GC并发阶段执行次数");
        MetricDictionary.entry(list, "jvm_gc_concurrent_phase_time_seconds_sum", "GC并发阶段累计耗时");
        MetricDictionary.entry(list, "jvm_gc_concurrent_phase_time_seconds_max", "GC并发阶段最大耗时");

        // ===== JVM 线程 =====
        MetricDictionary.entry(list, "jvm_threads_live_threads", "活动线程数");
        MetricDictionary.entry(list, "jvm_threads_daemon_threads", "守护线程数");
        MetricDictionary.entry(list, "jvm_threads_peak_threads", "峰值线程数");
        MetricDictionary.entry(list, "jvm_threads_started_threads_total", "累计启动线程数");
        MetricDictionary.entry(list, "jvm_threads_states_threads", "线程状态分布");

        // ===== JVM 类加载 & 编译 =====
        MetricDictionary.entry(list, "jvm_classes_loaded_classes", "已加载类数");
        MetricDictionary.entry(list, "jvm_classes_unloaded_classes_total", "累计卸载类数");
        MetricDictionary.entry(list, "jvm_compilation_time_ms_total", "JVM编译累计耗时(ms)");
        MetricDictionary.entry(list, "jvm_info", "JVM信息");

        // ===== 进程 & 系统 =====
        MetricDictionary.entry(list, "process_cpu_usage", "进程CPU使用率");
        MetricDictionary.entry(list, "process_cpu_time_ns_total", "进程CPU累计时间(纳秒)");
        MetricDictionary.entry(list, "process_uptime_seconds", "进程运行时间");
        MetricDictionary.entry(list, "process_files_max_files", "最大文件描述符");
        MetricDictionary.entry(list, "process_files_open_files", "已打开文件描述符");
        MetricDictionary.entry(list, "system_cpu_count", "系统CPU核心数");
        MetricDictionary.entry(list, "system_cpu_usage", "系统CPU使用率");
        MetricDictionary.entry(list, "disk_free_bytes", "磁盘可用字节");
        MetricDictionary.entry(list, "disk_total_bytes", "磁盘总字节");

        // ===== HTTP 请求（仅 Actuator） =====
        MetricDictionary.entry(list, "http_server_requests_seconds_count", "HTTP请求总数");
        MetricDictionary.entry(list, "http_server_requests_seconds_sum", "HTTP请求总耗时");
        MetricDictionary.entry(list, "http_server_requests_seconds_max", "HTTP请求最大耗时");
        MetricDictionary.entry(list, "http_server_requests_active_seconds_count", "活跃HTTP请求计数");
        MetricDictionary.entry(list, "http_server_requests_active_seconds_sum", "活跃HTTP请求总耗时");
        MetricDictionary.entry(list, "http_server_requests_active_seconds_max", "活跃HTTP请求最大耗时");

        // ===== Tomcat（仅 Actuator） =====
        MetricDictionary.entry(list, "tomcat_sessions_active_current_sessions", "Tomcat当前活跃会话");
        MetricDictionary.entry(list, "tomcat_sessions_active_max_sessions", "Tomcat最大活跃会话");
        MetricDictionary.entry(list, "tomcat_sessions_alive_max_seconds", "Tomcat会话最长存活时间");
        MetricDictionary.entry(list, "tomcat_sessions_created_sessions_total", "Tomcat累计创建会话");
        MetricDictionary.entry(list, "tomcat_sessions_expired_sessions_total", "Tomcat累计过期会话");
        MetricDictionary.entry(list, "tomcat_sessions_rejected_sessions_total", "Tomcat累计拒绝会话");

        // ===== 应用（仅 Actuator） =====
        MetricDictionary.entry(list, "application_started_time_seconds", "应用启动时间");
        MetricDictionary.entry(list, "application_ready_time_seconds", "应用就绪时间");

        // ===== HikariCP 连接池（仅 Actuator） =====
        MetricDictionary.entry(list, "hikaricp_connections", "HikariCP连接数");
        MetricDictionary.entry(list, "hikaricp_connections_active", "HikariCP活跃连接");
        MetricDictionary.entry(list, "hikaricp_connections_idle", "HikariCP空闲连接");
        MetricDictionary.entry(list, "hikaricp_connections_max", "HikariCP最大连接");
        MetricDictionary.entry(list, "hikaricp_connections_min", "HikariCP最小连接");
        MetricDictionary.entry(list, "hikaricp_connections_pending", "HikariCP等待连接");
        MetricDictionary.entry(list, "hikaricp_connections_timeout_total", "HikariCP超时连接");
        MetricDictionary.entry(list, "hikaricp_connections_acquire_seconds_count", "HikariCP连接获取次数");
        MetricDictionary.entry(list, "hikaricp_connections_acquire_seconds_sum", "HikariCP连接获取累计耗时");
        MetricDictionary.entry(list, "hikaricp_connections_acquire_seconds_max", "HikariCP连接获取最大耗时");
        MetricDictionary.entry(list, "hikaricp_connections_creation_seconds_count", "HikariCP连接创建次数");
        MetricDictionary.entry(list, "hikaricp_connections_creation_seconds_sum", "HikariCP连接创建累计耗时");
        MetricDictionary.entry(list, "hikaricp_connections_creation_seconds_max", "HikariCP连接创建最大耗时");
        MetricDictionary.entry(list, "hikaricp_connections_usage_seconds_count", "HikariCP连接使用次数");
        MetricDictionary.entry(list, "hikaricp_connections_usage_seconds_sum", "HikariCP连接使用累计耗时");
        MetricDictionary.entry(list, "hikaricp_connections_usage_seconds_max", "HikariCP连接使用最大耗时");

        // ===== JDBC 连接（仅 Actuator） =====
        MetricDictionary.entry(list, "jdbc_connections_active", "JDBC活跃连接");
        MetricDictionary.entry(list, "jdbc_connections_idle", "JDBC空闲连接");
        MetricDictionary.entry(list, "jdbc_connections_max", "JDBC最大连接");
        MetricDictionary.entry(list, "jdbc_connections_min", "JDBC最小连接");

        // ===== Logback & 定时任务（仅 Actuator） =====
        MetricDictionary.entry(list, "logback_events_total", "Logback日志事件总数");
        MetricDictionary.entry(list, "tasks_scheduled_execution_seconds_count", "定时任务执行次数");
        MetricDictionary.entry(list, "tasks_scheduled_execution_seconds_sum", "定时任务执行累计耗时");
        MetricDictionary.entry(list, "tasks_scheduled_execution_seconds_max", "定时任务执行最大耗时");
        MetricDictionary.entry(list, "tasks_scheduled_execution_active_seconds_count", "定时任务活跃执行次数");
        MetricDictionary.entry(list, "tasks_scheduled_execution_active_seconds_sum", "定时任务活跃执行累计耗时");
        MetricDictionary.entry(list, "tasks_scheduled_execution_active_seconds_max", "定时任务活跃执行最大耗时");
    }
}

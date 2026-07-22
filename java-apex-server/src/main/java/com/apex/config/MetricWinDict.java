package com.apex.config;

import java.util.List;

/**
 * Windows (windows_exporter) 指标字典 — 包含所有 windows_* 前缀的 Prometheus 指标翻译。
 * <p>
 * 通过 {@link #contribute(List)} 向主字典 {@link MetricDictionary} 注册条目。
 * </p>
 */
public final class MetricWinDict {

    private MetricWinDict() {}

    /**
     * 向指定列表注入所有 Windows 节点指标定义。
     *
     * @param list 目标条目列表
     */
    public static void contribute(List<MetricDictionary.MetricEntry> list) {
        // ========== Windows TCP (Port) ==========
        MetricDictionary.entry(list, "windows_tcp_connection_failures_total", "TCP连接失败总数");
        MetricDictionary.entry(list, "windows_tcp_connections_active_total",  "TCP主动连接总数");
        MetricDictionary.entry(list, "windows_tcp_connections_established",   "TCP已建立连接数");
        MetricDictionary.entry(list, "windows_tcp_connections_passive_total", "TCP被动连接总数");
        MetricDictionary.entry(list, "windows_tcp_connections_reset_total",   "TCP连接复位总数");
        MetricDictionary.entry(list, "windows_tcp_connections_state_count",   "TCP连接状态计数");
        MetricDictionary.entry(list, "windows_tcp_segments_received_total",   "TCP段接收总数");
        MetricDictionary.entry(list, "windows_tcp_segments_retransmitted_total","TCP段重传总数");
        MetricDictionary.entry(list, "windows_tcp_segments_sent_total",       "TCP段发送总数");
        MetricDictionary.entry(list, "windows_tcp_segments_total",            "TCP段总数");
        MetricDictionary.entry(list, "windows_listening_port",                "Windows监听端口");

        // ========== Windows CPU ==========
        MetricDictionary.entry(list, "windows_cpu_time_total",              "CPU时间总计");
        MetricDictionary.entry(list, "windows_cpu_clock_interrupts_total",  "CPU时钟中断总数");
        MetricDictionary.entry(list, "windows_cpu_interrupts_total",        "CPU硬件中断总数");
        MetricDictionary.entry(list, "windows_cpu_dpcs_total",              "CPU DPC调用总数");
        MetricDictionary.entry(list, "windows_cpu_idle_break_events_total", "CPU空闲唤醒次数");
        MetricDictionary.entry(list, "windows_cpu_core_frequency_mhz",      "CPU核心频率(MHz)");
        MetricDictionary.entry(list, "windows_cpu_cstate_seconds_total",    "CPU C状态秒数");
        MetricDictionary.entry(list, "windows_cpu_parking_status",                   "CPU Parking状态");
        MetricDictionary.entry(list, "windows_cpu_processor_mperf_total",            "CPU MPerf效率比");
        MetricDictionary.entry(list, "windows_cpu_processor_performance_total",      "CPU性能百分比");
        MetricDictionary.entry(list, "windows_cpu_processor_privileged_utility_total","CPU特权实用率");
        MetricDictionary.entry(list, "windows_cpu_processor_rtc_total",              "CPU RTC中断总数");
        MetricDictionary.entry(list, "windows_cpu_processor_utility_total",          "CPU实用率");
        MetricDictionary.entry(list, "windows_cpu_logical_processor",                "逻辑处理器数");

        // ========== Windows Memory ==========
        MetricDictionary.entry(list, "windows_memory_available_bytes",              "可用内存(字节)");
        MetricDictionary.entry(list, "windows_memory_cache_bytes",                  "缓存内存(字节)");
        MetricDictionary.entry(list, "windows_memory_cache_bytes_peak",             "缓存峰值(字节)");
        MetricDictionary.entry(list, "windows_memory_cache_faults_total",           "缓存错误总数");
        MetricDictionary.entry(list, "windows_memory_committed_bytes",              "已提交内存(字节)");
        MetricDictionary.entry(list, "windows_memory_commit_limit",                 "提交内存上限");
        MetricDictionary.entry(list, "windows_memory_demand_zero_faults_total",     "按需零填充错误总数");
        MetricDictionary.entry(list, "windows_memory_free_and_zero_page_list_bytes","空闲零页列表(字节)");
        MetricDictionary.entry(list, "windows_memory_free_system_page_table_entries","空闲系统页表项");
        MetricDictionary.entry(list, "windows_memory_modified_bytes",               "已修改页(字节)");
        MetricDictionary.entry(list, "windows_memory_modified_page_list_bytes",     "已修改页列表(字节)");
        MetricDictionary.entry(list, "windows_memory_page_faults_total",            "页面错误总数");
        MetricDictionary.entry(list, "windows_memory_physical_total_bytes",         "物理内存总量(字节)");
        MetricDictionary.entry(list, "windows_memory_physical_free_bytes",          "物理空闲内存(字节)");
        MetricDictionary.entry(list, "windows_memory_pool_nonpaged_allocs_total",   "非分页池分配总数");
        MetricDictionary.entry(list, "windows_memory_pool_nonpaged_bytes",          "非分页池(字节)");
        MetricDictionary.entry(list, "windows_memory_pool_paged_allocs_total",      "分页池分配总数");
        MetricDictionary.entry(list, "windows_memory_pool_paged_bytes",             "分页池(字节)");
        MetricDictionary.entry(list, "windows_memory_pool_paged_resident_bytes",    "分页池驻留(字节)");
        MetricDictionary.entry(list, "windows_memory_process_memory_limit_bytes",   "进程内存上限(字节)");
        MetricDictionary.entry(list, "windows_memory_standby_cache_bytes",          "备用缓存(字节)");
        MetricDictionary.entry(list, "windows_memory_standby_cache_core_bytes",     "备用缓存核心(字节)");
        MetricDictionary.entry(list, "windows_memory_standby_cache_normal_priority_bytes","备用缓存正常优先级(字节)");
        MetricDictionary.entry(list, "windows_memory_standby_cache_reserve_bytes",  "备用缓存保留(字节)");
        MetricDictionary.entry(list, "windows_memory_swap_page_operations_total",   "Swap页操作总数");
        MetricDictionary.entry(list, "windows_memory_swap_page_reads_total",        "Swap页读取总数");
        MetricDictionary.entry(list, "windows_memory_swap_page_writes_total",       "Swap页写入总数");
        MetricDictionary.entry(list, "windows_memory_swap_pages_read_total",        "Swap页读取(复数指标)");
        MetricDictionary.entry(list, "windows_memory_swap_pages_written_total",     "Swap页写入(复数指标)");
        MetricDictionary.entry(list, "windows_memory_system_cache_resident_bytes",  "系统缓存驻留(字节)");
        MetricDictionary.entry(list, "windows_memory_system_code_resident_bytes",   "系统代码驻留(字节)");
        MetricDictionary.entry(list, "windows_memory_system_code_total_bytes",      "系统代码总量(字节)");
        MetricDictionary.entry(list, "windows_memory_system_driver_resident_bytes", "系统驱动驻留(字节)");
        MetricDictionary.entry(list, "windows_memory_system_driver_total_bytes",    "系统驱动总量(字节)");
        MetricDictionary.entry(list, "windows_memory_transition_faults_total",      "转换错误总数");
        MetricDictionary.entry(list, "windows_memory_transition_pages_repurposed_total","转换页重映射总数");
        MetricDictionary.entry(list, "windows_memory_write_copies_total",           "写时复制总数");

        // ========== Windows Disk ==========
        MetricDictionary.entry(list, "windows_logical_disk_avg_read_requests_queued",       "逻辑磁盘平均读请求队列");
        MetricDictionary.entry(list, "windows_logical_disk_avg_write_requests_queued",      "逻辑磁盘平均写请求队列");
        MetricDictionary.entry(list, "windows_logical_disk_free_bytes",                     "逻辑磁盘空闲(字节)");
        MetricDictionary.entry(list, "windows_logical_disk_idle_seconds_total",             "逻辑磁盘空闲秒数");
        MetricDictionary.entry(list, "windows_logical_disk_info",                           "逻辑磁盘设备信息");
        MetricDictionary.entry(list, "windows_logical_disk_queue_length",                   "逻辑磁盘队列长度");
        MetricDictionary.entry(list, "windows_logical_disk_read_bytes_total",               "逻辑磁盘读取字节");
        MetricDictionary.entry(list, "windows_logical_disk_read_latency_seconds_total",     "逻辑磁盘读延迟秒数");
        MetricDictionary.entry(list, "windows_logical_disk_read_write_latency_seconds_total","逻辑磁盘读写延迟秒数");
        MetricDictionary.entry(list, "windows_logical_disk_read_seconds_total",             "逻辑磁盘读取耗时");
        MetricDictionary.entry(list, "windows_logical_disk_reads_total",                    "逻辑磁盘读取次数");
        MetricDictionary.entry(list, "windows_logical_disk_requests_queued",                "逻辑磁盘请求队列");
        MetricDictionary.entry(list, "windows_logical_disk_size_bytes",                     "逻辑磁盘总容量(字节)");
        MetricDictionary.entry(list, "windows_logical_disk_split_ios_total",                "逻辑磁盘拆分IO数");
        MetricDictionary.entry(list, "windows_logical_disk_write_bytes_total",              "逻辑磁盘写入字节");
        MetricDictionary.entry(list, "windows_logical_disk_write_latency_seconds_total",    "逻辑磁盘写延迟秒数");
        MetricDictionary.entry(list, "windows_logical_disk_write_seconds_total",            "逻辑磁盘写入耗时");
        MetricDictionary.entry(list, "windows_logical_disk_writes_total",                   "逻辑磁盘写入次数");
        MetricDictionary.entry(list, "windows_physical_disk_idle_seconds_total",            "物理磁盘空闲秒数");
        MetricDictionary.entry(list, "windows_physical_disk_queue_length",                  "物理磁盘队列长度");
        MetricDictionary.entry(list, "windows_physical_disk_read_bytes_total",              "物理磁盘读取字节");
        MetricDictionary.entry(list, "windows_physical_disk_read_latency_seconds_total",    "物理磁盘读延迟秒数");
        MetricDictionary.entry(list, "windows_physical_disk_read_write_latency_seconds_total","物理磁盘读写延迟秒数");
        MetricDictionary.entry(list, "windows_physical_disk_read_seconds_total",            "物理磁盘读取耗时");
        MetricDictionary.entry(list, "windows_physical_disk_reads_total",                   "物理磁盘读取次数");
        MetricDictionary.entry(list, "windows_physical_disk_requests_queued",               "物理磁盘请求队列");
        MetricDictionary.entry(list, "windows_physical_disk_size_bytes",                    "物理磁盘总容量");
        MetricDictionary.entry(list, "windows_physical_disk_split_ios_total",               "物理磁盘拆分IO数");
        MetricDictionary.entry(list, "windows_physical_disk_write_bytes_total",             "物理磁盘写入字节");
        MetricDictionary.entry(list, "windows_physical_disk_write_latency_seconds_total",   "物理磁盘写延迟秒数");
        MetricDictionary.entry(list, "windows_physical_disk_write_seconds_total",           "物理磁盘写入耗时");
        MetricDictionary.entry(list, "windows_physical_disk_writes_total",                  "物理磁盘写入次数");

        // ========== Windows Network ==========
        MetricDictionary.entry(list, "windows_net_bytes_received_total",                "网络接收字节总数");
        MetricDictionary.entry(list, "windows_net_bytes_sent_total",                    "网络发送字节总数");
        MetricDictionary.entry(list, "windows_net_bytes_total",                         "网络总字节数");
        MetricDictionary.entry(list, "windows_net_current_bandwidth",                   "网卡带宽(bps)");
        MetricDictionary.entry(list, "windows_net_current_bandwidth_bytes",             "网卡带宽(字节)");
        MetricDictionary.entry(list, "windows_net_nic_address_info",                    "网卡地址信息");
        MetricDictionary.entry(list, "windows_net_nic_info",                            "网卡接口信息");
        MetricDictionary.entry(list, "windows_net_nic_operation_status",                "网卡运行状态");
        MetricDictionary.entry(list, "windows_net_output_queue_length",                 "网卡输出队列");
        MetricDictionary.entry(list, "windows_net_output_queue_length_packets",         "网卡输出队列(包)");
        MetricDictionary.entry(list, "windows_net_packets_outbound_discarded_total",    "网络发送丢弃");
        MetricDictionary.entry(list, "windows_net_packets_outbound_errors_total",       "网络发送错误");
        MetricDictionary.entry(list, "windows_net_packets_received_discarded_total",    "网络接收丢弃");
        MetricDictionary.entry(list, "windows_net_packets_received_errors_total",       "网络接收错误");
        MetricDictionary.entry(list, "windows_net_packets_received_total",              "网络接收包总数");
        MetricDictionary.entry(list, "windows_net_packets_received_unknown_total",      "网络接收未知包总数");
        MetricDictionary.entry(list, "windows_net_packets_sent_total",                  "网络发送包总数");
        MetricDictionary.entry(list, "windows_net_packets_total",                       "网络包总数");

        // ========== Windows Service ==========
        MetricDictionary.entry(list, "windows_service_info",       "服务信息");
        MetricDictionary.entry(list, "windows_service_state",      "服务运行状态");
        MetricDictionary.entry(list, "windows_service_start_mode", "服务启动模式");
        MetricDictionary.entry(list, "windows_service_process",    "服务进程信息");

        // ========== Windows OS / System ==========
        MetricDictionary.entry(list, "windows_os_info",                       "操作系统信息");
        MetricDictionary.entry(list, "windows_os_hostname",                   "主机名");
        MetricDictionary.entry(list, "windows_system_boot_time_timestamp",     "系统启动时间戳");
        MetricDictionary.entry(list, "windows_system_context_switches_total",  "上下文切换总数");
        MetricDictionary.entry(list, "windows_system_exception_dispatches_total","异常派发总数");
        MetricDictionary.entry(list, "windows_system_processes",               "进程数");
        MetricDictionary.entry(list, "windows_system_processes_limit",         "进程数上限");
        MetricDictionary.entry(list, "windows_system_processor_queue_length",  "处理器队列长度");
        MetricDictionary.entry(list, "windows_system_system_calls_total",      "系统调用总数");
        MetricDictionary.entry(list, "windows_system_threads",                 "线程数");

        // ========== Windows Process ==========
        MetricDictionary.entry(list, "windows_process_cpu_time_total",             "进程CPU时间总计");
        MetricDictionary.entry(list, "windows_process_handles",                    "进程句柄数");
        MetricDictionary.entry(list, "windows_process_info",                       "进程信息");
        MetricDictionary.entry(list, "windows_process_io_bytes_total",             "进程IO字节总数");
        MetricDictionary.entry(list, "windows_process_io_operations_total",        "进程IO操作总数");
        MetricDictionary.entry(list, "windows_process_page_faults_total",          "进程页面错误总数");
        MetricDictionary.entry(list, "windows_process_page_file_bytes",            "进程页面文件(字节)");
        MetricDictionary.entry(list, "windows_process_pool_bytes",                 "进程池内存(字节)");
        MetricDictionary.entry(list, "windows_process_priority_base",              "进程优先级基数");
        MetricDictionary.entry(list, "windows_process_private_bytes",              "进程私有内存(字节)");
        MetricDictionary.entry(list, "windows_process_start_time_seconds_timestamp","进程启动时间戳");
        MetricDictionary.entry(list, "windows_process_threads",                    "进程线程数");
        MetricDictionary.entry(list, "windows_process_virtual_bytes",              "进程虚拟内存(字节)");
        MetricDictionary.entry(list, "windows_process_working_set_bytes",          "进程工作集(字节)");
        MetricDictionary.entry(list, "windows_process_working_set_peak_bytes",     "进程工作集峰值(字节)");
        MetricDictionary.entry(list, "windows_process_working_set_private_bytes",  "进程私有工作集(字节)");

        // ========== Windows Exporter 自监控 ==========
        MetricDictionary.entry(list, "windows_exporter_build_info",               "Exporter构建信息");
        MetricDictionary.entry(list, "windows_exporter_collector_duration_seconds","Exporter采集耗时(秒)");
        MetricDictionary.entry(list, "windows_exporter_collector_success",        "Exporter采集成功状态");
        MetricDictionary.entry(list, "windows_exporter_collector_timeout",        "Exporter采集超时状态");
        MetricDictionary.entry(list, "windows_exporter_scrape_duration_seconds",  "Exporter抓取耗时(秒)");
    }
}

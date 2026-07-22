package com.apex.config;

import java.util.List;

/**
 * Linux (node_exporter) 指标字典 — 包含所有 node_* 前缀的 Prometheus 指标翻译。
 * <p>
 * 通过 {@link #contribute(List)} 向主字典 {@link MetricDictionary} 注册条目。
 * </p>
 */
public final class MetricLinuxDict {

    private MetricLinuxDict() {}

    /**
     * 向指定列表注入所有 Linux 节点指标定义。
     *
     * @param list 目标条目列表
     */
    public static void contribute(List<MetricDictionary.MetricEntry> list) {
        // ========== CPU ==========
        MetricDictionary.entry(list, "node_cpu_seconds_total",              "CPU累计秒数");
        MetricDictionary.entry(list, "node_cpu_guest_seconds_total",        "CPU虚拟客户机秒数");
        MetricDictionary.entry(list, "node_schedstat_running_seconds_total","CPU调度运行秒数");
        MetricDictionary.entry(list, "node_schedstat_timeslices_total",     "CPU调度时间片总数");
        MetricDictionary.entry(list, "node_schedstat_waiting_seconds_total","CPU调度等待秒数");
        MetricDictionary.entry(list, "node_load1",                          "1分钟平均负载");
        MetricDictionary.entry(list, "node_load5",                          "5分钟平均负载");
        MetricDictionary.entry(list, "node_load15",                         "15分钟平均负载");
        MetricDictionary.entry(list, "node_procs_blocked",                  "阻塞进程数");
        MetricDictionary.entry(list, "node_procs_running",                  "运行中进程数");

        // ========== Memory ==========
        MetricDictionary.entry(list, "node_memory_MemTotal_bytes",      "物理内存总量(字节)");
        MetricDictionary.entry(list, "node_memory_MemFree_bytes",       "物理空闲内存(字节)");
        MetricDictionary.entry(list, "node_memory_MemAvailable_bytes",  "物理可用内存(字节)");
        MetricDictionary.entry(list, "node_memory_Buffers_bytes",       "缓冲区内存(字节)");
        MetricDictionary.entry(list, "node_memory_Cached_bytes",        "缓存内存(字节)");
        MetricDictionary.entry(list, "node_memory_SwapCached_bytes",    "Swap缓存(字节)");
        MetricDictionary.entry(list, "node_memory_Active_bytes",        "活跃内存(字节)");
        MetricDictionary.entry(list, "node_memory_Inactive_bytes",      "非活跃内存(字节)");
        MetricDictionary.entry(list, "node_memory_AnonPages_bytes",     "匿名页内存(字节)");
        MetricDictionary.entry(list, "node_memory_Mapped_bytes",        "映射内存(字节)");
        MetricDictionary.entry(list, "node_memory_Shmem_bytes",         "共享内存(字节)");
        MetricDictionary.entry(list, "node_memory_Slab_bytes",          "Slab内核内存(字节)");
        MetricDictionary.entry(list, "node_memory_SReclaimable_bytes",  "可回收Slab(字节)");
        MetricDictionary.entry(list, "node_memory_SUnreclaim_bytes",    "不可回收Slab(字节)");
        MetricDictionary.entry(list, "node_memory_KernelStack_bytes",   "内核栈内存(字节)");
        MetricDictionary.entry(list, "node_memory_PageTables_bytes",    "页表内存(字节)");
        MetricDictionary.entry(list, "node_memory_SwapTotal_bytes",     "Swap总量(字节)");
        MetricDictionary.entry(list, "node_memory_SwapFree_bytes",      "Swap空闲(字节)");
        MetricDictionary.entry(list, "node_memory_Dirty_bytes",         "脏页(字节)");
        MetricDictionary.entry(list, "node_memory_Writeback_bytes",     "回写页(字节)");
        MetricDictionary.entry(list, "node_memory_HugePages_Total",     "大页总数");
        MetricDictionary.entry(list, "node_memory_HugePages_Free",      "大页空闲");
        MetricDictionary.entry(list, "node_memory_HugePages_Rsvd",      "大页预留");
        MetricDictionary.entry(list, "node_memory_HugePages_Surp",      "大页盈余");
        MetricDictionary.entry(list, "node_memory_Hugepagesize_bytes",  "大页尺寸(字节)");
        MetricDictionary.entry(list, "node_memory_VmallocTotal_bytes",  "Vmalloc总量(字节)");
        MetricDictionary.entry(list, "node_memory_VmallocUsed_bytes",   "Vmalloc使用(字节)");
        MetricDictionary.entry(list, "node_memory_VmallocChunk_bytes",  "Vmalloc最大块(字节)");
        MetricDictionary.entry(list, "node_memory_Active_anon_bytes",    "活跃匿名页(字节)");
        MetricDictionary.entry(list, "node_memory_Active_file_bytes",    "活跃文件页(字节)");
        MetricDictionary.entry(list, "node_memory_AnonHugePages_bytes",  "匿名大页(字节)");
        MetricDictionary.entry(list, "node_memory_Bounce_bytes",          "Bounce缓冲区(字节)");
        MetricDictionary.entry(list, "node_memory_CommitLimit_bytes",     "提交内存上限(字节)");
        MetricDictionary.entry(list, "node_memory_Committed_AS_bytes",    "已提交地址空间(字节)");
        MetricDictionary.entry(list, "node_memory_DirectMap1G_bytes",     "DirectMap 1G页(字节)");
        MetricDictionary.entry(list, "node_memory_DirectMap2M_bytes",     "DirectMap 2M页(字节)");
        MetricDictionary.entry(list, "node_memory_DirectMap4k_bytes",     "DirectMap 4K页(字节)");
        MetricDictionary.entry(list, "node_memory_HardwareCorrupted_bytes","硬件损坏内存(字节)");
        MetricDictionary.entry(list, "node_memory_Inactive_anon_bytes",   "非活跃匿名页(字节)");
        MetricDictionary.entry(list, "node_memory_Inactive_file_bytes",   "非活跃文件页(字节)");
        MetricDictionary.entry(list, "node_memory_Mlocked_bytes",          "锁定内存(字节)");
        MetricDictionary.entry(list, "node_memory_NFS_Unstable_bytes",     "NFS不稳定页(字节)");
        MetricDictionary.entry(list, "node_memory_Percpu_bytes",           "Per-CPU分配(字节)");
        MetricDictionary.entry(list, "node_memory_ShmemHugePages_bytes",   "共享内存大页(字节)");
        MetricDictionary.entry(list, "node_memory_ShmemPmdMapped_bytes",   "共享内存PMD映射(字节)");
        MetricDictionary.entry(list, "node_memory_Unevictable_bytes",      "不可回收内存(字节)");
        MetricDictionary.entry(list, "node_memory_WritebackTmp_bytes",     "临时回写页(字节)");

        // ========== Disk IO ==========
        MetricDictionary.entry(list, "node_disk_read_bytes_total",              "磁盘读取字节总数");
        MetricDictionary.entry(list, "node_disk_written_bytes_total",           "磁盘写入字节总数");
        MetricDictionary.entry(list, "node_disk_reads_completed_total",         "磁盘读取完成次数");
        MetricDictionary.entry(list, "node_disk_writes_completed_total",        "磁盘写入完成次数");
        MetricDictionary.entry(list, "node_disk_read_time_seconds_total",       "磁盘读取耗时(秒)");
        MetricDictionary.entry(list, "node_disk_write_time_seconds_total",      "磁盘写入耗时(秒)");
        MetricDictionary.entry(list, "node_disk_io_time_seconds_total",         "磁盘IO耗时(秒)");
        MetricDictionary.entry(list, "node_disk_discard_time_seconds_total",    "磁盘discard耗时");
        MetricDictionary.entry(list, "node_disk_flush_requests_time_seconds_total","磁盘flush耗时");
        MetricDictionary.entry(list, "node_disk_reads_merged_total",            "磁盘读合并数");
        MetricDictionary.entry(list, "node_disk_writes_merged_total",           "磁盘写合并数");
        MetricDictionary.entry(list, "node_disk_info",                          "磁盘设备信息");
        MetricDictionary.entry(list, "node_disk_discarded_sectors_total",       "磁盘废弃扇区总数");
        MetricDictionary.entry(list, "node_disk_discards_completed_total",      "磁盘废弃操作完成次数");
        MetricDictionary.entry(list, "node_disk_discards_merged_total",         "磁盘废弃合并数");
        MetricDictionary.entry(list, "node_disk_flush_requests_total",          "磁盘Flush请求总数");
        MetricDictionary.entry(list, "node_disk_io_now",                        "磁盘当前IO数");
        MetricDictionary.entry(list, "node_disk_io_time_weighted_seconds_total","磁盘IO加权耗时(秒)");
        MetricDictionary.entry(list, "node_disk_filesystem_info",               "磁盘文件系统信息");

        // ========== Filesystem ==========
        MetricDictionary.entry(list, "node_filesystem_size_bytes",    "文件系统总容量(字节)");
        MetricDictionary.entry(list, "node_filesystem_free_bytes",    "文件系统空闲(字节)");
        MetricDictionary.entry(list, "node_filesystem_avail_bytes",   "文件系统可用(字节)");
        MetricDictionary.entry(list, "node_filesystem_files",         "文件系统inode总数");
        MetricDictionary.entry(list, "node_filesystem_files_free",    "文件系统inode空闲");
        MetricDictionary.entry(list, "node_filesystem_readonly",      "文件系统只读");
        MetricDictionary.entry(list, "node_filesystem_device_error",  "文件系统设备错误");
        MetricDictionary.entry(list, "node_filesystem_mount_info",    "文件系统挂载信息");
        MetricDictionary.entry(list, "node_filesystem_purgeable_bytes","文件系统可清除(字节)");

        // ========== Network ==========
        MetricDictionary.entry(list, "node_network_receive_bytes_total",    "网络接收字节总数");
        MetricDictionary.entry(list, "node_network_transmit_bytes_total",   "网络发送字节总数");
        MetricDictionary.entry(list, "node_network_receive_packets_total",  "网络接收包总数");
        MetricDictionary.entry(list, "node_network_transmit_packets_total", "网络发送包总数");
        MetricDictionary.entry(list, "node_network_receive_errs_total",     "网络接收错误数");
        MetricDictionary.entry(list, "node_network_transmit_errs_total",    "网络发送错误数");
        MetricDictionary.entry(list, "node_network_receive_drop_total",     "网络接收丢包数");
        MetricDictionary.entry(list, "node_network_transmit_drop_total",    "网络发送丢包数");
        MetricDictionary.entry(list, "node_network_speed_bytes",            "网卡速率(字节/秒)");
        MetricDictionary.entry(list, "node_network_mtu_bytes",              "网卡MTU");
        MetricDictionary.entry(list, "node_network_info",                   "网卡信息");
        MetricDictionary.entry(list, "node_network_carrier",                "网卡载波");
        MetricDictionary.entry(list, "node_network_iface_id",               "网卡接口ID");
        MetricDictionary.entry(list, "node_network_carrier_changes_total",     "载波变化总数");
        MetricDictionary.entry(list, "node_network_carrier_down_changes_total","载波断开次数");
        MetricDictionary.entry(list, "node_network_carrier_up_changes_total",  "载波连接次数");
        MetricDictionary.entry(list, "node_network_device_id",                 "网卡设备ID");
        MetricDictionary.entry(list, "node_network_dormant",                   "网卡休眠状态");
        MetricDictionary.entry(list, "node_network_flags",                     "网卡标志位");
        MetricDictionary.entry(list, "node_network_iface_link",                "网卡链路状态");
        MetricDictionary.entry(list, "node_network_iface_link_mode",           "网卡链路模式");
        MetricDictionary.entry(list, "node_network_name_assign_type",          "网卡名称分配类型");
        MetricDictionary.entry(list, "node_network_net_dev_group",             "网卡设备组");
        MetricDictionary.entry(list, "node_network_protocol_type",              "网卡协议类型");
        MetricDictionary.entry(list, "node_network_receive_compressed_total",   "网络接收压缩包总数");
        MetricDictionary.entry(list, "node_network_receive_fifo_total",         "网络接收FIFO错误");
        MetricDictionary.entry(list, "node_network_receive_frame_total",        "网络接收帧对齐错误");
        MetricDictionary.entry(list, "node_network_receive_multicast_total",    "网络接收多播包总数");
        MetricDictionary.entry(list, "node_network_transmit_carrier_total",     "网络发送载波错误");
        MetricDictionary.entry(list, "node_network_transmit_compressed_total",  "网络发送压缩包总数");
        MetricDictionary.entry(list, "node_network_transmit_fifo_total",        "网络发送FIFO错误");
        MetricDictionary.entry(list, "node_network_address_assign_type",         "网卡地址分配类型");
        MetricDictionary.entry(list, "node_network_receive_nohandler_total",     "网络接收无处理器丢包");
        MetricDictionary.entry(list, "node_network_transmit_colls_total",        "网络发送冲突总数");
        MetricDictionary.entry(list, "node_network_transmit_queue_length",      "网络发送队列长度");
        MetricDictionary.entry(list, "node_network_up",                         "网卡启用状态");

        // ========== Netstat ==========
        MetricDictionary.entry(list, "node_netstat_Icmp_InMsgs",     "ICMP入站消息");
        MetricDictionary.entry(list, "node_netstat_Icmp_OutMsgs",    "ICMP出站消息");
        MetricDictionary.entry(list, "node_netstat_Udp_InDatagrams", "UDP入站数据报");
        MetricDictionary.entry(list, "node_netstat_Udp_OutDatagrams","UDP出站数据报");
        MetricDictionary.entry(list, "node_netstat_Ip_Forwarding",   "IP转发");
        MetricDictionary.entry(list, "node_netstat_Icmp6_InErrors",   "ICMPv6入站错误");
        MetricDictionary.entry(list, "node_netstat_Icmp6_InMsgs",     "ICMPv6入站消息");
        MetricDictionary.entry(list, "node_netstat_Icmp6_OutMsgs",    "ICMPv6出站消息");
        MetricDictionary.entry(list, "node_netstat_Icmp_InErrors",    "ICMP入站错误");
        MetricDictionary.entry(list, "node_netstat_Ip6_InOctets",     "IPv6入站字节");
        MetricDictionary.entry(list, "node_netstat_Ip6_OutOctets",    "IPv6出站字节");
        MetricDictionary.entry(list, "node_netstat_IpExt_InOctets",   "IP扩展入站字节");
        MetricDictionary.entry(list, "node_netstat_IpExt_OutOctets",  "IP扩展出站字节");
        MetricDictionary.entry(list, "node_netstat_TcpExt_ListenDrops",     "TCP监听丢弃");
        MetricDictionary.entry(list, "node_netstat_TcpExt_ListenOverflows", "TCP监听溢出");
        MetricDictionary.entry(list, "node_netstat_TcpExt_SyncookiesFailed","TCP Syncookie失败");
        MetricDictionary.entry(list, "node_netstat_TcpExt_SyncookiesRecv",  "TCP Syncookie接收");
        MetricDictionary.entry(list, "node_netstat_TcpExt_SyncookiesSent",  "TCP Syncookie发送");
        MetricDictionary.entry(list, "node_netstat_TcpExt_TCPOFOQueue",     "TCP乱序队列");
        MetricDictionary.entry(list, "node_netstat_TcpExt_TCPRcvQDrop",     "TCP接收队列丢弃");
        MetricDictionary.entry(list, "node_netstat_TcpExt_TCPSynRetrans",   "TCP SYN重传");
        MetricDictionary.entry(list, "node_netstat_TcpExt_TCPTimeouts",     "TCP超时次数");
        MetricDictionary.entry(list, "node_netstat_Tcp_ActiveOpens",  "TCP主动打开数");
        MetricDictionary.entry(list, "node_netstat_Tcp_InErrs",       "TCP入站错误");
        MetricDictionary.entry(list, "node_netstat_Tcp_OutRsts",      "TCP出站RST");
        MetricDictionary.entry(list, "node_netstat_Tcp_PassiveOpens", "TCP被动打开数");
        MetricDictionary.entry(list, "node_netstat_Udp6_InDatagrams",  "UDPv6入站数据报");
        MetricDictionary.entry(list, "node_netstat_Udp6_InErrors",     "UDPv6入站错误");
        MetricDictionary.entry(list, "node_netstat_Udp6_NoPorts",      "UDPv6无端口");
        MetricDictionary.entry(list, "node_netstat_Udp6_OutDatagrams", "UDPv6出站数据报");
        MetricDictionary.entry(list, "node_netstat_Udp6_RcvbufErrors", "UDPv6接收缓冲错误");
        MetricDictionary.entry(list, "node_netstat_Udp6_SndbufErrors", "UDPv6发送缓冲错误");
        MetricDictionary.entry(list, "node_netstat_Udp_InErrors",     "UDP入站错误");
        MetricDictionary.entry(list, "node_netstat_Udp_NoPorts",      "UDP无端口");
        MetricDictionary.entry(list, "node_netstat_Udp_RcvbufErrors", "UDP接收缓冲错误");
        MetricDictionary.entry(list, "node_netstat_Udp_SndbufErrors", "UDP发送缓冲错误");
        MetricDictionary.entry(list, "node_netstat_UdpLite6_InErrors", "UDP-Litev6入站错误");
        MetricDictionary.entry(list, "node_netstat_UdpLite_InErrors",  "UDP-Lite入站错误");

        // ========== System ==========
        MetricDictionary.entry(list, "node_boot_time_seconds",            "系统启动时间戳");
        MetricDictionary.entry(list, "node_time_seconds",                 "当前系统时间戳");
        MetricDictionary.entry(list, "node_context_switches_total",       "上下文切换总数");
        MetricDictionary.entry(list, "node_intr_total",                   "中断总数");
        MetricDictionary.entry(list, "node_forks_total",                  "fork总数");
        MetricDictionary.entry(list, "node_entropy_available_bits",       "熵池可用位");
        MetricDictionary.entry(list, "node_filefd_allocated",             "已分配文件描述符");
        MetricDictionary.entry(list, "node_filefd_maximum",               "文件描述符上限");
        MetricDictionary.entry(list, "node_nf_conntrack_entries",         "连接跟踪条目数");
        MetricDictionary.entry(list, "node_nf_conntrack_entries_limit",   "连接跟踪上限");
        MetricDictionary.entry(list, "node_arp_entries",                  "ARP表条目数");
        MetricDictionary.entry(list, "node_os_info",                      "操作系统信息");
        MetricDictionary.entry(list, "node_uname_info",                   "系统uname信息");
        MetricDictionary.entry(list, "node_os_version",                   "操作系统版本号");
        MetricDictionary.entry(list, "node_selinux_enabled",              "SELinux状态");
        MetricDictionary.entry(list, "node_time_zone_offset_seconds",     "时区偏移(秒)");
        MetricDictionary.entry(list, "node_cooling_device_cur_state",     "散热设备当前状态");
        MetricDictionary.entry(list, "node_cooling_device_max_state",     "散热设备最大状态");
        MetricDictionary.entry(list, "node_dmi_info",                     "DMI硬件信息");
        MetricDictionary.entry(list, "node_entropy_pool_size_bits",       "熵池大小(位)");
        MetricDictionary.entry(list, "node_exporter_build_info",          "Node Exporter构建信息");
        MetricDictionary.entry(list, "node_processes_max_processes",      "进程数上限");
        MetricDictionary.entry(list, "node_processes_max_threads",        "线程数上限");
        MetricDictionary.entry(list, "node_processes_pids",               "当前PID数");
        MetricDictionary.entry(list, "node_processes_state",              "进程状态分布");
        MetricDictionary.entry(list, "node_processes_threads",            "线程总数");
        MetricDictionary.entry(list, "node_processes_threads_state",      "线程状态分布");
        MetricDictionary.entry(list, "node_scrape_collector_duration_seconds","Exporter采集器耗时(秒)");
        MetricDictionary.entry(list, "node_scrape_collector_success",     "Exporter采集器成功状态");
        MetricDictionary.entry(list, "node_tcp_connection_states",        "TCP连接状态分布");
        MetricDictionary.entry(list, "node_textfile_scrape_error",        "Textfile抓取错误");
        MetricDictionary.entry(list, "node_time_clocksource_available_info","可用时钟源信息");
        MetricDictionary.entry(list, "node_time_clocksource_current_info", "当前时钟源信息");

        // ========== VMStat / Pressure ==========
        MetricDictionary.entry(list, "node_vmstat_oom_kill",                      "OOM Kill次数");
        MetricDictionary.entry(list, "node_vmstat_pgfault",                       "页面错误次数");
        MetricDictionary.entry(list, "node_vmstat_pgmajfault",                    "主页面错误次数");
        MetricDictionary.entry(list, "node_vmstat_pgpgin",                        "页换入次数");
        MetricDictionary.entry(list, "node_vmstat_pgpgout",                       "页换出次数");
        MetricDictionary.entry(list, "node_vmstat_pswpin",                        "换入页数");
        MetricDictionary.entry(list, "node_vmstat_pswpout",                       "换出页数");
        MetricDictionary.entry(list, "node_pressure_cpu_waiting_seconds_total",   "CPU压力等待秒数");
        MetricDictionary.entry(list, "node_pressure_io_stalled_seconds_total",    "IO压力停滞秒数");
        MetricDictionary.entry(list, "node_pressure_io_waiting_seconds_total",    "IO压力等待秒数");
        MetricDictionary.entry(list, "node_pressure_memory_stalled_seconds_total","内存压力停滞秒数");
        MetricDictionary.entry(list, "node_pressure_memory_waiting_seconds_total","内存压力等待秒数");
        MetricDictionary.entry(list, "node_timex_estimated_error_seconds",        "时间同步估计误差(秒)");
        MetricDictionary.entry(list, "node_timex_frequency_adjustment_ratio",     "时间同步频率调整比率");
        MetricDictionary.entry(list, "node_timex_loop_time_constant",             "时间同步环路时间常数");
        MetricDictionary.entry(list, "node_timex_maxerror_seconds",               "时间同步最大误差(秒)");
        MetricDictionary.entry(list, "node_timex_offset_seconds",                 "时间同步偏移(秒)");
        MetricDictionary.entry(list, "node_timex_pps_calibration_total",          "PPS校准总数");
        MetricDictionary.entry(list, "node_timex_pps_error_total",                "PPS错误总数");
        MetricDictionary.entry(list, "node_timex_pps_frequency_hertz",            "PPS频率(Hz)");
        MetricDictionary.entry(list, "node_timex_pps_jitter_seconds",             "PPS抖动(秒)");
        MetricDictionary.entry(list, "node_timex_pps_jitter_total",               "PPS抖动总数");
        MetricDictionary.entry(list, "node_timex_pps_shift_seconds",              "PPS偏移(秒)");
        MetricDictionary.entry(list, "node_timex_pps_stability_exceeded_total",   "PPS稳定性超限总数");
        MetricDictionary.entry(list, "node_timex_pps_stability_hertz",            "PPS稳定性(Hz)");
        MetricDictionary.entry(list, "node_timex_status",                         "时间同步状态码");
        MetricDictionary.entry(list, "node_timex_sync_status",                    "时间同步状态");
        MetricDictionary.entry(list, "node_timex_tai_offset_seconds",             "TAI时间偏移(秒)");
        MetricDictionary.entry(list, "node_timex_tick_seconds",                   "时钟滴答(秒)");
        MetricDictionary.entry(list, "node_udp_queues",                           "UDP队列长度");

        // ========== Sockstat / Softnet ==========
        MetricDictionary.entry(list, "node_sockstat_UDP_inuse",       "UDP套接字使用中");
        MetricDictionary.entry(list, "node_sockstat_sockets_used",    "已使用套接字");
        MetricDictionary.entry(list, "node_sockstat_FRAG6_inuse",     "IPv6分片套接字使用中");
        MetricDictionary.entry(list, "node_sockstat_FRAG6_memory",    "IPv6分片套接字内存");
        MetricDictionary.entry(list, "node_sockstat_FRAG_inuse",      "IPv4分片套接字使用中");
        MetricDictionary.entry(list, "node_sockstat_FRAG_memory",     "IPv4分片套接字内存");
        MetricDictionary.entry(list, "node_sockstat_RAW6_inuse",      "IPv6 RAW套接字使用中");
        MetricDictionary.entry(list, "node_sockstat_RAW_inuse",       "IPv4 RAW套接字使用中");
        MetricDictionary.entry(list, "node_sockstat_TCP6_inuse",      "TCPv6套接字使用中");
        MetricDictionary.entry(list, "node_sockstat_UDP6_inuse",      "UDPv6套接字使用中");
        MetricDictionary.entry(list, "node_sockstat_UDPLITE6_inuse",  "UDP-Litev6套接字使用中");
        MetricDictionary.entry(list, "node_sockstat_UDPLITE_inuse",   "UDP-Lite套接字使用中");
        MetricDictionary.entry(list, "node_sockstat_UDP_mem",         "UDP套接字内存(页)");
        MetricDictionary.entry(list, "node_sockstat_UDP_mem_bytes",   "UDP套接字内存(字节)");
        MetricDictionary.entry(list, "node_softnet_processed_total",  "软中断处理数");
        MetricDictionary.entry(list, "node_softnet_dropped_total",    "软中断丢包数");
        MetricDictionary.entry(list, "node_softnet_backlog_len",       "软中断backlog长度");
        MetricDictionary.entry(list, "node_softnet_cpu_collision_total","软中断CPU冲突总数");
        MetricDictionary.entry(list, "node_softnet_flow_limit_count_total","软中断流控限制总数");
        MetricDictionary.entry(list, "node_softnet_received_rps_total","软中断RPS接收总数");
        MetricDictionary.entry(list, "node_softnet_times_squeezed_total","软中断挤压次数");

        // ========== Port (TCP 连接与传输 - Linux) ==========
        MetricDictionary.entry(list, "node_netstat_Tcp_CurrEstab",           "TCP当前连接数");
        MetricDictionary.entry(list, "node_netstat_Tcp_InSegs",              "TCP入站段数");
        MetricDictionary.entry(list, "node_netstat_Tcp_OutSegs",             "TCP出站段数");
        MetricDictionary.entry(list, "node_netstat_Tcp_RetransSegs",         "TCP重传段数");
        MetricDictionary.entry(list, "node_sockstat_TCP_inuse",               "TCP套接字使用中");
        MetricDictionary.entry(list, "node_sockstat_TCP_alloc",               "TCP套接字已分配");
        MetricDictionary.entry(list, "node_sockstat_TCP_mem",                 "TCP套接字内存(页)");
        MetricDictionary.entry(list, "node_sockstat_TCP_mem_bytes",           "TCP套接字内存(字节)");
        MetricDictionary.entry(list, "node_sockstat_TCP_orphan",              "TCP孤儿套接字");
        MetricDictionary.entry(list, "node_sockstat_TCP_tw",                  "TCP TIME_WAIT套接字");
        MetricDictionary.entry(list, "node_listening_port",                   "Linux监听端口");
    }
}

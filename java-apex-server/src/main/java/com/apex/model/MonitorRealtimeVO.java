package com.apex.model;

import java.util.List;

/**
 * 实时指标返回 VO。
 */
public record MonitorRealtimeVO(
        int machineId,
        boolean reachable,
        String errorMsg,
        double cpuUsage,
        double memUsage,
        double diskUsage,
        long networkRxBytes,
        long networkTxBytes,
        long uptimeSeconds,
        double loadAvg1,
        double loadAvg5,
        double loadAvg15,
        List<PortStatusVO> ports,
        // MySQL 专用字段（非 MySQL 时为 0）
        long mysqlConnections,
        long mysqlMaxConnections,
        double mysqlBufferPoolHitRate,
        long mysqlSlowQueries,
        long mysqlQueriesTotal,
        long mysqlThreadsRunning,
        // JAVA 专用字段（非 JAVA 时均为 0）
        double jvmHeapUsage,          // 堆内存使用率 0-100
        double jvmGcPauseSeconds,     // GC 累计暂停秒数
        double jvmGcCount,            // GC 累计次数
        double jvmThreadCount,        // 活动线程数
        double jvmDaemonThreadCount,  // 守护线程数
        double processCpuUsage,       // 进程 CPU 使用率 0-100
        double httpRequestCount,      // HTTP 累计请求数（仅 Actuator）
        double appUptimeSeconds       // 应用启动时间秒（仅 Actuator）
) {}


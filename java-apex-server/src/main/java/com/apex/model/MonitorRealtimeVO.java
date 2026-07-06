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
        long mysqlThreadsRunning
) {}


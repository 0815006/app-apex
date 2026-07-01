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
        List<PortStatusVO> ports
) {}


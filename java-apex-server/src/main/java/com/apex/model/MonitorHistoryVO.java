package com.apex.model;

import java.time.LocalDateTime;

/**
 * 历史数据点 VO。
 */
public record MonitorHistoryVO(
        long id,
        double cpuUsage,
        double memUsage,
        double diskUsage,
        LocalDateTime recordTime
) {}

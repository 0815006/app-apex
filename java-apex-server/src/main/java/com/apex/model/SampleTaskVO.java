package com.apex.model;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 采样任务列表 VO，带机器名称和关联的指标信息。
 */
public record SampleTaskVO(
        int id,
        int machineId,
        String machineName,
        String taskName,
        LocalDateTime startTime,
        LocalDateTime endTime,
        int collectInterval,
        String status,
        List<Integer> metricIds,
        List<MetricInfo> metricInfos
) {}

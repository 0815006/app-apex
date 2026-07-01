package com.apex.model;

import java.time.LocalDateTime;

/**
 * 采样任务列表 VO，带机器名称。
 */
public record SampleTaskVO(
        int id,
        int machineId,
        String machineName,
        String taskName,
        LocalDateTime startTime,
        LocalDateTime endTime,
        int collectInterval,
        String status
) {}

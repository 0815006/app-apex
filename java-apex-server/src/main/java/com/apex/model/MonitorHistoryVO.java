package com.apex.model;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 历史数据点 VO — 动态指标值结构。
 * values 的 key 为 metricKey，value 为指标当前值。
 * 兼容旧数据：若无 metricValues JSON，则从 cpu/mem/disk 旧字段补齐。
 */
public record MonitorHistoryVO(
        long id,
        Map<String, Double> values,
        LocalDateTime recordTime
) {}

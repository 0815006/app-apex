package com.apex.model;

/**
 * 单条 Exporter 指标项 VO。
 */
public record MonitorMetricItem(
        String metricKey,        // 指标唯一标识（含标签）
        String metricName,       // 纯指标名
        String chineseName,      // 中文翻译
        String value,            // 当前值（字符串表示）
        String description,      // 指标说明
        boolean customized,      // 是否已被用户定制
        Integer customMetricId   // 定制记录ID（未定制则为null）
) {}

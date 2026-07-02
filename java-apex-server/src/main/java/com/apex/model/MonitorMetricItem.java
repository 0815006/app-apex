package com.apex.model;

/**
 * 单条 Exporter 指标项 VO。
 */
public record MonitorMetricItem(
        String metricKey,        // 指标唯一标识（含标签，如 windows_cpu_clock_interrupts_total{core="0,0"}）
        String metricName,       // 纯指标名
        String labels,           // 标签信息（如 {core="0,0"}，无标签则为空字符串）
        String chineseName,      // 中文翻译
        String value,            // 当前值（字符串表示）
        String description,      // 指标说明
        boolean customized,      // 是否已被用户定制
        Integer customMetricId   // 定制记录ID（未定制则为null）
) {}

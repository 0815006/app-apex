package com.apex.model;

/**
 * 定制指标在卡片上的实时状态 VO。
 * 原用于端口展示，现扩展为任意定制指标的在线/离线状态。
 */
public record PortStatusVO(
        Integer customMetricId,   // 定制记录ID，null 表示未定制
        String metricName,        // 展示文本（如 "CPU温度=45.2°C" 或 "已丢失"）
        String displayValue,      // 当前值文本
        boolean customized,       // 是否已定制
        boolean online            // 指标是否在线（true=绿灯 false=红灯/已丢失）
) {}

package com.apex.model;

/**
 * 定制指标简要信息 Record，用于采样任务 VO 中携带指标元信息。
 */
public record MetricInfo(
        int id,
        String metricKey,
        String displayName,
        String category
) {}

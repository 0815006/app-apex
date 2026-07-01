package com.apex.model;

/**
 * 已定制指标 VO（用于列表接口返回，不暴露 Entity 内部字段）。
 */
public record CustomMetricVO(
        Integer id,
        Integer machineId,
        String metricKey,
        String metricName,
        String displayName,
        String category,
        Boolean isVisible,
        String createTime
) {}

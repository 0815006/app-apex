package com.apex.model;

import java.util.List;

/**
 * 全量指标响应 VO。
 * 包含按分类组织的所有 Exporter 指标 + 已定制但丢失的指标列表。
 */
public record MonitorFullMetricsVO(
        int machineId,
        boolean reachable,
        String errorMsg,
        List<MonitorMetricCategory> categories,   // 按分类组织的当前指标
        List<MonitorMetricItem> orphaned          // 已定制但 Exporter 当前未返回的丢失指标
) {}

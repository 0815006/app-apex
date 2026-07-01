package com.apex.model;

import java.util.List;

/**
 * Exporter 指标分类 VO（如 CPU、内存、磁盘等）。
 */
public record MonitorMetricCategory(
        String categoryKey,              // 分类标识（cpu/memory/disk/network/system）
        String categoryName,             // 分类中文名（CPU/内存/磁盘/网络/系统）
        List<MonitorMetricItem> metrics  // 该分类下的所有指标项
) {}

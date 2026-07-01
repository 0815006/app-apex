package com.apex.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 监控-用户定制指标表实体。
 * 用户可从 Exporter 全量指标中勾选任意行作为定制指标，
 * 在容量监控卡片中醒目展示。
 */
@Data
@TableName("monitor_custom_metric")
public class MonitorCustomMetric {

    @TableId(type = IdType.AUTO)
    private Integer id;

    /** 关联机器ID */
    private Integer machineId;

    /** 指标唯一标识（纯指标名或含标签的完整key） */
    private String metricKey;

    /** 纯指标名，用于后端匹配 */
    private String metricName;

    /** 用户自定义展示别名 */
    private String displayName;

    /** 指标分类（cpu/memory/disk/network/system/process/custom） */
    private String category;

    /** 日常大屏是否可见 1可见 0隐藏 */
    private Boolean isVisible;

    /** 创建时间 */
    private LocalDateTime createTime;
}

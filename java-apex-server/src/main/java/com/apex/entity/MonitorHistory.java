package com.apex.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 监控-采样历史流水表实体。
 * V8 已移除旧版固定列（cpu_usage/mem_usage/disk_usage），仅保留 JSON 动态存储。
 */
@Data
@TableName("monitor_history")
public class MonitorHistory {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联采样任务ID */
    private Integer taskId;

    /** 指标值快照 JSON，如 {"__sys_cpu_usage":45.2} 或 {"tcp_listen{port=\"3306\"}":1} */
    private String metricValues;

    /** 记录生成时间 */
    private LocalDateTime recordTime;
}

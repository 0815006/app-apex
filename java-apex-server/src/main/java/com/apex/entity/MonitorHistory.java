package com.apex.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 监控-采样历史流水表实体。
 */
@Data
@TableName("monitor_history")
public class MonitorHistory {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联任务ID */
    private Integer taskId;

    /** CPU使用率(%) */
    private Float cpuUsage;

    /** 内存使用率(%) */
    private Float memUsage;

    /** 主磁盘使用率(%) */
    private Float diskUsage;

    /** 记录生成时间 */
    private LocalDateTime recordTime;
}

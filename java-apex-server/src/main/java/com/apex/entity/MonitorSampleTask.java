package com.apex.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 监控-采样任务控制表实体。
 * status: WAITING(等待), RUNNING(采集中), FINISHED(已结束)
 */
@Data
@TableName("monitor_sample_task")
public class MonitorSampleTask {

    @TableId(type = IdType.AUTO)
    private Integer id;

    /** 关联机器ID */
    private Integer machineId;

    /** 任务名称/备注 */
    private String taskName;

    /** 任务开始采集时间 */
    private LocalDateTime startTime;

    /** 任务结束采集时间 */
    private LocalDateTime endTime;

    /** 采集频率(秒) */
    private Integer collectInterval;

    /** WAITING(等待), RUNNING(采集中), FINISHED(已结束) */
    private String status;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}

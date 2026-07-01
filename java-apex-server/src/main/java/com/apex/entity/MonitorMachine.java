package com.apex.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 监控-机器主表实体。
 */
@Data
@TableName("monitor_machine")
public class MonitorMachine {

    @TableId(type = IdType.AUTO)
    private Integer id;

    /** 机器别名 */
    private String machineName;

    /** 机器IP */
    private String ip;

    /** WINDOWS 或 LINUX */
    private String osType;

    /** Exporter端口 */
    private Integer exporterPort;

    /** 前端刷新频率(秒) */
    private Integer refreshInterval;

    /** 是否开启监控 1开启 0关闭 */
    private Boolean isEnabled;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}

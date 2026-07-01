package com.apex.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 新建采样任务的请求体。
 */
@Data
public class MonitorSampleTaskDTO {

    @NotNull(message = "机器ID不能为空")
    private Integer machineId;

    @NotBlank(message = "任务名称不能为空")
    private String taskName;

    @NotNull(message = "开始时间不能为空")
    private LocalDateTime startTime;

    @NotNull(message = "结束时间不能为空")
    private LocalDateTime endTime;

    @NotNull(message = "采集频率不能为空")
    private Integer collectInterval;
}

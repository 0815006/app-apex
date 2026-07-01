package com.apex.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 新增/修改机器的请求体。
 */
@Data
public class MonitorMachineDTO {

    /** 修改时需传，新增时不传 */
    private Integer id;

    @NotBlank(message = "机器别名不能为空")
    private String machineName;

    @NotBlank(message = "机器IP不能为空")
    private String ip;

    @NotBlank(message = "系统类型不能为空")
    private String osType;

    @NotNull(message = "Exporter端口不能为空")
    private Integer exporterPort;

    @NotNull(message = "刷新频率不能为空")
    private Integer refreshInterval;
}

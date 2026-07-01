package com.apex.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 定制指标的请求体。
 */
@Data
public class MonitorCustomMetricDTO {

    @NotBlank(message = "指标标识不能为空")
    private String metricKey;

    @NotBlank(message = "指标名不能为空")
    private String metricName;

    @NotBlank(message = "展示别名不能为空")
    private String displayName;

    /** 可选分类，后端自动推断时可为空 */
    private String category;
}

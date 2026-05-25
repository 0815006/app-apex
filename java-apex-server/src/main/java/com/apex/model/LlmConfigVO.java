package com.apex.model;

/**
 * LLM 配置 VO（用于下拉框展示，不暴露 apiKey）。
 */
public record LlmConfigVO(
        String id,
        String configName,
        String modelName
) {}

package com.apex.model;

import java.time.LocalDateTime;

/**
 * 会话列表 VO（用于左侧列表展示）。
 */
public record ChatSessionVO(
        String id,
        String title,
        String configName,
        String modelName,
        LocalDateTime createTime,
        LocalDateTime updateTime
) {}

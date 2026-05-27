package com.apex.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * AI Skill 视图对象（不包含 type 为 agent/workflow 相关的复杂字段）。
 */
public record SkillVO(
        String id,
        String name,
        String icon,
        String description,
        String type,
        String systemPrompt,
        BigDecimal temperature,
        String workflowId,
        Integer status,
        Integer sortOrder,
        LocalDateTime createTime,
        LocalDateTime updateTime
) {}

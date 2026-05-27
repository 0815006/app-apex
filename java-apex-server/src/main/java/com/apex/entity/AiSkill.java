package com.apex.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * AI 技能实体。
 * 支持三种类型：prompt（纯提示词）、agent（工具调用）、workflow（工作流）。
 * 现阶段仅实现 prompt 类型。
 */
@Data
@TableName("ai_skill")
public class AiSkill {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 技能名称 */
    private String name;

    /** 图标类名或URL */
    private String icon;

    /** 功能简介 */
    private String description;

    /** 核心类型：prompt / agent / workflow */
    private String type;

    /** System Prompt 内容（type 为 prompt 或 agent 时有效） */
    private String systemPrompt;

    /** 采样温度 */
    private BigDecimal temperature;

    /** 关联的工作流 ID（type 为 workflow 时有效） */
    private String workflowId;

    /** 状态：1启用，0禁用 */
    private Integer status;

    /** 排序序号 */
    private Integer sortOrder;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}

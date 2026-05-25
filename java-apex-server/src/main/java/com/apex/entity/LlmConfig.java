package com.apex.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 大模型配置实体。
 * 每个用户可配置多套大模型，通过 userId 实现多租户隔离。
 */
@Data
@TableName("llm_config")
public class LlmConfig {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 用户工号（多租户隔离） */
    private String userId;

    /** 配置别名（如：个人DeepSeek） */
    private String configName;

    /** 大模型 Base URL */
    private String apiUrl;

    /** API 密钥 */
    private String apiKey;

    /** 模型名称（如 gpt-4o） */
    private String modelName;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}

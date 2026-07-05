package com.apex.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 对话会话实体。
 * 每个用户拥有独立的会话列表，通过 userId + updateTime DESC 排序。
 */
@Data
@TableName("chat_session")
public class ChatSession {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 用户工号（多租户隔离） */
    private String userId;

    /** 会话标题（默认"新对话"，首条消息后更新为前10字） */
    private String title;

    /** 绑定的 LLM 配置 ID */
    private String configId;

    /** 会话模式：CHAT（纯对话）/ AGENT（智能体） */
    private String sessionMode;

    /** 关联的工作空间 ID（AGENT 模式时必选） */
    private String workspaceId;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}

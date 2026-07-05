package com.apex.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 消息明细实体。
 * role: user / assistant / tool
 */
@Data
@TableName("chat_message")
public class ChatMessage {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 所属会话 ID */
    private String sessionId;

    /** 角色：user（用户）或 assistant（AI）或 tool（工具返回） */
    private String role;

    /** 消息文本内容 */
    private String content;

    /** 工具名称 */
    private String toolName;

    /** 工具调用唯一ID（大模型返回的 call_id） */
    private String toolCallId;

    /** 工具执行状态：running / success / failed */
    private String toolStatus;

    /** 原始 tool_calls JSON（assistant 消息时存完整数组） */
    private String toolCallsJson;

    private LocalDateTime createTime;
}

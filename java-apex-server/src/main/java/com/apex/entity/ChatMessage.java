package com.apex.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 消息明细实体。
 * role: user / assistant
 */
@Data
@TableName("chat_message")
public class ChatMessage {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 所属会话 ID */
    private String sessionId;

    /** 角色：user（用户）或 assistant（AI） */
    private String role;

    /** 消息文本内容 */
    private String content;

    private LocalDateTime createTime;
}

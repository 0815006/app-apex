package com.apex.model;

/**
 * 发送消息请求 DTO。
 */
public record ChatRequest(
        String sessionId,
        String configId,
        String content,
        String skillId,
        String workspaceId
) {}

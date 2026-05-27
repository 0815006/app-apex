package com.apex.model;

import java.util.List;
import java.util.Map;

/**
 * 发送给 LLM 的 OpenAI 兼容请求体。
 */
public record LlmRequest(
        String model,
        List<Message> messages,
        boolean stream
) {
    public record Message(String role, String content) {}

    /**
     * 从历史消息构建 LLM 请求（不带 system prompt）。
     */
    public static LlmRequest of(String model, List<Map<String, String>> history, String newContent) {
        return of(model, null, history, newContent);
    }

    /**
     * 从历史消息构建 LLM 请求（带可选的 system prompt，来自 Skill）。
     */
    public static LlmRequest of(String model, String systemPrompt, List<Map<String, String>> history, String newContent) {
        List<Message> messages = new java.util.ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(new Message("system", systemPrompt));
        }
        for (Map<String, String> msg : history) {
            messages.add(new Message(msg.get("role"), msg.get("content")));
        }
        messages.add(new Message("user", newContent));
        return new LlmRequest(model, messages, true);
    }
}

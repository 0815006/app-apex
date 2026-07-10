package com.apex.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * 发送给 LLM 的 OpenAI 兼容请求体（支持 tools/tool_choice）。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LlmRequest(
        String model,
        List<Message> messages,
        boolean stream,
        List<Map<String, Object>> tools,
        @JsonProperty("tool_choice") String toolChoice
) {
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Message(String role, String content,
                          @JsonProperty("tool_calls") List<Map<String, Object>> toolCalls,
                          @JsonProperty("tool_call_id") String toolCallId,
                          String name) {
        public Message(String role, String content) {
            this(role, content, null, null, null);
        }
    }

    /**
     * 从历史消息构建 LLM 请求（不带 system prompt，默认开启流式）。
     */
    public static LlmRequest of(String model, List<Map<String, String>> history, String newContent) {
        return of(model, null, history, newContent, true, null, null);
    }

    /**
     * 从历史消息构建 LLM 请求（带可选的 system prompt，默认开启流式）。
     */
    public static LlmRequest of(String model, String systemPrompt, List<Map<String, String>> history, String newContent) {
        return of(model, systemPrompt, history, newContent, true, null, null);
    }

    /**
     * 从历史消息构建 LLM 请求（带可选的 system prompt，显式指定 stream 开关）。
     */
    public static LlmRequest of(String model, String systemPrompt, List<Map<String, String>> history, String newContent, boolean stream) {
        return of(model, systemPrompt, history, newContent, stream, null, null);
    }

    /**
     * 从历史消息构建 LLM 请求（完整参数：system prompt + stream + tools/tool_choice）。
     */
    public static LlmRequest of(String model, String systemPrompt, List<Map<String, String>> history,
                                 String newContent, boolean stream,
                                 List<Map<String, Object>> tools, String toolChoice) {
        List<Message> messages = new java.util.ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(new Message("system", systemPrompt));
        }
        for (Map<String, String> msg : history) {
            messages.add(new Message(msg.get("role"), msg.get("content")));
        }
        messages.add(new Message("user", newContent));
        return new LlmRequest(model, messages, stream, tools, toolChoice);
    }

    /**
     * 从完整 Message 列表构建 LLM 请求（Agent 循环用）。
     */
    public static LlmRequest fromMessages(String model, List<Message> messages, boolean stream,
                                           List<Map<String, Object>> tools, String toolChoice) {
        return new LlmRequest(model, messages, stream, tools, toolChoice);
    }
}

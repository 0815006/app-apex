package com.apex.service;

import com.apex.common.BusinessException;
import com.apex.common.EmpContext;
import com.apex.entity.AiSkill;
import com.apex.entity.ChatMessage;
import com.apex.entity.ChatSession;
import com.apex.entity.LlmConfig;
import com.apex.mapper.AiSkillMapper;
import com.apex.mapper.ChatMessageMapper;
import com.apex.mapper.ChatSessionMapper;
import com.apex.model.ChatSessionVO;
import com.apex.model.LlmRequest;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 聊天核心 Service。
 * 负责会话管理、消息持久化、LLM 代理转发（SSE 流式）。
 * 支持 CHAT（经典对话）和 AGENT（智能体）两种模式。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatSessionMapper chatSessionMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final LlmService llmService;
    private final AiSkillMapper aiSkillMapper;
    private final BuiltInToolRegistry builtInToolRegistry;
    private final WorkspaceResolver workspaceResolver;
    private final ObjectMapper objectMapper;

    /** LLM 是否开启流式输出 */
    @Value("${apex.llm.stream:true}")
    private boolean llmStreamEnabled;

    // ==================================================================
    //  会话列表 / 消息查询 / 会话管理（通用）
    // ==================================================================

    /**
     * 获取当前用户的会话列表（按 mode 过滤）。
     * @param mode CHAT / AGENT / null（查全部）
     */
    public List<ChatSessionVO> listSessions(String mode) {
        String empNo = EmpContext.getEmpNo();
        LambdaQueryWrapper<ChatSession> wrapper = new LambdaQueryWrapper<ChatSession>()
                .eq(ChatSession::getUserId, empNo);
        if (mode != null && !mode.isBlank()) {
            wrapper.eq(ChatSession::getSessionMode, mode);
        }
        wrapper.orderByDesc(ChatSession::getUpdateTime);
        List<ChatSession> sessions = chatSessionMapper.selectList(wrapper);

        List<ChatSessionVO> vos = new ArrayList<>();
        for (ChatSession session : sessions) {
            String configName = "";
            String modelName = "";
            try {
                LlmConfig config = llmService.getByIdForCurrentUser(session.getConfigId());
                configName = config.getConfigName();
                modelName = config.getModelName();
            } catch (Exception ignored) {}
            vos.add(new ChatSessionVO(
                    session.getId(), session.getTitle(),
                    configName, modelName,
                    session.getSessionMode(),
                    session.getCreateTime(), session.getUpdateTime()
            ));
        }
        return vos;
    }

    /** 获取指定会话的消息历史（按时间正序）。 */
    public List<ChatMessage> getMessages(String sessionId) {
        String empNo = EmpContext.getEmpNo();
        ChatSession session = chatSessionMapper.selectOne(
                new LambdaQueryWrapper<ChatSession>()
                        .eq(ChatSession::getId, sessionId)
                        .eq(ChatSession::getUserId, empNo)
        );
        if (session == null) {
            throw new BusinessException(404, "会话不存在或无权访问");
        }
        return chatMessageMapper.selectList(
                new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getSessionId, sessionId)
                        .orderByAsc(ChatMessage::getCreateTime)
        );
    }

    /** 创建新会话（通用，支持 AGENT mode + workspaceId）。 */
    @Transactional
    public ChatSession createSession(String configId, String firstContent, String mode, String workspaceId) {
        String empNo = EmpContext.getEmpNo();
        llmService.getByIdForCurrentUser(configId);

        ChatSession session = new ChatSession();
        session.setUserId(empNo);
        session.setConfigId(configId);
        session.setSessionMode(mode != null ? mode : "CHAT");
        session.setWorkspaceId(workspaceId);

        String title = firstContent.replaceAll("\\s+", " ").trim();
        if (title.length() > 10) {
            title = title.substring(0, 10) + "…";
        }
        session.setTitle(title.isEmpty() ? "新对话" : title);
        chatSessionMapper.insert(session);
        log.info("[{}] 创建会话: id={}, mode={}, title={}", empNo, session.getId(), session.getSessionMode(), session.getTitle());
        return session;
    }

    /** 删除会话及其所有消息。 */
    @Transactional
    public void deleteSession(String sessionId) {
        String empNo = EmpContext.getEmpNo();
        ChatSession session = chatSessionMapper.selectOne(
                new LambdaQueryWrapper<ChatSession>()
                        .eq(ChatSession::getId, sessionId)
                        .eq(ChatSession::getUserId, empNo)
        );
        if (session == null) {
            throw new BusinessException(404, "会话不存在或无权删除");
        }
        chatMessageMapper.delete(
                new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getSessionId, sessionId)
        );
        chatSessionMapper.deleteById(sessionId);
        log.info("[{}] 删除会话: id={}", empNo, sessionId);
    }

    /** 更新会话标题。 */
    @Transactional
    public void updateTitle(String sessionId, String title) {
        String empNo = EmpContext.getEmpNo();
        ChatSession session = chatSessionMapper.selectOne(
                new LambdaQueryWrapper<ChatSession>()
                        .eq(ChatSession::getId, sessionId)
                        .eq(ChatSession::getUserId, empNo)
        );
        if (session == null) {
            throw new BusinessException(404, "会话不存在或无权修改");
        }
        session.setTitle(title);
        chatSessionMapper.updateById(session);
    }

    // ==================================================================
    //  核心：发送消息（统一入口 + session_mode 分流）
    // ==================================================================

    /**
     * 发送消息并获取 SSE 流式响应。
     * 根据会话的 session_mode 分流到经典对话或 Agent 循环。
     */
    public SseEmitter sendMessage(String sessionId, String configId, String content,
                                   String skillId, String workspaceId) {
        String empNo = EmpContext.getEmpNo();

        // 1. 校验配置归属
        LlmConfig config = llmService.getByIdForCurrentUser(configId);

        // 2. 若 sessionId 为空，自动创建新会话（此时需要知道 mode）
        ChatSession session;
        if (sessionId == null || sessionId.isBlank()) {
            String mode = (workspaceId != null && !workspaceId.isBlank()) ? "AGENT" : "CHAT";
            session = createSession(configId, content, mode, workspaceId);
            sessionId = session.getId();
        } else {
            session = chatSessionMapper.selectOne(
                    new LambdaQueryWrapper<ChatSession>()
                            .eq(ChatSession::getId, sessionId)
                            .eq(ChatSession::getUserId, empNo)
            );
            if (session == null) {
                throw new BusinessException(404, "会话不存在或无权访问");
            }
            // 更新 configId 和 updateTime
            session.setConfigId(configId);
            chatSessionMapper.updateById(session);
        }

        String mode = session.getSessionMode();
        if (mode == null) mode = "CHAT";

        // 3. 保存用户消息
        ChatMessage userMsg = new ChatMessage();
        userMsg.setSessionId(sessionId);
        userMsg.setRole("user");
        userMsg.setContent(content);
        chatMessageMapper.insert(userMsg);

        // 4. 更新会话 update_time（复用已查出的 session 对象）
        session.setUpdateTime(LocalDateTime.now());
        chatSessionMapper.updateById(session);

        // 5. 根据 mode 分流
        if ("AGENT".equals(mode)) {
            String wsId = session.getWorkspaceId();
            if (wsId == null || wsId.isBlank()) {
                throw new BusinessException(400, "Agent 会话缺少工作空间关联");
            }
            return executeAgentLoop(session, config, content, skillId, userMsg);
        } else {
            return executeClassicChat(session, config, content, skillId, userMsg);
        }
    }

    // ==================================================================
    //  经典对话模式（原有逻辑，单独抽出）
    // ==================================================================

    private SseEmitter executeClassicChat(ChatSession session, LlmConfig config,
                                           String content, String skillId, ChatMessage userMsg) {
        String empNo = EmpContext.getEmpNo();
        String sessionId = session.getId();

        // 构建 LLM 请求
        String systemPrompt = resolveSystemPrompt(skillId);
        List<Map<String, String>> historyMaps = buildHistoryMaps(sessionId, userMsg.getId());
        LlmRequest llmReq = LlmRequest.of(config.getModelName(), systemPrompt, historyMaps, content, llmStreamEnabled);

        // 创建 SseEmitter
        SseEmitter emitter = new SseEmitter(300_000L);
        emitter.onCompletion(() -> log.info("[{}] SSE 流正常完成: sessionId={}", empNo, sessionId));
        emitter.onTimeout(() -> log.warn("[{}] SSE 流超时: sessionId={}", empNo, sessionId));
        emitter.onError(ex -> log.error("[{}] SSE 流异常: sessionId={}", empNo, sessionId, ex));

        Thread.ofVirtual().start(() -> {
            StringBuilder fullResponse = new StringBuilder();
            try {
                String chatUrl = buildChatUrl(config.getApiUrl());
                String requestBody = objectMapper.writeValueAsString(llmReq);

                HttpRequest httpRequest = HttpRequest.newBuilder()
                        .uri(URI.create(chatUrl))
                        .header("Authorization", "Bearer " + config.getApiKey())
                        .header("Content-Type", "application/json")
                        .header("Accept", "text/event-stream")
                        .timeout(Duration.ofMinutes(5))
                        .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                        .build();

                HttpClient httpClient = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(10))
                        .build();

                HttpResponse<java.util.stream.Stream<String>> httpResponse =
                        httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofLines());

                java.util.stream.Stream<String> lines = httpResponse.body();
                try (lines) {
                    java.util.Iterator<String> iterator = lines.iterator();
                    boolean sawSseData = false;
                    StringBuilder plainJsonBuffer = new StringBuilder();
                    while (iterator.hasNext()) {
                        String line = iterator.next();
                        if (line.isEmpty()) continue;
                        if (line.startsWith("data: ")) {
                            sawSseData = true;
                            String data = line.substring(6);
                            if ("[DONE]".equals(data)) break;
                            try {
                                @SuppressWarnings("unchecked")
                                Map<String, Object> jsonMap = objectMapper.readValue(data, Map.class);
                                @SuppressWarnings("unchecked")
                                var choices = (List<Map<String, Object>>) jsonMap.get("choices");
                                if (choices != null && !choices.isEmpty()) {
                                    @SuppressWarnings("unchecked")
                                    var delta = (Map<String, Object>) choices.get(0).get("delta");
                                    if (delta != null) {
                                        if (delta.get("reasoning_content") != null) {
                                            emitter.send(SseEmitter.event()
                                                    .name("reasoning")
                                                    .data(delta.get("reasoning_content")));
                                        }
                                        if (delta.get("content") != null) {
                                            String chunk = (String) delta.get("content");
                                            fullResponse.append(chunk);
                                            emitter.send(SseEmitter.event()
                                                    .name("message")
                                                    .data(chunk));
                                        }
                                    }
                                }
                            } catch (Exception parseEx) {
                                log.warn("解析 LLM 响应 JSON 失败: {}", parseEx.getMessage());
                            }
                        } else if (!sawSseData) {
                            plainJsonBuffer.append(line);
                        }
                    }

                    // 非流式回退
                    if (!sawSseData && !plainJsonBuffer.isEmpty()) {
                        try {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> jsonMap = objectMapper.readValue(plainJsonBuffer.toString(), Map.class);
                            @SuppressWarnings("unchecked")
                            var choices = (List<Map<String, Object>>) jsonMap.get("choices");
                            if (choices != null && !choices.isEmpty()) {
                                @SuppressWarnings("unchecked")
                                var message = (Map<String, Object>) choices.get(0).get("message");
                                if (message != null && message.get("content") != null) {
                                    String assistantContent = (String) message.get("content");
                                    fullResponse.append(assistantContent);
                                    emitter.send(SseEmitter.event().name("message").data(assistantContent));
                                }
                            }
                        } catch (Exception parseEx) {
                            log.warn("解析非流式 LLM 响应 JSON 失败: {}", parseEx.getMessage());
                        }
                    }
                }

                // 保存 AI 回复
                ChatMessage aiMsg = new ChatMessage();
                aiMsg.setSessionId(sessionId);
                aiMsg.setRole("assistant");
                aiMsg.setContent(fullResponse.toString());
                chatMessageMapper.insert(aiMsg);

                emitter.send(SseEmitter.event().name("done")
                        .data(Map.of("sessionId", sessionId, "messageId", aiMsg.getId())));
                emitter.complete();

            } catch (Exception e) {
                log.error("[{}] LLM 代理转发失败", empNo, e);
                try {
                    emitter.send(SseEmitter.event().name("error")
                            .data("大模型请求失败: " + e.getMessage()));
                    emitter.completeWithError(e);
                } catch (Exception sendEx) {
                    emitter.completeWithError(sendEx);
                }
            }
        });

        return emitter;
    }

    // ==================================================================
    //  Agent 智能体循环（while 循环 + tool_calls + SSE JSON 事件）
    // ==================================================================

    private static final int MAX_AGENT_LOOPS = 5;
    private final Map<String, AtomicBoolean> abortFlags = new HashMap<>();

    /** 中止指定会话的 Agent 循环 */
    public void abortAgent(String sessionId) {
        AtomicBoolean flag = abortFlags.get(sessionId);
        if (flag != null) {
            flag.set(true);
            log.info("[{}] Agent 循环中止标记已设置: sessionId={}", EmpContext.getEmpNo(), sessionId);
        }
    }

    private SseEmitter executeAgentLoop(ChatSession session, LlmConfig config,
                                         String content, String skillId, ChatMessage userMsg) {
        String empNo = EmpContext.getEmpNo();
        String sessionId = session.getId();
        String workspaceId = session.getWorkspaceId();

        SseEmitter emitter = new SseEmitter(600_000L); // Agent 超时 10 分钟
        emitter.onCompletion(() -> log.info("[{}] Agent SSE 流完成: sessionId={}", empNo, sessionId));
        emitter.onTimeout(() -> log.warn("[{}] Agent SSE 流超时: sessionId={}", empNo, sessionId));
        emitter.onError(ex -> log.error("[{}] Agent SSE 流异常: sessionId={}", empNo, sessionId, ex));

        AtomicBoolean cancelled = new AtomicBoolean(false);
        abortFlags.put(sessionId, cancelled);

        Thread.ofVirtual().start(() -> {
            try {
                String chatUrl = buildChatUrl(config.getApiUrl());
                String systemPrompt = resolveAgentSystemPrompt(skillId);

                // 组装工具列表：内置工具 + Skill 相关外部插件（MVP 阶段外部插件为空）
                List<Map<String, Object>> allTools = new ArrayList<>(builtInToolRegistry.getBuiltInToolsSchema());

                // 构建初始消息列表
                List<LlmRequest.Message> messages = new ArrayList<>();
                messages.add(new LlmRequest.Message("system", systemPrompt));
                // 加载历史消息（不含刚插入的用户消息，它将在下面单独追加）
                List<ChatMessage> history = chatMessageMapper.selectList(
                        new LambdaQueryWrapper<ChatMessage>()
                                .eq(ChatMessage::getSessionId, sessionId)
                                .orderByAsc(ChatMessage::getCreateTime)
                );
                for (ChatMessage msg : history) {
                    if (msg.getId().equals(userMsg.getId())) continue;
                    if ("tool".equals(msg.getRole())) {
                        // tool 消息需要 tool_call_id 和 name
                        messages.add(new LlmRequest.Message("tool", msg.getContent(),
                                null, msg.getToolCallId(), msg.getToolName()));
                    } else if ("assistant".equals(msg.getRole())
                            && msg.getToolCallsJson() != null && !msg.getToolCallsJson().isBlank()) {
                        // assistant 消息携带 tool_calls 时反序列化，否则 LLM 看不到上一轮调用了什么工具
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> toolCalls =
                                objectMapper.readValue(msg.getToolCallsJson(), List.class);
                        messages.add(new LlmRequest.Message("assistant", msg.getContent(),
                                toolCalls, null, null));
                    } else {
                        messages.add(new LlmRequest.Message(msg.getRole(), msg.getContent()));
                    }
                }
                // 追加当前用户消息
                messages.add(new LlmRequest.Message("user", content));

                // Agent 主循环
                int loopCount = 0;
                boolean doneSent = false;

                while (loopCount < MAX_AGENT_LOOPS && !cancelled.get()) {
                    loopCount++;
                    log.info("[{}] Agent 循环 #{}/{}", empNo, loopCount, MAX_AGENT_LOOPS);

                    LlmRequest llmReq = LlmRequest.fromMessages(config.getModelName(), messages,
                            llmStreamEnabled, allTools, "auto");

                    String requestBody = objectMapper.writeValueAsString(llmReq);
                    HttpRequest httpRequest = HttpRequest.newBuilder()
                            .uri(URI.create(chatUrl))
                            .header("Authorization", "Bearer " + config.getApiKey())
                            .header("Content-Type", "application/json")
                            .header("Accept", "text/event-stream")
                            .timeout(Duration.ofMinutes(5))
                            .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                            .build();

                    HttpClient httpClient = HttpClient.newBuilder()
                            .connectTimeout(Duration.ofSeconds(10))
                            .build();

                    // 本轮累积：文本内容 + tool_calls
                    StringBuilder roundContent = new StringBuilder();
                    List<Map<String, Object>> roundToolCalls = new ArrayList<>();
                    Map<String, StringBuilder> toolCallArgsBuffer = new LinkedHashMap<>();

                    HttpResponse<java.util.stream.Stream<String>> httpResponse =
                            httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofLines());

                    java.util.stream.Stream<String> lines = httpResponse.body();
                    try (lines) {
                        java.util.Iterator<String> iterator = lines.iterator();
                        while (iterator.hasNext()) {
                            String line = iterator.next();
                            if (line.isEmpty()) continue;
                            if (line.startsWith("data: ")) {
                                String data = line.substring(6);
                                if ("[DONE]".equals(data)) break;

                                try {
                                    @SuppressWarnings("unchecked")
                                    Map<String, Object> jsonMap = objectMapper.readValue(data, Map.class);
                                    @SuppressWarnings("unchecked")
                                    var choices = (List<Map<String, Object>>) jsonMap.get("choices");
                                    if (choices == null || choices.isEmpty()) continue;

                                    @SuppressWarnings("unchecked")
                                    var delta = (Map<String, Object>) choices.get(0).get("delta");
                                    if (delta == null) continue;

                                    // 思考链
                                    if (delta.get("reasoning_content") != null) {
                                        String reasoning = (String) delta.get("reasoning_content");
                                        emitter.send(SseEmitter.event().name("agent").data(
                                                Map.of("type", "reasoning", "content", reasoning)));
                                    }

                                    // 正文内容
                                    if (delta.get("content") != null) {
                                        String chunk = (String) delta.get("content");
                                        roundContent.append(chunk);
                                        emitter.send(SseEmitter.event().name("agent").data(
                                                Map.of("type", "text", "content", chunk)));
                                    }

                                    // tool_calls
                                    @SuppressWarnings("unchecked")
                                    var toolCalls = (List<Map<String, Object>>) delta.get("tool_calls");
                                    if (toolCalls != null) {
                                        for (Map<String, Object> tc : toolCalls) {
                                            int index = ((Number) tc.get("index")).intValue();
                                            String callId = (String) tc.get("id");

                                            // 确保容量
                                            while (roundToolCalls.size() <= index) {
                                                roundToolCalls.add(new LinkedHashMap<>());
                                            }
                                            @SuppressWarnings("unchecked")
                                            Map<String, Object> existing = (Map<String, Object>) roundToolCalls.get(index);
                                            if (callId != null) existing.put("id", callId);

                                            @SuppressWarnings("unchecked")
                                            var func = (Map<String, Object>) tc.get("function");
                                            if (func != null) {
                                                if (func.get("name") != null) {
                                                    existing.put("function", Map.of(
                                                            "name", func.get("name"),
                                                            "arguments", func.get("arguments") != null ? func.get("arguments") : ""));
                                                } else if (func.get("arguments") != null) {
                                                    @SuppressWarnings("unchecked")
                                                    Map<String, Object> existingFunc = (Map<String, Object>) existing.get("function");
                                                    if (existingFunc != null) {
                                                        String existingArgs = (String) existingFunc.getOrDefault("arguments", "");
                                                        existing.put("function", Map.of(
                                                                "name", existingFunc.get("name"),
                                                                "arguments", existingArgs + func.get("arguments")));
                                                    }
                                                }
                                            }
                                        }
                                    }
                                } catch (Exception parseEx) {
                                    log.warn("[{}] 解析 Agent LLM delta 失败: {}", empNo, parseEx.getMessage());
                                }
                            }
                        }
                    }

                    // 检查是否被取消
                    if (cancelled.get()) {
                        emitter.send(SseEmitter.event().name("agent").data(
                                Map.of("type", "error", "error", "任务已被用户中止")));
                        break;
                    }

                    // 本轮没有 tool_calls → 纯文本回复，结束循环
                    if (roundToolCalls.isEmpty()) {
                        // 保存 assistant 消息
                        ChatMessage aiMsg = new ChatMessage();
                        aiMsg.setSessionId(sessionId);
                        aiMsg.setRole("assistant");
                        aiMsg.setContent(roundContent.toString());
                        chatMessageMapper.insert(aiMsg);

                        // 追加到 messages 历史
                        messages.add(new LlmRequest.Message("assistant", roundContent.toString()));

                        emitter.send(SseEmitter.event().name("agent").data(
                                Map.of("type", "done", "sessionId", sessionId, "messageId", aiMsg.getId())));
                        doneSent = true;
                        break;
                    }

                    // 有 tool_calls → 保存 assistant 消息（含 tool_calls），执行工具
                    String toolCallsJson = objectMapper.writeValueAsString(roundToolCalls);

                    ChatMessage assistMsg = new ChatMessage();
                    assistMsg.setSessionId(sessionId);
                    assistMsg.setRole("assistant");
                    assistMsg.setContent(roundContent.toString());
                    assistMsg.setToolCallsJson(toolCallsJson);
                    chatMessageMapper.insert(assistMsg);

                    // 追加到 messages 历史
                    messages.add(new LlmRequest.Message("assistant", roundContent.toString(),
                            roundToolCalls, null, null));

                    // 执行每个 tool_call
                    for (Map<String, Object> tc : roundToolCalls) {
                        String callId = (String) tc.get("id");
                        @SuppressWarnings("unchecked")
                        Map<String, Object> func = (Map<String, Object>) tc.get("function");
                        String toolName = (String) func.get("name");
                        String argsStr = (String) func.get("arguments");

                        // 推送 tool_start SSE 事件
                        emitter.send(SseEmitter.event().name("agent").data(
                                Map.of("type", "tool_start", "name", toolName, "callId", callId)));

                        // 执行工具
                        String toolResult;
                        String toolStatus = "success";
                        try {
                            if (BuiltInToolRegistry.BUILT_IN_TOOL_NAMES.contains(toolName)) {
                                String dirName = workspaceResolver.getDirName(workspaceId);
                                toolResult = builtInToolRegistry.executeBuiltInTool(toolName, argsStr, dirName);
                            } else {
                                // 外部插件（MVP 阶段暂不实现）
                                toolResult = "外部插件暂未支持: " + toolName;
                                toolStatus = "failed";
                            }
                        } catch (Exception e) {
                            toolResult = "工具执行失败: " + e.getMessage();
                            toolStatus = "failed";
                            log.error("[{}] 工具执行失败: tool={}, args={}", empNo, toolName, argsStr, e);
                        }

                        // 推送 tool_end SSE 事件
                        String result = toolResult.length() > 200 ? toolResult.substring(0, 200) + "..." : toolResult;
                        emitter.send(SseEmitter.event().name("agent").data(
                                Map.of("type", "tool_end", "name", toolName, "callId", callId,
                                        "status", toolStatus, "result", result)));

                        // 保存 tool 消息
                        ChatMessage toolMsg = new ChatMessage();
                        toolMsg.setSessionId(sessionId);
                        toolMsg.setRole("tool");
                        toolMsg.setContent(toolResult);
                        toolMsg.setToolName(toolName);
                        toolMsg.setToolCallId(callId);
                        toolMsg.setToolStatus(toolStatus);
                        chatMessageMapper.insert(toolMsg);

                        // 追加到 messages 历史
                        messages.add(new LlmRequest.Message("tool", toolResult, null, callId, toolName));

                        // file_changed 事件
                        if (BuiltInToolRegistry.FILE_CHANGE_TOOLS.contains(toolName)) {
                            try {
                                JsonNode argsNode = objectMapper.readTree(argsStr);
                                String relPath = argsNode.get("relativePath").asText();
                                emitter.send(SseEmitter.event().name("agent").data(
                                        Map.of("type", "file_changed", "path", relPath)));
                            } catch (Exception ignored) {}
                        }
                    }

                    // 所有工具执行完毕，继续下一轮循环
                }

                // 循环结束但被取消或超限（仅当未在循环内发送 done 时）
                if (!doneSent) {
                    if (cancelled.get()) {
                        emitter.send(SseEmitter.event().name("agent").data(
                                Map.of("type", "error", "error", "任务已被用户中止")));
                    } else if (loopCount >= MAX_AGENT_LOOPS) {
                        emitter.send(SseEmitter.event().name("agent").data(
                                Map.of("type", "done", "sessionId", sessionId,
                                        "messageId", "max_loops_reached")));
                    }
                }

                emitter.complete();

            } catch (Exception e) {
                log.error("[{}] Agent 循环异常", empNo, e);
                try {
                    emitter.send(SseEmitter.event().name("agent").data(
                            Map.of("type", "error", "error", "Agent 执行失败: " + e.getMessage())));
                    emitter.completeWithError(e);
                } catch (Exception sendEx) {
                    emitter.completeWithError(sendEx);
                }
            } finally {
                abortFlags.remove(sessionId);
            }
        });

        return emitter;
    }

    // ==================================================================
    //  辅助方法
    // ==================================================================

    private String buildChatUrl(String apiUrl) {
        if (apiUrl.endsWith("/")) {
            apiUrl = apiUrl.substring(0, apiUrl.length() - 1);
        }
        return apiUrl + "/chat/completions";
    }

    private String resolveSystemPrompt(String skillId) {
        if (skillId != null && !skillId.isBlank()) {
            AiSkill skill = aiSkillMapper.selectById(skillId);
            if (skill != null && skill.getStatus() == 1 && skill.getSystemPrompt() != null) {
                return skill.getSystemPrompt();
            }
        }
        return null;
    }

    private String resolveAgentSystemPrompt(String skillId) {
        // Skill 的 system prompt（如果有）追加到 Agent 默认 prompt 之后
        String base = """
                你是一个在严格沙箱工程目录中工作的 AI 编程助手，拥有以下能力：
                1. 修改代码时，优先使用 apply_diff 精准替换，避免全量重写大文件
                2. 修改完成后，根据项目类型自动执行构建/测试命令验证结果
                3. 如果构建失败，分析 stderr 输出并自主修复，最多尝试 3 次
                4. 无绝对把握时不得删除已有业务逻辑，只做最小化修改
                5. 所有文件操作限定在工作空间根目录内，不得越界
                6. 查看目录结构或列出文件时，必须使用 list_dir 工具（禁止用 execute_command 执行 dir/ls 命令），并完整汇报工具返回的全部内容（包括所有 [DIR] 和 [FILE] 条目），不得省略或摘要
                
                === 文件创建严格规则 (CRITICAL) ===
                7. 创建文件时，必须严格使用用户明确指定的文件名和路径。禁止自行编造文件名、禁止将用户指定的文件路径替换为已有列表中的其他文件名！
                   例如：用户说「在 docs 下创建 1.md」，你必须调用 write_file 且 relativePath 精确为 docs/1.md，绝对不允许写成 docs/RELEASE_NOTES.md 或其他任何已有文件。
                8. 在执行任何 write_file 或 apply_diff 操作前，先确认目标路径是用户明确要求的，如果对路径有疑问，先向用户确认再操作。
                9. 禁止基于历史消息或已有文件列表「猜测」用户意图修改的文件名，文件名必须逐字匹配用户最新消息中的原文。
                """;
        String skillPrompt = resolveSystemPrompt(skillId);
        if (skillPrompt != null && !skillPrompt.isBlank()) {
            base = base + "\n\n" + skillPrompt;
        }
        return base;
    }

    private List<Map<String, String>> buildHistoryMaps(String sessionId, String excludeMsgId) {
        List<ChatMessage> history = chatMessageMapper.selectList(
                new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getSessionId, sessionId)
                        .orderByAsc(ChatMessage::getCreateTime)
        );
        return history.stream()
                .filter(m -> !m.getId().equals(excludeMsgId))
                .map(m -> {
                    Map<String, String> map = new HashMap<>();
                    map.put("role", m.getRole());
                    map.put("content", m.getContent());
                    return map;
                })
                .toList();
    }
}

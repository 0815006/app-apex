package com.apex.service;

import com.apex.common.BusinessException;
import com.apex.common.EmpContext;
import com.apex.entity.ChatMessage;
import com.apex.entity.ChatSession;
import com.apex.entity.LlmConfig;
import com.apex.mapper.ChatMessageMapper;
import com.apex.mapper.ChatSessionMapper;
import com.apex.model.ChatSessionVO;
import com.apex.model.LlmRequest;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 聊天核心 Service。
 * 负责会话管理、消息持久化、LLM 代理转发（SSE 流式）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatSessionMapper chatSessionMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final LlmService llmService;
    private final ObjectMapper objectMapper;

    /**
     * 获取当前用户的会话列表（按 update_time 倒序）。
     */
    public List<ChatSessionVO> listSessions() {
        String empNo = EmpContext.getEmpNo();
        List<ChatSession> sessions = chatSessionMapper.selectList(
                new LambdaQueryWrapper<ChatSession>()
                        .eq(ChatSession::getUserId, empNo)
                        .orderByDesc(ChatSession::getUpdateTime)
        );
        List<ChatSessionVO> vos = new ArrayList<>();
        for (ChatSession session : sessions) {
            String configName = "";
            String modelName = "";
            try {
                LlmConfig config = llmService.getByIdForCurrentUser(session.getConfigId());
                configName = config.getConfigName();
                modelName = config.getModelName();
            } catch (Exception ignored) {
                // 配置可能已被删除，忽略
            }
            vos.add(new ChatSessionVO(
                    session.getId(), session.getTitle(),
                    configName, modelName,
                    session.getCreateTime(), session.getUpdateTime()
            ));
        }
        return vos;
    }

    /**
     * 获取指定会话的消息历史（按时间正序）。
     */
    public List<ChatMessage> getMessages(String sessionId) {
        String empNo = EmpContext.getEmpNo();
        // 校验会话归属
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

    /**
     * 创建新会话（首次对话时调用）。
     */
    @Transactional
    public ChatSession createSession(String configId, String firstContent) {
        String empNo = EmpContext.getEmpNo();
        // 校验配置归属
        llmService.getByIdForCurrentUser(configId);

        ChatSession session = new ChatSession();
        session.setUserId(empNo);
        session.setConfigId(configId);
        // 截取前 10 个字作为标题
        String title = firstContent.replaceAll("\\s+", " ").trim();
        if (title.length() > 10) {
            title = title.substring(0, 10) + "…";
        }
        session.setTitle(title.isEmpty() ? "新对话" : title);
        chatSessionMapper.insert(session);
        log.info("[{}] 创建新会话: id={}, title={}", empNo, session.getId(), session.getTitle());
        return session;
    }

    /**
     * 删除会话及其所有消息。
     */
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
        // 删除所有关联消息
        chatMessageMapper.delete(
                new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getSessionId, sessionId)
        );
        // 删除会话
        chatSessionMapper.deleteById(sessionId);
        log.info("[{}] 删除会话: id={}", empNo, sessionId);
    }

    /**
     * 更新会话标题。
     */
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

    /**
     * 核心方法：发送消息并获取 LLM 流式响应。
     * 返回 SseEmitter 供前端接收打字机效果。
     *
     * @param sessionId 会话 ID（为 null 时自动创建）
     * @param configId  大模型配置 ID
     * @param content   用户消息内容
     * @return SseEmitter
     */
    public SseEmitter sendMessage(String sessionId, String configId, String content) {
        String empNo = EmpContext.getEmpNo();

        // 1. 校验配置归属
        LlmConfig config = llmService.getByIdForCurrentUser(configId);

        // 2. 若 sessionId 为空，自动创建新会话
        if (sessionId == null || sessionId.isBlank()) {
            ChatSession newSession = createSession(configId, content);
            sessionId = newSession.getId();
        } else {
            // 校验会话归属
            ChatSession exist = chatSessionMapper.selectOne(
                    new LambdaQueryWrapper<ChatSession>()
                            .eq(ChatSession::getId, sessionId)
                            .eq(ChatSession::getUserId, empNo)
            );
            if (exist == null) {
                throw new BusinessException(404, "会话不存在或无权访问");
            }
            // 更新会话的 config_id 和 update_time
            exist.setConfigId(configId);
            chatSessionMapper.updateById(exist);
        }

        // 3. 保存用户消息
        ChatMessage userMsg = new ChatMessage();
        userMsg.setSessionId(sessionId);
        userMsg.setRole("user");
        userMsg.setContent(content);
        chatMessageMapper.insert(userMsg);

        // 4. 更新会话 update_time（触发置顶排序）
        ChatSession session = chatSessionMapper.selectById(sessionId);
        session.setUpdateTime(LocalDateTime.now());
        chatSessionMapper.updateById(session);

        // 5. 加载历史消息上下文
        List<ChatMessage> history = chatMessageMapper.selectList(
                new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getSessionId, sessionId)
                        .orderByAsc(ChatMessage::getCreateTime)
        );

        // 6. 构建 LLM 请求
        List<Map<String, String>> historyMaps = history.stream()
                .filter(m -> !m.getId().equals(userMsg.getId())) // 去除刚插入的用户消息，避免重复
                .map(m -> {
                    Map<String, String> map = new HashMap<>();
                    map.put("role", m.getRole());
                    map.put("content", m.getContent());
                    return map;
                })
                .toList();
        LlmRequest llmReq = LlmRequest.of(config.getModelName(), historyMaps, content);

        // 7. 创建 SseEmitter（超时 5 分钟）
        SseEmitter emitter = new SseEmitter(300_000L);
        final String finalSessionId = sessionId;

        // 8. 虚拟线程异步执行 LLM 代理转发
        Thread.ofVirtual().start(() -> {
            StringBuilder fullResponse = new StringBuilder();
            try {
                RestClient restClient = RestClient.create();
                String apiUrl = config.getApiUrl();
                // 确保 URL 以 / 结尾没有双斜杠
                if (apiUrl.endsWith("/")) {
                    apiUrl = apiUrl.substring(0, apiUrl.length() - 1);
                }
                String chatUrl = apiUrl + "/chat/completions";

                log.info("[{}] 转发 LLM 请求: url={}, model={}", empNo, chatUrl, config.getModelName());

                restClient.post()
                        .uri(chatUrl)
                        .header("Authorization", "Bearer " + config.getApiKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(llmReq)
                        .exchange((request, response) -> {
                            try (BufferedReader reader = new BufferedReader(
                                    new InputStreamReader(response.getBody(), StandardCharsets.UTF_8))) {
                                String line;
                                while ((line = reader.readLine()) != null) {
                                    if (line.isEmpty()) continue;
                                    if (line.startsWith("data: ")) {
                                        String data = line.substring(6);
                                        if ("[DONE]".equals(data)) {
                                            break;
                                        }
                                        // 解析 JSON 提取 content delta
                                        try {
                                            @SuppressWarnings("unchecked")
                                            Map<String, Object> jsonMap = objectMapper.readValue(data, Map.class);
                                            var choices = (List<Map<String, Object>>) jsonMap.get("choices");
                                            if (choices != null && !choices.isEmpty()) {
                                                var delta = (Map<String, Object>) choices.get(0).get("delta");
                                                if (delta != null && delta.get("content") != null) {
                                                    String chunk = (String) delta.get("content");
                                                    fullResponse.append(chunk);
                                                    // 发送 SSE 事件给前端
                                                    emitter.send(SseEmitter.event()
                                                            .name("message")
                                                            .data(chunk));
                                                }
                                            }
                                        } catch (Exception parseEx) {
                                            log.warn("解析 LLM 响应 JSON 失败: {}", parseEx.getMessage());
                                        }
                                    }
                                }
                            }
                            return null;
                        });

                log.info("[{}] LLM 响应完成，总长度: {}", empNo, fullResponse.length());

                // 9. 保存 AI 回复
                ChatMessage aiMsg = new ChatMessage();
                aiMsg.setSessionId(finalSessionId);
                aiMsg.setRole("assistant");
                aiMsg.setContent(fullResponse.toString());
                chatMessageMapper.insert(aiMsg);

                // 发送完成事件（携带 sessionId 供前端关联）
                emitter.send(SseEmitter.event()
                        .name("done")
                        .data(Map.of("sessionId", finalSessionId, "messageId", aiMsg.getId())));

                emitter.complete();

            } catch (Exception e) {
                log.error("[{}] LLM 代理转发失败", empNo, e);
                try {
                    emitter.send(SseEmitter.event()
                            .name("error")
                            .data("大模型请求失败: " + e.getMessage()));
                    emitter.completeWithError(e);
                } catch (Exception sendEx) {
                    emitter.completeWithError(sendEx);
                }
            }
        });

        return emitter;
    }
}

package com.apex.controller;

import com.apex.common.EmpContext;
import com.apex.common.Result;
import com.apex.entity.ChatMessage;
import com.apex.model.ChatRequest;
import com.apex.model.ChatSessionVO;
import com.apex.service.ChatService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

/**
 * AI 聊天 Controller。
 */
@Slf4j
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    /**
     * 获取当前用户的会话列表。
     */
    @GetMapping("/sessions")
    public Result<List<ChatSessionVO>> listSessions() {
        log.info("[{}] 查询会话列表", EmpContext.getEmpNo());
        return Result.success(chatService.listSessions());
    }

    /**
     * 获取指定会话的消息历史。
     */
    @GetMapping("/messages/{sessionId}")
    public Result<List<ChatMessage>> getMessages(@PathVariable String sessionId) {
        log.info("[{}] 查询会话消息: sessionId={}", EmpContext.getEmpNo(), sessionId);
        return Result.success(chatService.getMessages(sessionId));
    }

    /**
     * 发送消息并获取 SSE 流式响应。
     * 如果 sessionId 为空，则自动创建新会话。
     */
    @PostMapping(value = "/send", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter sendMessage(@RequestBody ChatRequest request, HttpServletResponse response) {
        log.info("[{}] 发送消息: sessionId={}, configId={}, skillId={}, content={}",
                EmpContext.getEmpNo(),
                request.sessionId(),
                request.configId(),
                request.skillId(),
                request.content().substring(0, Math.min(request.content().length(), 30)));
        // 防止反向代理缓冲 SSE 流
        response.setHeader("X-Accel-Buffering", "no");
        response.setHeader("Cache-Control", "no-cache, no-transform");
        return chatService.sendMessage(
                request.sessionId(),
                request.configId(),
                request.content(),
                request.skillId()
        );
    }

    /**
     * 删除会话及其所有消息。
     */
    @DeleteMapping("/session/{sessionId}")
    public Result<Void> deleteSession(@PathVariable String sessionId) {
        log.info("[{}] 删除会话: sessionId={}", EmpContext.getEmpNo(), sessionId);
        chatService.deleteSession(sessionId);
        return Result.success();
    }

    /**
     * 更新会话标题。
     */
    @PutMapping("/session/{sessionId}/title")
    public Result<Void> updateTitle(@PathVariable String sessionId, @RequestBody Map<String, String> body) {
        String title = body.get("title");
        log.info("[{}] 更新会话标题: sessionId={}, title={}", EmpContext.getEmpNo(), sessionId, title);
        chatService.updateTitle(sessionId, title);
        return Result.success();
    }
}

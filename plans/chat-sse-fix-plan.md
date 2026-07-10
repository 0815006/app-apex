# Chat SSE 流式输出修复方案

## 根因总结

Agent 功能引入后，`ChatView` 的会话列表未按 `session_mode` 过滤，导致 **AGENT 会话混入 ChatView**。当用户在 ChatView 中点击 AGENT 会话发送消息时：

```
前端: ChatView → sendChatMessage()
        ↓ 只处理 event:reasoning|message|done|error
后端: ChatService.sendMessage() → session_mode='AGENT'
        ↓
      executeAgentLoop() → SSE event:agent + data:{"type":"text",...}
        ↓
前端: dispatchEvent() 不识别 event:agent
        ↓
      eventStarted = false → onError("服务器未返回有效响应...")
```

**结论**：不需要回退 Agent 功能，修复 ChatView 的会话过滤 + 后端增加防御性校验即可。

---

## 修复清单

### 🔴 P0-1：ChatView 会话列表按 mode 过滤

**文件**：[`web-apex-vue/src/views/ChatView.vue`](web-apex-vue/src/views/ChatView.vue)，第 393 行

```diff
-    const res = await listSessions()
+    const res = await listSessions('CHAT')
```

**影响**：彻底阻止 Agent 会话出现在 ChatView 的会话列表中。

---

### 🔴 P0-2：后端防御——ChatView 误传 workspaceId 时强制走 CHAT 模式

**文件**：[`java-apex-server/src/main/java/com/apex/service/ChatService.java`](java-apex-server/src/main/java/com/apex/service/ChatService.java)，第 188-204 行

当前逻辑：创建新会话时如果传了 `workspaceId` 就自动设 `mode = "AGENT"`。但 ChatView 的旧版可能在某个升级路径中意外传了 `workspaceId`。

修复方案：在 `sendMessage()` 新增会话后、分流前增加防御校验：

```java
// 在第 206 行 (String mode = session.getSessionMode()) 之前插入
// 防御：如果请求没带 workspaceId 但 session 是 AGENT（被污染数据），
// 或者请求带了 workspaceId 但前端是 ChatView 调用（无 workspaceId 但在会话列表中误选），
// 则强制按 CHAT 处理
String mode = session.getSessionMode();
if (mode == null) mode = "CHAT";

// 新增：如果 mode 是 AGENT 但没有 workspaceId，降级为 CHAT
if ("AGENT".equals(mode)) {
    String wsId = session.getWorkspaceId();
    if (wsId == null || wsId.isBlank()) {
        log.warn("[{}] AGENT 会话缺少 workspaceId，降级为 CHAT: sessionId={}", empNo, sessionId);
        mode = "CHAT";
    }
}
```

**更建议的防御位置**：在 `createSession` 方法中，如果 mode 参数是 `"AGENT"` 但 `workspaceId` 为空，直接报错或降级：

```java
// createSession() 方法中，约第 114-133 行
if ("AGENT".equals(mode) && (workspaceId == null || workspaceId.isBlank())) {
    throw new BusinessException(400, "Agent 会话必须关联工作空间");
}
```

---

### 🟡 P1-1：LlmRequest 外层 record 加 @JsonInclude(NON_NULL)

**文件**：[`java-apex-server/src/main/java/com/apex/model/LlmRequest.java`](java-apex-server/src/main/java/com/apex/model/LlmRequest.java)，第 12 行

```diff
+ @JsonInclude(JsonInclude.Include.NON_NULL)
  public record LlmRequest(
```

**影响**：经典对话时请求 JSON 不再包含 `"tools": null, "tool_choice": null`，避免干扰严格的 LLM 供应商实现。

---

### 🟡 P1-2：sendChatMessage 超时调整

**文件**：[`web-apex-vue/src/api/chat.ts`](web-apex-vue/src/api/chat.ts)，第 87 行

```diff
-  const READ_TIMEOUT_MS = 60_000
+  const READ_TIMEOUT_MS = 120_000
```

**影响**：经典 Chat 模式超时从 60s 提升到 120s，与长时间思考场景对齐。

---

### 🟢 P2（可选）：恢复 Sidebar Agent 入口

**文件**：[`web-apex-vue/src/components/Layout/Sidebar.vue`](web-apex-vue/src/components/Layout/Sidebar.vue)，第 30-33 行

当前 Agent 菜单项被注释。完成 P0 修复后，ChatView 与 AgentView 互不干扰，可安全恢复入口：

```diff
-      <!-- <el-menu-item index="/agent">
-        <el-icon><Cpu /></el-icon>
-        <span>Agent</span>
-      </el-menu-item> -->
+      <el-menu-item index="/agent">
+        <el-icon><Cpu /></el-icon>
+        <span>Agent</span>
+      </el-menu-item>
```

---

## 修复顺序

| 序号 | 修复项 | 文件 | 级别 |
|------|--------|------|------|
| 1 | ChatView 按 mode 过滤会话列表 | `ChatView.vue:393` | 🔴 P0 |
| 2 | 后端防御 AGENT 会话无 workspaceId | `ChatService.java:206` | 🔴 P0 |
| 3 | LlmRequest 加 @JsonInclude(NON_NULL) | `LlmRequest.java:12` | 🟡 P1 |
| 4 | sendChatMessage 超时 → 120s | `chat.ts:87` | 🟡 P1 |
| 5 | 恢复 Sidebar Agent 入口 | `Sidebar.vue:30-33` | 🟢 P2 |

## 不需要做的事情

- ❌ 回退 ChatService 重构 —— 经典对话路径完整无损
- ❌ 回退数据库 Flyway V9 —— 数据迁移安全，老数据默认 `session_mode = 'CHAT'`
- ❌ 修改 AgentView 或 sendAgentMessage —— 隔离性好，无需变更

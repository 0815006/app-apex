# AI 大模型对话功能 — 现状分析

> **分析日期**: 2026-07-05  
> **分析范围**: AI 对话 (`ChatView`)、Skill 管理 (`SkillManager`)、LLM 配置管理 (`LlmConfigDialog`)  
> **技术栈**: Java 21 (虚拟线程) + Spring Boot 3.4 + MyBatis Plus 3.5 + Vue 3 + TypeScript + Element Plus

---

## 一、架构总览

系统采用 **OpenAI 兼容接口代理转发** 模式：后端不直接调用任何大模型 SDK，而是将前端请求转换为符合 OpenAI `/v1/chat/completions` 格式的 HTTP 请求，通过 JDK `HttpClient` 代理到用户配置的任意第三方 LLM API（如 DeepSeek、OpenAI、Ollama 等），再将 SSE 流式响应逐 chunk 转发给前端。

```
┌─────────────────────────────────────────────────────┐
│                    Vue 3 前端                         │
│  ChatView.vue  ─── fetch + ReadableStream 解析 SSE   │
│  LlmConfigDialog.vue  ─── LLM 配置 CRUD             │
│  SkillManager.vue  ─── Skill 预设管理               │
└──────────────┬──────────────────────────────────────┘
               │ POST /api/chat/send  (SSE)
               ▼
┌─────────────────────────────────────────────────────┐
│              Spring Boot 3.4 后端                     │
│  ChatController  ─── 路由控制器                       │
│  ChatService     ─── 核心会话/流式代理逻辑             │
│  LlmService      ─── 配置管理（多租户隔离）            │
│  AiSkillService  ─── Skill CRUD                      │
│  LlmRequest      ─── OpenAI 兼容请求体 Record         │
└──────────────┬──────────────────────────────────────┘
               │ JDK HttpClient 代理转发
               ▼
┌─────────────────────────────────────────────────────┐
│              第三方 LLM API                           │
│  POST {apiUrl}/chat/completions  (SSE stream)        │
└─────────────────────────────────────────────────────┘
```

---

## 二、数据库表清单

### 2.1 `llm_config` — 大模型配置表

**Flyway 脚本**: [`V2__create_chat_tables.sql`](../java-apex-server/src/main/resources/db/migration/V2__create_chat_tables.sql:7)

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | VARCHAR(32) | 主键，雪花 ID |
| `user_id` | VARCHAR(7) | 用户工号（多租户隔离） |
| `config_name` | VARCHAR(100) | 配置别名（如"个人DeepSeek"） |
| `api_url` | VARCHAR(500) | LLM Base URL |
| `api_key` | VARCHAR(500) | API 密钥 |
| `model_name` | VARCHAR(100) | 模型名称（如 `deepseek-chat`） |
| `create_time` | DATETIME | 创建时间 |
| `update_time` | DATETIME | 更新时间 |

- **索引**: `idx_user_id` ON (`user_id`)
- **安全策略**: 列表接口 (`LlmConfigVO`) 只返回 `id/configName/modelName`，不暴露 `apiKey`；详情接口需校验用户归属

### 2.2 `chat_session` — 对话会话表

**Flyway 脚本**: [`V2__create_chat_tables.sql`](../java-apex-server/src/main/resources/db/migration/V2__create_chat_tables.sql:21)

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | VARCHAR(32) | 主键，雪花 ID |
| `user_id` | VARCHAR(7) | 用户工号（多租户隔离） |
| `title` | VARCHAR(200) | 会话标题（默认"新对话"，首条消息后取前10字） |
| `config_id` | VARCHAR(32) | 绑定的 LLM 配置 ID |
| `create_time` | DATETIME | 创建时间 |
| `update_time` | DATETIME | 更新时间（每次对话更新，用于置顶排序） |

- **索引**: `idx_user_id_update` ON (`user_id`, `update_time` DESC)

### 2.3 `chat_message` — 消息明细表

**Flyway 脚本**: [`V2__create_chat_tables.sql`](../java-apex-server/src/main/resources/db/migration/V2__create_chat_tables.sql:33)

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | VARCHAR(32) | 主键，雪花 ID |
| `session_id` | VARCHAR(32) | 所属会话 ID |
| `role` | VARCHAR(20) | 角色：`user`（用户）或 `assistant`（AI） |
| `content` | LONGTEXT | 消息文本内容 |
| `create_time` | DATETIME | 发送时间 |

- **索引**: `idx_session_id` ON (`session_id`), `idx_session_create` ON (`session_id`, `create_time` ASC)

### 2.4 `ai_skill` — AI 技能主表

**Flyway 脚本**: [`V4__create_skill_tables.sql`](../java-apex-server/src/main/resources/db/migration/V4__create_skill_tables.sql:8)

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | VARCHAR(64) | 主键，雪花 ID |
| `name` | VARCHAR(100) | 技能名称 |
| `icon` | VARCHAR(100) | 图标（预留） |
| `description` | VARCHAR(255) | 功能简介 |
| `type` | VARCHAR(20) | 类型：`prompt` / `agent` / `workflow` |
| `system_prompt` | TEXT | System 角色提示词内容 |
| `temperature` | DECIMAL(3,2) | 采样温度，默认 0.70 |
| `workflow_id` | VARCHAR(64) | 关联工作流 ID（预留） |
| `status` | TINYINT(1) | 状态：1=启用，0=禁用 |
| `sort_order` | INT(11) | 排序序号 |
| `create_time` | DATETIME | 创建时间 |
| `update_time` | DATETIME | 更新时间 |

### 2.5 `ai_tool` — 原子工具定义表（预留，未启用）

**Flyway 脚本**: [`V4__create_skill_tables.sql`](../java-apex-server/src/main/resources/db/migration/V4__create_skill_tables.sql:25)

| 字段 | 说明 |
|---|---|
| `id` | 工具唯一标识 |
| `name` / `description` | 工具名称与功能描述 |
| `declaration_json` | OpenAI Function Calling 标准 JSON 声明 |
| `adapter_bean` | 后端执行工具映射 Bean 名 |

### 2.6 `ai_skill_tool_relation` — 技能-工具绑定关系表（预留，未启用）

**Flyway 脚本**: [`V4__create_skill_tables.sql`](../java-apex-server/src/main/resources/db/migration/V4__create_skill_tables.sql:37)

技能与工具的多对多关联表，当前阶段未使用。

---

## 三、后端 API 接口清单

### 3.1 LLM 配置管理 (`/api/llm-config`)

**Controller**: [`LlmConfigController.java`](../java-apex-server/src/main/java/com/apex/controller/LlmConfigController.java:1)

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/llm-config` | 获取当前用户所有配置（下拉框用，不暴露 apiKey） |
| GET | `/api/llm-config/{id}` | 获取单个配置详情（含 apiKey，校验归属） |
| POST | `/api/llm-config` | 新增配置 |
| PUT | `/api/llm-config/{id}` | 更新配置 |
| DELETE | `/api/llm-config/{id}` | 删除配置 |

### 3.2 对话聊天 (`/api/chat`)

**Controller**: [`ChatController.java`](../java-apex-server/src/main/java/com/apex/controller/ChatController.java:1)

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/chat/sessions` | 获取当前用户的会话列表（按 update_time 倒序） |
| GET | `/api/chat/messages/{sessionId}` | 获取指定会话的消息历史（按时间正序） |
| POST | `/api/chat/send` | **核心接口**：发送消息，返回 SSE 流式响应 |
| DELETE | `/api/chat/session/{sessionId}` | 删除会话及其所有消息 |
| PUT | `/api/chat/session/{sessionId}/title` | 更新会话标题 |

### 3.3 Skill 技能管理 (`/api/skill`)

**Controller**: [`AiSkillController.java`](../java-apex-server/src/main/java/com/apex/controller/AiSkillController.java:1)

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/skill/enabled` | 获取所有启用的 Skill（ChatView 选择用） |
| GET | `/api/skill` | 获取全部 Skill（管理页面用，含禁用） |
| GET | `/api/skill/{id}` | 获取单个 Skill |
| POST | `/api/skill` | 新增 Skill |
| PUT | `/api/skill/{id}` | 更新 Skill |
| DELETE | `/api/skill/{id}` | 删除 Skill |

---

## 四、核心技术实现细节

### 4.1 SSE 流式代理转发（核心链路）

**实现位置**: [`ChatService.sendMessage()`](../java-apex-server/src/main/java/com/apex/service/ChatService.java:184)

完整流程如下：

```
前端 fetch POST /api/chat/send
  │
  ▼
ChatController.sendMessage()
  │  设置 X-Accel-Buffering: no（防止 Nginx 缓冲）
  │  设置 Cache-Control: no-cache, no-transform
  ▼
ChatService.sendMessage()
  │
  ├─ 1. 校验 LLM 配置归属（多租户隔离）
  ├─ 2. sessionId 为空 → 自动创建新会话（标题 = 首条消息前10字）
  ├─ 3. 保存用户消息到 chat_message 表
  ├─ 4. 更新 session.update_time（触发置顶排序）
  ├─ 5. 加载历史消息上下文（过滤刚插入的用户消息防重复）
  ├─ 6. 查询 Skill system_prompt（若指定了 skillId）
  ├─ 7. 构建 LlmRequest（OpenAI 兼容格式）
  │      model: "deepseek-chat"
  │      messages: [{role:"system",content:"..."}, {role:"user",content:"..."}, ...]
  │      stream: true
  ├─ 8. 创建 SseEmitter（超时 5 分钟）
  ├─ 9. Thread.ofVirtual().start() 异步执行
  │      │
  │      ├─ JDK HttpClient + BodyHandlers.ofLines() 逐行流式消费
  │      │   ├─ 解析 "data: " 开头的 SSE 行
  │      │   ├─ 提取 choices[0].delta.content → emitter.send("message", chunk)
  │      │   ├─ 提取 choices[0].delta.reasoning_content → emitter.send("reasoning", chunk)
  │      │   │   （DeepSeek R1 等推理模型的思考链）
  │      │   ├─ 遇到 "data: [DONE]" → 流结束
  │      │   └─ 非流式回退：若整个响应无 SSE data 行，解析为普通 JSON
  │      │
  │      ├─ 保存 AI 回复到 chat_message 表
  │      └─ emitter.send("done", {sessionId, messageId})
  │
  └─ 返回 SseEmitter
```

**关键设计亮点**:
- **JDK HttpClient 原生逐行消费**：使用 `BodyHandlers.ofLines()` 返回 `Stream<String>`，绝不缓冲整个响应体
- **虚拟线程异步**：`Thread.ofVirtual().start()` 执行 LLM 代理，不阻塞 Tomcat 请求线程
- **SSE 事件类型**：`message`(正文)、`reasoning`(思考链)、`done`(完成)、`error`(异常)
- **非流式回退兼容**：若 LLM 返回的是普通 JSON（非 SSE），自动降级解析

### 4.2 前端 SSE 消费

**实现位置**: [`sendChatMessage()`](../web-apex-vue/src/api/chat.ts:61)

- 使用原生 `fetch` + `ReadableStream` 解析 SSE（非 axios，因为 axios 不支持流式读取）
- 完整实现了 SSE 协议状态机：逐行解析 `event:` 和 `data:` 字段，按空行边界分发事件
- **60 秒流读取超时**：若 60 秒内无新数据，自动断开并报错
- 返回 `AbortController`：支持用户点击"停止生成"按钮中断流
- 手动注入 `X-Emp-No` 请求头（fetch 不走 axios 拦截器）

### 4.3 思考链（Reasoning）展示

前端在流式输出时，可通过 `onReasoning` 回调接收 `reasoning_content`（如 DeepSeek R1 的思考过程）。
- 在 [`ChatView.vue`](../web-apex-vue/src/views/ChatView.vue:165) 中使用可折叠面板展示
- 默认展开，用户可点击"思考过程"标题栏折叠
- 提示文字："可在配置中关闭展示"（当前仅 UI 提示，实际关闭开关尚未实现）

### 4.4 Skill 注入机制

当用户在 ChatView 中选择了一个 Skill 后发送消息：

1. 前端将 `skillId` 随请求体发送到 `/api/chat/send`
2. 后端在 [`ChatService.sendMessage()`](java-apex-server/src/main/java/com/apex/service/ChatService.java:229) 中查询对应 Skill
3. 若 Skill 状态为启用且 `systemPrompt` 不为空，将其作为 `role: "system"` 消息注入到 LLM 请求的 `messages` 数组开头
4. 当前仅支持 `prompt` 类型（纯 System Prompt 注入）

### 4.5 多租户隔离

所有数据操作基于 `EmpContext.getEmpNo()` 获取的 7 位员工号进行隔离：

| 表 | 隔离字段 | 说明 |
|---|---|---|
| `llm_config` | `user_id` | 每人只能管理自己的模型配置 |
| `chat_session` | `user_id` | 每人只能查看/操作自己的会话 |
| `chat_message` | 间接通过 session 归属校验 | |

`ai_skill` 表 **不隔离**，所有用户共享 Skill 预设（全局公共模板）。

### 4.6 配置项

**`application.yml`** 中 LLM 相关配置：

```yaml
apex:
  llm:
    stream: ${LLM_STREAM_ENABLED:true}  # 默认开启流式，可关闭改为非流式
```

当 `llmStreamEnabled = false` 时，`LlmRequest.of()` 中 `stream` 字段设为 `false`。

---

## 五、前端页面功能清单

### 5.1 ChatView（AI 对话主页）

**文件**: [`ChatView.vue`](../web-apex-vue/src/views/ChatView.vue:1)

**已实现功能**:

| 功能 | 状态 | 说明 |
|---|---|---|
| 会话列表 | ✅ 已实现 | 左侧侧边栏，按更新时间倒序展示 |
| 新建对话 | ✅ 已实现 | 清空当前对话，state 重置 |
| 会话重命名 | ✅ 已实现 | 内联编辑模式，Enter 确认 |
| 会话删除 | ✅ 已实现 | 下拉菜单触发，含 ElMessageBox 二次确认 |
| 模型选择器 | ✅ 已实现 | 顶部 `<el-select>`，从 `llm_config` 动态加载 |
| LLM 配置管理弹窗 | ✅ 已实现 | 底部"大模型配置"按钮 → `LlmConfigDialog.vue` |
| 消息发送 | ✅ 已实现 | Enter 发送，Shift+Enter 换行 |
| SSE 流式输出 | ✅ 已实现 | 打字机效果，实时渲染 Markdown |
| 停止生成 | ✅ 已实现 | `AbortController.abort()` 中断流 |
| 思考链展示 | ✅ 已实现 | 可折叠面板，显示 DeepSeek R1 推理过程 |
| Markdown 渲染 | ✅ 已实现 | 使用 `marked` 库，支持代码高亮、表格、引用等 |
| 一键复制 AI 回答 | ✅ 已实现 | hover 出现复制按钮，`navigator.clipboard` |
| Skill 选择弹窗 | ✅ 已实现 | 通过 Popover 展示启用 Skill 列表，带选中状态 |
| Skill 激活状态栏 | ✅ 已实现 | 选择 Skill 后底部显示绿色提示条，可关闭 |
| 发消息时无模型警告 | ✅ 已实现 | 未选模型时禁用发送按钮/输入框 |
| 空状态引导 | ✅ 已实现 | 无会话时显示空状态提示 |

**未实现/待扩展**:
- ❌ Skill 中 `agent` 和 `workflow` 类型的实际执行逻辑（UI 已禁用）
- ❌ 思考链"可在配置中关闭展示"的开关功能（仅文字提示）
- ❌ 消息编辑/重新生成
- ❌ 对话搜索

### 5.2 SkillManager（Skill 管理页面）

**文件**: [`SkillManager.vue`](../web-apex-vue/src/views/SkillManager.vue:1)

**已实现功能**:

| 功能 | 状态 | 说明 |
|---|---|---|
| Skill 列表展示 | ✅ 已实现 | `el-table` 展示所有 Skill（含禁用），支持加载状态 |
| 新建 Skill | ✅ 已实现 | 弹窗表单，必填名称 |
| 编辑 Skill | ✅ 已实现 | 弹窗回填已有数据 |
| 删除 Skill | ✅ 已实现 | Popconfirm 二次确认 |
| 类型选择 | ✅ 已实现 | 三个选项：prompt / agent / workflow（后两个 disabled） |
| System Prompt 编辑 | ✅ 已实现 | textarea，默认 8 行 |
| 温度滑块 | ✅ 已实现 | `el-slider`，0~2，步长 0.1，默认 0.7 |
| 排序序号 | ✅ 已实现 | `el-input-number`，0~9999 |
| 启用/禁用开关 | ✅ 已实现 | `el-switch`，启用=1 / 禁用=0 |
| 类型标签颜色 | ✅ 已实现 | prompt=绿色, agent=橙色, workflow=灰色 |

**未实现/待扩展**:
- ❌ `agent` 类型：工具调用（Function Calling）的完整链路
- ❌ `workflow` 类型：工作流编排引擎
- ❌ `ai_tool` 表的前端管理界面
- ❌ 图标字段（`icon`）的实际使用（UI 中无编辑入口）

### 5.3 LlmConfigDialog（LLM 配置管理弹窗）

**文件**: [`LlmConfigDialog.vue`](../web-apex-vue/src/components/chat/LlmConfigDialog.vue:1)

**已实现功能**:

| 功能 | 状态 | 说明 |
|---|---|---|
| 配置列表展示 | ✅ 已实现 | 展示 configName / modelName / apiUrl |
| 新增配置 | ✅ 已实现 | 表单含 configName, apiUrl, apiKey, modelName |
| 编辑配置 | ✅ 已实现 | 回填已有数据 |
| 删除配置 | ✅ 已实现 | Popconfirm 二次确认 |
| API Key 密码输入 | ✅ 已实现 | `type="password" show-password` |
| 表单校验 | ✅ 已实现 | 四个字段均必填 |
| 关闭自动刷新列表 | ✅ 已实现 | watch 监听弹窗关闭状态，自动重新加载 ChatView 的模型下拉 |

---

## 六、数据流与交互时序

### 6.1 发送一条消息的完整时序

```
用户输入消息 → 按 Enter
  │
  ▼
ChatView.handleSend()
  │
  ├─ 1. 本地立即 push 用户消息到 messages[]（乐观渲染）
  ├─ 2. 清空输入框
  ├─ 3. 设置 sending=true, streaming=true
  └─ 4. 调用 sendChatMessage(sessionId, configId, content, callbacks, skillId)
        │
        ▼
      fetch POST /api/chat/send
        │  headers: { X-Emp-No: "1234567" }
        │  body: { sessionId, configId, content, skillId }
        ▼
      ChatController.sendMessage()
        │  EmpContext 设置当前工号
        ▼
      ChatService.sendMessage()
        │
        ├─ 校验 config 归属
        ├─ sessionId==null → 创建新会话
        ├─ 保存 user 消息
        ├─ 加载历史上下文
        ├─ 查询 Skill systemPrompt（若指定）
        ├─ 构建 LlmRequest
        └─ 虚拟线程代理转发
            │  JDK HttpClient → LLM API
            │  逐行 SSE 解析
            │  emitter.send("message", chunk)  ────┐
            │  emitter.send("reasoning", chunk) ───┤
            │  emitter.send("done", {id}) ─────────┤
            └─ 保存 assistant 消息到 DB            │
                                                   ▼
      前端 SSE ReadableStream 回调
        │
        ├─ onMessage(chunk)  → streamingContent += chunk  → 实时渲染 Markdown
        ├─ onReasoning(chunk) → streamingReasoning += chunk → 思考链面板
        └─ onDone({sessionId, messageId})
            │
            ├─ 若 sessionId 变更（新会话）→ 更新 currentSessionId
            ├─ push 完整的 assistant 消息到 messages[]
            ├─ streaming=false, sending=false
            └─ loadSessions() 刷新左侧会话列表
```

### 6.2 Skill 选择流程

```
ChatView 输入区 → 点击"选择 Skill"按钮 → Popover 展开
  │
  ├─ 调用 listEnabledSkills() → GET /api/skill/enabled
  ├─ 展示启用状态且按 sort_order 排序的 Skill 列表
  ├─ 用户点击某个 Skill → selectedSkillId / selectedSkillName 更新
  ├─ 底部显示绿色 "已激活 Skill" 状态条
  └─ 发送消息时 skillId 随请求体传给后端 → 注入 systemPrompt
```

---

## 七、关键技术决策与约束

| 决策 | 说明 |
|---|---|
| **无 SDK 依赖，纯 HTTP 代理** | 不依赖任何 LLM SDK（如 OpenAI Java SDK），直接 JDK HttpClient 构建 OpenAI 兼容请求，最大化兼容性 |
| **SSE 而非 WebSocket** | 采用单向 SSE 流式输出，简单可靠，无需维护长连接状态 |
| **虚拟线程处理 IO** | `Thread.ofVirtual().start()` 处理 LLM 代理的长时间 IO 等待，不占用 Tomcat 线程池 |
| **BodyHandlers.ofLines()** | JDK 内置的流式消费 API，逐行惰性读取，保证不缓冲整个响应体 |
| **非流式回退** | 自动检测响应格式，若无 SSE `data:` 行则按普通 JSON 解析，兼容不支持流式的 LLM |
| **fetch 而非 EventSource** | 前端使用 `fetch` + `ReadableStream` 实现 SSE 消费，因为 `EventSource` 不支持 POST 请求和自定义请求头 |
| **60s 前端超时** | 流读取无数据超时保护，防止僵尸连接 |
| **多租户** | `llm_config` 和 `chat_session` 按 userId 隔离，`ai_skill` 全局共享 |
| **Skill 仅为 prompt 类型** | 当前阶段仅实现 System Prompt 注入，agent/workflow 表结构已预留但未实现 |

---

## 八、已实现 vs 规划中功能对照

| 功能域 | 已实现 | 规划中/预留 |
|---|---|---|
| **LLM 配置** | CRUD、多租户隔离、API Key 安全保护 | — |
| **对话管理** | 多会话、重命名、删除、历史消息加载 | 消息编辑/重新生成、对话搜索 |
| **流式输出** | SSE 打字机效果、Markdown 实时渲染 | WebSocket 双向通信 |
| **思考链** | DeepSeek R1 reasoning_content 折叠展示 | 配置化开关 |
| **Skill 体系** | prompt 类型 CRUD + System Prompt 注入 | agent 类型（Function Calling 工具调用）、workflow 类型（工作流编排） |
| **工具调用** | 表结构预留 (`ai_tool`, `ai_skill_tool_relation`) | Agent 循环、多步推理、工具执行适配器 |
| **多模态** | — | 图片输入、文件上传分析 |

---

## 九、相关文件索引

### 后端核心文件

| 文件 | 路径 |
|---|---|
| Chat Controller | [`java-apex-server/src/main/java/com/apex/controller/ChatController.java`](../java-apex-server/src/main/java/com/apex/controller/ChatController.java) |
| Chat Service | [`java-apex-server/src/main/java/com/apex/service/ChatService.java`](../java-apex-server/src/main/java/com/apex/service/ChatService.java) |
| LlmConfig Controller | [`java-apex-server/src/main/java/com/apex/controller/LlmConfigController.java`](../java-apex-server/src/main/java/com/apex/controller/LlmConfigController.java) |
| Llm Service | [`java-apex-server/src/main/java/com/apex/service/LlmService.java`](../java-apex-server/src/main/java/com/apex/service/LlmService.java) |
| AiSkill Controller | [`java-apex-server/src/main/java/com/apex/controller/AiSkillController.java`](../java-apex-server/src/main/java/com/apex/controller/AiSkillController.java) |
| AiSkill Service | [`java-apex-server/src/main/java/com/apex/service/AiSkillService.java`](../java-apex-server/src/main/java/com/apex/service/AiSkillService.java) |
| Entity: ChatSession | [`java-apex-server/src/main/java/com/apex/entity/ChatSession.java`](../java-apex-server/src/main/java/com/apex/entity/ChatSession.java) |
| Entity: ChatMessage | [`java-apex-server/src/main/java/com/apex/entity/ChatMessage.java`](../java-apex-server/src/main/java/com/apex/entity/ChatMessage.java) |
| Entity: LlmConfig | [`java-apex-server/src/main/java/com/apex/entity/LlmConfig.java`](../java-apex-server/src/main/java/com/apex/entity/LlmConfig.java) |
| Entity: AiSkill | [`java-apex-server/src/main/java/com/apex/entity/AiSkill.java`](../java-apex-server/src/main/java/com/apex/entity/AiSkill.java) |
| Model: ChatRequest | [`java-apex-server/src/main/java/com/apex/model/ChatRequest.java`](../java-apex-server/src/main/java/com/apex/model/ChatRequest.java) |
| Model: LlmRequest | [`java-apex-server/src/main/java/com/apex/model/LlmRequest.java`](../java-apex-server/src/main/java/com/apex/model/LlmRequest.java) |
| Model: ChatSessionVO | [`java-apex-server/src/main/java/com/apex/model/ChatSessionVO.java`](../java-apex-server/src/main/java/com/apex/model/ChatSessionVO.java) |
| Model: LlmConfigVO | [`java-apex-server/src/main/java/com/apex/model/LlmConfigVO.java`](../java-apex-server/src/main/java/com/apex/model/LlmConfigVO.java) |
| Model: SkillVO | [`java-apex-server/src/main/java/com/apex/model/SkillVO.java`](../java-apex-server/src/main/java/com/apex/model/SkillVO.java) |
| Flyway V2 (Chat) | [`java-apex-server/src/main/resources/db/migration/V2__create_chat_tables.sql`](../java-apex-server/src/main/resources/db/migration/V2__create_chat_tables.sql) |
| Flyway V4 (Skill) | [`java-apex-server/src/main/resources/db/migration/V4__create_skill_tables.sql`](../java-apex-server/src/main/resources/db/migration/V4__create_skill_tables.sql) |
| Config | [`java-apex-server/src/main/resources/application.yml`](../java-apex-server/src/main/resources/application.yml) |

### 前端核心文件

| 文件 | 路径 |
|---|---|
| ChatView | [`web-apex-vue/src/views/ChatView.vue`](../web-apex-vue/src/views/ChatView.vue) |
| SkillManager | [`web-apex-vue/src/views/SkillManager.vue`](../web-apex-vue/src/views/SkillManager.vue) |
| LlmConfigDialog | [`web-apex-vue/src/components/chat/LlmConfigDialog.vue`](../web-apex-vue/src/components/chat/LlmConfigDialog.vue) |
| API: chat.ts | [`web-apex-vue/src/api/chat.ts`](../web-apex-vue/src/api/chat.ts) |
| API: skill.ts | [`web-apex-vue/src/api/skill.ts`](../web-apex-vue/src/api/skill.ts) |
| Types: chat.ts | [`web-apex-vue/src/types/chat.ts`](../web-apex-vue/src/types/chat.ts) |
| Types: skill.ts | [`web-apex-vue/src/types/skill.ts`](../web-apex-vue/src/types/skill.ts) |

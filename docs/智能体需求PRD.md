# 智能体（Agent）功能需求 PRD — 基于现状改造升级

> **版本**: v2.1（专家评审修订版）  
> **日期**: 2026-07-05  
> **技术路线**: **纯 JDK HttpClient + while 循环状态机**，不引入任何 AI 框架（LangChain4j / Spring AI 等）  
> **前置阅读**: [`AI_LLM_STATUS_ANALYSIS.md`](AI_LLM_STATUS_ANALYSIS.md) — 当前系统已实现的 AI 大模型功能现状

---

## 一、背景与现状

### 1.1 系统当前已具备的基础设施

当前系统已完整实现以下能力（详见[现状分析](AI_LLM_STATUS_ANALYSIS.md)）：

| 已具备能力 | 对应组件 | 可复用性 |
|---|---|---|
| LLM 多模型动态配置 | `llm_config` 表 + `LlmService` | ✅ 直接复用 |
| SSE 流式代理转发 | `ChatService.sendMessage()` + JDK `HttpClient` | ✅ 核心链路直接扩展 |
| 多租户会话管理 | `chat_session` + `chat_message` 表 | ✅ 直接复用 |
| Skill 预设体系 | `ai_skill` 表（含 `type` 字段已预留 `agent`/`workflow`） | ✅ 直接启用 |
| 工具定义表 | `ai_tool` + `ai_skill_tool_relation`（已建表，未启用） | ✅ 直接启用 |
| 虚拟线程异步 | `Thread.ofVirtual().start()` | ✅ 直接复用 |
| Markdown 实时渲染 | `marked` + 思考链折叠面板 | ✅ 直接复用 |

### 1.2 差距分析：从"提示词模式"到"智能体模式"

当前系统的 Skill 仅支持 `prompt` 类型：将 `system_prompt` 注入到 LLM 请求的 `messages` 数组开头。要升级为智能体（Agent），核心差距只有**两层 JSON 协议的组装与解析**：

| 差距项 | 现状 | 目标 |
|---|---|---|
| 请求体 | `LlmRequest` 无 `tools` 字段 | 增加 `tools` 数组 + `tool_choice` |
| 响应解析 | 仅解析 `delta.content` 和 `delta.reasoning_content` | 增加 `delta.tool_calls` 解析 |
| 流处理模式 | 单轮：收到 content → 转发 SSE → 结束 | **多轮 while 循环**：收到 tool_calls → 本地执行 → 追加上下文 → 循环重试直到 content |
| 消息角色 | 仅 `user` / `assistant` | 增加 `tool` 角色 |
| 前端展示 | 纯文本流式输出 | 增加工具调用步骤状态的 SSE 事件推送 |
| 中止机制 | 无（仅前端 AbortController） | 后端 `AtomicBoolean` + `SseEmitter` 生命周期回调，确保多轮虚拟线程可优雅中止 |

---

## 二、技术路线决策

### 2.1 路线选择：现状改造升级（不引入 AI 框架）

**决定**：在现有 `ChatService.sendMessage()` 的 JDK `HttpClient` 代理链路上直接扩展，**不引入 LangChain4j、Spring AI 或任何 AI 框架**。

**核心理由**：

| 考量维度 | 现状改造 | 引入框架 |
|---|---|---|
| **SSE 流控制** | 完全自主的 `while` 循环，可自由插入自定义事件帧（如"正在写文件..."） | 框架接管底层 IO，难以在工具执行中间阶段插入业务推送 |
| **多模型动态切换** | 每次请求动态从 DB 读取配置，`HttpClient` 按需构建 | 框架通常要求模型客户端在启动时注册为单例 Bean，运行时动态切换需要额外 hack |
| **代码一致性** | 所有 Skill（含 prompt/agent）走同一套 `ChatService`，仅分支不同 | 两套代码风格，prompt 用手工、agent 用框架，割裂 |
| **依赖复杂度** | 零新增依赖 | 引入大量传递依赖，版本冲突风险 |
| **协议透明度** | 直接控制 OpenAI 兼容 JSON 的每一层字段 | 框架抽象层隐藏协议细节，出问题时难以排查 |

### 2.2 改造核心思路（三步法）

参照以下路线图，在现有代码骨架上做轻量缝合：

```
Step 0（MVP 前置）: 硬编码首批工具 JSON Schema
  在 MVP 初期阶段，ai_tool 表的 CRUD 管理界面尚未就绪
  但 ChatService 的 while 循环必须依赖 tools 数组才能进入 agent 分支
  解决方案：在 ToolExecutor 中硬编码 3 个工具的 declaration_json
  后续工具管理页上线后，再迁移为从 ai_tool 表动态读取
  硬编码只是"桥梁"，不是最终形态

Step 1: 协议层 DTO 升级
  LlmRequest 增加 tools / tool_choice 字段
  响应解析增加 tool_calls delta 分支

Step 2: 核心流处理「状态机」改造 — 多轮 while 循环
  while 循环增加状态判断（max_loops = 5 守卫）：
    - 普通状态: content chunk → emitter.send("message", chunk)
      → 流结束后保存 assistant 消息，退出循环，SSE 正常结束
    - 工具状态: tool_calls chunk → 按 index 分组累积参数 (ToolCallAccumulator)
      → 流结束后本地执行全部工具 → emitter.send("tool_status", {name, status})
      → 将 assistant(tool_calls) + tool(result) 追加到消息上下文
      → loopCount++ 并回到循环顶部，发起新一轮 LLM 请求
      → 若 loopCount >= maxLoops，强制退出并推送 "达到最大工具调用轮次" 提示
  中止机制：每轮循环顶部检查 taskCancelled 标志位，若为 true 则退出

Step 3: 上下文自动累增与消息持久化
  每轮循环结束时，工具执行结果作为 role:"tool" 消息写入 chat_message 表
  下一轮 buildExtendedHistory() 查询时自动包含上一轮的 assistant(tool_calls) + tool(result)
  无需手动管理临时消息列表，数据库即状态
```

---

## 三、新增功能模块

在现有系统基础上，需要新增/扩展以下模块：

```
现有系统
├── LlmService (llm_config 管理) ───── 扩展：增加 is_agent_supported 字段
├── ChatService (SSE 代理转发) ─────── 核心改造：while 循环状态机 + 工具调用 + 中止机制
├── AiSkillService (ai_skill 管理) ─── 扩展：启用 agent 类型 + 工具绑定接口
│
新增
├── ToolExecutor (工具执行引擎) ─────── 新增：本地 I/O 安全沙箱
├── AgentWorkspaceService ──────────── 新增：工作空间管理
└── 前端文件浏览器组件 ──────────────── 新增：工作空间目录树
```

### 3.1 工具执行引擎（Tool Executor）— 新增

**核心职责**：真正执行本地 I/O 的安全沙箱，由 `ChatService` 在收到 `tool_calls` 时调用。

**首批工具清单**：

| 工具名 | 功能 | 参数 | 安全约束 |
|---|---|---|---|
| `read_file` | 读取工作空间内指定文件 | `filePath: String` | 路径必须在 `agent_workspace.root_path` 范围内 |
| `write_file` | 写入/覆盖文件（含自动创建目录） | `filePath: String`, `content: String` | 路径越界检查，禁止写入系统目录 |
| `list_dir` | 列出目录结构 | `dirPath: String`（可选，默认根目录） | 路径越界检查 |

**安全设计（详见 5.3 节完整实现）**：

- **路径越界检查**：`Path.toRealPath()` 解析符号链接后，判断 `normalizedPath.startsWith(workspaceRoot.toRealPath())`
- **符号链接攻击防御**：`toRealPath()` 会自动解析所有软链接，攻击者无法通过链接逃逸工作空间
- **文件覆盖/写入安全**：写入前检查目标路径不指向系统目录、不在禁止列表中
- **大文件防护**：`read_file` 读取上限 `MAX_READ_SIZE = 2MB`，超出截断并标注 `[FILE_TRUNCATED]`
- **隐藏文件限制**：禁止访问以 `.` 开头的隐藏文件/目录（可配置白名单）

### 3.2 工作空间管理（Agent Workspace）— 新增

**核心职责**：定义智能体可操作的本地"沙箱"目录，实现项目级隔离。

**功能**：
- 工作空间的 CRUD 管理
- 将本地 `root_path` 映射为树状 JSON 返回前端
- 前端文件浏览器读取目录结构

### 3.3 前端改造

**ChatView 增强**：
- 工具调用步骤的 SSE 事件展示（Timeline / 步骤日志流形式）
- 工作空间选择器（关联当前会话到某个工作空间）
- 输入框 `@` 文件引用自动补全（从工作空间目录树获取文件列表）

**新增页面**：
- 工作空间管理页（CRUD）
- 工具管理页（基于现有 `ai_tool` 表，展示/编辑工具定义）

**SkillManager 增强**：
- 启用 `agent` 类型（当前为 disabled）
- agent 类型 Skill 可绑定多个 `ai_tool`（基于 `ai_skill_tool_relation` 多对多关系）

---

## 四、数据库变更设计

基于现有 Flyway 迁移体系，本次变更通过新增一个 Flyway 脚本来实现。不修改已有表结构（除非通过 ALTER TABLE 扩展列）。

### 4.1 扩展 `llm_config` — 增加 Agent 支持标记

**现有表**: [`llm_config`](../java-apex-server/src/main/resources/db/migration/V2__create_chat_tables.sql:7)

**变更方式**: `ALTER TABLE` 新增字段

```sql
ALTER TABLE `llm_config`
    ADD COLUMN `is_agent_supported` TINYINT(1) DEFAULT 0
    COMMENT '是否支持智能体/工具调用（Function Calling）';
```

### 4.2 扩展 `chat_message` — 增加工具调用相关字段

**现有表**: [`chat_message`](../java-apex-server/src/main/resources/db/migration/V2__create_chat_tables.sql:33)

**变更方式**: `ALTER TABLE` 新增字段

```sql
ALTER TABLE `chat_message`
    ADD COLUMN `tool_name` VARCHAR(64) DEFAULT NULL COMMENT '工具名称（write_file/read_file 等）',
    ADD COLUMN `tool_call_id` VARCHAR(64) DEFAULT NULL COMMENT '工具调用唯一ID（大模型返回的 call_id，tool 角色消息必填）',
    ADD COLUMN `tool_status` VARCHAR(20) DEFAULT NULL COMMENT '工具执行状态：running/success/failed',
    ADD COLUMN `tool_calls_json` TEXT DEFAULT NULL COMMENT '原始 tool_calls JSON（assistant 消息时，完整 JSON 数组）';
```

> **设计说明**：不新建独立的 `agent_message_detail` 表，而是在现有的 `chat_message` 上扩展。理由：
> 1. 同一会话内 prompt 模式和 agent 模式可能混合使用，统一存储便于上下文拼接
> 2. 避免 `chat_message` 和 `agent_message_detail` 双表联查的复杂度
> 3. 新增列均为 NULL，对现有 prompt 模式消息无影响

> **Role 字段扩展**：`role` 字段当前仅存储 `"user"` 和 `"assistant"`。Agent 模式下需增加 `"tool"` 角色：
> - 大模型流式输出完 `tool_calls` 后 → 保存一条 `role="assistant"` 的记录，`tool_calls_json` 存入完整的工具调用 JSON 数组
> - 本地工具执行完毕后 → 插入一条 `role="tool"` 的记录，`tool_call_id` 填入大模型返回的 `call_id`，`content` 填入工具执行结果文本，`tool_name`/`tool_status` 填入对应值
>
> 这样在 `buildExtendedHistory()` 组装多轮上下文时，直接按 `create_time` 顺序查询，天然对应 OpenAI 协议要求的消息序列：
>
> ```
> role: "assistant",  tool_calls_json: [{id:"call_001",...}]
> role: "tool",       tool_call_id: "call_001",  content: "文件读取成功，内容为..."
> ```

### 4.3 新增 `agent_workspace` — 工作空间表

**全新表**：

```sql
CREATE TABLE `agent_workspace` (
    `id` VARCHAR(32) NOT NULL COMMENT '主键（雪花ID）',
    `name` VARCHAR(64) NOT NULL COMMENT '工作空间名称（如：CRM系统优化项目）',
    `root_path` VARCHAR(512) NOT NULL COMMENT '核心：本地绝对路径（如 D:\\projects\\crm）',
    `description` VARCHAR(255) DEFAULT NULL COMMENT '工作空间描述',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='智能体工作空间表';
```

> **设计说明**：工作空间全局共享（不按用户隔离），与 `ai_skill` 保持一致。后期可扩展 `user_id` 字段实现多租户。

### 4.4 扩展 `chat_session` — 关联工作空间

**现有表**: [`chat_session`](../java-apex-server/src/main/resources/db/migration/V2__create_chat_tables.sql:21)

**变更方式**: `ALTER TABLE` 新增字段

```sql
ALTER TABLE `chat_session`
    ADD COLUMN `workspace_id` VARCHAR(32) DEFAULT NULL COMMENT '关联的工作空间 ID（agent 模式时使用）';
```

### 4.5 已有表启用（无需 DDL 变更）

以下两张表已在 [`V4__create_skill_tables.sql`](../java-apex-server/src/main/resources/db/migration/V4__create_skill_tables.sql) 中建好，本次直接启用逻辑层：

| 表名 | 当前状态 | 本次变更 |
|---|---|---|
| `ai_tool` | 已建表，未使用 | 后端实现 `ToolExecutor` 读取 `declaration_json` 构建 `tools` 数组 |
| `ai_skill_tool_relation` | 已建表，未使用 | agent 类型 Skill 通过此表关联工具 |

> **MVP 阶段注意**：`ai_tool` 表虽然已建，但 MVP 初期工具管理 CRUD 界面尚未上线，因此 ToolExecutor 需**硬编码首批 3 个工具的 declaration_json**（详见 5.3.1 节），后续再迁移到动态读取。

### 4.6 变更汇总

| 变更类型 | 目标表 | 说明 |
|---|---|---|
| ALTER TABLE | `llm_config` | +`is_agent_supported` |
| ALTER TABLE | `chat_message` | +`tool_name`, +`tool_call_id`, `tool_status`, `tool_calls_json` |
| ALTER TABLE | `chat_session` | +`workspace_id` |
| CREATE TABLE | `agent_workspace` | 新表 |
| 逻辑启用 | `ai_tool` | 已有表，新增 Service/Mapper/Controller |
| 逻辑启用 | `ai_skill_tool_relation` | 已有表，agent 类型 Skill 绑定工具 |

---

## 五、后端改造详细设计

### 5.1 协议层 DTO 升级

**5.1.1 `LlmRequest` 扩展** — 增加 `tools` 和 `tool_choice` 字段

```java
// LlmRequest.java 增加以下 Record 和字段
public record LlmRequest(
    String model,
    List<Message> messages,
    boolean stream,
    List<Tool> tools,          // 新增：工具定义列表
    String toolChoice          // 新增："auto" / "none" / 指定工具名
) {
    public record Message(String role, String content,
        List<ToolCall> toolCalls,  // 新增：assistant 消息携带的 tool_calls
        String toolCallId,         // 新增：tool 消息携带的 tool_call_id
        String name                // 新增：tool 消息携带的 tool 名称
    ) {}

    public record Tool(
        String type,               // "function"
        Function function          // { name, description, parameters }
    ) {}

    public record Function(
        String name,
        String description,
        Map<String, Object> parameters  // JSON Schema
    ) {}

    public record ToolCall(
        String id,
        String type,               // "function"
        FunctionCall function      // { name, arguments }
    ) {}

    public record FunctionCall(
        String name,
        String arguments           // JSON 字符串
    ) {}

    // 普通 prompt 模式的 of() — 现有签名不变，内部 tools/toolChoice 传空
    public static LlmRequest of(String model, String systemPrompt,
        List<Map<String, String>> history, String newContent, boolean stream) { ... }

    // agent 模式新增重载 — 带 tools + toolChoice
    public static LlmRequest of(String model, String systemPrompt,
        List<Map<String, String>> history, String newContent, boolean stream,
        List<Tool> tools, String toolChoice) { ... }
}
```

**关键设计**：

1. `LlmRequest.of()` 工厂方法增加重载，当 `skill.type == "agent"` 时，从 `ai_skill_tool_relation` 关联的工具中读取 `declaration_json` 动态拼装 `tools` 数组。
2. `Message` Record 扩展后，`of()` 内部的历史消息构造调用需适配：普通 user/assistant 消息传入 `null` 给新增的 `toolCalls`/`toolCallId`/`name` 字段；tool 角色的消息单独构造。
3. **`ChatRequest` 同步扩展**：增加 `workspaceId` 字段（可选），前端发送请求时若关联了工作空间则携带。

**5.1.2 响应 Delta 解析扩展**

在 [`ChatService.sendMessage()`](java-apex-server/src/main/java/com/apex/service/ChatService.java:297) 的 `while` 循环中，增加 `tool_calls` 分支：

```java
// 现有代码（309行附近）增加 tool_calls 解析
var delta = (Map<String, Object>) choices.get(0).get("delta");
if (delta != null) {
    // --- 已有：思考链 ---
    if (delta.get("reasoning_content") != null) { ... }

    // --- 已有：正文 ---
    if (delta.get("content") != null) { ... }

    // +++ 新增：工具调用 +++
    var toolCalls = (List<Map<String, Object>>) delta.get("tool_calls");
    if (toolCalls != null) {
        for (var tc : toolCalls) {
            // 注意：大模型可能一次返回多个 tool_call，以 index 区分
            Integer index = (Integer) tc.get("index");
            String callId = (String) tc.get("id");          // ★ 必须捕获 call_id
            var function = (Map<String, Object>) tc.get("function");
            String toolName = (String) function.get("name");
            String arguments = (String) function.get("arguments");
            // 按 index 分组累积参数片段（支持多个工具并发调用）
            toolCallAccumulators
                .computeIfAbsent(index, k -> new ToolCallAccumulator(callId, toolName))
                .appendArguments(arguments);
            // 推送工具调用步骤状态给前端（每个工具名只推送一次）
            if (!pushedToolNames.contains(toolName)) {
                pushedToolNames.add(toolName);
                emitter.send(SseEmitter.event()
                    .name("tool_status")
                    .data(Map.of("name", toolName, "status", "calling")));
            }
        }
    }
}
```

> **★ 关键**：`ToolCallAccumulator` 必须在第一帧 SSE chunk 到达时就捕获 `call_id`（`tc.get("id")`），因为后续的 SSE delta 片段只有 `index` 和 `function.arguments`，不再携带 `id`。若错过第一帧，call_id 将永久丢失，导致后续无法构建符合 OpenAI 协议的 `role: "tool"` 消息。

**ToolCallAccumulator 数据结构**：

```java
private static class ToolCallAccumulator {
    final String callId;        // ★ 首帧捕获的 tool_call id
    final String toolName;      // 工具名称
    final StringBuilder args;   // 累积的 JSON 参数字符串

    ToolCallAccumulator(String callId, String toolName) {
        this.callId = callId;
        this.toolName = toolName;
        this.args = new StringBuilder();
    }

    void appendArguments(String arguments) {
        this.args.append(arguments);
    }
}
```

### 5.2 核心流处理「状态机」改造 — 多轮 while 循环

**改造文件**: [`ChatService.java`](java-apex-server/src/main/java/com/apex/service/ChatService.java)

**状态机设计（多轮 while 循环）**：

```
                    ┌─────────────────────┐
                    │   用户发送消息         │
                    └─────────┬───────────┘
                              │
                              ▼
              ┌───────────────────────────────┐
              │ 构建 LlmRequest（含 tools 数组） │
              │ loopCount = 0                  │
              │ taskCancelled = new            │
              │   AtomicBoolean(false)         │
              └───────────────┬───────────────┘
                              │
              ┌───────────────▼─────────────────────┐
              │  while 循环守卫：                      │
              │    loopCount < MAX_AGENT_LOOPS (5)    │
              │    && !taskCancelled.get()             │
              └───────────────┬───────────────────────┘
                              │
                              ▼
              ┌───────────────────────────────────────┐
              │ JDK HttpClient → LLM API                │
              │ while 逐行解析 SSE（toolCallAccumulators │
              │   清空为新一轮准备）                      │
              └───────────────┬───────────────────────┘
                              │
              ┌───────────────┴───────────────┐
              │                               │
              ▼                               ▼
    ┌─────────────────┐           ┌─────────────────────┐
    │ content 分支      │           │ tool_calls 分支       │
    │ → SSE 推送正文    │           │ → 按 index 分组累积   │
    │ → 流结束保存消息  │           │ → SSE 推送            │
    │ → done 事件       │           │   tool_status         │
    │ → break 退出循环  │           │   "calling"           │
    └─────────────────┘           └─────────┬───────────┘
                                            │
                                            ▼
                                  ┌─────────────────────┐
                                  │ ToolExecutor 执行     │
                                  │ → 本地 I/O            │
                                  │ → SSE 推送 tool_status │
                                  │   "success"/"failed"  │
                                  └─────────┬───────────┘
                                            │
                                            ▼
                                  ┌─────────────────────┐
                                  │ 消息持久化到 DB       │
                                  │ assistant(tool_calls) │
                                  │ + tool(result)        │
                                  └─────────┬───────────┘
                                            │
                                            ▼
                                  ┌─────────────────────┐
                                  │ loopCount++          │
                                  │ 重新 buildExtended    │
                                  │ History() 获取上下文  │
                                  │ 构建新一轮 LlmRequest  │
                                  │ → 回到循环顶部        │
                                  └─────────────────────┘

  达到 max_loops 时：
    → emitter.send "已达到最大工具调用轮次 (5)"
    → break
```

**伪代码**：

```java
private static final int MAX_AGENT_LOOPS = 5;

public SseEmitter sendMessage(String sessionId, String configId,
        String content, String skillId) {
    // ... 前置校验、保存用户消息、加载历史上下文（现有逻辑不变）...

    // 判断 Skill 类型决定请求体
    List<LlmRequest.Tool> tools = Collections.emptyList();
    String workspaceRootPath = null;
    if (skill != null && "agent".equals(skill.getType())) {
        tools = toolExecutor.buildToolsFromRelations(skill.getId());
        workspaceRootPath = resolveWorkspaceRoot(sessionId);
    }
    // ★ 第一轮请求含 tools，后续轮次工具执行完后传空列表
    LlmRequest llmReq = LlmRequest.of(config.getModelName(), systemPrompt,
        historyMaps, content, llmStreamEnabled, tools, "auto");

    SseEmitter emitter = new SseEmitter(300_000L);

    // ★ 中止标志位
    AtomicBoolean taskCancelled = new AtomicBoolean(false);
    emitter.onCompletion(() -> taskCancelled.set(true));
    emitter.onTimeout(() -> taskCancelled.set(true));
    emitter.onError(e -> taskCancelled.set(true));

    Thread.ofVirtual().start(() -> {
        try {
            int loopCount = 0;
            // ★ 当前轮的 LlmRequest，后续循环体内重建
            LlmRequest currentReq = llmReq;

            while (loopCount < MAX_AGENT_LOOPS && !taskCancelled.get()) {
                // 重置本轮累积器
                final Map<Integer, ToolCallAccumulator> toolCallAccumulators =
                    new ConcurrentHashMap<>();
                final Set<String> pushedToolNames = new HashSet<>();
                final StringBuilder fullResponse = new StringBuilder();
                final StringBuilder fullReasoning = new StringBuilder();

                // ========== 代理转发 LLM ==========
                // ... HttpClient 发送请求（body = JSON.serialize(currentReq)）、逐行解析 ...

                // ★ 每轮循环顶部检查中止信号
                if (taskCancelled.get()) {
                    emitter.send(SseEmitter.event().name("error")
                        .data(Map.of("message", "请求已被用户中止")));
                    break;
                }

                // 流结束后判断：本轮是否产生了 tool_calls？
                if (!toolCallAccumulators.isEmpty()) {
                    // ─── 工具调用分支 ───
                    // 1. 保存 assistant 消息（含 tool_calls JSON + call_id）
                    saveAssistantMessageWithToolCalls(sessionId, toolCallAccumulators);

                    // 2. 逐个执行工具（按 index 顺序）
                    for (var entry : toolCallAccumulators.entrySet()) {
                        ToolCallAccumulator acc = entry.getValue();
                        ToolCallResult result = toolExecutor.execute(
                            acc.toolName, acc.args.toString(),
                            workspaceRootPath);
                        emitter.send(SseEmitter.event()
                            .name("tool_status")
                            .data(Map.of("name", result.name,
                                "status", result.status,
                                "detail", result.detail)));

                        // 3. 保存 role="tool" 消息（含 call_id）
                        saveToolMessage(sessionId, acc.callId, result);
                    }

                    // 4. loopCount++ 并检查是否超限
                    loopCount++;
                    if (loopCount >= MAX_AGENT_LOOPS) {
                        emitter.send(SseEmitter.event().name("message")
                            .data("\n\n> ⚠️ 已达到最大工具调用轮次 (5)，强制结束"));
                        break;
                    }

                    // 5. 重新构建下一轮请求（tools 传空，上下文已含工具结果）
                    List<Map<String, String>> extendedHistory =
                        buildExtendedHistory(sessionId);
                    currentReq = LlmRequest.of(config.getModelName(),
                        systemPrompt, extendedHistory, null,
                        llmStreamEnabled, Collections.emptyList(), null);
                    // → 回到 while 循环顶部继续

                } else {
                    // ─── 普通文本分支（退出循环）───
                    saveAssistantMessage(sessionId, fullResponse.toString(),
                        fullReasoning.toString());
                    emitter.send(SseEmitter.event().name("done")
                        .data(Map.of("sessionId", sessionId,
                            "messageId", aiMsg.getId())));
                    break;  // ★ 退出 while 循环
                }
            }
        } catch (Exception e) {
            if (!taskCancelled.get()) {
                log.error("Agent 循环异常", e);
                emitter.send(SseEmitter.event().name("error")
                    .data(Map.of("message", e.getMessage())));
            }
        } finally {
            emitter.complete();
        }
    });

    return emitter;
}
```

> **与旧版的关键差异**：
> 1. 不再有 `streamSecondRound()` 硬编码单次调用，改为 `while` 循环
> 2. 每轮循环顶部重置 `toolCallAccumulators` / `pushedToolNames` / `fullResponse`
> 3. 通过 `AtomicBoolean taskCancelled` + `SseEmitter` 生命周期回调实现优雅中止
> 4. `loopCount` 递增由 `MAX_AGENT_LOOPS = 5` 守卫防止死循环
> 5. `ToolCallAccumulator` 携带 `callId`，保证 `role: "tool"` 消息可追溯

### 5.3 工具执行引擎（Tool Executor）— 安全沙箱

**新文件**: `java-apex-server/src/main/java/com/apex/service/ToolExecutor.java`

```java
@Slf4j
@Component
public class ToolExecutor {

    /** 文件读取上限：2MB */
    private static final long MAX_READ_SIZE = 2 * 1024 * 1024;

    // ===== 5.3.1 MVP 硬编码首批工具 JSON Schema =====

    /**
     * MVP 阶段：硬编码首批 3 个工具的 declaration_json。
     * 后续工具管理 CRUD 页面上线后，改为从 ai_tool 表动态读取。
     */
    private static final List<Map<String, Object>> HARDCODED_TOOLS = List.of(
        Map.of(
            "type", "function",
            "function", Map.of(
                "name", "read_file",
                "description", "读取工作空间内指定文件的内容",
                "parameters", Map.of(
                    "type", "object",
                    "properties", Map.of(
                        "filePath", Map.of(
                            "type", "string",
                            "description", "相对于工作空间根目录的文件路径"
                        )
                    ),
                    "required", List.of("filePath")
                )
            )
        ),
        Map.of(
            "type", "function",
            "function", Map.of(
                "name", "write_file",
                "description", "写入或覆盖工作空间内的指定文件，目录不存在时自动创建",
                "parameters", Map.of(
                    "type", "object",
                    "properties", Map.of(
                        "filePath", Map.of(
                            "type", "string",
                            "description", "相对于工作空间根目录的文件路径"
                        ),
                        "content", Map.of(
                            "type", "string",
                            "description", "要写入的文件内容"
                        )
                    ),
                    "required", List.of("filePath", "content")
                )
            )
        ),
        Map.of(
            "type", "function",
            "function", Map.of(
                "name", "list_dir",
                "description", "列出工作空间目录下的文件和子目录",
                "parameters", Map.of(
                    "type", "object",
                    "properties", Map.of(
                        "dirPath", Map.of(
                            "type", "string",
                            "description", "相对于工作空间根目录的目录路径，不传则列出根目录"
                        )
                    ),
                    "required", List.of()
                )
            )
        )
    );

    /**
     * 构建 tools 数组。
     * MVP 阶段：直接返回硬编码列表。
     * 后续阶段：从 ai_skill_tool_relation 关联的 ai_tool 表 declaration_json 动态构建。
     */
    public List<LlmRequest.Tool> buildToolsFromRelations(String skillId) {
        // TODO: MVP 后改为从 DB 动态读取
        // List<AiTool> tools = aiToolMapper.findBySkillId(skillId);
        // 从 declaration_json 反序列化为 LlmRequest.Tool
        log.info("MVP 阶段：使用硬编码工具列表，skillId={}", skillId);
        return HARDCODED_TOOLS.stream()
            .map(tool -> /* Jackson 反序列化 */ null) // 简化示例
            .collect(Collectors.toList());
    }

    // ===== 5.3.2 工具执行 ====

    public ToolCallResult execute(String toolName, String argumentsJson,
            String workspaceRoot) {
        return switch (toolName) {
            case "read_file"  -> executeReadFile(argumentsJson, workspaceRoot);
            case "write_file" -> executeWriteFile(argumentsJson, workspaceRoot);
            case "list_dir"   -> executeListDir(argumentsJson, workspaceRoot);
            default -> ToolCallResult.failed(toolName, "未知工具: " + toolName);
        };
    }

    // ===== 5.3.3 安全防护 =====

    /**
     * 路径安全校验：防御符号链接逃逸攻击。
     * ★ 使用 toRealPath() 而非 toAbsolutePath()：
     *   toRealPath() 会解析所有符号链接，攻击者即使创建
     *   workspace/../etc/passwd 的软链接也无法逃逸。
     */
    private Path validateAndResolve(String relativePath, Path workspaceRoot) {
        try {
            Path resolved = workspaceRoot.resolve(relativePath).toRealPath();
            Path rootReal = workspaceRoot.toRealPath();

            if (!resolved.startsWith(rootReal)) {
                throw new SecurityException(
                    "路径越界拒绝: " + relativePath + " → " + resolved);
            }
            return resolved;
        } catch (NoSuchFileException e) {
            // 写操作时文件可能还不存在，校验父目录即可
            Path parent = workspaceRoot.resolve(relativePath).getParent();
            Path parentReal = parent.toRealPath();
            Path rootReal = workspaceRoot.toRealPath();
            if (!parentReal.startsWith(rootReal)) {
                throw new SecurityException(
                    "路径越界拒绝: " + relativePath);
            }
            return workspaceRoot.resolve(relativePath);
        } catch (IOException e) {
            throw new RuntimeException("路径解析失败: " + relativePath, e);
        }
    }

    /** 读文件：含大文件防护 */
    private ToolCallResult executeReadFile(String argsJson, String workspaceRoot) {
        var args = parseArgs(argsJson);
        String filePath = (String) args.get("filePath");
        Path resolved = validateAndResolve(filePath, Path.of(workspaceRoot));

        if (!Files.exists(resolved)) {
            return ToolCallResult.failed("read_file", "文件不存在: " + filePath);
        }

        long fileSize = Files.size(resolved);
        if (fileSize > MAX_READ_SIZE) {
            // 大文件截断读取并标注
            try (var in = new FileInputStream(resolved.toFile())) {
                byte[] buf = new byte[(int) MAX_READ_SIZE];
                in.read(buf);
                String content = new String(buf, StandardCharsets.UTF_8);
                return ToolCallResult.success("read_file",
                    content + "\n\n[FILE_TRUNCATED: 文件大小 " +
                    fileSize + " bytes, 仅读取前 " + MAX_READ_SIZE + " bytes]");
            } catch (IOException e) {
                return ToolCallResult.failed("read_file", "读取失败: " + e.getMessage());
            }
        }

        try {
            String content = Files.readString(resolved);
            return ToolCallResult.success("read_file", content);
        } catch (IOException e) {
            return ToolCallResult.failed("read_file", "读取失败: " + e.getMessage());
        }
    }

    /** 写文件：自动创建目录 + 覆盖写入 */
    private ToolCallResult executeWriteFile(String argsJson, String workspaceRoot) {
        var args = parseArgs(argsJson);
        String filePath = (String) args.get("filePath");
        String content = (String) args.get("content");
        Path resolved = validateAndResolve(filePath, Path.of(workspaceRoot));

        // 写入前检查文件名不以 "." 开头（防止写入隐藏文件破坏系统）
        if (resolved.getFileName().toString().startsWith(".")) {
            return ToolCallResult.failed("write_file",
                "禁止写入隐藏文件: " + resolved.getFileName());
        }

        try {
            Files.createDirectories(resolved.getParent());
            Files.writeString(resolved, content,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING);
            return ToolCallResult.success("write_file",
                "文件写入成功: " + filePath + " (" + content.length() + " 字节)");
        } catch (IOException e) {
            return ToolCallResult.failed("write_file", "写入失败: " + e.getMessage());
        }
    }

    /** 列出目录 */
    private ToolCallResult executeListDir(String argsJson, String workspaceRoot) {
        var args = parseArgs(argsJson);
        String dirPath = (String) args.getOrDefault("dirPath", ".");
        Path resolved = validateAndResolve(dirPath, Path.of(workspaceRoot));

        if (!Files.isDirectory(resolved)) {
            return ToolCallResult.failed("list_dir", "不是目录: " + dirPath);
        }

        try (var stream = Files.list(resolved)) {
            String listing = stream
                .map(p -> (Files.isDirectory(p) ? "[DIR]  " : "[FILE] ")
                    + p.getFileName().toString())
                .collect(Collectors.joining("\n"));
            return ToolCallResult.success("list_dir",
                listing.isEmpty() ? "(空目录)" : listing);
        } catch (IOException e) {
            return ToolCallResult.failed("list_dir", "列出目录失败: " + e.getMessage());
        }
    }

    private Map<String, Object> parseArgs(String argsJson) {
        try {
            return objectMapper.readValue(argsJson, new TypeReference<>() {});
        } catch (Exception e) {
            throw new RuntimeException("工具参数 JSON 解析失败: " + argsJson, e);
        }
    }
}
```

### 5.4 新增 SSE 事件类型

| 事件名 | 方向 | 携带数据 | 说明 |
|---|---|---|---|
| `message` | 后端→前端 | 文本 chunk | **已有**，AI 正文流 |
| `reasoning` | 后端→前端 | 思考链 chunk | **已有**，DeepSeek R1 推理过程 |
| `done` | 后端→前端 | `{sessionId, messageId}` | **已有**，流结束 |
| `error` | 后端→前端 | 错误信息 | **已有**，异常 |
| **`tool_status`** | **后端→前端** | **`{name, status, detail?}`** | **新增**，工具执行步骤状态 |

---

## 六、前端改造详细设计

### 6.1 ChatView SSE 事件扩展

**改造文件**: [`ChatView.vue`](../web-apex-vue/src/views/ChatView.vue)

**[`sendChatMessage()`](web-apex-vue/src/api/chat.ts:61) 增加 `onToolStatus` 回调**：

```typescript
export function sendChatMessage(
  sessionId: string | null,
  configId: string,
  content: string,
  onMessage: (chunk: string) => void,
  onDone: (data: { sessionId: string; messageId: string }) => void,
  onError: (error: string) => void,
  onReasoning?: (chunk: string) => void,
  onToolStatus?: (data: { name: string; status: string; detail?: string }) => void, // 新增
  skillId?: string | null,
  workspaceId?: string | null,  // 新增
): AbortController {
  // ... 现有 fetch 逻辑不变 ...
  // dispatchEvent 函数中增加:
  // } else if (event === 'tool_status') {
  //   const payload = JSON.parse(data)
  //   onToolStatus?.(payload)
  // }
}
```

**ChatView 增加工具步骤展示区域**：

在流式输出区域中，当收到 `tool_status` 事件时，在消息气泡上方渲染一个步骤 Timeline：

```
┌─────────────────────────────────────────┐
│ 🤖 AI 回复中...                          │
│ ┌─ 工具执行步骤 ───────────────────────┐ │
│ │ ⚙️ read_file   ✅ 读取成功           │ │
│ │ ⚙️ write_file  ✅ 写入成功           │ │
│ └──────────────────────────────────────┘ │
│ 根据读取的文件内容，我生成了以下代码...    │
└─────────────────────────────────────────┘
```

### 6.2 工作空间选择与文件浏览器

**新增组件**: `web-apex-vue/src/components/chat/WorkspaceFileTree.vue`

- 使用 `el-tree` 渲染工作空间目录结构
- 支持输入框 `@` 时联动目录树进行文件联想（Mentions）
- 选中文件后注入文件路径到输入框

### 6.3 SkillManager 增强

**改造文件**: [`SkillManager.vue`](../web-apex-vue/src/views/SkillManager.vue)

- `agent` 类型选项解除 disabled 状态
- 当 `type === 'agent'` 时，显示工具绑定多选区域（从 `ai_tool` 表加载）
- System Prompt 编辑器保留（agent 模式下仍需要指导大模型如何使用工具）

### 6.4 新增页面

| 页面 | 路由 | 功能 |
|---|---|---|
| 工具管理 | `/tools` | 展示/编辑 `ai_tool` 表中的工具定义 |
| 工作空间管理 | `/workspaces` | 工作空间的 CRUD |

---

## 七、MVP 最小可行开发顺序

基于现有基础设施，推荐以下四步 MVP 方案：

### Step 0：硬编码工具 JSON Schema + DTO 升级（预估 0.3 天）

- [ ] 在 `ToolExecutor` 中硬编码首批 3 个工具的 `declaration_json`（read_file / write_file / list_dir）
- [ ] `LlmRequest` 扩展：增加 `Tool`、`ToolCall`、`ToolCallAccumulator` 等 Record/类
- [ ] 响应 Delta 解析增加 `tool_calls` 分支（先打印日志验证能收到）
- [ ] `ToolCallAccumulator` 支持 `callId` 捕获

> **为什么需要 Step 0**：`ChatService` 的 while 循环依赖 `tools` 数组进入 agent 分支。若工具管理 CRUD 界面未上线就无法往 `ai_tool` 表插入数据，导致测试无法进行。硬编码 JSON Schema 作为临时桥梁，后续 Step 3 迁移为动态读取。

**验证方式**：用支持 Function Calling 的模型（如 `deepseek-chat`）发送带硬编码 tools 的请求，后端日志打印出 `tool_calls` JSON 完整结构（含 `id`、`function.name`、`function.arguments`）

### Step 1：数据库变更 + 工具执行引擎（预估 0.5 天）

- [ ] 新增 Flyway 脚本：ALTER `llm_config`、`chat_message`、`chat_session`，CREATE `agent_workspace`
- [ ] 实现 `ToolExecutor` 完整安全沙箱（`toRealPath` 防 symlink、大文件防护、隐藏文件限制）
- [ ] `AiSkill` Mapper 增加按 skillId 查询关联工具的 SQL
- [ ] `ChatService` 增加中止机制：`AtomicBoolean taskCancelled` + `SseEmitter` 生命周期回调

**验证方式**：单元测试覆盖路径越界、符号链接逃逸、大文件截断场景

### Step 2：while 循环多轮 Agent + 前端工具展示（预估 1 天）

- [ ] `ChatService.sendMessage()` 状态机改造：while 循环 + `MAX_AGENT_LOOPS = 5` 守卫
- [ ] 新增 SSE `tool_status` 事件推送
- [ ] 前端 `sendChatMessage()` 增加 `onToolStatus` 回调
- [ ] ChatView 增加工具步骤 Timeline 展示

**验证方式**：创建一个 agent 类型 Skill（system prompt: "你是一个文件管理助手，可以使用工具读写文件"），在 ChatView 中让 AI 写一个文件，观察步骤 Timeline 和多轮工具调用的完整过程

### Step 3：工作空间 + 前端完善 + 工具动态化（预估 1 天）

- [ ] 工作空间 CRUD API + 前端管理页
- [ ] ChatView 增加工作空间选择器
- [ ] 前端文件浏览器组件 + `@` 文件引用
- [ ] SkillManager agent 类型 + 工具绑定 UI
- [ ] 工具管理 CRUD 页面上线
- [ ] 将 `ToolExecutor.buildToolsFromRelations()` 从硬编码切换为从 `ai_tool` 表动态读取

**验证方式**：给 AI 指定工作空间，让 AI 读取一个现有文件 → 修改内容 → 写回，全程观察步骤 Timeline 和最终文件结果

---

## 八、相关文件索引

### 本次改造涉及的文件

| 文件 | 变更类型 | 说明 |
|---|---|---|
| `java-apex-server/src/main/java/com/apex/model/LlmRequest.java` | 扩展 | 增加 Tool/ToolCall/Function/ToolCallAccumulator Record |
| `java-apex-server/src/main/java/com/apex/service/ChatService.java` | 核心改造 | while 循环状态机 + 工具调用 + 中止机制 |
| `java-apex-server/src/main/java/com/apex/service/ToolExecutor.java` | 新增 | 工具执行引擎（含 MVP 硬编码 JSON Schema + 安全沙箱） |
| `java-apex-server/src/main/java/com/apex/service/AgentWorkspaceService.java` | 新增 | 工作空间管理 Service |
| `java-apex-server/src/main/java/com/apex/controller/AgentWorkspaceController.java` | 新增 | 工作空间 API |
| `java-apex-server/src/main/java/com/apex/controller/AiToolController.java` | 新增 | 工具定义管理 API |
| `java-apex-server/src/main/java/com/apex/entity/AgentWorkspace.java` | 新增 | 工作空间 Entity |
| `java-apex-server/src/main/java/com/apex/mapper/AgentWorkspaceMapper.java` | 新增 | 工作空间 Mapper |
| Flyway `V9__agent_upgrade.sql` | 新增 | 本次全部 DDL 变更 |
| `web-apex-vue/src/api/chat.ts` | 扩展 | `sendChatMessage` 增加 `onToolStatus`/`workspaceId` |
| `web-apex-vue/src/views/ChatView.vue` | 扩展 | 工具步骤 Timeline + 工作空间选择器 |
| `web-apex-vue/src/views/SkillManager.vue` | 扩展 | 启用 agent 类型 + 工具绑定 UI |
| `web-apex-vue/src/components/chat/WorkspaceFileTree.vue` | 新增 | 文件浏览器组件 |
| `web-apex-vue/src/types/chat.ts` | 扩展 | 增加 Agent 相关类型定义 |

### 已有关键文件（可复用）

| 文件 | 角色 |
|---|---|
| [`ChatService.java`](java-apex-server/src/main/java/com/apex/service/ChatService.java) | 核心改造目标 |
| [`LlmRequest.java`](java-apex-server/src/main/java/com/apex/model/LlmRequest.java) | DTO 扩展目标 |
| [`ai_skill` 表](java-apex-server/src/main/resources/db/migration/V4__create_skill_tables.sql:8) | type 字段已预留 agent/workflow |
| [`ai_tool` 表](java-apex-server/src/main/resources/db/migration/V4__create_skill_tables.sql:25) | declaration_json 字段已符合 OpenAI 标准 |
| [`ai_skill_tool_relation` 表](java-apex-server/src/main/resources/db/migration/V4__create_skill_tables.sql:37) | 多对多绑定关系已建好 |

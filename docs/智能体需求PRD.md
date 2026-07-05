# 智能体（Agent）功能需求 PRD — 基于现状改造升级

> **版本**: v4.5（独立页面 + 统一入口分流 + BuiltInToolRegistry 6 大内置工具 + 工作空间优先 + 标准双栏状态机布局 + 下阶段里程碑）
> **日期**: 2026-07-05
> **技术路线**: **纯 JDK HttpClient + while 循环状态机**，不引入任何 AI 框架
> **前置阅读**: [`AI_LLM_STATUS_ANALYSIS.md`](AI_LLM_STATUS_ANALYSIS.md)

---

## 一、背景与现状

### 1.1 系统当前已具备的基础设施

| 已具备能力 | 对应组件 | 可复用性 |
|---|---|---|
| LLM 多模型动态配置 | `llm_config` 表 + `LlmService` | ✅ 直接复用 |
| SSE 流式代理转发 | `ChatService.sendMessage()` + JDK `HttpClient` | ✅ 核心链路直接扩展 |
| 多租户会话管理 | `chat_session` + `chat_message` 表 | ✅ 直接复用 |
| Skill 预设体系 | `ai_skill` 表（`type` 字段已预留 `agent`/`workflow`） | ✅ 直接启用 |
| 工具定义表 | `ai_tool` + `ai_skill_tool_relation`（已建表，未启用） | ✅ 直接启用 |
| 虚拟线程异步 | `Thread.ofVirtual().start()` | ✅ 直接复用 |
| Markdown 实时渲染 | `marked` + 思考链折叠面板 | ✅ 直接复用 |

### 1.2 核心设计：独立页面 + 共用底层

**顶层策略**：

```
前端（视图层拆分）：
  /chat  → ChatView.vue（原页面，完全不动）
  /agent → AgentView.vue（全新页面）

后端（数据层/控制层共用升级）：
  /api/chat/send   → ChatController.send()（统一入口）
                      内部读 session_mode 分流：
                        'CHAT'  → classicChatService（原逻辑）
                        'AGENT' → agentChatService（新逻辑）
                      共用：HttpClient、鉴权、日志、异常处理

  /api/workspace/*  → WorkspaceController（新增，Agent 专属）

数据库（共用表，向上兼容）：
  chat_session  → + session_mode, + workspace_id
  chat_message  → + tool_calls_json, + tool_call_id, + tool_name, + tool_status
                  role 字段扩展支持 'tool'
  agent_workspace → 新表
```

| 维度 | 💬 对话模式（原 ChatView） | 🤖 Agent 模式（新 AgentView） |
|---|---|---|
| **前端页面** | `/chat`，ChatView.vue，**零改动** | `/agent`，AgentView.vue，全新 |
| **会话列表** | 仅查 `session_mode = 'CHAT'` | 仅查 `session_mode = 'AGENT'` |
| **LLM 请求** | 不含 `tools` 数组 | 含 `tools` 数组 + `tool_choice: "auto"`（后端隐式注入，见 3.3） |
| **工作空间** | 无 | **必须先选**，选择后自动展示文件树，否则输入框 + 创建/进入会话均禁用 |
| **内置工具** | 无 | 6 大内置工具（`list_dir` / `locate_files` / `read_file` / `write_file` / `apply_diff` / `execute_command`）由后端隐式注入，大模型自主按需调用，用户无需勾选 |
| **文件操作** | 无 | 大模型自主决定何时调用内置工具，支持精准补丁修改（`apply_diff`）和命令执行自愈闭环 |
| **界面布局** | 单栏对话区 | 标准双栏：左侧常驻文件树 + 右侧动态交互区（会话列表/聊天区二合一状态机切换，见 6.2） |
| **SSE 事件格式** | 纯文本字符串（现有，不动） | JSON 结构事件（升级格式，见 5.5） |
| **存储标识** | `chat_session.session_mode = 'CHAT'` | `chat_session.session_mode = 'AGENT'` |
| **Skill 作用** | 注入 system prompt | 注入 system prompt + 决定绑定外部高级工具（非内置工具） |

### 1.3 共用升级的兼容性保证

| 层面 | 兼容策略 |
|---|---|
| **老数据** | Flyway ALTER 设默认值：`session_mode` 默认 `'CHAT'`，`tool_calls_json` 默认 NULL |
| **老页面** | ChatView 查会话列表时加 `WHERE session_mode = 'CHAT'`，看不到 Agent 会话 |
| **新页面** | AgentView 查会话列表时加 `WHERE session_mode = 'AGENT'`，看不到纯对话 |
| **接口** | 同一个 `/api/chat/send`，老前端请求体中不传 `workspaceId`，后端自动走 CHAT 分支 |
| **未来互通** | 一条 SQL 即可将 CHAT 会话升级为 AGENT：`UPDATE chat_session SET session_mode = 'AGENT', workspace_id = ? WHERE id = ?` |

---

## 二、技术路线决策

### 2.1 不引入 AI 框架

在现有 `ChatService.sendMessage()` 的 JDK `HttpClient` 代理链路上直接扩展。所有 Skill（prompt/agent）走同一套 `ChatService`，仅根据 `session_mode` 分支。

### 2.2 改造核心思路

```
Step 0（MVP 前置）: 硬编码内置工具（BuiltInToolRegistry）
  后端 BuiltInToolRegistry 类固化 6 大内置工具的 OpenAI Schema + 执行逻辑 + 安全沙箱
  内置工具在代码层虚拟化，不存入 ai_tool 表，随 Java 源码打包迭代，零配置
  工具管理 CRUD 页面仅用于管理"外部插件"级别的非内置工具（如联网搜索、知识库 RAG）

Step 1: 数据库 Flyway + DTO 升级
  chat_session: + session_mode (默认 'CHAT'), + workspace_id
  chat_message: + tool_calls_json, + tool_call_id, + tool_name, + tool_status
  agent_workspace: 新表
  LlmRequest: + tools/tool_choice

Step 2: 后端流式接口改造（共用升级 + 分流）
  ChatService.sendMessage() 根据 session_mode 分支
  Agent 分支: while 循环（max_loops=5）+ 工具调用 + 中止机制
  内置工具由 BuiltInToolRegistry 产出 Schema + 执行分发，由 agentChatService 注入请求
  SSE 数据格式升级为 JSON 结构事件（仅 Agent 分支）

Step 3: 前端 AgentView 独立页面 + 工作空间管理
  /agent 路由 → AgentView.vue（标准双栏：左文件树 + 右动态交互区二合一状态机）
  **强制工作空间优先流程**：先选/创建工作空间 → 自动展示文件树 → 右侧默认 LIST 状态 → 创建/进入会话后切 CHAT 状态
  /workspaces 路由 → 工作空间 CRUD
  /tools 路由 → 工具管理（仅外部插件）
  /chat 路由 → ChatView.vue（完全不动）
```

---

## 三、新增/改造功能模块

```
前端（视图拆分）：
  /chat → ChatView.vue ──────── 原封不动
  /agent → AgentView.vue ─────── 全新页面
  /workspaces → 工作空间管理 ─── 全新页面
  /tools → 工具管理 ──────────── 全新页面

后端（共用升级）：
  ChatController.send() ── 统一入口 → ChatService.sendMessage()
      ├── session_mode = 'CHAT'  → 原逻辑（零改动）
      └── session_mode = 'AGENT' → while 循环 + BuiltInToolRegistry + 中止机制
  WorkspaceController ── 新增（空间 CRUD + 文件树）
  AiToolController ────── 新增（工具 CRUD）

数据库（Flyway 增量迁移）：
  chat_session → ALTER +session_mode +workspace_id
  chat_message → ALTER +tool_calls_json +tool_call_id +tool_name +tool_status
  agent_workspace → CREATE 新表
```

### 3.1 工作空间设计

```
application.yml:
  agent:
    workspace:
      root-dir: D:\agent-workspaces   ← 全局根目录

agent_workspace 表:
  id    name         dir_name      description
  ────  ───────────  ────────────  ─────────────
  001   CRM项目       crm-project  客户管理系统
  002   监控系统       monitor      运维监控

磁盘路径（运行时拼接）:
  D:\agent-workspaces\crm-project\
  D:\agent-workspaces\monitor\
```

- 根目录配置在 `application.yml`，不在 DB 中
- 表仅存 `dir_name` 子目录名，创建时自动在根目录下 `mkdir`
- 所有工具操作的路径基准：`rootDir + dirName`，通过 `toRealPath()` 防越界

### 3.2 工具执行引擎

#### 3.2.1 工具分层：内置工具 vs 外部插件

| 分类 | 工具 | 注入方式 | 用户是否可见 |
|---|---|---|---|
| **内置工具**（Built-in） | `list_dir` / `locate_files` / `read_file` / `write_file` / `apply_diff` / `execute_command`（共 6 个） | 后端 `BuiltInToolRegistry` 类硬编码产出 Schema + 执行逻辑，Agent 模式激活时自动注入 `LlmRequest.tools`，**不存数据库** | ❌ 用户无需勾选，无需感知，前端不展示 |
| **外部插件**（Plugin） | 联网搜索、知识库 RAG 等 | 通过 `ai_tool` 表动态管理，用户在 Agent 页面通过勾选框选择启用 | ✅ 用户在聊天框下方可见开关/勾选框 |

> **核心原则**：内置工具是系统级功能，**随 Java 源码一起打包迭代，零配置**，不存入 `ai_tool` 表。外部插件才走数据库配置。详见 3.3。

#### 3.2.2 六大内置工具规格

工具按"感知 → 决策 → 执行"闭环分为 4 类：

**📂 空间感知类**（Discovery — "AI 的眼睛"）

| 工具 | 参数 | 作用 |
|---|---|---|
| `list_dir` | `relativePath: String`（可选，默认根目录） | 列出目录下的文件和子目录结构 |
| `locate_files` | `query: String`（关键词/正则） | 在工作空间中全局搜索文件或文本，瞬间定位目标 |

**📝 文件读写类**（File I/O — "AI 的双手"）

| 工具 | 参数 | 作用 |
|---|---|---|
| `read_file` | `relativePath: String` | 读取文件完整内容（限 2MB） |
| `write_file` | `relativePath: String`, `content: String` | 新建或全量覆盖文件 |

**🛠️ 精准修改类**（Code Patch — "高级外科手术"）

| 工具 | 参数 | 作用 |
|---|---|---|
| `apply_diff` | `relativePath: String`, `searchContent: String`, `replaceContent: String` | 在文件中精准替换局部代码片段，避免全量重写大文件造成的 Token 浪费和延迟 |

**💻 命令执行类**（Terminal — "AI 的大脑反射"）

| 工具 | 参数 | 作用 |
|---|---|---|
| `execute_command` | `command: String` | 在工作空间路径下执行安全系统命令，返回 stdout/stderr，是 Agent 自我纠错（Self-Correction）的关键 |

**安全约束（所有工具共用）**：

| 约束 | 说明 |
|---|---|
| 路径防越界 | `toAbsolutePath().normalize()` → `startsWith(workspaceRoot)` 校验 |
| 文件大小上限 | `read_file` 限 2MB，超限拒绝 |
| 隐藏文件 | `write_file` 和 `apply_diff` 禁止操作 `.` 开头的隐藏文件 |
| 命令黑名单 | `execute_command` 拦截 `rm -rf /`、`wget`、`curl` 外部脚本、关机、改密码等高危指令 |

### 3.3 内置工具的"无感注入"与大模型自主决策

在 Agent 模式下，**绝对不需要用户手动指定调用哪些工具**。所有 6 大基础工具（空间感知、文件读写、精准修改、命令执行）都作为系统的内置工具（Built-in Tools），由后端在发起大模型请求时隐式注入，并由大模型自主进行按需调用——形成完整的"感知 → 决策 → 执行 → 自愈"闭环。如果让用户去勾选"本次对话允许大模型读文件"，不仅操作反人类，也失去了智能体自主规划、自主执行的核心意义。

#### 3.3.1 后端隐式注入：内置工具 + Skill 表动态工具合并

当用户在 AgentView 的聊天框中输入消息并发送时，后端的 `agentChatService` 通过统一的工具组装中心，将**硬编码的内置工具**与**数据库 `ai_tool` 表里的动态工具**合并为一个数组输出给大模型：

```java
// AgentChatService.assembleAllTools()
public List<Map<String, Object>> assembleAllTools(String skillId) {
    List<Map<String, Object>> allTools = new ArrayList<>();

    // 1. 强行注入 6 大内置工具（list_dir/locate_files/read_file/write_file/apply_diff/execute_command，硬编码，不查数据库）
    allTools.addAll(builtInToolRegistry.getBuiltInToolsSchema());

    // 2. 从 ai_tool 表查询该 Skill 关联的外部插件工具，转换为 LLM Schema 追加
    List<AiTool> externalTools = aiToolMapper.selectEnabledBySkillId(skillId);
    for (AiTool tool : externalTools) {
        allTools.add(convertToLlmSchema(tool));
    }

    return allTools;
}

// 发起大模型请求时
LlmRequest request = new LlmRequest();
request.setMessages(extendedHistory);
request.setTools(assembleAllTools(skillId));  // 内置 + 外部合并
request.setToolChoice("auto");
httpClient.sendStream(request, ...);
```

> **核心思想**：6 大内置工具不需要作为数据插入 `ai_tool` 表，而是通过 `BuiltInToolRegistry` 在代码层虚拟化，随 Java 源码一起打包迭代，零配置、零数据库依赖。

#### 3.3.2 大模型的自主决策流程（示例）

以用户输入 **"帮我把当前目录下的 index.html 里的标题改成'我的商城'"** 为例：

```
第 1 步：大模型理解意图后，自主决定"先读文件"
         → 不返回文本，输出 tool_call: read_file(relativePath: "index.html")

第 2 步：后端拦截 tool_call，在服务器上读取 index.html 的内容
         → 把文件内容作为 role: "tool" 的消息喂回给大模型

第 3 步：大模型拿到文件内容，进行代码修改，自主决定"写回文件"
         → 输出 tool_call: write_file(relativePath: "index.html", content: "...")

第 4 步：后端执行写入，告知大模型写入成功
         → 大模型向前端输出文本："我已经成功帮您修改了 index.html 的标题！"
```

在整个过程中，用户只看到了输入指令和最终结果，中间大模型像个经验丰富的程序员一样，**自己决定什么时候该读、什么时候该写**。前端通过 SSE 的 `tool_start` / `tool_end` 事件展示中间步骤卡片，做到透明可控。

#### 3.3.3 BuiltInToolRegistry：代码层虚拟化（Schema + 执行 + 沙箱）

**为什么不把内置工具存入 `ai_tool` 表？**

| 维度 | 方案 A：内置工具存 DB | 方案 B：代码层虚拟化（推荐） |
|---|---|---|
| **可维护性** | 差。换环境需刷 SQL 预置数据；改方法名要同步数据库 | 强。内置工具随 Java 源码一起打包迭代，零配置 |
| **安全性** | 低。若前端误删或修改了 `write_file` 记录，系统直接瘫痪 | 极高。硬编码在 Registry，受 Java 权限和沙箱严格保护 |
| **用户体验** | 乱。`ai_tool` 列表混入不能删/改的系统基础工具 | 清爽。`ai_tool` 表只展现高级插件，基础工具纯隐式 |

`BuiltInToolRegistry` 是一个 Spring `@Component`，不查数据库，内部使用 Enum 组织 6 大内置工具，直接提供 OpenAI 兼容 JSON Schema，并绑定具体的本地 Java 执行逻辑（沙箱保护）：

```java
@Component
public class BuiltInToolRegistry {

    @Value("${agent.workspace.root-dir}")
    private String workspaceRoot;

    @Autowired
    private AgentWorkspaceService workspaceService;

    // ========== 0. 内置工具枚举 ==========
    public enum Tool {
        LIST_DIR("list_dir", "列出工作空间内指定目录下的文件和子目录"),
        LOCATE_FILES("locate_files", "在工作空间中全局搜索文件或文本，支持关键词/正则"),
        READ_FILE("read_file", "读取当前工作空间内指定文件的文本内容"),
        WRITE_FILE("write_file", "在当前工作空间内新建文件或覆盖已有文件"),
        APPLY_DIFF("apply_diff", "精准替换文件中的局部代码片段，避免全量重写大文件"),
        EXECUTE_COMMAND("execute_command", "在工作空间路径下执行安全的系统命令，返回 stdout/stderr");

        private final String name;
        private final String description;
        Tool(String name, String description) { this.name = name; this.description = description; }
        public String getName() { return name; }
        public String getDescription() { return description; }
    }

    // ========== 1. 6 大内置工具 OpenAI Schema 定义 ==========
    public List<Map<String, Object>> getBuiltInToolsSchema() {
        return List.of(
            // list_dir
            Map.of("type", "function", "function", Map.of(
                "name", Tool.LIST_DIR.getName(),
                "description", Tool.LIST_DIR.getDescription(),
                "parameters", Map.of("type", "object",
                    "properties", Map.of("relativePath", Map.of("type", "string",
                        "description", "目录路径（相对），不传则默认根目录")),
                    "required", List.of())
            )),
            // locate_files
            Map.of("type", "function", "function", Map.of(
                "name", Tool.LOCATE_FILES.getName(),
                "description", Tool.LOCATE_FILES.getDescription(),
                "parameters", Map.of("type", "object",
                    "properties", Map.of("query", Map.of("type", "string",
                        "description", "搜索关键词或正则表达式，如 'max-retry' 或 'login'")),
                    "required", List.of("query"))
            )),
            // read_file
            Map.of("type", "function", "function", Map.of(
                "name", Tool.READ_FILE.getName(),
                "description", Tool.READ_FILE.getDescription(),
                "parameters", Map.of("type", "object",
                    "properties", Map.of("relativePath", Map.of("type", "string",
                        "description", "相对于工作空间根目录的相对路径，如 src/main.js")),
                    "required", List.of("relativePath"))
            )),
            // write_file
            Map.of("type", "function", "function", Map.of(
                "name", Tool.WRITE_FILE.getName(),
                "description", Tool.WRITE_FILE.getDescription(),
                "parameters", Map.of("type", "object",
                    "properties", Map.of(
                        "relativePath", Map.of("type", "string", "description", "目标相对路径"),
                        "content", Map.of("type", "string", "description", "要写入的完整文件内容")),
                    "required", List.of("relativePath", "content"))
            )),
            // apply_diff
            Map.of("type", "function", "function", Map.of(
                "name", Tool.APPLY_DIFF.getName(),
                "description", Tool.APPLY_DIFF.getDescription(),
                "parameters", Map.of("type", "object",
                    "properties", Map.of(
                        "relativePath", Map.of("type", "string", "description", "目标文件相对路径"),
                        "searchContent", Map.of("type", "string", "description", "要替换的原代码片段（精确匹配）"),
                        "replaceContent", Map.of("type", "string", "description", "替换后的新代码片段")),
                    "required", List.of("relativePath", "searchContent", "replaceContent"))
            )),
            // execute_command
            Map.of("type", "function", "function", Map.of(
                "name", Tool.EXECUTE_COMMAND.getName(),
                "description", Tool.EXECUTE_COMMAND.getDescription(),
                "parameters", Map.of("type", "object",
                    "properties", Map.of("command", Map.of("type", "string",
                        "description", "要执行的 Shell 命令，如 'npm run build' 或 'mvn clean test'")),
                    "required", List.of("command"))
            ))
        );
    }

    // ========== 2. 命令黑名单（高危指令拦截） ==========
    private static final List<Pattern> COMMAND_BLACKLIST = List.of(
        Pattern.compile("rm\\s+-rf\\s+/"),           // 强制删除根目录
        Pattern.compile("wget\\s+"),                  // 下载外部脚本
        Pattern.compile("curl\\s+.*\\|\\s*(ba)?sh"),  // curl 管道执行
        Pattern.compile("shutdown|reboot|halt"),      // 关机/重启
        Pattern.compile("passwd|chpasswd"),           // 修改密码
        Pattern.compile(">\\s*/dev/")                 // 覆盖系统设备
    );

    // ========== 3. 统一的内置工具执行分发器（含安全沙箱） ==========
    public String executeBuiltInTool(String toolName, String argumentsJson, String workspaceId)
            throws Exception {
        // 通过 workspaceId 获取该空间的物理沙箱主目录
        String dirName = workspaceService.getDirNameById(workspaceId);
        Path wsRoot = Paths.get(workspaceRoot, dirName).toAbsolutePath().normalize();

        ObjectMapper mapper = new ObjectMapper();
        JsonNode args = mapper.readTree(argumentsJson);

        switch (toolName) {
            // ===== 📂 空间感知 =====
            case "list_dir" -> {
                String relPath = args.has("relativePath") ? args.get("relativePath").asText() : "";
                Path targetPath = wsRoot.resolve(relPath).toAbsolutePath().normalize();
                if (!targetPath.startsWith(wsRoot))
                    throw new SecurityException("路径越界，无权访问！");
                StringBuilder sb = new StringBuilder();
                try (var stream = Files.list(targetPath)) {
                    stream.sorted().forEach(p -> {
                        String prefix = Files.isDirectory(p) ? "[DIR]  " : "[FILE] ";
                        sb.append(prefix).append(wsRoot.relativize(p)).append("\n");
                    });
                }
                return sb.isEmpty() ? "（空目录）" : sb.toString();
            }
            case "locate_files" -> {
                String query = args.get("query").asText();
                StringBuilder sb = new StringBuilder();
                Pattern pattern;
                try {
                    pattern = Pattern.compile(query, Pattern.CASE_INSENSITIVE);
                } catch (PatternSyntaxException e) {
                    // 如果不是合法正则，当作字面量搜索
                    pattern = Pattern.compile(Pattern.quote(query), Pattern.CASE_INSENSITIVE);
                }
                Files.walk(wsRoot)
                    .filter(Files::isRegularFile)
                    .filter(p -> !p.getFileName().toString().startsWith("."))
                    .forEach(p -> {
                        try {
                            String content = Files.readString(p);
                            var matcher = pattern.matcher(content);
                            while (matcher.find()) {
                                int lineNum = content.substring(0, matcher.start()).split("\n").length;
                                sb.append(wsRoot.relativize(p)).append(":").append(lineNum)
                                    .append(" → ").append(matcher.group()).append("\n");
                            }
                        } catch (IOException ignored) {}
                    });
                return sb.isEmpty() ? "未找到匹配结果" : sb.toString();
            }
            // ===== 📝 文件读写 =====
            case "read_file" -> {
                String relPath = args.get("relativePath").asText();
                Path targetPath = wsRoot.resolve(relPath).toAbsolutePath().normalize();
                if (!targetPath.startsWith(wsRoot))
                    throw new SecurityException("路径越界，无权访问！");
                if (Files.size(targetPath) > 2 * 1024 * 1024)
                    throw new IllegalArgumentException("文件超过 2MB 读取上限");
                return Files.readString(targetPath);
            }
            case "write_file" -> {
                String relPath = args.get("relativePath").asText();
                String content = args.get("content").asText();
                Path targetPath = wsRoot.resolve(relPath).toAbsolutePath().normalize();
                if (!targetPath.startsWith(wsRoot))
                    throw new SecurityException("路径越界，无权访问！");
                if (relPath.startsWith("."))
                    throw new SecurityException("禁止操作隐藏文件");
                Files.createDirectories(targetPath.getParent());
                Files.writeString(targetPath, content);
                return "文件写入成功！路径: " + relPath;
            }
            // ===== 🛠️ 精准修改 =====
            case "apply_diff" -> {
                String relPath = args.get("relativePath").asText();
                String searchContent = args.get("searchContent").asText();
                String replaceContent = args.get("replaceContent").asText();
                Path targetPath = wsRoot.resolve(relPath).toAbsolutePath().normalize();
                if (!targetPath.startsWith(wsRoot))
                    throw new SecurityException("路径越界，无权访问！");
                if (relPath.startsWith("."))
                    throw new SecurityException("禁止操作隐藏文件");
                String fileContent = Files.readString(targetPath);
                if (!fileContent.contains(searchContent))
                    throw new IllegalArgumentException("未找到匹配的代码片段，请确认 searchContent 是否正确");
                String newContent = fileContent.replace(searchContent, replaceContent);
                if (newContent.equals(fileContent))
                    throw new IllegalArgumentException("替换后内容未变化，可能 searchContent 存在歧义匹配");
                Files.writeString(targetPath, newContent);
                return "精准修改成功！路径: " + relPath;
            }
            // ===== 💻 命令执行 =====
            case "execute_command" -> {
                String command = args.get("command").asText();
                // 黑名单检查
                for (Pattern p : COMMAND_BLACKLIST) {
                    if (p.matcher(command).find())
                        throw new SecurityException("命令被安全策略拦截: " + command);
                }
                ProcessBuilder pb = new ProcessBuilder();
                // Windows 用 cmd /c，Linux/Mac 用 sh -c
                if (System.getProperty("os.name").toLowerCase().contains("win")) {
                    pb.command("cmd", "/c", command);
                } else {
                    pb.command("sh", "-c", command);
                }
                pb.directory(wsRoot.toFile());
                pb.redirectErrorStream(true);
                Process process = pb.start();
                String output = new String(process.getInputStream().readAllBytes());
                process.waitFor(30, TimeUnit.SECONDS);  // 30 秒超时
                if (process.isAlive()) {
                    process.destroyForcibly();
                    return output + "\n[系统] 命令执行超时（30s），已强制终止";
                }
                return output.isEmpty() ? "命令执行成功（无输出）" : output;
            }
            default -> throw new IllegalArgumentException("未知的内置工具: " + toolName);
        }
    }
}
```

> **设计要点**：`apply_diff` 是避免 Token 浪费的关键——大模型只需输出变动的代码片段而非整个文件，后端通过 `String.replace()` 精准打补丁。`execute_command` 是实现 Agent 自我纠错（Self-Correction）的核心——大模型改完代码后自己跑编译/测试，根据报错自主修复。

#### 3.3.4 大模型返回 tool_calls 时的执行分流

当大模型在 SSE 流中返回 `tool_calls` 要求调用工具时，后端先判断是否为内置工具，走不同的执行路径：

```java
String toolName = toolCall.getName();
String result;

Set<String> builtInToolNames = Set.of(
    "list_dir", "locate_files", "read_file", "write_file", "apply_diff", "execute_command"
);

if (builtInToolNames.contains(toolName)) {
    // 内置工具：走 BuiltInToolRegistry 本地沙箱执行
    result = builtInToolRegistry.executeBuiltInTool(toolName, toolCall.getArguments(), workspaceId);

    // 【核心联动】涉及文件变更的内置工具 → 推送 file_changed SSE 事件
    if (List.of("write_file", "apply_diff").contains(toolName)) {
        String relPath = new ObjectMapper().readTree(toolCall.getArguments()).get("relativePath").asText();
        emitter.send(SseEmitter.event().name("agent").data(
            Map.of("type", "file_changed", "path", relPath)
        ));
    }
} else {
    // 外部插件：走 ai_tool 表里定义的 HTTP 调用或脚本执行逻辑
    result = skillExecutor.execute(toolName, toolCall.getArguments());
}
```

#### 3.3.5 用户唯一能干预的工具场景

只有当系统扩展了以下**非基础性、带有明确边界和成本开销**的外部能力时，前端才需要提供勾选框：

| 外部插件（用户可控） | 需要干预的原因 |
|---|---|
| 🌐 联网搜索 | 调用 Google/SerpAPI，消耗额外 Token 或 API 密钥 |
| 📂 知识库 RAG | 用户可选挂载或不挂载某个 500MB 的 PDF 资料库 |
| 📨 邮件发送 | 涉及外部 SMTP 服务，有发送频率/配额限制 |

这些外部插件通过 `ai_tool` 表管理，在聊天框下方以勾选框/开关的形式呈现给用户。

---

## 四、数据库变更设计（Flyway 增量迁移）

### 4.1 `application.yml` 新增

```yaml
agent:
  workspace:
    root-dir: D:\agent-workspaces
```

### 4.2 升级 `chat_session`

```sql
ALTER TABLE `chat_session`
    ADD COLUMN `session_mode` VARCHAR(16) NOT NULL DEFAULT 'CHAT'
        COMMENT '会话模式：CHAT（纯对话）/ AGENT（智能体）',
    ADD COLUMN `workspace_id` VARCHAR(32) DEFAULT NULL
        COMMENT '关联的工作空间 ID（AGENT 模式时必选）';
```

> 老数据默认 `'CHAT'`。ChatView 查询时 `WHERE session_mode = 'CHAT'`；AgentView 查询时 `WHERE session_mode = 'AGENT'`。

### 4.3 升级 `chat_message`

```sql
ALTER TABLE `chat_message`
    ADD COLUMN `tool_name` VARCHAR(64) DEFAULT NULL
        COMMENT '工具名称',
    ADD COLUMN `tool_call_id` VARCHAR(64) DEFAULT NULL
        COMMENT '工具调用唯一ID（大模型返回的 call_id）',
    ADD COLUMN `tool_status` VARCHAR(20) DEFAULT NULL
        COMMENT '工具执行状态：running/success/failed',
    ADD COLUMN `tool_calls_json` TEXT DEFAULT NULL
        COMMENT '原始 tool_calls JSON（assistant 消息时存完整数组）';
```

> `role` 扩展支持 `'tool'`。老页面消息这四个字段永远为 NULL，无影响。

### 4.4 新增 `agent_workspace`

```sql
CREATE TABLE `agent_workspace` (
    `id` VARCHAR(32) NOT NULL COMMENT '主键（雪花ID）',
    `name` VARCHAR(64) NOT NULL COMMENT '工作空间显示名称',
    `dir_name` VARCHAR(128) NOT NULL COMMENT '子目录名',
    `description` VARCHAR(255) DEFAULT NULL COMMENT '描述',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_dir_name` (`dir_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='智能体工作空间表';
```

### 4.4.1 升级 `llm_config`

```sql
ALTER TABLE `llm_config`
    ADD COLUMN `is_agent_supported` TINYINT(1) NOT NULL DEFAULT 0
        COMMENT '是否支持 Agent 模式（0=不支持，1=支持）';
```

> 前端 Agent 页面的模型选择器仅展示 `is_agent_supported = 1` 的模型，避免用户选了不支持 Function Calling 的模型导致 Agent 循环失败。

### 4.5 变更汇总

| 变更 | 目标 | 说明 |
|---|---|---|
| yml 配置 | `application.yml` | + `agent.workspace.root-dir` |
| ALTER | `chat_session` | + `session_mode`（默认 CHAT）, + `workspace_id` |
| ALTER | `chat_message` | + `tool_name`, + `tool_call_id`, + `tool_status`, + `tool_calls_json` |
| ALTER | `llm_config` | + `is_agent_supported` |
| CREATE | `agent_workspace` | `name` + `dir_name` + `description` |
| 逻辑启用 | `ai_tool` | MVP 阶段暂不接入外部插件，Step 4 统一通过 `/tools` 页面动态管理 |
| 逻辑启用 | `ai_skill_tool_relation` | agent 类型 Skill 绑定工具 |

---

## 五、后端改造详细设计

### 5.1 统一入口 + 策略分流

**改造文件**: [`ChatService.java`](java-apex-server/src/main/java/com/apex/service/ChatService.java)

```java
public SseEmitter sendMessage(String sessionId, String configId,
        String content, String skillId) {

    ChatSession session = sessionMapper.selectById(sessionId);
    boolean isAgent = "AGENT".equals(session.getSessionMode());

    if (isAgent) {
        String workspacePath = workspaceResolver.resolve(session.getWorkspaceId());
        return executeAgentLoop(session, configId, content, skillId, workspacePath);
    } else {
        // 原有逻辑，完全不动
        return executeClassicChat(session, configId, content, skillId);
    }
}
```

**接口路径不变**：`POST /api/chat/send`（现有前端无需任何修改）。

### 5.2 工作空间路径解析器

```java
@Component
public class WorkspaceResolver {
    @Value("${agent.workspace.root-dir}")
    private String rootDir;

    public String resolve(String workspaceId) {
        AgentWorkspace ws = workspaceMapper.selectById(workspaceId);
        return rootDir + File.separator + ws.getDirName();
    }
}
```

### 5.3 Agent 模式 while 循环（含内置工具注入）

```
executeAgentLoop():
  1. 注入内置工具（硬编码，无条件附加到每一轮请求）：
     builtInToolRegistry.getBuiltInToolsSchema()
  2. 从 ai_skill_tool_relation 关联获取外部插件 tools（可能为空）
  3. 合并工具列表：builtInSchemas + externalSchemas → 构建 LlmRequest
  4. loopCount = 0, taskCancelled = AtomicBoolean(false)
  5. while (loopCount < 5 && !taskCancelled):
     a. HttpClient → LLM API，逐行解析 SSE delta
     b. content → 推送 JSON SSE → 保存 assistant(message) → break
     c. tool_calls → 累积 → BuiltInToolRegistry 执行分发
        → 推送 JSON SSE tool_start/tool_end
        → 保存 assistant(tool_calls) + tool(result)
        → loopCount++ → buildExtendedHistory() → 重建请求（含内置工具）→ continue
  6. emitter.complete()
```

> **关键设计**：内置工具由 `BuiltInToolRegistry` 在代码中写死（Schema + 执行逻辑 + 安全沙箱），外部插件通过 `ai_skill_tool_relation` 多对多绑定表按 Skill 附加。合并后的完整 tools 数组注入到每一轮 LLM 请求中，大模型自行决定是否调用以及调用哪个。执行时通过 3.3.4 的"执行分流"：内置工具走本地沙箱，外部插件走 HTTP/脚本。

### 5.4 新增 WorkspaceController

```java
@RestController
@RequestMapping("/api/workspace")
public class WorkspaceController {

    @GetMapping("/list")              // 获取用户工作空间列表
    @PostMapping("/create")           // 创建（自动在根目录下 mkdir）
    @PutMapping("/{id}")              // 编辑名称/描述
    @DeleteMapping("/{id}")           // 删除（不删物理目录）
    @GetMapping("/{id}/tree")         // 获取文件树 JSON
    @GetMapping("/{id}/file")         // 读取工作空间内指定文件内容
}
```

### 5.5 SSE 数据格式升级（Agent 模式专用）

改造前，SSE 直接推送纯文本字符串。改造后，Agent 模式 SSE 统一推送 JSON 结构事件。**对话模式保持原样不动**。

| type | 触发时机 | payload 示例 | 前端行为 |
|---|---|---|---|
| `text` | LLM 返回 content chunk | `{"type":"text","content":"好的，我来帮您..."}` | 追加到 Markdown 渲染区 |
| `reasoning` | LLM 返回 reasoning_content | `{"type":"reasoning","content":"嗯，用户想要..."}` | 追加到思考链面板 |
| `tool_start` | 检测到 tool_calls 首个 delta | `{"type":"tool_start","name":"write_file","callId":"call_001"}` | 渲染步骤卡片：⏳ 正在调用 write_file... |
| `tool_end` | 工具执行完毕 | `{"type":"tool_end","name":"write_file","callId":"call_001","status":"success","detail":"写入成功 (1.2KB)"}` | 更新步骤卡片：✅ write_file 写入成功 |
| `file_changed` | write_file 写磁盘后 | `{"type":"file_changed","path":"config/redis.yml"}` | 触发右侧文件树增量刷新 |
| `done` | 流结束 | `{"type":"done","sessionId":"...","messageId":"..."}` | 结束标记 |
| `error` | 异常 | `{"type":"error","message":"..."}` | 错误提示 |

**后端推送示例**：

```java
// 文本
emitter.send(SseEmitter.event().name("agent").data(
    Map.of("type", "text", "content", chunk)));

// 工具开始
emitter.send(SseEmitter.event().name("agent").data(
    Map.of("type", "tool_start", "name", "write_file", "callId", callId)));

// 工具结束
emitter.send(SseEmitter.event().name("agent").data(
    Map.of("type", "tool_end", "name", "write_file",
        "callId", callId, "status", "success", "detail", "写入成功 (1.2KB)")));

// 文件变更
emitter.send(SseEmitter.event().name("agent").data(
    Map.of("type", "file_changed", "path", "config/redis.yml")));
```

> **兼容性**：老页面监听的是 SSE `message`/`done`/`error` 事件（纯文本）。Agent 模式使用独立的 SSE 事件名 `agent`，老页面不受任何影响。

### 5.6 已有接口不变

| 接口 | 变更 |
|---|---|
| `POST /api/chat/send` | 内部增加 session_mode 分流，老前端调用路径/参数/响应格式不变 |
| `GET /api/chat/sessions` | ChatView 传参不变；AgentView 新增请求参数 `?mode=AGENT` |
| `GET /api/chat/messages/{id}` | 不变，返回消息列表 |

---

## 六、前端改造详细设计

### 6.1 ChatView.vue — 完全不动

原有 [`ChatView.vue`](../web-apex-vue/src/views/ChatView.vue) **不做任何修改**。它查询 `session_mode = 'CHAT'` 的会话，发消息不带 `workspaceId`，后端自动走经典对话分支。

### 6.2 AgentView.vue — 全新页面（标准双栏 + 右侧二合一状态机）

**路由**: `/agent`
**文件**: `web-apex-vue/src/views/AgentView.vue`

#### 6.2.1 设计理念：借鉴 Roo Code / Cursor 的极简双栏

传统三栏布局（文件树 + 会话列表 + 聊天区）在屏幕较小时过于拥挤。本次设计借鉴 Roo Code 的经典交互，采用 **"标准双栏布局"**：左侧常驻文件树，右侧动态交互区通过**状态机**在"会话列表（LIST）"和"当前聊天（CHAT）"之间分时复用。

```
标准双栏布局：
┌────────────────────┬──────────────────────────────────────────┐
│  左侧栏（常驻）      │  右侧栏（动态交互区 — 二合一状态机）         │
│  📂 文件树          │                                          │
│  ├── src/          │  状态 1: LIST（会话历史 Dashboard）         │
│  ├── config/       │  状态 2: CHAT（当前会话流式聊天区）           │
│  └── ...           │                                          │
└────────────────────┴──────────────────────────────────────────┘
```

> **核心心智**：用户在写代码聊天时，历史列表完全隐藏，右侧整个空间完全留给代码上下文和 Agent 执行步骤胶囊，体验极其沉浸。会话列表和聊天区共享同一个 `workspaceId` 上下文，不需跨多栏同步数据。

#### 6.2.2 右侧面板状态机 (State Machine) 定义

右侧面板 `AgentControlPanel.vue` 只有两种核心状态：

| 状态 | 标识 | 描述 | 触发条件 |
|---|---|---|---|
| **LIST** | 会话列表态 | 展示当前工作空间下所有历史 Agent 会话 | 未打开任何会话；点击了"返回列表"；切换工作空间后 |
| **CHAT** | 聊天交互态 | 展示当前选中会话的完整聊天气泡流 + 输入框 | 点击某条历史会话；点击"新建交互"按钮 |

```
+-----------------------------------------------------------------------------------+
|  [左侧栏] 📂 文件树           | [右侧栏] 💬 动态交互区                              |
+-----------------------------+-----------------------------------------------------+
|                             |  状态 1: 【LIST 状态】 (没有打开任何会话)              |
| ├── 📂 src/                 |  +-----------------------------------------------+  |
| │   ├── 📄 main.js          |  |  🕒 历史会话 (当前空间)        [＋ 新建交互]    |  |
| ├── 📄 index.html           |  | ───────────────────────────────────────────── |  |
|                             |  |  📄 修改首页轮播图bug           (10分钟前) >  |  |
|                             |  |  📄 优化打包体积                (昨天)     >  |  |
|                             |  +-----------------------------------------------+  |
|                             |                                                     |
|                             |  状态 2: 【CHAT 状态】 (点击了某条历史，或新建交互)     |
|                             |  +-----------------------------------------------+  |
|                             |  |  [<- 返回列表]   💬 修改首页轮播图              |  |
|                             |  | ───────────────────────────────────────────── |  |
|                             |  |  用户: 帮我把轮播图时间改成3秒                 |  |
|                             |  |  AI: [执行工具 write_file...] ✅ 成功          |  |
|                             |  | ───────────────────────────────────────────── |  |
|                             |  |  [ @文件... 请输入指令                       ] |  |
|                             |  +-----------------------------------------------+  |
+-----------------------------+-----------------------------------------------------+
```

#### 6.2.3 组件结构

```html
<!-- AgentView.vue -->
<div class="agent-workspace-layout">
  <!-- 左栏：文件树（常驻） -->
  <div class="left-panel">
    <WorkspaceFileTree :workspaceId="workspaceId" />
  </div>

  <!-- 右栏：动态交互区（二合一） -->
  <div class="right-panel">
    <!-- 状态一：会话列表 -->
    <SessionList
      v-if="currentStatus === 'LIST'"
      :workspaceId="workspaceId"
      @select-session="openSession"
      @create-session="createNewSession"
    />

    <!-- 状态二：当前聊天区 -->
    <AgentChat
      v-else-if="currentStatus === 'CHAT'"
      :sessionId="currentSessionId"
      @close-chat="backToList"
    />
  </div>
</div>
```

> 顶部统一配置栏（模型选择器、Skill 选择器、工作空间选择器）放置于 `AgentView.vue` 的 header 区域，作为两个状态的公共头部，不需要在 LIST/CHAT 之间重复渲染。

#### 6.2.4 工作空间优先流程（强制性约束）

Agent 页面进入后**必须先选择工作空间**。选择后右侧默认进入 `LIST` 状态展示历史会话列表。只有选定了工作空间，才能创建新会话或进入已有会话。

```
打开 /agent 页面
    │
    ▼
┌─────────────────────────────────┐
│  未选择工作空间（初始态）          │
│  ┌─────────────────────────────┐│
│  │ 请选择或创建一个工作空间       ││
│  │ [工作空间选择器 ▼] [📂 新建]  ││
│  ├─────────────────────────────┤│
│  │ 🚫 左侧文件树  空白占位       ││
│  │ 🚫 右侧 LIST   隐藏          ││
│  │ 🚫 右侧 CHAT   隐藏          ││
│  └─────────────────────────────┘│
└─────────────────────────────────┘
    │ 用户选择或创建工作空间
    ▼
┌─────────────────────────────────┐
│  已选择工作空间（就绪态）          │
│  ┌─────────────────────────────┐│
│  │ ✅ 左侧文件树自动加载目录结构   ││
│  │ ✅ 右侧默认进入 LIST 状态      ││
│  │    （展示历史会话 + 新建按钮）  ││
│  │ ✅ 点击会话 → 切至 CHAT 态    ││
│  └─────────────────────────────┘│
└─────────────────────────────────┘
```

**强制约束规则**：
- 必须**先选定工作空间**，才能创建新 Agent 会话或进入已有会话
- 选择工作空间后：左侧文件树自动加载 → 右侧默认进入 `LIST` 状态展示历史会话
- 未选择工作空间时：整个右侧交互区隐藏，居中提示"请先选择或创建一个工作空间"
- 切换工作空间时：文件树刷新 + 右侧强制回到 `LIST` 状态（清空当前 CHAT + 重新加载会话列表）

#### 6.2.5 核心用户旅程

**场景 A：用户刚进入某个工作空间**

1. 用户从大厅点击进入某个工作空间，或通过选择器切换
2. 右侧默认 `LIST` 状态，展示该空间下所有历史会话
3. 如果历史为空，界面居中展示"发起你的第一个 Agent 任务"大按钮
4. 大模型静默，不消耗任何 Token

**场景 B：用户点击"新建交互"或点击某条历史**

1. 触发后右侧平滑切换（Vue `<transition>` 左推动画）进入 `CHAT` 状态
2. 聊天区顶部导航栏左侧出现 `<- 返回列表` 按钮（或 `✕` 关闭图标）
3. 若为新建：自动创建 Agent 会话（关联当前 workspaceId），获取 sessionId 后开始 SSE 流式交互
4. 若为历史：加载该会话的消息历史，展示完整对话流

**场景 C：关闭当前会话（返回列表）**

1. 用户点击 `<- 返回列表` 或 `✕`
2. 前端将 `currentStatus` 改为 `'LIST'`，`currentSessionId` 置空
3. 自动触发 `SessionList` 重新加载，确保刚聊完的会话标题和时间在列表里是最新的
4. 文件树保持不动，受 `file_changed` SSE 事件驱动增量更新

#### 6.2.6 核心功能

- **顶部公共栏**：LLM 模型选择器 + Skill 选择器 + 工作空间选择器（两个状态共享）
- **左侧常驻**：文件树面板（`el-tree`，选择工作空间后自动加载，`file_changed` 事件驱动增量刷新）
- **右侧 LIST 态**：历史会话列表 + "新建交互"按钮 + 空态引导
- **右侧 CHAT 态**：消息气泡 + 工具执行步骤卡片 + `<- 返回列表` 导航 + 输入框（`@` 文件联想）+ 停止按钮
- **状态切换**：Vue `<transition>` 动画平滑过渡，使用 `v-if`/`v-else` 条件渲染
- **内置工具无感化**：前端不展示任何内置工具勾选框，6 大工具完全由后端隐式注入

#### 6.2.7 SSE 事件处理

```typescript
// AgentChat.vue <script setup>（仅在 CHAT 状态下监听）
function handleAgentSSE(response: Response) {
  const reader = response.body!.getReader()
  // ... 逐行解析 SSE ...
  function dispatch(event: string, data: string) {
    if (event !== 'agent') return
    const payload = JSON.parse(data)
    switch (payload.type) {
      case 'text':
        appendToMessage(payload.content)
        break
      case 'reasoning':
        appendToReasoning(payload.content)
        break
      case 'tool_start':
        addToolStep({ name: payload.name, callId: payload.callId, status: 'running' })
        break
      case 'tool_end':
        updateToolStep(payload.callId, { status: payload.status, detail: payload.detail })
        break
      case 'file_changed':
        refreshFileTree()  // 增量刷新左侧文件树（通过 emit 或 Pinia 通知）
        break
      case 'done':
        finishMessage(payload)
        break
      case 'error':
        showError(payload.message)
        break
    }
  }
}
```

### 6.3 新增/扩展前端文件

| 文件 | 说明 |
|---|---|
| `src/views/AgentView.vue` | **新增**：Agent 父页面（顶部公共栏 + 左侧文件树 + 右侧二合一状态机容器） |
| `src/views/ChatView.vue` | **不动** |
| `src/api/chat.ts` | **扩展**：AgentView 调用 send 时传 `workspaceId` 参数 |
| `src/api/agent.ts` | **新增**：工作空间 API（list/create/update/delete/tree/file） |
| `src/components/chat/WorkspaceSelector.vue` | **新增**：工作空间下拉选择器 + 新建按钮 |
| `src/components/chat/WorkspaceFileTree.vue` | **新增**：`el-tree` 文件树面板（左侧常驻） |
| `src/components/agent/SessionList.vue` | **新增**：LIST 状态 — 历史会话列表 + "新建交互"按钮 + 空态引导 |
| `src/components/agent/AgentChat.vue` | **新增**：CHAT 状态 — 消息气泡 + 工具步骤卡片 + `<- 返回列表` + 输入框 + SSE 监听 |
| `src/types/chat.ts` | **扩展**：新增 Agent SSE 事件类型定义 |
| `src/router/index.ts` | **扩展**：+ `/agent` 路由, + `/workspaces` 路由, + `/tools` 路由 |

### 6.4 新增页面

| 页面 | 路由 | 功能 |
|---|---|---|
| Agent 工作台 | `/agent` | 标准双栏：左文件树 + 右二合一状态机（LIST/CHAT） |
| 工作空间管理 | `/workspaces` | 工作空间 CRUD |
| 工具管理 | `/tools` | `ai_tool` 启用/停用/编辑（仅外部插件） |

### 6.5 SkillManager 增强（同上版）

- `agent` 类型解除 disabled
- agent 类型弹窗增加"绑定工具"多选区域（仅绑定外部插件，内置工具由系统无条件注入）

---

## 七、MVP 最小可行开发顺序

### Step 0：内置工具 + DTO 升级（0.3 天）

- [ ] `application.yml` + `agent.workspace.root-dir`
- [ ] `LlmRequest` 扩展：`ToolDefinition`/`ToolCall`/`FunctionInfo` Record
- [ ] **`BuiltInToolRegistry` 类**：固化 6 大内置工具的 OpenAI 标准 JSON Schema + 实际执行逻辑 + 安全沙箱（`getBuiltInToolsSchema()` + `executeBuiltInTool()`），含命令黑名单、`apply_diff` 精准补丁、`locate_files` 全局搜索
- [ ] 响应 Delta 解析增加 `tool_calls` 分支

### Step 1：数据库 Flyway + 工作空间 CRUD（0.5 天）

- [ ] Flyway：ALTER `chat_session`(+session_mode/+workspace_id)、`chat_message`、`llm_config`
- [ ] Flyway：CREATE `agent_workspace`
- [ ] `AgentWorkspaceService` CRUD + 自动 mkdir
- [ ] `WorkspaceResolver` 路径拼接
- [ ] `WorkspaceController`（list/create/update/delete/tree/file）
- [ ] 前端工作空间管理页（`/workspaces`）

### Step 2：后端 Agent 分流 + SSE 格式升级（1 天）

- [ ] `ChatService.sendMessage()` 增加 session_mode 分流
- [ ] Agent 分支：while 循环（max_loops=5）+ 工具调用 + 中止
- [ ] SSE 数据格式升级为 JSON 结构事件（`agent` 事件名）
- [ ] 新增 `chat_session_mode` 过滤：ChatView 列表只查 CHAT，AgentView 只查 AGENT

### Step 3：AgentView 前端页面（标准双栏 + 状态机）（2 天）

- [ ] `/agent` 路由 → `AgentView.vue`（标准双栏：左文件树 + 右状态机容器）
- [ ] **右侧二合一状态机**：`currentStatus: 'LIST' | 'CHAT'`，`v-if`/`v-else` 条件渲染 + `<transition>` 动画
- [ ] **工作空间优先流程**：未选空间时整个右侧隐藏，选择后左侧加载文件树 + 右侧默认 LIST 态
- [ ] `WorkspaceSelector` 组件（位于顶部公共栏，内联新建入口）
- [ ] `WorkspaceFileTree` 面板（左侧常驻，选择空间后自动加载，`file_changed` 事件驱动增量刷新）
- [ ] `SessionList` 组件（LIST 态）：历史会话列表 + "新建交互"按钮 + 空态引导
- [ ] `AgentChat` 组件（CHAT 态）：消息气泡 + 工具步骤卡片 + `<- 返回列表` + 输入框（`@` 联想）+ 停止按钮
- [ ] SSE JSON 事件分发处理（text/tool_start/tool_end/file_changed）— 对接到 `AgentChat` 组件内
- [ ] 前端 `src/api/agent.ts`（工作空间 API 函数）
- [ ] **注意：不展示内置工具勾选框**，6 大内置工具（list_dir/locate_files/read_file/write_file/apply_diff/execute_command）完全由后端自动注入

### Step 4：外部插件管理 + Skill 绑定（1 天）

- [ ] 工具管理页（`/tools`）—— **仅管理外部插件**（联网搜索、知识库 RAG），不展示内置工具
- [ ] `AgentChatService.assembleAllTools()` 将 `ai_tool` 表中的外部插件动态合并到内置工具数组后（详见 3.3.1）
- [ ] SkillManager agent 类型 + 工具绑定 UI（仅绑定外部插件，内置工具由系统无条件注入）

**验证流程**：
1. 打开 `/chat` → ChatView 正常对话 → 与改造前完全一致 ✅
2. 打开 `/agent` →
   - 初始态：左侧文件树空白占位、右侧整个隐藏 → 居中提示"请选择工作空间" ✅
   - 选择/新建工作空间 → 左侧文件树自动加载 → 右侧进入 LIST 态展示历史会话 ✅
3. LIST 态点击"新建交互" → 右侧 `<transition>` 左推切换至 CHAT 态 → 自动创建 Agent 会话 ✅
4. 输入"列出当前目录下的文件" → 大模型自主调用内置 `list_dir`（前端无勾选框）→ 步骤卡片展示 → 最终回复 ✅
5. 输入"在 config/test.yml 写入测试配置" → 大模型自主调用内置 `write_file` → 文件树实时刷新 → 磁盘实际写入 ✅
6. 点击 `<- 返回列表` → `<transition>` 右推切回 LIST 态 → 会话列表自动刷新（最新会话排第一）✅
7. 同一会话切回 `/chat` → 该会话不可见（session_mode 隔离）✅
8. 前端任意位置搜索不到任何内置工具勾选框 → 6 大工具完全由后端隐式注入 ✅
9. 输入"帮我跑一下这个项目的构建命令看看有没有报错" → 大模型先通过 `list_dir` 判断项目类型（pom.xml → `mvn compile`，package.json → `npm run build`，go.mod → `go build ./...`），然后自动调用 `execute_command` 执行 → 根据 stdout/stderr 自主判断结果 → 若有报错大模型自主调用 `apply_diff` 修复后重新构建 → 自愈闭环 ✅

---

## 八、相关文件索引

### 本次改造涉及的文件

| 文件 | 变更 | 说明 |
|---|---|---|
| `application.yml` | 扩展 | + `agent.workspace.root-dir` |
| `LlmRequest.java` | 扩展 | + Tool/ToolCall/Function/ToolCallAccumulator |
| `ChatRequest.java` | 扩展 | + `workspaceId` |
| `ChatSession.java` | 扩展 | + `sessionMode`, + `workspaceId` |
| `ChatMessage.java` | 扩展 | + `toolName`, + `toolCallId`, + `toolStatus`, + `toolCallsJson` |
| `ChatService.java` | 核心改造 | 统一入口 session_mode 分流 + Agent while 循环 + SSE JSON |
| `ChatController.java` | 微调 | sessions 列表按 mode 过滤 |
| `BuiltInToolRegistry.java` | 新增 | **内置工具注册表**（Schema 定义 + 执行分发 + 安全沙箱，不查数据库） |
| `WorkspaceResolver.java` | 新增 | 根目录 + 子目录 → 实际路径 |
| `AgentWorkspaceService.java` | 新增 | 工作空间 CRUD + 目录树 |
| `WorkspaceController.java` | 新增 | 工作空间 API |
| `AiToolController.java` | 新增 | 工具管理 API（仅外部插件） |
| `AgentWorkspace.java` | 新增 | 工作空间 Entity |
| `AgentWorkspaceMapper.java` | 新增 | 工作空间 Mapper |
| Flyway `V9__agent_upgrade.sql` | 新增 | 全部 DDL |
| `web-apex-vue/src/views/AgentView.vue` | **新增** | Agent 父页面（双栏 + 状态机容器） |
| `web-apex-vue/src/views/ChatView.vue` | **不动** | 纯对话页面保持原样 |
| `web-apex-vue/src/api/chat.ts` | 扩展 | + `workspaceId` 参数（AgentView 调用用） |
| `web-apex-vue/src/api/agent.ts` | 新增 | 工作空间 API 函数 |
| `web-apex-vue/src/types/chat.ts` | 扩展 | + Agent SSE 事件类型 |
| `web-apex-vue/src/components/chat/WorkspaceSelector.vue` | 新增 | 工作空间选择器（顶部公共栏） |
| `web-apex-vue/src/components/chat/WorkspaceFileTree.vue` | 新增 | 文件树面板（左侧常驻） |
| `web-apex-vue/src/components/agent/SessionList.vue` | **新增** | LIST 态 — 历史会话列表 + 新建交互 |
| `web-apex-vue/src/components/agent/AgentChat.vue` | **新增** | CHAT 态 — 消息流 + 工具卡片 + SSE |
| `web-apex-vue/src/router/index.ts` | 扩展 | + `/agent`, `/workspaces`, `/tools` |
| `web-apex-vue/src/views/SkillManager.vue` | 扩展 | agent 类型 + 工具绑定（仅外部插件） |

### 已有关键文件（可复用）

| 文件 | 角色 |
|---|---|
| [`ChatService.java`](java-apex-server/src/main/java/com/apex/service/ChatService.java) | 核心改造目标 |
| [`LlmRequest.java`](java-apex-server/src/main/java/com/apex/model/LlmRequest.java) | DTO 扩展 |
| [`ai_skill` 表](java-apex-server/src/main/resources/db/migration/V4__create_skill_tables.sql:8) | type 已预留 agent |
| [`ai_tool` 表](java-apex-server/src/main/resources/db/migration/V4__create_skill_tables.sql:25) | declaration_json 符合 OpenAI 标准 |
| [`ai_skill_tool_relation` 表](java-apex-server/src/main/resources/db/migration/V4__create_skill_tables.sql:37) | 多对多绑定 |

---

## 九、下阶段里程碑（v5.0 生产级增强）

当前 v4.5 方案已覆盖核心闭环（前端双栏状态机 + 6 大内置工具 + 后端执行分流 + SSE JSON 事件），可直接进入 MVP 编码。以下 5 项是推向生产环境前必须考虑的"终极拼图"，建议在 Step 4 结束后按优先级迭代。

### 9.1 Agent System Prompt 框架（Agent 的心脏）

普通大模型（尤其是 7B/13B 基础模型）在复杂 Agent 循环中极易产生幻觉：工具参数乱写、死循环调用、不该调工具时瞎调。

| 措施 | 说明 |
|---|---|
| **模型选型** | Agent 模式强绑定具备顶级 Function Calling 能力的模型（Claude 3.5 Sonnet / GPT-4o / DeepSeek-V3/R1） |
| **System Prompt** | 后端拼装请求时硬编码 Agent 系统提示词，明确约束：沙箱边界、`apply_diff` 优先于 `write_file`、无绝对把握不得全量重写文件、多工具协作策略 |

```java
// AgentChatService 中注入 System Prompt
String agentSystemPrompt = """
    你是一个在严格沙箱工程目录中工作的 AI 编程助手，拥有以下能力：
    1. 修改代码时，优先使用 apply_diff 精准替换，避免全量重写大文件
    2. 修改完成后，根据项目类型自动执行构建/测试命令验证结果
    3. 如果构建失败，分析 stderr 输出并自主修复，最多尝试 3 次
    4. 无绝对把握时不得删除已有业务逻辑，只做最小化修改
    5. 所有文件操作限定在工作空间根目录内，不得越界
    """;
messages.add(0, new LlmMessage("system", agentSystemPrompt));
```

### 9.2 长对话上下文裁剪（Token 爆仓防御）

Agent 模式是"Token 暴雨"——每次工具调用（读 3 个文件、跑编译、报错输出）都全量追加到历史，十几轮后单次请求 Token 可达十几万。

| 阶段 | 策略 |
|---|---|
| **MVP（当前）** | 不做裁剪，依赖大模型原生上下文窗口（DeepSeek-V3 支持 128K） |
| **v5.0** | 当 `loopCount >= 10` 时触发裁剪：保留最初的 User Intent + 最新 3 轮工具结果，中间已成功的历史转为 `[摘要] 步骤1-7 已成功执行` 替换 |

```java
// 上下文裁剪伪代码（v5.0 实现）
if (historyMessages.size() > 20) {
    List<LlmMessage> trimmed = new ArrayList<>();
    trimmed.add(historyMessages.get(0));              // system prompt
    trimmed.add(historyMessages.get(1));              // 最初的 user 消息
    trimmed.add(new LlmMessage("assistant", "[摘要] 中间步骤已成功执行"));
    trimmed.addAll(historyMessages.subList(size - 6, size)); // 最新 3 轮
    request.setMessages(trimmed);
}
```

### 9.3 SSE 长连接心跳与断线处理

Agent 模式下 AI 可能需要 1-2 分钟执行 `mvn clean package`。Nginx/网关/浏览器的默认超时（30-60s）会断开 SSE 连接，导致前端失联。

| 措施 | 说明 |
|---|---|
| **后端心跳** | 执行耗时工具时，主 SSE 线程每 5-10 秒发送心跳包：`{"type":"heartbeat","status":"running_command"}` |
| **Nginx 配置** | `proxy_read_timeout 300s;` + `proxy_buffering off;`（与现有 AI 流式接口配置一致） |
| **前端重连** | `AgentChat.vue` 监听 SSE `onerror`，中断时弹提示"连接中断，正在重试..."，携带 `lastEventId` 重新建连 |

```java
// 心跳机制伪代码（v5.0 实现）
ScheduledExecutorService heartbeat = Executors.newSingleThreadScheduledExecutor();
heartbeat.scheduleAtFixedRate(() -> {
    try {
        emitter.send(SseEmitter.event().name("agent").data(
            Map.of("type", "heartbeat", "status", "running_command")
        ));
    } catch (IOException e) {
        heartbeat.shutdown(); // 前端已断开，停止心跳
    }
}, 5, 5, TimeUnit.SECONDS);
```

### 9.4 前端强行中止按钮（Abort / Cancel）

用户可能让 AI 执行 `npm run dev`（常驻服务器永不退出），或 AI 写的代码陷入死循环。`ProcessBuilder` 会卡死，大模型永久等待。

| 措施 | 说明 |
|---|---|
| **前端** | CHAT 态输入框旁增加红色 `[⏹ 停止]` 按钮（现有 PRD 6.2.6 已规划） |
| **后端** | 点击后前端发 `POST /api/chat/abort/{sessionId}`，后端通过 `AtomicBoolean` 标记取消 + `process.destroyForcibly()` 强杀子进程，跳出 while 循环 |

> **注意**：中止按钮 UI 已在 6.2.6 节 CHAT 态功能列表中包含。此处重点补充的是后端的 **Process 级强杀** 和 **Agent 循环优雅退出** 实现思路。

### 9.5 代码差异对比视图（Code Diff View）

AI 调用 `apply_diff`/`write_file` 修改代码后，用户需要直观看到改动内容（类似 Git 红绿对比），否则不信任 AI 的修改。

| 阶段 | 策略 |
|---|---|
| **MVP（当前）** | 不做。`file_changed` SSE 事件刷新文件树 + 步骤卡片展示工具执行状态即可 |
| **v5.0** | 右侧 CHAT 态顶部增加 `[📋 查看变更]` 按钮，点击弹出 Drawer，利用 `vue-code-diff`（或 Monaco Editor DiffEditor）以红绿高亮展示修改前后对比 |

### 9.6 优先级排序

| 优先级 | 项目 | 理由 |
|---|---|---|
| **P0（MVP 必做）** | System Prompt 框架 + 模型选型 | 没有好的 Prompt 和模型，Agent 循环根本跑不起来 |
| **P1（MVP 建议做）** | 中止按钮（前端已有占位，后端需补 Process 强杀） | 用户体验底线——不能让一个死循环命令卡死整个会话 |
| **P2（v5.0）** | SSE 心跳 + Nginx 超时配置 | Agent 模式特有，但现有 ChatView 的 SSE 配置已部分覆盖 |
| **P3（v5.0）** | 上下文裁剪 | MVP 阶段依赖模型 128K 原生窗口通常足够 |
| **P4（v5.0）** | Diff 视图 | 纯体验加分项，不影响核心功能闭环 |

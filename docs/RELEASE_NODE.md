# Apex 全栈平台 — 发布节点 v1.0.2

> **版本号**：v1.0.2  
> **发布日期**：2026-07-01  
> **上一版本**：v1.0.1  
> **文档语言**：简体中文  

---

## 1. 版本概述

**Apex** 是一个全栈 Web 应用平台，基于 **Vue 3 + Spring Boot 3.4** 构建，提供统一的知识管理、AI 对话、文件共享与技能编排能力。

v1.0.2 相比 v1.0.1 是一次**架构性重构**，核心变更如下：

| 维度 | v1.0.1 | v1.0.2 |
|------|--------|--------|
| 身份模型 | Sa-Token + JWT + Redis | **无登录**，7位工号 `X-Emp-No` 请求头 |
| 数据隔离 | 无（全局共享） | **多租户**，按工号隔离会话与 LLM 配置 |
| 基础设施 | MySQL + Redis + Nginx + App（4容器） | **MySQL + Nginx + App（3容器）**，移除 Redis |
| AI 对话 | 基础 SSE 流式 | +Skill 系统提示词注入 + 思维链 + 非流式回退 + 会话重命名 |

| 模块 | 状态 | 说明 |
|------|------|------|
| Wiki 知识库 | ✅ 正式 | Markdown 文档树管理 + 拖拽排序 + 导入/导出 + 双链跳转 + 循环引用防护 |
| AI 对话 | ✅ 正式 | 多会话管理 + SSE 流式输出 + Skill 注入 + 思维链 + 非流式回退 |
| 文件共享 | ✅ 正式 | 多文件上传 + 分页浏览 + IP 记录 + 一键下载 |
| AI 技能编排 | ✅ 正式 | 技能 CRUD + 启用/禁用 + Markdown 编辑 + 对话中一键选用 |
| LLM 配置管理 | ✅ 正式 | 多租户隔离 + 多模型配置 + 前端统一管理弹窗 |
| 部署方案 | ✅ 正式 | Docker Compose 三容器编排 + 腾讯云/内网双环境脚本 |

---

## 2. v1.0.2 核心变更详解

### 2.1 身份模型：从 JWT 到无登录工号制

**移除**：Sa-Token、JWT、Redis 全部下线。

**新增**：[`EmpContext`](java-apex-server/src/main/java/com/apex/common/EmpContext.java:1) — 基于 `ThreadLocal` 的员工号上下文：

```
前端 Header.vue 输入7位工号
       │
       ▼
localStorage['apex_current_emp_no']
       │
       ▼ 请求拦截器注入 X-Emp-No
后端 EmpContextConfig HandlerInterceptor
       │
       ▼ ThreadLocal.set(empNo)
Controller/Service 任意位置 EmpContext.getEmpNo()
       │
       ▼ ThreadLocal.remove() (请求结束自动清理)
```

| 组件 | 文件 | 职责 |
|------|------|------|
| 前端状态 | [`currentUser.ts`](web-apex-vue/src/utils/currentUser.ts) | 7位数字校验 + localStorage 持久化 |
| 请求注入 | [`request.ts`](web-apex-vue/src/utils/request.ts:17) | 拦截器自动注入 `X-Emp-No` |
| 后端拦截 | [`EmpContextConfig.java`](java-apex-server/src/main/java/com/apex/config/EmpContextConfig.java) | 请求进入时 set，结束时 clear |
| 上下文获取 | [`EmpContext.java`](java-apex-server/src/main/java/com/apex/common/EmpContext.java) | `getEmpNo()`，未设置返回 `"0000000"` |

### 2.2 多租户数据隔离

Chat 会话和 LLM 配置按工号隔离，每位用户只能看到和管理自己的数据：

| 实体 | 隔离字段 | 涉及 Service |
|------|---------|-------------|
| `chat_session` | `user_id` | [`ChatService`](java-apex-server/src/main/java/com/apex/service/ChatService.java) 所有查询/修改/删除均校验归属 |
| `llm_config` | `user_id` | [`LlmService`](java-apex-server/src/main/java/com/apex/service/LlmService.java) 所有操作校验归属 |

### 2.3 Chat 模块增强

#### 2.3.1 Skill 系统提示词注入

对话时可通过 `skillId` 参数附加预定义的 Skill（AI 技能）作为 system prompt：

- [`ChatRequest`](java-apex-server/src/main/java/com/apex/model/ChatRequest.java:6) 新增 `skillId` 字段
- [`ChatService.sendMessage()`](java-apex-server/src/main/java/com/apex/service/ChatService.java:230) 查询 Skill → 提取 `systemPrompt` → 注入 LLM 请求
- 前端 ChatView 新增 "+" 按钮弹出 Skill 选择器

#### 2.3.2 思维链渲染

支持 DeepSeek R1 等推理模型的 `reasoning_content` 增量输出：

- [`ChatService`](java-apex-server/src/main/java/com/apex/service/ChatService.java:317) 识别 `delta.reasoning_content` → SSE `event:reasoning`
- 前端 [`chat.ts`](web-apex-vue/src/api/chat.ts:133) 通过 `onReasoning` 回调独立渲染

#### 2.3.3 非流式回退

当 LLM 返回非 SSE 格式的普通 JSON 时自动解析：

- [`ChatService`](java-apex-server/src/main/java/com/apex/service/ChatService.java:336) 检测 `sawSseData` 标志 → 若未收到任何 SSE data 行 → 回退解析 `choices[0].message.content`

#### 2.3.4 会话重命名

- 前端 ChatView 下拉菜单新增 "重命名" 选项，支持就地编辑
- 后端 [`PUT /api/chat/session/{sessionId}/title`](java-apex-server/src/main/java/com/apex/controller/ChatController.java:84)

#### 2.3.5 前端 SSE 状态机重写

- 从 axios 切换到原生 `fetch` + `ReadableStream`
- 完整 SSE 状态机：`event:` / `data:` 字段级解析
- 流读取超时保护（60s 无数据自动断开）

### 2.4 Wiki 安全与健壮性增强

| 变更 | 说明 |
|------|------|
| **防循环引用** | [`moveNode()`](java-apex-server/src/main/java/com/apex/service/WikiService.java:140) 移动前检查目标父节点不能是自身或子孙节点 |
| **目标父节点校验** | 移动时必须确保目标父节点存在且为文件夹类型 |
| **标题唯一性** | [`saveOrUpdate()`](java-apex-server/src/main/java/com/apex/service/WikiService.java:88) 保存时校验标题不重复 |
| **整数间隔排序** | [`reorderSiblings()`](java-apex-server/src/main/java/com/apex/service/WikiService.java:181) 使用 0→10→20 间隔法，减少重排频率 |
| **节点计数徽标** | 前端文件夹节点显示子节点数量 badge |

### 2.5 API 路径变更（破坏性变更）

| 模块 | v1.0.1 路径 | v1.0.2 路径 |
|------|-----------|-----------|
| Wiki 保存 | `POST /api/wiki` | `POST /api/wiki/save` |
| Wiki 批量排序 | `POST /api/wiki/sort-order` | `PUT /api/wiki/sort-batch` |
| Chat 消息历史 | `GET /api/chat/sessions/{id}/messages` | `GET /api/chat/messages/{id}` |
| Chat 删除会话 | `DELETE /api/chat/sessions/{id}` | `DELETE /api/chat/session/{id}` |
| Chat 标题更新 | `PATCH /api/chat/sessions/{id}/title` | `PUT /api/chat/session/{id}/title` |
| 文件共享前缀 | `/api/fileshare/*` | `/api/file-share/*` |
| AI 技能前缀 | `/api/ai-skill/*` | `/api/skill/*` |
| LLM 配置列表 | `GET /api/llm-config/list` | `GET /api/llm-config` |

### 2.6 基础设施简化

| 维度 | v1.0.1 | v1.0.2 |
|------|--------|--------|
| 容器数量 | 4（app + nginx + mysql + redis） | 3（app + nginx + mysql） |
| 认证中间件 | Sa-Token + Redis 会话 | 无（仅 EmpContext 请求头） |
| 部署脚本 | 无 | [`deploy/`](deploy/) 8 个脚本 + 3 套 Nginx 配置 |
| 健康检查 | 无 | `/api/health` → [`HealthController`](java-apex-server/src/main/java/com/apex/controller/HealthController.java) |

---

## 3. 技术栈总览

### 3.1 前端技术栈

| 技术 | 版本 | 用途 |
|------|------|------|
| Vue 3 | 3.5.x | 渐进式前端框架，Composition API + `<script setup>` |
| TypeScript | 5.7.x | 类型安全，禁止 `any` |
| Vite | 6.0.x | 极速构建工具 |
| Element Plus | 2.9.x | UI 组件库 |
| Tailwind CSS | 3.4.x | 原子化 CSS 框架 |
| md-editor-v3 | 5.4.x | Markdown 编辑器 + 预览（支持双链 `[[]]`） |
| Axios | 1.7.x | HTTP 请求封装 |
| Pinia | 2.3.x | 轻量级状态管理 |
| Vue Router | 4.5.x | SPA 路由 |

### 3.2 后端技术栈

| 技术 | 版本 | 用途 |
|------|------|------|
| Java | 21 (虚拟线程) | 运行时环境 |
| Spring Boot | 3.4.4 | 后端框架 |
| MyBatis Plus | 3.5.9 | ORM + 分页 |
| MySQL | 8.4 | 主数据库 |
| Flyway | 随 Spring Boot | 数据库版本迁移 |
| JDK HttpClient | 21 内置 | LLM SSE 流式代理（`BodyHandlers.ofLines()`） |
| Lombok | 最新 | 代码简化 |

### 3.3 部署设施

| 组件 | 版本 | 用途 |
|------|------|------|
| Nginx | 1.27-alpine | 静态文件 + 反向代理 `/api` |
| Docker | 24+ | 容器运行时 |
| Docker Compose | 2.24+ | 多服务编排 |

---

## 4. 功能模块详情

### 4.1 Wiki 知识库

**核心功能**：

- **文档树管理**：树形展示文件夹与 Markdown 文档，支持无限层级嵌套
- **搜索过滤**：按名称实时搜索高亮文档/文件夹
- **新建与编辑**：支持根目录、文件夹内创建文档/文件夹；使用 `md-editor-v3` 实时编辑和预览
- **拖拽排序**：自由拖拽节点到目标文件夹或同级位置，后端同步更新排序序号
- **排序按钮**：节点 hover 显示上移/下移箭头图标，实现同级快速排序
- **右键菜单**：
  - 文件夹节点：新建文档、新建文件夹、导入 .md、重命名、删除
  - 文档节点：重命名、导出 Markdown、删除
- **Markdown 导入**：支持上传 `.md` 文件，可选择导入到根目录或指定文件夹
- **Markdown 导出**：将文档内容以 `.md` 格式下载到本地
- **双链跳转 `[[]]`**：内容中出现 `[[文档标题]]` 自动转为可点击链接；若目标不存在，弹窗提示一键创建
- **面包屑导航**：实时展示当前文档/文件夹的完整路径，支持点击跳转
- **循环引用防护**：移动节点时自动检测并阻止将节点移动到自身或子孙节点下
- **标题唯一性校验**：保存时自动检查标题冲突
- **整数间隔排序**：同父节点下采用 0→10→20 间隔法，减少全量重排次数

**后端接口**：[`WikiController`](java-apex-server/src/main/java/com/apex/controller/WikiController.java)

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/wiki/tree` | 获取完整文档树 |
| GET | `/api/wiki/{id}` | 获取文档详情 |
| GET | `/api/wiki/by-title` | 按标题查询（双链跳转用） |
| POST | `/api/wiki/save` | 创建/更新文档 |
| DELETE | `/api/wiki/{id}` | 删除节点（级联子节点） |
| PUT | `/api/wiki/{id}/move` | 移动节点 + 更新排序（含循环引用检测） |
| PUT | `/api/wiki/sort-batch` | 批量更新排序 |
| GET | `/api/wiki/{folderId}/children` | 获取文件夹子节点列表 |

**数据库**：`wiki_document` 表（Flyway [`V1__create_wiki_document.sql`](java-apex-server/src/main/resources/db/migration/V1__create_wiki_document.sql)）

### 4.2 AI 对话

**核心功能**：

- **会话管理**：创建/删除/重命名对话会话，左侧会话列表按更新时间倒序
- **多租户隔离**：会话按 `user_id`（工号）隔离，用户间互不可见
- **流式响应**：基于 SSE (`text/event-stream`) 的实时流式输出
- **LLM 灵活切换**：每条消息可绑定不同模型配置
- **Skill 系统提示词**：对话时可通过 `skillId` 附加预定义的 AI 技能作为 system prompt
- **思维链渲染**：支持 DeepSeek R1 等推理模型的 `reasoning_content` 独立渲染
- **非流式回退**：当 LLM 返回普通 JSON（非 SSE 格式）时自动解析
- **消息持久化**：对话历史自动保存，刷新页面恢复
- **流式超时保护**：前端 ReadableStream 60s 无数据自动断开

**后端接口**：[`ChatController`](java-apex-server/src/main/java/com/apex/controller/ChatController.java)

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/chat/sessions` | 获取当前用户会话列表 |
| GET | `/api/chat/messages/{sessionId}` | 获取会话消息历史（含归属校验） |
| POST | `/api/chat/send` | 发送消息（SSE 流式响应，支持 `skillId`） |
| DELETE | `/api/chat/session/{sessionId}` | 删除会话及其所有消息（含归属校验） |
| PUT | `/api/chat/session/{sessionId}/title` | 修改会话标题 |

**SSE 事件类型**：

| 事件名 | 说明 |
|--------|------|
| `reasoning` | 思维链增量（DeepSeek R1 等推理模型） |
| `message` | 正文内容增量 |
| `done` | 流结束，携带 `sessionId` + `messageId` |
| `error` | 错误信息 |

**数据库**：`chat_session` + `chat_message` 表（Flyway [`V2__create_chat_tables.sql`](java-apex-server/src/main/resources/db/migration/V2__create_chat_tables.sql)）

### 4.3 文件共享

**核心功能**：

- **多文件上传**：批量选择文件上传至服务端本地存储
- **文件列表**：分页查看所有已共享文件，显示文件名、大小、上传时间、上传者 IP
- **下载**：点击文件名触发浏览器下载（正确处理中文文件名）
- **删除**：从服务端删除文件

**后端接口**：[`FileShareController`](java-apex-server/src/main/java/com/apex/controller/FileShareController.java)

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/file-share/upload` | 上传文件（`multipart/form-data`） |
| GET | `/api/file-share/list` | 分页查询文件列表 |
| GET | `/api/file-share/all` | 获取全部文件（不分页） |
| GET | `/api/file-share/download/{id}` | 下载文件（中文文件名 URL 编码） |
| DELETE | `/api/file-share/{id}` | 删除文件 |

**数据库**：`shared_file` 表（Flyway [`V3__create_shared_file.sql`](java-apex-server/src/main/resources/db/migration/V3__create_shared_file.sql)）

### 4.4 AI 技能编排

**核心功能**：

- **技能 CRUD**：创建、查看、编辑、删除 AI 技能
- **启用/禁用**：控制技能是否对外可见
- **技能内容**：技能提示词正文，支持 Markdown 编辑器
- **对话集成**：在 ChatView 中通过 "+" 按钮选择已启用的 Skill，自动注入 system prompt

**后端接口**：[`AiSkillController`](java-apex-server/src/main/java/com/apex/controller/AiSkillController.java)

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/skill/enabled` | 查询所有已启用技能 |
| GET | `/api/skill` | 查询全部技能（含禁用） |
| GET | `/api/skill/{id}` | 查询单个技能 |
| POST | `/api/skill` | 创建技能 |
| PUT | `/api/skill/{id}` | 更新技能 |
| DELETE | `/api/skill/{id}` | 删除技能 |

**数据库**：`ai_skill` 表（Flyway [`V4__create_skill_tables.sql`](java-apex-server/src/main/resources/db/migration/V4__create_skill_tables.sql)）

### 4.5 LLM 配置管理

**核心功能**：

- 管理多个 LLM 提供商的连接信息（API Key、Base URL、模型名称）
- **多租户隔离**：每个用户的配置互不可见
- 对话时自由切换模型
- 前端通过统一弹窗管理所有配置

**后端接口**：[`LlmConfigController`](java-apex-server/src/main/java/com/apex/controller/LlmConfigController.java)

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/llm-config` | 获取当前用户的所有配置（不含 apiKey） |
| GET | `/api/llm-config/{id}` | 查询单个配置详情（含 apiKey，含归属校验） |
| POST | `/api/llm-config` | 创建配置（自动绑定当前工号） |
| PUT | `/api/llm-config/{id}` | 更新配置（含归属校验） |
| DELETE | `/api/llm-config/{id}` | 删除配置（含归属校验） |

### 4.6 系统信息

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/health` | 健康检查 |
| GET | `/api/system/info` | 获取客户端登录 IP |

---

## 5. 数据库版本历史

| 版本 | 脚本 | 内容 |
|------|------|------|
| V1 | [`V1__create_wiki_document.sql`](java-apex-server/src/main/resources/db/migration/V1__create_wiki_document.sql) | 创建 `wiki_document` 表，支持树结构（`parent_id` + `type` + `sort_order`） |
| V2 | [`V2__create_chat_tables.sql`](java-apex-server/src/main/resources/db/migration/V2__create_chat_tables.sql) | 创建 `chat_session`（含 `user_id` 多租户）和 `chat_message` 表 |
| V3 | [`V3__create_shared_file.sql`](java-apex-server/src/main/resources/db/migration/V3__create_shared_file.sql) | 创建 `shared_file` 表 |
| V4 | [`V4__create_skill_tables.sql`](java-apex-server/src/main/resources/db/migration/V4__create_skill_tables.sql) | 创建 `ai_skill` 表 |

> **规范**：所有 DDL 变更必须通过 Flyway 脚本管理，禁止手动改库。

---

## 6. 部署方案

### 6.1 Docker 一键部署（推荐）

```bash
# 在 deploy/ 目录执行
docker compose up -d
```

自动构建并启动 MySQL 8.4 + Spring Boot + Nginx 三个容器。健康检查链路：MySQL → App → Nginx。

详细说明见 [`DEPLOYMENT.md`](DEPLOYMENT.md)。

### 6.2 本地开发模式

```bash
# 后端
cd java-apex-server
mvn spring-boot:run

# 前端
cd web-apex-vue
npm install
npm run dev
```

前端 Vite 开发服务器通过 [`vite.config.ts`](web-apex-vue/vite.config.ts) 的 `server.proxy` 将 `/api` 请求代理到后端 `8093` 端口。

### 6.3 多环境部署脚本

| 脚本 | 用途 |
|------|------|
| [`build-server-lan.bat`](deploy/build-server-lan.bat) | 编译后端 JAR 到 `bin/backend-server/` |
| [`build-web-lan.bat`](deploy/build-web-lan.bat) | 编译前端静态资源到 `bin/web-dist/` |
| [`deploy-server-tencent.bat`](deploy/deploy-server-tencent.bat) | 编译 → SCP 上传 → 远程重启（腾讯云） |
| [`deploy-web-tencent.bat`](deploy/deploy-web-tencent.bat) | 编译 → SCP 上传前端资源（腾讯云） |
| [`deploy-docker-dev.bat`](deploy/deploy-docker-dev.bat) | 本地 Docker Compose 开发环境一键启动 |

Nginx 配置文件：

| 配置 | 适用场景 |
|------|---------|
| [`nginx-apex-lan.conf`](deploy/nginx-apex-lan.conf) | 内网 Windows Nginx 托管静态文件 + 代理接口 |
| [`nginx-apex-dmz.conf`](deploy/nginx-apex-dmz.conf) | DMZ Linux Nginx 流量摆渡（`proxy_buffering off`） |
| [`nginx-apex-tencent.conf`](deploy/nginx-apex-tencent.conf) | 腾讯云生产环境 |

### 6.4 生产构建

```bash
cd web-apex-vue
npm run build
# 产物在 dist/ 目录
```

---

## 7. 项目结构速查

```
app-apex/
├── deploy/
│   ├── docker-compose.yml              # Docker 三容器编排
│   ├── Dockerfile                      # 腾讯云单阶段构建
│   ├── build-server-lan.bat            # 后端内网构建
│   ├── build-web-lan.bat               # 前端内网构建
│   ├── deploy-server-tencent.bat       # 后端腾讯云部署
│   ├── deploy-web-tencent.bat          # 前端腾讯云部署
│   ├── nginx-apex-lan.conf             # 内网 Nginx 配置
│   ├── nginx-apex-dmz.conf             # DMZ 摆渡配置
│   └── nginx-apex-tencent.conf         # 腾讯云 Nginx 配置
├── docs/
│   ├── DEPLOYMENT.md                   # 部署手册
│   ├── DEPLOY_LAN.md                   # 内网部署手册
│   └── RELEASE_NODE.md                 # 本文档
├── java-apex-server/                   # Spring Boot 后端
│   ├── Dockerfile                      # 多阶段构建
│   ├── pom.xml
│   └── src/main/
│       ├── resources/
│       │   ├── application.yml         # 主配置（环境变量驱动）
│       │   ├── logback-spring.xml
│       │   └── db/migration/           # Flyway SQL
│       └── java/com/apex/
│           ├── common/                 # Result, EmpContext, BusinessException, GlobalExceptionHandler
│           ├── config/                 # EmpContextConfig, MyBatisPlusConfig
│           ├── controller/             # REST 控制器（Health, Wiki, Chat, FileShare, AiSkill, LlmConfig, System）
│           ├── entity/                 # MyBatis Plus 实体
│           ├── mapper/                 # MyBatis Plus Mapper
│           ├── model/                  # DTO / VO Record 类
│           └── service/                # 业务 Service
└── web-apex-vue/                       # Vue 3 前端
    ├── Dockerfile
    ├── nginx.conf                      # Nginx 反向代理 + 静态资源
    ├── package.json
    ├── vite.config.ts
    └── src/
        ├── api/                        # API 函数（wiki, chat, skill, fileShare, system, health）
        ├── components/
        │   ├── Layout/                 # 主布局（index, Header, Sidebar, StatusBar）
        │   ├── chat/                   # LLM 配置弹窗
        │   └── wiki/                   # Wiki 文件夹文档列表
        ├── router/                     # 路由配置
        ├── types/                      # TypeScript 类型定义（wiki, chat, skill）
        ├── utils/                      # request.ts, currentUser.ts
        └── views/                      # 页面（Home, ChatView, WikiManager, FileShare, SkillManager）
```

---

## 8. 已知限制

1. **Wiki 权限**：当前所有用户共享同一 Wiki 空间，无文档级权限控制。
2. **LLM 流式输出**：依赖 OpenAI 兼容 API 格式，其他非标准 API 需适配。
3. **文件共享**：文件存储在本地磁盘，未支持对象存储（OSS/S3）。
4. **搜索引擎**：Wiki 搜索基于前端过滤的全文匹配，未集成服务端全文检索。
5. **多语言**：前端界面和文档目前仅支持中文。
6. **LLM 配置**：`apiKey` 明文存储在数据库，生产环境建议加密或使用 Vault。
7. **会话归属**：删除 LLM 配置后，关联的历史会话仍保留，但无法再发送新消息（config 查不到会抛异常）。

---

## 9. 后续规划

| 优先级 | 计划 | 目标版本 |
|--------|------|----------|
| P0 | LLM apiKey 字段加密存储（AES-256-GCM） | v1.1 |
| P1 | Wiki 全文检索（Elasticsearch / MySQL FULLTEXT） | v1.2 |
| P1 | Wiki 版本历史 / 差异对比 | v1.2 |
| P2 | 文件共享支持对象存储（S3/MinIO） | v1.3 |
| P2 | AI 对话支持多轮 Skill 切换 | v1.3 |
| P3 | 国际化 (i18n) | v1.4 |
| P3 | CI/CD 自动化流水线 | v1.5 |

---

> 📌 **版本签名**  
> Apex v1.0.2 — 架构性重构版本。移除 JWT/Sa-Token/Redis，全面采用无登录工号制 + 多租户隔离。Chat 模块新增 Skill 系统提示词注入、思维链渲染、非流式回退。Wiki 模块增强循环引用防护与标题唯一性校验。基础设施从 4 容器简化为 3 容器。  
> 发布日期：2026-07-01

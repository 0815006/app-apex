# Apex 全栈平台 — 发布节点 v1.0.1

> **版本号**：1.0.0 (首个正式可交付版本)  
> **发布日期**：2026-06-09  
> **文档语言**：简体中文  

---

## 1. 版本概述

**Apex** 是一个全栈 Web 应用平台，基于 **Vue 3 + Spring Boot 3.4** 构建，旨在提供统一的知识管理、AI 对话、文件共享与技能编排能力。v1.0.0 作为首个正式可交付版本，涵盖了以下核心模块：

| 模块 | 状态 | 说明 |
|------|------|------|
| Wiki 知识库 | ✅ 正式 | Markdown 文档树管理 + 实时编辑预览 + 导入/导出 + 双链跳转 |
| AI 对话 | ✅ 正式 | 多会话管理 + SSE 流式输出 + LLM 配置切换 + 对话历史持久化 |
| 文件共享 | ✅ 正式 | 多文件上传 + 分页浏览 + IP 记录 + 一键下载 |
| AI 技能编排 | ✅ 正式 | 系统技能创建/启用/禁用 + Markdown 编辑支持 |
| 部署方案 | ✅ 正式 | Docker Compose 一键编排 + 多阶段构建 |

---

## 2. 技术栈总览

### 2.1 前端技术栈

| 技术 | 版本 | 用途 |
|------|------|------|
| Vue 3 | 3.5.x | 渐进式前端框架，Composition API |
| TypeScript | 5.7.x | 类型安全 |
| Vite | 6.0.x | 极速构建工具 |
| Element Plus | 2.9.x | UI 组件库 |
| Tailwind CSS | 3.4.x | 原子化 CSS 框架 |
| md-editor-v3 | 5.4.x | Markdown 编辑器 + 预览（支持双链 [[标题]]） |
| Axios | 1.7.x | HTTP 请求封装 |
| Pinia | 2.3.x | 轻量级状态管理 |
| Vue Router | 4.5.x | SPA 路由 |

### 2.2 后端技术栈

| 技术 | 版本 | 用途 |
|------|------|------|
| Java | 21 (虚拟机程) | 运行时环境 |
| Spring Boot | 3.4.4 | 后端框架 |
| MyBatis Plus | 3.5.9 | ORM + 分页 |
| MySQL | 8.4 | 主数据库 |
| Redis | 7.2 | 缓存 / Sa-Token 会话 |
| Flyway | 随 Spring Boot | 数据库版本迁移 |
| Sa-Token | 随 Spring Boot | 轻量级认证鉴权框架 |
| Lombok | 最新 | 代码简化 |

### 2.3 部署设施

| 组件 | 版本 | 用途 |
|------|------|------|
| Nginx | 1.27-alpine | 静态文件 + 反向代理 `/api` |
| Docker | 24+ | 容器运行时 |
| Docker Compose | 2.24+ | 多服务编排 |

---

## 3. 功能模块详情

### 3.1 Wiki 知识库

**核心功能**：

- **文档树管理**：树形展示文件夹与 Markdown 文档，支持无限层级嵌套
- **搜索过滤**：按名称实时搜索高亮文档/文件夹
- **新建与编辑**：支持根目录、文件夹内创建文档/文件夹；使用 `md-editor-v3` 实时编辑和预览 Markdown 内容
- **拖拽排序**：自由拖拽节点到目标文件夹或同级位置，后端同步更新排序序号
- **排序按钮**：节点 hover 显示上移/下移箭头图标，实现同级快速排序
- **右键菜单**：
  - 文件夹节点：新建文档、新建文件夹、导入 .md、重命名、删除
  - 文档节点：重命名、导出 Markdown、删除
- **Markdown 导入**：支持上传 `.md` 文件，可选择导入到根目录或指定文件夹
- **Markdown 导出**：将文档内容以 `.md` 格式下载到本地
- **双链跳转 `[[]]`**：内容中出现 `[[文档标题]]` 自动转为可点击链接；若目标不存在，弹窗提示一键创建
- **面包屑导航**：实时展示当前文档/文件夹的完整路径，支持点击跳转

**后端接口**：`WikiController`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/wiki/tree` | 获取完整文档树 |
| GET | `/api/wiki/{id}` | 获取文档详情 |
| GET | `/api/wiki/by-title` | 按标题模糊查询（双链跳转用） |
| POST | `/api/wiki` | 创建/更新文档 |
| DELETE | `/api/wiki/{id}` | 删除节点（级联子节点） |
| PUT | `/api/wiki/{id}/move` | 移动节点 + 更新排序 |
| POST | `/api/wiki/sort-order` | 批量更新排序 |
| GET | `/api/wiki/{folderId}/children` | 获取文件夹子节点列表 |

**数据库**：`wiki_document` 表（Flyway `V1__create_wiki_document.sql`）

---

### 3.2 AI 对话

**核心功能**：

- **会话管理**：创建/删除/重命名对话会话，左侧会话列表
- **流式响应**：基于 SSE (`text/event-stream`) 的实时流式输出，用户体验流畅
- **LLM 灵活切换**：从 `llm_config` 表读取已启用的模型配置（OpenAI 兼容接口），每条消息可绑定不同模型
- **消息持久化**：对话历史自动保存，刷新页面恢复

**后端接口**：`ChatController`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/chat/sessions` | 获取所有会话 |
| GET | `/api/chat/sessions/{sessionId}/messages` | 获取会话消息历史 |
| POST | `/api/chat/send` | 发送消息（SSE 流式响应） |
| DELETE | `/api/chat/sessions/{sessionId}` | 删除会话 |
| PATCH | `/api/chat/sessions/{sessionId}/title` | 修改会话标题 |

**数据库**：`chat_session` + `chat_message` 表（Flyway `V2__create_chat_tables.sql`）

---

### 3.3 文件共享

**核心功能**：

- **多文件上传**：批量选择文件上传至服务端本地存储
- **文件列表**：分页查看所有已共享文件，显示文件名、大小、上传时间、上传者 IP
- **下载**：点击文件名触发浏览器下载
- **删除**：从服务端删除文件

**后端接口**：`FileShareController`

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/fileshare/upload` | 上传文件（`multipart/form-data`） |
| GET | `/api/fileshare/list` | 分页查询文件列表 |
| GET | `/api/fileshare/all` | 获取全部文件（不分页） |
| GET | `/api/fileshare/download/{id}` | 下载文件 |
| DELETE | `/api/fileshare/{id}` | 删除文件 |

**数据库**：`shared_file` 表（Flyway `V3__create_shared_file.sql`）

---

### 3.4 AI 技能编排

**核心功能**：

- **技能 CRUD**：创建、查看、编辑、删除 AI 技能
- **启用/禁用**：控制技能是否对外可见
- **技能内容**：技能提示词正文，支持 Markdown 编辑器

**后端接口**：`AiSkillController`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/ai-skill/enabled` | 查询所有已启用技能 |
| GET | `/api/ai-skill/all` | 查询全部技能（含禁用） |
| GET | `/api/ai-skill/{id}` | 查询单个技能 |
| POST | `/api/ai-skill` | 创建技能 |
| PUT | `/api/ai-skill/{id}` | 更新技能 |
| DELETE | `/api/ai-skill/{id}` | 删除技能 |

**数据库**：`ai_skill` 表（Flyway `V4__create_skill_tables.sql`）

---

### 3.5 LLM 配置管理

**核心功能**：

- 管理多个 LLM 提供商的连接信息（API Key、Base URL、模型名称）
- 对话时自由切换模型

**后端接口**：`LlmConfigController`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/llm-config/list` | 获取所有 LLM 配置 |
| GET | `/api/llm-config/{id}` | 查询单个配置 |
| POST | `/api/llm-config` | 创建配置 |
| PUT | `/api/llm-config/{id}` | 更新配置 |
| DELETE | `/api/llm-config/{id}` | 删除配置 |

---

## 4. 数据库版本历史

| 版本 | 脚本 | 内容 |
|------|------|------|
| V1 | `V1__create_wiki_document.sql` | 创建 `wiki_document` 表，支持树结构（`parent_id` + `type` 区分文件夹/文档 + `sort_order` 排序） |
| V2 | `V2__create_chat_tables.sql` | 创建 `chat_session` 和 `chat_message` 表 |
| V3 | `V3__create_shared_file.sql` | 创建 `shared_file` 表 |
| V4 | `V4__create_skill_tables.sql` | 创建 `ai_skill` 表 |

> **规范**：所有 DDL 变更必须通过 Flyway 脚本管理，禁止手动改库。

---

## 5. 部署方案

### 5.1 Docker 一键部署（推荐）

```bash
# 在项目根目录执行
docker compose up -d
```

自动拉取镜像并构建，启动 MySQL 8.4 + Redis 7.2 + Spring Boot + Nginx 四个容器。

详细说明见 [DEPLOYMENT.md](DEPLOYMENT.md)。

### 5.2 本地开发模式

```bash
# 后端
cd java-apex-server
mvn spring-boot:run

# 前端
cd web-apex-vue
npm install
npm run dev
```

### 5.3 生产构建

```bash
cd web-apex-vue
npm run build
# 产物在 dist/ 目录
```

---

## 6. 安全待办项（v1.1+）

| 项 | 当前状态 | 说明 |
|----|----------|------|
| JWT 密钥 | 硬编码默认值 | 生产环境必须以环境变量 `JWT_SECRET` 注入随机值 |
| 数据库密码 | `root123` | 生产环境通过 Docker Secrets 或环境变量注入 |
| Redis 密码 | 无 | 生产环境建议设置 `requirepass` |
| CORS 策略 | 未限制 | 生产环境应配置允许的 Origin 白名单 |
| 用户认证 | 基础 JWT | 下一步可扩展为用户注册/登录/权限系统 |

---

## 7. 已知限制

1. **Wiki 权限**：当前所有用户共享同一 Wiki 空间，无文档级权限控制。
2. **LLM 流式输出**：依赖 OpenAI 兼容 API 格式，其他非标准 API 需适配。
3. **文件共享**：文件存储在本地磁盘，未支持对象存储（OSS/S3）。
4. **搜索引擎**：Wiki 搜索是基于前端过滤的全文匹配，未集成服务端全文检索（如 Elasticsearch）。
5. **多语言**：前端界面和文档目前仅支持中文。

---

## 8. 后续规划

| 优先级 | 计划 | 目标版本 |
|--------|------|----------|
| P0 | 生产安全加固（密钥、密码外部化） | v1.0.1 |
| P1 | 用户注册/登录 + RBAC 权限 | v1.1 |
| P1 | Wiki 全文检索（Elasticsearch） | v1.2 |
| P2 | Wiki 版本历史 / 差异对比 | v1.2 |
| P2 | 文件共享支持对象存储 | v1.3 |
| P3 | 国际化 (i18n) | v1.4 |
| P3 | CI/CD 自动化流水线 | v1.5 |

---

## 9. 项目结构速查

```
app-apex/
├── docker-compose.yml                # 一键部署编排
├── docs/
│   ├── DEPLOYMENT.md                 # 部署手册
│   └── RELEASE_NODE.md               # 本文档
├── java-apex-server/                 # Spring Boot 后端
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/main/
│       ├── resources/
│       │   ├── application.yml       # 主配置
│       │   └── db/migration/         # Flyway SQL
│       └── java/com/apex/
│           ├── common/               # 通用工具、异常处理
│           ├── config/               # SaToken, MyBatisPlus, 安全
│           └── controller/           # REST 控制器层
└── web-apex-vue/                     # Vue 3 前端
    ├── Dockerfile
    ├── nginx.conf                    # Nginx 反向代理
    ├── package.json
    ├── vite.config.ts
    └── src/
        ├── api/                      # API 函数
        ├── components/               # 可复用组件
        ├── router/                   # 路由配置
        ├── types/                    # TypeScript 类型定义
        ├── utils/                    # 工具函数
        └── views/                    # 页面组件
```

---

> 📌 **版本签名**  
> Apex v1.0.0 — 首个正式可交付版本，具备完整的 Wiki 知识管理、AI 对话、文件共享与技能编排能力，支持 Docker 一键部署。  
> 发布日期：2026-06-09
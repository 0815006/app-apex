# 2026 全栈稳健版项目规范 (Java 21 + Vue 3)

## 1. 项目基础信息与目录结构
当前工作区是一个基于 **Java 21 (虚拟线程)** 和 **Vue 3 (Vite 6)** 的全栈项目。
- **后端目录**：`java-apex-server` (Spring Boot 3.4+, Maven, Java 21)
- **前端目录**：`web-apex-vue` (Vue 3.5+, Vite 6, TypeScript, Element Plus)
- **数据库**：`apex_db` (MySQL 8.4 LTS)

---

## 2. 后端开发规范 (Spring Boot 3.4)
你是一个资深的 Java 架构师。在处理后端代码时，必须遵守以下准则：

### 2.1 核心架构与并发
* **高性能并发**：强制开启虚拟线程：`spring.threads.virtual.enabled: true`。
* **身份识别机制**：无传统登录流程。前端通过右上角7位工号控制当前操作者身份，所有 `/api` 请求由 [`request.ts`](web-apex-vue/src/utils/request.ts) 自动注入 `X-Emp-No` 请求头。后端通过 [`EmpContext.getEmpNo()`](java-apex-server/src/main/java/com/apex/common/EmpContext.java) 获取当前操作员工号。
* **代码风格**：使用 **Lombok** (`@Data`, `@Slf4j`)，接口返回数据优先使用 Java **Record** 类。

### 2.2 接口路径规范
* **路径前缀**：所有 Controller 的 `@RequestMapping` **必须以 `/api` 开头**（例如：`/api/user`, `/api/order`），**禁止**添加 `/v1` 等版本号。

### 2.3 安全加密流
* **敏感数据**：手机号、身份证等字段使用 **AES-256-GCM** 算法进行加解密存储。

### 2.4 持久层与数据库
* **ORM 框架**：使用 **MyBatis Plus 3.5.x**，优先使用 `LambdaQueryWrapper`。
* **主键规范**：对于 Wiki 文档等核心业务表，主键必须使用 String 类型（VARCHAR(32)），对应 MyBatis-Plus 的雪花 ID（`@TableId(type = IdType.ASSIGN_ID)`）。
* **版本管理**：**禁止手动改库**。所有变更通过 **Flyway** 脚本实现（`src/main/resources/db/migration`）。
* **SQL 规范**：MySQL 8.4 语法，`ENGINE=InnoDB`，字符集 `utf8mb4`，字段必须带 `COMMENT`。

### 2.5 响应与异常
* **统一响应**：所有 Controller 返回泛型类 `Result<T>`：`{ "code": 200, "message": "success", "data": { ... } }`。
* **全局异常**：通过 `@RestControllerAdvice` 统一捕获异常并封装为 `Result`。

### 2.6 员工号上下文 (EmpContext)
* **获取方式**：在任何 Bean（Controller/Service/Mapper）中直接调用 `EmpContext.getEmpNo()` 即可获取当前操作员工号，无需层层透传参数。
* **默认值**：若前端未设置员工号，默认返回 `"0000000"`。
* **生命周期**：由 [`EmpContextConfig`](java-apex-server/src/main/java/com/apex/config/EmpContextConfig.java) 中的 `HandlerInterceptor` 在请求进入时设置、请求结束时清理，基于 ThreadLocal，天然支持虚拟线程。

## 3. 前端开发规范 (Vue 3 + TS)
你是一个资深的前端架构师。**禁止输出 Vue 2、Options API 或纯 JS**：

### 3.1 语法与 UI
* **核心语法**：必须使用 **Vue 3 `<script setup>` + TypeScript**。严禁使用 `any`。
* **UI 组件库**：必须使用 **Element Plus**。
* **Markdown 组件**：文档编辑与预览统一采用 **`md-editor-v3`** 组件，排版美化必须配合 **`@tailwindcss/typography`** 插件（使用 `prose` 类名）。
* **组件组织规范**：页面专用的复杂弹窗、抽屉等组件（Dialog/Drawer），**禁止**堆砌在单一的 View 视图大文件中。必须将其抽离并统一放置在 `src/components/` 下对应的业务子目录中（例如：`src/components/wiki/WikiFolderDialog.vue`）。子目录名称必须与业务页面（View）的名称或功能严格对应，以便清晰识别组件的业务归属。

### 3.2 网络请求与 API 管理
* **API 集中化**：必须在 `src/api/` 目录下创建 **`.ts`** 文件统一管理接口函数。
* **接口路径**：请求路径必须与后端 `/api` 前缀保持一致。
* **Axios 封装**：
    * 封装位于 `src/utils/request.ts`。
    * **拦截器**：自动从 [`currentUser.ts`](web-apex-vue/src/utils/currentUser.ts) 读取工号并注入 `X-Emp-No` 请求头；识别 `code !== 200` 并通过 `ElMessage.error` 提示（注意：需放行特定业务逻辑错误码如 Wiki 的 404，由页面自行处理）。

### 3.3 全局身份与 Layout 框架规范
AI 在处理、重构或引用系统级主架构时，必须严格保持以下原项目策略的 Vue 3 TS 升级版实现：

#### 3.3.1 核心身份模型（7位工号切换）
* **状态存储**：当前登录员工号依赖 `src/utils/currentUser.ts`（包含 `getCurrentEmpNo()`, `setCurrentEmpNo()`, `isEmpNoValid()`）。
* **验证规则**：员工号必须为 **7位数字** 字符串（对应数据库 String 类型识别）。
* **交互闭环**：`Header.vue` 内部必须保留点击工号 Tag 切换为 `<el-input>` 的无缝就地编辑（Inline Edit）模式。支持 `maxlength="7"`、`@keyup.enter` 触发切换，并在身份更新后利用 `ElMessage.success` 反馈。
* **请求头注入**：工号切换后，[`request.ts`](web-apex-vue/src/utils/request.ts) 的请求拦截器自动将最新工号注入 `X-Emp-No` 请求头，后端通过 `EmpContext.getEmpNo()` 获取，全程无需登录。

#### 3.3.2 经典网格布局框架 (CSS Grid)
主环境布局 `src/components/Layout/index.vue` 必须严格基于以下网格骨架进行渲染，禁止随意修改结构：

* **结构划分**：
```css
.layout-wrapper {
  display: grid;
  grid-template-columns: 240px 1fr; /* 左侧菜单宽 240px */
  grid-template-rows: auto 1fr 34px; /* 顶栏自适应，中间主视图，底栏 34px */
  height: 100dvh;
  width: 100%;
  overflow: hidden;
}

```

* **状态持久栏 (Status Bar)**：底部必须保留统一的 `status-bar`，用于展示通过定时器（每秒刷新）驱动的系统本地化时间，以及通过 `src/api/system.ts` 获取并放行的用户真实 `Login IP`。

2. **事件委托拦截**：在主要阅读容器上绑定 `@click` 事件，通过 `(e.target as HTMLElement).closest('.wiki-internal-double-link')` 优雅拦截动态生成的锚点点击。
3. **404 引导创建**：点击后若后端返回文档不存在，必须触发 `ElMessageBox.confirm` 弹窗提示，确认后前端自动初始化该标题的数据并一键切入**编辑模式**，达成双链闭环。
---

## 4. 工程化与部署
### 4.1 自动化部署
* **Dockerfile**：腾讯云使用单阶段构建（JRE 21 运行），本地 Maven 编译后 SCP 上传 JAR 包。本地 Docker Compose 环境使用 `java-apex-server/Dockerfile` 多阶段构建。
* **Docker Compose**：提供一键启动脚本，包含 `app`, `nginx`, `mysql 8.4`。
* **网络隔离**：生产环境中数据库仅在 Docker 内部网络开放，**禁止**将 MySQL 端口映射到宿主机。本地开发环境（`docker-compose.yml`）允许映射 `3306` 端口以便调试工具连接。

### 4.2 代理配置
* **Vite 配置**：在 `vite.config.ts` 配置 `server.proxy` 将 `/api` 请求代理到后端。**禁止硬编码 `localhost`**。

### 4.3 多环境部署与脚本编写规范

#### 4.3.1 项目交付架构与目录规范
- 所有面向本地或内网（LAN）环境编译打包的最终成品，必须严格输出到 `bin/` 目录下：
  - `bin/web-dist/`：存放纯前端 Web 静态资源。
  - `bin/backend-server/`：存放 Java 后端 JAR 包及 WinSW 后台服务注册相关文件。
  - `bin/client-lan/`：存放专为内网环境（指定 IP 变体）生成的客户端可执行文件（.exe）。
- 所有的部署脚本、自动化批处理文件、Nginx 配置文件，必须全部存放在 `deploy/` 目录下。

#### 4.3.2 脚本命名硬性规范
在编写或修改任何部署/打包脚本时，必须严格遵守以下命名动宾结构，禁止使用模糊的名称（如 "deploy.bat" 或 "build.sh"）：
- **内网/本地环境（本地落盘）**：统一使用 `build-[对象]-[环境].bat` 格式。
  - 示例：`build-server-lan.bat`、`build-web-lan.bat`、`build-client-lan.bat`。
- **腾讯云环境（远程一键流）**：统一使用 `deploy-[对象]-[环境].bat` 格式。
  - 示例：`deploy-server-tencent.bat`。

#### 4.3.3 技术栈特定约束
* **后端 (Java)**：针对本地长期挂载的 Windows 系统服务，必须在启动参数中显式限制 JVM 内存（例如 `-Xms256m -Xmx512m`），防止内存溢出。
* **前端 (Vue/Vite)**：
  - 针对本地局域网联调环境，`vite.config.ts` 中的 `server.host` 必须配置为 `'0.0.0.0'`，以允许外部 Nginx 摆渡流量接入。
  - 必须妥善处理大模型流式响应接口（如 `/chat/send`），确保代理层不开启压缩和缓存缓冲，实现打字机效果逐块流式传递。

#### 4.3.4 网络与部署逻辑
* **腾讯云部署**：采用纯自动化远程流。脚本执行时在本地临时编译后，直接通过 SSH/SCP 推送到云端服务器并触发远程重启命令。**绝对不能**将腾讯云的编译产物遗留或污染到本地的 `bin/` 目录中。
* **内网 Windows 与 Linux 摆渡**：本地 Windows Nginx 负责托管 `bin/web-dist` 并代理接口；公共 Linux 服务器的 Nginx 仅作为"流量传话筒"，将信创机等外部访问无脑反向代理回本地 Windows 机器，且必须针对 AI 流式接口配置 `proxy_buffering off;`。


## 5. 数据替换与修改逻辑 (AI 执行指令)
1. **去 Mock化**：识别页面静态假数据，在 `onMounted` 中调用 `src/api/` 里的 TS 函数获取真实数据。
2. **加载反馈**：请求期间必须配合 `v-loading` 增加加载状态。
3. **重构逻辑**：输入代码若为旧版 Vue 2 或 Java 8，自动将其"无损重构"为上述 2026 技术栈版本。

---

## 6. 专属提示
* **生成 SQL**：核心时间审计字段命名为 `create_time` 和 `update_time`，且默认为 `CURRENT_TIMESTAMP`。对于 Wiki 核心业务表，主键设计为 `VARCHAR(32)`。
* **输出页面**：给出完整的 `.vue` 文件（Template, Script setup TS, Style scoped）。
* **双链处理逻辑**：在渲染 Markdown 前，必须通过正则 `/\[\[(.*?)\]\]/g` 将双链语法替换为具有 `data-wiki-title` 属性的本地路由跳转链接，并实现点击拦截与"后端返回 404 时引导用户快捷新建"的交互闭环。

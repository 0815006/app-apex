# Apex 全栈平台 — 部署手册

> **适用版本**：1.0.0-SNAPSHOT  
> **最后更新**：2026-06-15  
> **适用环境**：Windows / Linux / macOS

---

## 1. 两种部署模式概览

本项目支持两种部署方式，分别对应不同的使用场景：

| 模式 | 目标环境 | 核心文件 | 用途 |
|------|---------|---------|------|
| **模式 A：全家桶一键部署** | 本地开发机（Docker Desktop） | [`deploy/docker-compose.yml`](deploy/docker-compose.yml) | 本地开发/测试，一键拉起全部服务 |
| **模式 B：腾讯云服务器部署** | 腾讯云 CVM（129.211.9.238） | [`deploy/deploy_cloud_backend.bat`](deploy/deploy_cloud_backend.bat) + [`deploy/deploy_cloud_frontend.bat`](deploy/deploy_cloud_frontend.bat) | 生产环境，前后端分离部署 |

---

## 2. 系统架构

### 2.1 模式 A 架构（Docker Compose 本地全家桶）

```
                    ┌──────────────┐
                    │   浏览器       │
                    │  localhost:80  │
                    └──────┬───────┘
                           │
                    ┌──────▼───────┐
                    │  Nginx :80   │  ← web-apex-vue/Dockerfile 构建
                    │  (容器内)     │    静态文件 + /api 反向代理
                    └──────┬───────┘
                           │ /api → app:8093
                    ┌──────▼───────┐
                    │ Spring Boot  │  ← java-apex-server/Dockerfile 多阶段构建
                    │  :8093       │    启用虚拟线程
                    └──┬────────┬──┘
                       │        │
              ┌────────▼──┐ ┌──▼────────┐
              │ MySQL 8.4 │ │ Redis 7.2 │  ← 仅 apex-internal 桥接网络
              │  :3306    │ │  :6379    │
              └───────────┘ └───────────┘
```

- 所有服务在同一个 `docker-compose.yml` 中编排
- MySQL/Redis **仅在 `apex-internal` 桥接网络内部暴露**，不对外（开发阶段 MySQL 端口映射到宿主机方便调试）
- 数据持久化：MySQL 通过命名卷 `mysql-data` 挂载

### 2.2 模式 B 架构（腾讯云生产环境）

```
  用户浏览器
  (realapex.site:80 或 IP:8083)
       │
  ┌────▼──────────────────────────────────┐
  │  腾讯云 CVM (129.211.9.238)            │
  │                                       │
  │  ┌─────────────────────────────────┐ │
  │  │  Nginx (宿主机直接安装)           │ │
  │  │  listen 8083                    │ │
  │  │  / → /var/www/app-apex/dist/    │ │
  │  │  /api/ → proxy 127.0.0.1:8093   │ │
  │  └──────────┬──────────────────────┘ │
  │             │                         │
  │  ┌──────────▼──────────────────────┐ │
  │  │  Docker 容器: app-apex           │ │
  │  │  Port: 8093 (宿主机映射)         │ │
  │  │  cloud-dockerfile 单阶段构建     │ │
  │  │  JRE 21 Alpine + app.jar        │ │
  │  └──────────┬──────────────────────┘ │
  │             │ host.docker.internal    │
  │  ┌──────────▼──────────────────────┐ │
  │  │  MySQL 8.4 (宿主机直接安装)      │ │
  │  │  Port: 3306                     │ │
  │  └─────────────────────────────────┘ │
  │                                       │
  │  /data/apex/file-share (宿主机目录)    │
  └───────────────────────────────────────┘
```

- 后端以 **Docker 容器** 运行（单阶段 JRE 镜像），MySQL 和 Nginx 在宿主机直接安装
- DB 连接通过 `--add-host=host.docker.internal:host-gateway` 从容器访问宿主机 MySQL
- 文件共享目录 `/data/apex/file-share` 挂载到容器
- 前端通过 `deploy/nginx-apex-tencent.conf` 配置 Nginx

---

## 3. 核心组件

| 组件 | 版本 | 用途 |
|------|------|------|
| Nginx | 1.27-alpine / 宿主机 1.27 | 前端静态文件托管 + `/api` 反向代理 |
| Java | 21 (Eclipse Temurin) | 后端运行时，启用虚拟线程 |
| Spring Boot | 3.4+ | 后端框架 |
| MySQL | 8.4 | 主数据库 (`apex_db`) |
| Redis | 7.2-alpine | 缓存 |
| Node.js | 22-alpine (仅构建阶段) | 前端 Vite 编译 |

### 关键端口一览

| 服务 | 容器内端口 | 对外端口（模式A） | 对外端口（模式B） |
|------|----------|-----------------|------------------|
| Nginx | 80 | **80** | **8083** |
| Spring Boot | 8093 | **8093**（调试） | **8093**（Docker映射） |
| MySQL | 3306 | 3306（调试可关） | 3306（宿主机） |
| Redis | 6379 | 未暴露 | —（模式B不使用Redis） |

---

## 4. 环境要求

### 4.1 模式 A 所需工具

| 工具 | 最低版本 | 验证命令 |
|------|---------|----------|
| Docker | 24+ | `docker --version` |
| Docker Compose | 2.24+ | `docker compose version` |

**无需本地安装** JDK / Maven / Node.js / MySQL / Redis，全部由 Docker 镜像提供。

### 4.2 模式 B 所需工具（本地开发机）

| 工具 | 最低版本 | 说明 |
|------|---------|------|
| JDK | 21+ | Maven 编译后端 |
| Maven | 3.9+ | 后端打包 |
| Node.js | 22+ | 前端 Vite 构建 |
| npm | 10+ | 依赖管理 |
| SSH 客户端 | — | `ssh` 和 `scp` 命令可用，连接腾讯云 |
| 腾讯云服务器 | — | 已安装 Docker、MySQL 8.4、Nginx 1.27 |

---

## 5. 模式 A：本地全家桶一键部署

### 5.1 快速启动

在项目根目录执行：

```bash
docker compose -f deploy/docker-compose.yml up -d
```

此命令将自动：
1. 拉取 `mysql:8.4` 和 `redis:7.2-alpine` 镜像
2. 通过多阶段构建编译后端（Maven 编译 → JRE 21 运行）
3. 通过多阶段构建编译前端（Node 22 编译 → Nginx 1.27 运行）
4. 创建 `apex-internal` 桥接网络
5. 按依赖顺序启动：MySQL → Redis → App → Nginx
6. 挂载 MySQL 数据卷 `mysql-data` 实现持久化

### 5.2 启动后的验证

```bash
# 检查容器状态
docker compose -f deploy/docker-compose.yml ps

# 预期输出
# NAME          STATUS
# apex-mysql    healthy
# apex-redis    running
# apex-server   healthy
# apex-nginx    running
```

```bash
# 健康检查
curl http://localhost:8093/api/health
# 返回: {"code":200,"message":"success","data":"Apex Server is running"}

# 前端页面（通过 Nginx）
curl -I http://localhost
# 返回: HTTP/1.1 200 OK
```

### 5.3 常用命令

```bash
# 查看日志（所有服务）
docker compose -f deploy/docker-compose.yml logs -f

# 查看特定服务日志
docker compose -f deploy/docker-compose.yml logs -f app
docker compose -f deploy/docker-compose.yml logs -f nginx

# 重启单个服务
docker compose -f deploy/docker-compose.yml restart app

# 重新构建并启动（代码变更后）
docker compose -f deploy/docker-compose.yml up -d --build

# 停止所有服务
docker compose -f deploy/docker-compose.yml down

# 停止并删除数据卷（⚠️ 清除数据库）
docker compose -f deploy/docker-compose.yml down -v
```

### 5.4 环境变量

后端 [`application.yml`](java-apex-server/src/main/resources/application.yml:20) 通过环境变量配置数据库连接，`docker-compose.yml` 中已为 `app` 服务注入：

| 变量 | 默认值 | Docker Compose 覆盖值 | 说明 |
|------|--------|----------------------|------|
| `DB_HOST` | `localhost` | `mysql` | 数据库主机 |
| `DB_PORT` | `3306` | `3306` | 数据库端口 |
| `DB_USER` | `root` | `root` | 数据库用户名 |
| `DB_PASSWORD` | `root` | `root123` | 数据库密码 |
| `FILE_STORAGE_PATH` | `D:\data\apex-v1\file-share` | 未覆盖（使用默认） | 文件共享存储路径 |

> 生产环境建议使用 Docker secrets 或 `.env` 文件管理敏感信息，不要将密码硬编码在 compose 文件中。

---

## 6. 模式 B：腾讯云服务器部署

### 6.1 部署流程总览

```
  本地开发机 (Windows)                    腾讯云 CVM (129.211.9.238)
  ──────────────────                     ───────────────────────────
  ① Maven 编译 → target/*.jar
  ② npm run build → dist/               ──scp/ssh──→  /var/www/app-apex/
  ③ 执行 .bat 脚本                                                │
                                                          ④ Docker build & run
                                                          ⑤ Nginx reload
```

### 6.2 云端前置准备

在腾讯云服务器上一次性完成以下安装（如果尚未配置）：

```bash
# 安装 Docker
curl -fsSL https://get.docker.com | bash
systemctl enable docker && systemctl start docker

# 安装 MySQL 8.4
# （通过 apt/yum 安装，或使用 Docker）
# 确保创建数据库: CREATE DATABASE IF NOT EXISTS apex_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

# 安装 Nginx 1.27
apt install nginx
```

### 6.3 后端部署

执行 [`deploy/deploy_cloud_backend.bat`](deploy/deploy_cloud_backend.bat)，该脚本自动完成以下步骤：

| 步骤 | 操作 | 说明 |
|------|------|------|
| [1/4] | `mvn clean package -Dmaven.test.skip=true` | 在本地编译后端 JAR |
| [2/4] | `scp target/java-apex-server-1.0.0-SNAPSHOT.jar → app.jar` | 上传并重命名 JAR 到服务器 |
| [2/4] | `scp deploy/cloud-dockerfile → Dockerfile` | 上传云端专用 Dockerfile |
| [3/4] | `docker build -t app-apex .` | 在服务器构建镜像 |
| [3/4] | `docker stop/rm && docker run` | 重建容器并启动 |

**Docker Run 参数解析**（第38行）：

```bash
docker run -d \
  --name app-apex \                              # 容器名
  -p 8093:8093 \                                  # 端口映射（宿主机:容器）
  --restart always \                              # 自动重启
  --add-host=host.docker.internal:host-gateway \  # 容器内可访问宿主机 MySQL
  -v /data/apex/file-share:/data/apex/file-share \ # 文件共享目录挂载
  -e DB_HOST=host.docker.internal \               # 数据库指向宿主机
  -e DB_PORT=3306 \
  -e DB_USER=root \
  -e DB_PASSWORD=root \
  -e FILE_STORAGE_PATH=/data/apex/file-share \
  app-apex
```

关键架构决策：
- 后端容器通过 `host.docker.internal` 访问**宿主机上的 MySQL**（非容器内 MySQL）
- 文件共享目录挂载到宿主机 `/data/apex/file-share`，确保容器重启后文件不丢失

### 6.4 前端部署

执行 [`deploy/deploy_cloud_frontend.bat`](deploy/deploy_cloud_frontend.bat)，该脚本自动完成以下步骤：

| 步骤 | 操作 | 说明 |
|------|------|------|
| [1/3] | `npm run build` | 本地 Vite 生产构建 → `dist/` |
| [2/3] | `scp -r dist/* → /var/www/app-apex/dist/` | 上传静态资源 |
| [3/3] | `scp nginx-apex-tencent.conf → /etc/nginx/conf.d/app-apex.conf` | 上传 Nginx 配置 |
| [3/3] | `nginx -t && nginx -s reload` | 验证配置并热重载 |

**Nginx 配置** ([`deploy/nginx-apex-tencent.conf`](deploy/nginx-apex-tencent.conf))：

- 监听端口 **8083**（域名 `realapex.site` 走 80 端口时也兼容）
- `/` → 静态文件 `/var/www/app-apex/dist/`（Vue Router History 模式）
- `/api/` → 反向代理到 `http://127.0.0.1:8093/api/`（Docker 后端容器）

### 6.5 验证云端部署

```bash
# 后端健康检查
curl http://129.211.9.238:8093/api/health

# 前端页面
curl -I http://129.211.9.238:8083
# 返回: HTTP/1.1 200 OK

# 通过域名访问
curl -I http://realapex.site:8083
```

### 6.6 `cloud-dockerfile` 说明

[`deploy/cloud-dockerfile`](deploy/cloud-dockerfile) 与后端源码目录下的 [`java-apex-server/Dockerfile`](java-apex-server/Dockerfile) 不同：

| 特性 | 本地 Dockerfile | 云端 cloud-dockerfile |
|------|----------------|----------------------|
| 构建阶段 | **多阶段**（Maven 编译 + JRE 运行） | **单阶段**（仅 JRE 运行） |
| 基础镜像 | `maven:3.9-eclipse-temurin-21` → `eclipse-temurin:21-jre` | `eclipse-temurin:21-jre-alpine` |
| JAR 来源 | 镜像内 Maven 编译 | 本地编译 → scp 上传 `app.jar` |
| 镜像体积 | 较大（含 Maven 层） | 较小（Alpine JRE） |
| 用途 | 本地无 JDK/Maven 环境时一键构建 | 生产环境瘦镜像快速部署 |

---

## 7. 数据库初始化

### 7.1 创建数据库

```sql
CREATE DATABASE IF NOT EXISTS apex_db
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci
  COMMENT 'Apex 全栈平台主数据库';
```

> **模式 A**：`docker-compose.yml` 中 `MYSQL_DATABASE: apex_db` 会自动创建数据库，无需手动执行。  
> **模式 B**：需要在云端宿主机 MySQL 中手动执行上述 SQL。

### 7.2 Flyway 自动迁移

后端 [`application.yml`](java-apex-server/src/main/resources/application.yml:25) 中已开启 Flyway：

```yaml
spring:
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true
```

- 所有 DDL 变更通过 Flyway 脚本管理，存放在 [`java-apex-server/src/main/resources/db/migration/`](java-apex-server/src/main/resources/db/migration/)。
- **禁止手动改库**，这是项目强制规范。
- 首次启动时 Flyway 会自动创建 `flyway_schema_history` 版本追踪表。
- 命名格式：`V{version}__{description}.sql`，例如 `V1__create_wiki_document.sql`。

### 7.3 MySQL 8.4 配置建议

在服务器 `my.cnf` 中添加：

```ini
[mysqld]
character-set-server=utf8mb4
collation-server=utf8mb4_unicode_ci
default-time-zone=+08:00
innodb_buffer_pool_size=512M
max_connections=200
```

---

## 8. 本地开发模式（不依赖 Docker）

如果不想使用 Docker，可以直接在本地运行各个服务。

### 8.1 数据库 & Redis

确保本地 MySQL 8.4 和 Redis 7.2 已启动，执行 [7.1 节](#71-创建数据库) 的建库 SQL。

### 8.2 后端启动

```bash
cd java-apex-server

# 安装依赖并编译
mvn clean install -DskipTests

# 启动（开发模式，热重载需配合 spring-boot-devtools）
mvn spring-boot:run

# 或直接运行 JAR
java -jar target/java-apex-server-1.0.0-SNAPSHOT.jar
```

验证：

```bash
curl http://localhost:8093/api/health
# {"code":200,"message":"success","data":"Apex Server is running"}
```

### 8.3 前端启动

```bash
cd web-apex-vue

# 安装依赖
npm install

# 开发模式启动（热更新）
npm run dev
```

默认端口 **8083**，Vite 代理配置见 [`vite.config.ts`](web-apex-vue/vite.config.ts:16)：

- `/api` → `http://127.0.0.1:8093`（可通过环境变量 `VITE_API_TARGET` 覆盖）

访问：`http://localhost:8083`

### 8.4 前端生产构建

```bash
cd web-apex-vue

# 类型检查 + 构建
npm run build

# 产物在 dist/ 目录，可直接部署到 Nginx
```

---

## 9. 生产环境安全建议

| 项 | 当前值 | 建议生产值 |
|----|--------|------------|
| MySQL Root 密码 | `root` / `root123` | 强密码，通过环境变量注入 |
| Redis 密码 | 无 | 设置 `requirepass` |
| MySQL 端口 | 对外暴露 | 仅内网访问 |
| 文件共享路径 | `/data/apex/file-share` | 确保磁盘空间充足，定期备份 |
| Docker Restart Policy | `always`（模式B） | 保持，确保服务自动恢复 |
| Nginx 日志 | 默认 | 配置日志轮转 |

---

## 10. 常见问题排查

### 10.1 Docker Compose 启动失败

```bash
# 查看完整日志
docker compose -f deploy/docker-compose.yml logs app --tail=100

# 常见原因：
# 1. MySQL 未就绪 → 等待 healthcheck 通过（最多 50 秒）
# 2. 端口被占用 → netstat -ano | findstr "80 8093 3306"（Windows）
# 3. Maven 构建失败 → 检查 pom.xml 依赖是否完整、网络是否可访问 Maven 中央仓库
```

### 10.2 腾讯云端部署失败

```bash
# SSH 到服务器查看容器日志
ssh root@129.211.9.238 "docker logs app-apex --tail=100"

# 常见原因：
# 1. MySQL 连接失败 → 确认宿主机 MySQL 已启动，root 密码正确
# 2. 端口冲突 → netstat -tlnp | grep 8093
# 3. 容器未启动 → docker ps -a 查看退出状态
# 4. SSH 权限 → 确认已配置免密登录或输入密码
```

### 10.3 Flyway 迁移失败

```sql
-- 如果迁移脚本执行出错，检查版本历史
SELECT * FROM flyway_schema_history;

-- 如需修复，删除失败记录后重新启动
DELETE FROM flyway_schema_history WHERE success = 0;
```

### 10.4 前端代理 404

开发模式下前端无法请求后端 API 时：

1. 确认后端已启动：`curl http://localhost:8093/api/health`
2. 检查 Vite 代理目标：设置环境变量 `VITE_API_TARGET` 或使用默认 `http://127.0.0.1:8093`
3. Docker 模式下检查 Nginx 配置中 `proxy_pass http://app:8093` 是否正确

### 10.5 端口冲突

```bash
# Windows
netstat -ano | findstr "80 8083 8093 3306"

# Linux
lsof -i :80 -i :8083 -i :8093 -i :3306
```

修改端口：
- 后端：修改 [`application.yml`](java-apex-server/src/main/resources/application.yml:2) `server.port`
- 前端开发模式：修改 [`vite.config.ts`](web-apex-vue/vite.config.ts:13) `server.port`
- Docker Compose：修改 [`docker-compose.yml`](deploy/docker-compose.yml:9) `ports` 映射
- 云端 Nginx：修改 [`nginx-apex-tencent.conf`](deploy/nginx-apex-tencent.conf:10) `listen` 端口

### 10.6 云端文件共享不可用

确认容器内挂载正确：

```bash
ssh root@129.211.9.238 "docker exec app-apex ls /data/apex/file-share"
```

如果目录不存在，在宿主机创建：

```bash
ssh root@129.211.9.238 "mkdir -p /data/apex/file-share"
```

---

## 11. 目录结构速查

```
app-apex/
├── deploy/                               # 部署相关文件
│   ├── docker-compose.yml               # 模式 A：本地全家桶编排
│   ├── cloud-dockerfile                  # 模式 B：云端单阶段构建
│   ├── deploy_cloud_backend.bat          # 模式 B：后端部署脚本（Windows）
│   ├── deploy_cloud_frontend.bat         # 模式 B：前端部署脚本（Windows）
│   └── nginx-apex-tencent.conf          # 模式 B：云端 Nginx 配置
├── docs/
│   ├── DEPLOYMENT.md                     # 本文档
│   └── RELEASE_NODE.md
├── java-apex-server/                     # 后端服务
│   ├── Dockerfile                        # 模式 A：多阶段构建（Maven + JRE）
│   ├── pom.xml
│   └── src/main/
│       ├── resources/
│       │   ├── application.yml           # 主配置（端口 8093）
│       │   └── db/migration/             # Flyway SQL 脚本
│       └── java/com/apex/
│           ├── ApexApplication.java
│           ├── common/                   # Result, EmpContext, BusinessException
│           ├── config/                   # EmpContextConfig, MyBatisPlusConfig
│           └── controller/               # REST 控制器
└── web-apex-vue/                         # 前端应用
    ├── Dockerfile                        # 模式 A：多阶段构建（Node → Nginx）
    ├── nginx.conf                        # 模式 A：容器内 Nginx 配置
    ├── package.json
    ├── vite.config.ts                    # Vite 构建 + 开发代理（端口 8083）
    └── src/
        ├── api/                          # API 函数
        ├── components/                   # 组件
        ├── router/                       # Vue Router
        ├── utils/                        # request.ts, currentUser.ts
        └── views/                        # 页面组件
```

---

> 📌 **关键提醒**  
> 1. 模式 A（Docker Compose）适合本地开发，一条命令拉起全部服务。  
> 2. 模式 B（腾讯云）前端走 Nginx 端口 **8083**，后端 Docker 容器端口 **8093**。  
> 3. 数据库密码通过环境变量注入，不要硬编码在配置文件中。  
> 4. 所有 DDL 变更必须通过 Flyway 脚本，**禁止手动改库**。  
> 5. 云端文件共享目录 `/data/apex/file-share` 需提前创建并确保磁盘空间充足。  
> 6. Windows 下执行 `.bat` 部署脚本前，确保 `ssh` 和 `scp` 命令可用（建议使用 Git Bash 或 OpenSSH）。

# Apex 全栈平台 — 部署手册

> **适用版本**：1.0.0-SNAPSHOT  
> **最后更新**：2026-05-25  
> **适用环境**：Linux / macOS / Windows (WSL2 + Docker Desktop)

---

## 1. 系统架构概览

```
                  ┌──────────────┐
                  │   浏览器      │
                  │  (Port 80)    │
                  └──────┬───────┘
                         │
                  ┌──────▼───────┐
                  │  Nginx 1.27  │  ← 静态文件 + 反向代理 /api
                  │  (Port 80)   │
                  └──────┬───────┘
                         │ /api 代理
                  ┌──────▼───────┐
                  │ Spring Boot  │  ← Java 21 虚拟线程
                  │  (Port 8080) │
                  └──┬────────┬──┘
                     │        │
            ┌────────▼──┐ ┌──▼────────┐
            │ MySQL 8.4 │ │ Redis 7.2 │  ← 仅 Docker 内部网络暴露
            │ (Port 3306)│ │ (Port 6379)│
            └───────────┘ └───────────┘
```

### 核心组件

| 组件 | 版本 | 用途 |
|------|------|------|
| Nginx | 1.27-alpine | 前端静态文件托管 + `/api` 反向代理 |
| Java | 21 (Eclipse Temurin JRE) | 后端运行时，启用虚拟线程 |
| Spring Boot | 3.4.4 | 后端框架 |
| MySQL | 8.4 | 主数据库 (`apex_db`) |
| Redis | 7.2-alpine | 缓存 / Sa-Token 会话 |
| Node.js | 22-alpine (仅构建) | 前端 Vite 构建 |

### 网络隔离策略

MySQL 和 Redis **仅在 `apex-internal` 桥接网络内部暴露**，不对外网开放端口。  
生产环境建议在 [`docker-compose.yml`](docker-compose.yml) 中移除 `mysql.ports` 和 `redis.ports` 映射。

---

## 2. 环境要求

### 2.1 本地开发环境

| 工具 | 最低版本 | 验证命令 |
|------|---------|----------|
| JDK | 21+ | `java -version` |
| Maven | 3.9+ | `mvn -version` |
| Node.js | 22+ | `node -v` |
| npm | 10+ | `npm -v` |
| MySQL | 8.4 | `mysql --version` |
| Redis | 7.2+ | `redis-cli ping` |

### 2.2 Docker 部署环境

| 工具 | 最低版本 | 验证命令 |
|------|---------|----------|
| Docker | 24+ | `docker --version` |
| Docker Compose | 2.24+ | `docker compose version` |

---

## 3. 数据库初始化

### 3.1 创建数据库

```sql
CREATE DATABASE IF NOT EXISTS apex_db
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci
  COMMENT 'Apex 全栈平台主数据库';
```

### 3.2 创建用户（可选，建议生产环境使用）

```sql
CREATE USER 'apex'@'%' IDENTIFIED BY 'your_strong_password';
GRANT ALL PRIVILEGES ON apex_db.* TO 'apex'@'%';
FLUSH PRIVILEGES;
```

### 3.3 Flyway 自动迁移

后端的 [`application.yml`](java-apex-server/src/main/resources/application.yml:17) 中已开启 Flyway 自动迁移：

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
- 命名格式：`V{version}__{description}.sql`，例如 `V1__init_schema.sql`。

### 3.4 MySQL 8.4 配置建议

在 `my.cnf` 中添加以下优化参数：

```ini
[mysqld]
character-set-server=utf8mb4
collation-server=utf8mb4_unicode_ci
default-time-zone=+08:00
innodb_buffer_pool_size=512M
max_connections=200
```

---

## 4. Redis 配置

Redis 使用默认配置即可，无密码。用于：
- Sa-Token JWT 会话管理（`sa-token.token-style: tik`）

生产环境建议在 `redis.conf` 中设置密码，并在 [`application.yml`](java-apex-server/src/main/resources/application.yml:33) 中配置连接密码。

---

## 5. Docker 部署（推荐，一键启动）

### 5.1 快速启动

```bash
# 在项目根目录执行
docker compose up -d
```

此命令将自动：

1. 拉取 `mysql:8.4` 和 `redis:7.2-alpine` 镜像
2. 构建后端 `java-apex-server` 镜像（多阶段：Maven 编译 → JRE 运行）
3. 构建前端 `web-apex-vue` 镜像（多阶段：Node 编译 → Nginx 运行）
4. 创建 `apex-internal` 桥接网络
5. 按依赖顺序启动：MySQL → Redis → App → Nginx
6. 挂载 MySQL 数据卷 `mysql-data` 实现持久化

### 5.2 验证部署

```bash
# 检查容器状态
docker compose ps

# 预期输出
# NAME          STATUS
# apex-mysql    healthy
# apex-redis    running
# apex-server   running
# apex-nginx    running
```

```bash
# 健康检查
curl http://localhost/api/health
# 返回: {"code":200,"message":"success","data":"Apex Server is running"}

# 前端页面
curl -I http://localhost
# 返回: HTTP/1.1 200 OK
```

### 5.3 常用命令

```bash
# 查看日志（所有服务）
docker compose logs -f

# 查看特定服务日志
docker compose logs -f app
docker compose logs -f nginx

# 重启单个服务
docker compose restart app

# 重新构建并启动
docker compose up -d --build

# 停止所有服务
docker compose down

# 停止并删除数据卷（⚠️ 清除数据库）
docker compose down -v
```

### 5.4 服务端口

| 服务 | 端口 | 外部访问 | Docker 内部 |
|------|------|---------|------------|
| Nginx | 80 | ✅ | `nginx:80` |
| App (Spring Boot) | 8080 | ✅ (开发调试) | `app:8080` |
| MySQL | 3306 | ✅ (开发调试) | `mysql:3306` |
| Redis | 6379 | ❌ | `redis:6379` |

### 5.5 环境变量

后端 [`application.yml`](java-apex-server/src/main/resources/application.yml:12) 通过环境变量配置数据库连接：

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `DB_HOST` | `localhost` | 数据库主机（Docker 内为 `mysql`） |
| `DB_PORT` | `3306` | 数据库端口 |
| `DB_USER` | `root` | 数据库用户名 |
| `DB_PASSWORD` | `root123` | 数据库密码 |

在 [`docker-compose.yml`](docker-compose.yml:9) 中已为 `app` 服务设置正确值。  
生产环境建议使用 Docker secrets 或 `.env` 文件管理敏感信息。

---

## 6. 本地开发模式部署

### 6.1 数据库 & Redis

确保本地 MySQL 8.4 和 Redis 7.2 已启动。

```bash
# MySQL (如果使用本地安装)
# macOS: brew services start mysql@8.4
# Linux: systemctl start mysqld
# Windows: net start MySQL84

# Redis
# macOS: brew services start redis
# Linux: systemctl start redis
# Windows: redis-server.exe
```

执行 [3.1 节](#31-创建数据库) 的建库 SQL，然后 Flyway 会在应用启动时自动建表。

### 6.2 后端启动

```bash
cd java-apex-server

# 安装依赖并编译
mvn clean install -DskipTests

# 启动（开发模式）
mvn spring-boot:run

# 或直接运行 JAR
java -jar target/java-apex-server-1.0.0-SNAPSHOT.jar
```

验证：
```bash
curl http://localhost:8080/api/health
# {"code":200,"message":"success","data":"Apex Server is running"}
```

### 6.3 前端启动

```bash
cd web-apex-vue

# 安装依赖
npm install

# 开发模式启动（热更新）
npm run dev
```

默认端口 `5173`，Vite 已配置代理：`/api` → `http://127.0.0.1:8080`（通过环境变量 `VITE_API_TARGET` 控制，见 [`vite.config.ts`](web-apex-vue/vite.config.ts:16)）。

访问：`http://localhost:5173`

### 6.4 前端生产构建

```bash
cd web-apex-vue

# 类型检查 + 构建
npm run build

# 产物在 dist/ 目录，可直接部署到 Nginx
```

---

## 7. 生产环境部署清单

### 7.1 安全加固

| 项 | 当前值 | 建议生产值 |
|----|--------|------------|
| JWT 密钥 | `apex-jwt-secret-key-change-in-production` | 随机 64 位字符串 |
| MySQL Root 密码 | `root123` | 强密码 |
| Redis 密码 | 无 | 设置 `requirepass` |
| MySQL 端口 | 对外 3306 | 移除 `ports` 映射 |
| CORS | 未限制 | 配置允许的 Origin |

修改 JWT 密钥（[`application.yml`](java-apex-server/src/main/resources/application.yml:41)）：

```yaml
sa-token:
  jwt-secret-key: ${JWT_SECRET:your-64-char-random-secret}
```

### 7.2 性能优化

```yaml
# application-prod.yml
mybatis-plus:
  configuration:
    log-impl: org.apache.ibatis.logging.nologging.NoLoggingImpl  # 关闭 SQL 日志

server:
  tomcat:
    threads:
      max: 200  # 虚拟线程下此配置影响较小
```

### 7.3 健康检查端点

| 端点 | 认证 | 说明 |
|------|------|------|
| `GET /api/health` | 无 | 服务存活检查（已在 SaToken 过滤器中放行） |

---

## 8. 常见问题排查

### 8.1 容器启动失败

```bash
# 查看完整日志
docker compose logs app --tail=100

# 常见原因：
# 1. MySQL 未就绪 → 等待 healthcheck 通过（最多 50 秒）
# 2. 端口被占用 → lsof -i :8080 (macOS) / netstat -ano | findstr 8080 (Win)
# 3. Maven 构建失败 → 检查 pom.xml 依赖是否完整
```

### 8.2 Flyway 迁移失败

```sql
-- 如果迁移脚本执行出错，检查版本历史
SELECT * FROM flyway_schema_history;

-- 如需修复，删除失败记录
DELETE FROM flyway_schema_history WHERE success = 0;
```

然后修改 SQL 脚本后重新启动。

### 8.3 前端代理 404

开发模式下前端无法请求后端 API 时：

1. 确认后端已启动：`curl http://localhost:8080/api/health`
2. 检查 Vite 代理目标：设置环境变量 `VITE_API_TARGET` 或使用默认 `http://127.0.0.1:8080`
3. Docker 模式下检查 Nginx 配置中 `proxy_pass http://app:8080` 是否正确

### 8.4 数据库连接拒绝

```
java.sql.SQLException: Access denied for user 'root'
```

- Docker 环境：确认 `docker-compose.yml` 中 `DB_HOST: mysql`，密码与 `MYSQL_ROOT_PASSWORD` 一致
- 本地环境：确认 MySQL 已启动，`application.yml` 中用户名密码正确

### 8.5 端口冲突

```bash
# 查找占用端口的进程
# Linux/macOS
lsof -i :8080 -i :3306 -i :80

# Windows
netstat -ano | findstr "8080 3306 80"
```

修改端口：

- 后端：修改 [`application.yml`](java-apex-server/src/main/resources/application.yml:2) `server.port`
- 前端开发模式：修改 [`vite.config.ts`](web-apex-vue/vite.config.ts:13) `server.port`
- Docker：修改 [`docker-compose.yml`](docker-compose.yml:8) `ports` 映射

---

## 9. 目录结构速查

```
app-apex/
├── docker-compose.yml            # 一键部署编排
├── docs/
│   └── DEPLOYMENT.md             # 本文档
├── java-apex-server/             # 后端服务
│   ├── Dockerfile                # 多阶段构建 (JDK 21 → JRE 21)
│   ├── pom.xml                   # Maven 依赖
│   └── src/main/
│       ├── resources/
│       │   ├── application.yml   # 主配置
│       │   └── db/migration/     # Flyway SQL 脚本
│       └── java/com/apex/
│           ├── ApexApplication.java
│           ├── common/           # Result, BusinessException, GlobalExceptionHandler
│           ├── config/           # SaToken, MyBatisPlus, Security
│           └── controller/       # REST 控制器
└── web-apex-vue/                 # 前端应用
    ├── Dockerfile                # 多阶段构建 (Node → Nginx)
    ├── nginx.conf                # Nginx 反向代理配置
    ├── package.json              # NPM 依赖
    ├── vite.config.ts            # Vite 构建 + 代理配置
    └── src/
        ├── api/                  # API 函数
        ├── components/Layout/    # 核心布局组件
        ├── router/               # Vue Router
        ├── utils/                # request.ts, currentUser.ts
        └── views/                # 页面组件
```

---

## 10. CI/CD 建议

### GitHub Actions 示例骨架

```yaml
name: Build & Deploy
on:
  push:
    branches: [main]

jobs:
  backend:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
      - run: mvn -f java-apex-server clean package -DskipTests

  frontend:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with:
          node-version: '22'
      - run: cd web-apex-vue && npm ci && npm run build

  docker:
    needs: [backend, frontend]
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - run: docker compose build
      - run: docker compose push  # 需提前配置 registry
```

---

> 📌 **关键提醒**  
> 1. 生产环境务必将 [`jwt-secret-key`](java-apex-server/src/main/resources/application.yml:41) 替换为安全随机值。  
> 2. 数据库密码不要硬编码，通过环境变量或 Docker Secrets 注入。  
> 3. Redis 建议设置 `requirepass`，并在 `application.yml` 中配置。  
> 4. 所有 DDL 变更必须通过 Flyway 脚本，**禁止手动改库**。

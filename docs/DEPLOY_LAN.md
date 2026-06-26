# Apex 内网 Windows 部署指南

> 适用环境：Windows 11 + Nginx + JDK 21

---

## 1. 概述

本地部署分为**后端服务**和**前端 Web**两个独立模块：

| 模块 | 构建脚本 | 产物目录 | 运行方式 |
|------|---------|---------|---------|
| 后端 | [`deploy/build-server-lan.bat`](../deploy/build-server-lan.bat) | `bin/apex-server/` | WinSW 注册为 Windows 服务 |
| 前端 | [`deploy/build-web-lan.bat`](../deploy/build-web-lan.bat) | `bin/apex-web/` | Nginx 静态托管 |

---

## 2. 前置环境

| 工具 | 版本 | 验证命令 |
|------|------|---------|
| JDK | 21+ | `java -version` |
| Maven | 3.9+ | `mvn -version` |
| Node.js | 22+ | `node -v` |
| npm | 10+ | `npm -v` |
| Nginx | 1.27+ | `nginx -v` |

---

## 3. 构建步骤

### 3.1 构建后端

双击运行或终端执行：

```bat
deploy\build-server-lan.bat
```

产物输出到 `bin\apex-server\`：

```
bin\apex-server\
├── apex-server.jar       ← Spring Boot JAR（JVM: -Xms256m -Xmx512m）
├── ApexServer.xml        ← WinSW 服务定义（含 DB 等环境变量）
├── startServer.bat       ← 管理员运行，安装 + 启动服务
└── stopServer.bat        ← 管理员运行，停止 + 卸载服务
```

### 3.2 构建前端

双击运行或终端执行：

```bat
deploy\build-web-lan.bat
```

产物输出到 `bin\apex-web\`：

```
bin\apex-web\
├── index.html
├── apex-logo.svg
└── assets/
    ├── *.js
    └── *.css
```

---

## 4. 部署步骤

### 4.1 部署后端服务

**① 复制到部署目录**

```bat
xcopy /e /y bin\apex-server\* D:\app\apex-server\
```

**② 以管理员身份运行**

```bat
D:\app\apex-server\startServer.bat
```

此脚本会执行：
- `ApexServer.exe install` — 注册 Windows 服务
- `ApexServer.exe start` — 启动服务

**③ 验证后端**

```
http://localhost:8093/api/health
```

返回 `{"code":200,"data":"Apex Server is running"}` 即成功。

### 4.2 部署前端静态文件

**① 复制到部署目录**

```bat
xcopy /e /y bin\apex-web\* D:\app\apex-web\
```

**② 确认 Nginx 配置已就位**

Nginx 配置已在 `D:/nginx/conf/conf.d/nginx-apex-lan.conf`（由 `deploy/nginx-apex-lan.conf` 复制而来）。

该配置：

```nginx
server {
    listen       8083;
    server_name  _;

    root   D:/app/apex-web;
    index  index.html;

    location / {
        try_files $uri $uri/ /index.html;   # Vue History 模式
    }

    location /api/ {
        proxy_pass http://127.0.0.1:8093/api/;
        proxy_buffering off;                # AI 流式接口不缓冲
        proxy_cache off;
    }
}
```

**③ 重载 Nginx**

将 `deploy/` 下的 Nginx 管理脚本复制到 `D:\nginx\`，双击运行：

```bat
copy deploy\nginx-start.bat  D:\nginx\
copy deploy\nginx-reload.bat D:\nginx\
copy deploy\nginx-stop.bat   D:\nginx\
```

然后执行热重载：

```bat
D:\nginx\nginx-reload.bat
```

**④ 验证前端**

```
http://localhost:8083
```

---

## 5. 最终部署目录结构

```
D:\app\
├── nginx\
│   ├── nginx.exe
│   └── conf\
│       ├── nginx.conf                     ← 末尾有 include conf.d/*.conf;
│       └── conf.d\
│           └── nginx-apex-lan.conf        ← 监听 8083
├── apex-server\
│   ├── ApexServer.exe                     ← WinSW（构建脚本自动复制）
│   ├── ApexServer.xml                     ← 服务定义
│   ├── apex-server.jar                    ← Spring Boot JAR
│   ├── startServer.bat
│   └── stopServer.bat
└── apex-web\                              ← Nginx root
    ├── index.html
    └── assets\
```

---

## 6. 日常运维

### 后端服务

```bat
:: 停止
D:\app\apex-server\stopServer.bat

:: 启动（重新构建后覆盖 JAR，再启动）
D:\app\apex-server\startServer.bat

:: 查看状态
D:\app\apex-server\ApexServer.exe status

:: 查看日志
D:\app\apex-server\*.log
```

### 前端静态文件

重新构建前端后，只需重新复制并重载 Nginx：

```bat
deploy\build-web-lan.bat
xcopy /e /y bin\apex-web\* D:\app\apex-web\
D:\nginx\nginx-reload.bat
```

### Nginx

将 `deploy/` 下脚本复制到 `D:\nginx\` 后，双击使用：

| 脚本 | 操作 |
|------|------|
| `nginx-start.bat` | 启动 Nginx |
| `nginx-reload.bat` | 校验配置 + 热重载 |
| `nginx-stop.bat` | 优雅停止（失败则快速停止）
```

---

## 7. 端口一览

| 服务 | 端口 | 用途 |
|------|------|------|
| Nginx | 8083 | 前端入口 + API 反代 |
| Spring Boot | 8093 | 后端 API（仅 Nginx 访问，不直接对外） |
| MySQL | 3306 | 数据库（内网 `22.188.9.144`） |

---

## 8. 故障排查

| 问题 | 检查方法 |
|------|---------|
| 后端启动失败 | 查看 `D:\app\apex-server\*.log`，确认 MySQL 可达 |
| 前端 404 | `nginx -t` 验证配置，确认 `D:\app\apex-web\` 目录存在 |
| API 502 | 确认后端 `localhost:8093` 已启动，`nginx -s reload` 后重试 |
| AI 流式卡住 | 确认 Nginx 配置中 `proxy_buffering off` |

# Exporter Agent 部署指南

> 适用版本：windows_exporter 0.31.7 / node_exporter 1.11.1
> 最后更新：2026-07-02

---

## 1. 概述

Apex 容量监控通过 Prometheus Exporter 采集服务器运行指标。本项目提供开箱即用的 Exporter 部署包，包含 **指标暴露** 和 **端口监听采集** 两个独立模块。

### 1.1 目录结构

```
deploy/
├── exporter-agent/                               # Windows 部署包
│   ├── windows_exporter-0.31.7-amd64.exe         # Prometheus Windows Exporter
│   ├── ExporterAgent.exe                         # WinSW 服务包装器
│   ├── ExporterAgent.xml                         # WinSW 服务定义
│   ├── config.yaml                               # Exporter 配置 (collectors)
│   ├── collect-ports.ps1                         # 端口采集脚本 (PowerShell)
│   ├── install-exporter.bat                      # 安装 Exporter 服务
│   ├── uninstall-exporter.bat                    # 卸载 Exporter 服务
│   ├── install-port-collector.bat                # 安装端口采集计划任务
│   └── uninstall-port-collector.bat              # 卸载端口采集计划任务
│
├── exporter-linux/                               # Linux 部署包
│   ├── node_exporter-1.11.1.linux-amd64.tar.gz   # Prometheus Node Exporter
│   ├── node_exporter.service                     # systemd 单元模板
│   ├── collect-ports.sh                          # 端口采集脚本 (Bash)
│   ├── collect-ports.service                     # systemd oneshot 服务
│   ├── collect-ports.timer                       # systemd 定时器 (每 10s)
│   ├── install-exporter.sh                       # 安装 Exporter 服务
│   ├── uninstall-exporter.sh                     # 卸载 Exporter 服务
│   ├── install-port-collector.sh                 # 安装端口采集定时器
│   └── uninstall-port-collector.sh               # 卸载端口采集定时器
│
├── windows_exporter-0.31.7-amd64.msi             # MSI 安装包 (备用)
└── WinSW-x64.exe                                 # WinSW 原始文件 (备用)
```

### 1.2 架构说明

```
┌──────────────────────────────────────────────────────┐
│                    Apex 后端                          │
│         MonitorService.fetchMetrics()                │
│         HTTP GET /metrics (每 3s)                      │
└──────────────────────┬───────────────────────────────┘
                       │
          ┌────────────┴────────────┐
          ▼                         ▼
┌──────────────────┐     ┌──────────────────┐
│  Windows Exporter │     │  Linux Exporter   │
│  (WinSW 服务)      │     │  (systemd 服务)    │
│  port 9182        │     │  port 9100        │
│                    │     │                    │
│  ┌──────────────┐ │     │  ┌──────────────┐ │
│  │ textfile/     │ │     │  │ textfile/     │ │
│  │ *.prom 文件   │◄│     │  │ *.prom 文件   │◄│
│  └──────────────┘ │     │  └──────────────┘ │
│        ▲          │     │        ▲          │
└────────┼──────────┘     └────────┼──────────┘
         │                         │
┌────────┴──────────┐     ┌───────┴──────────┐
│ collect-ports.ps1 │     │ collect-ports.sh │
│ 计划任务 (每 10s)   │     │ systemd timer   │
│                    │     │ (每 10s)         │
└────────────────────┘     └──────────────────┘
```

**两个组件完全解耦**，可独立安装/卸载。Exporter 负责暴露 `/metrics` 端点，端口采集器负责定时写入 `.prom` 文件。

---

## 2. Windows 部署

### 2.1 部署路径

> **固定路径**：必须部署到 `D:\app\exporter-agent\`

将 `deploy/exporter-agent/` 目录整体复制到 `D:\app\exporter-agent\`。

### 2.2 安装 Exporter 服务

以**管理员身份**运行：

```
D:\app\exporter-agent\install-exporter.bat
```

脚本自动执行：
1. 创建 `textfile\` 目录
2. 通过 WinSW 注册 Windows 服务（服务名：`ExporterAgent`）
3. 启动服务

验证：
```powershell
# 检查服务状态
Get-Service ExporterAgent

# 拉取指标
curl http://localhost:9182/metrics
```

### 2.3 安装端口采集器

以**管理员身份**运行：

```
D:\app\exporter-agent\install-port-collector.bat
```

脚本自动执行：
1. 注册 Windows 计划任务 `Apex Port Collector`（每分钟触发，每 10 秒重复）
2. 立即执行一次采集

验证：
```powershell
# 查看计划任务
schtasks /query /tn "Apex Port Collector"

# 查看采集结果
type D:\app\exporter-agent\textfile\listening_ports.prom

# 通过 Exporter 查看
curl http://localhost:9182/metrics | findstr windows_listening_port
```

### 2.4 卸载

```powershell
# 卸载端口采集器（可选，保留 Exporter）
D:\app\exporter-agent\uninstall-port-collector.bat

# 卸载 Exporter 服务（可选，保留采集器）
D:\app\exporter-agent\uninstall-exporter.bat
```

两个卸载脚本互相独立，卸载后 `D:\app\exporter-agent\` 目录及其文件保留，可手动删除。

### 2.5 Windows 文件清单

| 文件 | 说明 |
|------|------|
| `windows_exporter-0.31.7-amd64.exe` | Prometheus Windows Exporter，保留版本号 |
| `ExporterAgent.exe` | WinSW 服务包装器（与 `ExporterAgent.xml` 同名配对） |
| `ExporterAgent.xml` | 服务定义：id/name 均为 `ExporterAgent`，`services.msc` 中可识别 |
| `config.yaml` | 启用 collectors: cpu,memory,disk,net,os,service,system,tcp,process,textfile |
| `collect-ports.ps1` | PowerShell 端口采集，`Get-NetTCPConnection` / `Get-NetUDPEndpoint` |

---

## 3. Linux 部署

### 3.1 部署路径

> **推荐路径**：`/opt/exporter-linux/`（脚本自动检测实际路径，不强制）

将 `deploy/exporter-linux/` 目录整体复制到目标服务器：

```bash
scp -r deploy/exporter-linux/ root@<server>:/opt/exporter-linux/
```

### 3.2 安装 Exporter 服务

```bash
cd /opt/exporter-linux
sudo ./install-exporter.sh
```

脚本自动执行：
1. 从 `node_exporter-1.11.1.linux-amd64.tar.gz` 解压二进制
2. 创建 `textfile/` 目录
3. 安装 systemd 单元 `/etc/systemd/system/node_exporter.service`
4. 启用并启动服务（监听 `0.0.0.0:9100`）

验证：
```bash
# 服务状态
systemctl status node_exporter

# 拉取指标
curl -s http://localhost:9100/metrics | head -20
```

### 3.3 安装端口采集器

```bash
cd /opt/exporter-linux
sudo ./install-port-collector.sh
```

脚本自动执行：
1. 赋予 `collect-ports.sh` 执行权限
2. 安装 systemd oneshot 服务 `/etc/systemd/system/collect-ports.service`
3. 安装 systemd timer `/etc/systemd/system/collect-ports.timer`（每 10 秒）
4. 启用并启动 timer，立即执行一次采集

验证：
```bash
# timer 状态
systemctl status collect-ports.timer

# 查看采集结果
cat /opt/exporter-linux/textfile/listening_ports.prom

# 通过 Exporter 查看
curl -s http://localhost:9100/metrics | grep node_listening_port
```

### 3.4 卸载

```bash
# 卸载端口采集器（可选）
sudo ./uninstall-port-collector.sh

# 卸载 Exporter 服务（可选）
sudo ./uninstall-exporter.sh
```

### 3.5 Linux 文件清单

| 文件 | 说明 |
|------|------|
| `node_exporter-1.11.1.linux-amd64.tar.gz` | 原始发行包，install 脚本自动解压 |
| `node_exporter` | 解压后的二进制（install 后生成） |
| `node_exporter.service` | systemd 单元模板，含 `--collector.textfile.directory` |
| `collect-ports.sh` | Bash 端口采集，`ss -tlnp` / `ss -ulnp` |
| `collect-ports.service` | systemd oneshot 服务，`__BASE__` 占位符由 install 脚本替换 |
| `collect-ports.timer` | systemd 定时器，`OnUnitActiveSec=10` |

---

## 4. 端口采集指标规格

### 4.1 指标名称

| 平台 | 指标名 | 类型 |
|------|--------|------|
| Windows | `windows_listening_port` | gauge |
| Linux | `node_listening_port` | gauge |

### 4.2 标签 (Labels)

| 标签 | 说明 | 示例 |
|------|------|------|
| `port` | 监听端口号 | `"22"`, `"3306"`, `"9182"` |
| `protocol` | 协议类型 | `"tcp"`, `"udp"` |
| `process` | 进程名称 | `"sshd"`, `"mysqld"`, `"java"` |
| `pid` | 进程 ID | `"1234"` |
| `local_address` | 监听地址 | `"0.0.0.0"`, `"127.0.0.1"` |

### 4.3 值含义

- `1` — 端口正在监听
- 端口停止后，对应 label 组合从指标中消失（指标行消失）

### 4.4 采集频率

| 层级 | 间隔 | 说明 |
|------|------|------|
| 端口采集脚本 | 10 秒 | 写入 `.prom` 文件 |
| Apex 后端抓取 | 3 秒 | `MonitorService.fetchMetrics()` HTTP GET `/metrics` |
| 前端卡片刷新 | 3 秒（默认） | `MachineCard` `refreshInterval`，可配置 |

采集脚本每 10 秒更新 `.prom` 文件，Exporter 被后端拉取时读取最新文件内容，前端卡片通过 Apex API 获得最新端口状态后更新 UI。

---

## 5. 与 Apex 监控系统集成

### 5.1 后端

[`MetricDictionary.java`](../java-apex-server/src/main/java/com/apex/config/MetricDictionary.java) 中已添加端口指标前缀规则：

```java
PREFIX_RULES.put("node_listening_port",   MetricCategory.PORT);
PREFIX_RULES.put("windows_listening_port",MetricCategory.PORT);
```

端口数据自动流转：`Exporter /metrics` → `fetchMetrics()` → `parseAllMetrics()` → `inferCategory(PORT)` → 前端展示。

### 5.2 使用方式

1. 部署成功后，打开 Apex "容量监控" 页面
2. 点击目标机器的"全量指标"按钮
3. 展开 **PORT** 分类，查看所有监听端口指标
4. 对感兴趣的端口点击"定制"，添加到卡片上
5. 卡片上显示为 🟢 绿灯（在线）/ 🔴 红灯（已丢失）

---

## 6. 注意事项

| 项目 | 说明 |
|------|------|
| **管理员权限** | Windows 安装脚本必须以管理员运行（注册服务/计划任务）；Linux 需要 `sudo`（注册 systemd） |
| **路径要求** | 路径不要含中文或空格 |
| **防火墙** | 确保 `9182`（Windows）/ `9100`（Linux）端口对 Apex 后端所在机器可达 |
| **Docker 环境** | 腾讯云 Docker 部署的 node_exporter 已在 `docker-compose.yml` 中配置，无需额外部署，仅需挂载 `textfile` 目录并部署端口采集脚本 |
| **Windows UDP** | `Get-NetUDPEndpoint` 返回所有已绑定 UDP 端点，可能包含临时端口 |
| **Linux 权限** | `ss -tlnp` 需要 root 才能显示进程名，systemd 服务以 root 运行 |

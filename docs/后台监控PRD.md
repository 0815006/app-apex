你之前用 Exporter 采集操作系统和 MySQL 数据的思路非常对。现在要监控 **Spring Boot（Java后台）**，核心逻辑是一模一样的：**让 Java 应用自己把 JVM 和业务指标暴露出来，然后由 Prometheus 等工具去 Pull（拉取）数据。**

先说你遇到的 PowerShell 弹窗问题：**Java 本身不会直接弹出 PowerShell 窗口。** 这种弹窗通常有两种可能：

1. 项目代码中写了调用 PowerShell 或 Windows 批处理（`.bat`）的脚本（比如定时任务执行了系统命令，执行报错时弹窗）。
2. Windows 系统级别的任务计划或某些守护进程检测到 Java 进程异常（如 OOM 内存溢出、CPU 飙高）触发了告警脚本。

要彻底解决并实现对 Java 后台的精准监控，可以按照以下路线演进：

---

## 方案一：标准轻量方案（无缝对接你现有的 Exporter 体系）

既然你已经有了 Prometheus / Exporter 采集架构，监控 Spring Boot 最优雅的方式就是直接使用 Spring 原生支持的 **Actuator + Micrometer**。

### 1. Spring Boot 项目引入依赖

在 Java 项目的 `pom.xml` 中引入这两个依赖（它扮演了原本独立 Exporter 的角色）：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>

```

### 2. 配置暴露 Endpoint

在 `application.yml` 中开启 Prometheus 端点：

```yaml
management:
  endpoints:
    web:
      exposure:
        include: prometheus,health,info
  metrics:
    tags:
      application: ${spring.application.name:my-java-app} # 区分不同应用

```

启动应用后，访问 `http://localhost:8080/actuator/prometheus`，就会看到标准的 Prometheus 格式指标（包含堆内存、GC 次数、线程数、HTTP 接口 QPS 等）。

---

## 方案二：无侵入 Agent 方案（不用改 Java 代码）

如果你不想修改 Java 源码或重新打包，可以使用 Prometheus 官方提供的 **jmx_exporter**。

1. 下载 `jmx_prometheus_javaagent.jar`。
2. 在 Spring Boot 启动命令中加入 Agent 参数：
```bash
java -javaagent:/path/to/jmx_prometheus_javaagent.jar=9104:/path/to/config.yaml -jar your-app.jar

```


3. 这样就会在 `9104` 端口单独暴露 JMX 监控数据。

---

## 核心监控指标（排查 PowerShell 与 JVM 问题）

引入采集后，你在前端监控页面或 Dashboard 上重点看以下 4 类指标：

| 监控维度 | 指标名称 (Prometheus Metric) | 排查价值 / 告警阈值 |
| --- | --- | --- |
| **堆内存 (Heap)** | `jvm_memory_used_bytes{area="heap"}` | 观察内存使用率，若持续升至 85%+ 且 GC 后不下降，可能存在**内存泄漏**。 |
| **垃圾回收 (GC)** | `jvm_gc_pause_seconds_sum` | GC 频繁或 Pause 时间过长会导致线程卡顿、响应超时，甚至引发系统守护脚本报错。 |
| **线程状态** | `jvm_threads_live_threads` / `blocked_threads` | 死锁或线程池爆满会导致接口无响应、进程假死。 |
| **CPU 占用** | `process_cpu_usage` | 配合系统层面的 CPU 监控，定位是否有死循环或高并发冲顶。 |

---

## 排查当前问题的应急三步法

为了搞清楚那个 PowerShell 弹窗到底是什么，建议立刻做这三步：

1. **查 Java 启动参数**：检查启动命令是否设置了 `-XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=...`。如果是 OOM 触发了某种系统的脚本响应，日志里会有 `OutOfMemoryError`。
2. **全局搜索代码**：在 Spring Boot 项目中搜索 `Runtime.getRuntime().exec` 或 `ProcessBuilder`，排查是否有代码在后台定时调用 Windows 脚本。
3. **日志排查**：检查 Spring Boot 输出的 `logs` 文件，重点看弹窗对应时间点附近有没有 `Exception` 或 `Error`。
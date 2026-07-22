# Java/JVM 监控接入方案 — 实施计划（双模式：Actuator + JMX Exporter）

> **目标**：将 Java 应用的 JVM 运行状态接入现有监控体系。同时支持两种数据源：
> - **Actuator**：Spring Boot 项目内置 `/actuator/prometheus`（自己的项目，已部署）
> - **JMX Exporter**：`-javaagent` 无侵入挂载 `/metrics`（别人的项目，无需改代码）
>
> **核心洞察**：两者输出的 Prometheus Text 指标名完全一致（均为 Micrometer 标准），解析代码 100% 可复用。
>
> **技术栈**：Spring Boot 3.4+ (Java 21 虚拟线程) + Vue 3 (Vite 6, TypeScript, Element Plus) + MySQL 8.4

---

## 1. 设计决策：两个子类型，零额外字段

### 1.1 为什么用子类型而不是路径字段

| 方案 | 用户体验 | 实现复杂度 | 维护性 |
|------|---------|-----------|--------|
| ❌ 一个 JAVA + 手动填路径 | 需理解 `/actuator/prometheus` vs `/metrics` | 需新增 DB 列 + DTO 字段 | 一般 |
| ✅ **两个子类型自动决定** | 下拉选即可，无需理解路径概念 | 无需改表结构，仅枚举扩展 | 极佳 |

### 1.2 子类型定义

| osType 值 | 下拉显示 | 默认端口 | 端点路径 | 适用场景 |
|-----------|---------|---------|---------|---------|
| `JAVA_ACTUATOR` | Java (Actuator) | **8080** | `/actuator/prometheus` | 自己的 / 别人的 Spring Boot 项目 |
| `JAVA_JMX` | Java (JMX Exporter) | **9104** | `/metrics` | 非 Spring Boot 的 Java 应用，或无法改代码的项目 |

### 1.3 架构流程图

```mermaid
flowchart TD
    A["前端添加机器下拉"] -->|Java (Actuator)| B["osType=JAVA_ACTUATOR<br/>默认端口 8080"]
    A -->|Java (JMX Exporter)| C["osType=JAVA_JMX<br/>默认端口 9104"]
    B --> D[fetchMetrics]
    C --> D
    D -->|根据 osType 自动选择路径| E["JAVA_ACTUATOR → /actuator/prometheus<br/>JAVA_JMX → /metrics"]
    E --> F["parseJvmHeapUsage / parseJvmGc..."]
    F --> G["MonitorRealtimeVO<br/>同一组 JVM 字段"]

    style B fill:#67C23A,color:#fff
    style C fill:#409EFF,color:#fff
    style F fill:#E6A23C,color:#fff
```

---

## 2. 变更范围总览

| 序号 | 交付物 | 类型 | 说明 |
|------|--------|------|------|
| 1 | `V11__add_java_os_type.sql` | Flyway 迁移 | 扩展 os_type 列注释 |
| 2 | `MonitorService.java` | Service | `fetchMetrics()` 根据 osType 选路径，新增 JAVA 分支 + JVM 解析方法 |
| 3 | `MonitorRealtimeVO.java` | Record | 新增 8 个 JVM 专用字段 |
| 4 | `MetricDictionary.java` | 配置 | 新增 `jvm_*` / `process_*` 前缀归类 + 中文翻译 |
| 5 | `monitor.ts` (types) | 前端类型 | osType 新增两个 JAVA 子类型，MonitorRealtime 新增 JVM 字段 |
| 6 | `MachineFormDialog.vue` | 前端组件 | 下拉新增两个 JAVA 选项，自动切换默认端口 |
| 7 | `MachineCard.vue` | 前端组件 | 新增 JAVA 专用卡片模式 |

> **无需变更**：`MonitorController.java`、`MonitorSampleScheduler.java`、`MonitorMachine.java`、`MonitorMachineDTO.java`、`MonitorMachineMapper.java`。**无需新增任何数据库列。**

---

## 3. 数据库变更 (Flyway V11)

### 3.1 迁移脚本 — 仅扩展枚举注释

```sql
-- V11__add_java_os_type.sql
-- =============================================
-- V11: 扩展 os_type 支持 JAVA_ACTUATOR 和 JAVA_JMX
-- 无需新增列，路径在后端代码中根据 osType 自动选择
-- =============================================
ALTER TABLE `monitor_machine`
    MODIFY COLUMN `os_type` VARCHAR(20) NOT NULL
    COMMENT 'WINDOWS、LINUX、MYSQL、JAVA_ACTUATOR 或 JAVA_JMX';
```

**文件位置**：
```
java-apex-server/src/main/resources/db/migration/V11__add_java_os_type.sql
```

### 3.2 实体无需修改

[`MonitorMachine.java`](java-apex-server/src/main/java/com/apex/entity/MonitorMachine.java) 的 `osType` 字段和 `exporterPort` 字段均无需改动，也无需新增任何字段。

---

## 4. 后端实现计划

### 4.1 fetchMetrics — 根据 osType 自动选择路径

[`MonitorService.java`](java-apex-server/src/main/java/com/apex/service/MonitorService.java) 第 146 行，将硬编码的 `/metrics` 替换为根据 osType 动态选择：

```java
// 改造前：
String url = String.format("http://%s:%d/metrics", machine.getIp(), machine.getExporterPort());

// 改造后：根据 osType 自动选择端点路径
private String buildMetricsUrl(MonitorMachine machine) {
    String path = switch (machine.getOsType().toUpperCase()) {
        case "JAVA_ACTUATOR" -> "/actuator/prometheus";
        default -> "/metrics";  // JAVA_JMX, WINDOWS, LINUX, MYSQL 都走 /metrics
    };
    return String.format("http://%s:%d%s", machine.getIp(), machine.getExporterPort(), path);
}
```

### 4.2 JVM 核心监控指标

| # | 指标类别 | Prometheus 指标名 | 计算方式 | Actuator | JMX Exporter |
|---|---------|-------------------|---------|----------|-------------|
| 1 | 堆内存使用率 | `jvm_memory_used_bytes{area="heap"}` / `jvm_memory_max_bytes{area="heap"}` | `used / max * 100` | ✅ | ✅ |
| 2 | GC 累计暂停 | `jvm_gc_pause_seconds_sum` | 直接取值 | ✅ | ✅ |
| 3 | GC 累计次数 | `jvm_gc_pause_seconds_count` | 直接取值 | ✅ | ✅ |
| 4 | 活动线程数 | `jvm_threads_live_threads` | 直接取值 | ✅ | ✅ |
| 5 | 守护线程数 | `jvm_threads_daemon_threads` | 直接取值 | ✅ | ✅ |
| 6 | 进程 CPU | `process_cpu_usage` | `* 100` 转百分比 | ✅ | ✅ |
| 7 | HTTP 请求总数 | `http_server_requests_seconds_count` | 直接取值 | ✅ | ❌ (返回0) |
| 8 | 应用启动时间 | `application_started_time_seconds` | 直接取值 | ✅ | ❌ (返回0) |

### 4.3 getRealtimeMetrics — 新增 JAVA 分支

在 [`MonitorService.java`](java-apex-server/src/main/java/com/apex/service/MonitorService.java) 的 `getRealtimeMetrics()` 方法（约第 621 行 MYSQL 分支之后）新增：

```java
} else if ("JAVA_ACTUATOR".equalsIgnoreCase(osType) || "JAVA_JMX".equalsIgnoreCase(osType)) {
    return new MonitorRealtimeVO(machineId, true, null,
            -1, -1, -1,            // OS 指标不适用
            0L, 0L, 0L,            // 网络/uptime 不适用
            0.0, 0.0, 0.0,         // loadAvg 不适用
            portStatusList,         // 定制指标（如果有）
            0L, 0L, 0.0, 0L, 0L, 0L, // MySQL 字段不适用
            parseJvmHeapUsage(metricsText),
            parseJvmGcPauseSeconds(metricsText),
            parseJvmGcCount(metricsText),
            parseJvmThreadCount(metricsText),
            parseJvmDaemonThreadCount(metricsText),
            parseProcessCpuUsage(metricsText),
            parseHttpRequestCount(metricsText),
            parseAppUptime(metricsText));
}
```

### 4.4 新增 JVM 指标解析方法

复用已有的 [`extractMetricValue`](java-apex-server/src/main/java/com/apex/service/MonitorService.java:877) 和 [`extractLabeledMetricValue`](java-apex-server/src/main/java/com/apex/service/MonitorService.java:886)：

```java
private double parseJvmHeapUsage(String text) {
    double used = extractLabeledMetricValue(text, "jvm_memory_used_bytes", "area", "heap");
    double max = extractLabeledMetricValue(text, "jvm_memory_max_bytes", "area", "heap");
    if (max <= 0) return 0;
    return (used / max) * 100;
}

private double parseJvmGcPauseSeconds(String text) {
    return extractMetricValue(text, "jvm_gc_pause_seconds_sum");
}

private double parseJvmGcCount(String text) {
    return extractMetricValue(text, "jvm_gc_pause_seconds_count");
}

private double parseJvmThreadCount(String text) {
    return extractMetricValue(text, "jvm_threads_live_threads");
}

private double parseJvmDaemonThreadCount(String text) {
    return extractMetricValue(text, "jvm_threads_daemon_threads");
}

private double parseProcessCpuUsage(String text) {
    return extractMetricValue(text, "process_cpu_usage") * 100;
}

private double parseHttpRequestCount(String text) {
    // JMX Exporter 无此指标，extractMetricValue 不存在时返回 0
    return extractMetricValue(text, "http_server_requests_seconds_count");
}

private double parseAppUptime(String text) {
    // JMX Exporter 无此指标，extractMetricValue 不存在时返回 0
    return extractMetricValue(text, "application_started_time_seconds");
}
```

### 4.5 MonitorRealtimeVO 扩展

[`MonitorRealtimeVO.java`](java-apex-server/src/main/java/com/apex/model/MonitorRealtimeVO.java) 末尾新增：

```java
        // JAVA 专用字段（非 JAVA 时均为 0）
        double jvmHeapUsage,          // 堆内存使用率 0-100
        double jvmGcPauseSeconds,     // GC 累计暂停秒数
        double jvmGcCount,            // GC 累计次数
        double jvmThreadCount,        // 活动线程数
        double jvmDaemonThreadCount,  // 守护线程数
        double processCpuUsage,       // 进程 CPU 使用率 0-100
        double httpRequestCount,      // HTTP 累计请求数（仅 Actuator）
        double appUptimeSeconds       // 应用启动时间秒（仅 Actuator）
) {}
```

### 4.6 无需修改的文件

| 文件 | 原因 |
|------|------|
| `MonitorController.java` | 接口路径和参数不变 |
| `MonitorSampleScheduler.java` | `fetchMetrics()` 改造后自动适配 |
| `MonitorMachine.java` | osType String 字段无需改动 |
| `MonitorMachineDTO.java` | osType String 字段无需改动 |
| `MonitorMachineMapper.java` | MyBatis Plus BaseMapper 自动映射 |

---

## 5. MetricDictionary 重构 + JVM 扩展

> **背景**：当前 [`MetricDictionary.java`](java-apex-server/src/main/java/com/apex/config/MetricDictionary.java) 已达 1585 行，每次新增指标类型都需要读写整个文件，Token 消耗极大。
> **策略**：将 ENTRIES 静态块的 ~1310 行指标条目按监控类别拆分为 4 个独立文件，平铺在 `config/` 下，本次新增 JVM 只需新建一个 ~40 行的 `MetricJvmDict.java`。

### 5.1 拆分后的文件结构

```
config/
├── MetricDictionary.java      ← 主入口（精简至 ~200 行）
│                                 · MetricCategory 枚举
│                                 · MetricEntry record
│                                 · PREFIX_RULES + inferCategoryByPrefix()
│                                 · entry() 便捷方法
│                                 · ENTRIES 聚合入口
│                                 · NAME_TO_ENTRY + 公共 API
├── MetricLinuxDict.java       ← node_* 指标条目（~200 行）
├── MetricWinDict.java         ← windows_* 指标条目（~200 行）
├── MetricMysqlDict.java       ← mysql_* 指标条目（~500 行）
├── MetricJvmDict.java         ← jvm_* / process_* / http_* 条目（~40 行）★ 本次新建
├── EmpContextConfig.java
├── MyBatisPlusConfig.java
```

### 5.2 子文件模板

每个子文件结构完全一致，只暴露一个静态方法：

```java
// MetricJvmDict.java
package com.apex.config;

import java.util.List;
import static com.apex.config.MetricDictionary.entry;

/**
 * JVM 指标中文翻译字典 — Actuator / JMX Exporter 通用。
 * 分类由 {@link MetricDictionary#inferCategoryByPrefix} 自动推断。
 */
public final class MetricJvmDict {
    private MetricJvmDict() {}

    public static void contribute(List<MetricDictionary.MetricEntry> list) {
        // ===== JVM 内存 =====
        entry(list, "jvm_memory_used_bytes", "JVM 内存已使用");
        entry(list, "jvm_memory_committed_bytes", "JVM 内存已提交");
        entry(list, "jvm_memory_max_bytes", "JVM 内存最大值");
        entry(list, "jvm_buffer_total_capacity_bytes", "JVM 缓冲区总容量");
        entry(list, "jvm_buffer_count_buffers", "JVM 缓冲区数量");

        // ===== JVM GC =====
        entry(list, "jvm_gc_pause_seconds_count", "GC 次数");
        entry(list, "jvm_gc_pause_seconds_sum", "GC 累计耗时");
        entry(list, "jvm_gc_pause_seconds_max", "GC 最大暂停时间");
        entry(list, "jvm_gc_memory_allocated_bytes_total", "GC 后内存分配总量");
        entry(list, "jvm_gc_memory_promoted_bytes_total", "GC 晋升内存总量");
        entry(list, "jvm_gc_live_data_size_bytes", "GC 存活数据大小");

        // ===== JVM 线程 =====
        entry(list, "jvm_threads_live_threads", "活动线程数");
        entry(list, "jvm_threads_daemon_threads", "守护线程数");
        entry(list, "jvm_threads_peak_threads", "峰值线程数");
        entry(list, "jvm_threads_started_threads_total", "累计启动线程数");

        // ===== JVM 类加载 =====
        entry(list, "jvm_classes_loaded_classes", "已加载类数");
        entry(list, "jvm_classes_unloaded_classes_total", "累计卸载类数");

        // ===== 进程 =====
        entry(list, "process_cpu_usage", "进程CPU使用率");
        entry(list, "process_uptime_seconds", "进程运行时间");
        entry(list, "process_files_max_files", "最大文件描述符");
        entry(list, "process_files_open_files", "已打开文件描述符");

        // ===== HTTP 请求（仅 Actuator） =====
        entry(list, "http_server_requests_seconds_count", "HTTP请求总数");
        entry(list, "http_server_requests_seconds_sum", "HTTP请求总耗时");
        entry(list, "http_server_requests_seconds_max", "HTTP请求最大耗时");

        // ===== 应用（仅 Actuator） =====
        entry(list, "application_started_time_seconds", "应用启动时间");
        entry(list, "application_ready_time_seconds", "应用就绪时间");
    }
}
```

### 5.3 主类聚合改造

[`MetricDictionary.java`](java-apex-server/src/main/java/com/apex/config/MetricDictionary.java) 的 ENTRIES 静态块改为调用子字典：

```java
static {
    List<MetricEntry> list = new ArrayList<>();
    MetricLinuxDict.contribute(list);
    MetricWinDict.contribute(list);
    MetricMysqlDict.contribute(list);
    MetricJvmDict.contribute(list);   // ← 新增一行
    ENTRIES = Collections.unmodifiableList(list);
}
```

同时将 `entry()` 和 `inferCategoryByPrefix()` 的访问修饰符从 `private` 改为 `package-private`（或 `public static`），供子文件调用。

### 5.4 PREFIX_RULES 新增 JVM 归类

在主类的 `PREFIX_RULES` 静态块末尾（`process_` 的规则之前）新增：

```java
// ===== JVM 前缀归类 =====
PREFIX_RULES.put("jvm_memory_",             MetricCategory.MEMORY);
PREFIX_RULES.put("jvm_buffer_",             MetricCategory.MEMORY);
PREFIX_RULES.put("jvm_gc_",                 MetricCategory.CPU);
PREFIX_RULES.put("jvm_threads_",            MetricCategory.THREAD);
PREFIX_RULES.put("jvm_classes_",            MetricCategory.RUNTIME);
PREFIX_RULES.put("jvm_info",                MetricCategory.RUNTIME);
PREFIX_RULES.put("jvm_runtime_",            MetricCategory.RUNTIME);
PREFIX_RULES.put("http_server_requests_",   MetricCategory.SERVICE);
PREFIX_RULES.put("application_",            MetricCategory.RUNTIME);
PREFIX_RULES.put("tomcat_",                 MetricCategory.SERVICE);
PREFIX_RULES.put("executor_",               MetricCategory.SERVICE);
```

同时将 `process_` 的归类从 `RUNTIME` 改为 `SYSTEM`，因为 JVM 语境下 `process_cpu_usage` 等指标更接近系统资源而非 Go 运行时。

### 5.5 Token 消耗对比

| 操作 | 拆分前 | 拆分后 |
|------|-------|--------|
| 新增 JVM 指标 | 读+写 1585 行 | 新建 40 行 `MetricJvmDict.java` + 主类加 1 行聚合 + 加 ~11 行 PREFIX_RULES |
| 修改 Linux 指标 | 读+写 1585 行 | 只读写 `MetricLinuxDict.java` (~200 行) |
| 修改 MySQL 指标 | 读+写 1585 行 | 只读写 `MetricMysqlDict.java` (~500 行) |


---

## 6. 前端实现计划

### 6.1 TypeScript 类型扩展

[`web-apex-vue/src/types/monitor.ts`](web-apex-vue/src/types/monitor.ts)：

```typescript
// MonitorMachine — osType 新增两个 JAVA 子类型
export interface MonitorMachine {
  id: number
  machineName: string
  ip: string
  osType: 'WINDOWS' | 'LINUX' | 'MYSQL' | 'JAVA_ACTUATOR' | 'JAVA_JMX'
  exporterPort: number
  refreshInterval: number
  isEnabled: boolean
}

// MonitorRealtime — 新增 JVM 字段
export interface MonitorRealtime {
  // ... 现有字段保持不变 ...
  mysqlConnections: number
  mysqlMaxConnections: number
  mysqlBufferPoolHitRate: number
  mysqlSlowQueries: number
  mysqlQueriesTotal: number
  mysqlThreadsRunning: number
  /** JAVA 专用字段 */
  jvmHeapUsage: number           // 堆内存使用率 0-100
  jvmGcPauseSeconds: number      // GC 累计暂停秒数
  jvmGcCount: number             // GC 累计次数
  jvmThreadCount: number         // 活动线程数
  jvmDaemonThreadCount: number   // 守护线程数
  processCpuUsage: number        // 进程 CPU 使用率 0-100
  httpRequestCount: number       // HTTP 累计请求数（仅 Actuator）
  appUptimeSeconds: number       // 应用启动时间秒（仅 Actuator）
}
```

**工具函数**（判断是否为 Java 类型）：

```typescript
/** 判断 osType 是否为 Java 类型（Actuator 或 JMX） */
export function isJavaOsType(osType: string): boolean {
  return osType === 'JAVA_ACTUATOR' || osType === 'JAVA_JMX'
}

/** 判断是否为 Actuator 模式 */
export function isActuatorMode(osType: string): boolean {
  return osType === 'JAVA_ACTUATOR'
}
```

### 6.2 MachineFormDialog — 新增两个 JAVA 选项

[`MachineFormDialog.vue`](web-apex-vue/src/components/monitor/MachineFormDialog.vue) 变更：

#### 6.2.1 下拉新增两个选项

```html
<el-select v-model="form.osType" placeholder="请选择系统类型" style="width: 100%">
  <el-option label="Linux" value="LINUX" />
  <el-option label="Windows" value="WINDOWS" />
  <el-option label="MySQL" value="MYSQL" />
  <el-option label="Java (Actuator)" value="JAVA_ACTUATOR" />
  <el-option label="Java (JMX Exporter)" value="JAVA_JMX" />
</el-select>
```

#### 6.2.2 选择时自动切换默认端口

```typescript
// osType 切换时自动调整端口
watch(() => form.osType, (newType) => {
  switch (newType) {
    case 'LINUX':          form.exporterPort = 9100; break
    case 'WINDOWS':        form.exporterPort = 9182; break
    case 'MYSQL':          form.exporterPort = 9104; break
    case 'JAVA_ACTUATOR':  form.exporterPort = 8080; break
    case 'JAVA_JMX':       form.exporterPort = 9104; break
  }
})
```
#### 6.2.3 端点 URL 实时预览 + 一键复制

在表单底部用一个只读输入框展示完整 URL，带复制按钮。无论新增还是编辑，随 IP / osType / 端口变化实时更新，方便用户保存后复制去浏览器验证。

```html
<!-- 端点 URL 预览（所有类型通用） -->
<el-form-item label="端点 URL">
  <el-input
    :model-value="endpointUrl"
    readonly
    class="endpoint-url-input"
  >
    <template #append>
      <el-button
        :icon="CopyDocument"
        @click="copyEndpointUrl"
      >
        复制
      </el-button>
    </template>
  </el-input>
  <div class="form-tip">
    <el-icon><Link /></el-icon>
    <span>保存后可在浏览器打开此地址验证指标是否正常返回</span>
  </div>
</el-form-item>
```

**计算属性** — 根据 osType 自动拼接路径：

```typescript
import { computed } from 'vue'
import { ElMessage } from 'element-plus'
import { CopyDocument, Link } from '@element-plus/icons-vue'

/** 计算完整端点 URL */
const endpointUrl = computed(() => {
  const ip = form.ip || 'IP'
  const port = form.exporterPort
  const path = getMetricsPath(form.osType)
  return `http://${ip}:${port}${path}`
})

/** 根据 osType 获取对应的指标端点路径 */
function getMetricsPath(osType: string): string {
  switch (osType) {
    case 'JAVA_ACTUATOR': return '/actuator/prometheus'
    default:              return '/metrics'
  }
}

/** 复制端点 URL 到剪贴板 */
async function copyEndpointUrl() {
  if (!form.ip || !form.exporterPort) {
    ElMessage.warning('请先填写 IP 和端口')
    return
  }
  try {
    await navigator.clipboard.writeText(endpointUrl.value)
    ElMessage.success('已复制端点地址：' + endpointUrl.value)
  } catch {
    // 降级方案
    const input = document.createElement('input')
    input.value = endpointUrl.value
    document.body.appendChild(input)
    input.select()
    document.execCommand('copy')
    document.body.removeChild(input)
    ElMessage.success('已复制端点地址')
  }
}
```

#### 6.2.4 端点标注 — 不同 osType 显示不同说明

```html
<div class="form-tip text-gray-400 text-xs mt-1">
  <template v-if="form.osType === 'JAVA_ACTUATOR'">
    ✅ Actuator 模式：适用于 Spring Boot 项目，端点由应用内置提供
  </template>
  <template v-else-if="form.osType === 'JAVA_JMX'">
    ✅ JMX Exporter 模式：需通过 -javaagent 参数挂载 jmx_prometheus_javaagent.jar
  </template>
  <template v-else>
    📡 标准 Exporter 格式 (node_exporter / windows_exporter / mysqld_exporter)
  </template>
</div>
```

**效果预览**（编辑已有机器时自动回显）：

```
┌──────────────────────────────────────────────────┐
│  编辑机器                                         │
│                                                   │
│  机器别名    [ 生产-Java服务            ]         │
│  机器IP      [ 192.168.1.100            ]         │
│  系统类型    [ Java (Actuator)      ▾  ]         │
│  Exporter端口 [ 8080                ▾  ]         │
│  刷新频率(秒) [ 3                    ▾  ]         │
│                                                   │
│  端点 URL    ┌─────────────────────────┬──────┐  │
│              │ http://192.168.1.100:8080/actuator/prometheus │  │  ← 实时计算
│              └─────────────────────────┴──────┘  │
│              🔗 保存后可在浏览器打开此地址验证     │
│              ✅ Actuator 模式：适用于 Spring Boot项目  │
│                                                   │
│                              [ 取消 ] [ 确定 ]    │
└──────────────────────────────────────────────────┘
```

### 6.3 MachineCard — 新增 JAVA 展示模式

[`MachineCard.vue`](web-apex-vue/src/components/monitor/MachineCard.vue) 条件渲染改造：

```html
<!-- JAVA 模式 (Actuator 或 JMX) -->
<template v-if="isJavaOsType(machine.osType)">
  <!-- JAVA 卡片内容 -->
</template>
<!-- MySQL 模式 -->
<template v-else-if="machine.osType === 'MYSQL'">
  <!-- 现有 MySQL 卡片 -->
</template>
<!-- OS 模式 -->
<template v-else>
  <!-- 现有 OS 卡片 -->
</template>
```

#### 6.3.1 JAVA 卡片布局

```
┌──────────────────────────────────────────────────┐
│  🟢 生产-Java服务      [ Actuator ]   ⚙️  🔄    │  ← 顶栏 + 模式标签
│──────────────────────────────────────────────────│
│                                                   │
│  堆内存使用                                       │
│  ████████████████░░░░░░░░  65.2%                 │  ← el-progress
│  已用 334 MB / 最大 512 MB                        │
│                                                   │
│  ┌──────────┬──────────┬──────────┐             │
│  │ GC 暂停   │ 活动线程  │ CPU 使用  │             │  ← 六宫格
│  │ 2.34 s   │   89     │  12.5 %  │             │
│  ├──────────┼──────────┼──────────┤             │
│  │ HTTP 请求 │ 守护线程  │ 运行时间  │             │
│  │ 45,231   │   21     │  3d 5h   │             │  ← JMX 下 HTTP/运行时间 显示 N/A
│  └──────────┴──────────┴──────────┘             │
│                                                   │
│  💡 定制指标（如果有）                             │
│                                                   │
│  IP: 192.168.1.100  端口: 8080 (Actuator)         │
│  刷新: 3s                                         │
└──────────────────────────────────────────────────┘
```

#### 6.3.2 模式标签

```html
<el-tag v-if="isActuatorMode(machine.osType)" size="small">Actuator</el-tag>
<el-tag v-else-if="machine.osType === 'JAVA_JMX'" size="small" type="success">JMX Exporter</el-tag>
```

#### 6.3.3 Actuator 独有指标降级显示

```html
<!-- HTTP 请求数 -->
<span class="cell-value">
  {{ isActuatorMode(machine.osType)
      ? formatNumber(data.httpRequestCount)
      : 'N/A' }}
</span>

<!-- 运行时间 -->
<span class="cell-value">
  {{ isActuatorMode(machine.osType)
      ? formatUptime(data.appUptimeSeconds)
      : 'N/A' }}
</span>
```

#### 6.3.4 辅助函数

```typescript
function heapColor(usage: number): string {
  if (usage > 80) return '#F56C6C'   // 红色
  if (usage > 60) return '#E6A23C'   // 橙色
  return '#67C23A'                    // 绿色
}

function formatSeconds(s: number): string {
  if (s <= 0) return 'N/A'
  if (s < 60) return s.toFixed(2) + ' s'
  if (s < 3600) return (s / 60).toFixed(1) + ' min'
  return (s / 3600).toFixed(1) + ' h'
}

function formatNumber(n: number): string {
  if (n <= 0) return 'N/A'
  if (n >= 1_000_000) return (n / 1_000_000).toFixed(1) + 'M'
  if (n >= 1_000) return (n / 1_000).toFixed(1) + 'K'
  return String(Math.round(n))
}

function formatUptime(s: number): string {
  if (s <= 0) return 'N/A'
  const d = Math.floor(s / 86400)
  const h = Math.floor((s % 86400) / 3600)
  if (d > 0) return d + 'd ' + h + 'h'
  if (h > 0) return h + 'h ' + Math.floor((s % 3600) / 60) + 'm'
  return Math.floor(s / 60) + 'm'
}
```

### 6.4 API 层：无需修改

[`web-apex-vue/src/api/monitor.ts`](web-apex-vue/src/api/monitor.ts) 无需任何改动。`addMachine`、`updateMachine` 等方法直接透传 `osType` 字符串。

---

## 7. 实施步骤

| 步骤 | 内容 | 依赖 |
|------|------|------|
| **Step 1** | Flyway 迁移 — 扩展 os_type 注释 | 无 |
| **Step 2a** | `MetricDictionary.java` — 拆分 ENTRIES 块为 4 个 `Metric*Dict.java` 子文件 | 无 |
| **Step 2b** | `MetricDictionary.java` — PREFIX_RULES 新增 JVM 归类 + 聚合入口调用 `MetricJvmDict.contribute()` | Step 2a |
| **Step 2c** | 新建 `MetricJvmDict.java` — JVM 指标中文翻译（~40 行） | Step 2a |
| **Step 3** | `MonitorRealtimeVO.java` — 新增 8 个 JAVA 专用字段 | 无 |
| **Step 4** | `MonitorService.java` — `fetchMetrics()` URL 自适应 + JAVA 分支 + 8 个解析方法 | Step 2b, 3 |
| **Step 5** | 前端 `monitor.ts` — 类型扩展 + 工具函数 | Step 3 |
| **Step 6** | 前端 `MachineFormDialog.vue` — 新增两个 JAVA 选项 + 端口联动 + 端点 URL 实时预览 + 一键复制 | Step 5 |
| **Step 7** | 前端 `MachineCard.vue` — 新增 JAVA 展示模式 | Step 5 |
| **Step 8** | 联调测试：本机 Actuator 模式 | Step 1-7 |
| **Step 9** | 联调测试：JMX Exporter 模式 | Step 1-7 |

---

## 8. 技术要点与注意事项

### 8.1 URL 构造对照表

| osType | 默认端口 | URL |
|--------|---------|-----|
| `JAVA_ACTUATOR` | 8080 | `http://IP:8080/actuator/prometheus` |
| `JAVA_JMX` | 9104 | `http://IP:9104/metrics` |
| WINDOWS | 9182 | `http://IP:9182/metrics` |
| LINUX | 9100 | `http://IP:9100/metrics` |
| MYSQL | 9104 | `http://IP:9104/metrics` |

### 8.2 Actuator 独有指标

| 指标 | `JAVA_ACTUATOR` | `JAVA_JMX` | 前端处理 |
|------|----------------|-----------|---------|
| `http_server_requests_seconds_count` | ✅ 有值 | ❌ 返回 0 | 显示 "N/A" |
| `application_started_time_seconds` | ✅ 有值 | ❌ 返回 0 | 显示 "N/A" |
| 其余 6 项 JVM 指标 | ✅ | ✅ | 正常显示 |

### 8.3 JMX Exporter 部署

别人的 Java 应用只需启动参数中加一行：

```bash
java -javaagent:jmx_prometheus_javaagent.jar=9104:config.yaml -jar app.jar
```

然后在监控系统添加机器：`osType=JAVA_JMX`, `exporterPort=9104`。

### 8.4 向后兼容

现有 `WINDOWS`、`LINUX`、`MYSQL` 类型的机器行为完全不变。`fetchMetrics()` 中只有 `JAVA_ACTUATOR` 走 `/actuator/prometheus`，其他全部走 `/metrics`。

### 8.5 采样任务适配

`MonitorSampleScheduler.collectSample()` 中已有 osType 分支判断，新增 `JAVA_ACTUATOR` / `JAVA_JMX` 后按需扩展系统指标映射即可。

---

## 9. 测试要点

| # | 测试场景 | 验证点 |
|---|---------|--------|
| 1 | 添加「Java (Actuator)」本机 127.0.0.1:8080 | 8 项指标全部有值，模式标签显示 "Actuator" |
| 2 | 添加「Java (JMX Exporter)」模拟 | 前 6 项有值，HTTP 请求和运行时间显示 "N/A"，标签显示 "JMX Exporter" |
| 3 | 切换下拉选项 | 选 Actuator 默认端口 8080，选 JMX 默认端口 9104 |
| 4 | 现有 LINUX/WINDOWS/MYSQL 机器 | 行为完全不变 |
| 5 | Exporter 不可达 | 卡片显示 "无法连接" |
| 6 | 详情弹窗 | JAVA 机器的全量指标分类展示正确 |
| 7 | 采样任务 | JAVA 机器的采样历史数据正确 |

---

## 10. 总结

```
┌──────────────────────────────┐
│  用户只需下拉选一个：         │
│  ● Java (Actuator)           │  → 自动: 端口 8080, 路径 /actuator/prometheus
│  ● Java (JMX Exporter)       │  → 自动: 端口 9104, 路径 /metrics
│                              │
│  剩下的 IP 和端口（如需覆盖）  │
│  跟 Linux/Windows 一样填即可  │
└──────────────────────────────┘
```

<strong>零额外字段、零新表、零新列</strong>。两个子类型完全由后端 switch 分支和前端下拉选项承载，解析代码 100% 复用，是当前架构下最简洁的扩展方式。

# Java/JVM 监控接入方案 — 实施计划

> **目标**：将本项目自身 Java 应用的 JVM 运行状态接入现有监控体系，像监控 Windows/Linux 一样监控 Java。
> **数据源**：利用已部署的 Spring Boot Actuator + Micrometer Prometheus，对接 `/actuator/prometheus` 端点的 Prometheus Text 格式输出。
> **技术栈**：Spring Boot 3.4+ (Java 21 虚拟线程) + Vue 3 (Vite 6, TypeScript, Element Plus) + MySQL 8.4

---

## 1. 核心思路

现有的 `MonitorMachine` 实体已支持三种 `osType`：`WINDOWS`、`LINUX`、`MYSQL`。本次新增 `JAVA` 作为第四种类型，整体实现路径完全复用现有架构：

| 维度 | WINDOWS/LINUX | MYSQL | **JAVA（新增）** |
|------|--------------|-------|-----------------|
| 数据源 | node_exporter / windows_exporter | mysqld_exporter | **本应用自身 `/actuator/prometheus`** |
| 默认端口 | 9100 / 9182 | 9104 | **8080（应用服务端口）** |
| 核心指标 | CPU/内存/磁盘/网络 | 连接数/缓冲池/慢查询 | **堆内存/GC/线程/CPU/HTTP 请求** |
| 是否需要额外部署 Exporter | 是 | 是 | **否（Actuator 内置）** |

> **关键优势**：Java 监控不需要在目标机器上额外安装任何 Exporter 代理。Spring Boot Actuator 已内建 `/actuator/prometheus` 端点（Plan 1 已完成依赖与配置），直接 HTTP GET 即可获取 JVM 全部运行时指标。

---

## 2. 变更范围总览

| 序号 | 交付物 | 类型 | 说明 |
|------|--------|------|------|
| 1 | `V11__add_java_os_type.sql` | Flyway 迁移 | 扩展 os_type 列注释，新增 JAVA |
| 2 | `MonitorMachine.java` | 实体 | osType 注释更新（无需改代码） |
| 3 | `MonitorService.java` | Service | 新增 JAVA 分支 + JVM 指标解析方法 |
| 4 | `MonitorRealtimeVO.java` | Record | 新增 JVM 专用字段（堆内存、GC、线程、CPU） |
| 5 | `MetricDictionary.java` | 配置 | 新增 `jvm_*` / `process_*` 前缀归类规则 + JVM 指标中文翻译 |
| 6 | `monitor.ts` (types) | 前端类型 | osType 新增 `'JAVA'`，MonitorRealtime 新增 JVM 字段 |
| 7 | `MachineFormDialog.vue` | 前端组件 | 系统类型下拉新增 "Java 应用" 选项 |
| 8 | `MachineCard.vue` | 前端组件 | 新增 JAVA 专用卡片展示模式（堆/GC/线程/CPU） |

> **无需变更**：`MonitorController.java`、`MonitorSampleScheduler.java`、`MonitorMachineDTO.java`、`MonitorMachineMapper.java` 均无需修改。采样调度器自动适配（通过通用的 `fetchMetrics` + `parseAllMetrics` 管道）。

---

## 3. 数据库变更 (Flyway V11)

### 3.1 迁移脚本

```sql
-- V11__add_java_os_type.sql
-- =============================================
-- V11: 扩展 os_type 支持 JAVA (Actuator Prometheus)
-- =============================================
ALTER TABLE `monitor_machine`
    MODIFY COLUMN `os_type` VARCHAR(20) NOT NULL COMMENT 'WINDOWS、LINUX、MYSQL 或 JAVA';
```

**文件位置**：
```
java-apex-server/src/main/resources/db/migration/V11__add_java_os_type.sql
```

### 3.2 实体注释更新

[`MonitorMachine.java`](java-apex-server/src/main/java/com/apex/entity/MonitorMachine.java) 第 24 行 `osType` 字段的 Javadoc 注释更新为：

```java
/** WINDOWS、LINUX、MYSQL 或 JAVA */
private String osType;
```

---

## 4. 后端实现计划

### 4.1 JVM 核心监控指标

从 `/actuator/prometheus` 端点返回的指标中，提取以下关键指标进行实时展示：

| 指标类别 | Prometheus 指标名 | 说明 | 计算方式 |
|---------|-------------------|------|---------|
| **堆内存使用率** | `jvm_memory_used_bytes{area="heap"}` / `jvm_memory_max_bytes{area="heap"}` | JVM 堆内存占用百分比 | `used / max * 100` |
| **GC 暂停时间** | `jvm_gc_pause_seconds_sum` | GC 累计暂停秒数（用于计算增量速率） | 两次采集差值 / 时间差 |
| **GC 次数** | `jvm_gc_pause_seconds_count` | GC 累计次数 | 两次采集差值 |
| **活动线程数** | `jvm_threads_live_threads` | 当前存活线程总数 | 直接取值 |
| **守护线程数** | `jvm_threads_daemon_threads` | 守护线程数 | 直接取值 |
| **进程 CPU 使用率** | `process_cpu_usage` | JVM 进程 CPU 占用比例 (0-1) | `value * 100` |
| **HTTP 请求总数** | `http_server_requests_seconds_count` | 应用累计处理 HTTP 请求数 | 两次采集差值 |
| **HTTP 错误率** | `http_server_requests_seconds_count{status="5xx"}` / 总量 | 5xx 错误占比 | 增量计算 |
| **应用启动时间** | `application_started_time_seconds` | 应用已启动秒数 | 直接取值 |
| **系统负载** | `system_load_average_1m` | 系统 1 分钟负载 | 直接取值 |

### 4.2 MonitorService 新增方法

#### 4.2.1 `getRealtimeMetrics()` 方法中的 JAVA 分支

在 [`MonitorService.java`](java-apex-server/src/main/java/com/apex/service/MonitorService.java) 的 `getRealtimeMetrics()` 方法（约第 591-679 行）中，现有的 osType 判断逻辑（`"MYSQL".equals(osType)`）之后新增 `"JAVA".equals(osType)` 分支：

```java
// 现有代码结构中，在 MYSQL 分支之后添加：
else if ("JAVA".equals(osType)) {
    vo = new MonitorRealtimeVO(
            machineId, true, null,
            -1, -1, -1,            // CPU/内存/磁盘沿用 -1（由 process_cpu_usage 替代 CPU）
            0L, 0L, 0L,             // 网络、uptime 不适用
            0.0, 0.0, 0.0,          // loadAvg 不适用
            List.of(),              // ports 不适用
            0L, 0L, 0.0, 0L, 0L, 0L, // MySQL 字段不适用
            parseJvmHeapUsage(text),       // 堆内存使用率
            parseJvmGcPauseSeconds(text),  // GC 累计暂停秒数
            parseJvmGcCount(text),         // GC 累计次数
            parseJvmThreadCount(text),     // 活动线程数
            parseJvmDaemonThreadCount(text), // 守护线程数
            parseProcessCpuUsage(text),    // 进程 CPU 使用率
            parseHttpRequestRate(text),    // HTTP 请求总数
            parseAppUptime(text)           // 应用启动时间
    );
}
```

#### 4.2.2 新增 JVM 指标解析方法

所有解析方法均使用与现有 `extractMetricValue` / `extractLabeledMetricValue` 一致的 Prometheus Text 行解析模式：

```java
/**
 * 解析堆内存使用率 (%) = jvm_memory_used_bytes{area="heap"} / jvm_memory_max_bytes{area="heap"} * 100
 */
private double parseJvmHeapUsage(String text) {
    double used = extractLabeledMetricValue(text, "jvm_memory_used_bytes", "area", "heap");
    double max = extractLabeledMetricValue(text, "jvm_memory_max_bytes", "area", "heap");
    if (max <= 0) return 0;
    return used / max * 100; // #2 decimals used in front-end
}

/**
 * 解析 GC 累计暂停秒数
 */
private double parseJvmGcPauseSeconds(String text) {
    return extractMetricValue(text, "jvm_gc_pause_seconds_sum");
}

/**
 * 解析 GC 累计次数
 */
private double parseJvmGcCount(String text) {
    return extractMetricValue(text, "jvm_gc_pause_seconds_count");
}

/**
 * 解析活动线程数
 */
private double parseJvmThreadCount(String text) {
    return extractMetricValue(text, "jvm_threads_live_threads");
}

/**
 * 解析守护线程数
 */
private double parseJvmDaemonThreadCount(String text) {
    return extractMetricValue(text, "jvm_threads_daemon_threads");
}

/**
 * 解析进程 CPU 使用率 (0-1 比例转为百分比)
 */
private double parseProcessCpuUsage(String text) {
    double ratio = extractMetricValue(text, "process_cpu_usage");
    return ratio * 100;
}

/**
 * 解析 HTTP 累计请求数
 */
private double parseHttpRequestRate(String text) {
    return extractMetricValue(text, "http_server_requests_seconds_count");
}

/**
 * 解析应用启动时间（秒）
 */
private double parseAppUptime(String text) {
    return extractMetricValue(text, "application_started_time_seconds");
}
```

> **注意**：`extractMetricValue` 和 `extractLabeledMetricValue` 是 `MonitorService` 已有的私有方法（第 877-894 行），无需重新实现。

#### 4.2.3 `SYS_METRICS` 扩展（可选）

[`MonitorService.java`](java-apex-server/src/main/java/com/apex/service/MonitorService.java) 第 41-48 行的 `SYS_METRICS` 列表已内置 -1 到 -6 的通用指标。对于 JAVA 类型，可在 `collectSample()` 的 osType 分支判断中，当 `osType = JAVA` 时，将系统指标映射为对应的 JVM 指标名进行解析。也可以将当前已有的 6 个系统内置指标扩展为 JAVA 专用变体。推荐方案是保持现有 `SYS_METRICS` 不变，在采样器中根据 osType 做适配。

#### 4.2.4 `getFullMetrics()` 中的分类适配

[`MonitorService.java`](java-apex-server/src/main/java/com/apex/service/MonitorService.java) 的 `getFullMetrics()` 方法（约第 229-325 行）已通过 `MetricDictionary.inferCategory()` 自动为每条指标分类。只需在 [`MetricDictionary.java`](java-apex-server/src/main/java/com/apex/config/MetricDictionary.java) 中补全 `jvm_*` 前缀的归类规则即可（见第 5 节）。

### 4.3 MonitorRealtimeVO 扩展

[`MonitorRealtimeVO.java`](java-apex-server/src/main/java/com/apex/model/MonitorRealtimeVO.java) 新增 JVM 专用字段，保持与 MySQL 扩展相同的模式（非 JAVA 时为 0 / -1）：

```java
public record MonitorRealtimeVO(
        // ... 现有字段保持不变 ...
        long mysqlConnections,
        long mysqlMaxConnections,
        double mysqlBufferPoolHitRate,
        long mysqlSlowQueries,
        long mysqlQueriesTotal,
        long mysqlThreadsRunning,
        // JAVA 专用字段（非 JAVA 时为 0 / -1）
        double jvmHeapUsage,          // 堆内存使用率 0-100
        double jvmGcPauseSeconds,     // GC 累计暂停秒数
        double jvmGcCount,            // GC 累计次数
        double jvmThreadCount,        // 活动线程数
        double jvmDaemonThreadCount,  // 守护线程数
        double processCpuUsage,       // 进程 CPU 使用率 0-100
        double httpRequestCount,      // HTTP 累计请求数
        double appUptimeSeconds       // 应用启动时间（秒）
) {}
```

### 4.4 无需修改的文件

| 文件 | 原因 |
|------|------|
| `MonitorController.java` | 全部接口路径不变，返回 `Result<T>` 不变 |
| `MonitorSampleScheduler.java` | 通过通用的 `fetchMetrics()` + 采样 osType 分支适配 |
| `MonitorMachineDTO.java` | osType 为 `String` 类型，前端直接传 `"JAVA"` 即可 |
| `MonitorMachineMapper.java` | MyBatis Plus `BaseMapper`，自动映射 |

---

## 5. MetricDictionary 扩展

### 5.1 前缀归类规则

在 [`MetricDictionary.java`](java-apex-server/src/main/java/com/apex/config/MetricDictionary.java) 的 `inferCategoryByPrefix()` 方法（约第 201-208 行）中新增 JVM 相关前缀：

```java
private static MetricCategory inferCategoryByPrefix(String metricName) {
    // ... 现有 node_ / windows_ / mysql_ 前缀规则保持不变 ...

    // JVM 内存指标
    if (metricName.startsWith("jvm_memory_") || metricName.startsWith("jvm_buffer_")) {
        return MetricCategory.MEMORY;
    }
    // JVM GC 指标
    if (metricName.startsWith("jvm_gc_")) {
        // 可直接归入 CPU（GC 影响 CPU）或新增 GC 分类
        return MetricCategory.CPU;
    }
    // JVM 线程指标
    if (metricName.startsWith("jvm_threads_")) {
        return MetricCategory.THREAD;
    }
    // JVM 类加载指标
    if (metricName.startsWith("jvm_classes_")) {
        return MetricCategory.RUNTIME;
    }
    // JVM 信息指标
    if (metricName.startsWith("jvm_info") || metricName.startsWith("jvm_runtime_")) {
        return MetricCategory.RUNTIME;
    }
    // 进程级指标
    if (metricName.startsWith("process_")) {
        return MetricCategory.SYSTEM;
    }
    // HTTP 请求指标
    if (metricName.startsWith("http_server_requests_")) {
        return MetricCategory.SERVICE;
    }
    // 应用级指标
    if (metricName.startsWith("application_")) {
        return MetricCategory.RUNTIME;
    }
    // Tomcat/Netty 指标
    if (metricName.startsWith("tomcat_") || metricName.startsWith("executor_")) {
        return MetricCategory.SERVICE;
    }

    return MetricCategory.OTHER;
}
```

### 5.2 关键指标中文翻译

将以下核心 JVM 指标添加到字典的静态初始化块中：

```java
// ===== JVM 内存 =====
entry(LIST, "jvm_memory_used_bytes", "JVM 内存已使用");
entry(LIST, "jvm_memory_committed_bytes", "JVM 内存已提交");
entry(LIST, "jvm_memory_max_bytes", "JVM 内存最大值");
entry(LIST, "jvm_buffer_total_capacity_bytes", "JVM 缓冲区总容量");
entry(LIST, "jvm_buffer_count_buffers", "JVM 缓冲区数量");

// ===== JVM GC =====
entry(LIST, "jvm_gc_pause_seconds_count", "GC 次数");
entry(LIST, "jvm_gc_pause_seconds_sum", "GC 累计耗时");
entry(LIST, "jvm_gc_pause_seconds_max", "GC 最大暂停时间");
entry(LIST, "jvm_gc_memory_allocated_bytes_total", "GC 后内存分配总量");
entry(LIST, "jvm_gc_memory_promoted_bytes_total", "GC 晋升内存总量");
entry(LIST, "jvm_gc_live_data_size_bytes", "GC 存活数据大小");
entry(LIST, "jvm_gc_overhead_percent", "GC 开销百分比");

// ===== JVM 线程 =====
entry(LIST, "jvm_threads_live_threads", "活动线程数");
entry(LIST, "jvm_threads_daemon_threads", "守护线程数");
entry(LIST, "jvm_threads_peak_threads", "峰值线程数");
entry(LIST, "jvm_threads_started_threads_total", "累计启动线程数");
entry(LIST, "jvm_threads_states_threads", "线程状态分布");

// ===== JVM 类加载 =====
entry(LIST, "jvm_classes_loaded_classes", "已加载类数");
entry(LIST, "jvm_classes_unloaded_classes_total", "累计卸载类数");

// ===== 进程 =====
entry(LIST, "process_cpu_usage", "进程CPU使用率");
entry(LIST, "process_uptime_seconds", "进程运行时间");
entry(LIST, "process_start_time_seconds", "进程启动时间戳");
entry(LIST, "process_files_max_files", "最大文件描述符");
entry(LIST, "process_files_open_files", "已打开文件描述符");

// ===== HTTP 请求 =====
entry(LIST, "http_server_requests_seconds_count", "HTTP请求总数");
entry(LIST, "http_server_requests_seconds_sum", "HTTP请求总耗时");
entry(LIST, "http_server_requests_seconds_max", "HTTP请求最大耗时");

// ===== 应用 =====
entry(LIST, "application_started_time_seconds", "应用启动时间");
entry(LIST, "application_ready_time_seconds", "应用就绪时间");
```

> 以上仅列出关键指标。实际部署后可从 `/actuator/prometheus` 端点获取完整指标列表进行补充。

### 5.3 MetricCategory 扩展（可选）

现有 `MetricCategory` 枚举已包含 `THREAD`、`RUNTIME` 等分类，基本满足 JVM 需求。如果未来需要更细粒度的 GC 分类，可新增：

```java
GC("gc", "GC回收"),
```

---

## 6. 前端实现计划

### 6.1 TypeScript 类型扩展

[`web-apex-vue/src/types/monitor.ts`](web-apex-vue/src/types/monitor.ts) 变更：

#### 6.1.1 MonitorMachine 的 osType 联合类型

```typescript
// 第 11 行：osType 新增 'JAVA'
osType: 'WINDOWS' | 'LINUX' | 'MYSQL' | 'JAVA'
```

#### 6.1.2 MonitorRealtime 新增 JVM 字段

```typescript
export interface MonitorRealtime {
  // ... 现有字段保持不变 ...
  mysqlConnections: number
  mysqlMaxConnections: number
  mysqlBufferPoolHitRate: number
  mysqlSlowQueries: number
  mysqlQueriesTotal: number
  mysqlThreadsRunning: number
  /** JAVA 专用：堆内存使用率 0-100（非 JAVA 时为 0） */
  jvmHeapUsage: number
  /** JAVA 专用：GC 累计暂停秒数（非 JAVA 时为 0） */
  jvmGcPauseSeconds: number
  /** JAVA 专用：GC 累计次数（非 JAVA 时为 0） */
  jvmGcCount: number
  /** JAVA 专用：活动线程数（非 JAVA 时为 0） */
  jvmThreadCount: number
  /** JAVA 专用：守护线程数（非 JAVA 时为 0） */
  jvmDaemonThreadCount: number
  /** JAVA 专用：进程 CPU 使用率 0-100（非 JAVA 时为 0） */
  processCpuUsage: number
  /** JAVA 专用：HTTP 累计请求数（非 JAVA 时为 0） */
  httpRequestCount: number
  /** JAVA 专用：应用启动时间秒（非 JAVA 时为 0） */
  appUptimeSeconds: number
}
```

### 6.2 机器表单弹窗

[`MachineFormDialog.vue`](web-apex-vue/src/components/monitor/MachineFormDialog.vue) 变更：

#### 6.2.1 系统类型下拉新增选项

在 `<el-select>` 的 `<el-option>` 中新增：

```html
<el-option label="Java 应用" value="JAVA" />
```

#### 6.2.2 默认值和端口联动

在 `watch(osType, ...)` 或表单逻辑中增加 JAVA 分支：

```typescript
// 当选择 JAVA 时，默认端口设为 8080
if (form.osType === 'JAVA') {
  form.exporterPort = 8080
}
```

#### 6.2.3 端口说明文字

在端口输入框下方增加 `osType` 相关的提示：

```html
<template v-if="form.osType === 'JAVA'">
  <span class="text-gray-400 text-xs">默认使用应用的 /actuator/prometheus 端点，端口与 server.port 一致</span>
</template>
```

### 6.3 机器卡片组件（核心变更）

[`MachineCard.vue`](web-apex-vue/src/components/monitor/MachineCard.vue) 当前有两种展示模式：
- **OS 模式**（`osType !== 'MYSQL'`）：CPU/内存/磁盘 进度条 + 网络/负载
- **MySQL 模式**（`osType === 'MYSQL'`）：连接数/缓冲池/线程

新增第三种 **JAVA 模式**（`osType === 'JAVA'`）：

#### 6.3.1 JAVA 卡片布局

```
┌─────────────────────────────────────────┐
│  🟢 Java-生产服务     [ ... ]  ⚙️  🔄  │  ← 顶栏：名称 + 状态指示 + 操作
│─────────────────────────────────────────│
│                                         │
│  堆内存使用                              │
│  ████████████████░░░░░░  65.2%          │  ← 进度条（el-progress）
│  已用: 334 MB / 最大: 512 MB            │  ← 具体数值
│                                         │
│  ┌──────────┬──────────┬──────────┐    │
│  │ GC 暂停   │ 活动线程  │ CPU 使用  │    │  ← 三列指标卡片
│  │ 2.34s    │   89     │  12.5%   │    │
│  │ (累计)   │ (当前)   │ (进程)   │    │
│  ├──────────┼──────────┼──────────┤    │
│  │ HTTP 请求 │ 守护线程  │ 运行时间  │    │
│  │ 45,231   │   21     │  3d 5h   │    │
│  │ (累计)   │ (当前)   │ (自启动) │    │
│  └──────────┴──────────┴──────────┘    │
│                                         │
│  💡 定制指标（如果有）                   │
│  • 指标A = 12.3  • 指标B = 45.6         │
│                                         │
│  IP: 192.168.1.100  端口: 8080          │  ← 底部元信息
│  刷新: 3s                               │
└─────────────────────────────────────────┘
```

#### 6.3.2 模板条件渲染

```html
<!-- OS 模式 (Linux/Windows)：CPU + 内存 + 磁盘 + 网络 + 负载 -->
<template v-if="machine.osType !== 'MYSQL' && machine.osType !== 'JAVA'">
  <!-- 现有的 OS 卡片内容保持不变 -->
</template>

<!-- MySQL 模式 -->
<template v-else-if="machine.osType === 'MYSQL'">
  <!-- 现有的 MySQL 卡片内容保持不变 -->
</template>

<!-- JAVA 模式 -->
<template v-else-if="machine.osType === 'JAVA'">
  <div class="java-metrics">
    <!-- 堆内存进度条 -->
    <div class="metric-row">
      <span class="metric-label">堆内存使用</span>
      <el-progress
        :percentage="data.jvmHeapUsage"
        :color="heapColor(data.jvmHeapUsage)"
        :stroke-width="8"
      />
      <span class="metric-text">{{ heapText }}</span>
    </div>

    <!-- 六宫格指标 -->
    <div class="java-grid">
      <div class="java-cell">
        <span class="cell-label">GC 暂停</span>
        <span class="cell-value">{{ formatSeconds(data.jvmGcPauseSeconds) }}</span>
        <span class="cell-sub">累计</span>
      </div>
      <div class="java-cell">
        <span class="cell-label">活动线程</span>
        <span class="cell-value">{{ data.jvmThreadCount }}</span>
        <span class="cell-sub">当前</span>
      </div>
      <div class="java-cell">
        <span class="cell-label">CPU 使用</span>
        <span class="cell-value">{{ data.processCpuUsage.toFixed(1) }}%</span>
        <span class="cell-sub">进程</span>
      </div>
      <div class="java-cell">
        <span class="cell-label">HTTP 请求</span>
        <span class="cell-value">{{ formatNumber(data.httpRequestCount) }}</span>
        <span class="cell-sub">累计</span>
      </div>
      <div class="java-cell">
        <span class="cell-label">守护线程</span>
        <span class="cell-value">{{ data.jvmDaemonThreadCount }}</span>
        <span class="cell-sub">当前</span>
      </div>
      <div class="java-cell">
        <span class="cell-label">运行时间</span>
        <span class="cell-value">{{ formatUptime(data.appUptimeSeconds) }}</span>
        <span class="cell-sub">自启动</span>
      </div>
    </div>
  </div>
</template>
```

#### 6.3.3 辅助方法

```typescript
// 堆内存颜色（>80% 红，>60% 橙，正常绿）
function heapColor(usage: number): string {
  if (usage > 80) return '#F56C6C'
  if (usage > 60) return '#E6A23C'
  return '#67C23A'
}

// 格式化秒数为人类可读
function formatSeconds(s: number): string {
  if (s < 60) return s.toFixed(2) + 's'
  if (s < 3600) return (s / 60).toFixed(1) + 'min'
  return (s / 3600).toFixed(1) + 'h'
}

// 格式化大数字
function formatNumber(n: number): string {
  if (n >= 1_000_000) return (n / 1_000_000).toFixed(1) + 'M'
  if (n >= 1_000) return (n / 1_000).toFixed(1) + 'K'
  return String(Math.round(n))
}

// 格式化运行时间
function formatUptime(s: number): string {
  const d = Math.floor(s / 86400)
  const h = Math.floor((s % 86400) / 3600)
  if (d > 0) return `${d}d ${h}h`
  if (h > 0) return `${h}h ${Math.floor((s % 3600) / 60)}m`
  return `${Math.floor(s / 60)}m`
}
```

#### 6.3.4 实时查询适配

JAVA 机器的 `realtime` 数据获取方式与 OS/MYSQL 完全一致：前端通过 `getRealtimeMetrics(machineId)` 调用，后端根据 `osType` 自动分流到 JAVA 解析器。前端卡片中同样按 `refreshInterval` 频率轮询。

#### 6.3.5 详情弹窗适配

点击 JAVA 卡片的 "详情" 按钮，打开 `MonitorScanDialog`，展示从 `/actuator/prometheus` 获取的全量 JVM 指标（`getFullMetrics` + 分类），用户可定制感兴趣的指标到卡片上显示。

---

## 7. 实施步骤（推荐顺序）

| 步骤 | 内容 | 预估工作量 | 依赖 |
|------|------|-----------|------|
| **Step 1** | Flyway 迁移 `V11__add_java_os_type.sql` | 5 分钟 | 无 |
| **Step 2** | `MonitorMachine.java` 注释更新 | 1 分钟 | Step 1 |
| **Step 3** | `MetricDictionary.java` 新增 JVM 前缀规则 + 中文翻译 | 20 分钟 | 无 |
| **Step 4** | `MonitorRealtimeVO.java` 新增 JAVA 字段 | 5 分钟 | 无 |
| **Step 5** | `MonitorService.java` 新增 JAVA 分支 + JVM 解析方法 | 30 分钟 | Step 3, 4 |
| **Step 6** | 前端 `monitor.ts` 类型扩展 | 10 分钟 | Step 4 |
| **Step 7** | 前端 `MachineFormDialog.vue` 新增 JAVA 选项 | 10 分钟 | Step 6 |
| **Step 8** | 前端 `MachineCard.vue` 新增 JAVA 展示模式 | 45 分钟 | Step 6, 7 |
| **Step 9** | 联调测试（本地启动 → 添加 JAVA 机器 → 验证卡片展示） | 20 分钟 | Step 1-8 |

> 总预估工作量：约 2.5 小时

---

## 8. 技术要点与注意事项

### 8.1 网络可达性

- JAVA 类型机器的 `exporterPort` 填写**应用服务端口**（通常 8080），因为 Actuator 端点与应用共享端口
- `fetchMetrics()` 方法构造 URL 为 `http://{ip}:{exporterPort}/actuator/prometheus`
- 如果被测 Java 应用运行在独立管理端口（如 `management.server.port=8081`），则 `exporterPort` 应填写管理端口

### 8.2 指标名称差异

不同版本的 Spring Boot / Micrometer 可能产生略有不同的指标名（如旧版 `jvm_memory_used_bytes` vs 新版可能带更多标签）。解析时应使用 `extractMetricValue` 的简单行匹配，只按指标名匹配，忽略标签差异。

### 8.3 累加型指标（Counter）

GC 暂停秒数 (`jvm_gc_pause_seconds_sum`) 和 HTTP 请求数 (`http_server_requests_seconds_count`) 是 Counter 类型，值会持续累加。在实时卡片展示中直接显示累计值即可；在未来的走势图场景中需要做增量计算（`rate()`）。

### 8.4 零值兜底

参考 MySQL 扩展模式，所有 JAVA 专用字段在非 JAVA 类型时返回 0 或 -1，前端通过 `osType === 'JAVA'` 条件渲染，不会显示无意义的数据。

### 8.5 采样任务兼容

`MonitorSampleScheduler` 的 `collectSample()` 方法中已有 `"MYSQL".equals(osType)` 等分支判断。新增 JAVA 后，需在此处增加对 JAVA 系统指标（堆内存等）的采集逻辑，或将 JAVA 指标映射到通用 `metricIds` 体系中。

### 8.6 安全性

`/actuator/prometheus` 端点默认无需认证即可访问。如果生产环境引入了 Spring Security，需确保该端点对监控消费者（本应用自身）开放。

---

## 9. 测试要点

1. **基本连通性**：添加 IP 为本机 `127.0.0.1`、端口 8080 的 JAVA 机器，验证 `fetchMetrics()` 能正常获取数据
2. **堆内存解析**：验证 `parseJvmHeapUsage()` 返回值在 0-100 之间，且与实际 JVM 堆使用量一致
3. **GC 指标解析**：验证 GC 暂停秒数和次数能正确提取
4. **线程指标解析**：验证活动线程数和守护线程数正确
5. **CPU 指标解析**：验证 `process_cpu_usage` 能正确转换为百分比
6. **HTTP 指标解析**：发送几次请求后，验证请求计数增加
7. **前端卡片渲染**：JAVA 模式下正确显示堆内存进度条和六宫格指标
8. **不可达处理**：停止目标应用后，卡片显示 "无法连接" 状态（复用现有 reachable 逻辑）
9. **详情弹窗**：JAVA 机器的全量指标浏览弹窗正确显示所有 JVM 指标分类
10. **采样任务**：创建 JAVA 机器的采样任务，验证历史数据采集正确

---

## 10. 总结

本次变更完全遵循现有架构的扩展模式（与 MYSQL 接入保持一致），核心变更集中在 **一个 Service 方法** (`MonitorService.java` 新增 JAVA 解析器) 和 **两个前端组件** (`MachineFormDialog.vue` + `MachineCard.vue`)。后端 Controller、Mapper、Scheduler 均无需改动，前端 API 层和类型定义只需追加字段。

完成后的效果：在容量监控页面添加一台 "Java 应用" 类型的机器，输入目标 IP 和端口，即可像监控 Windows/Linux 一样实时查看 JVM 的堆内存、GC、线程、CPU 等核心运行时指标。

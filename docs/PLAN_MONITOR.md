# 轻量级自建服务器监控系统 — 实施计划

> **技术栈**：Spring Boot 3.4+ (Java 21 虚拟线程) + Vue 3 (Vite 6, TypeScript, Element Plus, ECharts) + MySQL 8.4
> **数据源**：对接开源 `node_exporter` (Linux, :9100) 与 `windows_exporter` (Windows, :9182) 的 Prometheus Text 格式输出
> **设计原则**：前端打开才采集（日常按需消费）+ 任务制高频采样（自动存表定时关闭）

---

## 1. 总体范围与交付物

| 序号 | 交付物 | 说明 |
|------|--------|------|
| 1 | Flyway 迁移脚本 `V5__create_monitor_tables.sql` | 4 张表 DDL |
| 2 | 后端 Entity × 4 | `MonitorMachine`, `MonitorAppPort`, `MonitorSampleTask`, `MonitorHistory` |
| 3 | 后端 Mapper × 4 | MyBatis Plus `BaseMapper` |
| 4 | 后端 Service | `MonitorService` (CRUD + 扫描解析 + 实时查询), `MonitorSampleScheduler` (采样守护) |
| 5 | 后端 Controller | `MonitorController` (所有 `/api/monitor/**` 接口) |
| 6 | 后端 Model/Record | 请求/响应 DTO |
| 7 | 前端 API 模块 | `src/api/monitor.ts` |
| 8 | 前端视图 × 2 | `CapacityMonitor.vue` (容量监控), `MonitorTrend.vue` (监控走势) |
| 9 | 前端组件 × 4 | `MachineCard.vue`, `MachineFormDialog.vue`, `MonitorScanDialog.vue`, `SampleTaskForm.vue` |
| 10 | 路由 + 侧边栏更新 | 新增两个菜单项 |

---

## 2. 数据库设计 (Flyway V5)

### 2.1 表结构

```sql
-- V5__create_monitor_tables.sql

-- 1. 机器监控主表
CREATE TABLE `monitor_machine` (
  `id` int NOT NULL AUTO_INCREMENT,
  `machine_name` varchar(100) NOT NULL COMMENT '机器别名',
  `ip` varchar(50) NOT NULL COMMENT '机器IP',
  `os_type` varchar(20) NOT NULL COMMENT 'WINDOWS 或 LINUX',
  `exporter_port` int NOT NULL DEFAULT '9100' COMMENT 'Exporter端口',
  `refresh_interval` int NOT NULL DEFAULT '3' COMMENT '前端刷新频率(秒)',
  `is_enabled` tinyint(1) NOT NULL DEFAULT '1' COMMENT '是否开启监控 1开启 0关闭',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='监控-机器主表';

-- 2. 自建应用/端口精细化配置表
CREATE TABLE `monitor_app_port` (
  `id` int NOT NULL AUTO_INCREMENT,
  `machine_id` int NOT NULL COMMENT '关联机器ID',
  `app_name` varchar(100) NOT NULL COMMENT '应用/服务别名',
  `port` int NOT NULL COMMENT '具体监听端口',
  `is_visible` tinyint(1) NOT NULL DEFAULT '1' COMMENT '日常大屏是否可见 1可见 0隐藏',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_machine_id` (`machine_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='监控-应用端口配置表';

-- 3. 采样任务控制表
CREATE TABLE `monitor_sample_task` (
  `id` int NOT NULL AUTO_INCREMENT,
  `machine_id` int NOT NULL COMMENT '关联机器ID',
  `task_name` varchar(100) NOT NULL COMMENT '任务名称/备注',
  `start_time` datetime NOT NULL COMMENT '任务开始采集时间',
  `end_time` datetime NOT NULL COMMENT '任务结束采集时间',
  `collect_interval` int NOT NULL DEFAULT '3' COMMENT '采集频率(秒)',
  `status` varchar(20) NOT NULL DEFAULT 'WAITING' COMMENT 'WAITING(等待), RUNNING(采集中), FINISHED(已结束)',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='监控-采样任务控制表';

-- 4. 采样历史流水数据表
CREATE TABLE `monitor_history` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `task_id` int NOT NULL COMMENT '关联任务ID',
  `cpu_usage` float NOT NULL COMMENT 'CPU使用率(%)',
  `mem_usage` float NOT NULL COMMENT '内存使用率(%)',
  `disk_usage` float NOT NULL COMMENT '主磁盘使用率(%)',
  `record_time` datetime NOT NULL COMMENT '记录生成时间',
  PRIMARY KEY (`id`),
  KEY `idx_task_time` (`task_id`, `record_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='监控-采样历史流水表';
```

### 2.2 级联删除约束

- 删除 `monitor_machine` 时，需级联删除关联的 `monitor_app_port` 和 `monitor_sample_task`
- 删除 `monitor_sample_task` 时，需级联删除关联的 `monitor_history`
- **实现方式**：在 Service 层使用 `@Transactional` 手动级联删除，不使用数据库外键（保持与项目风格一致）

### 2.3 Flyway 文件位置

```
java-apex-server/src/main/resources/db/migration/V5__create_monitor_tables.sql
```

---

## 3. 后端实现计划

### 3.1 文件结构

```
java-apex-server/src/main/java/com/apex/
├── entity/
│   ├── MonitorMachine.java
│   ├── MonitorAppPort.java
│   ├── MonitorSampleTask.java
│   └── MonitorHistory.java
├── mapper/
│   ├── MonitorMachineMapper.java
│   ├── MonitorAppPortMapper.java
│   ├── MonitorSampleTaskMapper.java
│   └── MonitorHistoryMapper.java
├── model/
│   ├── MonitorMachineDTO.java        (新增/修改机器)
│   ├── MonitorAppPortDTO.java        (定制端口)
│   ├── MonitorSampleTaskDTO.java     (新建任务)
│   ├── MonitorRealtimeVO.java        (实时指标返回 — Record)
│   ├── MonitorScanResultVO.java      (扫描结果 — Record)
│   └── MonitorHistoryVO.java         (历史数据点 — Record)
├── service/
│   ├── MonitorService.java           (核心业务逻辑)
│   └── MonitorSampleScheduler.java   (采样任务守护调度器)
└── controller/
    └── MonitorController.java        (REST API)
```

### 3.2 核心业务逻辑

#### 3.2.1 Exporter 文本解析器 (`MonitorService` 内部方法)

**输入**：从 `http://{ip}:{exporter_port}/metrics` 获取的 Prometheus Text 原始文本

**解析规则**（正则）：

| 指标 | Linux 线索 | Windows 线索 |
|------|-----------|-------------|
| CPU 使用率 | `node_cpu_seconds_total{mode="idle"}` → 计算 `100 - (idle增量/总量增量*100)` | `windows_cpu_time_total{mode="idle"}` → 同上计算 |
| 内存使用率 | `node_memory_MemTotal_bytes` / `node_memory_MemAvailable_bytes` | `windows_os_physical_memory_free_bytes` / `windows_cs_physical_memory_bytes` |
| 磁盘使用率 | `node_filesystem_size_bytes{mountpoint="/"}` / `node_filesystem_free_bytes{mountpoint="/"}` | `windows_logical_disk_size_bytes{volume="C:"}` / `windows_logical_disk_free_bytes{volume="C:"}` |
| TCP 监听端口 | `netstat_Tcp_CurrEstab` + 扫描含 `state="listen"` 的指标行 | 同左，指标前缀可能不同 |

**端口扫描逻辑**：
- 解析所有 TCP 相关指标，提取端口号
- 识别 `LISTEN` 状态的端口
- 返回端口列表 `[{port, state}]`

#### 3.2.2 实时查询接口逻辑

```
GET /api/monitor/machine/{machineId}/realtime
```

1. 查 `monitor_machine` 获取 IP + exporter_port
2. HTTP GET `http://{ip}:{exporter_port}/metrics`
3. 解析 CPU/内存/磁盘使用率
4. 查 `monitor_app_port` 获取该机器已定制的端口列表
5. 对每个已定制端口，检查 metrics 文本中是否存在 `state="listen"` → 绿灯(UP) / 红灯(DOWN)
6. 合并返回 `MonitorRealtimeVO`

#### 3.2.3 全量扫描接口

```
GET /api/monitor/machine/{machineId}/scan
```

1. HTTP GET metrics 文本
2. 解析出所有端口（不限定制）
3. 返回 `List<MonitorScanResultVO>`，每个条目包含端口号、状态、是否已定制、别名

#### 3.2.4 采样调度器 (`MonitorSampleScheduler`)

- **类级别**：`@Component` + `@Slf4j`
- **核心定时器**：`@Scheduled(fixedRate = 5000)` 每 5 秒扫描一次 `monitor_sample_task` 表
- **状态机**：
  - `WAITING` + 当前时间 ≥ `start_time` → 改为 `RUNNING`，提交虚拟线程执行采集
  - `RUNNING` + 当前时间 ≥ `end_time` → 改为 `FINISHED`，移除线程
- **采集线程**：每个 RUNNING 任务一个独立虚拟线程，按 `collect_interval` 频率采集 CPU/内存/磁盘，写入 `monitor_history`
- **线程管理**：使用 `ConcurrentHashMap<String, ScheduledFuture<?>>` 管理运行中的任务，支持取消

---

### 3.3 API 设计

> 所有接口前缀 `/api/monitor`，返回 `Result<T>`

#### 3.3.1 机器管理

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/api/monitor/machine/list` | 获取所有机器列表 |
| `POST` | `/api/monitor/machine` | 新增机器 |
| `PUT` | `/api/monitor/machine` | 修改机器 |
| `DELETE` | `/api/monitor/machine/{id}` | 删除机器 |
| `PUT` | `/api/monitor/machine/{id}/toggle` | 切换启用/禁用 |

#### 3.3.2 端口定制

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/api/monitor/machine/{machineId}/scan` | 全量扫描端口 |
| `GET` | `/api/monitor/machine/{machineId}/ports` | 获取已定制端口列表 |
| `POST` | `/api/monitor/machine/{machineId}/port` | 定制一个端口（含别名） |
| `DELETE` | `/api/monitor/machine/{machineId}/port/{portId}` | 取消定制 |

#### 3.3.3 实时监控

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/api/monitor/machine/{machineId}/realtime` | 获取单机实时指标（含已定制端口状态） |

#### 3.3.4 采样任务

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/api/monitor/sample/task/list` | 获取所有采样任务列表 |
| `POST` | `/api/monitor/sample/task` | 新建采样任务 |
| `DELETE` | `/api/monitor/sample/task/{id}` | 删除采样任务 |

#### 3.3.5 历史数据

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/api/monitor/sample/task/{taskId}/history` | 获取指定任务的全量历史数据点 |
| `GET` | `/api/monitor/sample/task/{taskId}/history/latest` | 获取指定任务最新一条数据点（RUNNING 状态下实时增量使用） |

---

## 4. 前端实现计划

### 4.1 文件结构

```
web-apex-vue/src/
├── api/
│   └── monitor.ts                          (所有监控 API 函数)
├── views/
│   ├── CapacityMonitor.vue                 (页面一：容量监控)
│   └── MonitorTrend.vue                    (页面二：监控走势)
├── components/
│   └── monitor/
│       ├── MachineCard.vue                 (机器卡片组件)
│       ├── MachineFormDialog.vue           (新增/编辑机器弹窗)
│       ├── MonitorScanDialog.vue           (全量扫描大弹窗)
│       └── SampleTaskForm.vue              (新建采样任务表单)
├── types/
│   └── monitor.ts                          (TypeScript 类型定义)
└── router/
    └── index.ts                            (新增路由)
```

### 4.2 TypeScript 类型定义 (`src/types/monitor.ts`)

```typescript
// 机器
export interface MonitorMachine {
  id: number
  machineName: string
  ip: string
  osType: 'WINDOWS' | 'LINUX'
  exporterPort: number
  refreshInterval: number
  isEnabled: boolean
}

// 实时指标
export interface MonitorRealtime {
  machineId: number
  cpuUsage: number        // -1 表示 Exporter 不可达
  memUsage: number        // -1 表示 Exporter 不可达
  diskUsage: number       // -1 表示 Exporter 不可达
  reachable: boolean      // Exporter 是否可达
  errorMsg?: string       // 不可达时的错误描述
  ports: PortStatus[]
}

// 端口状态
export interface PortStatus {
  portId: number | null       // null 表示未定制
  port: number
  appName: string             // 定制别名，未定制为空
  isCustomized: boolean
  isListening: boolean        // true=绿灯 false=红灯
}

// 扫描结果条目
export interface ScanResultItem {
  port: number
  appName: string
  isCustomized: boolean
  isListening: boolean
}

// 采样任务
export interface SampleTask {
  id: number
  machineId: number
  machineName: string
  taskName: string
  startTime: string
  endTime: string
  collectInterval: number
  status: 'WAITING' | 'RUNNING' | 'FINISHED'
}

// 历史数据点
export interface HistoryPoint {
  id: number
  cpuUsage: number
  memUsage: number
  diskUsage: number
  recordTime: string
}
```

### 4.3 API 模块 (`src/api/monitor.ts`)

```typescript
import request from '@/utils/request'
import type { MonitorMachine, MonitorRealtime, ScanResultItem, PortStatus, SampleTask, HistoryPoint } from '@/types/monitor'

// ============ 机器管理 ============
export function getMachineList(): Promise<MonitorMachine[]>
export function addMachine(data: Partial<MonitorMachine>): Promise<MonitorMachine>
export function updateMachine(data: MonitorMachine): Promise<void>
export function deleteMachine(id: number): Promise<void>
export function toggleMachine(id: number): Promise<void>

// ============ 扫描与端口定制 ============
export function scanPorts(machineId: number): Promise<ScanResultItem[]>
export function getCustomizedPorts(machineId: number): Promise<PortStatus[]>
export function addCustomPort(machineId: number, port: number, appName: string): Promise<void>
export function removeCustomPort(machineId: number, portId: number): Promise<void>

// ============ 实时数据 ============
export function getRealtimeMetrics(machineId: number): Promise<MonitorRealtime>

// ============ 采样任务 ============
export function getSampleTaskList(): Promise<SampleTask[]>
export function createSampleTask(data: Partial<SampleTask>): Promise<void>
export function deleteSampleTask(id: number): Promise<void>
export function getTaskHistory(taskId: number): Promise<HistoryPoint[]>
export function getTaskHistoryLatest(taskId: number): Promise<HistoryPoint>
```

### 4.4 页面一：容量监控 (`CapacityMonitor.vue`)

#### 布局结构

```
┌──────────────────────────────────────────────┐
│  容量监控                      [+ 添加机器]   │  ← 顶部操作栏
├──────────────────────────────────────────────┤
│  ┌──────────┐  ┌──────────┐  ┌──────────┐   │
│  │ 机器A     │  │ 机器B     │  │ 机器C     │   │  ← 机器卡片网格
│  │ CPU: 12%  │  │ CPU: 45%  │  │ ⚠ 未定制  │   │    (CSS Grid)
│  │ 内存: 60% │  │ [MySQL]🟢 │  │           │   │
│  │ [详情]    │  │ [详情]    │  │ [详情]    │   │
│  └──────────┘  └──────────┘  └──────────┘   │
└──────────────────────────────────────────────┘
```

#### 核心交互逻辑

1. **`onMounted`**：调用 `getMachineList()` 加载机器列表
2. **已定制机器**：每个卡片启动独立 `setInterval`，按 `refreshInterval` 频率调用 `getRealtimeMetrics(machineId)`
3. **未定制机器**：卡片中心显示灰色提示文字 `"⚠️ 暂未定制化监控指标，请点击详情配置"`
4. **卡片 `isEnabled=false`**：卡片整体灰显，停止定时器
5. **【详情】按钮**：打开 `MonitorScanDialog` 大弹窗
6. **弹窗关闭回调**：刷新对应机器卡片的实时数据
7. **`onUnmounted`**：清理所有定时器

#### 卡片轮询注意事项

- 使用 `ref<Map<number, ReturnType<typeof setInterval>>>` 管理定时器
- 卡片数据用 `reactive(new Map())` 或 `ref<Record<number, MonitorRealtime>>()` 存储
- 定时器回调中仅更新对应机器的数据，避免全量刷新

### 4.5 组件：全量扫描大弹窗 (`MonitorScanDialog.vue`)

#### 弹窗结构

```
┌─────────────────────────────────────────────────┐
│  全局监控配置 — 机器A [Linux] | CPU:15% 内存:60% │  ← 顶栏常驻死活
│                                                  │
│  ┌──────────────────────────────────────────┐   │
│  │ 端口: 3306 [活动中]        [➕ 定制此指标] │   │  ← 未定制项
│  ├──────────────────────────────────────────┤   │
│  │ 端口: 8080 (我的Java应用) [活动中] [❌ 取消] │   │  ← 已定制项
│  ├──────────────────────────────────────────┤   │
│  │ 端口: 6379 [活动中]        [➕ 定制此指标] │   │
│  ├──────────────────────────────────────────┤   │
│  │ ...  (可滚动)                             │   │
│  └──────────────────────────────────────────┘   │
│                                                  │
│                              [关闭弹窗]           │
└─────────────────────────────────────────────────┘
```

#### 交互逻辑

1. **打开弹窗**：调用 `scanPorts(machineId)` 获取全量端口列表
2. **点击 ➕ 定制**：
   - 弹出 `ElMessageBox.prompt` 让用户输入别名
   - 回车后调用 `addCustomPort()`，该行即时变色
3. **点击 ❌ 取消**：调用 `removeCustomPort()`，该行恢复未定制状态
4. **关闭弹窗**：`emit('closed')`，父组件刷新卡片

### 4.6 页面二：监控走势 (`MonitorTrend.vue`)

#### 布局结构（左右分栏）

```
┌──────────────────┬──────────────────────────────────────────┐
│  左侧 (35%)      │  右侧 (65%)                              │
│                  │                                          │
│  ┌─新建任务────┐ │  ┌────────────────────────────────────┐  │
│  │ 选择机器    │ │  │                                    │  │
│  │ 开始时间    │ │  │   ECharts 折线图                     │  │
│  │ 结束时间    │ │  │   🔴 CPU  🔵 内存  🟢 磁盘          │  │
│  │ 采集频率    │ │  │                                    │  │
│  │ 备注        │ │  │   /\/\/\/\/\/\/\/\/\/\/\/\/\       │  │
│  │ [提交任务]  │ │  │                                    │  │
│  └─────────────┘ │  └────────────────────────────────────┘  │
│                  │                                          │
│  ┌─任务列表────┐ │                                          │
│  │ 任务1 已完成 │ │                                          │
│  │ 任务2 采集中 │ │                                          │
│  │ 任务3 等待中 │ │                                          │
│  └─────────────┘ │                                          │
└──────────────────┴──────────────────────────────────────────┘
```

#### 核心交互逻辑

1. **`onMounted`**：调用 `getSampleTaskList()` 加载任务列表
2. **新建任务**：表单提交调用 `createSampleTask()`，刷新任务列表
3. **选中任务**：左侧列表点击某任务
   - `FINISHED`：一次性加载全量 `getTaskHistory(taskId)`，完整渲染 ECharts
   - `RUNNING`：首次加载全量历史 + 启动定时器每隔 `collectInterval` 秒调 `getTaskHistoryLatest(taskId)`，数据 `push` 到图表 series
   - `WAITING`：右侧显示提示 "任务尚未开始，预计于 {startTime} 激活采样..."
4. **切换任务**：清理旧定时器，按新任务状态重新加载

#### ECharts 配置要点

- **固定颜色**：CPU `#F56C6C` (红)，内存 `#409EFF` (蓝)，磁盘 `#67C23A` (绿)
- **X 轴**：时间轴 (`recordTime`)
- **Y 轴**：百分比 0-100
- **DataZoom 组件**：底部滑块支持框选放大
- **Tooltip**：cross 触发，显示精确数值
- **RUNNING 状态下动态更新**：使用 `chart.setOption({ series: [...] })` 增量更新，而非全量 `setOption(option, true)`

### 4.7 路由与侧边栏更新

#### 路由 (`src/router/index.ts`) 新增

```typescript
{
  path: 'monitor',
  name: 'CapacityMonitor',
  component: () => import('@/views/CapacityMonitor.vue'),
  meta: { title: '容量监控' },
},
{
  path: 'monitor-trend',
  name: 'MonitorTrend',
  component: () => import('@/views/MonitorTrend.vue'),
  meta: { title: '监控走势' },
},
```

#### 侧边栏 (`Sidebar.vue`) 新增菜单项

```html
<el-menu-item index="/monitor">
  <el-icon><Monitor /></el-icon>
  <span>容量监控</span>
</el-menu-item>
<el-menu-item index="/monitor-trend">
  <el-icon><TrendCharts /></el-icon>
  <span>监控走势</span>
</el-menu-item>
```

---

## 5. 实施步骤（推荐顺序）

| 步骤 | 内容 | 依赖 |
|------|------|------|
| **Step 1** | Flyway 迁移脚本 `V5__create_monitor_tables.sql` | 无 |
| **Step 2** | 后端 Entity × 4 | Step 1 |
| **Step 3** | 后端 Mapper × 4 | Step 2 |
| **Step 4** | 后端 Model DTO/VO | Step 2 |
| **Step 5** | 后端 `MonitorService` (解析器 + CRUD + 扫描) | Step 3, 4 |
| **Step 6** | 后端 `MonitorSampleScheduler` (采样守护) | Step 3, 5 |
| **Step 7** | 后端 `MonitorController` | Step 5, 6 |
| **Step 8** | 前端 TypeScript 类型 + API 模块 | 无（可与后端并行） |
| **Step 9** | 前端 `MachineCard.vue` + `MachineFormDialog.vue` | Step 8 |
| **Step 10** | 前端 `MonitorScanDialog.vue` | Step 8 |
| **Step 11** | 前端 `CapacityMonitor.vue` 页面组装 | Step 9, 10 |
| **Step 12** | 前端 `SampleTaskForm.vue` | Step 8 |
| **Step 13** | 前端 `MonitorTrend.vue` 页面 + ECharts 集成 | Step 12 |
| **Step 14** | 路由 + 侧边栏更新 | Step 11, 13 |

---

## 6. 技术要点与风险

### 6.1 后端

| 要点 | 说明 |
|------|------|
| **HTTP 客户端** | 使用 Java 11+ 内置 `java.net.http.HttpClient`，配合虚拟线程实现高并发请求 Exporter |
| **Exporter 超时** | 设置 5 秒连接超时 + 10 秒读取超时，防止某台机器不可达阻塞整个请求 |
| **采样守护** | 使用 `ScheduledExecutorService` + 虚拟线程工厂，确保每个采样任务独立执行互不影响 |
| **Expoter 不可达** | 捕获 `IOException`，返回 `reachable=false` + `errorMsg`，前端显示 "无法连接" |
| **调度器开关** | 通过 `application.yml` 的 `apex.monitor.scheduler.enabled` 控制（默认 true），方便开发时关闭 |
| **调度器重启恢复** | 应用启动时（`@PostConstruct`），将表中所有 `RUNNING` 状态但实际 `end_time` 已过的任务批量改为 `FINISHED`；对仍在时间范围内的 `RUNNING` 任务重新提交采集线程，防止重启导致任务丢失 |
| **全局异常处理** | 复用现有 `GlobalExceptionHandler`，监控相关异常统一返回 `Result.error()` |

### 6.2 前端

| 要点 | 说明 |
|------|------|
| **ECharts 引入** | `npm install echarts`，按需引入折线图、DataZoom、Tooltip 等组件 |
| **定时器清理** | 所有 `setInterval` 必须在 `onUnmounted` 中清理，防止内存泄漏 |
| **大弹窗性能** | 端口列表超过 100 条时考虑虚拟滚动（`el-table-v2` 或 vxe-table），正常场景直接 `el-table` |
| **响应式卡片网格** | CSS Grid `grid-template-columns: repeat(auto-fill, minmax(340px, 1fr))` 自适应 |
| **请求去重** | 快速切换页面/关闭弹窗时，使用 `AbortController` 取消进行中的请求 |

### 6.3 依赖新增

- **前端**：`echarts` (无额外依赖，Element Plus 已就绪)
- **后端**：无额外 Maven 依赖（使用 JDK 内置 HttpClient + Spring Scheduling）

---

## 7. 测试要点

1. **Exporter 解析正确性**：分别针对 Linux `node_exporter` 和 Windows `windows_exporter` 的真实 metrics 输出做单元测试
2. **采样调度器状态机**：WAITING → RUNNING → FINISHED 转换正确，边界时间（startTime = endTime, 过去时间）处理
3. **前端定时器生命周期**：页面切换、弹窗关闭时定时器正确清理
4. **Exporter 不可达**：前端显示友好提示而非白屏/报错
5. **并发采样**：多台机器同时创建任务，互不干扰

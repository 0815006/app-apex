/**
 * 监控模块 TypeScript 类型定义。
 */

// ============ 机器管理 ============

export interface MonitorMachine {
  id: number
  machineName: string
  ip: string
  osType: 'WINDOWS' | 'LINUX'
  exporterPort: number
  refreshInterval: number
  isEnabled: boolean
}

export interface MonitorMachineForm {
  id?: number
  machineName: string
  ip: string
  osType: string
  exporterPort: number
  refreshInterval: number
}

// ============ 实时指标 ============

export interface MonitorRealtime {
  machineId: number
  cpuUsage: number       // -1 表示 Exporter 不可达
  memUsage: number       // -1 表示 Exporter 不可达
  diskUsage: number      // -1 表示 Exporter 不可达
  networkRxBytes: number
  networkTxBytes: number
  uptimeSeconds: number
  loadAvg1: number       // Linux only
  loadAvg5: number       // Linux only
  loadAvg15: number      // Linux only
  reachable: boolean
  errorMsg: string | null
  ports: CustomMetricStatus[]   // 定制指标状态列表（复用原 ports 字段名保持兼容）
}

/** 定制指标在卡片上的实时状态 */
export interface CustomMetricStatus {
  customMetricId: number | null  // 定制记录ID，null 表示未定制
  metricName: string             // 展示文本：displayName=当前值
  displayValue: string           // 当前值
  customized: boolean
  online: boolean                // true=指标在线 false=已丢失
}

// ============ 全量指标浏览器（MonitorScanDialog 重构后使用） ============

/** Exporter 返回的单条指标项 */
export interface MonitorMetricItem {
  metricKey: string         // 指标唯一标识（含标签）
  metricName: string        // 纯指标名
  chineseName: string       // 中文翻译
  value: string             // 当前值
  description: string       // 指标说明
  customized: boolean       // 是否已被用户定制
  customMetricId: number | null  // 定制记录ID，未定制则为 null
}

/** Exporter 指标分类 */
export interface MonitorMetricCategory {
  categoryKey: string                 // 分类标识（cpu/memory/disk/network/service/system/runtime/other）
  categoryName: string                // 分类中文名
  metrics: MonitorMetricItem[]
}

/** 全量指标接口响应 */
export interface MonitorFullMetrics {
  machineId: number
  reachable: boolean
  errorMsg: string | null
  categories: MonitorMetricCategory[]   // 按分类组织的当前指标
  orphaned: MonitorMetricItem[]         // 已定制但 Exporter 未返回的丢失指标
}

/** 已定制的指标记录（从后端 CustomMetricVO 返回） */
export interface MonitorCustomMetric {
  id: number
  machineId: number
  metricKey: string
  metricName: string
  displayName: string
  category: string
  isVisible: boolean
  createTime: string | null
}

/** 定制指标请求体 */
export interface AddCustomMetricReq {
  metricKey: string
  metricName: string
  displayName: string
  category: string
}

// ============ 采样任务 ============

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

export interface SampleTaskForm {
  machineId: number | null
  taskName: string
  startTime: string
  endTime: string
  collectInterval: number
}

// ============ 历史数据点 ============

export interface HistoryPoint {
  id: number
  cpuUsage: number
  memUsage: number
  diskUsage: number
  recordTime: string
}

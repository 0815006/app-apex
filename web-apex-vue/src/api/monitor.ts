import request from '@/utils/request'
import type {
  MonitorMachine,
  MonitorMachineForm,
  MonitorRealtime,
  MonitorFullMetrics,
  MonitorCustomMetric,
  AddCustomMetricReq,
  SampleTask,
  SampleTaskForm,
  HistoryPoint,
} from '@/types/monitor'

// ============ 机器管理 ============

export function getMachineList(): Promise<MonitorMachine[]> {
  return request.get('/monitor/machine/list').then((res) => res.data)
}

export function addMachine(data: MonitorMachineForm): Promise<MonitorMachine> {
  return request.post('/monitor/machine', data).then((res) => res.data)
}

export function updateMachine(data: MonitorMachineForm): Promise<void> {
  return request.put('/monitor/machine', data)
}

export function deleteMachine(id: number): Promise<void> {
  return request.delete(`/monitor/machine/${id}`)
}

export function toggleMachine(id: number): Promise<void> {
  return request.put(`/monitor/machine/${id}/toggle`)
}

// ============ 全量指标浏览与定制 ============

/** 获取机器 Exporter 返回的全量指标（含分类和丢失的定制指标） */
export function getFullMetrics(machineId: number): Promise<MonitorFullMetrics> {
  return request.get(`/monitor/machine/${machineId}/metrics`).then((res) => res.data)
}

/** 获取机器已定制的指标列表 */
export function getCustomizedMetrics(machineId: number): Promise<MonitorCustomMetric[]> {
  return request.get(`/monitor/machine/${machineId}/custom-metrics`).then((res) => res.data)
}

/** 定制一个指标 */
export function addCustomMetric(machineId: number, data: AddCustomMetricReq): Promise<void> {
  return request.post(`/monitor/machine/${machineId}/custom-metric`, data)
}

/** 取消定制一个指标 */
export function removeCustomMetric(machineId: number, metricId: number): Promise<void> {
  return request.delete(`/monitor/machine/${machineId}/custom-metric/${metricId}`)
}

// ============ 实时数据 ============

export function getRealtimeMetrics(machineId: number): Promise<MonitorRealtime> {
  return request.get(`/monitor/machine/${machineId}/realtime`).then((res) => res.data)
}

// ============ 采样任务 ============

export function getSampleTaskList(): Promise<SampleTask[]> {
  return request.get('/monitor/sample/task/list').then((res) => res.data)
}

export function createSampleTask(data: SampleTaskForm): Promise<void> {
  return request.post('/monitor/sample/task', data)
}

export function deleteSampleTask(id: number): Promise<void> {
  return request.delete(`/monitor/sample/task/${id}`)
}

export function getTaskHistory(taskId: number): Promise<HistoryPoint[]> {
  return request.get(`/monitor/sample/task/${taskId}/history`).then((res) => res.data)
}

export function getTaskHistoryLatest(taskId: number): Promise<HistoryPoint | null> {
  return request.get(`/monitor/sample/task/${taskId}/history/latest`).then((res) => res.data)
}

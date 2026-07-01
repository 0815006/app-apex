<template>
  <div class="monitor-trend">
    <!-- 左侧栏 -->
    <div class="trend-left">
      <div class="left-section">
        <div class="section-header">
          <h4 class="section-title">采样任务</h4>
          <el-button type="primary" size="small" @click="showTaskDialog = true">
            <el-icon><Plus /></el-icon>
            新建
          </el-button>
        </div>
        <SampleTaskForm
          v-model:visible="showTaskDialog"
          :machines="machines"
          @submitted="loadTasks"
        />
      </div>

      <div class="left-section task-list-section">
        <h4 class="section-title">历史列表</h4>
        <div v-loading="taskLoading" class="task-table-wrapper">
          <template v-if="tasks.length > 0">
            <div
              v-for="t in tasks"
              :key="t.id"
              class="task-row"
              :class="{ active: selectedTask?.id === t.id }"
              @click="selectTask(t)"
            >
              <div class="task-row-header">
                <span class="task-name">{{ t.taskName }}</span>
                <el-tag
                  :type="statusTagType(t.status)"
                  size="small"
                  effect="dark"
                >
                  {{ statusLabel(t.status) }}
                </el-tag>
              </div>
              <div class="task-row-sub">
                <span>{{ t.machineName }}</span>
                <span>{{ formatTime(t.startTime) }} ~ {{ formatTime(t.endTime) }}</span>
              </div>
              <div class="task-row-meta">
                频率: {{ t.collectInterval }}秒
              </div>
            </div>
          </template>
          <el-empty v-else description="暂无采样任务" :image-size="80" />
        </div>
      </div>
    </div>

    <!-- 右侧展示区 -->
    <div class="trend-right">
      <!-- 未选择任务 -->
      <div v-if="!selectedTask" class="right-placeholder">
        <el-icon :size="48" color="#C0C4CC"><TrendCharts /></el-icon>
        <p>请在左侧选择或创建一个采样任务以查看波动图</p>
      </div>

      <!-- WAITING 状态 -->
      <div v-else-if="selectedTask.status === 'WAITING'" class="right-placeholder">
        <el-icon :size="48" color="#E6A23C"><Clock /></el-icon>
        <p>任务尚未开始，预计于 {{ formatTime(selectedTask.startTime) }} 激活采样...</p>
      </div>

      <!-- RUNNING 或 FINISHED 状态 — ECharts 图 -->
      <div v-else class="chart-container">
        <div class="chart-legend">
          <span class="legend-item"><i style="background:#F56C6C"></i> CPU</span>
          <span class="legend-item"><i style="background:#409EFF"></i> 内存</span>
          <span class="legend-item"><i style="background:#67C23A"></i> 磁盘</span>
          <el-tag
            :type="selectedTask.status === 'RUNNING' ? 'warning' : 'success'"
            size="small"
            effect="dark"
            style="margin-left: auto"
          >
            {{ selectedTask.status === 'RUNNING' ? '🟡 采集中' : '🟢 已结束' }}
          </el-tag>
        </div>
        <div ref="chartRef" class="chart-body" v-loading="chartLoading"></div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, onMounted, onUnmounted, nextTick } from 'vue'
import { ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import type { MonitorMachine, SampleTask, HistoryPoint } from '@/types/monitor'
import { getMachineList, getSampleTaskList, getTaskHistory, getTaskHistoryLatest, deleteSampleTask } from '@/api/monitor'
import SampleTaskForm from '@/components/monitor/SampleTaskForm.vue'
import * as echarts from 'echarts/core'
import { LineChart } from 'echarts/charts'
import {
  TitleComponent,
  TooltipComponent,
  GridComponent,
  LegendComponent,
  DataZoomComponent,
} from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'

echarts.use([
  LineChart,
  TitleComponent,
  TooltipComponent,
  GridComponent,
  LegendComponent,
  DataZoomComponent,
  CanvasRenderer,
])

// ============ 弹窗控制 ============
const showTaskDialog = ref(false)

// ============ 机器列表 ============
const machines = ref<MonitorMachine[]>([])
async function loadMachines() {
  try {
    machines.value = await getMachineList()
  } catch {
    machines.value = []
  }
}

// ============ 任务列表 ============
const tasks = ref<SampleTask[]>([])
const taskLoading = ref(false)
async function loadTasks() {
  taskLoading.value = true
  try {
    tasks.value = await getSampleTaskList()
  } catch {
    tasks.value = []
  } finally {
    taskLoading.value = false
  }
}

// ============ 选中任务 ============
const selectedTask = ref<SampleTask | null>(null)
let pollTimer: ReturnType<typeof setInterval> | null = null

function selectTask(task: SampleTask) {
  selectedTask.value = task
  // 清理旧定时器
  stopPolling()

  if (task.status === 'FINISHED') {
    loadFullHistory(task.id)
  } else if (task.status === 'RUNNING') {
    loadFullHistory(task.id).then(() => startPolling(task))
  }
  // WAITING 仅显示提示
}

// ============ ECharts ============
const chartRef = ref<HTMLDivElement | null>(null)
const chartLoading = ref(false)
let chartInstance: echarts.ECharts | null = null
const historyData = ref<HistoryPoint[]>([])

const CHART_COLORS = {
  cpu: '#F56C6C',
  mem: '#409EFF',
  disk: '#67C23A',
}

function initChart() {
  if (!chartRef.value) return
  if (chartInstance) {
    window.removeEventListener('resize', handleResize)
    chartInstance.dispose()
  }
  chartInstance = echarts.init(chartRef.value)
  window.addEventListener('resize', handleResize)
}

function handleResize() {
  chartInstance?.resize()
}

function getBaseOption() {
  return {
    tooltip: {
      trigger: 'axis' as const,
      axisPointer: { type: 'cross' as const },
      valueFormatter: (value: unknown) => {
        if (typeof value === 'number') {
          return value === -1 ? '--' : value.toFixed(1) + '%'
        }
        return String(value)
      },
    },
    legend: {
      data: ['CPU', '内存', '磁盘'],
      bottom: 30,
    },
    grid: {
      top: 20,
      right: 30,
      bottom: 80,
      left: 50,
    },
    xAxis: {
      type: 'time' as const,
      axisLabel: {
        formatter: (value: number) => {
          const d = new Date(value)
          return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}:${String(d.getSeconds()).padStart(2, '0')}`
        },
      },
    },
    yAxis: {
      type: 'value' as const,
      name: '使用率 (%)',
      min: 0,
      max: 100,
      axisLabel: { formatter: '{value}%' },
    },
    dataZoom: [
      { type: 'slider' as const, start: 0, end: 100, bottom: 0 },
      { type: 'inside' as const },
    ],
    series: [
      {
        name: 'CPU',
        type: 'line',
        smooth: true,
        showSymbol: false,
        lineStyle: { color: CHART_COLORS.cpu, width: 2 },
        itemStyle: { color: CHART_COLORS.cpu },
        data: [] as [number, number][],
      },
      {
        name: '内存',
        type: 'line',
        smooth: true,
        showSymbol: false,
        lineStyle: { color: CHART_COLORS.mem, width: 2 },
        itemStyle: { color: CHART_COLORS.mem },
        data: [] as [number, number][],
      },
      {
        name: '磁盘',
        type: 'line',
        smooth: true,
        showSymbol: false,
        lineStyle: { color: CHART_COLORS.disk, width: 2 },
        itemStyle: { color: CHART_COLORS.disk },
        data: [] as [number, number][],
      },
    ],
  }
}

function mapDataToSeries(data: HistoryPoint[]): [number[][], number[][], number[][]] {
  const cpuData: number[][] = []
  const memData: number[][] = []
  const diskData: number[][] = []
  for (const p of data) {
    const t = new Date(p.recordTime).getTime()
    cpuData.push([t, p.cpuUsage === -1 ? null as unknown as number : p.cpuUsage])
    memData.push([t, p.memUsage === -1 ? null as unknown as number : p.memUsage])
    diskData.push([t, p.diskUsage === -1 ? null as unknown as number : p.diskUsage])
  }
  return [cpuData, memData, diskData]
}

function renderChart(data: HistoryPoint[]) {
  if (!chartInstance) return
  const [cpu, mem, disk] = mapDataToSeries(data)
  chartInstance.setOption({
    ...getBaseOption(),
    series: [
      { ...getBaseOption().series[0], data: cpu },
      { ...getBaseOption().series[1], data: mem },
      { ...getBaseOption().series[2], data: disk },
    ],
  }, true)
}

function appendDataPoint(point: HistoryPoint) {
  if (!chartInstance) return
  const t = new Date(point.recordTime).getTime()
  const cpuVal = point.cpuUsage === -1 ? null : point.cpuUsage
  const memVal = point.memUsage === -1 ? null : point.memUsage
  const diskVal = point.diskUsage === -1 ? null : point.diskUsage

  chartInstance.setOption({
    series: [
      { data: [[t, cpuVal]] },
      { data: [[t, memVal]] },
      { data: [[t, diskVal]] },
    ],
  })
}

async function loadFullHistory(taskId: number) {
  chartLoading.value = true
  try {
    historyData.value = await getTaskHistory(taskId)
    await nextTick()
    if (!chartInstance) initChart()
    renderChart(historyData.value)
  } catch {
    historyData.value = []
  } finally {
    chartLoading.value = false
  }
}

async function fetchLatest(taskId: number) {
  try {
    const point = await getTaskHistoryLatest(taskId)
    if (point && point.id && selectedTask.value?.id === taskId) {
      // 检查是否已存在（去重）
      const lastPoint = historyData.value[historyData.value.length - 1]
      if (!lastPoint || point.id !== lastPoint.id) {
        historyData.value.push(point)
        appendDataPoint(point)
      }
    }
  } catch {
    // 忽略
  }
}

function startPolling(task: SampleTask) {
  stopPolling()
  const interval = (task.collectInterval || 3) * 1000
  pollTimer = setInterval(() => {
    if (selectedTask.value?.id === task.id && selectedTask.value?.status === 'RUNNING') {
      fetchLatest(task.id)
    } else {
      stopPolling()
    }
  }, interval)
}

function stopPolling() {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

// ============ 工具方法 ============
function formatTime(t: string): string {
  if (!t) return ''
  // 统一格式化：2026-07-01T12:00:00 → 07-01 12:00
  const d = new Date(t)
  if (isNaN(d.getTime())) {
    // 尝试 "2026-07-01 12:00:00" 格式
    return t.substring(5, 16).replace(' ', ' ')
  }
  return `${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

function statusTagType(status: string) {
  switch (status) {
    case 'WAITING': return 'info'
    case 'RUNNING': return 'warning'
    case 'FINISHED': return 'success'
    default: return 'info'
  }
}

function statusLabel(status: string) {
  switch (status) {
    case 'WAITING': return '等待中'
    case 'RUNNING': return '采集中'
    case 'FINISHED': return '已完成'
    default: return status
  }
}

// ============ 生命周期 ============
onMounted(() => {
  loadMachines()
  loadTasks()
  nextTick(() => initChart())
})

onUnmounted(() => {
  stopPolling()
  window.removeEventListener('resize', handleResize)
  chartInstance?.dispose()
})
</script>

<style scoped>
.monitor-trend {
  display: flex;
  height: 100%;
  gap: 0;
}

/* 左侧栏 */
.trend-left {
  width: 35%;
  min-width: 320px;
  max-width: 440px;
  border-right: 1px solid #e4e7ed;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.left-section {
  padding: 16px;
}

.left-section + .left-section {
  border-top: 1px solid #ebeef5;
}

.task-list-section {
  flex: 1;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.section-header .section-title {
  margin: 0;
}

.section-title {
  margin: 0 0 12px 0;
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}

.task-table-wrapper {
  flex: 1;
  overflow-y: auto;
}

.task-row {
  padding: 10px 12px;
  border-radius: 6px;
  cursor: pointer;
  transition: background 0.15s;
  margin-bottom: 6px;
  border: 1px solid transparent;
}
.task-row:hover {
  background: #f5f7fa;
}
.task-row.active {
  background: #ecf5ff;
  border-color: #409EFF;
}

.task-row-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 4px;
}
.task-name {
  font-size: 14px;
  font-weight: 500;
  color: #303133;
}
.task-row-sub {
  display: flex;
  gap: 12px;
  font-size: 12px;
  color: #909399;
}
.task-row-meta {
  font-size: 12px;
  color: #C0C4CC;
  margin-top: 2px;
}

/* 右侧展示区 */
.trend-right {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.right-placeholder {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: #C0C4CC;
  gap: 12px;
}
.right-placeholder p {
  margin: 0;
  font-size: 14px;
}

.chart-container {
  flex: 1;
  display: flex;
  flex-direction: column;
  padding: 16px;
}

.chart-legend {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 8px;
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: #606266;
}
.legend-item i {
  display: inline-block;
  width: 12px;
  height: 12px;
  border-radius: 2px;
}

.chart-body {
  flex: 1;
  min-height: 0;
}
</style>

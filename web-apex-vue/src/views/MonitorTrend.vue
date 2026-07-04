<template>
  <div class="flex h-full w-full bg-slate-50/60 font-sans overflow-hidden">
    <!-- ========== 左侧栏：采样任务列表 ========== -->
    <aside class="w-80 bg-white/75 backdrop-blur-md border-r border-slate-200/50 flex flex-col shadow-sm z-10">
      <!-- 顶部工具栏 -->
      <div class="p-4 flex justify-between items-center border-b border-slate-100">
        <div class="flex items-center gap-2">
          <span class="text-xl">📊</span>
          <span class="text-base font-bold tracking-tight text-slate-800">采样任务</span>
        </div>
        <el-button size="small" type="primary" plain class="!rounded-full !px-3" @click="showTaskDialog = true">
          <el-icon class="mr-1"><Plus /></el-icon>新建
        </el-button>
      </div>

      <!-- 任务列表 -->
      <div class="flex-1 overflow-y-auto px-2 py-2" v-loading="taskLoading">
        <el-empty
          v-if="tasks.length === 0 && !taskLoading"
          description="暂无采样任务，点击上方新建"
          :image-size="80"
          class="mt-8"
        >
          <template #image>
            <div class="text-3xl">📊</div>
          </template>
        </el-empty>

        <div v-else class="trend-task-list">
          <div
            v-for="t in tasks"
            :key="t.id"
            :class="['task-item', { active: selectedTask?.id === t.id }]"
            @click="selectTask(t)"
          >
            <div class="task-item-header">
              <span class="task-item-name">{{ t.taskName }}</span>
              <el-tag
                :type="statusTagType(t.status)"
                size="small"
                effect="dark"
              >
                {{ statusLabel(t.status) }}
              </el-tag>
            </div>
            <div class="task-item-sub">
              <span class="truncate">{{ t.machineName }}</span>
              <span>{{ formatTime(t.startTime) }} ~ {{ formatTime(t.endTime) }}</span>
            </div>
            <div class="task-item-meta">
              <span>频率: {{ t.collectInterval }}秒</span>
              <span v-if="t.metricInfos && t.metricInfos.length > 0" class="task-item-metrics">
                <span class="text-slate-300 mx-1">|</span>
                <el-tag
                  v-for="mi in t.metricInfos.slice(0, 3)"
                  :key="mi.id"
                  size="small"
                  type="info"
                  class="metric-mini-tag"
                >
                  {{ mi.displayName }}
                </el-tag>
                <span v-if="t.metricInfos.length > 3" class="more-metrics">+{{ t.metricInfos.length - 3 }}</span>
              </span>
            </div>
          </div>
        </div>
      </div>

      <SampleTaskForm
        v-model:visible="showTaskDialog"
        :machines="machines"
        @submitted="loadTasks"
      />
    </aside>

    <!-- ========== 右侧展示区 ========== -->
    <main class="flex-1 flex flex-col bg-white overflow-hidden">
      <!-- 未选择任务 -->
      <div v-if="!selectedTask" class="flex-1 flex flex-col items-center justify-center text-slate-300 gap-3">
        <el-icon :size="48" color="#C0C4CC"><TrendCharts /></el-icon>
        <p class="m-0 text-sm">请在左侧选择或创建一个采样任务以查看波动图</p>
      </div>

      <!-- WAITING 状态 -->
      <div v-else-if="selectedTask.status === 'WAITING'" class="flex-1 flex flex-col items-center justify-center text-slate-400 gap-3">
        <el-icon :size="48" color="#E6A23C"><Clock /></el-icon>
        <p class="m-0 text-sm">任务尚未开始，预计于 {{ formatTime(selectedTask.startTime) }} 激活采样...</p>
      </div>

      <!-- RUNNING 或 FINISHED 状态 — 动态 ECharts 图 -->
      <div v-else class="flex-1 flex flex-col p-4 overflow-hidden">
        <div class="chart-legend">
          <span
            v-for="(info, idx) in selectedTask.metricInfos"
            :key="info.id"
            class="legend-item"
          >
            <i :style="{ background: getMetricColor(idx) }"></i>
            {{ info.displayName }}
          </span>
          <el-tag
            :type="selectedTask.status === 'RUNNING' ? 'warning' : 'success'"
            size="small"
            effect="dark"
            class="ml-auto"
          >
            {{ selectedTask.status === 'RUNNING' ? '🟡 采集中' : '🟢 已结束' }}
          </el-tag>
        </div>
        <div ref="chartRef" class="chart-body" v-loading="chartLoading"></div>
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, onMounted, onUnmounted, nextTick } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import type { MonitorMachine, SampleTask, HistoryPoint } from '@/types/monitor'
import { getMachineList, getSampleTaskList, getTaskHistory, getTaskHistoryLatest } from '@/api/monitor'
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

// ============ 动态调色板 ============
const COLOR_PALETTE = [
  '#F56C6C', '#409EFF', '#67C23A', '#E6A23C', '#8B5CF6',
  '#06B6D4', '#F97316', '#EC4899', '#14B8A6', '#6366F1',
  '#EF4444', '#3B82F6', '#22C55E', '#F59E0B', '#A855F7',
  '#10B981', '#D946EF', '#0EA5E9', '#84CC16', '#F43F5E',
]

function getMetricColor(index: number): string {
  return COLOR_PALETTE[index % COLOR_PALETTE.length]
}

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
  stopPolling()

  if (task.status === 'FINISHED') {
    loadFullHistory(task.id)
  } else if (task.status === 'RUNNING') {
    loadFullHistory(task.id).then(() => startPolling(task))
  }
}

// ============ ECharts ============
const chartRef = ref<HTMLDivElement | null>(null)
const chartLoading = ref(false)
let chartInstance: echarts.ECharts | null = null
const historyData = ref<HistoryPoint[]>([])

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

/** 根据选中任务的 metricInfos 动态生成 ECharts option */
function buildDynamicOption() {
  const task = selectedTask.value
  if (!task || task.metricInfos.length === 0) return {}

  const metricKeys = task.metricInfos.map(m => m.metricKey)
  const displayNames = task.metricInfos.map(m => m.displayName)

  return {
    tooltip: {
      trigger: 'axis' as const,
      axisPointer: { type: 'cross' as const },
    },
    legend: {
      data: displayNames,
      bottom: 30,
      type: 'scroll' as const,
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
      name: '值',
      axisLabel: {},
    },
    dataZoom: [
      { type: 'slider' as const, start: 0, end: 100, bottom: 0 },
      { type: 'inside' as const },
    ],
    series: metricKeys.map((key, idx) => ({
      name: displayNames[idx],
      type: 'line',
      smooth: true,
      showSymbol: false,
      lineStyle: { color: getMetricColor(idx), width: 2 },
      itemStyle: { color: getMetricColor(idx) },
      data: [] as [number, number][],
    })),
  }
}

/** 将 HistoryPoint[] 映射为动态 series 数据 */
function mapDataToSeries(data: HistoryPoint[], metricKeys: string[]): number[][][] {
  const result: number[][][] = metricKeys.map(() => [])
  for (const p of data) {
    const t = new Date(p.recordTime).getTime()
    for (let i = 0; i < metricKeys.length; i++) {
      const key = metricKeys[i]
      const val = p.values[key]
      result[i].push([t, val != null && val !== -1 ? val : (null as unknown as number)])
    }
  }
  return result
}

function renderChart(data: HistoryPoint[]) {
  if (!chartInstance) return
  const task = selectedTask.value
  if (!task || task.metricInfos.length === 0) return

  const metricKeys = task.metricInfos.map(m => m.metricKey)
  const baseOption = buildDynamicOption()
  const seriesData = mapDataToSeries(data, metricKeys)

  chartInstance.setOption({
    ...baseOption,
    series: seriesData.map((d, idx) => ({
      ...(baseOption.series as Array<Record<string, unknown>>)[idx],
      data: d,
    })),
  }, true)
}

function appendDataPoint(point: HistoryPoint) {
  if (!chartInstance) return
  const task = selectedTask.value
  if (!task || task.metricInfos.length === 0) return

  const t = new Date(point.recordTime).getTime()
  const metricKeys = task.metricInfos.map(m => m.metricKey)

  const seriesUpdates = metricKeys.map((key) => {
    const val = point.values[key]
    const v = val != null && val !== -1 ? val : null
    const pointData: [number, number | null][] = [[t, v]]
    return { data: pointData }
  })

  chartInstance.setOption({ series: seriesUpdates })
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
  const d = new Date(t)
  if (isNaN(d.getTime())) {
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
/* ========== 任务列表仿 Wiki/Chat 风格 ========== */
.trend-task-list {
  display: flex;
  flex-direction: column;
  gap: 1px;
}

.task-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 8px 10px;
  cursor: pointer;
  border-radius: 8px;
  transition: all 0.18s ease;
}

.task-item:hover {
  background-color: rgb(248 250 252);
}

.task-item.active {
  background-color: rgb(238 242 255);
  box-shadow: inset 0 0 0 1px rgba(99, 102, 241, 0.12);
}

.task-item.active .task-item-name {
  color: rgb(79 70 229);
}

.task-item-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.task-item-name {
  font-size: 14px;
  font-weight: 500;
  color: #303133;
  transition: color 0.18s;
}

.task-item-sub {
  display: flex;
  gap: 12px;
  font-size: 11px;
  color: #909399;
}

.task-item-meta {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 11px;
  color: #c0c4cc;
  flex-wrap: wrap;
}

.task-item-metrics {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.metric-mini-tag {
  transform: scale(0.8);
  transform-origin: left center;
}

.more-metrics {
  font-size: 11px;
  color: #909399;
}

/* ========== ECharts 图例与图表区 ========== */
.chart-legend {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 8px;
  flex-wrap: wrap;
  flex-shrink: 0;
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

<template>
  <div class="machine-card" :class="{ disabled: !machine.isEnabled }">
    <div class="card-header">
      <el-tag :type="machine.isEnabled ? 'success' : 'info'" size="small" effect="dark">
        {{ machine.isEnabled ? '运行中' : '已关闭' }}
      </el-tag>
      <el-dropdown trigger="click" @command="handleCommand">
        <el-button link type="default" size="small">
          <el-icon><MoreFilled /></el-icon>
        </el-button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="edit">
              <el-icon><Edit /></el-icon> 编辑
            </el-dropdown-item>
            <el-dropdown-item command="toggle">
              <el-icon><Switch /></el-icon>
              {{ machine.isEnabled ? '关闭监控' : '开启监控' }}
            </el-dropdown-item>
            <el-dropdown-item command="delete" divided>
              <el-icon><Delete /></el-icon> 删除
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>

    <div class="card-title">
      <span class="machine-name">{{ machine.machineName }}</span>
      <span class="machine-os">[{{ machine.osType }}]</span>
    </div>
    <div class="machine-ip">{{ machine.ip }}:{{ machine.exporterPort }}</div>

    <!-- Exporter 不可达错误 -->
    <div v-if="realtime && !realtime.reachable" class="metric-error">
      ⚠️ {{ realtime.errorMsg || '无法连接' }}
    </div>

    <!-- 核心指标区（OS 模式：CPU/内存/磁盘） -->
    <div v-if="realtime && machine.osType !== 'MYSQL'" class="core-metrics">
      <div class="core-row">
        <div class="core-item">
          <span class="core-label">CPU</span>
          <el-progress
            :percentage="clampPercent(realtime.cpuUsage)"
            :color="cpuColor"
            :stroke-width="8"
            :show-text="false"
          />
          <span class="core-value" :style="{ color: cpuColor }">{{ realtime.cpuUsage.toFixed(1) }}%</span>
        </div>
        <div class="core-item">
          <span class="core-label">内存</span>
          <el-progress
            :percentage="clampPercent(realtime.memUsage)"
            :color="memColor"
            :stroke-width="8"
            :show-text="false"
          />
          <span class="core-value" :style="{ color: memColor }">{{ realtime.memUsage.toFixed(1) }}%</span>
        </div>
        <div class="core-item">
          <span class="core-label">磁盘</span>
          <el-progress
            :percentage="clampPercent(realtime.diskUsage)"
            :color="diskColor"
            :stroke-width="8"
            :show-text="false"
          />
          <span class="core-value" :style="{ color: diskColor }">{{ realtime.diskUsage.toFixed(1) }}%</span>
        </div>
      </div>
      <div class="core-sub">
        <span class="core-sub-item">↙{{ formatBytes(realtime.networkRxBytes) }} ↗{{ formatBytes(realtime.networkTxBytes) }}</span>
        <span v-if="realtime.uptimeSeconds > 0" class="core-sub-item">{{ formatUptime(realtime.uptimeSeconds) }}</span>
        <span v-if="machine.osType === 'LINUX' && realtime.loadAvg1 > 0" class="core-sub-item">
          负载 {{ realtime.loadAvg1.toFixed(1) }}
        </span>
      </div>
    </div>

    <!-- 核心指标区（MySQL 模式：连接数/缓冲池/慢查询等） -->
    <div v-if="realtime && machine.osType === 'MYSQL'" class="core-metrics mysql-metrics">
      <!-- 连接数进度条 -->
      <div class="core-item">
        <span class="core-label">连接</span>
        <el-progress
          :percentage="mysqlConnectionPercent"
          :color="mysqlConnColor"
          :stroke-width="8"
          :show-text="false"
        />
        <span class="core-value" :style="{ color: mysqlConnColor }">
          {{ realtime.mysqlConnections }}/{{ realtime.mysqlMaxConnections }}
        </span>
      </div>
      <!-- 缓冲池命中率 -->
      <div class="core-item">
        <span class="core-label">命中</span>
        <el-progress
          :percentage="clampPercent(realtime.mysqlBufferPoolHitRate)"
          :color="bufferPoolColor"
          :stroke-width="8"
          :show-text="false"
        />
        <span class="core-value" :style="{ color: bufferPoolColor }">{{ realtime.mysqlBufferPoolHitRate.toFixed(1) }}%</span>
      </div>
      <!-- 活跃线程数 -->
      <div class="core-item">
        <span class="core-label">线程</span>
        <span class="core-value" style="color: #303133;">{{ realtime.mysqlThreadsRunning }}</span>
      </div>
      <div class="core-sub">
        <span class="core-sub-item">🐌 慢查: {{ formatCount(realtime.mysqlSlowQueries) }}</span>
        <span class="core-sub-item">📊 查询: {{ formatCount(realtime.mysqlQueriesTotal) }}</span>
        <span v-if="realtime.uptimeSeconds > 0" class="core-sub-item">{{ formatUptime(realtime.uptimeSeconds) }}</span>
      </div>
    </div>

    <!-- 定制指标区 -->
    <div v-if="realtime?.reachable && realtime.ports.length > 0" class="custom-section">
      <div class="custom-section-title">📌 定制指标</div>
      <div class="custom-tags">
        <span
          v-for="p in realtime.ports"
          :key="p.customMetricId ?? p.metricName"
          class="custom-tag"
          :class="{ lost: !p.online }"
        >
          <span class="tag-dot" :class="{ green: p.online, red: !p.online }"></span>
          <span class="tag-name">{{ p.metricName }}</span>
          <span v-if="p.online && p.displayValue && p.displayValue !== '—'" class="tag-val">{{ p.displayValue }}</span>
          <span v-else class="tag-lost">已丢失</span>
        </span>
      </div>
    </div>
    <div v-else-if="realtime?.reachable && realtime.ports.length === 0" class="custom-empty">
      💡 点击详情，从全量指标中定制关注项
    </div>

    <div class="card-footer">
      <el-button type="primary" link size="small" @click="$emit('openDetail', machine)">
        <el-icon><View /></el-icon> 详情
      </el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onUnmounted } from 'vue'
import { ElMessageBox, ElMessage } from 'element-plus'
import type { MonitorMachine, MonitorRealtime } from '@/types/monitor'
import { getRealtimeMetrics, toggleMachine, deleteMachine } from '@/api/monitor'

function formatBytes(bytes: number): string {
  if (bytes <= 0) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  let i = 0
  let size = bytes
  while (size >= 1024 && i < units.length - 1) {
    size /= 1024
    i++
  }
  return size.toFixed(i > 0 ? 1 : 0) + ' ' + units[i]
}

function formatUptime(seconds: number): string {
  if (seconds <= 0) return 'N/A'
  const d = Math.floor(seconds / 86400)
  const h = Math.floor((seconds % 86400) / 3600)
  const m = Math.floor((seconds % 3600) / 60)
  if (d > 0) return `${d}d ${h}h`
  if (h > 0) return `${h}h ${m}m`
  return `${m}m`
}

function clampPercent(v: number): number {
  if (v < 0) return 0
  if (v > 100) return 100
  return Math.round(v)
}

function formatCount(n: number): string {
  if (n < 1000) return String(n)
  if (n < 1000000) return (n / 1000).toFixed(1) + 'K'
  return (n / 1000000).toFixed(1) + 'M'
}

const props = defineProps<{
  machine: MonitorMachine
}>()

const emit = defineEmits<{
  (e: 'openDetail', machine: MonitorMachine): void
  (e: 'edit', machine: MonitorMachine): void
  (e: 'changed'): void
}>()

const realtime = ref<MonitorRealtime | null>(null)
let timer: ReturnType<typeof setInterval> | null = null

const cpuColor = computed(() => {
  if (!realtime.value) return '#E6E6E6'
  const v = realtime.value.cpuUsage
  if (v >= 90) return '#F56C6C'
  if (v >= 70) return '#E6A23C'
  return '#67C23A'
})

const memColor = computed(() => {
  if (!realtime.value) return '#E6E6E6'
  const v = realtime.value.memUsage
  if (v >= 90) return '#F56C6C'
  if (v >= 70) return '#E6A23C'
  return '#67C23A'
})

const diskColor = computed(() => {
  if (!realtime.value) return '#E6E6E6'
  const v = realtime.value.diskUsage
  if (v >= 90) return '#F56C6C'
  if (v >= 70) return '#E6A23C'
  return '#67C23A'
})

const mysqlConnectionPercent = computed(() => {
  if (!realtime.value || realtime.value.mysqlMaxConnections === 0) return 0
  return clampPercent((realtime.value.mysqlConnections / realtime.value.mysqlMaxConnections) * 100)
})

const mysqlConnColor = computed(() => {
  const v = mysqlConnectionPercent.value
  if (v >= 90) return '#F56C6C'
  if (v >= 70) return '#E6A23C'
  return '#67C23A'
})

const bufferPoolColor = computed(() => {
  const v = realtime.value?.mysqlBufferPoolHitRate ?? 100
  if (v < 90) return '#F56C6C'
  if (v < 95) return '#E6A23C'
  return '#67C23A'
})

function startPolling() {
  stopPolling()
  if (!props.machine.isEnabled) return
  const interval = (props.machine.refreshInterval || 3) * 1000
  fetchMetrics()
  timer = setInterval(fetchMetrics, interval)
}

function stopPolling() {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
}

async function fetchMetrics() {
  try {
    realtime.value = await getRealtimeMetrics(props.machine.id)
  } catch {
    // 忽略轮询错误
  }
}

async function handleCommand(cmd: string) {
  if (cmd === 'edit') {
    emit('edit', props.machine)
  } else if (cmd === 'toggle') {
    await toggleMachine(props.machine.id)
    ElMessage.success(props.machine.isEnabled ? '已关闭监控' : '已开启监控')
    emit('changed')
  } else if (cmd === 'delete') {
    try {
      await ElMessageBox.confirm(
        `确定要删除机器「${props.machine.machineName}」吗？关联的定制指标和采样任务也将一并删除。`,
        '删除确认',
        { type: 'warning' }
      )
      await deleteMachine(props.machine.id)
      ElMessage.success('已删除')
      emit('changed')
    } catch {
      // 取消
    }
  }
}

watch(() => props.machine.isEnabled, (v) => {
  if (v) startPolling()
  else stopPolling()
})

watch(() => props.machine.refreshInterval, () => {
  if (props.machine.isEnabled) startPolling()
})

// 初始化
if (props.machine.isEnabled) {
  startPolling()
}

onUnmounted(() => {
  stopPolling()
})
</script>

<style scoped>
.machine-card {
  background: #fff;
  border-radius: 10px;
  padding: 16px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  transition: box-shadow 0.2s, opacity 0.3s;
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-height: 280px;
}
.machine-card:hover {
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.1);
}
.machine-card.disabled {
  opacity: 0.55;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-title {
  display: flex;
  align-items: center;
  gap: 8px;
}
.machine-name {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}
.machine-os {
  font-size: 12px;
  color: #909399;
}
.machine-ip {
  font-size: 12px;
  color: #C0C4CC;
  font-family: 'JetBrains Mono', monospace;
}

.metric-error {
  color: #F56C6C;
  font-size: 13px;
  padding: 8px 12px;
  background: #FEF0F0;
  border-radius: 6px;
}

/* 核心指标 */
.core-metrics {
  background: #f8f9fb;
  border-radius: 8px;
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.core-row {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.core-item {
  display: flex;
  align-items: center;
  gap: 8px;
}
.core-label {
  font-size: 12px;
  color: #909399;
  width: 32px;
  flex-shrink: 0;
  text-align: right;
}
.core-item :deep(.el-progress) {
  flex: 1;
}
.core-value {
  font-size: 13px;
  font-weight: 600;
  font-family: 'JetBrains Mono', monospace;
  width: 48px;
  text-align: right;
  flex-shrink: 0;
}
.core-sub {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding-top: 4px;
  border-top: 1px solid #EBEEF5;
}
.core-sub-item {
  font-size: 11px;
  color: #909399;
  font-family: 'JetBrains Mono', monospace;
}

/* 定制指标 */
.custom-section {
  margin-top: 2px;
}
.custom-section-title {
  font-size: 12px;
  color: #909399;
  margin-bottom: 6px;
  font-weight: 500;
}
.custom-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.custom-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  background: #f0f5ff;
  padding: 3px 10px;
  border-radius: 12px;
  transition: background 0.2s;
}
.custom-tag.lost {
  background: #FEF0F0;
  border: 1px solid #FBC4C4;
}
.tag-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  flex-shrink: 0;
}
.tag-dot.green { background: #67C23A; }
.tag-dot.red { background: #F56C6C; }
.tag-name { color: #303133; font-weight: 500; }
.tag-val {
  color: #409EFF;
  font-family: 'JetBrains Mono', monospace;
  font-weight: 600;
}
.tag-lost {
  color: #F56C6C;
  font-weight: 600;
  font-size: 11px;
}

.custom-empty {
  text-align: center;
  color: #C0C4CC;
  font-size: 12px;
  padding: 12px 0;
}

.card-footer {
  margin-top: auto;
  padding-top: 10px;
  border-top: 1px solid #f0f0f0;
  text-align: center;
}
</style>

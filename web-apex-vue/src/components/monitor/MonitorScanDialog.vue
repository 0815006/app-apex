<template>
  <el-dialog
    v-model="visible"
    :title="`全量指标浏览 — ${machine?.machineName} [${machine?.osType}]`"
    width="850px"
    top="3vh"
    :close-on-click-modal="false"
    @opened="loadData"
    class="scan-dialog"
  >
    <!-- 顶栏：核心实时指标（OS 模式） -->
    <div class="scan-header" v-if="headerReachable != null && machine?.osType !== 'MYSQL' && !isScanJavaOsType">
      <span v-if="headerReachable" class="header-metrics">
        <span class="h-cpu">CPU: {{ headerCpu.toFixed(1) }}%</span>
        <span class="h-sep">|</span>
        <span class="h-mem">内存: {{ headerMem.toFixed(1) }}%</span>
        <span class="h-sep">|</span>
        <span class="h-disk">磁盘: {{ headerDisk.toFixed(1) }}%</span>
        <span class="h-sep">|</span>
        <span class="h-net">↙{{ formatBytes(headerNetRx) }} ↗{{ formatBytes(headerNetTx) }}</span>
        <span class="h-sep">|</span>
        <span class="h-uptime">{{ formatUptime(headerUptime) }}</span>
      </span>
      <span v-else class="h-error">⚠️ {{ headerErrorMsg || '无法连接 Exporter' }}</span>
    </div>

    <!-- 顶栏：核心实时指标（JAVA 模式） -->
    <div class="scan-header java-header" v-if="headerReachable != null && isScanJavaOsType">
      <span v-if="headerReachable" class="header-metrics">
        <span class="h-heap">
          堆: <strong>{{ safeNum(headerJvmHeapUsage).toFixed(1) }}%</strong>
        </span>
        <span class="h-sep">|</span>
        <span class="h-cpu">进程: {{ safeNum(headerProcessCpu).toFixed(1) }}%</span>
        <span class="h-sep">|</span>
        <span class="h-thread">线程: {{ safeNum(headerJvmThreadCount) }}</span>
        <span class="h-sep">|</span>
        <span class="h-gc">🧹 GC {{ safeNum(headerJvmGcPause).toFixed(2) }}s / {{ formatCount(safeNum(headerJvmGcCount)) }}次</span>
        <span v-if="safeNum(headerHttpRequestCount) > 0" class="h-sep">|</span>
        <span v-if="safeNum(headerHttpRequestCount) > 0" class="h-qps">🌐 请求 {{ formatCount(safeNum(headerHttpRequestCount)) }}</span>
        <span v-if="safeNum(headerAppUptime) > 0" class="h-sep">|</span>
        <span v-if="safeNum(headerAppUptime) > 0" class="h-uptime">⏱ {{ formatUptime(safeNum(headerAppUptime)) }}</span>
      </span>
      <span v-else class="h-error">⚠️ {{ headerErrorMsg || '无法连接 Exporter' }}</span>
    </div>

    <!-- 顶栏：核心实时指标（MySQL 模式） -->
    <div class="scan-header mysql-header" v-if="headerReachable != null && machine?.osType === 'MYSQL'">
      <span v-if="headerReachable" class="header-metrics">
        <span class="h-conn">
          连接: <strong>{{ headerMysqlConnections }}</strong>/{{ headerMysqlMaxConnections }}
          ({{ mysqlConnPct.toFixed(0) }}%)
        </span>
        <span class="h-sep">|</span>
        <span class="h-bp">命中: {{ headerMysqlBpHitRate.toFixed(1) }}%</span>
        <span class="h-sep">|</span>
        <span class="h-thread">线程: {{ headerMysqlThreadsRunning }}</span>
        <span class="h-sep">|</span>
        <span class="h-slow">🐌 慢查: {{ formatCount(headerMysqlSlowQueries) }}</span>
        <span class="h-sep">|</span>
        <span class="h-qps">📊 查询: {{ formatCount(headerMysqlQueriesTotal) }}</span>
        <span class="h-sep">|</span>
        <span class="h-uptime">{{ formatUptime(headerUptime) }}</span>
      </span>
      <span v-else class="h-error">⚠️ {{ headerErrorMsg || '无法连接 Exporter' }}</span>
    </div>

    <!-- 中间滚动区域：加载态 / 指标折叠面板 -->
    <div class="scan-scroll-area">
      <!-- 加载中 -->
      <div v-if="loading" class="scan-loading">
        <el-icon class="is-loading"><Loading /></el-icon>
        <span>正在拉取全量指标...</span>
      </div>

      <!-- 按分类展示所有指标 -->
      <template v-else-if="fullMetrics">
        <!-- Exporter 不可达提示 -->
        <el-alert
          v-if="!fullMetrics.reachable"
          :title="fullMetrics.errorMsg || 'Exporter 不可达'"
          type="error"
          show-icon
          :closable="false"
          style="margin-bottom: 12px"
        />

        <!-- 分类指标区域 — 折叠面板 -->
        <el-collapse v-if="fullMetrics.categories && fullMetrics.categories.length > 0" v-model="activeCategories">
          <el-collapse-item
            v-for="cat in fullMetrics.categories"
            :key="cat.categoryKey"
            :name="cat.categoryKey"
          >
            <template #title>
              <div class="category-title">
                <span class="cat-icon">{{ getCategoryIcon(cat.categoryKey) }}</span>
                <span class="cat-name">{{ cat.categoryName }}</span>
                <el-tag size="small" round>{{ cat.metrics.length }} 项</el-tag>
              </div>
            </template>

            <div class="metric-list">
              <div
                v-for="item in cat.metrics"
                :key="item.metricKey"
                class="metric-row"
                :class="{ customized: item.customized }"
              >
                <div class="metric-info">
                  <span class="metric-chinese">{{ item.chineseName }}</span>
                  <el-tooltip v-if="item.description" :content="item.description" placement="top">
                    <el-icon class="metric-desc-icon"><InfoFilled /></el-icon>
                  </el-tooltip>
                  <span class="metric-key-tag">{{ item.metricName }}</span>
                  <span v-if="item.labels" class="metric-labels-tag">{{ item.labels }}</span>
                </div>
                <div class="metric-right">
                  <code class="metric-value">{{ item.value }}</code>
                  <template v-if="item.customized">
                    <el-button type="danger" link size="small" @click="handleRemoveCustom(item)">
                      取消定制
                    </el-button>
                  </template>
                  <template v-else>
                    <el-button type="primary" link size="small" @click="handleCustomMetric(item)">
                      ➕ 定制
                    </el-button>
                  </template>
                </div>
              </div>
            </div>
          </el-collapse-item>
        </el-collapse>

        <el-empty v-else description="Exporter 未返回任何指标数据" :image-size="60" />

        <!-- 丢失的定制指标（红色醒目区域） -->
        <div v-if="fullMetrics.orphaned && fullMetrics.orphaned.length > 0" class="orphaned-section">
          <div class="orphaned-title">
            <span class="orphaned-icon">⚠️</span>
            <span>已丢失的定制指标（Exporter 不再返回这些指标，可能服务已下线）</span>
          </div>
          <div
            v-for="item in fullMetrics.orphaned"
            :key="item.metricKey"
            class="orphaned-row"
          >
            <div class="metric-info">
              <span class="metric-chinese">{{ item.chineseName }}</span>
              <span class="metric-key-tag">{{ item.metricName }}</span>
              <span v-if="item.labels" class="metric-labels-tag">{{ item.labels }}</span>
            </div>
            <div class="metric-right">
              <el-tag type="danger" size="small" effect="dark">已丢失</el-tag>
              <el-button type="danger" link size="small" @click="handleRemoveCustom(item)">
                移除定制
              </el-button>
            </div>
          </div>
        </div>
      </template>
    </div>

    <template #footer>
      <el-button @click="visible = false">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { MonitorMachine, MonitorFullMetrics, MonitorMetricItem } from '@/types/monitor'
import type { MonitorRealtime } from '@/types/monitor'
import { getFullMetrics, addCustomMetric, removeCustomMetric, getRealtimeMetrics } from '@/api/monitor'

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

function getCategoryIcon(key: string): string {
  const icons: Record<string, string> = {
    cpu: '🖥️',
    memory: '🧠',
    disk: '💾',
    network: '🌐',
    gc: '🧹',
    service: '⚙️',
    port: '🔌',
    process: '🏭',
    system: '🖧',
    runtime: '☕',
    connection: '🔗',
    query: '🔍',
    innodb: '🗄️',
    thread: '🧵',
    table_op: '📋',
    handler: '🤲',
    config: '🔧',
  }
  return icons[key] || '📊'
}

const props = defineProps<{
  modelValue: boolean
  machine: MonitorMachine | null
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', v: boolean): void
  (e: 'closed'): void
}>()

const visible = ref(props.modelValue)
watch(() => props.modelValue, (v) => { visible.value = v })
watch(visible, (v) => { emit('update:modelValue', v); if (!v) emit('closed') })

const loading = ref(false)
const savingRow = ref(false)
const fullMetrics = ref<MonitorFullMetrics | null>(null)
const activeCategories = ref<string[]>(['cpu', 'memory', 'disk', 'network'])

// 顶栏实时指标（OS 通用）
const headerReachable = ref<boolean | null>(null)
const headerCpu = ref(0)
const headerMem = ref(0)
const headerDisk = ref(0)
const headerNetRx = ref(0)
const headerNetTx = ref(0)
const headerUptime = ref(0)
const headerErrorMsg = ref('')

// 顶栏实时指标（MySQL 专用）
const headerMysqlConnections = ref(0)
const headerMysqlMaxConnections = ref(0)
const headerMysqlBpHitRate = ref(0)
const headerMysqlSlowQueries = ref(0)
const headerMysqlQueriesTotal = ref(0)
const headerMysqlThreadsRunning = ref(0)

// 顶栏实时指标（JAVA 专用）
const headerJvmHeapUsage = ref(0)
const headerProcessCpu = ref(0)
const headerJvmThreadCount = ref(0)
const headerJvmGcPause = ref(0)
const headerJvmGcCount = ref(0)
const headerHttpRequestCount = ref(0)
const headerAppUptime = ref(0)

/** JAVA osType 判定 */
const isScanJavaOsType = computed(() =>
  props.machine?.osType === 'JAVA_ACTUATOR' || props.machine?.osType === 'JAVA_JMX'
)

/** MySQL 连接数百分比 */
const mysqlConnPct = computed(() => {
  if (headerMysqlMaxConnections.value === 0) return 0
  return Math.round((headerMysqlConnections.value / headerMysqlMaxConnections.value) * 100)
})

function formatCount(n: number): string {
  if (n < 1000) return String(n)
  if (n < 1000000) return (n / 1000).toFixed(1) + 'K'
  return (n / 1000000).toFixed(1) + 'M'
}

/** 安全取值：若字段为 undefined/null/NaN，返回 0 */
function safeNum(val: number | undefined | null): number {
  if (val == null || Number.isNaN(val)) return 0
  return val
}

async function loadData() {
  if (!props.machine) return
  loading.value = true
  try {
    // 并行拉取全量指标和实时核心指标
    const [metrics, realtime] = await Promise.all([
      getFullMetrics(props.machine.id),
      getRealtimeMetrics(props.machine.id).catch(() => null),
    ])
    fullMetrics.value = metrics

    if (realtime) {
      headerReachable.value = realtime.reachable
      headerCpu.value = realtime.cpuUsage
      headerMem.value = realtime.memUsage
      headerDisk.value = realtime.diskUsage
      headerNetRx.value = realtime.networkRxBytes
      headerNetTx.value = realtime.networkTxBytes
      headerUptime.value = realtime.uptimeSeconds
      headerErrorMsg.value = realtime.errorMsg || ''
      // MySQL 专用字段
      headerMysqlConnections.value = realtime.mysqlConnections
      headerMysqlMaxConnections.value = realtime.mysqlMaxConnections
      headerMysqlBpHitRate.value = realtime.mysqlBufferPoolHitRate
      headerMysqlSlowQueries.value = realtime.mysqlSlowQueries
      headerMysqlQueriesTotal.value = realtime.mysqlQueriesTotal
      headerMysqlThreadsRunning.value = realtime.mysqlThreadsRunning
      // JAVA 专用字段
      headerJvmHeapUsage.value = safeNum(realtime.jvmHeapUsage)
      headerProcessCpu.value = safeNum(realtime.processCpuUsage)
      headerJvmThreadCount.value = safeNum(realtime.jvmThreadCount)
      headerJvmGcPause.value = safeNum(realtime.jvmGcPauseSeconds)
      headerJvmGcCount.value = safeNum(realtime.jvmGcCount)
      headerHttpRequestCount.value = safeNum(realtime.httpRequestCount)
      headerAppUptime.value = safeNum(realtime.appUptimeSeconds)
    } else {
      headerReachable.value = metrics.reachable
      headerErrorMsg.value = metrics.errorMsg || ''
    }
  } catch {
    fullMetrics.value = null
    headerReachable.value = false
    headerErrorMsg.value = '请求失败'
  } finally {
    loading.value = false
  }
}

async function handleCustomMetric(item: MonitorMetricItem) {
  if (!props.machine) return
  try {
    const { value: displayName } = await ElMessageBox.prompt(
      `为指标「${item.chineseName}」设置展示别名：`,
      '定制监控指标',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        inputPlaceholder: item.chineseName,
        inputValue: item.chineseName,
        inputValidator: (v: string) => v && v.trim().length > 0 ? true : '名称不能为空',
      }
    )
    if (!displayName) return

    savingRow.value = true
    await addCustomMetric(props.machine.id, {
      metricKey: item.metricKey,
      metricName: item.metricName,
      displayName: displayName.trim(),
      category: '',  // 后端会自动推断
    })

    // 更新本地状态
    item.customized = true
    ElMessage.success('已定制')
  } catch {
    // 取消
  } finally {
    savingRow.value = false
  }
}

async function handleRemoveCustom(item: MonitorMetricItem) {
  if (!props.machine || !item.customMetricId) return
  try {
    await ElMessageBox.confirm(
      `确定取消对「${item.chineseName}」的定制吗？`,
      '取消定制确认',
      { type: 'warning' }
    )
    savingRow.value = true
    await removeCustomMetric(props.machine.id, item.customMetricId)

    // 从本地状态中移除：如果在 orphaned 列表中则移除，否则取消标记
    if (fullMetrics.value) {
      const orphIdx = fullMetrics.value.orphaned.findIndex(o => o.metricKey === item.metricKey)
      if (orphIdx >= 0) {
        fullMetrics.value.orphaned.splice(orphIdx, 1)
      } else {
        item.customized = false
        item.customMetricId = null
      }
    }
    ElMessage.success('已取消定制')
  } catch {
    // 取消
  } finally {
    savingRow.value = false
  }
}
</script>

<style scoped>
/* 中间滚动容器：限定高度，独立滚动 */
.scan-scroll-area {
  max-height: calc(80vh - 180px);
  min-height: 100px;
  overflow-y: auto;
}

.scan-header {
  padding: 10px 16px;
  background: #f5f7fa;
  border-radius: 6px;
  margin-bottom: 12px;
  font-size: 14px;
  font-weight: 500;
}
.scan-header .header-metrics {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0;
}
.scan-header .h-cpu { color: #409EFF; }
.scan-header .h-mem { color: #67C23A; }
.scan-header .h-disk { color: #E6A23C; }
.scan-header .h-net { color: #00ACC1; font-size: 12px; }
.scan-header .h-uptime { color: #8D6E63; font-size: 12px; }
.scan-header .h-sep { color: #DCDFE6; margin: 0 8px; }
.scan-header .h-error { color: #F56C6C; }

/* MySQL 顶栏专属颜色 */
.scan-header .h-conn { color: #409EFF; }
.scan-header .h-conn strong { color: #303133; }
.scan-header .h-bp { color: #67C23A; }
.scan-header .h-thread { color: #E6A23C; }
.scan-header .h-slow { color: #F56C6C; }
.scan-header .h-qps { color: #00ACC1; }

.scan-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 40px;
  color: #909399;
}

/* 分类标题 */
.category-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 500;
}
.cat-icon {
  font-size: 16px;
}
.cat-name {
  margin-right: 4px;
}

/* 指标列表 */
.metric-list {
  padding: 0 4px;
}
.metric-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 12px;
  border-bottom: 1px solid #f5f5f5;
  transition: background 0.15s;
  font-size: 13px;
}
.metric-row:last-child {
  border-bottom: none;
}
.metric-row:hover {
  background: #f5f7fa;
}
.metric-row.customized {
  background: #ecf5ff;
}

.metric-info {
  display: flex;
  align-items: center;
  gap: 6px;
  flex: 1;
  min-width: 0;
}
.metric-chinese {
  color: #303133;
  font-weight: 500;
  white-space: nowrap;
}
.metric-desc-icon {
  font-size: 14px;
  color: #C0C4CC;
  cursor: help;
}
.metric-key-tag {
  font-size: 11px;
  color: #C0C4CC;
  font-family: 'JetBrains Mono', 'Cascadia Code', monospace;
}
/* 标签信息（如 core="0,0"），用不同颜色区分 */
.metric-labels-tag {
  font-size: 11px;
  color: #909399;
  background: #f0f2f5;
  padding: 1px 5px;
  border-radius: 3px;
  font-family: 'JetBrains Mono', 'Cascadia Code', monospace;
  white-space: nowrap;
}

.metric-right {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
}
.metric-value {
  font-family: 'JetBrains Mono', 'Cascadia Code', monospace;
  font-size: 13px;
  color: #409EFF;
  font-weight: 600;
  background: #f0f5ff;
  padding: 1px 8px;
  border-radius: 4px;
  min-width: 60px;
  text-align: right;
  white-space: nowrap;
}

/* 丢失指标区域 */
.orphaned-section {
  margin-top: 16px;
  border: 2px solid #F56C6C;
  border-radius: 8px;
  overflow: hidden;
}
.orphaned-title {
  background: #FEF0F0;
  color: #F56C6C;
  padding: 10px 16px;
  font-size: 14px;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 8px;
}
.orphaned-icon {
  font-size: 18px;
}
.orphaned-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 16px;
  background: #FDF6F6;
  border-bottom: 1px solid #FDE2E2;
  font-size: 13px;
}
.orphaned-row:last-child {
  border-bottom: none;
}
.orphaned-row .metric-chinese {
  color: #F56C6C;
  text-decoration: line-through;
}
/* orphaned 行中 labels 保持灰色 */
.orphaned-row .metric-labels-tag {
  color: #C0C4CC;
  background: #FDF6F6;
}
</style>

<!-- 非 scoped：穿透 Element Plus 弹窗结构 -->
<style>
.scan-dialog {
  max-height: 85vh;
}
</style>

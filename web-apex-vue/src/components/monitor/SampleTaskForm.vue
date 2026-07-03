<template>
  <el-dialog
    :model-value="visible"
    title="新建采样任务"
    width="560px"
    :close-on-click-modal="false"
    destroy-on-close
    @update:model-value="emit('update:visible', $event)"
  >
    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-width="90px"
      size="default"
      @submit.prevent
    >
      <el-form-item label="选择机器" prop="machineId">
        <el-select
          v-model="form.machineId"
          placeholder="请选择机器"
          style="width: 100%"
          @change="onMachineChange"
        >
          <el-option
            v-for="m in machines"
            :key="m.id"
            :label="`${m.machineName} (${m.ip})`"
            :value="m.id"
          />
        </el-select>
      </el-form-item>

      <el-form-item label="选择指标" prop="metricIds">
        <div v-if="form.machineId === null" class="metric-select-hint">
          请先选择机器
        </div>
        <template v-else-if="metricLoading">
          <el-icon class="is-loading"><Loading /></el-icon>
          <span style="margin-left: 8px; color: #909399;">加载指标中...</span>
        </template>
        <template v-else-if="availableMetrics.length === 0">
          <el-empty description="该机器暂无定制指标，请先在容量监控页面定制指标" :image-size="60">
            <template #image>
              <el-icon :size="48" color="#C0C4CC"><WarningFilled /></el-icon>
            </template>
          </el-empty>
        </template>
        <el-checkbox-group v-else v-model="form.metricIds" class="metric-checkbox-group">
          <div
            v-for="group in groupedMetrics"
            :key="group.category"
            class="metric-category-group"
          >
            <div class="category-label">{{ group.categoryName }}</div>
            <el-checkbox
              v-for="m in group.metrics"
              :key="m.id"
              :value="m.id"
              :label="m.id"
              class="metric-checkbox-item"
            >
              <span class="metric-display-name">{{ m.displayName }}</span>
              <el-tag size="small" type="info" class="metric-key-tag">{{ m.metricKey }}</el-tag>
            </el-checkbox>
          </div>
        </el-checkbox-group>
      </el-form-item>

      <el-form-item label="开始时间" prop="startTime">
        <el-date-picker
          v-model="form.startTime"
          type="datetime"
          placeholder="选择开始时间"
          style="width: 100%"
          :disabled-date="disabledPastDate"
          value-format="YYYY-MM-DDTHH:mm:ss"
        />
      </el-form-item>

      <el-form-item label="结束时间" prop="endTime">
        <el-date-picker
          v-model="form.endTime"
          type="datetime"
          placeholder="选择结束时间"
          style="width: 100%"
          :disabled-date="disabledPastDate"
          value-format="YYYY-MM-DDTHH:mm:ss"
        />
      </el-form-item>

      <el-form-item label="采集频率" prop="collectInterval">
        <el-select v-model="form.collectInterval" style="width: 100%">
          <el-option label="每 3 秒" :value="3" />
          <el-option label="每 5 秒" :value="5" />
          <el-option label="每 10 秒" :value="10" />
          <el-option label="每 30 秒" :value="30" />
        </el-select>
      </el-form-item>

      <el-form-item label="备注" prop="taskName">
        <el-input
          v-model="form.taskName"
          placeholder="如：压测观察"
          maxlength="100"
        />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="emit('update:visible', false)">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="handleSubmit">
        提交任务
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, watch } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Loading, WarningFilled } from '@element-plus/icons-vue'
import type { MonitorMachine, MonitorCustomMetric, SampleTaskForm } from '@/types/monitor'
import { createSampleTask, getCustomizedMetrics } from '@/api/monitor'

// ============ 分类名映射 ============
const CATEGORY_NAME_MAP: Record<string, string> = {
  cpu: 'CPU',
  memory: '内存',
  disk: '磁盘',
  network: '网络',
  service: '服务',
  system: '系统',
  runtime: '运行时',
  process: '进程',
  custom: '自定义',
  other: '其他',
}

// ============ Props / Emits ============
const props = defineProps<{
  machines: MonitorMachine[]
  visible: boolean
}>()

const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
  (e: 'submitted'): void
}>()

// ============ 表单 ============
const formRef = ref<FormInstance>()
const submitting = ref(false)

const form = reactive<SampleTaskForm>({
  machineId: null,
  taskName: '',
  startTime: '',
  endTime: '',
  collectInterval: 3,
  metricIds: [],
})

const rules: FormRules = {
  machineId: [{ required: true, message: '请选择机器', trigger: 'change' }],
  metricIds: [
    { type: 'array' as const, required: true, message: '至少选择一个指标', trigger: 'change', min: 1 },
  ],
  startTime: [{ required: true, message: '请选择开始时间', trigger: 'change' }],
  endTime: [{ required: true, message: '请选择结束时间', trigger: 'change' }],
  taskName: [{ required: true, message: '请填写备注', trigger: 'blur' }],
  collectInterval: [{ required: true, message: '请选择采集频率', trigger: 'change' }],
}

// ============ 指标加载与分组 ============
const availableMetrics = ref<MonitorCustomMetric[]>([])
const metricLoading = ref(false)

// 按分类分组
interface MetricGroup {
  category: string
  categoryName: string
  metrics: MonitorCustomMetric[]
}

const groupedMetrics = ref<MetricGroup[]>([])

function buildGroups(metrics: MonitorCustomMetric[]) {
  const map = new Map<string, MetricGroup>()
  for (const m of metrics) {
    const cat = m.category || 'other'
    if (!map.has(cat)) {
      map.set(cat, {
        category: cat,
        categoryName: CATEGORY_NAME_MAP[cat] || cat,
        metrics: [],
      })
    }
    map.get(cat)!.metrics.push(m)
  }
  // 固定分类顺序
  const order = ['cpu', 'memory', 'disk', 'network', 'service', 'system', 'runtime', 'process', 'custom', 'other']
  groupedMetrics.value = order
    .filter(k => map.has(k))
    .map(k => map.get(k)!)
  // 加上不在 order 里的
  for (const [k, g] of map) {
    if (!order.includes(k)) {
      groupedMetrics.value.push(g)
    }
  }
}

async function onMachineChange(machineId: number | null) {
  form.metricIds = []
  availableMetrics.value = []
  groupedMetrics.value = []
  if (machineId === null) return

  metricLoading.value = true
  try {
    availableMetrics.value = await getCustomizedMetrics(machineId)
    buildGroups(availableMetrics.value)
  } catch {
    availableMetrics.value = []
    groupedMetrics.value = []
  } finally {
    metricLoading.value = false
  }
}

// 弹窗关闭时重置
watch(() => props.visible, (val) => {
  if (!val) {
    form.machineId = null
    form.taskName = ''
    form.startTime = ''
    form.endTime = ''
    form.collectInterval = 3
    form.metricIds = []
    availableMetrics.value = []
    groupedMetrics.value = []
  }
})

// ============ 验证 & 提交 ============
function disabledPastDate(date: Date) {
  return date.getTime() < Date.now() - 60 * 1000
}

async function handleSubmit() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  if (form.endTime <= form.startTime) {
    ElMessage.error('结束时间必须晚于开始时间')
    return
  }

  submitting.value = true
  try {
    await createSampleTask({
      machineId: form.machineId!,
      taskName: form.taskName,
      startTime: form.startTime,
      endTime: form.endTime,
      collectInterval: form.collectInterval,
      metricIds: form.metricIds,
    })
    ElMessage.success('采样任务已创建')
    form.taskName = ''
    emit('submitted')
    emit('update:visible', false)
  } catch {
    // 错误已统一提示
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.metric-select-hint {
  color: #909399;
  font-size: 13px;
}

.metric-checkbox-group {
  width: 100%;
  max-height: 280px;
  overflow-y: auto;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  padding: 8px 12px;
}

.metric-category-group {
  margin-bottom: 8px;
}

.metric-category-group:last-child {
  margin-bottom: 0;
}

.category-label {
  font-size: 12px;
  font-weight: 600;
  color: #909399;
  padding: 4px 0 4px 0;
  border-bottom: 1px solid #ebeef5;
  margin-bottom: 4px;
}

.metric-checkbox-item {
  display: flex !important;
  align-items: center;
  width: 100%;
  margin-right: 0;
  padding: 3px 0;
}

.metric-display-name {
  font-size: 13px;
  color: #303133;
  margin-right: 8px;
}

.metric-key-tag {
  transform: scale(0.85);
  transform-origin: left center;
}
</style>

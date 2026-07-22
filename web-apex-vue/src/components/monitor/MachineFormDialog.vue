<template>
  <el-dialog
    v-model="visible"
    :title="isEdit ? '编辑机器' : '添加机器'"
    width="520px"
    :close-on-click-modal="false"
    @closed="resetForm"
  >
    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-width="110px"
      @submit.prevent
    >
      <el-form-item label="机器别名" prop="machineName">
        <el-input v-model="form.machineName" placeholder="如：生产服务器-01" maxlength="100" />
      </el-form-item>
      <el-form-item label="机器IP" prop="ip">
        <el-input v-model="form.ip" placeholder="如：192.168.1.100" maxlength="50" />
      </el-form-item>
      <el-form-item label="系统类型" prop="osType">
        <el-select v-model="form.osType" placeholder="请选择系统类型" style="width: 100%" @change="onOsTypeChange">
          <el-option label="Linux" value="LINUX" />
          <el-option label="Windows" value="WINDOWS" />
          <el-option label="MySQL" value="MYSQL" />
          <el-option label="Java (Actuator)" value="JAVA_ACTUATOR" />
          <el-option label="Java (JMX Exporter)" value="JAVA_JMX" />
        </el-select>
      </el-form-item>
      <el-form-item label="Exporter 端口" prop="exporterPort">
        <el-input-number
          v-model="form.exporterPort"
          :min="1"
          :max="65535"
          style="width: 100%"
        />
      </el-form-item>
      <el-form-item label="刷新频率(秒)" prop="refreshInterval">
        <el-input-number
          v-model="form.refreshInterval"
          :min="1"
          :max="60"
          style="width: 100%"
        />
      </el-form-item>
      <!-- 端点 URL 实时预览（仅 JAVA 类型显示） -->
      <el-form-item v-if="isJavaOsType" label="端点 URL">
        <div style="display: flex; gap: 6px; width: 100%;">
          <el-input
            :model-value="computedMetricsUrl"
            readonly
            style="flex: 1;"
          />
          <el-button @click="copyUrl" :icon="CopyDocument" size="small" title="复制 URL" />
        </div>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="handleSubmit">确定</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, watch, computed } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { CopyDocument } from '@element-plus/icons-vue'
import type { MonitorMachine, MonitorMachineForm } from '@/types/monitor'

const props = defineProps<{
  modelValue: boolean
  machine?: MonitorMachine | null
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', v: boolean): void
  (e: 'saved'): void
}>()

const visible = ref(props.modelValue)
watch(() => props.modelValue, (v) => { visible.value = v })
watch(visible, (v) => { emit('update:modelValue', v) })

const isEdit = ref(false)
const submitting = ref(false)
const formRef = ref<FormInstance>()

/** 各 osType 默认端口映射 */
const DEFAULT_PORTS: Record<string, number> = {
  LINUX: 9100,
  WINDOWS: 9182,
  MYSQL: 9104,
  JAVA_ACTUATOR: 8080,
  JAVA_JMX: 9104,
}

const defaultForm = (): MonitorMachineForm => ({
  machineName: '',
  ip: '',
  osType: 'LINUX',
  exporterPort: DEFAULT_PORTS['LINUX'],
  refreshInterval: 3,
})

const form = reactive<MonitorMachineForm>(defaultForm())

const rules: FormRules = {
  machineName: [{ required: true, message: '请输入机器别名', trigger: 'blur' }],
  ip: [{ required: true, message: '请输入机器IP', trigger: 'blur' }],
  osType: [{ required: true, message: '请选择系统类型', trigger: 'change' }],
  exporterPort: [{ required: true, message: '请输入端口', trigger: 'blur' }],
  refreshInterval: [{ required: true, message: '请输入刷新频率', trigger: 'blur' }],
}

// 当弹窗打开且传入 machine 时预填数据
watch(visible, (v) => {
  if (v && props.machine) {
    isEdit.value = true
    form.id = props.machine.id
    form.machineName = props.machine.machineName
    form.ip = props.machine.ip
    form.osType = props.machine.osType
    form.exporterPort = props.machine.exporterPort
    form.refreshInterval = props.machine.refreshInterval
  } else if (v) {
    isEdit.value = false
    Object.assign(form, defaultForm())
  }
})

/** 当前是否为 JAVA 类型 */
const isJavaOsType = computed(() =>
  form.osType === 'JAVA_ACTUATOR' || form.osType === 'JAVA_JMX'
)

/** 计算端点 URL（实时预览） */
const computedMetricsUrl = computed(() => {
  if (!form.ip || !form.exporterPort) return ''
  const path = form.osType === 'JAVA_ACTUATOR' ? '/actuator/prometheus' : '/metrics'
  return `http://${form.ip}:${form.exporterPort}${path}`
})

/** 一键复制端点 URL */
async function copyUrl() {
  if (!computedMetricsUrl.value) return
  try {
    await navigator.clipboard.writeText(computedMetricsUrl.value)
    ElMessage.success('已复制')
  } catch {
    ElMessage.warning('复制失败，请手动复制')
  }
}

/** osType 变更时自动切换默认端口（仅新建模式） */
function onOsTypeChange(newType: string) {
  if (isEdit.value) return // 编辑模式不改端口
  const defaultPort = DEFAULT_PORTS[newType]
  if (defaultPort !== undefined) {
    form.exporterPort = defaultPort
  }
}

function resetForm() {
  formRef.value?.resetFields()
  Object.assign(form, defaultForm())
}

async function handleSubmit() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    const { addMachine, updateMachine } = await import('@/api/monitor')
    if (isEdit.value && form.id) {
      await updateMachine({ ...form, id: form.id })
      ElMessage.success('机器已更新')
    } else {
      await addMachine(form)
      ElMessage.success('机器已添加')
    }
    visible.value = false
    emit('saved')
  } catch {
    // 错误已在 request.ts 拦截器中统一提示
  } finally {
    submitting.value = false
  }
}
</script>

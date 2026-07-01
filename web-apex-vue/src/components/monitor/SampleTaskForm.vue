<template>
  <el-dialog
    :model-value="visible"
    title="新建采样任务"
    width="480px"
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
        >
          <el-option
            v-for="m in machines"
            :key="m.id"
            :label="`${m.machineName} (${m.ip})`"
            :value="m.id"
          />
        </el-select>
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
import { ref, reactive } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import type { MonitorMachine, SampleTaskForm } from '@/types/monitor'
import { createSampleTask } from '@/api/monitor'

const props = defineProps<{
  machines: MonitorMachine[]
  visible: boolean
}>()

const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
  (e: 'submitted'): void
}>()

const formRef = ref<FormInstance>()
const submitting = ref(false)

const form = reactive<SampleTaskForm>({
  machineId: null,
  taskName: '',
  startTime: '',
  endTime: '',
  collectInterval: 3,
})

const rules: FormRules = {
  machineId: [{ required: true, message: '请选择机器', trigger: 'change' }],
  startTime: [{ required: true, message: '请选择开始时间', trigger: 'change' }],
  endTime: [{ required: true, message: '请选择结束时间', trigger: 'change' }],
  taskName: [{ required: true, message: '请填写备注', trigger: 'blur' }],
  collectInterval: [{ required: true, message: '请选择采集频率', trigger: 'change' }],
}

function disabledPastDate(date: Date) {
  return date.getTime() < Date.now() - 60 * 1000 // 允许 1 分钟前的缓冲
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
    })
    ElMessage.success('采样任务已创建')
    // 重置表单
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

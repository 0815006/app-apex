<template>
  <el-dialog
    v-model="visible"
    title="大模型配置管理"
    width="600px"
    :close-on-click-modal="false"
    destroy-on-close
    @closed="handleClosed"
  >
    <!-- 配置列表 -->
    <div class="config-list" v-loading="loading">
      <div v-if="configs.length === 0" class="empty-hint">暂无配置，请新增一个大模型配置</div>
      <div
        v-for="config in configs"
        :key="config.id"
        class="config-item"
      >
        <div class="config-info">
          <div class="config-name">{{ config.configName }}</div>
          <div class="config-sub">
            <span class="config-model">{{ config.modelName }}</span>
            <span class="config-url">{{ config.apiUrl }}</span>
          </div>
        </div>
        <div class="config-actions">
          <el-button type="primary" link size="small" @click="handleEdit(config)">编辑</el-button>
          <el-popconfirm title="确定删除该配置？" @confirm="handleDelete(config.id!)">
            <template #reference>
              <el-button type="danger" link size="small">删除</el-button>
            </template>
          </el-popconfirm>
        </div>
      </div>
    </div>

    <!-- 新增按钮 -->
    <div class="add-bar">
      <el-button type="primary" @click="handleAdd">新增配置</el-button>
    </div>

    <!-- 编辑/新增表单弹窗 -->
    <el-dialog
      v-model="formVisible"
      :title="formTitle"
      width="500px"
      :close-on-click-modal="false"
      append-to-body
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="配置名称" prop="configName">
          <el-input v-model="form.configName" placeholder="如：个人DeepSeek" maxlength="50" />
        </el-form-item>
        <el-form-item label="Base URL" prop="apiUrl">
          <el-input v-model="form.apiUrl" placeholder="如 https://api.openai.com/v1" />
        </el-form-item>
        <el-form-item label="API Key" prop="apiKey">
          <el-input v-model="form.apiKey" placeholder="sk-..." type="password" show-password />
        </el-form-item>
        <el-form-item label="模型名称" prop="modelName">
          <el-input v-model="form.modelName" placeholder="如 gpt-4o、deepseek-chat" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="formLoading" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import type { LlmConfig } from '@/types/chat'
import { listLlmConfigs, getLlmConfig, createLlmConfig, updateLlmConfig, deleteLlmConfig } from '@/api/chat'

const props = defineProps<{
  modelValue: boolean
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', val: boolean): void
}>()

const visible = ref(props.modelValue)
watch(() => props.modelValue, (v) => { visible.value = v })
watch(visible, (v) => emit('update:modelValue', v))

// 配置列表
const configs = ref<LlmConfig[]>([])
const loading = ref(false)

async function loadConfigs() {
  loading.value = true
  try {
    const res = await listLlmConfigs()
    if (res.code === 200) {
      // listLlmConfigs 只返回部分字段，需要逐个查询详情获取完整数据
      const fullList: LlmConfig[] = []
      for (const item of res.data) {
        try {
          const detailRes = await getLlmConfig(item.id)
          if (detailRes.code === 200) {
            fullList.push(detailRes.data)
          }
        } catch {
          // 单个查询失败跳过
        }
      }
      configs.value = fullList
    }
  } finally {
    loading.value = false
  }
}

watch(visible, (v) => {
  if (v) loadConfigs()
})

// 新增/编辑表单
const formVisible = ref(false)
const formLoading = ref(false)
const formRef = ref<FormInstance>()
const editingId = ref<string | null>(null)
const formTitle = ref('新增配置')

const form = reactive<LlmConfig>({
  configName: '',
  apiUrl: '',
  apiKey: '',
  modelName: '',
})

const rules: FormRules = {
  configName: [{ required: true, message: '请输入配置名称', trigger: 'blur' }],
  apiUrl: [{ required: true, message: '请输入 Base URL', trigger: 'blur' }],
  apiKey: [{ required: true, message: '请输入 API Key', trigger: 'blur' }],
  modelName: [{ required: true, message: '请输入模型名称', trigger: 'blur' }],
}

function handleAdd() {
  editingId.value = null
  formTitle.value = '新增配置'
  form.configName = ''
  form.apiUrl = ''
  form.apiKey = ''
  form.modelName = ''
  formVisible.value = true
}

function handleEdit(config: LlmConfig) {
  editingId.value = config.id!
  formTitle.value = '编辑配置'
  form.configName = config.configName
  form.apiUrl = config.apiUrl
  form.apiKey = config.apiKey
  form.modelName = config.modelName
  formVisible.value = true
}

async function handleSave() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  formLoading.value = true
  try {
    const payload = {
      configName: form.configName,
      apiUrl: form.apiUrl.replace(/\/+$/, ''), // 去掉末尾斜杠
      apiKey: form.apiKey,
      modelName: form.modelName,
    }
    if (editingId.value) {
      const res = await updateLlmConfig(editingId.value, payload)
      if (res.code !== 200) return
      ElMessage.success('修改成功')
    } else {
      const res = await createLlmConfig(payload)
      if (res.code !== 200) return
      ElMessage.success('新增成功')
    }
    formVisible.value = false
    await loadConfigs()
  } finally {
    formLoading.value = false
  }
}

async function handleDelete(id: string) {
  try {
    const res = await deleteLlmConfig(id)
    if (res.code !== 200) return
    ElMessage.success('删除成功')
    await loadConfigs()
  } catch {
    // 错误已由拦截器处理
  }
}

function handleClosed() {
  // 弹窗关闭时清理
}
</script>

<style scoped>
.config-list {
  max-height: 320px;
  overflow-y: auto;
}

.empty-hint {
  text-align: center;
  color: #909399;
  padding: 40px 0;
  font-size: 14px;
}

.config-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  margin-bottom: 8px;
  transition: border-color 0.2s;
}
.config-item:hover {
  border-color: #409eff;
}

.config-info {
  flex: 1;
  min-width: 0;
}

.config-name {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 4px;
}

.config-sub {
  display: flex;
  gap: 12px;
  font-size: 12px;
  color: #909399;
}

.config-url {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 240px;
}

.config-actions {
  display: flex;
  gap: 4px;
  flex-shrink: 0;
  margin-left: 12px;
}

.add-bar {
  margin-top: 12px;
  text-align: center;
}
</style>

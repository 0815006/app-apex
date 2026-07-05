<template>
  <el-dialog
    v-model="visible"
    title="工作空间管理"
    width="780px"
    destroy-on-close
    :close-on-click-modal="false"
    @closed="handleClosed"
  >
    <div class="ws-dialog-body">
      <!-- 顶部工具栏 -->
      <div class="ws-toolbar">
        <div class="ws-toolbar-left">
          <span class="ws-count-badge">
            <el-icon><Folder /></el-icon>
            <span>{{ workspaces.length }} 个工作空间</span>
          </span>
        </div>
        <div class="ws-toolbar-right">
          <el-button :icon="FolderAdd" @click="handleShowImport" round>导入已有目录</el-button>
          <el-button type="primary" :icon="Plus" @click="handleCreate" round>新建工作空间</el-button>
        </div>
      </div>

      <!-- 卡片网格 -->
      <div v-loading="loading" class="ws-cards-container">
        <TransitionGroup name="ws-card" tag="div" class="ws-card-grid">
          <div
            v-for="ws in workspaces"
            :key="ws.id"
            class="ws-card"
          >
            <!-- 卡片头部：头像 + 标题 -->
            <div class="ws-card-header">
              <div class="ws-card-avatar" :style="{ backgroundColor: avatarColor(ws.name) }">
                {{ ws.name.charAt(0).toUpperCase() }}
              </div>
              <div class="ws-card-title-area">
                <span class="ws-card-name">{{ ws.name }}</span>
                <el-tag size="small" round effect="plain" class="ws-card-dir-tag">
                  <el-icon style="margin-right: 3px;"><FolderOpened /></el-icon>
                  {{ ws.dirName }}
                </el-tag>
              </div>
              <div class="ws-card-actions">
                <el-button :icon="Edit" circle size="small" @click="handleEdit(ws)" />
                <el-popconfirm
                  title="确定删除该工作空间？（不会删除物理文件）"
                  confirm-button-text="删除"
                  cancel-button-text="取消"
                  @confirm="handleDelete(ws.id)"
                >
                  <template #reference>
                    <el-button :icon="Delete" circle size="small" type="danger" />
                  </template>
                </el-popconfirm>
              </div>
            </div>

            <!-- 卡片内容：描述 -->
            <div class="ws-card-body">
              <p class="ws-card-desc">{{ ws.description || '暂无描述' }}</p>
            </div>

            <!-- 卡片底部：时间 -->
            <div class="ws-card-footer">
              <el-icon class="ws-card-time-icon"><Clock /></el-icon>
              <span class="ws-card-time">{{ formatTime(ws.createTime) }}</span>
            </div>
          </div>
        </TransitionGroup>

        <!-- 空状态 -->
        <div v-if="!loading && workspaces.length === 0" class="ws-empty-state">
          <el-empty description="暂无工作空间，点击上方按钮创建一个吧">
            <el-button type="primary" :icon="Plus" @click="handleCreate">新建工作空间</el-button>
          </el-empty>
        </div>
      </div>
    </div>

    <!-- 嵌套：新建/编辑弹窗 -->
    <el-dialog
      v-model="formDialogVisible"
      :title="isEdit ? '编辑工作空间' : '新建工作空间'"
      width="500px"
      destroy-on-close
      :close-on-click-modal="false"
      append-to-body
      class="ws-form-dialog"
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="70px"
        :disabled="submitting"
        class="ws-form"
      >
        <el-form-item label="名称" prop="name">
          <el-input
            v-model="form.name"
            placeholder="如：我的项目"
            maxlength="100"
            :prefix-icon="Edit"
          />
        </el-form-item>
        <el-form-item label="目录名" prop="dirName">
          <el-input
            v-model="form.dirName"
            placeholder="英文字母、数字、下划线"
            maxlength="50"
            :disabled="isEdit"
            :prefix-icon="FolderOpened"
          />
          <div class="form-hint">创建后不可修改，将用于磁盘实际目录名</div>
        </el-form-item>
        <el-form-item label="描述">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="3"
            placeholder="可选描述"
            maxlength="255"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formDialogVisible = false" :disabled="submitting">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">
          {{ isEdit ? '保存' : '创建' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 嵌套：导入已有目录弹窗 -->
    <el-dialog
      v-model="importDialogVisible"
      title="导入已有目录"
      width="500px"
      destroy-on-close
      :close-on-click-modal="false"
      append-to-body
      @open="handleImportDialogOpen"
      class="ws-form-dialog"
    >
      <el-form
        ref="importFormRef"
        :model="importForm"
        :rules="importRules"
        label-width="70px"
        :disabled="importing"
        class="ws-form"
      >
        <el-form-item label="目录选择" prop="dirName">
          <el-select
            v-model="importForm.dirName"
            placeholder="请选择一个磁盘上存在的目录"
            style="width: 100%"
            v-loading="unregisteredDirsLoading"
            no-data-text="没有可导入的目录"
          >
            <el-option
              v-for="dir in unregisteredDirs"
              :key="dir"
              :label="dir"
              :value="dir"
            />
          </el-select>
          <div class="form-hint">仅显示磁盘上存在但未注册的目录</div>
        </el-form-item>
        <el-form-item label="名称">
          <el-input
            v-model="importForm.name"
            placeholder="留空则使用目录名作为显示名称"
            maxlength="64"
            :prefix-icon="Edit"
          />
        </el-form-item>
        <el-form-item label="描述">
          <el-input
            v-model="importForm.description"
            type="textarea"
            :rows="3"
            placeholder="可选描述"
            maxlength="255"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="importDialogVisible = false" :disabled="importing">取消</el-button>
        <el-button type="primary" :loading="importing" @click="handleImportSubmit">导入</el-button>
      </template>
    </el-dialog>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, FolderAdd, Edit, Delete, Folder, FolderOpened, Clock } from '@element-plus/icons-vue'
import type { FormInstance, FormRules } from 'element-plus'
import { listWorkspaces, createWorkspace, updateWorkspace, deleteWorkspace, listUnregisteredDirs, importWorkspace } from '@/api/agent'
import type { AgentWorkspace } from '@/types/chat'

// ========== 双向绑定 visible ==========
const props = defineProps<{
  modelValue: boolean
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'workspaceCreated'): void
}>()

const visible = ref(props.modelValue)

watch(() => props.modelValue, (val) => {
  visible.value = val
  if (val) {
    loadWorkspaces()
  }
})

watch(visible, (val) => {
  emit('update:modelValue', val)
})

function handleClosed() {
  // 关闭时清理弹窗状态
  formDialogVisible.value = false
  importDialogVisible.value = false
}

// ========== 列表 ==========
const workspaces = ref<AgentWorkspace[]>([])
const loading = ref(false)

async function loadWorkspaces() {
  loading.value = true
  try {
    const res = await listWorkspaces()
    if (res.code === 200) {
      workspaces.value = res.data
    }
  } finally {
    loading.value = false
  }
}

// ========== 表单弹窗 ==========
const formDialogVisible = ref(false)
const isEdit = ref(false)
const submitting = ref(false)
const formRef = ref<FormInstance>()

const defaultForm = () => ({
  name: '',
  dirName: '',
  description: '',
})

const form = reactive(defaultForm())

const rules: FormRules = {
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  dirName: [
    { required: true, message: '请输入目录名', trigger: 'blur' },
    { pattern: /^[a-zA-Z0-9_-]+$/, message: '只能包含英文字母、数字、下划线、连字符', trigger: 'blur' },
  ],
}

function handleCreate() {
  isEdit.value = false
  Object.assign(form, defaultForm())
  delete (form as Record<string, unknown>).id
  formDialogVisible.value = true
}

function handleEdit(row: AgentWorkspace) {
  isEdit.value = true
  form.name = row.name
  form.dirName = row.dirName
  form.description = row.description || ''
  ;(form as Record<string, unknown>).id = row.id
  formDialogVisible.value = true
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    const id = (form as Record<string, unknown>).id as string | undefined
    if (isEdit.value && id) {
      const res = await updateWorkspace(id, { name: form.name, description: form.description })
      if (res.code === 200) {
        ElMessage.success('更新成功')
        formDialogVisible.value = false
        loadWorkspaces()
        emit('workspaceCreated')
      }
    } else {
      const res = await createWorkspace({ name: form.name, dirName: form.dirName, description: form.description })
      if (res.code === 200) {
        ElMessage.success('创建成功')
        formDialogVisible.value = false
        loadWorkspaces()
        emit('workspaceCreated')
      }
    }
  } finally {
    submitting.value = false
  }
}

// ========== 导入已有目录 ==========
const importDialogVisible = ref(false)
const unregisteredDirs = ref<string[]>([])
const unregisteredDirsLoading = ref(false)
const importing = ref(false)
const importFormRef = ref<FormInstance>()

const importForm = reactive({
  dirName: '',
  name: '',
  description: '',
})

const importRules: FormRules = {
  dirName: [{ required: true, message: '请选择目录', trigger: 'change' }],
}

function handleShowImport() {
  importDialogVisible.value = true
}

async function handleImportDialogOpen() {
  unregisteredDirsLoading.value = true
  try {
    const res = await listUnregisteredDirs()
    if (res.code === 200) {
      unregisteredDirs.value = res.data
    }
  } finally {
    unregisteredDirsLoading.value = false
  }
}

async function handleImportSubmit() {
  const valid = await importFormRef.value?.validate().catch(() => false)
  if (!valid) return

  importing.value = true
  try {
    const res = await importWorkspace({
      dirName: importForm.dirName,
      name: importForm.name || importForm.dirName,
      description: importForm.description,
    })
    if (res.code === 200) {
      ElMessage.success('导入成功')
      importDialogVisible.value = false
      importForm.dirName = ''
      importForm.name = ''
      importForm.description = ''
      loadWorkspaces()
      emit('workspaceCreated')
    }
  } finally {
    importing.value = false
  }
}

/** 根据名称哈希生成柔和卡片头像背景色 */
function avatarColor(name: string): string {
  const palette = [
    '#3b82f6', '#8b5cf6', '#ec4899', '#f43f5e',
    '#f97316', '#eab308', '#22c55e', '#14b8a6',
    '#06b6d4', '#6366f1',
  ]
  let hash = 0
  for (let i = 0; i < name.length; i++) {
    hash = name.charCodeAt(i) + ((hash << 5) - hash)
  }
  return palette[Math.abs(hash) % palette.length]
}

async function handleDelete(id: string) {
  try {
    const res = await deleteWorkspace(id)
    if (res.code === 200) {
      ElMessage.success('已删除')
      loadWorkspaces()
      emit('workspaceCreated')
    }
  } catch { /* 拦截器已处理 */ }
}

function formatTime(dateStr: string): string {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  const y = d.getFullYear()
  const M = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  const hh = String(d.getHours()).padStart(2, '0')
  const mm = String(d.getMinutes()).padStart(2, '0')
  return `${y}-${M}-${day} ${hh}:${mm}`
}
</script>

<style scoped>
/* ===================== 主容器 ===================== */
.ws-dialog-body {
  display: flex;
  flex-direction: column;
  gap: 0;
  max-height: 520px;
  overflow: hidden;
}

/* ===================== 工具栏 ===================== */
.ws-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 4px 16px 4px;
  flex-shrink: 0;
}

.ws-toolbar-right {
  display: flex;
  gap: 8px;
}

.ws-count-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 14px;
  border-radius: 20px;
  font-size: 13px;
  color: #606266;
  background: #f0f2f5;
  font-weight: 500;
}

.ws-count-badge .el-icon {
  font-size: 15px;
  color: #909399;
}

/* ===================== 卡片网格 ===================== */
.ws-cards-container {
  flex: 1;
  overflow-y: auto;
  min-height: 100px;
  padding: 0 4px;
}

.ws-cards-container::-webkit-scrollbar {
  width: 6px;
}

.ws-cards-container::-webkit-scrollbar-thumb {
  border-radius: 3px;
  background: #dcdfe6;
}

.ws-card-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 14px;
}

/* ===================== 单张卡片 ===================== */
.ws-card {
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 12px;
  padding: 20px 20px 14px;
  transition: all 0.25s ease;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.ws-card:hover {
  border-color: #c6d4f7;
  box-shadow: 0 4px 16px rgba(59, 130, 246, 0.08);
  transform: translateY(-2px);
}

/* ---- 卡片头部 ---- */
.ws-card-header {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}

.ws-card-avatar {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 17px;
  font-weight: 700;
  flex-shrink: 0;
  letter-spacing: 0.5px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.10);
}

.ws-card-title-area {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.ws-card-name {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.ws-card-dir-tag {
  width: fit-content;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ws-card-actions {
  display: flex;
  gap: 4px;
  flex-shrink: 0;
  opacity: 0;
  transition: opacity 0.2s;
}

.ws-card:hover .ws-card-actions {
  opacity: 1;
}

/* ---- 卡片内容 ---- */
.ws-card-body {
  flex: 1;
  min-height: 0;
}

.ws-card-desc {
  margin: 0;
  font-size: 13px;
  color: #909399;
  line-height: 1.55;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

/* ---- 卡片底部 ---- */
.ws-card-footer {
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: 12px;
  color: #c0c4cc;
  padding-top: 4px;
  border-top: 1px dashed #ebeef5;
}

.ws-card-time-icon {
  font-size: 13px;
}

/* ===================== 空态 ===================== */
.ws-empty-state {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px 0;
}

/* ===================== 卡片动画 ===================== */
.ws-card-enter-active,
.ws-card-leave-active {
  transition: all 0.35s ease;
}

.ws-card-enter-from {
  opacity: 0;
  transform: translateY(18px) scale(0.96);
}

.ws-card-leave-to {
  opacity: 0;
  transform: translateY(-10px) scale(0.94);
}

.ws-card-move {
  transition: transform 0.35s ease;
}

/* ===================== 表单 ===================== */
.ws-form {
  padding-top: 8px;
}

.form-hint {
  font-size: 11px;
  color: #909399;
  margin-top: 4px;
}

/* ===================== 表单弹窗微调 ===================== */
:deep(.ws-form-dialog .el-dialog__header) {
  border-bottom: 1px solid #f0f0f0;
  margin-bottom: 0;
  padding-bottom: 16px;
}

:deep(.ws-form-dialog .el-dialog__body) {
  padding-top: 20px;
  padding-bottom: 10px;
}

:deep(.ws-form-dialog .el-dialog__footer) {
  padding-top: 10px;
  border-top: 1px solid #f0f0f0;
}
</style>

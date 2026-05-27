<template>
  <div class="skill-manager">
    <!-- 工具栏 -->
    <div class="toolbar">
      <span class="page-title">Skill 管理</span>
      <el-button type="primary" :icon="Plus" @click="handleCreate">
        新建 Skill
      </el-button>
    </div>

    <!-- 表格 -->
    <el-table
      :data="skills"
      v-loading="loading"
      stripe
      class="skill-table"
      empty-text="暂无 Skill，点击上方按钮创建一个吧"
    >
      <el-table-column prop="name" label="名称" min-width="140" />
      <el-table-column prop="description" label="简介" min-width="200" show-overflow-tooltip />
      <el-table-column prop="type" label="类型" width="100">
        <template #default="{ row }">
          <el-tag :type="typeTag(row.type)" size="small">
            {{ typeLabel(row.type) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
            {{ row.status === 1 ? '启用' : '禁用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="sortOrder" label="排序" width="70" />
      <el-table-column prop="updateTime" label="更新时间" width="170">
        <template #default="{ row }">
          {{ formatTime(row.updateTime) }}
        </template>
      </el-table-column>
      <el-table-column label="操作" width="140" fixed="right">
        <template #default="{ row }">
          <el-button :icon="Edit" link size="small" @click="handleEdit(row)">编辑</el-button>
          <el-popconfirm
            title="确定删除该 Skill？"
            confirm-button-text="删除"
            cancel-button-text="取消"
            @confirm="handleDelete(row.id)"
          >
            <template #reference>
              <el-button :icon="Delete" link size="small" type="danger">删除</el-button>
            </template>
          </el-popconfirm>
        </template>
      </el-table-column>
    </el-table>

    <!-- 新建/编辑弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑 Skill' : '新建 Skill'"
      width="620px"
      destroy-on-close
      :close-on-click-modal="false"
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="90px"
        label-position="right"
        :disabled="submitting"
      >
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" placeholder="如：代码审查专家" maxlength="100" />
        </el-form-item>
        <el-form-item label="简介" prop="description">
          <el-input v-model="form.description" placeholder="一句话描述该 Skill 的功能" maxlength="255" />
        </el-form-item>
        <el-form-item label="类型" prop="type">
          <el-select v-model="form.type" style="width: 100%">
            <el-option label="提示词 (prompt)" value="prompt" />
            <el-option label="工具调用 (agent)" value="agent" disabled />
            <el-option label="工作流 (workflow)" value="workflow" disabled />
          </el-select>
        </el-form-item>
        <el-form-item label="System Prompt" prop="systemPrompt">
          <el-input
            v-model="form.systemPrompt"
            type="textarea"
            :rows="8"
            placeholder="输入给大模型的 System 角色提示词..."
          />
        </el-form-item>
        <el-form-item label="采样温度">
          <el-slider
            v-model="form.temperature"
            :min="0"
            :max="2"
            :step="0.1"
            :marks="{ 0: '0', 0.7: '0.7', 1: '1', 2: '2' }"
            show-input
          />
        </el-form-item>
        <el-form-item label="排序序号">
          <el-input-number v-model="form.sortOrder" :min="0" :max="9999" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch
            v-model="form.status"
            :active-value="1"
            :inactive-value="0"
            active-text="启用"
            inactive-text="禁用"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false" :disabled="submitting">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">
          {{ isEdit ? '保存' : '创建' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Edit, Delete } from '@element-plus/icons-vue'
import type { FormInstance, FormRules } from 'element-plus'
import { listAllSkills, createSkill, updateSkill, deleteSkill } from '@/api/skill'
import type { SkillVO, SkillForm } from '@/types/skill'

// ========== 列表数据 ==========
const skills = ref<SkillVO[]>([])
const loading = ref(false)

async function loadSkills() {
  loading.value = true
  try {
    const res = await listAllSkills()
    if (res.code === 200) {
      skills.value = res.data
    }
  } finally {
    loading.value = false
  }
}

// ========== 弹窗表单 ==========
const dialogVisible = ref(false)
const isEdit = ref(false)
const submitting = ref(false)
const formRef = ref<FormInstance>()

const defaultForm = (): SkillForm => ({
  name: '',
  description: '',
  type: 'prompt',
  systemPrompt: '',
  temperature: 0.7,
  sortOrder: 0,
  status: 1,
})

const form = reactive<SkillForm>(defaultForm())

const rules: FormRules = {
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
}

function handleCreate() {
  isEdit.value = false
  Object.assign(form, defaultForm())
  dialogVisible.value = true
}

function handleEdit(row: SkillVO) {
  isEdit.value = true
  form.id = row.id
  form.name = row.name
  form.description = row.description || ''
  form.type = row.type
  form.systemPrompt = row.systemPrompt || ''
  form.temperature = row.temperature
  form.status = row.status
  form.sortOrder = row.sortOrder
  dialogVisible.value = true
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    if (isEdit.value && form.id) {
      const res = await updateSkill(form.id, { ...form })
      if (res.code === 200) {
        ElMessage.success('更新成功')
        dialogVisible.value = false
        loadSkills()
      }
    } else {
      const res = await createSkill({ ...form })
      if (res.code === 200) {
        ElMessage.success('创建成功')
        dialogVisible.value = false
        loadSkills()
      }
    }
  } finally {
    submitting.value = false
  }
}

async function handleDelete(id: string) {
  try {
    const res = await deleteSkill(id)
    if (res.code === 200) {
      ElMessage.success('已删除')
      loadSkills()
    }
  } catch {
    // 拦截器已处理
  }
}

// ========== 辅助 ==========
function typeTag(type: string): 'success' | 'warning' | 'info' {
  if (type === 'prompt') return 'success'
  if (type === 'agent') return 'warning'
  return 'info'
}

function typeLabel(type: string): string {
  if (type === 'prompt') return '提示词'
  if (type === 'agent') return 'Agent'
  if (type === 'workflow') return '工作流'
  return type
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

// ========== 生命周期 ==========
onMounted(() => {
  loadSkills()
})
</script>

<style scoped>
.skill-manager {
  padding: 20px;
  height: 100%;
  display: flex;
  flex-direction: column;
}

.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.page-title {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
}

.skill-table {
  flex: 1;
}
</style>

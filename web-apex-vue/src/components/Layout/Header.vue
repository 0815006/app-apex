<template>
  <div class="header-bar">
    <div class="header-left">
      <span class="header-title">Apex 全栈平台</span>
    </div>
    <div class="header-right">
      <!-- 工号Tag / 就地编辑 -->
      <div v-if="!isEditing" class="emp-tag" @click="startEdit">
        <el-tag type="success" size="large">
          {{ currentEmpNo || '未设置工号' }}
        </el-tag>
      </div>
      <el-input
        v-else
        ref="empInputRef"
        v-model="empNoDraft"
        class="emp-input"
        maxlength="7"
        placeholder="输入7位工号"
        @keyup.enter="confirmEdit"
        @blur="confirmEdit"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { getCurrentEmpNo, setCurrentEmpNo, isEmpNoValid } from '@/utils/currentUser'

const currentEmpNo = ref(getCurrentEmpNo())
const isEditing = ref(false)
const empNoDraft = ref('')
const empInputRef = ref()

function startEdit() {
  empNoDraft.value = currentEmpNo.value
  isEditing.value = true
  nextTick(() => {
    empInputRef.value?.focus()
  })
}

function confirmEdit() {
  if (!isEditing.value) return

  const trimmed = empNoDraft.value.trim()
  if (trimmed && !isEmpNoValid(trimmed)) {
    ElMessage.warning('工号必须为7位数字')
    return
  }

  if (trimmed) {
    setCurrentEmpNo(trimmed)
    currentEmpNo.value = trimmed
    ElMessage.success(`工号已切换为: ${trimmed}`)
  }

  isEditing.value = false
}
</script>

<style scoped>
.header-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 56px;
  padding: 0 20px;
}

.header-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.emp-tag {
  cursor: pointer;
}

.emp-input {
  width: 160px;
}
</style>

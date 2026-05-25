<template>
  <div class="home-container">
    <el-card class="welcome-card">
      <template #header>
        <div class="card-header">
          <span>欢迎使用 Apex 全栈平台</span>
        </div>
      </template>
      <div class="card-body">
        <p class="text-lg text-gray-600 mb-4">
          当前登录工号: <el-tag type="success">{{ currentEmpNo || '未设置' }}</el-tag>
        </p>
        <el-descriptions :column="2" border>
          <el-descriptions-item label="后端框架">Spring Boot 3.4 + Java 21</el-descriptions-item>
          <el-descriptions-item label="前端框架">Vue 3.5 + Vite 6 + Element Plus</el-descriptions-item>
          <el-descriptions-item label="ORM">MyBatis Plus 3.5</el-descriptions-item>
          <el-descriptions-item label="鉴权">Sa-Token (JWT)</el-descriptions-item>
          <el-descriptions-item label="数据库">MySQL 8.4</el-descriptions-item>
          <el-descriptions-item label="状态" :span="1">
            <el-button
              :type="healthOk ? 'success' : 'danger'"
              size="small"
              :loading="healthLoading"
              @click="checkHealth"
            >
              {{ healthOk ? '服务正常' : '检查服务' }}
            </el-button>
          </el-descriptions-item>
        </el-descriptions>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getCurrentEmpNo } from '@/utils/currentUser'
import { getHealth } from '@/api/health'

const currentEmpNo = ref(getCurrentEmpNo())
const healthOk = ref(false)
const healthLoading = ref(false)

async function checkHealth() {
  healthLoading.value = true
  try {
    const result = await getHealth()
    healthOk.value = !!result
  } catch {
    healthOk.value = false
  } finally {
    healthLoading.value = false
  }
}

onMounted(() => {
  checkHealth()
})
</script>

<style scoped>
.home-container {
  max-width: 900px;
  margin: 0 auto;
}

.welcome-card {
  border-radius: 8px;
}

.card-header {
  font-size: 18px;
  font-weight: 600;
}

.card-body {
  padding: 8px 0;
}
</style>

<template>
  <div class="status-bar">
    <div class="status-left">
      <span class="status-text">{{ currentTime }}</span>
    </div>
    <div class="status-center">
      <span class="status-text copyright-text">Copyright &copy; 2026 Apex网站 All Rights Reserved. cd5403 版权所有</span>
    </div>
    <div class="status-right">
      <span class="status-text">Login IP: {{ loginIp || '--' }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { getSystemInfo } from '@/api/system'

const currentTime = ref('')
const loginIp = ref('')

let timer: ReturnType<typeof setInterval> | null = null

function updateTime() {
  const now = new Date()
  const year = now.getFullYear()
  const month = String(now.getMonth() + 1).padStart(2, '0')
  const day = String(now.getDate()).padStart(2, '0')
  const hours = String(now.getHours()).padStart(2, '0')
  const minutes = String(now.getMinutes()).padStart(2, '0')
  const seconds = String(now.getSeconds()).padStart(2, '0')
  currentTime.value = `${year}-${month}-${day} ${hours}:${minutes}:${seconds}`
}

async function fetchSystemInfo() {
  try {
    const info = await getSystemInfo()
    loginIp.value = info.loginIp
  } catch {
    // 后端未启动时静默忽略
  }
}

onMounted(() => {
  updateTime()
  timer = setInterval(updateTime, 1000)
  fetchSystemInfo()
})

onUnmounted(() => {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
})
</script>

<style scoped>
.status-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 34px;
  padding: 0 16px;
}

.status-left {
  flex-shrink: 0;
}

.status-center {
  flex: 1;
  text-align: center;
}

.status-right {
  flex-shrink: 0;
}

.status-text {
  font-size: 12px;
  color: #909399;
}

.copyright-text {
  color: #606266;
}
</style>

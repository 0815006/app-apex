<template>
  <div class="sidebar-container">
    <div class="sidebar-logo">
      <img src="/apex-logo.svg" alt="Apex" class="logo-icon" />
      <span class="logo-text">Apex</span>
    </div>
    <el-menu
      :default-active="activeMenu"
      background-color="#304156"
      text-color="#bfcbd9"
      active-text-color="#409eff"
      router
    >
      <el-menu-item index="/home">
        <el-icon><HomeFilled /></el-icon>
        <span>首页</span>
      </el-menu-item>
      <el-menu-item index="/wiki">
        <el-icon><Collection /></el-icon>
        <span>Wiki在线</span>
      </el-menu-item>
      <el-menu-item index="/fileshare">
        <el-icon><FolderOpened /></el-icon>
        <span>文件共享</span>
      </el-menu-item>
      <el-menu-item index="/chat">
        <el-icon><ChatDotRound /></el-icon>
        <span>AI大模型</span>
      </el-menu-item>
      <!-- <el-menu-item index="/agent">
        <el-icon><Cpu /></el-icon>
        <span>Agent</span>
      </el-menu-item> -->
      <el-menu-item index="/skills">
        <el-icon><MagicStick /></el-icon>
        <span>Skill工具</span>
      </el-menu-item>
      <el-menu-item index="/monitor">
        <el-icon><Monitor /></el-icon>
        <span>容量监控</span>
      </el-menu-item>
      <el-menu-item index="/monitor-trend">
        <el-icon><TrendCharts /></el-icon>
        <span>监控走势</span>
      </el-menu-item>
    </el-menu>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const activeMenu = computed(() => route.path)
</script>

<style scoped>
.sidebar-container {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.sidebar-logo {
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
}

.logo-icon {
  width: 28px;
  height: 28px;
  flex-shrink: 0;
}

.logo-text {
  font-size: 20px;
  font-weight: 700;
  color: #fff;
  letter-spacing: 2px;
}

.el-menu {
  border-right: none;
  flex: 1;
}
</style>

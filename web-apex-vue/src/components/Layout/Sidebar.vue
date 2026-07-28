<template>
  <div class="sidebar-container" :class="{ collapsed }">
    <div class="sidebar-logo">
      <img src="/apex-logo.svg" alt="Apex" class="logo-icon" />
      <span v-show="!collapsed" class="logo-text">Apex</span>

      <el-tooltip :content="collapsed ? '展开侧边栏' : '折叠侧边栏'" placement="right" :show-after="300">
        <div
          class="sidebar-toggle"
          :class="{ 'is-overlay': collapsed }"
          @click="toggle"
        >
          <el-icon :size="16">
            <DArrowLeft v-if="!collapsed" />
            <DArrowRight v-else />
          </el-icon>
        </div>
      </el-tooltip>
    </div>

    <el-menu
      :default-active="activeMenu"
      :collapse="collapsed"
      background-color="#304156"
      text-color="#bfcbd9"
      active-text-color="#409eff"
      router
    >
      <el-menu-item index="/home">
        <el-icon><HomeFilled /></el-icon>
        <template #title>首页</template>
      </el-menu-item>
      <el-menu-item index="/wiki">
        <el-icon><Collection /></el-icon>
        <template #title>Wiki在线</template>
      </el-menu-item>
      <el-menu-item index="/fileshare">
        <el-icon><FolderOpened /></el-icon>
        <template #title>文件共享</template>
      </el-menu-item>
      <el-menu-item index="/chat">
        <el-icon><ChatDotRound /></el-icon>
        <template #title>AI大模型</template>
      </el-menu-item>
      <el-menu-item index="/agent">
        <el-icon><Cpu /></el-icon>
        <template #title>Agent</template>
      </el-menu-item>
      <el-menu-item index="/skills">
        <el-icon><MagicStick /></el-icon>
        <template #title>Skill工具</template>
      </el-menu-item>
      <el-menu-item index="/monitor">
        <el-icon><Monitor /></el-icon>
        <template #title>容量监控</template>
      </el-menu-item>
      <el-menu-item index="/monitor-trend">
        <el-icon><TrendCharts /></el-icon>
        <template #title>监控走势</template>
      </el-menu-item>
    </el-menu>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false,
  },
})

const emit = defineEmits(['update:modelValue'])

const collapsed = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val),
})

const toggle = () => {
  collapsed.value = !collapsed.value
}

const route = useRoute()
const activeMenu = computed(() => route.path)
</script>

<style scoped>
.sidebar-container {
  height: 100%;
  display: flex;
  flex-direction: column;
  transition: width 0.3s ease;
}

.sidebar-logo {
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
  padding: 0 16px;
  position: relative;
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
  white-space: nowrap;
  overflow: hidden;
}

/* ========== 折叠切换按钮 ========== */
.sidebar-toggle {
  position: absolute;
  top: 50%;
  right: 10px;
  transform: translateY(-50%);
  width: 24px;
  height: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: #304156;
  border: 1px solid rgba(255, 255, 255, 0.25);
  border-radius: 50%;
  cursor: pointer;
  color: #bfcbd9;
  z-index: 10;
  transition: opacity 0.25s ease, border-color 0.2s, color 0.2s;
}

.sidebar-toggle:hover {
  color: #409eff;
  border-color: #409eff;
}

/* 折叠态：居中覆盖 Logo 图标，默认隐藏 */
.sidebar-toggle.is-overlay {
  top: 50%;
  left: 50%;
  right: auto;
  transform: translate(-50%, -50%);
  width: 36px;
  height: 36px;
  background-color: rgba(48, 65, 86, 0.92);
  opacity: 0;
  pointer-events: none;
}

/* 折叠态：鼠标悬停 Logo 区域时显示 */
.sidebar-logo:hover .sidebar-toggle.is-overlay {
  opacity: 1;
  pointer-events: auto;
}

.el-menu {
  border-right: none;
  flex: 1;
}
</style>

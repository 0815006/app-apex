<template>
  <div class="layout-wrapper">
    <!-- 左侧菜单 -->
    <aside class="layout-sidebar" :class="{ 'layout-sidebar--collapsed': sidebarCollapsed }">
      <Sidebar v-model="sidebarCollapsed" />
    </aside>

    <!-- 顶栏 -->
    <header class="layout-header">
      <Header />
    </header>

    <!-- 主视图 -->
    <main :class="['layout-main', { 'layout-main--no-padding': isFullHeightRoute }]">
      <router-view />
    </main>

    <!-- 底部状态栏 -->
    <footer class="layout-footer">
      <StatusBar />
    </footer>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute } from 'vue-router'
import Header from './Header.vue'
import Sidebar from './Sidebar.vue'
import StatusBar from './StatusBar.vue'

const route = useRoute()
const isFullHeightRoute = computed(() => route.path.startsWith('/wiki') || route.path.startsWith('/chat'))

const sidebarCollapsed = ref(false)
</script>

<style scoped>
.layout-wrapper {
  display: grid;
  grid-template-columns: 240px 1fr; /* 左侧菜单宽 240px */
  grid-template-rows: auto 1fr 34px; /* 顶栏自适应，中间主视图，底栏 34px */
  height: 100dvh;
  width: 100%;
  overflow: hidden;
  transition: grid-template-columns 0.3s ease;
}

.layout-wrapper:has(.layout-sidebar--collapsed) {
  grid-template-columns: 64px 1fr; /* 折叠后左侧菜单宽 64px */
}

.layout-sidebar {
  grid-row: 1 / 4;
  grid-column: 1 / 2;
  background-color: #304156;
  overflow-y: auto;
  overflow-x: hidden;
}

.layout-header {
  grid-row: 1 / 2;
  grid-column: 2 / 3;
  background-color: #fff;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.08);
  z-index: 10;
}

.layout-main {
  grid-row: 2 / 3;
  grid-column: 2 / 3;
  overflow-y: auto;
  padding: 16px;
  background-color: #f5f7fa;
}

.layout-main--no-padding {
  padding: 0;
  overflow: hidden;
}

.layout-footer {
  grid-row: 3 / 4;
  grid-column: 2 / 3;
  background-color: #fff;
  border-top: 1px solid #e4e7ed;
}
</style>

import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    component: () => import('@/components/Layout/index.vue'),
    redirect: '/home',
    children: [
      {
        path: 'home',
        name: 'Home',
        component: () => import('@/views/Home.vue'),
        meta: { title: '首页' },
      },
      {
        path: 'wiki',
        name: 'Wiki',
        component: () => import('@/views/WikiManager.vue'),
        meta: { title: 'Wiki' },
      },
      {
        path: 'fileshare',
        name: 'FileShare',
        component: () => import('@/views/FileShare.vue'),
        meta: { title: '文件共享' },
      },
      {
        path: 'chat',
        name: 'Chat',
        component: () => import('@/views/ChatView.vue'),
        meta: { title: 'AI 聊天' },
      },
      {
        path: 'skills',
        name: 'SkillManager',
        component: () => import('@/views/SkillManager.vue'),
        meta: { title: 'Skill 管理' },
      },
      {
        path: 'monitor',
        name: 'CapacityMonitor',
        component: () => import('@/views/CapacityMonitor.vue'),
        meta: { title: '容量监控' },
      },
      {
        path: 'monitor-trend',
        name: 'MonitorTrend',
        component: () => import('@/views/MonitorTrend.vue'),
        meta: { title: '监控走势' },
      },
      {
        path: 'agent',
        name: 'Agent',
        component: () => import('@/views/AgentView.vue'),
        meta: { title: 'Agent' },
      },
    ],
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('@/views/NotFound.vue'),
    meta: { title: '404' },
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

export default router

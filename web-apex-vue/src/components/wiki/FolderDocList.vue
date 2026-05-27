<template>
  <div class="px-12 py-10 animate-fade-in">
    <!-- 文件夹标题区 -->
    <div class="flex items-center justify-between mb-6">
      <div class="flex items-center gap-3">
        <div class="text-4xl">📁</div>
        <div>
          <h2 class="text-2xl font-bold text-slate-800">{{ folderTitle }}</h2>
          <p class="text-sm text-slate-400 mt-1">
            共 {{ children.length }} 个节点
            <template v-if="children.length > 0">
              （{{ docCount }} 篇文档，{{ folderCount }} 个文件夹）
            </template>
          </p>
        </div>
      </div>
      <div class="flex items-center gap-2">
        <el-button size="small" @click="$emit('importMarkdown')">
          <el-icon class="mr-1"><Upload /></el-icon>导入文档
        </el-button>
        <el-button size="small" type="primary" plain @click="$emit('createDoc')">
          <el-icon class="mr-1"><DocumentAdd /></el-icon>新建文档
        </el-button>
      </div>
    </div>

    <!-- 空状态 -->
    <el-empty
      v-if="loading"
      description="加载中..."
      :image-size="80"
    />
    <el-empty
      v-else-if="children.length === 0"
      description="此文件夹为空，请新建文档或导入 Markdown 文件"
      :image-size="120"
    >
      <template #image>
        <div class="text-5xl mb-2">📂</div>
      </template>
    </el-empty>

    <!-- 文档列表 -->
    <div v-else class="grid grid-cols-1 gap-3">
      <div
        v-for="item in children"
        :key="item.id"
        class="flex items-center gap-3 p-4 rounded-xl border border-slate-100 hover:border-indigo-200 hover:bg-indigo-50/30 cursor-pointer transition-all group"
        @click="$emit('selectNode', item)"
      >
        <!-- 图标 -->
        <div
          class="w-10 h-10 rounded-lg flex items-center justify-center shrink-0"
          :class="item.type === 1 ? 'bg-amber-100 text-amber-500' : 'bg-indigo-100 text-indigo-500'"
        >
          <el-icon :size="18">
            <Folder v-if="item.type === 1" />
            <Document v-else />
          </el-icon>
        </div>

        <!-- 信息 -->
        <div class="flex-1 min-w-0">
          <div class="text-sm font-medium text-slate-700 group-hover:text-indigo-600 truncate transition-colors">
            {{ item.title }}
          </div>
          <div class="text-xs text-slate-400 mt-0.5">
            <template v-if="item.type === 1">📁 文件夹</template>
            <template v-else>📄 文档</template>
            <span v-if="item.updateTime" class="ml-2">· {{ formatTime(item.updateTime) }}</span>
          </div>
        </div>

        <!-- 右侧指示箭头 -->
        <el-icon class="text-slate-300 group-hover:text-indigo-400 transition-colors shrink-0">
          <ArrowRight />
        </el-icon>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Folder, Document, Upload, DocumentAdd, ArrowRight } from '@element-plus/icons-vue'
import type { WikiNodeVO } from '@/types/wiki'

const props = defineProps<{
  folderTitle: string
  children: WikiNodeVO[]
  loading: boolean
}>()

defineEmits<{
  selectNode: [item: WikiNodeVO]
  createDoc: []
  importMarkdown: []
}>()

const docCount = computed(() => props.children.filter((c) => c.type === 2).length)
const folderCount = computed(() => props.children.filter((c) => c.type === 1).length)

const formatTime = (time?: string): string => {
  if (!time) return ''
  const d = new Date(time)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}
</script>

<style scoped>
.animate-fade-in {
  animation: fadeIn 0.3s ease-in-out;
}

@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateY(6px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
</style>

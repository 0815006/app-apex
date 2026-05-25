<template>
  <div class="flex h-full w-full bg-slate-50/60 font-sans overflow-hidden">
    <!-- ========== 左侧边栏：目录树 ========== -->
    <aside class="w-72 bg-white/75 backdrop-blur-md border-r border-slate-200/50 flex flex-col shadow-sm z-10">
      <!-- 顶部工具栏 -->
      <div class="p-4 flex justify-between items-center border-b border-slate-100">
        <div class="flex items-center gap-2">
          <span class="text-xl">🧠</span>
          <span class="text-base font-bold tracking-tight text-slate-800">Wiki文档</span>
        </div>
        <div class="flex items-center gap-1">
          <el-button size="small" type="primary" plain class="!rounded-full !px-3" @click="triggerMdImport">
            <el-icon class="mr-1"><Upload /></el-icon>导入
          </el-button>
          <input
            ref="fileInputRef"
            type="file"
            accept=".md"
            class="hidden"
            @change="handleMdFileChange"
          />
        </div>
      </div>

      <!-- 搜索栏 -->
      <div class="px-3 pt-3">
        <el-input
          v-model="filterText"
          placeholder="搜索文档或文件夹..."
          size="small"
          :prefix-icon="Search"
          clearable
        />
      </div>

      <!-- 目录树区域 -->
      <div class="flex-1 overflow-y-auto px-2 py-2">
        <el-tree
          ref="treeRef"
          :data="wikiTreeData"
          :props="defaultProps"
          node-key="id"
          highlight-current
          default-expand-all
          :filter-node-method="filterNode"
          @node-click="handleNodeClick"
          class="!bg-transparent wiki-custom-tree"
        >
          <template #default="{ node, data }">
            <span class="flex items-center justify-between w-full group pr-1">
              <span class="flex items-center gap-2 text-sm truncate min-w-0">
                <el-icon v-if="data.type === 1" class="text-amber-400 shrink-0"><Folder /></el-icon>
                <el-icon v-else class="text-indigo-400 shrink-0"><Document /></el-icon>
                <span class="truncate text-slate-600 group-hover:text-indigo-600 transition-colors">
                  {{ node.label }}
                </span>
              </span>
              <el-dropdown trigger="click" @command="(cmd: string) => handleCommand(cmd, data)">
                <el-icon class="opacity-0 group-hover:opacity-100 text-slate-400 hover:text-slate-600 transition-opacity shrink-0 ml-1">
                  <MoreFilled />
                </el-icon>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="createDoc" v-if="data.type === 1">
                      <el-icon class="mr-1"><DocumentAdd /></el-icon>新建文档
                    </el-dropdown-item>
                    <el-dropdown-item command="createFolder" v-if="data.type === 1">
                      <el-icon class="mr-1"><FolderAdd /></el-icon>新建文件夹
                    </el-dropdown-item>
                    <el-dropdown-item command="rename">
                      <el-icon class="mr-1"><Edit /></el-icon>重命名
                    </el-dropdown-item>
                    <el-dropdown-item command="delete" class="!text-red-500">
                      <el-icon class="mr-1"><Delete /></el-icon>删除
                    </el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </span>
          </template>
        </el-tree>
      </div>

      <!-- 底部操作栏 -->
      <div class="p-4 border-t border-slate-100 bg-slate-50/50">
        <div class="flex flex-col gap-2">
          <el-button class="w-full !rounded-xl shadow-sm" @click="handleCreateRootDoc">
            <span class="inline-flex items-center gap-1.5">
              <el-icon><DocumentAdd /></el-icon>新建根文档
            </span>
          </el-button>
          <el-button v-if="currentDoc.id" type="primary" class="w-full !rounded-xl shadow-sm" @click="toggleEditMode">
            <span class="inline-flex items-center gap-1.5">
              <el-icon><EditPen /></el-icon>
              {{ isEditing ? '保存修改' : '编辑当前文档' }}
            </span>
          </el-button>
        </div>
      </div>
    </aside>

    <!-- ========== 右侧内容区 ========== -->
    <main class="flex-1 flex flex-col bg-white overflow-hidden" v-loading="loading">
      <!-- 顶部面包屑 -->
      <header class="h-14 border-b border-slate-100 flex items-center justify-between px-8 bg-slate-50/30 shrink-0">
        <div class="flex items-center gap-2 text-sm text-slate-400">
          <span>工作区</span>
          <span class="text-xs">/</span>
          <span class="text-slate-600 font-medium">{{ currentDoc.title || '未选择文档' }}</span>
        </div>
        <div class="text-xs text-slate-400" v-if="currentDoc.updateTime">
          最后更新：{{ formatTime(currentDoc.updateTime) }}
        </div>
      </header>

      <!-- 内容区域 -->
      <div class="flex-1 overflow-y-auto content-scroll-container" @click="handleContentAreaClick">
        <!-- 编辑模式 -->
        <div v-if="isEditing && currentDoc.id" class="h-full p-6 animate-fade-in flex flex-col">
          <div class="w-full mb-4">
            <input
              v-model="currentDoc.title"
              class="w-full text-3xl font-bold border-b-2 border-slate-200 focus:border-indigo-500 focus:outline-none pb-2 text-slate-800 bg-transparent"
              placeholder="无标题文档"
            />
          </div>
          <div class="flex-1 w-full">
            <md-editor
              v-model="currentDoc.content"
              language="zh-CN"
              :toolbars-exclude="['github']"
              preview-theme="default"
              class="!h-full !border !border-slate-200 !rounded-xl !shadow-sm"
            />
          </div>
        </div>

        <!-- 预览模式 -->
        <div v-else class="px-12 py-10 animate-fade-in">
          <div v-if="currentDoc.id" class="prose prose-slate prose-indigo max-w-none">
            <h1 class="text-3xl font-extrabold text-slate-800 tracking-tight mb-8 !mt-0">
              {{ currentDoc.title }}
            </h1>
            <MdPreview :modelValue="processedContent" class="wiki-preview-content" />
          </div>
          <el-empty
            v-else
            description="请在左侧选择或导入一个 Wiki 文档开始阅读"
            class="mt-24"
            :image-size="160"
          >
            <template #image>
              <div class="text-6xl mb-4">📖</div>
            </template>
          </el-empty>
        </div>
      </div>
    </main>

    <!-- ========== 新建/重命名对话框 ========== -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="420px"
      :close-on-click-modal="false"
      destroy-on-close
      class="wiki-dialog"
    >
      <el-form @submit.prevent="handleDialogConfirm">
        <el-form-item label="名称" required>
          <el-input
            v-model="dialogFormTitle"
            placeholder="请输入名称"
            maxlength="100"
            show-word-limit
            ref="dialogInputRef"
            @keyup.enter="handleDialogConfirm"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleDialogConfirm" :disabled="!dialogFormTitle.trim()">
          确定
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, nextTick, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { MdEditor, MdPreview } from 'md-editor-v3'
import 'md-editor-v3/lib/style.css'
import {
  Search,
  Upload,
  Folder,
  Document,
  MoreFilled,
  DocumentAdd,
  FolderAdd,
  Edit,
  Delete,
  EditPen,
} from '@element-plus/icons-vue'
import { getWikiTree, getDocDetail, getDocByTitle, saveDoc, deleteDoc } from '@/api/wiki'
import type { WikiNodeVO, WikiDocument } from '@/types/wiki'

// ==================== 状态定义 ====================

const wikiTreeData = ref<WikiNodeVO[]>([])
const currentDoc = ref<WikiDocument>({
  title: '',
  content: '',
  type: 2,
  parentId: '0',
})
const isEditing = ref(false)
const loading = ref(false)
const filterText = ref('')
const fileInputRef = ref<HTMLInputElement | null>(null)
const treeRef = ref<InstanceType<typeof import('element-plus').ElTree> | null>(null)
const dialogInputRef = ref<InstanceType<typeof import('element-plus').ElInput> | null>(null)

// 对话框相关
const dialogVisible = ref(false)
const dialogTitle = ref('')
const dialogFormTitle = ref('')
const dialogMode = ref<'createDoc' | 'createFolder' | 'rename'>('createDoc')
const dialogTargetData = ref<WikiNodeVO | null>(null)

// ==================== 树配置 ====================

const defaultProps = {
  children: 'children',
  label: 'title',
}

// ==================== 数据加载 ====================

const loadTree = async () => {
  try {
    const res = await getWikiTree()
    if (res.code === 200) {
      wikiTreeData.value = res.data || []
    }
  } catch {
    // 接口异常由拦截器处理
  }
}

onMounted(() => {
  loadTree()
})

// ==================== 搜索过滤 ====================

watch(filterText, (val) => {
  ;(treeRef.value as { filter: (v: string) => void } | null)?.filter(val)
})

function filterNode(value: string, data: WikiNodeVO): boolean {
  if (!value) return true
  return data.title.toLowerCase().includes(value.toLowerCase())
}

// ==================== 树节点点击 ====================

const handleNodeClick = async (data: WikiNodeVO) => {
  if (data.type === 1) {
    // 文件夹不触发内容加载
    return
  }
  await loadDocument(data.id)
}

const loadDocument = async (id: string) => {
  loading.value = true
  try {
    const res = await getDocDetail(id)
    if (res.code === 200 && res.data) {
      currentDoc.value = res.data
      isEditing.value = false
    }
  } catch {
    // 拦截器统一处理
  } finally {
    loading.value = false
  }
}

// ==================== 编辑模式切换 ====================

const toggleEditMode = async () => {
  if (isEditing.value) {
    // 保存
    if (!currentDoc.value.title.trim()) {
      ElMessage.warning('标题不能为空')
      return
    }
    loading.value = true
    try {
      const res = await saveDoc({
        id: currentDoc.value.id,
        title: currentDoc.value.title,
        content: currentDoc.value.content,
        type: currentDoc.value.type,
        parentId: currentDoc.value.parentId,
      })
      if (res.code === 200) {
        currentDoc.value = res.data
        isEditing.value = false
        ElMessage.success('保存成功')
        await loadTree()
      }
    } catch {
      // 拦截器处理
    } finally {
      loading.value = false
    }
  } else {
    isEditing.value = true
  }
}

// ==================== 双链跳转: [[标题]] 正则处理 ====================

const processedContent = computed(() => {
  if (!currentDoc.value.content) return ''
  const regex = /\[\[(.*?)\]\]/g
  return currentDoc.value.content.replace(regex, (_, title: string) => {
    return `<a href="javascript:void(0);" data-wiki-title="${title}" class="wiki-internal-double-link">🔗 ${title}</a>`
  })
})

const handleContentAreaClick = async (e: MouseEvent) => {
  const targetLink = (e.target as HTMLElement).closest('.wiki-internal-double-link') as HTMLElement | null
  if (!targetLink) return

  const title = targetLink.getAttribute('data-wiki-title')
  if (!title) return

  loading.value = true
  try {
    const res = await getDocByTitle(title)
    if (res.code === 200 && res.data) {
      // 情景 A: 文档存在，丝滑切换
      currentDoc.value = res.data
      isEditing.value = false
    } else if (res.code === 404) {
      // 情景 B: 文档不存在（拦截器对 404 放行 resolve），引导创建
      handleDoubleLinkNotFound(title)
    }
  } catch {
    // 网络异常等由拦截器统一处理
  } finally {
    loading.value = false
  }
}

const handleDoubleLinkNotFound = (title: string) => {
  ElMessageBox.confirm(
    `知识库中暂未找到名为「${title}」的文档，是否立即为您创建一个？`,
    '双链断开提示',
    {
      confirmButtonText: '立即新建',
      cancelButtonText: '再想想',
      type: 'info',
      buttonSize: 'default',
    }
  )
    .then(() => {
      currentDoc.value = {
        title: title,
        content: `# ${title}\n\n在此输入新文档的内容...`,
        type: 2,
        parentId: currentDoc.value.parentId || '0',
      }
      isEditing.value = true
    })
    .catch(() => {
      // 用户取消
    })
}

// ==================== 右键菜单操作 ====================

const handleCommand = (cmd: string, data: WikiNodeVO) => {
  switch (cmd) {
    case 'createDoc':
      openDialog('createDoc', data)
      break
    case 'createFolder':
      openDialog('createFolder', data)
      break
    case 'rename':
      openDialog('rename', data)
      break
    case 'delete':
      handleDelete(data)
      break
  }
}

const openDialog = (mode: 'createDoc' | 'createFolder' | 'rename', data?: WikiNodeVO) => {
  dialogMode.value = mode
  dialogTargetData.value = data || null

  if (mode === 'createDoc') {
    dialogTitle.value = '新建文档'
    dialogFormTitle.value = ''
  } else if (mode === 'createFolder') {
    dialogTitle.value = '新建文件夹'
    dialogFormTitle.value = ''
  } else if (mode === 'rename') {
    dialogTitle.value = '重命名'
    dialogFormTitle.value = data?.title || ''
  }

  dialogVisible.value = true
  nextTick(() => {
    ;(dialogInputRef.value as { focus: () => void } | null)?.focus()
  })
}

const handleDialogConfirm = async () => {
  const title = dialogFormTitle.value.trim()
  if (!title) {
    ElMessage.warning('名称不能为空')
    return
  }

  if (dialogMode.value === 'rename') {
    await handleRename(title)
  } else {
    await handleCreate(title)
  }
}

const handleCreate = async (title: string) => {
  const parentId = dialogTargetData.value?.id || '0'
  const type = dialogMode.value === 'createDoc' ? 2 : 1

  loading.value = true
  try {
    const res = await saveDoc({
      title,
      content: type === 2 ? `# ${title}\n\n新文档内容...` : null as unknown as string,
      type,
      parentId,
    })
    if (res.code === 200) {
      ElMessage.success(`${dialogMode.value === 'createDoc' ? '文档' : '文件夹'}创建成功`)
      dialogVisible.value = false
      await loadTree()
      // 如果是文档，自动选中
      if (type === 2 && res.data) {
        currentDoc.value = res.data
        isEditing.value = false
      }
    }
  } catch {
    // 拦截器处理
  } finally {
    loading.value = false
  }
}

const handleRename = async (newTitle: string) => {
  if (!dialogTargetData.value) return

  loading.value = true
  try {
    // 先获取完整文档数据，更新标题后保存
    const res = await getDocDetail(dialogTargetData.value.id)
    if (res.code === 200 && res.data) {
      const updated = { ...res.data, title: newTitle }
      const saveRes = await saveDoc(updated)
      if (saveRes.code === 200) {
        ElMessage.success('重命名成功')
        dialogVisible.value = false
        await loadTree()
        // 如果当前打开的就是被重命名的文档，更新标题
        if (currentDoc.value.id === dialogTargetData.value.id) {
          currentDoc.value.title = newTitle
        }
      }
    }
  } catch {
    // 拦截器处理
  } finally {
    loading.value = false
  }
}

const handleDelete = async (data: WikiNodeVO) => {
  const typeLabel = data.type === 1 ? '文件夹' : '文档'
  const warningMsg =
    data.type === 1
      ? `确定要删除文件夹「${data.title}」及其所有子内容吗？此操作不可恢复。`
      : `确定要删除文档「${data.title}」吗？此操作不可恢复。`

  try {
    await ElMessageBox.confirm(warningMsg, `删除${typeLabel}`, {
      confirmButtonText: '确认删除',
      cancelButtonText: '取消',
      type: 'warning',
      confirmButtonClass: 'el-button--danger',
    })

    loading.value = true
    const res = await deleteDoc(data.id)
    if (res.code === 200) {
      ElMessage.success(`${typeLabel}已删除`)
      // 如果删除的是当前打开的文档，清空
      if (currentDoc.value.id === data.id) {
        currentDoc.value = { title: '', content: '', type: 2, parentId: '0' }
        isEditing.value = false
      }
      await loadTree()
    }
  } catch (err: unknown) {
    if (err !== 'cancel' && err !== 'close') {
      // 由拦截器处理
    }
  } finally {
    loading.value = false
  }
}

// ==================== 根目录创建 ====================

const handleCreateRootDoc = () => {
  dialogMode.value = 'createDoc'
  dialogTargetData.value = null
  dialogTitle.value = '新建根文档'
  dialogFormTitle.value = ''
  dialogVisible.value = true
  nextTick(() => {
    ;(dialogInputRef.value as { focus: () => void } | null)?.focus()
  })
}

// ==================== Markdown 导入 ====================

const triggerMdImport = () => {
  fileInputRef.value?.click()
}

const handleMdFileChange = (e: Event) => {
  const target = e.target as HTMLInputElement
  const file = target.files?.[0]
  if (!file) return

  const reader = new FileReader()
  loading.value = true

  reader.onload = async (event) => {
    try {
      const mdContent = event.target?.result as string
      const docTitle = file.name.replace(/\.md$/i, '')

      const parentId =
        currentDoc.value.id && currentDoc.value.type === 1 ? currentDoc.value.id : '0'

      const res = await saveDoc({
        title: docTitle,
        content: mdContent,
        type: 2,
        parentId,
      })

      if (res.code === 200) {
        ElMessage.success('Markdown 导入并同步成功')
        await loadTree()
        if (res.data) {
          currentDoc.value = res.data
          isEditing.value = false
        }
      }
    } catch {
      ElMessage.error('导入文档失败')
    } finally {
      loading.value = false
      if (fileInputRef.value) fileInputRef.value.value = ''
    }
  }
  reader.readAsText(file)
}

// ==================== 工具函数 ====================

const formatTime = (time?: string): string => {
  if (!time) return ''
  const d = new Date(time)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}
</script>

<style scoped>
/* ========== Tree 定制样式 ========== */
.wiki-custom-tree :deep(.el-tree-node__content) {
  height: 36px !important;
  border-radius: 8px !important;
  margin: 2px 0;
  transition: all 0.2s ease;
}

.wiki-custom-tree :deep(.el-tree-node.is-current > .el-tree-node__content) {
  background-color: rgb(238 242 255) !important;
  color: rgb(79 70 229) !important;
  font-weight: 600;
}

.wiki-custom-tree :deep(.el-tree-node__content:hover) {
  background-color: rgb(248 250 252) !important;
}

/* ========== 滚动区域平滑 ========== */
.content-scroll-container {
  scroll-behavior: smooth;
}

/* ========== 双链样式 ========== */
.wiki-preview-content :deep(.wiki-internal-double-link) {
  color: rgb(79 70 229);
  text-decoration: none;
  border-bottom: 1px dashed rgb(165 180 252);
  transition: all 0.2s ease;
  font-weight: 500;
}

.wiki-preview-content :deep(.wiki-internal-double-link:hover) {
  color: rgb(55 48 163);
  border-bottom-style: solid;
  background-color: rgb(238 242 255);
  border-radius: 2px;
  padding: 0 2px;
}

/* ========== 淡入动画 ========== */
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

/* ========== Dialog 微调 ========== */
.wiki-dialog :deep(.el-dialog__header) {
  border-bottom: 1px solid rgb(241 245 249);
  padding-bottom: 16px;
}

.wiki-dialog :deep(.el-dialog__footer) {
  border-top: 1px solid rgb(241 245 249);
  padding-top: 16px;
}

/* ========== Prose 覆盖 ========== */
:deep(.prose h1) {
  margin-top: 0 !important;
}

:deep(.prose pre) {
  border-radius: 12px !important;
  box-shadow: 0 1px 3px rgb(0 0 0 / 0.06);
}

:deep(.prose code::before),
:deep(.prose code::after) {
  content: none;
}
</style>

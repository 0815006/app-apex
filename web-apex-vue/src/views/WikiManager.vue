<template>
  <div class="flex h-full w-full bg-slate-50/60 font-sans overflow-hidden" :class="{ 'select-none': isResizing }">
    <!-- ========== 左侧边栏：目录树 ========== -->
    <aside :style="{ width: sidebarWidth + 'px', minWidth: sidebarWidth + 'px' }" class="bg-white/75 backdrop-blur-md border-r border-slate-200/50 flex flex-col shadow-sm z-10 shrink-0">
      <!-- 顶部工具栏 -->
      <div class="p-4 flex justify-between items-center border-b border-slate-100">
        <div class="flex items-center gap-2">
          <span class="text-xl"></span>
          <span class="text-base font-bold tracking-tight text-slate-800">Wiki文档</span>
        </div>
        <div class="flex items-center gap-1">
          <el-button size="small" type="primary" plain class="!rounded-full !px-3" @click="onImportClick">
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

      <!-- 搜索栏 + 刷新 -->
      <div class="px-3 pt-3 flex items-center gap-1">
        <el-input
          v-model="filterText"
          placeholder="搜索文档或文件夹..."
          size="small"
          :prefix-icon="Search"
          clearable
          class="flex-1"
        />
        <el-button size="small" :icon="Refresh" circle class="!border-none !shadow-none" @click="refreshTree" />
      </div>

      <!-- 目录树区域 -->
      <div class="flex-1 overflow-y-auto px-2 py-2">
        <!-- 空状态 -->
        <el-empty
          v-if="wikiTreeData.length === 0"
          description="暂无文档，请新建或导入"
          :image-size="80"
          class="mt-8"
        >
          <template #image>
            <div class="text-3xl">📂</div>
          </template>
        </el-empty>

        <el-tree
          v-else
          ref="treeRef"
          :data="wikiTreeData"
          :props="defaultProps"
          node-key="id"
          highlight-current
          :expand-on-click-node="false"
          :filter-node-method="filterNode"
          draggable
          :allow-drop="allowDrop"
          @node-click="handleNodeClick"
          @node-drop="handleNodeDrop"
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
                <!-- 节点计数徽标 -->
                <el-tag
                  v-if="data.type === 1 && data.children && data.children.length > 0"
                  size="small"
                  class="!text-[10px] !px-1.5 !py-0 !leading-none ml-1 shrink-0"
                  type="info"
                  round
                >
                  {{ data.children.length }}
                </el-tag>
              </span>
              <!-- hover 操作区域 -->
              <span class="flex items-center gap-0.5 shrink-0">
                <!-- 排序按钮 -->
                <el-button
                  v-if="hasSiblingsAbove(data)"
                  text
                  size="small"
                  class="!h-5 !w-5 !p-0 opacity-0 group-hover:opacity-100"
                  @click.stop="handleMoveUp(data)"
                >
                  <el-icon :size="12"><CaretTop /></el-icon>
                </el-button>
                <el-button
                  v-if="hasSiblingsBelow(data)"
                  text
                  size="small"
                  class="!h-5 !w-5 !p-0 opacity-0 group-hover:opacity-100"
                  @click.stop="handleMoveDown(data)"
                >
                  <el-icon :size="12"><CaretBottom /></el-icon>
                </el-button>
                <!-- 右键菜单 -->
                <el-dropdown trigger="click" @command="(cmd: unknown) => handleCommand(String(cmd), data)">
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
                      <el-dropdown-item command="importMarkdown" v-if="data.type === 1">
                        <el-icon class="mr-1"><Upload /></el-icon>导入 .md 到此
                      </el-dropdown-item>
                      <el-dropdown-item command="rename">
                        <el-icon class="mr-1"><Edit /></el-icon>重命名
                      </el-dropdown-item>
                      <el-dropdown-item command="export" v-if="data.type === 2">
                        <el-icon class="mr-1"><Download /></el-icon>导出 Markdown
                      </el-dropdown-item>
                      <el-dropdown-item command="delete" class="!text-red-500">
                        <el-icon class="mr-1"><Delete /></el-icon>删除
                      </el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
              </span>
            </span>
          </template>
        </el-tree>
      </div>

      <!-- 底部操作栏 -->
      <div class="p-4 border-t border-slate-100 bg-slate-50/50">
        <div class="flex flex-col gap-2">
          <div class="flex gap-2">
            <el-button class="flex-1 !rounded-xl shadow-sm" @click="handleCreateRootDoc">
              <span class="inline-flex items-center gap-1.5">
                <el-icon><DocumentAdd /></el-icon>根文档
              </span>
            </el-button>
            <el-button class="flex-1 !rounded-xl shadow-sm" @click="handleCreateRootFolder">
              <span class="inline-flex items-center gap-1.5">
                <el-icon><FolderAdd /></el-icon>根文件夹
              </span>
            </el-button>
          </div>
          <el-button v-if="currentDoc.id" type="primary" class="w-full !rounded-xl shadow-sm" @click="toggleEditMode">
            <span class="inline-flex items-center gap-1.5">
              <el-icon><EditPen /></el-icon>
              {{ isEditing ? '保存修改' : '编辑当前文档' }}
            </span>
          </el-button>
        </div>
      </div>
    </aside>

    <!-- ========== 可拖拽分隔线 ========== -->
    <div
      class="resize-divider"
      @mousedown="onDragStart"
    >
      <div class="resize-divider-line" />
    </div>

    <!-- ========== 右侧内容区 ========== -->
    <main class="flex-1 flex flex-col bg-white overflow-hidden" v-loading="loading">
      <!-- 顶部面包屑（全路径） -->
      <header class="h-14 border-b border-slate-100 flex items-center justify-between px-8 bg-slate-50/30 shrink-0">
        <div class="flex items-center gap-1 text-sm text-slate-400 overflow-hidden">
          <span
            class="cursor-pointer hover:text-indigo-500 transition-colors shrink-0"
            @click="navigateToRoot"
          >工作区</span>
          <div v-for="(crumb, idx) in breadcrumbPath" :key="crumb.id" class="flex items-center gap-1">
            <span class="text-xs mx-1">/</span>
            <span
              class="cursor-pointer hover:text-indigo-500 transition-colors truncate max-w-[200px]"
              :class="{ 'text-slate-600 font-medium': idx === breadcrumbPath.length - 1 }"
              @click="navigateToBreadcrumb(crumb)"
            >
              {{ crumb.title }}
            </span>
          </div>
        </div>
        <div class="text-xs text-slate-400 shrink-0 ml-4" v-if="lastUpdateTimeDisplay">
          最后更新：{{ lastUpdateTimeDisplay }}
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

        <!-- 文件夹内容预览 -->
        <FolderDocList
          v-else-if="currentFolder && !currentDoc.id"
          :folder-title="currentFolder.title"
          :children="folderChildren"
          :loading="folderChildrenLoading"
          @select-node="handleSelectFolderNode"
          @create-doc="handleCreateDocInFolder"
          @import-markdown="handleImportToCurrentFolder"
        />

        <!-- 文档预览模式 -->
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
import { ref, computed, watch, nextTick, onMounted, onBeforeUnmount } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { MdEditor, MdPreview } from 'md-editor-v3'
import 'md-editor-v3/lib/style.css'
import {
  Search,
  Refresh,
  Upload,
  Folder,
  Document,
  MoreFilled,
  DocumentAdd,
  FolderAdd,
  Edit,
  Delete,
  EditPen,
  CaretTop,
  CaretBottom,
  Download,
} from '@element-plus/icons-vue'
import {
  getWikiTree,
  getDocDetail,
  getDocByTitle,
  saveDoc,
  deleteDoc,
  moveNode,
  getFolderChildren,
} from '@/api/wiki'
import type { WikiNodeVO, WikiDocument } from '@/types/wiki'
import FolderDocList from '@/components/wiki/FolderDocList.vue'

// ==================== 分隔线拖拽 ====================
const SIDEBAR_WIDTH_KEY = 'apex_wiki_sidebar_width'

function loadSidebarWidth(): number {
  const stored = localStorage.getItem(SIDEBAR_WIDTH_KEY)
  if (stored) {
    const parsed = parseInt(stored, 10)
    if (!isNaN(parsed) && parsed >= 200 && parsed <= 600) return parsed
  }
  return 320
}

const sidebarWidth = ref(loadSidebarWidth())
const isResizing = ref(false)
let dragStartX = 0
let dragStartWidth = 0

function onDragStart(e: MouseEvent) {
  e.preventDefault()
  isResizing.value = true
  dragStartX = e.clientX
  dragStartWidth = sidebarWidth.value
  document.body.style.cursor = 'col-resize'
  document.addEventListener('mousemove', onDragMove)
  document.addEventListener('mouseup', onDragEnd)
}

function onDragMove(e: MouseEvent) {
  if (!isResizing.value) return
  const dx = e.clientX - dragStartX
  sidebarWidth.value = Math.min(600, Math.max(200, dragStartWidth + dx))
}

function onDragEnd() {
  if (!isResizing.value) return
  isResizing.value = false
  document.removeEventListener('mousemove', onDragMove)
  document.removeEventListener('mouseup', onDragEnd)
  document.body.style.cursor = ''
  localStorage.setItem(SIDEBAR_WIDTH_KEY, String(sidebarWidth.value))
}

// ==================== 状态定义 ====================

const wikiTreeData = ref<WikiNodeVO[]>([])
const currentDoc = ref<WikiDocument>({
  title: '',
  content: '',
  type: 2,
  parentId: '0',
})
const currentFolder = ref<WikiNodeVO | null>(null)
const folderChildren = ref<WikiNodeVO[]>([])
const folderChildrenLoading = ref(false)
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

// 导入目标文件夹（右键「导入到此处」专用）
const importTargetFolderId = ref<string | null>(null)

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

const refreshTree = async () => {
  await loadTree()
  ElMessage.success('目录树已刷新')
}

onMounted(() => {
  loadTree()
})

onBeforeUnmount(() => {
  document.removeEventListener('mousemove', onDragMove)
  document.removeEventListener('mouseup', onDragEnd)
  document.body.style.cursor = ''
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
    // 文件夹：仅展开/折叠，不触发后台请求
    const node = (treeRef.value as { getNode: (key: string) => { expanded: boolean; expand: () => void; collapse: () => void } | null } | null)?.getNode(data.id)
    if (node) {
      if (node.expanded) {
        node.collapse()
      } else {
        node.expand()
      }
    }
  } else {
    // 文档：加载内容
    currentFolder.value = null
    folderChildren.value = []
    await loadDocument(data.id)
  }
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

const loadFolderContent = async (folder: WikiNodeVO) => {
  currentFolder.value = folder
  folderChildrenLoading.value = true
  try {
    const res = await getFolderChildren(folder.id)
    if (res.code === 200) {
      folderChildren.value = res.data || []
    }
  } catch {
    folderChildren.value = []
  } finally {
    folderChildrenLoading.value = false
  }
}

// ==================== 文件夹内选择节点 ====================

const handleSelectFolderNode = async (item: WikiNodeVO) => {
  if (item.type === 1) {
    // 文件夹内的文件夹 → 进入该文件夹
    await loadFolderContent(item)
    // 同时定位并展开树
    ;(treeRef.value as { setCurrentKey: (id: string) => void } | null)?.setCurrentKey(item.id)
  } else {
    // 文档 → 加载
    currentFolder.value = null
    folderChildren.value = []
    await loadDocument(item.id)
    ;(treeRef.value as { setCurrentKey: (id: string) => void } | null)?.setCurrentKey(item.id)
  }
}

// ==================== 编辑模式切换 ====================

const toggleEditMode = async () => {
  if (isEditing.value) {
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

// ==================== 面包屑头部时间显示 ====================

const lastUpdateTimeDisplay = computed(() => {
  const docTime = currentDoc.value.updateTime
  const folderTime = currentFolder.value?.updateTime
  const time = docTime || folderTime
  if (!time) return ''
  return formatTime(time)
})

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
      currentDoc.value = res.data
      currentFolder.value = null
      isEditing.value = false
    } else if (res.code === 404) {
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
    case 'importMarkdown':
      handleImportToFolder(data)
      break
    case 'rename':
      openDialog('rename', data)
      break
    case 'export':
      handleExport(data)
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
      if (type === 2 && res.data) {
        currentDoc.value = res.data
        currentFolder.value = null
        isEditing.value = false
      } else if (type === 1 && dialogTargetData.value) {
        // 在当前文件夹下创建子文件夹，刷新该文件夹内容
        await loadFolderContent(dialogTargetData.value)
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
    const res = await getDocDetail(dialogTargetData.value.id)
    if (res.code === 200 && res.data) {
      const updated = { ...res.data, title: newTitle }
      const saveRes = await saveDoc(updated)
      if (saveRes.code === 200) {
        ElMessage.success('重命名成功')
        dialogVisible.value = false
        await loadTree()
        if (currentDoc.value.id === dialogTargetData.value.id) {
          currentDoc.value.title = newTitle
        }
        if (currentFolder.value?.id === dialogTargetData.value.id) {
          currentFolder.value.title = newTitle
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
      if (currentDoc.value.id === data.id) {
        currentDoc.value = { title: '', content: '', type: 2, parentId: '0' }
        isEditing.value = false
      }
      if (currentFolder.value?.id === data.id) {
        currentFolder.value = null
        folderChildren.value = []
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

const handleCreateRootFolder = () => {
  dialogMode.value = 'createFolder'
  dialogTargetData.value = null
  dialogTitle.value = '新建根文件夹'
  dialogFormTitle.value = ''
  dialogVisible.value = true
  nextTick(() => {
    ;(dialogInputRef.value as { focus: () => void } | null)?.focus()
  })
}

// ==================== 文件夹内快捷操作 ====================

const handleCreateDocInFolder = () => {
  if (!currentFolder.value) return
  dialogMode.value = 'createDoc'
  dialogTargetData.value = currentFolder.value
  dialogTitle.value = `在「${currentFolder.value.title}」下新建文档`
  dialogFormTitle.value = ''
  dialogVisible.value = true
  nextTick(() => {
    ;(dialogInputRef.value as { focus: () => void } | null)?.focus()
  })
}

const handleImportToCurrentFolder = () => {
  if (!currentFolder.value) return
  importTargetFolderId.value = currentFolder.value.id
  fileInputRef.value?.click()
}

const handleImportToFolder = (data: WikiNodeVO) => {
  importTargetFolderId.value = data.id
  fileInputRef.value?.click()
}

// ==================== 拖拽排序与移动 ====================

const allowDrop = (
  _draggingNode: { data: WikiNodeVO },
  dropNode: { data: WikiNodeVO },
  type: string
): boolean => {
  // 只能拖到节点之前或之后（同级排序），或者拖到文件夹内部
  if (type === 'inner') {
    // 拖入文件夹内部 → 只允许文件夹接收
    return dropNode.data.type === 1
  }
  // 同级排序 → 始终允许
  return true
}

const handleNodeDrop = async (
  draggingNode: { data: WikiNodeVO },
  dropNode: { data: WikiNodeVO },
  dropType: string,
  _ev: unknown
) => {
  const draggedId = draggingNode.data.id
  let newParentId: string
  let newSortOrder: number

  if (dropType === 'inner') {
    // 拖入文件夹内部 → 成为其子节点
    newParentId = dropNode.data.id
    // 获取目标文件夹的现有子节点数，放到最后
    const existingChildren = dropNode.data.children || []
    newSortOrder = existingChildren.length * 10
  } else {
    // 同级排序 → 保持同一父节点，计算新的 sort_order
    newParentId = dropNode.data.parentId
    // 用目标节点的 sort_order 作为参考
    const siblings = getFlatSiblings(dropNode.data.parentId)
    const targetIdx = siblings.findIndex((s) => s.id === dropNode.data.id)
    if (dropType === 'before') {
      // 插入到目标之前
      if (targetIdx <= 0) {
        newSortOrder = 0
      } else {
        const prevSortOrder = siblings[targetIdx - 1].sortOrder || 0
        const targetSortOrder = siblings[targetIdx].sortOrder || 0
        newSortOrder = Math.floor((prevSortOrder + targetSortOrder) / 2)
      }
    } else {
      // 插入到目标之后 (after)
      if (targetIdx >= siblings.length - 1) {
        newSortOrder = ((siblings[targetIdx].sortOrder || 0) + 10)
      } else {
        const targetSortOrder = siblings[targetIdx].sortOrder || 0
        const nextSortOrder = siblings[targetIdx + 1].sortOrder || 0
        newSortOrder = Math.floor((targetSortOrder + nextSortOrder) / 2)
      }
    }
  }

  loading.value = true
  try {
    const res = await moveNode(draggedId, newParentId, newSortOrder)
    if (res.code === 200) {
      ElMessage.success('移动成功')
      await loadTree()
      // 如果当前文件夹内容需要刷新
      if (currentFolder.value) {
        await loadFolderContent(currentFolder.value)
      }
    } else {
      ElMessage.error('移动失败：' + (res.message || '未知错误'))
    }
  } catch (err: unknown) {
    const msg = err instanceof Error ? err.message : '请求失败'
    ElMessage.error('移动失败：' + msg)
  } finally {
    loading.value = false
  }
}

// 获取指定 parentId 下所有同级的平铺节点列表（用于计算 sort_order）
const getFlatSiblings = (parentId: string): { id: string; sortOrder: number }[] => {
  // 从树中递归查找
  const result: { id: string; sortOrder: number }[] = []
  const collectFromTree = (nodes: WikiNodeVO[]) => {
    for (const node of nodes) {
      if (node.parentId === parentId) {
        // 这是顶层同级节点
        for (let i = 0; i < nodes.length; i++) {
          if (nodes[i].parentId === parentId) {
            result.push({ id: nodes[i].id, sortOrder: i * 10 })
          }
        }
        return
      }
      if (node.children) {
        collectFromTree(node.children)
      }
    }
  }
  collectFromTree(wikiTreeData.value)
  return result
}

// ==================== 排序按钮 ====================

const findParentNode = (data: WikiNodeVO): { parentId: string; siblings: WikiNodeVO[] } | null => {
  const find = (nodes: WikiNodeVO[]): { parentId: string; siblings: WikiNodeVO[] } | null => {
    for (const node of nodes) {
      if (node.children) {
        if (node.children.some((c) => c.id === data.id)) {
          return { parentId: node.id, siblings: node.children }
        }
        const result = find(node.children)
        if (result) return result
      }
    }
    return null
  }
  // 根层级
  if (wikiTreeData.value.some((n) => n.id === data.id)) {
    return { parentId: '0', siblings: wikiTreeData.value }
  }
  return find(wikiTreeData.value)
}

const hasSiblingsAbove = (data: WikiNodeVO): boolean => {
  const parent = findParentNode(data)
  if (!parent) return false
  const idx = parent.siblings.findIndex((s) => s.id === data.id)
  return idx > 0
}

const hasSiblingsBelow = (data: WikiNodeVO): boolean => {
  const parent = findParentNode(data)
  if (!parent) return false
  const idx = parent.siblings.findIndex((s) => s.id === data.id)
  return idx < parent.siblings.length - 1
}

const handleMoveUp = async (data: WikiNodeVO) => {
  const parent = findParentNode(data)
  if (!parent) return
  const idx = parent.siblings.findIndex((s) => s.id === data.id)
  if (idx <= 0) return

  loading.value = true
  try {
    const res = await moveNode(data.id, parent.parentId, (idx - 1) * 10 - 5)
    if (res.code !== 200) {
      ElMessage.error('上移失败：' + (res.message || '未知错误'))
      return
    }
    await loadTree()
  } catch (err: unknown) {
    const msg = err instanceof Error ? err.message : '请求失败'
    ElMessage.error('上移失败：' + msg)
  } finally {
    loading.value = false
  }
}

const handleMoveDown = async (data: WikiNodeVO) => {
  const parent = findParentNode(data)
  if (!parent) return
  const idx = parent.siblings.findIndex((s) => s.id === data.id)
  if (idx >= parent.siblings.length - 1) return

  loading.value = true
  try {
    const res = await moveNode(data.id, parent.parentId, (idx + 1) * 10 + 5)
    if (res.code !== 200) {
      ElMessage.error('下移失败：' + (res.message || '未知错误'))
      return
    }
    await loadTree()
  } catch (err: unknown) {
    const msg = err instanceof Error ? err.message : '请求失败'
    ElMessage.error('下移失败：' + msg)
  } finally {
    loading.value = false
  }
}

// ==================== Markdown 导出 ====================

const handleExport = async (data: WikiNodeVO) => {
  if (!data.id) return
  loading.value = true
  try {
    const res = await getDocDetail(data.id)
    if (res.code === 200 && res.data) {
      const doc = res.data
      const blob = new Blob([doc.content || ''], { type: 'text/markdown;charset=utf-8' })
      const url = URL.createObjectURL(blob)
      const link = document.createElement('a')
      link.href = url
      link.download = `${doc.title || 'untitled'}.md`
      document.body.appendChild(link)
      link.click()
      document.body.removeChild(link)
      URL.revokeObjectURL(url)
      ElMessage.success('导出成功')
    } else {
      ElMessage.error('导出失败：无法获取文档内容')
    }
  } catch {
    ElMessage.error('导出失败')
  } finally {
    loading.value = false
  }
}

// ==================== Markdown 导入 ====================

const onImportClick = () => {
  importTargetFolderId.value = null
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

      // 确定 parentId：优先使用右键指定的文件夹，其次当前选中的文件夹
      const parentId =
        importTargetFolderId.value ||
        (currentFolder.value?.id ? currentFolder.value.id : '0')

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
          currentFolder.value = null
          isEditing.value = false
        }
        // 如果导入到文件夹，刷新文件夹内容
        if (parentId !== '0' && currentFolder.value?.id === parentId) {
          await loadFolderContent(currentFolder.value)
        }
      }
    } catch {
      ElMessage.error('导入文档失败')
    } finally {
      loading.value = false
      importTargetFolderId.value = null
      if (fileInputRef.value) fileInputRef.value.value = ''
    }
  }
  reader.readAsText(file)
}

// ==================== 面包屑导航 ====================

// 面包屑路径：从根到当前选择节点的完整路径
const breadcrumbPath = computed(() => {
  const targetId = currentDoc.value.id || currentFolder.value?.id
  if (!targetId) return []

  const path: { id: string; title: string }[] = []
  const findPath = (nodes: WikiNodeVO[], currentPath: { id: string; title: string }[]): boolean => {
    for (const node of nodes) {
      const newPath = [...currentPath, { id: node.id, title: node.title }]
      if (node.id === targetId) {
        path.push(...newPath)
        return true
      }
      if (node.children && findPath(node.children, newPath)) {
        return true
      }
    }
    return false
  }
  findPath(wikiTreeData.value, [])
  return path
})

const navigateToRoot = () => {
  currentDoc.value = { title: '', content: '', type: 2, parentId: '0' }
  currentFolder.value = null
  isEditing.value = false
  folderChildren.value = []
  ;(treeRef.value as { setCurrentKey: (id: string) => void } | null)?.setCurrentKey('')
}

const navigateToBreadcrumb = async (crumb: { id: string; title: string }) => {
  // 在树中查找该节点类型
  const findNode = (nodes: WikiNodeVO[]): WikiNodeVO | null => {
    for (const node of nodes) {
      if (node.id === crumb.id) return node
      if (node.children) {
        const found = findNode(node.children)
        if (found) return found
      }
    }
    return null
  }
  const node = findNode(wikiTreeData.value)
  if (!node) return

  if (node.type === 1) {
    await loadFolderContent(node)
    currentDoc.value = { title: '', content: '', type: 2, parentId: '0' }
    isEditing.value = false
  } else {
    currentFolder.value = null
    folderChildren.value = []
    await loadDocument(node.id)
  }
  ;(treeRef.value as { setCurrentKey: (id: string) => void } | null)?.setCurrentKey(node.id)
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

/* 拖拽视觉反馈 */
.wiki-custom-tree :deep(.el-tree-node__drop-indicator) {
  height: 2px;
  background-color: rgb(99 102 241);
  left: 0 !important;
  right: 0 !important;
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
/* ========== 可拖拽分隔线 ========== */
.resize-divider {
  width: 10px;
  height: 100%;
  cursor: col-resize;
  background-color: transparent;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  position: relative;
  z-index: 20;
  transition: background-color 0.15s;
}
.resize-divider:hover {
  background-color: rgba(59, 130, 246, 0.06);
}
.resize-divider-line {
  width: 3px;
  height: 100%;
  border-radius: 2px;
  background-color: transparent;
  transition: background-color 0.2s;
  pointer-events: none;
}
.resize-divider:hover .resize-divider-line,
.resize-divider:active .resize-divider-line {
  background-color: #3b82f6;
}
</style>

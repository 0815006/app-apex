<template>
  <div
    class="flex h-full w-full bg-slate-50/60 font-sans overflow-hidden"
    :class="{ 'select-none': isResizing }"
  >
    <!-- ========== 左侧：文件树 (常驻) ========== -->
    <aside :style="{ width: sidebarWidth + 'px', minWidth: sidebarWidth + 'px' }" class="bg-white/75 backdrop-blur-md border-r border-slate-200/50 flex flex-col shadow-sm z-10 shrink-0">
      <!-- 工作空间选择器 -->
      <div class="p-3 border-b border-slate-100">
        <div class="flex items-center gap-2">
          <span class="text-lg">🤖</span>
          <span class="text-sm font-bold tracking-tight text-slate-700 flex-1">Agent</span>
          <el-button size="small" :icon="Setting" @click="showWorkspacesDialog" title="管理工作空间" />
        </div>
        <el-select
          v-model="selectedWorkspaceId"
          placeholder="选择工作空间"
          class="w-full mt-2"
          size="small"
          :loading="wsLoading"
        >
          <el-option
            v-for="ws in workspaces"
            :key="ws.id"
            :label="ws.name"
            :value="ws.id"
          />
        </el-select>
      </div>

      <!-- 文件树区域 -->
      <div class="flex-1 overflow-y-auto" v-loading="treeLoading">
        <div v-if="!selectedWorkspaceId" class="p-4 text-center text-slate-400 text-xs">
          请先选择工作空间
        </div>
        <div v-else-if="fileTree.length === 0 && !treeLoading" class="p-4 text-center text-slate-400 text-xs">
          工作空间为空
        </div>
        <div v-else class="py-1">
          <div
            v-for="node in fileTree"
            :key="node.path"
          >
            <!-- 目录节点 -->
            <div
              :class="['tree-node', { expanded: expandedDirs.has(node.path) }]"
              @click="toggleDir(node)"
            >
              <span class="tree-arrow">{{ expandedDirs.has(node.path) ? '▼' : '▶' }}</span>
              <span class="tree-icon">📁</span>
              <span class="tree-name">{{ getNodeName(node) }}</span>
              <span
                class="tree-action-wrapper"
                @click.stop
                @mouseleave="activeMenuNode = null"
              >
                <span
                  class="tree-more"
                  @click.stop="activeMenuNode = activeMenuNode === node.path ? null : node.path"
                >⋯</span>
                <div v-if="activeMenuNode === node.path" class="tree-dropdown">
                  <div class="tree-action-item" @click.stop="handleRename(node)">✏️ 重命名</div>
                  <div class="tree-action-item" @click.stop="handleUploadToDir(node)">📤 上传文件</div>
                  <div class="tree-action-item danger" @click.stop="handleDeleteNode(node)">🗑️ 删除</div>
                </div>
              </span>
            </div>
            <!-- 子节点 -->
            <div v-if="expandedDirs.has(node.path) && node.children" class="tree-children">
              <div
                v-for="child in node.children"
                :key="child.path"
                class="tree-node tree-file"
                @click.stop="handleFileClick(child)"
              >
                <span class="tree-icon">📄</span>
                <span class="tree-name">{{ getNodeName(child) }}</span>
                <span
                  class="tree-action-wrapper"
                  @click.stop
                  @mouseleave="activeMenuNode = null"
                >
                  <span
                    class="tree-more"
                    @click.stop="activeMenuNode = activeMenuNode === child.path ? null : child.path"
                  >⋯</span>
                  <div v-if="activeMenuNode === child.path" class="tree-dropdown">
                    <div class="tree-action-item" @click.stop="handleRename(child)">✏️ 重命名</div>
                    <div class="tree-action-item danger" @click.stop="handleDeleteNode(child)">🗑️ 删除</div>
                  </div>
                </span>
              </div>
            </div>
          </div>
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

    <!-- ========== 右侧：动态交互区 (LIST / CHAT 分时复用) ========== -->

    <!-- 状态一：会话列表 (LIST) -->
    <section v-if="agentState === 'LIST'" class="session-list-panel">
      <div class="list-header">
        <h2 class="list-title">任务列表</h2>
        <el-button type="primary" @click="handleNewTask" :disabled="!selectedWorkspaceId">
          <el-icon><Plus /></el-icon>新建交互
        </el-button>
      </div>
      <div class="list-body" v-loading="sessionLoading">
        <div
          v-for="session in sessions"
          :key="session.id"
          :class="['session-card', { active: currentSessionId === session.id }]"
          @click="handleSelectSession(session.id)"
        >
          <div class="session-card-left">
            <el-icon class="text-indigo-400 shrink-0" size="18"><ChatDotRound /></el-icon>
            <div class="session-card-info">
              <span class="session-card-title">{{ session.title }}</span>
              <span class="session-card-meta">{{ session.modelName }} · {{ formatTime(session.updateTime) }}</span>
            </div>
          </div>
          <el-popconfirm
            title="删除此任务？"
            @confirm="handleDeleteSession(session.id)"
            @click.stop
          >
            <template #reference>
              <el-icon class="delete-icon" @click.stop><Close /></el-icon>
            </template>
          </el-popconfirm>
        </div>
        <div v-if="sessions.length === 0 && !sessionLoading" class="empty-list">
          <div class="empty-list-icon">💬</div>
          <div class="empty-list-title">发起你的第一个 Agent 任务</div>
          <div class="empty-list-desc">选择工作空间后，点击上方「新建交互」开始</div>
        </div>
      </div>
    </section>

    <!-- 状态二：当前聊天区 (CHAT) -->
    <section v-else class="chat-main">
      <!-- 顶部：返回列表 + 模型选择 + 会话标题 -->
      <header class="chat-header">
        <div class="header-left">
          <button class="back-btn" @click="backToList">← 返回列表</button>
          <el-select
            v-model="selectedConfigId"
            placeholder="选择模型"
            size="default"
            class="model-select"
            :loading="configLoading"
          >
            <el-option
              v-for="c in agentConfigs"
              :key="c.id"
              :label="`${c.configName} (${c.modelName})`"
              :value="c.id"
            />
          </el-select>
        </div>
        <div class="header-right">
          <span v-if="currentSessionId" class="current-session-badge">
            <span class="badge-dot" />
            {{ currentSessionTitle || 'Agent 任务' }}
          </span>
        </div>
      </header>

      <!-- 消息列表（含工具调用卡片） -->
      <div class="chat-messages" ref="messagesContainer">
        <div v-if="messages.length === 0 && !streaming && !currentSessionId" class="empty-chat">
          <div class="empty-icon">🤖</div>
          <div class="empty-text">输入任务描述，Agent 将自动执行</div>
        </div>

        <template v-for="(msg, idx) in messages">
          <!-- 用户消息 -->
          <div v-if="msg.role === 'user'" :key="'u' + idx" class="message-row user">
            <div class="message-avatar">👤</div>
            <div class="message-bubble" v-html="renderMarkdown(msg.content)" />
          </div>

          <!-- 工具调用消息（tool role） -->
          <div v-else-if="msg.role === 'tool'" :key="'t' + idx" class="tool-call-card">
            <div class="tool-call-header">
              <span class="tool-call-icon">🔧</span>
              <span class="tool-call-name">{{ msg.toolName || '工具调用' }}</span>
              <el-tag
                :type="msg.toolStatus === 'success' ? 'success' : 'danger'"
                size="small"
                effect="plain"
              >
                {{ msg.toolStatus === 'success' ? '完成' : '失败' }}
              </el-tag>
            </div>
            <div class="tool-call-result" v-if="msg.content">
              <pre>{{ msg.content }}</pre>
            </div>
          </div>

          <!-- AI 回复 -->
          <div v-else :key="'a' + idx" class="message-row assistant">
            <div class="message-avatar">🤖</div>
            <div class="message-body">
              <div class="message-bubble" v-html="renderMarkdown(msg.content)" />
              <div class="message-actions">
                <button class="copy-btn" @click="copyMessage(msg.content)">
                  <el-icon><CopyDocument /></el-icon>复制
                </button>
              </div>
            </div>
          </div>
        </template>

        <!-- 流式输出中：推理过程 -->
        <div v-if="streamingReasoning" class="reasoning-block">
          <div class="reasoning-header" @click="reasoningExpanded = !reasoningExpanded">
            <span class="reasoning-icon">{{ reasoningExpanded ? '▼' : '▶' }}</span>
            <span>思考过程</span>
          </div>
          <div v-if="reasoningExpanded" class="reasoning-content" v-html="renderMarkdown(streamingReasoning)" />
        </div>

        <!-- 流式输出中：当前工具调用 -->
        <div v-if="activeToolCall" class="tool-call-card streaming">
          <div class="tool-call-header">
            <span class="tool-call-icon">⚙️</span>
            <span class="tool-call-name">{{ activeToolCall.toolName }}</span>
            <span class="streaming-dot" />
          </div>
          <div class="tool-call-result" v-if="activeToolCall.result">
            <pre>{{ activeToolCall.result }}</pre>
          </div>
        </div>

        <!-- 流式输出中：文本 -->
        <div v-if="streaming" class="message-row assistant">
          <div class="message-avatar">🤖</div>
          <div class="message-bubble streaming" v-html="renderMarkdown(streamingContent || '▊')" />
        </div>

        <div ref="scrollAnchor" />
      </div>

      <!-- 底部输入区 -->
      <footer class="chat-input-area">
        <div class="input-container">
          <div class="input-wrapper">
            <el-input
              v-model="inputContent"
              type="textarea"
              :rows="2"
              placeholder="描述你要完成的任务，Agent 将自动规划执行…"
              resize="none"
              :disabled="!selectedConfigId || sending"
              @keydown.enter.exact="handleSend"
            />
          </div>
          <div class="input-toolbar">
            <span v-if="streaming" class="streaming-hint">
              <span class="pulse-dot" />
              Agent 执行中 ({{ loopCount }}/5)
            </span>
            <div class="toolbar-right">
              <el-button
                v-if="streaming"
                type="warning"
                :icon="VideoPause"
                @click="handleStop"
                round
                size="small"
              >
                停止
              </el-button>
              <el-button
                v-else
                type="primary"
                :disabled="!inputContent.trim() || !selectedConfigId"
                :loading="sending"
                @click="handleSend"
                round
              >
                <span>发送</span>
                <el-icon class="send-icon"><Promotion /></el-icon>
              </el-button>
            </div>
          </div>
        </div>
      </footer>
    </section>
    <!-- 工作空间管理弹窗 -->
    <WorkspacesDialog v-model="workspacesDialogVisible" @workspace-created="onWorkspaceChanged" />
  </div>
</template>

<script setup lang="ts">
import { ref, nextTick, onMounted, onBeforeUnmount, computed, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Plus, Setting, ChatDotRound, Close,
  VideoPause, Promotion, CopyDocument,
} from '@element-plus/icons-vue'
import { marked } from 'marked'
import { listSessions, getMessages, deleteSession, sendAgentMessage, abortAgent, listLlmConfigs } from '@/api/chat'
import { listWorkspaces, getWorkspaceFileTree, deleteWorkspaceFile, renameWorkspaceFile, uploadWorkspaceFile } from '@/api/agent'
import { getCurrentEmpNo } from '@/utils/currentUser'
import WorkspacesDialog from '@/components/agent/WorkspacesDialog.vue'
import type { ChatSessionVO, ChatMessage, LlmConfigVO, AgentWorkspace, WorkspaceFileNode } from '@/types/chat'


// ========== 分隔线拖拽 ==========
const SIDEBAR_WIDTH_KEY_PREFIX = 'apex_agent_sidebar_width_'

function getSidebarWidthKey(): string {
  return SIDEBAR_WIDTH_KEY_PREFIX + getCurrentEmpNo()
}

function loadSidebarWidth(): number {
  const stored = localStorage.getItem(getSidebarWidthKey())
  if (stored) {
    const parsed = parseInt(stored, 10)
    if (!isNaN(parsed) && parsed >= 200 && parsed <= 600) {
      return parsed
    }
  }
  return 288 // 默认 w-72 = 18rem = 288px
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
  localStorage.setItem(getSidebarWidthKey(), String(sidebarWidth.value))
}

// ========== 状态机 ==========
const agentState = ref<'LIST' | 'CHAT'>('LIST')

// ========== 工作空间 ==========
const workspaces = ref<AgentWorkspace[]>([])
const wsLoading = ref(false)
const selectedWorkspaceId = ref<string | null>(null)
const fileTree = ref<WorkspaceFileNode[]>([])
const treeLoading = ref(false)
const expandedDirs = ref<Set<string>>(new Set())
let treeRefreshTimer: ReturnType<typeof setTimeout> | null = null

const LAST_WS_KEY_PREFIX = 'apex_last_workspace_'

function getLastWorkspaceKey(): string {
  return LAST_WS_KEY_PREFIX + getCurrentEmpNo()
}

async function loadWorkspaces() {
  wsLoading.value = true
  try {
    const res = await listWorkspaces()
    if (res.code === 200) {
      workspaces.value = res.data
      // 自动选中最近一次操作的工作空间
      const lastWsId = localStorage.getItem(getLastWorkspaceKey())
      if (lastWsId && workspaces.value.some(ws => ws.id === lastWsId)) {
        selectedWorkspaceId.value = lastWsId
      } else if (workspaces.value.length > 0 && !selectedWorkspaceId.value) {
        // 没有记录时默认选中第一个
        selectedWorkspaceId.value = workspaces.value[0].id ?? null
      }
    }
  } finally {
    wsLoading.value = false
  }
}

async function loadFileTree() {
  if (!selectedWorkspaceId.value) return
  treeLoading.value = true
  try {
    const res = await getWorkspaceFileTree(selectedWorkspaceId.value)
    if (res.code === 200) {
      fileTree.value = res.data
      expandedDirs.value = new Set(res.data.map(n => n.path))
    }
  } finally {
    treeLoading.value = false
  }
}

function debouncedRefreshTree() {
  if (treeRefreshTimer) clearTimeout(treeRefreshTimer)
  treeRefreshTimer = setTimeout(() => {
    loadFileTree()
  }, 300)
}

function toggleDir(node: WorkspaceFileNode) {
  if (expandedDirs.value.has(node.path)) {
    expandedDirs.value.delete(node.path)
  } else {
    expandedDirs.value.add(node.path)
  }
}

function getNodeName(node: WorkspaceFileNode): string {
  // 兼容后端可能返回 label 或 name 字段
  const raw = node as unknown as Record<string, unknown>
  return (raw.name || raw.label || node.path) as string
}

const workspacesDialogVisible = ref(false)

function showWorkspacesDialog() {
  workspacesDialogVisible.value = true
}

function onWorkspaceChanged() {
  loadWorkspaces()
}

function handleFileClick(_node: WorkspaceFileNode) {
  // 预留：可扩展文件预览功能
}

// ========== 文件树三点菜单 ==========
const activeMenuNode = ref<string | null>(null)

async function handleRename(node: WorkspaceFileNode) {
  activeMenuNode.value = null
  const name = getNodeName(node)
  try {
    const { value } = await ElMessageBox.prompt('请输入新名称', '重命名', {
      inputValue: name,
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      inputValidator: (val: string) => {
        if (!val || !val.trim()) return '名称不能为空'
        return true
      },
    })
    if (!value || !selectedWorkspaceId.value) return
    const res = await renameWorkspaceFile(selectedWorkspaceId.value, node.path, value.trim())
    if (res.code === 200) {
      ElMessage.success('重命名成功')
      loadFileTree()
    }
  } catch {
    // 用户取消
  }
}

async function handleUploadToDir(node: WorkspaceFileNode) {
  activeMenuNode.value = null
  // 创建隐藏的 file input 触发选择
  const input = document.createElement('input')
  input.type = 'file'
  input.style.display = 'none'
  input.onchange = async () => {
    const file = input.files?.[0]
    if (!file || !selectedWorkspaceId.value) return
    try {
      const res = await uploadWorkspaceFile(selectedWorkspaceId.value, node.path, file)
      if (res.code === 200) {
        ElMessage.success('文件上传成功')
        loadFileTree()
      }
    } catch {
      // 拦截器已处理
    }
  }
  document.body.appendChild(input)
  input.click()
  document.body.removeChild(input)
}

async function handleDeleteNode(node: WorkspaceFileNode) {
  activeMenuNode.value = null
  const name = getNodeName(node)
  try {
    await ElMessageBox.confirm(
      `确定要永久删除「${name}」吗？${node.type === 'dir' ? '该目录下所有文件都将被删除。' : ''}`,
      '删除确认',
      {
        confirmButtonText: '确认删除',
        cancelButtonText: '取消',
        type: 'warning',
        confirmButtonClass: 'el-button--danger',
      },
    )
    if (!selectedWorkspaceId.value) return
    const res = await deleteWorkspaceFile(selectedWorkspaceId.value, node.path)
    if (res.code === 200) {
      ElMessage.success('删除成功')
      loadFileTree()
    }
  } catch {
    // 用户取消
  }
}

// ========== 会话列表 ==========
const sessions = ref<ChatSessionVO[]>([])
const sessionLoading = ref(false)
const currentSessionId = ref<string | null>(null)
const currentSessionTitle = computed(() => {
  return sessions.value.find(s => s.id === currentSessionId.value)?.title || ''
})

function formatTime(time: string): string {
  if (!time) return ''
  const d = new Date(time)
  const now = new Date()
  const diff = now.getTime() - d.getTime()
  if (diff < 60_000) return '刚刚'
  if (diff < 3_600_000) return Math.floor(diff / 60_000) + ' 分钟前'
  if (diff < 86_400_000) return Math.floor(diff / 3_600_000) + ' 小时前'
  return d.toLocaleDateString('zh-CN')
}

async function loadAgentSessions() {
  sessionLoading.value = true
  try {
    const res = await listSessions('AGENT')
    if (res.code === 200) {
      sessions.value = res.data
    }
  } finally {
    sessionLoading.value = false
  }
}

function handleSelectSession(sessionId: string) {
  currentSessionId.value = sessionId
  agentState.value = 'CHAT'
  loadMessages(sessionId)
}

async function handleDeleteSession(sessionId: string) {
  try {
    const res = await deleteSession(sessionId)
    if (res.code !== 200) return
    ElMessage.success('已删除')
    if (currentSessionId.value === sessionId) {
      currentSessionId.value = null
      messages.value = []
      agentState.value = 'LIST'
    }
    await loadAgentSessions()
  } catch { /* 拦截器已处理 */ }
}

function backToList() {
  abortStream()
  streaming.value = false
  streamingContent.value = ''
  streamingReasoning.value = ''
  activeToolCall.value = null
  loopCount.value = 0
  currentSessionId.value = null
  messages.value = []
  inputContent.value = ''
  agentState.value = 'LIST'
  loadAgentSessions()
}

// ========== 模型选择 ==========
const agentConfigs = ref<LlmConfigVO[]>([])
const configLoading = ref(false)
const selectedConfigId = ref<string | null>(null)

async function loadConfigs() {
  configLoading.value = true
  try {
    const res = await listLlmConfigs()
    if (res.code === 200) {
      agentConfigs.value = res.data
      if (!selectedConfigId.value && agentConfigs.value.length > 0) {
        selectedConfigId.value = agentConfigs.value[0].id
      }
    }
  } finally {
    configLoading.value = false
  }
}

// ========== 消息相关 ==========
const messages = ref<ChatMessage[]>([])
const inputContent = ref('')
const sending = ref(false)
const streaming = ref(false)
const streamingContent = ref('')
const streamingReasoning = ref('')
const reasoningExpanded = ref(true)
const loopCount = ref(0)
const messagesContainer = ref<HTMLElement | null>(null)
const scrollAnchor = ref<HTMLElement | null>(null)
let currentAbortController: AbortController | null = null

interface ActiveToolCall {
  toolName: string
  toolCallId: string
  result?: string
}
const activeToolCall = ref<ActiveToolCall | null>(null)

async function loadMessages(sessionId: string) {
  try {
    const res = await getMessages(sessionId)
    if (res.code === 200) {
      messages.value = res.data
      await nextTick()
      scrollToBottom()
    }
  } catch { /* 404 放行 */ }
}

function scrollToBottom() {
  nextTick(() => {
    scrollAnchor.value?.scrollIntoView({ behavior: 'smooth' })
  })
}

marked.setOptions({ breaks: true, gfm: true })

function renderMarkdown(text: string): string {
  if (!text) return ''
  try {
    return marked.parse(text) as string
  } catch {
    const A = String.fromCharCode(38)
    return text.replace(/&/g, A + 'amp;').replace(/</g, A + 'lt;').replace(/>/g, A + 'gt;')
  }
}

async function copyMessage(content: string) {
  try {
    await navigator.clipboard.writeText(content)
    ElMessage.success('已复制')
  } catch {
    const ta = document.createElement('textarea')
    ta.value = content
    ta.style.position = 'fixed'
    ta.style.opacity = '0'
    document.body.appendChild(ta)
    ta.select()
    document.execCommand('copy')
    document.body.removeChild(ta)
    ElMessage.success('已复制')
  }
}

function handleNewTask() {
  abortStream()
  streaming.value = false
  streamingContent.value = ''
  streamingReasoning.value = ''
  activeToolCall.value = null
  loopCount.value = 0
  currentSessionId.value = null
  messages.value = []
  inputContent.value = ''
  agentState.value = 'CHAT'
}

function handleSend() {
  const content = inputContent.value.trim()
  if (!content || !selectedConfigId.value || sending.value || streaming.value) return

  const userMsg: ChatMessage = {
    sessionId: currentSessionId.value || '',
    role: 'user',
    content,
    createTime: new Date().toISOString(),
  }
  messages.value.push(userMsg)
  inputContent.value = ''
  sending.value = true
  streaming.value = true
  streamingContent.value = ''
  streamingReasoning.value = ''
  activeToolCall.value = null
  loopCount.value = 0
  reasoningExpanded.value = true

  nextTick(() => scrollToBottom())

  currentAbortController = sendAgentMessage(
    currentSessionId.value,
    selectedConfigId.value,
    content,
    {
      onText: (delta) => {
        streamingContent.value += delta
        scrollToBottom()
      },
      onReasoning: (delta) => {
        streamingReasoning.value += delta
        scrollToBottom()
      },
      onToolStart: (toolName, toolCallId) => {
        activeToolCall.value = { toolName, toolCallId }
        scrollToBottom()
      },
      onToolEnd: (toolName, toolCallId, status, result) => {
        messages.value.push({
          sessionId: currentSessionId.value || '',
          role: 'tool',
          content: result || '',
          toolName,
          toolCallId,
          toolStatus: status,
          createTime: new Date().toISOString(),
        })
        activeToolCall.value = null
        scrollToBottom()
      },
      onFileChanged: (_path, _tool) => {
        debouncedRefreshTree()
      },
      onDone: (payload) => {
        if (!currentSessionId.value && payload.sessionId) {
          currentSessionId.value = payload.sessionId
        }
        if (streamingContent.value) {
          messages.value.push({
            id: payload.messageId,
            sessionId: payload.sessionId,
            role: 'assistant',
            content: streamingContent.value,
            createTime: new Date().toISOString(),
          })
        }
        streaming.value = false
        streamingContent.value = ''
        streamingReasoning.value = ''
        activeToolCall.value = null
        loopCount.value = 0
        sending.value = false
        currentAbortController = null
        loadAgentSessions()
        scrollToBottom()
      },
      onError: (error) => {
        ElMessage.error(error)
        streaming.value = false
        sending.value = false
        currentAbortController = null
        if (streamingContent.value) {
          messages.value.push({
            sessionId: currentSessionId.value || '',
            role: 'assistant',
            content: streamingContent.value + '\n\n> ⚠️ ' + error,
            createTime: new Date().toISOString(),
          })
          streamingContent.value = ''
        }
      },
    },
    null,
    selectedWorkspaceId.value
  )
}

function abortStream() {
  if (currentAbortController) {
    currentAbortController.abort()
    currentAbortController = null
  }
}

async function handleStop() {
  abortStream()
  if (currentSessionId.value) {
    try {
      await abortAgent(currentSessionId.value)
    } catch { /* 忽略 */ }
  }
  if (streamingContent.value) {
    messages.value.push({
      sessionId: currentSessionId.value || '',
      role: 'assistant',
      content: streamingContent.value + '\n\n> ⚠️ 已停止',
      createTime: new Date().toISOString(),
    })
    streamingContent.value = ''
    streamingReasoning.value = ''
  }
  activeToolCall.value = null
  streaming.value = false
  sending.value = false
  loadAgentSessions()
}

// ========== 生命周期 ==========
onMounted(() => {
  loadWorkspaces()
  loadConfigs()
})

onBeforeUnmount(() => {
  document.removeEventListener('mousemove', onDragMove)
  document.removeEventListener('mouseup', onDragEnd)
  document.body.style.cursor = ''
})

// 选择工作空间后自动刷新文件树与会话（首次加载也会触发），默认进入 LIST 状态
watch(selectedWorkspaceId, (newWsId) => {
  if (newWsId) {
    // 记住最近使用的工作空间
    localStorage.setItem(getLastWorkspaceKey(), newWsId)
    loadFileTree()
    loadAgentSessions()
    agentState.value = 'LIST'
  }
})
</script>

<style scoped>
/* ========== 左侧面板 ========== */
.tree-node {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px 12px;
  cursor: pointer;
  font-size: 13px;
  color: #475569;
  transition: background-color 0.15s;
  border-radius: 4px;
  margin: 0 4px;
  user-select: none;
}
.tree-node:hover {
  background-color: #f1f5f9;
}
.tree-arrow {
  width: 14px;
  font-size: 9px;
  color: #94a3b8;
  text-align: center;
  flex-shrink: 0;
}
.tree-icon {
  flex-shrink: 0;
}
.tree-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.tree-file {
  padding-left: 30px;
}
.tree-children {
  /* nested children */
}

/* ========== 文件树三点菜单 ========== */
.tree-more {
  margin-left: auto;
  opacity: 0;
  font-size: 15px;
  color: #94a3b8;
  cursor: pointer;
  padding: 0 4px;
  line-height: 1;
  font-weight: 700;
  transition: opacity 0.2s, color 0.2s;
  flex-shrink: 0;
}
.tree-node:hover .tree-more {
  opacity: 1;
}
.tree-more:hover {
  color: #3b82f6;
}

.tree-action-wrapper {
  position: relative;
  display: inline-flex;
  align-items: center;
  margin-left: auto;
  flex-shrink: 0;
}
.tree-dropdown {
  position: absolute;
  right: 0;
  top: 100%;
  margin-top: 2px;
  background: #fff;
  border: 1px solid #e8ecf1;
  border-radius: 10px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.12);
  z-index: 100;
  min-width: 130px;
  padding: 4px 0;
  white-space: nowrap;
}
.tree-dropdown .tree-action-item {
  padding: 8px 14px;
  font-size: 13px;
  color: #475569;
  cursor: pointer;
  transition: background-color 0.15s;
}
.tree-dropdown .tree-action-item:hover {
  background-color: #f1f5f9;
}
.tree-dropdown .tree-action-item.danger {
  color: #ef4444;
}
.tree-dropdown .tree-action-item.danger:hover {
  background-color: #fef2f2;
}

/* ========== 右侧 LIST 面板 ========== */
.session-list-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  background-color: #fff;
}
.list-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 24px;
  border-bottom: 1px solid #ebeef5;
  background: linear-gradient(180deg, #fafbfc 0%, #fff 100%);
  flex-shrink: 0;
}
.list-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
  margin: 0;
}
.list-body {
  flex: 1;
  overflow-y: auto;
  padding: 12px 20px;
}
.session-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px;
  cursor: pointer;
  border-radius: 10px;
  transition: all 0.18s ease;
  margin-bottom: 4px;
  border: 1px solid transparent;
}
.session-card:hover {
  background-color: #f8fafc;
  border-color: #e2e8f0;
}
.session-card.active {
  background-color: #eef2ff;
  border-color: rgba(99, 102, 241, 0.18);
}
.session-card-left {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
  flex: 1;
}
.session-card-info {
  display: flex;
  flex-direction: column;
  min-width: 0;
}
.session-card-title {
  font-size: 14px;
  font-weight: 500;
  color: #1e293b;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.session-card-meta {
  font-size: 12px;
  color: #94a3b8;
  margin-top: 2px;
}
.session-card .delete-icon {
  opacity: 0;
  color: #94a3b8;
  font-size: 16px;
  cursor: pointer;
  transition: all 0.2s;
  flex-shrink: 0;
}
.session-card:hover .delete-icon {
  opacity: 1;
}
.session-card .delete-icon:hover {
  color: #ef4444;
}

.empty-list {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 80px 20px;
  text-align: center;
}
.empty-list-icon {
  font-size: 56px;
  margin-bottom: 16px;
}
.empty-list-title {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 6px;
}
.empty-list-desc {
  font-size: 13px;
  color: #94a3b8;
}

/* ========== 右侧 CHAT 面板 ========== */
.chat-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  background-color: #fff;
}

.chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  border-bottom: 1px solid #ebeef5;
  background: linear-gradient(180deg, #fafbfc 0%, #fff 100%);
  min-height: 54px;
  flex-shrink: 0;
}
.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}
.back-btn {
  display: inline-flex;
  align-items: center;
  padding: 5px 12px;
  border: 1px solid #dcdfe6;
  border-radius: 8px;
  background: transparent;
  color: #606266;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.2s ease;
  white-space: nowrap;
  flex-shrink: 0;
}
.back-btn:hover {
  color: #409eff;
  border-color: #c6e2ff;
  background-color: #ecf5ff;
}
.model-select {
  width: 260px;
}
.model-select :deep(.el-input__wrapper) {
  background-color: #f5f6f8;
  border-radius: 10px;
  box-shadow: none;
}
.header-right {
  display: flex;
  align-items: center;
}
.current-session-badge {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #909399;
  background-color: #f5f6f8;
  padding: 5px 12px;
  border-radius: 20px;
}
.badge-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background-color: #67c23a;
  flex-shrink: 0;
}

/* 消息区域 */
.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
  background-color: #f5f7fa;
}
.empty-chat {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: #c0c4cc;
}
.empty-icon {
  font-size: 56px;
  margin-bottom: 16px;
}
.empty-text {
  font-size: 15px;
}

.message-row {
  display: flex;
  gap: 10px;
  margin-bottom: 16px;
  max-width: 85%;
}
.message-row.user {
  margin-left: auto;
  flex-direction: row-reverse;
}
.message-row.assistant {
  margin-right: auto;
}
.message-avatar {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  flex-shrink: 0;
  background-color: #fff;
  border: 1px solid #e4e7ed;
}
.message-bubble {
  padding: 10px 14px;
  border-radius: 10px;
  font-size: 14px;
  line-height: 1.7;
  word-break: break-word;
  background-color: #fff;
  border: 1px solid #e4e7ed;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.04);
}
.message-row.user .message-bubble {
  background-color: #409eff;
  color: #fff;
  border-color: #409eff;
}
.message-bubble.streaming {
  border-color: #409eff;
  box-shadow: 0 0 6px rgba(64, 158, 255, 0.2);
}
.message-body {
  flex: 1;
  min-width: 0;
}

/* 工具调用卡片 */
.tool-call-card {
  margin: 8px 0 16px 46px;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  overflow: hidden;
  background-color: #f8fafc;
}
.tool-call-card.streaming {
  border-color: #f59e0b;
  box-shadow: 0 0 6px rgba(245, 158, 11, 0.15);
}
.tool-call-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  background-color: #f1f5f9;
  font-size: 13px;
  font-weight: 500;
  color: #475569;
}
.tool-call-icon {
  font-size: 15px;
}
.tool-call-name {
  flex: 1;
}
.tool-call-result {
  padding: 8px 12px;
  max-height: 200px;
  overflow-y: auto;
}
.tool-call-result pre {
  margin: 0;
  font-size: 12px;
  line-height: 1.5;
  color: #334155;
  white-space: pre-wrap;
  word-break: break-all;
  font-family: 'Fira Code', 'Consolas', monospace;
}

/* 思考过程 */
.reasoning-block {
  margin: 6px 0 6px 46px;
  border: 1px solid #e8d5a0;
  border-radius: 8px;
  overflow: hidden;
}
.reasoning-header {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 10px;
  background-color: #fef9e7;
  cursor: pointer;
  font-size: 12px;
  color: #8b6914;
  user-select: none;
}
.reasoning-header:hover {
  background-color: #fef3c7;
}
.reasoning-icon {
  font-size: 10px;
  width: 14px;
  text-align: center;
}
.reasoning-content {
  padding: 8px 10px;
  background-color: #fffdf5;
  font-size: 13px;
  line-height: 1.6;
  color: #6b5a1e;
  border-top: 1px solid #f0e5b0;
  max-height: 240px;
  overflow-y: auto;
}

/* 消息操作按钮 */
.message-actions {
  display: flex;
  margin-top: 6px;
  opacity: 0;
  transition: opacity 0.2s ease;
}
.message-row:hover .message-actions {
  opacity: 1;
}
.copy-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 3px 10px;
  border: 1px solid #dcdfe6;
  border-radius: 6px;
  background: transparent;
  color: #909399;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.2s ease;
}
.copy-btn:hover {
  color: #409eff;
  border-color: #409eff;
}

/* 输入区 */
.chat-input-area {
  padding: 0;
  border-top: 1px solid #ebeef5;
  background-color: #fff;
  flex-shrink: 0;
}
.input-container {
  padding: 14px 20px;
}
.input-wrapper {
  margin-bottom: 10px;
}
.input-wrapper :deep(.el-textarea__inner) {
  border-radius: 14px;
  border: 2px solid #e4e7ed;
  padding: 12px 16px;
  font-size: 14px;
  line-height: 1.6;
  background-color: #fafbfc;
  transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);
  resize: none;
  min-height: 52px !important;
}
.input-wrapper :deep(.el-textarea__inner):hover {
  border-color: #c6e2ff;
  background-color: #fff;
}
.input-wrapper :deep(.el-textarea__inner):focus {
  border-color: #409eff;
  box-shadow: 0 0 0 4px rgba(64, 158, 255, 0.08);
  background-color: #fff;
}
.input-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.streaming-hint {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  font-weight: 500;
  color: #e6a23c;
}
.pulse-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background-color: #e6a23c;
  animation: pulse-dot 1.2s ease-in-out infinite;
}
@keyframes pulse-dot {
  0%, 100% { opacity: 0.3; transform: scale(0.8); }
  50% { opacity: 1; transform: scale(1.3); }
}
.toolbar-right {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-left: auto;
}
.send-icon {
  margin-left: 4px;
  font-size: 15px;
}
.streaming-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background-color: #f59e0b;
  animation: pulse-dot 1.2s ease-in-out infinite;
  flex-shrink: 0;
}

/* Markdown 渲染样式 */
.message-bubble :deep(pre) {
  background-color: #282c34;
  color: #abb2bf;
  padding: 12px 16px;
  border-radius: 6px;
  overflow-x: auto;
  font-size: 13px;
  line-height: 1.5;
  margin: 8px 0;
}
.message-bubble :deep(code) {
  font-family: 'Fira Code', 'Consolas', monospace;
  font-size: 13px;
}
.message-bubble :deep(p) { margin: 4px 0; }
.message-bubble :deep(ul), .message-bubble :deep(ol) { padding-left: 20px; margin: 4px 0; }
.message-bubble :deep(blockquote) {
  border-left: 3px solid #dcdfe6;
  padding-left: 12px;
  margin: 8px 0;
  color: #909399;
}
.message-row.user .message-bubble :deep(pre) { background-color: rgba(0, 0, 0, 0.2); }
.message-row.user .message-bubble :deep(blockquote) {
  border-left-color: rgba(255, 255, 255, 0.4);
  color: rgba(255, 255, 255, 0.8);
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


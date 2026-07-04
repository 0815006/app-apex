<template>
  <div class="flex h-full w-full bg-slate-50/60 font-sans overflow-hidden">
    <!-- ========== 左侧：会话列表 ========== -->
    <aside class="w-80 bg-white/75 backdrop-blur-md border-r border-slate-200/50 flex flex-col shadow-sm z-10">
      <!-- 顶部工具栏 -->
      <div class="p-4 flex justify-between items-center border-b border-slate-100">
        <div class="flex items-center gap-2">
          <span class="text-xl">💬</span>
          <span class="text-base font-bold tracking-tight text-slate-800">AI 对话</span>
        </div>
      </div>

      <!-- 新建对话按钮 -->
      <div class="px-3 pt-3">
        <el-button class="w-full !rounded-xl shadow-sm" @click="handleNewChat">
          <span class="inline-flex items-center gap-1.5">
            <el-icon><ChatDotRound /></el-icon>新建对话
          </span>
        </el-button>
      </div>

      <!-- 会话列表 -->
      <div class="flex-1 overflow-y-auto px-2 py-2" v-loading="sessionLoading">
        <el-empty
          v-if="sessions.length === 0 && !sessionLoading"
          description="暂无对话，点击上方开始新对话"
          :image-size="80"
          class="mt-8"
        >
          <template #image>
            <div class="text-3xl">💬</div>
          </template>
        </el-empty>

        <div v-else class="chat-session-list">
          <div
            v-for="session in sessions"
            :key="session.id"
            :class="['session-item', { active: currentSessionId === session.id }]"
            @click="handleSelectSession(session.id)"
          >
            <span class="flex items-center justify-between w-full group pr-1">
              <span class="flex items-center gap-2 text-sm truncate min-w-0">
                <el-icon class="text-indigo-400 shrink-0"><ChatDotRound /></el-icon>
                <span class="flex flex-col min-w-0">
                  <!-- 重命名内联模式 -->
                  <span v-if="renamingSessionId === session.id" class="session-rename-row" @click.stop>
                    <el-input
                      ref="renameInputRef"
                      v-model="renameTitle"
                      size="small"
                      maxlength="100"
                      @keyup.enter="handleRenameConfirm(session.id)"
                      @blur="handleRenameCancel"
                    />
                  </span>
                  <span v-else class="truncate text-slate-600 group-hover:text-indigo-600 transition-colors font-medium">
                    {{ session.title }}
                  </span>
                  <span class="flex items-center gap-2 text-[11px] text-slate-400 mt-0.5">
                    <span v-if="session.modelName" class="truncate max-w-[100px]">{{ session.modelName }}</span>
                    <span v-if="session.updateTime">{{ formatTime(session.updateTime) }}</span>
                  </span>
                </span>
              </span>
              <!-- hover 操作区域：下拉菜单 -->
              <span class="flex items-center gap-0.5 shrink-0">
                <el-dropdown trigger="click" @command="(cmd: string) => handleSessionCommand(cmd, session)">
                  <el-icon class="opacity-0 group-hover:opacity-100 text-slate-400 hover:text-slate-600 transition-opacity shrink-0 ml-1">
                    <MoreFilled />
                  </el-icon>
                  <template #dropdown>
                    <el-dropdown-menu>
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
            </span>
          </div>
        </div>
      </div>

      <!-- 底部：设置按钮 -->
      <div class="p-4 border-t border-slate-100 bg-slate-50/50">
        <el-button :icon="Setting" link class="settings-btn w-full" @click="configDialogVisible = true">
          大模型配置
        </el-button>
      </div>
    </aside>

    <!-- ========== 右侧：聊天主区域 ========== -->
    <section class="chat-main">
      <!-- 顶部：模型选择器 -->
      <header class="chat-header">
        <div class="header-left">
          <div class="model-select-wrapper">
            <span class="model-label">模型</span>
            <el-select
              v-model="selectedConfigId"
              placeholder="请选择大模型"
              class="model-select"
              @change="handleConfigChange"
              :loading="configLoading"
              size="default"
            >
              <el-option
                v-for="config in configOptions"
                :key="config.id"
                :label="`${config.configName} (${config.modelName})`"
                :value="config.id"
              />
            </el-select>
          </div>
          <span v-if="configOptions.length === 0 && !configLoading" class="no-config-hint">
            请先在左侧底部配置大模型
          </span>
        </div>
        <div class="header-right">
          <span v-if="currentSessionId" class="current-session-badge">
            <span class="badge-dot" />
            {{ sessions.find(s => s.id === currentSessionId)?.title || '对话中' }}
          </span>
        </div>
      </header>

      <!-- 中间：消息列表 -->
      <div class="chat-messages" ref="messagesContainer">
        <div v-if="messages.length === 0 && !streaming" class="empty-chat">
          <div class="empty-icon">💬</div>
          <div class="empty-text">选择一个模型，开始对话吧</div>
        </div>

        <div
          v-for="(msg, idx) in messages"
          :key="idx"
          :class="['message-row', msg.role]"
        >
          <div class="message-avatar">
            {{ msg.role === 'user' ? '👤' : '🤖' }}
          </div>
          <template v-if="msg.role === 'assistant'">
            <div class="message-body">
              <div class="message-bubble" v-html="renderMarkdown(msg.content)" />
              <div class="message-actions">
                <button class="copy-btn" @click="copyMessage(msg.content)" title="复制回答">
                  <el-icon><CopyDocument /></el-icon>
                  <span>复制</span>
                </button>
              </div>
            </div>
          </template>
          <div v-else class="message-bubble" v-html="renderMarkdown(msg.content)" />
        </div>

        <!-- 流式输出中的 AI 消息 -->
        <div v-if="streaming" class="message-row assistant">
          <div class="message-avatar">🤖</div>
          <div class="message-body">
            <div v-if="streamingReasoning" class="reasoning-block">
              <div class="reasoning-header" @click="reasoningExpanded = !reasoningExpanded">
                <span class="reasoning-icon">{{ reasoningExpanded ? '▼' : '▶' }}</span>
                <span>思考过程</span>
                <span v-if="reasoningExpanded" class="reasoning-hint">（可在配置中关闭展示）</span>
              </div>
              <div v-if="reasoningExpanded" class="reasoning-content" v-html="renderMarkdown(streamingReasoning)" />
            </div>
            <div class="message-bubble streaming" v-html="renderMarkdown(streamingContent || '▊')" />
          </div>
        </div>

        <!-- 滚动锚点 -->
        <div ref="scrollAnchor" />
      </div>

      <!-- 底部：输入框 -->
      <footer class="chat-input-area">
        <div v-if="selectedSkillId" class="skill-active-bar">
          <div class="skill-active-info">
            <span class="skill-active-icon">⚡</span>
            <span class="skill-active-label">已激活 Skill</span>
            <el-tag type="success" size="small" effect="dark" closable @close="handleClearSkill">
              {{ selectedSkillName }}
            </el-tag>
          </div>
          <span class="skill-active-desc">
            {{ enabledSkills.find(s => s.id === selectedSkillId)?.description || '' }}
          </span>
        </div>

        <div class="input-container">
          <div class="input-wrapper">
            <el-input
              v-model="inputContent"
              type="textarea"
              :rows="2"
              :placeholder="selectedSkillId ? `以「${selectedSkillName}」身份发送消息…` : '输入消息，Enter 发送，Shift+Enter 换行'"
              resize="none"
              :disabled="!selectedConfigId || sending"
              @keydown.enter.exact="handleSend"
              class="input-textarea"
            />
          </div>

          <div class="input-toolbar">
            <div class="toolbar-left">
              <el-popover
                placement="top-start"
                :width="280"
                trigger="click"
                :visible="skillPopoverVisible"
                @show="loadSkills"
              >
                <template #reference>
                  <el-button
                    :icon="Plus"
                    size="small"
                    class="skill-trigger-btn"
                    :class="{ 'skill-active': !!selectedSkillId }"
                    @click="skillPopoverVisible = !skillPopoverVisible"
                  >
                    {{ selectedSkillId ? selectedSkillName : '选择 Skill' }}
                  </el-button>
                </template>
                <div class="skill-popover-content">
                  <div class="skill-popover-title">选择预置 Skill</div>
                  <div v-if="skillLoading" class="skill-loading">
                    <el-icon class="is-loading"><Loading /></el-icon>
                    <span>加载中...</span>
                  </div>
                  <div v-else-if="enabledSkills.length === 0" class="skill-empty">
                    <div class="skill-empty-icon">📦</div>
                    <div>暂无可用 Skill</div>
                    <router-link to="/skills" class="skill-empty-link">前往创建 →</router-link>
                  </div>
                  <div
                    v-for="skill in enabledSkills"
                    :key="skill.id"
                    :class="['skill-option', { selected: selectedSkillId === skill.id }]"
                    @click="handleSelectSkill(skill)"
                  >
                    <div class="skill-option-head">
                      <span class="skill-option-name">{{ skill.name }}</span>
                      <el-icon v-if="selectedSkillId === skill.id" class="skill-check" color="#67c23a"><Check /></el-icon>
                    </div>
                    <div class="skill-option-desc">{{ skill.description || '暂无简介' }}</div>
                  </div>
                  <div v-if="selectedSkillId" class="skill-clear-row">
                    <el-button link size="small" @click="handleClearSkill">
                      移除 Skill
                    </el-button>
                  </div>
                </div>
              </el-popover>
            </div>

            <div class="toolbar-right">
              <span v-if="streaming" class="streaming-hint">
                <span class="streaming-dot" />
                AI 思考中
              </span>
              <el-button
                v-if="streaming"
                type="warning"
                :icon="VideoPause"
                @click="handleStop"
                round
                class="stop-btn"
              >
                停止生成
              </el-button>
              <el-button
                v-else
                type="primary"
                :disabled="!inputContent.trim() || !selectedConfigId"
                :loading="sending"
                @click="handleSend"
                round
                class="send-btn"
              >
                <template v-if="!sending">
                  <span>发送</span>
                  <el-icon class="send-icon"><Promotion /></el-icon>
                </template>
              </el-button>
            </div>
          </div>
        </div>
      </footer>
    </section>

    <!-- LLM 配置弹窗 -->
    <LlmConfigDialog v-model="configDialogVisible" />
  </div>
</template>

<script setup lang="ts">
import { ref, nextTick, watch, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Plus,
  Delete,
  Setting,
  Check,
  Loading,
  VideoPause,
  Promotion,
  ChatDotRound,
  MoreFilled,
  Edit,
  CopyDocument,
} from '@element-plus/icons-vue'
import { marked } from 'marked'
import {
  listSessions,
  getMessages,
  deleteSession,
  sendChatMessage,
  updateSessionTitle,
} from '@/api/chat'
import { listEnabledSkills } from '@/api/skill'
import type { ChatSessionVO, ChatMessage, LlmConfigVO } from '@/types/chat'
import type { SkillVO } from '@/types/skill'
import LlmConfigDialog from '@/components/chat/LlmConfigDialog.vue'
import { listLlmConfigs } from '@/api/chat'

// ========== 会话列表 ==========
const sessions = ref<ChatSessionVO[]>([])
const sessionLoading = ref(false)
const currentSessionId = ref<string | null>(null)
const renamingSessionId = ref<string | null>(null)
const renameTitle = ref('')
const renameInputRef = ref<InstanceType<typeof import('element-plus').ElInput> | null>(null)

async function loadSessions() {
  sessionLoading.value = true
  try {
    const res = await listSessions()
    if (res.code === 200) {
      sessions.value = res.data
    }
  } finally {
    sessionLoading.value = false
  }
}

function handleSelectSession(sessionId: string) {
  if (currentSessionId.value === sessionId) return
  currentSessionId.value = sessionId
  loadMessages(sessionId)
}

function handleNewChat() {
  abortStream()
  streaming.value = false
  streamingReasoning.value = ''
  currentSessionId.value = null
  messages.value = []
  inputContent.value = ''
  handleClearSkill()
}

// ========== 会话右键菜单 ==========
function handleSessionCommand(cmd: string, session: ChatSessionVO) {
  switch (cmd) {
    case 'rename':
      handleStartRename(session)
      break
    case 'delete':
      handleDeleteSession(session)
      break
  }
}

async function handleDeleteSession(session: ChatSessionVO) {
  try {
    await ElMessageBox.confirm(
      `确定要删除对话「${session.title}」吗？此操作不可恢复。`,
      '删除对话',
      {
        confirmButtonText: '确认删除',
        cancelButtonText: '取消',
        type: 'warning',
        confirmButtonClass: 'el-button--danger',
      }
    )

    const res = await deleteSession(session.id)
    if (res.code !== 200) return
    ElMessage.success('已删除')
    if (currentSessionId.value === session.id) {
      handleNewChat()
    }
    await loadSessions()
  } catch (err: unknown) {
    if (err !== 'cancel' && err !== 'close') {
      // 拦截器已处理
    }
  }
}

// ========== 会话重命名（内联编辑） ==========
function handleStartRename(session: ChatSessionVO) {
  renamingSessionId.value = session.id
  renameTitle.value = session.title
  nextTick(() => {
    renameInputRef.value?.focus()
  })
}

async function handleRenameConfirm(sessionId: string) {
  const title = renameTitle.value.trim()
  if (!title) {
    handleRenameCancel()
    return
  }
  try {
    const res = await updateSessionTitle(sessionId, title)
    if (res.code === 200) {
      ElMessage.success('已重命名')
      renamingSessionId.value = null
      renameTitle.value = ''
      await loadSessions()
    }
  } catch {
    // 拦截器已处理
  }
}

function handleRenameCancel() {
  renamingSessionId.value = null
  renameTitle.value = ''
}

// ========== 模型选择 ==========
const configOptions = ref<LlmConfigVO[]>([])
const configLoading = ref(false)
const selectedConfigId = ref<string | null>(null)

async function loadConfigs() {
  configLoading.value = true
  try {
    const res = await listLlmConfigs()
    if (res.code === 200) {
      configOptions.value = res.data
      if (!selectedConfigId.value && configOptions.value.length > 0) {
        selectedConfigId.value = configOptions.value[0].id
      }
    }
  } finally {
    configLoading.value = false
  }
}

function handleConfigChange() {
  if (messages.value.length > 0) {
    ElMessage.info('已切换模型，后续消息将使用新模型回复')
  }
}

// ========== Skill 选择 ==========
const enabledSkills = ref<SkillVO[]>([])
const skillLoading = ref(false)
const selectedSkillId = ref<string | null>(null)
const selectedSkillName = ref<string>('')
const skillPopoverVisible = ref(false)

async function loadSkills() {
  if (enabledSkills.value.length > 0 && !skillLoading.value) return
  skillLoading.value = true
  try {
    const res = await listEnabledSkills()
    if (res.code === 200) {
      enabledSkills.value = res.data
    }
  } finally {
    skillLoading.value = false
  }
}

function handleSelectSkill(skill: SkillVO) {
  selectedSkillId.value = skill.id
  selectedSkillName.value = skill.name
  skillPopoverVisible.value = false
  ElMessage.success(`已选择 Skill：${skill.name}`)
}

function handleClearSkill() {
  selectedSkillId.value = null
  selectedSkillName.value = ''
}

// ========== 消息相关 ==========
const messages = ref<Array<ChatMessage & { _local?: boolean }>>([])
const inputContent = ref('')
const sending = ref(false)
const streaming = ref(false)
const streamingContent = ref('')
const streamingReasoning = ref('')
const reasoningExpanded = ref(true)
const messagesContainer = ref<HTMLElement | null>(null)
const scrollAnchor = ref<HTMLElement | null>(null)
let currentAbortController: AbortController | null = null

async function loadMessages(sessionId: string) {
  try {
    const res = await getMessages(sessionId)
    if (res.code === 200) {
      messages.value = res.data
      await nextTick()
      scrollToBottom()
    }
  } catch {
    // 404 等由拦截器放行
  }
}

function scrollToBottom() {
  nextTick(() => {
    scrollAnchor.value?.scrollIntoView({ behavior: 'smooth' })
  })
}

marked.setOptions({
  breaks: true,
  gfm: true,
})

function renderMarkdown(text: string): string {
  if (!text) return ''
  try {
    return marked.parse(text) as string
  } catch {
    return text.replace(/</g, '<').replace(/>/g, '>')
  }
}

async function copyMessage(content: string) {
  try {
    await navigator.clipboard.writeText(content)
    ElMessage.success('已复制到剪贴板')
  } catch {
    // 降级方案
    const textarea = document.createElement('textarea')
    textarea.value = content
    textarea.style.position = 'fixed'
    textarea.style.opacity = '0'
    document.body.appendChild(textarea)
    textarea.select()
    try {
      document.execCommand('copy')
      ElMessage.success('已复制到剪贴板')
    } catch {
      ElMessage.error('复制失败')
    }
    document.body.removeChild(textarea)
  }
}

async function handleSend() {
  const content = inputContent.value.trim()
  if (!content || !selectedConfigId.value || sending.value || streaming.value) return

  if (!selectedConfigId.value) {
    ElMessage.warning('请先选择大模型')
    return
  }

  const userMsg: ChatMessage & { _local?: boolean } = {
    id: '',
    sessionId: currentSessionId.value || '',
    role: 'user',
    content,
    createTime: new Date().toISOString(),
    _local: true,
  }
  messages.value.push(userMsg)
  inputContent.value = ''
  sending.value = true
  streaming.value = true
  streamingContent.value = ''
  streamingReasoning.value = ''
  reasoningExpanded.value = true

  await nextTick()
  scrollToBottom()

  currentAbortController = sendChatMessage(
    currentSessionId.value,
    selectedConfigId.value,
    content,
    (chunk: string) => {
      streamingContent.value += chunk
      scrollToBottom()
    },
    (data: { sessionId: string; messageId: string }) => {
      if (!currentSessionId.value && data.sessionId) {
        currentSessionId.value = data.sessionId
      }
      messages.value.push({
        id: data.messageId,
        sessionId: data.sessionId,
        role: 'assistant',
        content: streamingContent.value,
        createTime: new Date().toISOString(),
      })
      streaming.value = false
      streamingContent.value = ''
      streamingReasoning.value = ''
      sending.value = false
      currentAbortController = null
      loadSessions()
      scrollToBottom()
    },
    (error: string) => {
      ElMessage.error(error)
      streaming.value = false
      sending.value = false
      currentAbortController = null
      if (streamingContent.value) {
        messages.value.push({
          id: '',
          sessionId: currentSessionId.value || '',
          role: 'assistant',
          content: streamingContent.value + '\n\n> ⚠️ 生成中断',
          createTime: new Date().toISOString(),
        })
        streamingContent.value = ''
        streamingReasoning.value = ''
      }
    },
    (chunk: string) => {
      streamingReasoning.value += chunk
      scrollToBottom()
    },
    selectedSkillId.value
  )
}

function abortStream() {
  if (currentAbortController) {
    currentAbortController.abort()
    currentAbortController = null
  }
}

function handleStop() {
  abortStream()
  if (streamingContent.value) {
    messages.value.push({
      id: '',
      sessionId: currentSessionId.value || '',
      role: 'assistant',
      content: streamingContent.value + '\n\n> ⚠️ 已停止生成',
      createTime: new Date().toISOString(),
    })
    streamingContent.value = ''
    streamingReasoning.value = ''
  }
  streaming.value = false
  sending.value = false
}

// ========== 时间格式化 ==========
function formatTime(dateStr: string): string {
  if (!dateStr) return ''
  const date = new Date(dateStr)
  const now = new Date()
  const today = new Date(now.getFullYear(), now.getMonth(), now.getDate())
  const yesterday = new Date(today.getTime() - 86400000)
  const target = new Date(date.getFullYear(), date.getMonth(), date.getDate())

  const hh = String(date.getHours()).padStart(2, '0')
  const mm = String(date.getMinutes()).padStart(2, '0')

  if (target.getTime() === today.getTime()) {
    return `${hh}:${mm}`
  } else if (target.getTime() === yesterday.getTime()) {
    return `昨天 ${hh}:${mm}`
  }
  const y = date.getFullYear()
  const M = String(date.getMonth() + 1).padStart(2, '0')
  const d = String(date.getDate()).padStart(2, '0')
  return `${y}-${M}-${d}`
}

// ========== 配置弹窗 ==========
const configDialogVisible = ref(false)

watch(configDialogVisible, (v) => {
  if (!v) loadConfigs()
})

// ========== 生命周期 ==========
onMounted(() => {
  loadSessions()
  loadConfigs()
})
</script>

<style scoped>
/* ================================================================
   全局布局
   ================================================================ */
/* 左侧边栏定制 — 对齐 Wiki 风格 */
.chat-session-list {
  display: flex;
  flex-direction: column;
  gap: 1px;
}

.session-item {
  display: flex;
  align-items: center;
  padding: 8px 10px;
  cursor: pointer;
  border-radius: 8px;
  transition: all 0.18s ease;
  min-height: 36px;
}

.session-item:hover {
  background-color: rgb(248 250 252);
}

.session-item.active {
  background-color: rgb(238 242 255);
  box-shadow: inset 0 0 0 1px rgba(99, 102, 241, 0.12);
}

.session-item.active :deep(.el-icon) {
  color: rgb(79 70 229);
}

/* 重命名输入行 */
.session-rename-row {
  width: 100%;
}

.session-rename-row :deep(.el-input__wrapper) {
  background-color: #fff;
  box-shadow: 0 0 0 1px #409eff inset;
}

/* 设置按钮 */
.settings-btn {
  font-size: 13px;
  color: #909399;
  transition: color 0.2s;
}

.settings-btn:hover {
  color: #409eff;
}

/* ================================================================
   右侧聊天主区域
   ================================================================ */
.chat-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  background-color: #fff;
}

/* ---------- 顶部模型选择器 ---------- */
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

.model-select-wrapper {
  display: flex;
  align-items: center;
  gap: 10px;
  background-color: #f5f6f8;
  border-radius: 10px;
  padding: 4px 6px 4px 14px;
  transition: box-shadow 0.2s;
}

.model-select-wrapper:hover {
  box-shadow: 0 0 0 2px rgba(64, 158, 255, 0.12);
}

.model-label {
  font-size: 12px;
  font-weight: 600;
  color: #909399;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  white-space: nowrap;
}

.model-select {
  width: 260px;
}

.model-select :deep(.el-input__wrapper) {
  background-color: transparent;
  box-shadow: none;
  border-radius: 8px;
  padding: 2px 8px;
}

.model-select :deep(.el-input__wrapper:hover) {
  box-shadow: none;
  background-color: rgba(0, 0, 0, 0.02);
}

.model-select :deep(.el-input__inner) {
  font-weight: 500;
  color: #303133;
}

.no-config-hint {
  font-size: 12px;
  color: #c0c4cc;
  font-style: italic;
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
  max-width: 200px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.badge-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background-color: #67c23a;
  flex-shrink: 0;
}

/* ---------- 消息列表 ---------- */
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
  margin-bottom: 20px;
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

/* 思考过程折叠块 */
.reasoning-block {
  margin-bottom: 6px;
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
  transition: background-color 0.15s;
}

.reasoning-header:hover {
  background-color: #fef3c7;
}

.reasoning-icon {
  font-size: 10px;
  width: 14px;
  text-align: center;
}

.reasoning-hint {
  font-size: 11px;
  color: #b0a06a;
  margin-left: auto;
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

.reasoning-content :deep(p) {
  margin: 2px 0;
}

.reasoning-content :deep(code) {
  background-color: #f0e5b0;
  padding: 1px 4px;
  border-radius: 3px;
  font-size: 12px;
}

/* 消息操作按钮（复制） */
.message-actions {
  display: flex;
  justify-content: flex-start;
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
  line-height: 1.4;
}

.copy-btn:hover {
  color: #409eff;
  border-color: #409eff;
  background: rgba(64, 158, 255, 0.04);
}

.copy-btn:active {
  color: #337ecc;
  border-color: #337ecc;
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

.message-bubble :deep(p) {
  margin: 4px 0;
}

.message-bubble :deep(ul),
.message-bubble :deep(ol) {
  padding-left: 20px;
  margin: 4px 0;
}

.message-bubble :deep(blockquote) {
  border-left: 3px solid #dcdfe6;
  padding-left: 12px;
  margin: 8px 0;
  color: #909399;
}

.message-bubble :deep(table) {
  border-collapse: collapse;
  margin: 8px 0;
  font-size: 13px;
}

.message-bubble :deep(th),
.message-bubble :deep(td) {
  border: 1px solid #dcdfe6;
  padding: 6px 10px;
}

.message-bubble :deep(th) {
  background-color: #f5f7fa;
}

.message-row.user .message-bubble :deep(pre) {
  background-color: rgba(0, 0, 0, 0.2);
}

.message-row.user .message-bubble :deep(blockquote) {
  border-left-color: rgba(255, 255, 255, 0.4);
  color: rgba(255, 255, 255, 0.8);
}

/* ================================================================
   底部输入区
   ================================================================ */
.chat-input-area {
  padding: 0;
  border-top: 1px solid #ebeef5;
  background-color: #fff;
  flex-shrink: 0;
}

.skill-active-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 20px;
  background: linear-gradient(135deg, rgba(103, 194, 58, 0.06) 0%, rgba(103, 194, 58, 0.02) 100%);
  border-bottom: 1px solid rgba(103, 194, 58, 0.12);
  font-size: 12px;
}

.skill-active-info {
  display: flex;
  align-items: center;
  gap: 8px;
}

.skill-active-icon {
  font-size: 14px;
}

.skill-active-label {
  color: #909399;
  font-weight: 500;
}

.skill-active-desc {
  color: #b0b4bd;
  max-width: 400px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.input-container {
  padding: 14px 20px;
}

.input-wrapper {
  margin-bottom: 10px;
}

.input-textarea :deep(.el-textarea__inner) {
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

.input-textarea :deep(.el-textarea__inner):hover {
  border-color: #c6e2ff;
  background-color: #fff;
}

.input-textarea :deep(.el-textarea__inner):focus {
  border-color: #409eff;
  box-shadow: 0 0 0 4px rgba(64, 158, 255, 0.08);
  background-color: #fff;
}

.input-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.toolbar-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.skill-trigger-btn {
  border-radius: 20px;
  font-size: 12px;
  font-weight: 500;
  padding: 6px 14px;
  color: #909399;
  border-color: #e4e7ed;
  background-color: #fafbfc;
  transition: all 0.22s ease;
}

.skill-trigger-btn:hover {
  color: #409eff;
  border-color: #c6e2ff;
  background-color: #ecf5ff;
}

.skill-trigger-btn.skill-active {
  color: #67c23a;
  border-color: #b3e19d;
  background-color: rgba(103, 194, 58, 0.06);
  font-weight: 600;
}

.skill-trigger-btn.skill-active:hover {
  color: #5daf34;
  border-color: #95d475;
  background-color: rgba(103, 194, 58, 0.1);
}

.skill-popover-content {
  max-height: 360px;
  overflow-y: auto;
}

.skill-popover-title {
  font-size: 13px;
  font-weight: 600;
  color: #303133;
  padding: 0 4px 10px 4px;
  border-bottom: 1px solid #ebeef5;
  margin-bottom: 6px;
}

.skill-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 24px;
  color: #909399;
  font-size: 13px;
}

.skill-empty {
  text-align: center;
  padding: 20px;
  color: #909399;
  font-size: 13px;
}

.skill-empty-icon {
  font-size: 32px;
  margin-bottom: 8px;
}

.skill-empty-link {
  display: inline-block;
  margin-top: 8px;
  color: #409eff;
  font-weight: 500;
  text-decoration: none;
}

.skill-empty-link:hover {
  text-decoration: underline;
}

.skill-option {
  padding: 10px 10px;
  border-radius: 10px;
  cursor: pointer;
  transition: all 0.18s ease;
  margin-bottom: 2px;
}

.skill-option:hover {
  background-color: #f5f6f8;
}

.skill-option.selected {
  background-color: rgba(103, 194, 58, 0.06);
  box-shadow: inset 0 0 0 1px rgba(103, 194, 58, 0.2);
}

.skill-option-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.skill-option-name {
  font-size: 13px;
  font-weight: 600;
  color: #303133;
}

.skill-option.selected .skill-option-name {
  color: #67c23a;
}

.skill-check {
  font-size: 16px;
}

.skill-option-desc {
  font-size: 12px;
  color: #909399;
  margin-top: 3px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.skill-clear-row {
  text-align: center;
  padding-top: 10px;
  border-top: 1px solid #ebeef5;
  margin-top: 6px;
}

.toolbar-right {
  display: flex;
  align-items: center;
  gap: 10px;
}

.streaming-hint {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  font-weight: 500;
  color: #e6a23c;
}

.streaming-dot {
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

.send-btn {
  height: 36px;
  padding: 0 20px;
  font-weight: 600;
  letter-spacing: 0.5px;
  box-shadow: 0 2px 10px rgba(64, 158, 255, 0.3);
  transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);
  border-radius: 20px;
}

.send-btn:hover:not(:disabled) {
  box-shadow: 0 4px 18px rgba(64, 158, 255, 0.45);
  transform: translateY(-1px);
}

.send-btn:active:not(:disabled) {
  transform: translateY(0);
  box-shadow: 0 1px 6px rgba(64, 158, 255, 0.3);
}

.send-icon {
  margin-left: 4px;
  font-size: 15px;
}

.stop-btn {
  height: 36px;
  padding: 0 20px;
  font-weight: 600;
  border-radius: 20px;
  box-shadow: 0 2px 10px rgba(230, 162, 60, 0.3);
  transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);
}

.stop-btn:hover {
  box-shadow: 0 4px 18px rgba(230, 162, 60, 0.45);
  transform: translateY(-1px);
}
</style>

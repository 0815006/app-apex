<template>
  <div class="chat-view">
    <!-- ========== 左侧：会话列表 ========== -->
    <aside class="chat-sidebar">
      <!-- 新建对话按钮 -->
      <div class="sidebar-header">
        <el-button type="primary" class="new-chat-btn" @click="handleNewChat" :icon="Plus">
          新建对话
        </el-button>
      </div>

      <!-- 会话列表 -->
      <div class="session-list" v-loading="sessionLoading">
        <div
          v-for="session in sessions"
          :key="session.id"
          :class="['session-item', { active: currentSessionId === session.id }]"
          @click="handleSelectSession(session.id)"
        >
          <div class="session-content">
            <div class="session-title">{{ session.title }}</div>
            <div class="session-meta">
              <span class="session-model">{{ session.modelName }}</span>
              <span class="session-time">{{ formatTime(session.updateTime) }}</span>
            </div>
          </div>
          <el-popconfirm
            title="确定删除该会话？"
            confirm-button-text="删除"
            cancel-button-text="取消"
            @confirm.stop="handleDeleteSession(session.id)"
          >
            <template #reference>
              <el-button
                class="session-delete-btn"
                :icon="Delete"
                link
                size="small"
                @click.stop
              />
            </template>
          </el-popconfirm>
        </div>
        <div v-if="sessions.length === 0 && !sessionLoading" class="empty-sessions">
          暂无对话，开始一个新对话吧
        </div>
      </div>

      <!-- 底部：设置按钮 -->
      <div class="sidebar-footer">
        <el-button :icon="Setting" link class="settings-btn" @click="configDialogVisible = true">
          大模型配置
        </el-button>
      </div>
    </aside>

    <!-- ========== 右侧：聊天主区域 ========== -->
    <section class="chat-main">
      <!-- 顶部：模型选择器 -->
      <header class="chat-header">
        <el-select
          v-model="selectedConfigId"
          placeholder="请选择大模型"
          class="model-select"
          @change="handleConfigChange"
          :loading="configLoading"
        >
          <el-option
            v-for="config in configOptions"
            :key="config.id"
            :label="`${config.configName} (${config.modelName})`"
            :value="config.id"
          />
        </el-select>
        <span v-if="configOptions.length === 0 && !configLoading" class="no-config-hint">
          请先在左侧底部配置大模型
        </span>
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
          <div class="message-bubble" v-html="renderMarkdown(msg.content)" />
        </div>

        <!-- 流式输出中的 AI 消息 -->
        <div v-if="streaming" class="message-row assistant">
          <div class="message-avatar">🤖</div>
          <div class="message-body">
            <!-- 可折叠的思考过程 -->
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
        <el-input
          v-model="inputContent"
          type="textarea"
          :rows="3"
          placeholder="输入消息，Enter 发送，Shift+Enter 换行"
          resize="none"
          :disabled="!selectedConfigId || sending"
          @keydown.enter.exact="handleSend"
        />
        <div class="input-actions">
          <span v-if="streaming" class="streaming-hint">AI 正在思考中…</span>
          <el-button
            v-if="streaming"
            type="warning"
            @click="handleStop"
            size="small"
          >
            停止生成
          </el-button>
          <el-button
            v-else
            type="primary"
            :disabled="!inputContent.trim() || !selectedConfigId"
            :loading="sending"
            @click="handleSend"
          >
            发送
          </el-button>
        </div>
      </footer>
    </section>

    <!-- LLM 配置弹窗 -->
    <LlmConfigDialog v-model="configDialogVisible" />
  </div>
</template>

<script setup lang="ts">
import { ref, nextTick, watch, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Delete, Setting } from '@element-plus/icons-vue'
import { marked } from 'marked'
import {
  listSessions,
  getMessages,
  deleteSession,
  sendChatMessage,
} from '@/api/chat'
import type { ChatSessionVO, ChatMessage, LlmConfigVO } from '@/types/chat'
import LlmConfigDialog from '@/components/chat/LlmConfigDialog.vue'
import { listLlmConfigs } from '@/api/chat'

// ========== 会话列表 ==========
const sessions = ref<ChatSessionVO[]>([])
const sessionLoading = ref(false)
const currentSessionId = ref<string | null>(null)

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
  // 从会话中获取绑定的配置
  const session = sessions.value.find(s => s.id === sessionId)
  if (session) {
    // 查找对应 configId
    const match = configOptions.value.find(c =>
      c.configName === session.configName && c.modelName === session.modelName
    )
    // 无法精确匹配 configId，先保留当前选择
  }
}

function handleNewChat() {
  // 停止当前流式
  abortStream()
  streaming.value = false
  streamingReasoning.value = ''
  currentSessionId.value = null
  messages.value = []
  inputContent.value = ''
}

async function handleDeleteSession(sessionId: string) {
  try {
    const res = await deleteSession(sessionId)
    if (res.code !== 200) return
    ElMessage.success('已删除')
    if (currentSessionId.value === sessionId) {
      handleNewChat()
    }
    await loadSessions()
  } catch {
    // 拦截器已处理
  }
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
  // 模型切换时提示
  if (messages.value.length > 0) {
    ElMessage.info('已切换模型，后续消息将使用新模型回复')
  }
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

// Markdown 渲染
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

// 发送消息
async function handleSend() {
  const content = inputContent.value.trim()
  if (!content || !selectedConfigId.value || sending.value || streaming.value) return

  // 有效性校验
  if (!selectedConfigId.value) {
    ElMessage.warning('请先选择大模型')
    return
  }

  // 添加用户消息到界面
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

  // 发起 SSE 请求
  currentAbortController = sendChatMessage(
    currentSessionId.value,
    selectedConfigId.value,
    content,
    // onMessage
    (chunk: string) => {
      streamingContent.value += chunk
      scrollToBottom()
    },
    // onDone
    (data: { sessionId: string; messageId: string }) => {
      // 如果是新会话，更新 currentSessionId
      if (!currentSessionId.value && data.sessionId) {
        currentSessionId.value = data.sessionId
      }
      // 将流式内容落库为正式消息
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
      // 刷新会话列表（新会话会置顶）
      loadSessions()
      scrollToBottom()
    },
    // onError
    (error: string) => {
      ElMessage.error(error)
      streaming.value = false
      sending.value = false
      currentAbortController = null
      // 保留已流式输出的部分
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
    // onReasoning
    (chunk: string) => {
      streamingReasoning.value += chunk
      scrollToBottom()
    }
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

// 监听配置弹窗关闭后刷新选项
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
.chat-view {
  display: flex;
  height: 100%;
  background-color: #fff;
}

/* ===== 左侧会话列表 ===== */
.chat-sidebar {
  width: 280px;
  min-width: 280px;
  display: flex;
  flex-direction: column;
  border-right: 1px solid #e4e7ed;
  background-color: #fafafa;
}

.sidebar-header {
  padding: 12px;
  border-bottom: 1px solid #e4e7ed;
}

.new-chat-btn {
  width: 100%;
}

.session-list {
  flex: 1;
  overflow-y: auto;
  padding: 4px 0;
}

.session-item {
  display: flex;
  align-items: center;
  padding: 10px 12px;
  cursor: pointer;
  transition: background-color 0.15s;
  border-left: 3px solid transparent;
}

.session-item:hover {
  background-color: #f0f2f5;
}

.session-item.active {
  background-color: #e6f4ff;
  border-left-color: #409eff;
}

.session-content {
  flex: 1;
  min-width: 0;
}

.session-title {
  font-size: 14px;
  font-weight: 500;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  margin-bottom: 4px;
}

.session-meta {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: #909399;
}

.session-model {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 100px;
}

.session-delete-btn {
  opacity: 0;
  transition: opacity 0.15s;
  flex-shrink: 0;
  margin-left: 4px;
}

.session-item:hover .session-delete-btn {
  opacity: 1;
}

.empty-sessions {
  text-align: center;
  color: #909399;
  padding: 40px 16px;
  font-size: 13px;
}

.sidebar-footer {
  padding: 10px 12px;
  border-top: 1px solid #e4e7ed;
  text-align: center;
}

.settings-btn {
  font-size: 13px;
  color: #606266;
}

/* ===== 右侧聊天主区域 ===== */
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
  padding: 10px 20px;
  border-bottom: 1px solid #e4e7ed;
  background-color: #fafafa;
  min-height: 52px;
}

.model-select {
  width: 300px;
}

.no-config-hint {
  font-size: 13px;
  color: #909399;
  margin-left: 12px;
}

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

/* 消息体（含思考过程 + 气泡） */
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

/* ===== 底部输入区 ===== */
.chat-input-area {
  padding: 12px 20px;
  border-top: 1px solid #e4e7ed;
  background-color: #fff;
}

.chat-input-area :deep(.el-textarea__inner) {
  border-radius: 8px;
}

.input-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 8px;
}

.streaming-hint {
  font-size: 12px;
  color: #e6a23c;
  animation: pulse 1.5s ease-in-out infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.4; }
}
</style>

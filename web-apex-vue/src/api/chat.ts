import request from '@/utils/request'
import type { LlmConfigVO, LlmConfig, ChatSessionVO, ChatMessage, AgentSSEEvent, AgentDonePayload, AgentCallbacks } from '@/types/chat'

/**
 * LLM 配置 API
 */

/** 获取当前用户的所有配置 */
export function listLlmConfigs(): Promise<{ code: number; message: string; data: LlmConfigVO[] }> {
  return request.get('/llm-config')
}

/** 获取单个配置详情 */
export function getLlmConfig(id: string): Promise<{ code: number; message: string; data: LlmConfig }> {
  return request.get(`/llm-config/${id}`)
}

/** 新增配置 */
export function createLlmConfig(config: Omit<LlmConfig, 'id' | 'userId' | 'createTime' | 'updateTime'>): Promise<{ code: number; message: string; data: LlmConfig }> {
  return request.post('/llm-config', config)
}

/** 更新配置 */
export function updateLlmConfig(id: string, config: Omit<LlmConfig, 'id' | 'userId' | 'createTime' | 'updateTime'>): Promise<{ code: number; message: string; data: LlmConfig }> {
  return request.put(`/llm-config/${id}`, config)
}

/** 删除配置 */
export function deleteLlmConfig(id: string): Promise<{ code: number; message: string; data: null }> {
  return request.delete(`/llm-config/${id}`)
}

/**
 * 聊天 API
 */

/** 获取会话列表（可按 mode 过滤） */
export function listSessions(mode?: string): Promise<{ code: number; message: string; data: ChatSessionVO[] }> {
  const params = mode ? { mode } : {}
  return request.get('/chat/sessions', { params })
}

/** 获取会话消息历史 */
export function getMessages(sessionId: string): Promise<{ code: number; message: string; data: ChatMessage[] }> {
  return request.get(`/chat/messages/${sessionId}`)
}

/** 删除会话 */
export function deleteSession(sessionId: string): Promise<{ code: number; message: string; data: null }> {
  return request.delete(`/chat/session/${sessionId}`)
}

/** 更新会话标题 */
export function updateSessionTitle(sessionId: string, title: string): Promise<{ code: number; message: string; data: null }> {
  return request.put(`/chat/session/${sessionId}/title`, { title })
}

/** 中断 Agent 会话 */
export function abortAgent(sessionId: string): Promise<{ code: number; message: string; data: null }> {
  return request.post(`/chat/abort/${sessionId}`)
}

/**
 * 发送消息（SSE 流式）。
 * 兼容经典 Chat 模式和 Agent 模式。
 */
export function sendChatMessage(
  sessionId: string | null,
  configId: string,
  content: string,
  onMessage: (chunk: string) => void,
  onDone: (data: { sessionId: string; messageId: string }) => void,
  onError: (error: string) => void,
  onReasoning?: (chunk: string) => void,
  skillId?: string | null,
  workspaceId?: string | null
): AbortController {
  const controller = new AbortController()

  const url = '/api/chat/send'
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    'X-Emp-No': localStorage.getItem('apex_current_emp_no') || '0000000',
  }

  let streamTimeout: ReturnType<typeof setTimeout> | null = null
  const READ_TIMEOUT_MS = 120_000

  function resetTimeout() {
    if (streamTimeout) clearTimeout(streamTimeout)
    streamTimeout = setTimeout(() => {
      controller.abort()
      onError('流式响应超时，长时间未收到数据')
    }, READ_TIMEOUT_MS)
  }

  function stopTimeout() {
    if (streamTimeout) {
      clearTimeout(streamTimeout)
      streamTimeout = null
    }
  }

  fetch(url, {
    method: 'POST',
    headers,
    body: JSON.stringify({ sessionId, configId, content, skillId: skillId || null, workspaceId: workspaceId || null }),
    signal: controller.signal,
  }).then(async (response) => {
    if (!response.ok) {
      onError(`HTTP ${response.status}: ${response.statusText}`)
      return
    }
    const reader = response.body?.getReader()
    if (!reader) {
      onError('无法读取响应流')
      return
    }
    const decoder = new TextDecoder()
    let buffer = ''
    resetTimeout()

    let currentEvent = ''
    let currentData = ''
    let hasData = false
    let eventStarted = false

    function parseField(line: string): { field: string; value: string } | null {
      const colonIdx = line.indexOf(':')
      if (colonIdx === -1) return null
      const field = line.substring(0, colonIdx)
      let value = line.substring(colonIdx + 1)
      if (value.startsWith(' ')) value = value.substring(1)
      return { field, value }
    }

    function dispatchEvent(event: string, data: string) {
      if (event === 'reasoning') {
        onReasoning?.(data)
      } else if (event === 'message') {
        onMessage(data)
      } else if (event === 'done') {
        try {
          const payload = JSON.parse(data)
          onDone(payload)
        } catch {
          onDone({ sessionId: '', messageId: '' })
        }
      } else if (event === 'error') {
        onError(data)
      }
    }

    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      resetTimeout()
      buffer += decoder.decode(value, { stream: true })

      const parts = buffer.split('\n')
      buffer = parts.pop() || ''

      for (const rawLine of parts) {
        const line = rawLine.endsWith('\r') ? rawLine.slice(0, -1) : rawLine

        if (line === '') {
          if (currentEvent && hasData) {
            dispatchEvent(currentEvent, currentData)
            eventStarted = true
          }
          currentEvent = ''
          currentData = ''
          hasData = false
        } else if (line.startsWith(':')) {
          continue
        } else {
          const parsed = parseField(line)
          if (parsed) {
            if (parsed.field === 'event') {
              currentEvent = parsed.value
              currentData = ''
              hasData = false
            } else if (parsed.field === 'data') {
              hasData = true
              currentData += (currentData ? '\n' : '') + parsed.value
            }
          }
        }
      }
    }

    if (currentEvent && hasData) {
      dispatchEvent(currentEvent, currentData)
      eventStarted = true
    }

    if (!eventStarted) {
      onError('服务器未返回有效响应，请检查模型配置或稍后重试')
    }
    stopTimeout()
  }).catch((err) => {
    stopTimeout()
    if (err.name !== 'AbortError') {
      onError(err.message || '网络异常')
    }
  })

  return controller
}

/**
 * 发送 Agent 消息（SSE 流式，支持 Agent 专属事件）。
 * 后端通过 event:agent + data:json 发送所有 Agent 事件。
 */
export function sendAgentMessage(
  sessionId: string | null,
  configId: string,
  content: string,
  callbacks: AgentCallbacks,
  skillId?: string | null,
  workspaceId?: string | null
): AbortController {
  const controller = new AbortController()

  const url = '/api/chat/send'
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    'X-Emp-No': localStorage.getItem('apex_current_emp_no') || '0000000',
  }

  let streamTimeout: ReturnType<typeof setTimeout> | null = null
  // Agent 可执行多轮，超时设长一些（5 分钟无数据则断开）
  const READ_TIMEOUT_MS = 300_000

  function resetTimeout() {
    if (streamTimeout) clearTimeout(streamTimeout)
    streamTimeout = setTimeout(() => {
      controller.abort()
      callbacks.onError('Agent 响应超时，长时间未收到数据')
    }, READ_TIMEOUT_MS)
  }

  function stopTimeout() {
    if (streamTimeout) {
      clearTimeout(streamTimeout)
      streamTimeout = null
    }
  }

  fetch(url, {
    method: 'POST',
    headers,
    body: JSON.stringify({ sessionId, configId, content, skillId: skillId || null, workspaceId: workspaceId || null }),
    signal: controller.signal,
  }).then(async (response) => {
    if (!response.ok) {
      callbacks.onError(`HTTP ${response.status}: ${response.statusText}`)
      return
    }
    const reader = response.body?.getReader()
    if (!reader) {
      callbacks.onError('无法读取响应流')
      return
    }
    const decoder = new TextDecoder()
    let buffer = ''
    resetTimeout()

    let currentEvent = ''
    let currentData = ''
    let hasData = false
    let eventStarted = false

    function parseField(line: string): { field: string; value: string } | null {
      const colonIdx = line.indexOf(':')
      if (colonIdx === -1) return null
      const field = line.substring(0, colonIdx)
      let value = line.substring(colonIdx + 1)
      if (value.startsWith(' ')) value = value.substring(1)
      return { field, value }
    }

    function dispatchAgentEvent(data: string) {
      try {
        const event: AgentSSEEvent = JSON.parse(data)
        switch (event.type) {
          case 'text':
            callbacks.onText(event.content || '')
            break
          case 'reasoning':
            callbacks.onReasoning(event.reasoning || '')
            break
          case 'tool_start':
            callbacks.onToolStart(event.toolName || '', event.toolCallId || '')
            break
          case 'tool_end':
            callbacks.onToolEnd(
              event.toolName || '',
              event.toolCallId || '',
              event.status || 'success',
              event.result
            )
            break
          case 'file_changed':
            callbacks.onFileChanged(event.path || '', event.tool || '')
            break
          case 'done':
            callbacks.onDone({
              sessionId: event.sessionId || '',
              messageId: event.messageId || '',
            })
            break
          case 'error':
            callbacks.onError(event.error || '未知错误')
            break
        }
      } catch {
        // 非 JSON 数据，忽略（可能是心跳注释）
      }
    }

    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      resetTimeout()
      buffer += decoder.decode(value, { stream: true })

      const parts = buffer.split('\n')
      buffer = parts.pop() || ''

      for (const rawLine of parts) {
        const line = rawLine.endsWith('\r') ? rawLine.slice(0, -1) : rawLine

        if (line === '') {
          if (currentEvent && hasData) {
            if (currentEvent === 'agent') {
              dispatchAgentEvent(currentData)
            } else if (currentEvent === 'message') {
              // 兼容：经典 chat 模式下也走 agent view
              callbacks.onText(currentData)
            } else if (currentEvent === 'reasoning') {
              callbacks.onReasoning(currentData)
            } else if (currentEvent === 'error') {
              callbacks.onError(currentData)
            }
            eventStarted = true
          }
          currentEvent = ''
          currentData = ''
          hasData = false
        } else if (line.startsWith(':')) {
          continue
        } else {
          const parsed = parseField(line)
          if (parsed) {
            if (parsed.field === 'event') {
              currentEvent = parsed.value
              currentData = ''
              hasData = false
            } else if (parsed.field === 'data') {
              hasData = true
              currentData += (currentData ? '\n' : '') + parsed.value
            }
          }
        }
      }
    }

    if (currentEvent && hasData) {
      if (currentEvent === 'agent') {
        dispatchAgentEvent(currentData)
      }
      eventStarted = true
    }

    if (!eventStarted) {
      callbacks.onError('服务器未返回有效响应，请检查模型配置或稍后重试')
    }
    stopTimeout()
  }).catch((err) => {
    stopTimeout()
    if (err.name !== 'AbortError') {
      callbacks.onError(err.message || '网络异常')
    }
  })

  return controller
}

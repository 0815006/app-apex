import request from '@/utils/request'
import type { LlmConfigVO, LlmConfig, ChatSessionVO, ChatMessage } from '@/types/chat'

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

/** 获取会话列表 */
export function listSessions(): Promise<{ code: number; message: string; data: ChatSessionVO[] }> {
  return request.get('/chat/sessions')
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

/**
 * 发送消息（SSE 流式）。
 * 不使用 axios，直接用 fetch + ReadableStream 解析 SSE。
 */
export function sendChatMessage(
  sessionId: string | null,
  configId: string,
  content: string,
  onMessage: (chunk: string) => void,
  onDone: (data: { sessionId: string; messageId: string }) => void,
  onError: (error: string) => void,
  onReasoning?: (chunk: string) => void
): AbortController {
  const controller = new AbortController()

  const url = '/api/chat/send'
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    'X-Emp-No': localStorage.getItem('apex_current_emp_no') || '0000000',
  }

  // 流读取超时（60 秒无数据则自动断开）
  let streamTimeout: ReturnType<typeof setTimeout> | null = null
  const READ_TIMEOUT_MS = 60_000

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
    body: JSON.stringify({ sessionId, configId, content }),
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

    // SSE 逐行解析状态机
    let currentEvent = ''
    let currentData = ''
    let hasData = false
    let eventStarted = false // 是否已经开始收到过有效 SSE 事件

    function parseField(line: string): { field: string; value: string } | null {
      const colonIdx = line.indexOf(':')
      if (colonIdx === -1) return null
      const field = line.substring(0, colonIdx)
      // SSE spec: 冒号后可选一个空格，跳过之
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
          // 空行 = SSE 消息边界
          if (currentEvent && hasData) {
            dispatchEvent(currentEvent, currentData)
            eventStarted = true
          }
          currentEvent = ''
          currentData = ''
          hasData = false
        } else if (line.startsWith(':')) {
          // SSE comment，忽略
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
            // id:, retry: 等其他 SSE 字段忽略
          }
        }
      }
    }

    // 流结束后处理缓冲中残留的最后一条消息
    if (currentEvent && hasData) {
      dispatchEvent(currentEvent, currentData)
      eventStarted = true
    }

    // 如果流结束但从未收到任何有效 SSE 事件，视为错误
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

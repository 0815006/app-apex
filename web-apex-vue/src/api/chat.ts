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

    // SSE 逐行解析状态机
    let currentEvent = ''
    let currentData = ''
    let hasData = false  // 关键：标记是否至少收到过 data: 行

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
      buffer += decoder.decode(value, { stream: true })

      // 按 \n 拆行；trimEnd 去除 \r（兼容 \r\n 行尾）
      const parts = buffer.split('\n')
      buffer = parts.pop() || ''

      for (const rawLine of parts) {
        const line = rawLine.endsWith('\r') ? rawLine.slice(0, -1) : rawLine

        if (line.startsWith('event: ')) {
          // 如果有未分发的上一事件（例如 data 为空但 event 已设置），跳过
          // 新事件开始，重置状态
          currentEvent = line.slice(7).trim()
          currentData = ''
          hasData = false
        } else if (line.startsWith('data: ')) {
          hasData = true
          const payload = line.slice(6)
          currentData += (currentData ? '\n' : '') + payload
        } else if (line === '' && currentEvent && hasData) {
          // 空行 = SSE 消息边界；仅在已有事件类型且已收到 data 时触发
          dispatchEvent(currentEvent, currentData)
          currentEvent = ''
          currentData = ''
          hasData = false
        }
        // 其他行（如 :comment, id: 等 SSE 可选字段）忽略
      }
    }

    // 流结束后处理缓冲中残留的最后一条消息
    if (currentEvent && hasData) {
      dispatchEvent(currentEvent, currentData)
    }
  }).catch((err) => {
    if (err.name !== 'AbortError') {
      onError(err.message || '网络异常')
    }
  })

  return controller
}

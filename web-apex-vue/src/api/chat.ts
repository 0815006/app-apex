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
 * 返回 ReadableStream 用于自定义读取 SSE 事件。
 * 不使用 axios，直接用 fetch 读取流。
 */
export function sendChatMessage(
  sessionId: string | null,
  configId: string,
  content: string,
  onMessage: (chunk: string) => void,
  onDone: (data: { sessionId: string; messageId: string }) => void,
  onError: (error: string) => void
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
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      // SSE 格式: event: xxx\ndata: xxx\n\n
      const lines = buffer.split('\n\n')
      buffer = lines.pop() || '' // 不完整的部分放回 buffer
      for (const block of lines) {
        const eventMatch = block.match(/^event:\s*(.+)$/m)
        const dataMatch = block.match(/^data:\s*(.+)$/m)
        if (!eventMatch || !dataMatch) continue
        const event = eventMatch[1].trim()
        const data = dataMatch[1].trim()
        if (event === 'message') {
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
    }
  }).catch((err) => {
    if (err.name !== 'AbortError') {
      onError(err.message || '网络异常')
    }
  })

  return controller
}

/**
 * 聊天相关类型定义。
 */

/** LLM 配置（下拉框用） */
export interface LlmConfigVO {
  id: string
  configName: string
  modelName: string
}

/** LLM 配置完整实体 */
export interface LlmConfig {
  id?: string
  userId?: string
  configName: string
  apiUrl: string
  apiKey: string
  modelName: string
  createTime?: string
  updateTime?: string
}

/** 会话列表项 */
export interface ChatSessionVO {
  id: string
  title: string
  configName: string
  modelName: string
  createTime: string
  updateTime: string
}

/** 消息 */
export interface ChatMessage {
  id?: string
  sessionId: string
  role: 'user' | 'assistant'
  content: string
  createTime?: string
}

/** 发送消息请求 */
export interface ChatRequest {
  sessionId: string | null
  configId: string
  content: string
}

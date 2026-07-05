/**
 * 聊天相关类型定义。
 */

/** LLM 配置（下拉框用） */
export interface LlmConfigVO {
  id: string
  configName: string
  modelName: string
  isAgentSupported?: number
}

/** LLM 配置完整实体 */
export interface LlmConfig {
  id?: string
  userId?: string
  configName: string
  apiUrl: string
  apiKey: string
  modelName: string
  isAgentSupported?: number
  createTime?: string
  updateTime?: string
}

/** 会话列表项 */
export interface ChatSessionVO {
  id: string
  title: string
  configName: string
  modelName: string
  sessionMode: string
  createTime: string
  updateTime: string
}

/** 消息 */
export interface ChatMessage {
  id?: string
  sessionId: string
  role: 'user' | 'assistant' | 'tool'
  content: string
  toolName?: string
  toolCallId?: string
  toolStatus?: string
  toolCallsJson?: string
  createTime?: string
}

/** 发送消息请求 */
export interface ChatRequest {
  sessionId: string | null
  configId: string
  content: string
  skillId?: string | null
  workspaceId?: string | null
}

// ===================== Agent SSE 事件类型 =====================

/** Agent SSE 事件类型 */
export type AgentEventType =
  | 'text'
  | 'reasoning'
  | 'tool_start'
  | 'tool_end'
  | 'file_changed'
  | 'done'
  | 'error'

/** Agent SSE 基础事件 */
export interface AgentSSEEvent {
  type: AgentEventType
  /** text 块的 delta 内容 */
  content?: string
  /** reasoning 块的 delta 内容 */
  reasoning?: string
  /** 工具名称 */
  toolName?: string
  /** 工具调用 ID */
  toolCallId?: string
  /** 工具执行结果 */
  result?: string
  /** tool_end 的状态 */
  status?: string
  /** file_changed 的文件路径 */
  path?: string
  /** file_changed 的工具名 */
  tool?: string
  /** done 事件的会话信息 */
  sessionId?: string
  messageId?: string
  /** error 事件的错误信息 */
  error?: string
}

/** Agent 发送完成回调 */
export interface AgentDonePayload {
  sessionId: string
  messageId: string
}

/** Agent 消息回调集合 */
export interface AgentCallbacks {
  onText: (delta: string) => void
  onReasoning: (delta: string) => void
  onToolStart: (toolName: string, toolCallId: string) => void
  onToolEnd: (toolName: string, toolCallId: string, status: string, result?: string) => void
  onFileChanged: (path: string, tool: string) => void
  onDone: (payload: AgentDonePayload) => void
  onError: (error: string) => void
}

// ===================== 工作空间类型 =====================

/** 工作空间 */
export interface AgentWorkspace {
  id?: string
  name: string
  dirName: string
  description?: string
  createTime?: string
  updateTime?: string
}

/** 工作空间文件树节点 */
export interface WorkspaceFileNode {
  name: string
  path: string
  type: 'dir' | 'file'
  children?: WorkspaceFileNode[]
}

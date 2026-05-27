/**
 * AI Skill 相关类型定义。
 */

/** Skill 视图对象 */
export interface SkillVO {
  id: string
  name: string
  icon: string | null
  description: string | null
  type: 'prompt' | 'agent' | 'workflow'
  systemPrompt: string | null
  temperature: number
  workflowId: string | null
  status: number
  sortOrder: number
  createTime: string
  updateTime: string
}

/** Skill 表单数据（创建/更新） */
export interface SkillForm {
  id?: string
  name: string
  icon?: string
  description?: string
  type: 'prompt' | 'agent' | 'workflow'
  systemPrompt?: string
  temperature?: number
  workflowId?: string
  status?: number
  sortOrder?: number
}

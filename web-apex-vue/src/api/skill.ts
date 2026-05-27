import request from '@/utils/request'
import type { SkillVO, SkillForm } from '@/types/skill'

/**
 * 获取所有启用的 Skill 列表（ChatView 加号按钮用）。
 */
export function listEnabledSkills(): Promise<{ code: number; message: string; data: SkillVO[] }> {
  return request.get('/skill/enabled')
}

/**
 * 获取全部 Skill（管理页面用，含禁用）。
 */
export function listAllSkills(): Promise<{ code: number; message: string; data: SkillVO[] }> {
  return request.get('/skill')
}

/**
 * 获取单个 Skill。
 */
export function getSkill(id: string): Promise<{ code: number; message: string; data: SkillVO }> {
  return request.get(`/skill/${id}`)
}

/**
 * 新增 Skill。
 */
export function createSkill(skill: SkillForm): Promise<{ code: number; message: string; data: SkillVO }> {
  return request.post('/skill', skill)
}

/**
 * 更新 Skill。
 */
export function updateSkill(id: string, skill: SkillForm): Promise<{ code: number; message: string; data: SkillVO }> {
  return request.put(`/skill/${id}`, skill)
}

/**
 * 删除 Skill。
 */
export function deleteSkill(id: string): Promise<{ code: number; message: string; data: null }> {
  return request.delete(`/skill/${id}`)
}

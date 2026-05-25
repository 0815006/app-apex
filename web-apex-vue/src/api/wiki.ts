import request from '@/utils/request'
import type { WikiNodeVO, WikiDocument } from '@/types/wiki'

/**
 * 获取完整 Wiki 目录树。
 */
export function getWikiTree(): Promise<{ code: number; message: string; data: WikiNodeVO[] }> {
  return request.get('/wiki/tree')
}

/**
 * 根据 ID 获取文档详情。
 */
export function getDocDetail(id: string): Promise<{ code: number; message: string; data: WikiDocument }> {
  return request.get(`/wiki/${id}`)
}

/**
 * 根据标题获取文档（双链跳转专用）。
 */
export function getDocByTitle(title: string): Promise<{ code: number; message: string; data: WikiDocument }> {
  return request.get('/wiki/by-title', { params: { title } })
}

/**
 * 创建或更新文档。
 */
export function saveDoc(doc: {
  id?: string
  title: string
  content?: string
  type: number
  parentId?: string
  sortOrder?: number
}): Promise<{ code: number; message: string; data: WikiDocument }> {
  return request.post('/wiki/save', doc)
}

/**
 * 删除文档（级联删除子节点）。
 */
export function deleteDoc(id: string): Promise<{ code: number; message: string; data: null }> {
  return request.delete(`/wiki/${id}`)
}

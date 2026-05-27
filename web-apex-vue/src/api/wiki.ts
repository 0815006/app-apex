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

/**
 * 移动节点到指定父节点和排序位置。
 */
export function moveNode(
  id: string,
  newParentId: string,
  newSortOrder: number
): Promise<{ code: number; message: string; data: null }> {
  return request.put(`/wiki/${id}/move`, { newParentId, newSortOrder })
}

/**
 * 批量更新同级节点排序。
 */
export function batchUpdateSortOrder(
  items: { id: string; sortOrder: number }[]
): Promise<{ code: number; message: string; data: null }> {
  return request.put('/wiki/sort-batch', { items })
}

/**
 * 获取指定文件夹的直接子节点（不递归）。
 */
export function getFolderChildren(
  folderId: string
): Promise<{ code: number; message: string; data: WikiNodeVO[] }> {
  return request.get(`/wiki/${folderId}/children`)
}

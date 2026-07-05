import request from '@/utils/request'
import type { AgentWorkspace, WorkspaceFileNode } from '@/types/chat'

/**
 * Agent 工作空间 API
 */

/** 获取工作空间列表 */
export function listWorkspaces(): Promise<{ code: number; message: string; data: AgentWorkspace[] }> {
  return request.get('/workspace/list')
}

/** 创建工作空间 */
export function createWorkspace(data: { name: string; dirName: string; description?: string }): Promise<{ code: number; message: string; data: AgentWorkspace }> {
  return request.post('/workspace/create', data)
}

/** 更新工作空间 */
export function updateWorkspace(id: string, data: { name: string; description?: string }): Promise<{ code: number; message: string; data: AgentWorkspace }> {
  return request.put(`/workspace/${id}`, data)
}

/** 删除工作空间（不删除物理文件） */
export function deleteWorkspace(id: string): Promise<{ code: number; message: string; data: null }> {
  return request.delete(`/workspace/${id}`)
}

/** 查询磁盘上存在但未入库的目录名（用于导入） */
export function listUnregisteredDirs(): Promise<{ code: number; message: string; data: string[] }> {
  return request.get('/workspace/unregistered-dirs')
}

/** 导入已有磁盘目录为工作空间 */
export function importWorkspace(data: { dirName: string; name?: string; description?: string }): Promise<{ code: number; message: string; data: AgentWorkspace }> {
  return request.post('/workspace/import', data)
}

/** 获取工作空间文件树 */
export function getWorkspaceFileTree(workspaceId: string): Promise<{ code: number; message: string; data: WorkspaceFileNode[] }> {
  return request.get(`/workspace/${workspaceId}/tree`)
}

/** 读取工作空间中的文件内容 */
export function readWorkspaceFile(workspaceId: string, filePath: string): Promise<{ code: number; message: string; data: string }> {
  return request.get(`/workspace/${workspaceId}/file`, {
    params: { path: filePath },
  })
}

/** 删除工作空间中的文件或目录 */
export function deleteWorkspaceFile(workspaceId: string, filePath: string): Promise<{ code: number; message: string; data: null }> {
  return request.delete(`/workspace/${workspaceId}/file`, {
    params: { path: filePath },
  })
}

/** 重命名工作空间中的文件或目录 */
export function renameWorkspaceFile(workspaceId: string, filePath: string, newName: string): Promise<{ code: number; message: string; data: null }> {
  return request.put(`/workspace/${workspaceId}/rename`, { path: filePath, newName })
}

/** 上传文件到工作空间指定目录（使用 FormData） */
export function uploadWorkspaceFile(workspaceId: string, dirPath: string, file: File): Promise<{ code: number; message: string; data: null }> {
  const formData = new FormData()
  formData.append('path', dirPath)
  formData.append('file', file)
  return request.post(`/workspace/${workspaceId}/upload`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

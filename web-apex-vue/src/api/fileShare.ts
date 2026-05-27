import request from '@/utils/request'

/** 文件记录 */
export interface SharedFile {
  id: string
  fileName: string
  fileSize: number
  storagePath: string
  uploadEmpNo: string
  uploadIp: string
  createTime: string
  updateTime: string
}

/** 分页结果 */
export interface PageResult<T> {
  records: T[]
  total: number
  size: number
  current: number
  pages: number
}

/**
 * 上传文件。
 */
export function uploadFile(file: File): Promise<{ code: number; message: string; data: SharedFile }> {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/file-share/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 120000, // 上传大文件需要更长的超时时间
  })
}

/**
 * 分页获取文件列表。
 */
export function getFileList(
  page = 1,
  pageSize = 20
): Promise<{ code: number; message: string; data: PageResult<SharedFile> }> {
  return request.get('/file-share/list', { params: { page, pageSize } })
}

/**
 * 获取全部文件列表（不分页）。
 */
export function getAllFiles(): Promise<{ code: number; message: string; data: SharedFile[] }> {
  return request.get('/file-share/all')
}

/**
 * 获取文件下载 URL。
 */
export function getDownloadUrl(id: string): string {
  return `/api/file-share/download/${id}`
}

/**
 * 删除文件。
 */
export function deleteFile(id: string): Promise<{ code: number; message: string; data: null }> {
  return request.delete(`/file-share/${id}`)
}

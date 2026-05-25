import request from '@/utils/request'

/**
 * 健康检查
 */
export function getHealth(): Promise<string> {
  return request.get('/health').then((res) => res.data)
}

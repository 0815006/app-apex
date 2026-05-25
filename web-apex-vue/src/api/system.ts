import request from '@/utils/request'

/**
 * 系统信息接口
 */
export interface SystemInfo {
  loginIp: string
  serverTime: string
}

/**
 * 获取系统信息（含用户真实 Login IP）
 */
export function getSystemInfo(): Promise<SystemInfo> {
  return request.get('/system/info').then((res) => res.data)
}

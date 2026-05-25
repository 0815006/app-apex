import axios from 'axios'
import { ElMessage } from 'element-plus'
import { getCurrentEmpNo } from './currentUser'

const request = axios.create({
  baseURL: '/api',
  timeout: 15000,
  headers: {
    'Content-Type': 'application/json',
  },
})

// 请求拦截器：自动注入当前员工号
request.interceptors.request.use(
  (config) => {
    const empNo = getCurrentEmpNo()
    if (empNo) {
      config.headers['X-Emp-No'] = empNo
    }
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

// 响应拦截器：统一错误处理
request.interceptors.response.use(
  (response) => {
    const { data } = response
    // 放行正常响应
    if (data.code === 200) {
      return data
    }
    // 放行特定业务逻辑错误码（如 Wiki 的 404），由页面自行处理
    if (data.code === 404) {
      return data
    }
    // 其他错误统一提示
    ElMessage.error(data.message || '请求失败')
    return Promise.reject(data)
  },
  (error) => {
    ElMessage.error(error.message || '网络异常')
    return Promise.reject(error)
  }
)

export default request

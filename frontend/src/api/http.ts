import axios from 'axios'
import { ElMessage } from 'element-plus'

export interface ApiResponse<T> {
  code: number
  message: string
  data: T
  traceId?: string
}

const ACCESS_KEY = 'offerpilot_access_token'
const REFRESH_KEY = 'offerpilot_refresh_token'
const USER_KEY = 'offerpilot_user'

const http = axios.create({
  baseURL: '/api',
  timeout: 15000
})

http.interceptors.request.use((config) => {
  const token = localStorage.getItem(ACCESS_KEY)
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

http.interceptors.response.use(
  (response) => {
    const body = response.data as ApiResponse<unknown>
    if (body.code !== 0) {
      ElMessage.error(body.message || '请求失败')
      return Promise.reject(new Error(body.message || '请求失败'))
    }
    // 运行时返回业务 data；泛型由各 API 方法的第二个参数声明
    return body.data as unknown as typeof response
  },
  (error) => {
    const status = error.response?.status
    const message = error.response?.data?.message

    if (status === 401) {
      localStorage.removeItem(ACCESS_KEY)
      localStorage.removeItem(REFRESH_KEY)
      localStorage.removeItem(USER_KEY)
      if (window.location.pathname !== '/login') {
        window.location.assign('/login')
      }
    }

    ElMessage.error(message || '网络异常，请稍后重试')
    return Promise.reject(error)
  }
)

export default http

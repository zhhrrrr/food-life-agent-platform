import axios, { type InternalAxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../stores/auth'
import type { ApiResponse } from '../types/api'

export const http = axios.create({
  timeout: 10000,
})

function expireCredential(config?: Pick<InternalAxiosRequestConfig, 'headers'>) {
  const auth = useAuthStore()
  // A request from the previous account must not invalidate a newer login.
  if (auth.token && config?.headers?.authorization === auth.token) auth.logout()
}

http.interceptors.request.use((config) => {
  const auth = useAuthStore()
  if (auth.token) {
    config.headers = config.headers ?? {}
    config.headers.authorization = auth.token
  }
  return config
})

http.interceptors.response.use(
  (response) => {
    const payload = response.data as ApiResponse<unknown>
    if (payload?.code && payload.code !== '0000') {
      if (String(payload.code) === '401') expireCredential(response.config)
      ElMessage.error(payload.message || '请求失败')
      return Promise.reject(new Error(payload.message || '请求失败'))
    }
    return response
  },
  (error) => {
    if (error.response?.status === 401) expireCredential(error.config)
    const message = error.response?.data?.message || (typeof error.response?.data?.detail === 'string' ? error.response.data.detail : '') || error.message || '网络异常'
    ElMessage.error(message)
    return Promise.reject(error)
  },
)

export async function request<T>(config: Parameters<typeof http.request>[0]) {
  const response = await http.request<ApiResponse<T>>(config)
  return response.data.data
}

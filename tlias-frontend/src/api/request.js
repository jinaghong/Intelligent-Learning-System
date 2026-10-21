import axios from 'axios'
import { ElMessage } from 'element-plus'

import { clearToken, clearUser, getToken } from '@/utils/token'

const service = axios.create({
  baseURL: '/api',
  timeout: 20000
})

service.interceptors.request.use((config) => {
  const token = getToken()
  if (token) {
    config.headers['tlias-token'] = token
  }
  return config
})

service.interceptors.response.use(
  (response) => {
    if (response.config.responseType === 'blob') {
      return response
    }
    const body = response.data
    if (body && body.code === 1) {
      return body.data
    }
    const message = body?.msg || '请求失败'
    if (!response.config?.silent) {
      ElMessage.error(message)
    }
    return Promise.reject(new Error(message))
  },
  (error) => {
    if (error.response?.status === 401) {
      clearToken()
      clearUser()
      const message = error.response?.data?.msg || '登录已过期，请重新登录'
      ElMessage.error(message)
      if (window.location.pathname !== '/login') {
        const redirect = encodeURIComponent(window.location.pathname + window.location.search)
        window.location.href = `/login?redirect=${redirect}`
      }
    } else if (!error.config?.silent) {
      ElMessage.error(error.response?.data?.msg || error.message || '网络异常，请稍后重试')
    }
    return Promise.reject(error)
  }
)

export default service

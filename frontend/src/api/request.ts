import axios from 'axios'
import { toast } from 'vue-sonner'

const request = axios.create({
  baseURL: '/api',
  timeout: 30000,
})

request.interceptors.request.use((config) => {
  const token = localStorage.getItem('spectrace-token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

request.interceptors.response.use(
  (response) => {
    const body = response.data
    if (body && typeof body === 'object' && 'code' in body) {
      if (body.code !== 0) {
        toast.error(body.message || '请求失败')
        return Promise.reject(new Error(body.message))
      }
      return body.data
    }
    return body
  },
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem('spectrace-token')
      localStorage.removeItem('spectrace-user')
      if (location.hash.includes('/admin') && !location.hash.includes('/admin/login')) {
        toast.error('登录已过期，请重新登录')
        location.hash = '#/admin/login'
      }
    } else {
      toast.error(error.response?.data?.message || error.message || '网络错误')
    }
    return Promise.reject(error)
  },
)

export default request

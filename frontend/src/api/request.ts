import axios from 'axios'
import { toast } from 'vue-sonner'
import { useAuthStore } from '@/stores/auth'

declare module 'axios' {
  export interface AxiosRequestConfig {
    /** 路由守卫静默校验会话时置 true：失败不弹提示、不做整页跳转，由调用方自行处理 */
    skipAuthRedirect?: boolean
  }
}

const request = axios.create({
  baseURL: '/api',
  timeout: 30000,
})

request.interceptors.request.use((config) => {
  // 只在请求时取 store：模块加载期 pinia 还没安装，提前取会拿不到实例
  const { token } = useAuthStore()
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
    // 静默校验（如路由守卫里的 /auth/me）由调用方决定如何提示与跳转
    const silent = (error.config as { skipAuthRedirect?: boolean } | undefined)?.skipAuthRedirect
    if (error.response?.status === 401) {
      // 统一走 store：访客页上的管理员入口也会随状态清空即时隐藏
      useAuthStore().clear()
      if (!silent && location.pathname.startsWith('/admin') && !location.pathname.startsWith('/admin/login')) {
        toast.error('登录已过期，请重新登录')
        location.href = '/admin/login'
      }
    } else if (!silent) {
      toast.error(error.response?.data?.message || error.message || '网络错误')
    }
    return Promise.reject(error)
  },
)

export default request

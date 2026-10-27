import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { toast } from 'vue-sonner'

import App from './App.vue'
import router from './router'
import './style.css'
import 'vue-sonner/style.css'

const app = createApp(App)

/**
 * 渲染期未捕获异常的最后一道兜底。
 *
 * <p>页面级降级由 AppErrorBoundary 负责；走到这里的通常是边界之外的异常（事件回调、
 * 异步任务、根组件自身），此时至少要有日志与用户可见提示，不能静默白屏。</p>
 */
app.config.errorHandler = (error, instance, info) => {
  const component = (instance as { $options?: { name?: string } } | null)?.$options?.name ?? null
  console.error('[vue] 未捕获异常', { info, component, route: location.pathname }, error)
  toast.error('页面出现异常，请重试')
}

app.use(createPinia())
app.use(router)
app.mount('#app')

// T89 PWA：只在生产构建注册 SW，开发环境不缓存热更新资源
if (import.meta.env.PROD && 'serviceWorker' in navigator) {
  window.addEventListener('load', () => {
    navigator.serviceWorker.register('/sw.js').catch((error) => {
      console.warn('Service Worker 注册失败（不影响使用）', error)
    })
  })
}

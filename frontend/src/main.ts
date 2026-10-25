import { createApp } from 'vue'
import { createPinia } from 'pinia'

import App from './App.vue'
import router from './router'
import './style.css'
import 'vue-sonner/style.css'

const app = createApp(App)

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

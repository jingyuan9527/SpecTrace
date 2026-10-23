import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

// 默认代理到本地 8080；联调多实例时可用 VITE_API_TARGET 覆盖
const apiTarget = process.env.VITE_API_TARGET || 'http://localhost:8080'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: apiTarget,
        changeOrigin: true,
      },
      '/uploads': {
        target: apiTarget,
        changeOrigin: true,
      },
      '/brands': {
        target: apiTarget,
        changeOrigin: true,
      },
      // 机型图走独立前缀：与 SPA 的 /devices 路由同名会被代理吞掉，导致详情页 500
      '/device-images': {
        target: apiTarget,
        changeOrigin: true,
      },
    },
  },
})

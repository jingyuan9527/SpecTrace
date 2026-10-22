import { defineStore } from 'pinia'

const STORAGE_KEY = 'spectrace-dark'

/**
 * 主题状态：Nova 设计令牌以 .dark 类切换，首屏由 index.html 内联脚本提前恢复，避免闪白。
 */
export const useThemeStore = defineStore('theme', {
  state: () => ({
    dark: localStorage.getItem(STORAGE_KEY) === '1',
  }),
  actions: {
    apply() {
      document.documentElement.classList.toggle('dark', this.dark)
      localStorage.setItem(STORAGE_KEY, this.dark ? '1' : '0')
    },
    toggle() {
      this.dark = !this.dark
      this.apply()
    },
  },
})

import { defineStore } from 'pinia'

export const useThemeStore = defineStore('theme', {
  state: () => ({
    dark: localStorage.getItem('spectrace-dark') === '1',
  }),
  actions: {
    apply() {
      const root = document.documentElement
      root.classList.toggle('dark', this.dark)
      localStorage.setItem('spectrace-dark', this.dark ? '1' : '0')
    },
    toggle() {
      this.dark = !this.dark
      this.apply()
    },
  },
})

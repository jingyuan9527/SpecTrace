import { defineStore } from 'pinia'

/**
 * 对比池：访客在机型库/详情页挑选机型，跨页面共享选择结果。
 */
export const useCompareStore = defineStore('compare', {
  state: () => ({
    devices: [] as { id: number; name: string; brandName?: string; thumbnail?: string }[],
  }),
  getters: {
    ids: (state) => state.devices.map((d) => d.id),
    count: (state) => state.devices.length,
  },
  actions: {
    add(device: { id: number; name: string; brandName?: string; thumbnail?: string }) {
      if (!this.devices.find((d) => d.id === device.id)) {
        this.devices.push(device)
      }
    },
    remove(id: number) {
      this.devices = this.devices.filter((d) => d.id !== id)
    },
    clear() {
      this.devices = []
    },
    has(id: number) {
      return this.devices.some((d) => d.id === id)
    },
  },
})

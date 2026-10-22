import { defineStore } from 'pinia'

export interface CompareDevice {
  id: number
  name: string
  brandName?: string
  thumbnail?: string
}

const STORAGE_KEY = 'spectrace-compare'
const MAX_COMPARE = 4

function restore(): CompareDevice[] {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    return raw ? (JSON.parse(raw) as CompareDevice[]) : []
  } catch {
    return []
  }
}

/**
 * 对比池：访客在机型库/详情页挑选机型，跨页面共享并持久化到本地。
 * 状态管理统一使用 Pinia（React 侧 Zustand 的等价方案）。
 */
export const useCompareStore = defineStore('compare', {
  state: () => ({
    devices: restore(),
  }),
  getters: {
    ids: (state) => state.devices.map((d) => d.id),
    count: (state) => state.devices.length,
  },
  actions: {
    persist() {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(this.devices))
    },
    /** 返回是否真正加入；已在池中或超过上限时返回 false。 */
    add(device: CompareDevice): boolean {
      if (this.devices.find((d) => d.id === device.id)) return false
      if (this.devices.length >= MAX_COMPARE) return false
      this.devices.push(device)
      this.persist()
      return true
    },
    remove(id: number) {
      this.devices = this.devices.filter((d) => d.id !== id)
      this.persist()
    },
    clear() {
      this.devices = []
      this.persist()
    },
    has(id: number) {
      return this.devices.some((d) => d.id === id)
    },
  },
})

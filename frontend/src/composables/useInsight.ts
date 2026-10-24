import { onBeforeUnmount, ref, type Ref } from 'vue'
import type { InsightResult } from '@/types/api'

/**
 * AI 文案（对比差异摘要 / 世代回顾）的共享状态机。
 *
 * <p>护栏语义与后端约定：只读探测失败静默隐藏、生成失败降级为 failed（HTTP 200）、
 * 429 由请求拦截器统一提示不再重复报错。两处页面的行为必须一致，因此收口到这里。</p>
 */
export function useInsight(probe: () => Promise<InsightResult | null>, generate: () => Promise<InsightResult>) {
  const insight: Ref<InsightResult | null> = ref(null)
  const generating = ref(false)
  let probeSeq = 0

  /** 只读探测：决定入口是否展示（available=false 时前端整块隐藏）。 */
  async function load() {
    const seq = ++probeSeq
    insight.value = null
    try {
      const result = await probe()
      if (seq === probeSeq) insight.value = result
    } catch {
      // 文案不是关键路径，取不到就不展示
      if (seq === probeSeq) insight.value = null
    }
  }

  async function run() {
    if (generating.value) return
    generating.value = true
    try {
      insight.value = await generate()
    } catch (error) {
      // 429 已由请求拦截器提示「生成过于频繁」，保留入口态即可，避免误报服务不可用
      if ((error as { response?: { status?: number } })?.response?.status !== 429) {
        insight.value = { available: true, failed: true }
      }
    } finally {
      generating.value = false
    }
  }

  return { insight, generating, load, run }
}

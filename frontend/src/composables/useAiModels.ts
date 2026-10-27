import { computed, ref } from 'vue'
import { adminApi } from '@/api'
import type { AiModel } from '@/types/api'

/**
 * AI 模型选项：后台多个弹窗（抓取、订阅）共用同一份「供应商连接 / 模型名」列表。
 *
 * <p>进程内缓存 60 秒：模型配置改动不频繁，但改完立刻回列表页再开弹窗的概率不低，
 * 无缓存会读到上一次的旧列表。需要强制刷新时传 `force`。</p>
 */
const CACHE_TTL_MS = 60_000

const models = ref<AiModel[]>([])
const loading = ref(false)
let loadedAt = 0
let pending: Promise<AiModel[]> | null = null

export function useAiModels() {
  async function load(force = false): Promise<AiModel[]> {
    const fresh = models.value.length > 0 && Date.now() - loadedAt < CACHE_TTL_MS
    if (fresh && !force) {
      return models.value
    }
    if (pending) {
      return pending
    }
    loading.value = true
    pending = adminApi.aiModels()
      .then((list) => {
        models.value = list
        loadedAt = Date.now()
        return list
      })
      .catch((error) => {
        // 未配置 AI 或接口异常时退化为空列表：抓取仍可用后台默认模型，弹窗不该因此打不开
        console.warn('[useAiModels] 模型列表加载失败，抓取将使用后台默认模型', error)
        return [] as AiModel[]
      })
      .finally(() => {
        loading.value = false
        pending = null
      })
    return pending
  }

  /** 选项文案与旧实现保持一致：供应商连接 / 模型名（默认） */
  const options = computed(() =>
    models.value.map((m) => ({
      label: `${m.configName} / ${m.model}${m.isDefault === 1 ? '（默认）' : ''}`,
      value: m.id,
    })),
  )

  const defaultModelId = computed(() => models.value.find((m) => m.isDefault === 1)?.id ?? null)

  return { models, loading, load, options, defaultModelId }
}

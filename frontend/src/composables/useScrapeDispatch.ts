import { computed, ref } from 'vue'
import { toast } from 'vue-sonner'
import { adminApi } from '@/api'
import { useAiModels } from '@/composables/useAiModels'

/** 抓取目标：三个入口的机型类型不同，进弹窗前统一成这一形状 */
export interface ScrapeTarget {
  id: number
  name: string
  brandName?: string
  status?: number
}

export interface ScrapeOpenOptions {
  /** 已知目标列表；与 `load` 二选一 */
  targets?: ScrapeTarget[]
  /** 需要先向后端要目标（系列页按系列拉缺参/陈旧清单） */
  load?: () => Promise<ScrapeTarget[]>
  title: string
  description: string
  /** 目标行右侧展示品牌还是上下架状态 */
  meta?: 'brand' | 'status'
  emptyText?: string
  /** 提交按钮文案，默认「提交 N 个任务」 */
  submitLabel?: (count: number) => string
  /** 成功提示，默认「已提交 N 个抓取任务，结果将进入待审核」 */
  successMessage?: (count: number) => string
}

/**
 * 批量发起抓取：三处入口（机型页、数据健康度、系列页）共用一套状态与提交逻辑。
 *
 * <p>顺序逐台提交而不是并发：任务队列消费能力有限，一次并发打满只会让所有任务一起变慢，
 * 并且失败时无法定位到具体是哪台。</p>
 */
export function useScrapeDispatch(options: { onSubmitted?: () => void | Promise<void> } = {}) {
  const aiModels = useAiModels()

  const open = ref(false)
  const targets = ref<ScrapeTarget[]>([])
  const loading = ref(false)
  const submitting = ref(false)
  const modelId = ref<number | null>(null)

  const title = ref('')
  const description = ref('')
  const meta = ref<'brand' | 'status'>('status')
  const emptyText = ref('没有需要抓取的机型')
  let submitLabel: (count: number) => string = (count) => `提交 ${count} 个任务`
  let successMessage: (count: number) => string = (count) => `已提交 ${count} 个抓取任务，结果将进入待审核`

  const submitText = computed(() => submitLabel(targets.value.length))

  async function loadModels() {
    await aiModels.load()
    modelId.value = aiModels.defaultModelId.value
  }

  async function start(opts: ScrapeOpenOptions) {
    title.value = opts.title
    description.value = opts.description
    meta.value = opts.meta ?? 'status'
    emptyText.value = opts.emptyText ?? '没有需要抓取的机型'
    submitLabel = opts.submitLabel ?? ((count) => `提交 ${count} 个任务`)
    successMessage = opts.successMessage ?? ((count) => `已提交 ${count} 个抓取任务，结果将进入待审核`)

    targets.value = opts.targets ?? []
    open.value = true
    loading.value = Boolean(opts.load)

    // 目标与模型并行拉取：系列页的清单可能较大，串行会让弹窗空等一个 RTT
    const tasks: Promise<unknown>[] = [loadModels()]
    if (opts.load) {
      tasks.push(
        opts.load()
          .then((rows) => {
            targets.value = rows
          })
          .finally(() => {
            loading.value = false
          }),
      )
    }
    await Promise.all(tasks)
  }

  async function confirm() {
    if (!targets.value.length || submitting.value) {
      return
    }
    const count = targets.value.length
    submitting.value = true
    try {
      for (const target of targets.value) {
        await adminApi.createAgentTask({
          modelName: target.name,
          brandName: target.brandName ?? '',
          aiModelId: modelId.value ?? undefined,
        })
      }
      toast.success(successMessage(count))
      open.value = false
      await options.onSubmitted?.()
    } finally {
      submitting.value = false
    }
  }

  return {
    open,
    targets,
    loading,
    submitting,
    modelId,
    modelOptions: aiModels.options,
    title,
    description,
    meta,
    emptyText,
    submitText,
    start,
    confirm,
  }
}

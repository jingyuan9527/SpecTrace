import { onMounted, reactive, ref, watch, type Ref } from 'vue'

export interface PagedList<T> {
  records: T[]
  total: number
}

interface UsePagedListOptions<T, F extends object> {
  /** 数据加载函数：入参为分页 + 筛选，返回分页结果 */
  fetcher: (params: { current: number; size: number } & F) => Promise<PagedList<T>>
  /** 筛选条件初始值（不含分页字段） */
  filters?: () => F
  /** 每页条数，默认 20 */
  size?: number
  /** 是否 onMounted 自动加载，默认 true；由路由参数初始化的页面可关闭后手动调 load */
  immediate?: boolean
  /** 每次列表写入后的回调（用于详情行同步等副作用），静默轮询也会触发 */
  onLoaded?: (result: PagedList<T>) => void
}

/**
 * 后台分页列表的统一状态机：query（分页+筛选）、load、筛选变更重置页码。
 *
 * <p>筛选变更统一走 {@link search}：只在页码真的变化时交给 current 的 watch 触发加载，
 * 保证「非第一页切筛选」只发一次请求——旧实现 current-watch 与筛选-watch 会并发双 load，
 * 慢的那个后返回会覆盖新结果。seq 序号兜底其余并发场景（如连点刷新）。</p>
 */
export function usePagedList<T, F extends object = Record<string, never>>(
  options: UsePagedListOptions<T, F>,
) {
  const size = options.size ?? 20
  const list: Ref<T[]> = ref([])
  const total = ref(0)
  const loading = ref(false)
  const query = reactive({ current: 1, size, ...(options.filters?.() ?? ({} as F)) })

  let seq = 0

  /**
   * 拉取列表。silent=true 用于后台轮询：不点亮 loading、不吞掉既有列表，
   * 结果照常写入并触发 onLoaded。
   */
  async function load(silent = false) {
    const current = ++seq
    if (!silent) {
      loading.value = true
    }
    try {
      // reactive 展开 后 TS 无法证明仍是 F 的子类型，这里收口为一次显式断言
      const res = await options.fetcher({ ...query } as { current: number; size: number } & F)
      if (current !== seq) return
      list.value = res.records
      total.value = res.total
      options.onLoaded?.(res)
    } finally {
      if (!silent && current === seq) {
        loading.value = false
      }
    }
  }

  /** 筛选变更：重置页码并只发一次请求。 */
  function search() {
    if (query.current === 1) {
      load()
    } else {
      // 页码变化交给 current 的 watch 统一加载，避免双请求竞态
      query.current = 1
    }
  }

  if (options.immediate !== false) {
    onMounted(load)
  }
  watch(
    () => query.current,
    () => load(),
  )

  return { list, total, loading, query, load, search }
}

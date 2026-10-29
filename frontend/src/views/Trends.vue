<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { motion } from 'motion-v'
import { ArrowDownRight, ArrowRight, ArrowUpRight, GitCompareArrows, Share2, TrendingUp } from '@lucide/vue'
import { toast } from 'vue-sonner'
import { publicApi } from '@/api'
import { useECharts } from '@/composables/useECharts'
import { useInsight } from '@/composables/useInsight'
import InsightCard from '@/components/InsightCard.vue'
import SocialExportDialog from '@/components/SocialExportDialog.vue'
import DeviceAvatar from '@/components/DeviceAvatar.vue'
import ValueSparkline from '@/components/ValueSparkline.vue'
import { cssVar } from '@/lib/utils'
import type { Brand, EvolutionParam, EvolutionResult, GenerationMatrix, ParamDef, SeriesItem } from '@/types/api'
import { useThemeStore } from '@/stores/theme'
import { useCompareStore } from '@/stores/compare'
import { useAuthStore } from '@/stores/auth'
import { Alert, Badge, Button, Card, CardContent, Empty, Select, Skeleton, Tabs, type TabItem } from '@/components/ui'

/** 卡片视图：visiblePoints/viewTrend 与 SocialExportDialog 的入参契约同构，免去逐字段映射 */
interface ParamView extends EvolutionParam {
  visiblePoints: EvolutionParam['points']
  rate: number | null
  viewTrend: 'UP' | 'DOWN' | 'FLAT' | 'UNKNOWN'
}

const MAX_PARAMS = 8
/** 跨代默认加载 & 同代横比固定的关键参数 */
const DEFAULT_PARAM_KEYS = ['screen_size', 'battery_capacity', 'weight', 'launch_price']

const theme = useThemeStore()
const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
const compare = useCompareStore()

function parseId(value: unknown) {
  const raw = Array.isArray(value) ? value[0] : value
  const id = Number(raw)
  return Number.isFinite(id) && id > 0 ? id : undefined
}

/** 支持 paramDefIds=3,11,18 */
function parseIdList(value: unknown): number[] {
  const raw = Array.isArray(value) ? value.join(',') : String(value ?? '')
  return raw
    .split(',')
    .map((item) => Number(item))
    .filter((id) => Number.isFinite(id) && id > 0)
}

const mode = ref<'cross' | 'same'>('cross')
const brands = ref<Brand[]>([])
const defs = ref<ParamDef[]>([])
const allSeries = ref<SeriesItem[]>([])
const years = ref<number[]>([])

const brandId = ref<number>()
const seriesId = ref<number>()
const year = ref<number>()
const selectedDefIds = ref<number[]>([])
const focusDefId = ref<number | undefined>()

const crossData = ref<EvolutionResult | null>(null)
const matrix = ref<GenerationMatrix | null>(null)
const loading = ref(false)
const socialOpen = ref(false)
/** 首屏装载完成前不回写 URL，避免把默认值当成用户选择 */
const ready = ref(false)

/** 访客页只读登录态：管理员登录后多一个「去后台补抓」快捷入口，token 失效时会随 store 自动隐藏 */
const isAdmin = computed(() => auth.isLoggedIn)

const modeTabs: TabItem[] = [
  { value: 'cross', label: '跨代趋势' },
  { value: 'same', label: '同代横比' },
]

const brandOptions = computed(() => brands.value.map((b) => ({ label: b.name, value: b.id })))
const brandSeries = computed(() => allSeries.value.filter((s) => s.brandId === brandId.value))
const devices = computed(() => crossData.value?.devices ?? [])

const { insight, generating: insightGenerating, load: loadInsight, run: generateInsight } = useInsight(
  async () => {
    if (!seriesId.value || devices.value.length < 2) return null
    return publicApi.seriesInsight(seriesId.value, devices.value.map((d) => d.id), selectedDefIds.value)
  },
  () =>
    publicApi.generateSeriesInsight(
      seriesId.value!,
      devices.value.map((d) => d.id),
      selectedDefIds.value,
    ),
)

/** 涨跌幅与趋势的唯一算法：首末数值对比，文本参数无趋势可言。 */
function trendOf(param: EvolutionParam): { rate: number | null; viewTrend: ParamView['viewTrend'] } {
  const numbers = param.points
    .map((point) => point.value)
    .filter((value): value is number => typeof value === 'number' && Number.isFinite(value))
  if (!param.numeric || numbers.length < 2 || !numbers[0]) {
    return { rate: null, viewTrend: 'UNKNOWN' }
  }
  const rate = Math.round(((numbers[numbers.length - 1] - numbers[0]) / numbers[0]) * 10000) / 100
  if (Math.abs(rate) < 0.01) {
    return { rate: 0, viewTrend: 'FLAT' }
  }
  return { rate, viewTrend: rate > 0 ? 'UP' : 'DOWN' }
}

const params = computed<ParamView[]>(() =>
  (crossData.value?.params ?? []).map((param) => ({ ...param, visiblePoints: param.points, ...trendOf(param) })),
)

const focusParam = computed(() => params.value.find((param) => param.paramDefId === focusDefId.value))
const hasChart = computed(() => params.value.some((param) => param.numeric && param.points.length > 0))
const conflictCount = computed(
  () => params.value.reduce((sum, param) => sum + param.points.filter((p) => p.conflict).length, 0),
)

/** 截图里的代际摘要，替代缩略图横条（html2canvas 对图片与横向滚动容器测量不稳） */
const generationSummary = computed(() => devices.value.map((d) => `${yearOf(d.releaseDate)} ${d.name}`).join(' · '))

// ---------- 同代横比 ----------
/** 各参数在兄弟系列间的最值，用于中性标注「最高/最低」；只有 1 个有效值或全相等时不标 */
const extremaByParam = computed(() => {
  const map = new Map<number, { max: number; min: number }>()
  for (const param of matrix.value?.items[0]?.params ?? []) {
    const values = (matrix.value?.items ?? [])
      .map((item) => item.params.find((p) => p.paramDefId === param.paramDefId))
      .filter((p): p is NonNullable<typeof p> => !!p && p.numeric && typeof p.value === 'number')
      .map((p) => p.value as number)
    if (values.length > 1 && Math.max(...values) > Math.min(...values)) {
      map.set(param.paramDefId, { max: Math.max(...values), min: Math.min(...values) })
    }
  }
  return map
})

const sameDevices = computed(() => (matrix.value?.items ?? []).filter((item) => item.device))

function paramOfDef(defId: number) {
  return defs.value.find((def) => def.id === defId)
}

/** 同代卡片上的参数值：数值带单位、起售价带 ¥、文本显示原文。 */
function generationValue(itemParam: { paramDefId: number; value?: number | null; raw?: string | null }) {
  if (itemParam.value === null || itemParam.value === undefined) {
    return itemParam.raw || '—'
  }
  const def = paramOfDef(itemParam.paramDefId)
  if (def?.paramKey === 'launch_price') return priceOf(itemParam.value)
  return `${formatNumber(itemParam.value)}${def?.unit ?? ''}`
}

function extremaBadge(paramDefId: number, value?: number | null): 'max' | 'min' | null {
  if (typeof value !== 'number') return null
  const extrema = extremaByParam.value.get(paramDefId)
  if (!extrema) return null
  if (value === extrema.max) return 'max'
  if (value === extrema.min) return 'min'
  return null
}

function addAllToCompare() {
  const targets = sameDevices.value
  let added = 0
  let skipped = 0
  for (const item of targets) {
    const device = item.device!
    if (compare.add({ id: device.id, name: device.name, brandName: matrix.value?.brandName, thumbnail: device.thumbnail })) {
      added++
    } else {
      skipped++
    }
  }
  if (!added) {
    toast.warning('对比池已满或机型都已在对比中')
    return
  }
  toast.success(`已加入 ${added} 款机型${skipped ? `，${skipped} 款已在池中或超上限` : ''}`)
  router.push({ name: 'compare' })
}

// ---------- 数据加载 ----------

/** 品牌下默认系列：优先有机型的系列，避免一进来就撞上空态 */
function firstActiveSeries(brand: number | undefined) {
  const list = allSeries.value.filter((s) => s.brandId === brand)
  return list.find((s) => s.deviceCount > 0) ?? list[0]
}

async function loadYears() {
  years.value = brandId.value ? (await publicApi.years(brandId.value)).slice().sort((a, b) => b - a) : []
  if (years.value.length && !years.value.includes(year.value ?? 0)) {
    year.value = years.value[0]
  }
}

async function loadCross() {
  if (!seriesId.value) {
    crossData.value = null
    return
  }
  // 加载态会卸载图表容器，旧实例必须销毁，否则重挂后画在已脱离文档的 canvas 上
  charts.disposeAll()
  loading.value = true
  try {
    crossData.value = await publicApi.evolution(seriesId.value, selectedDefIds.value)
  } finally {
    loading.value = false
  }
  // URL 指定或默认聚焦：优先数值参数，避免一进来是文本列表
  if (!focusDefId.value || !crossData.value.params.some((p) => p.paramDefId === focusDefId.value)) {
    focusDefId.value =
      crossData.value.params.find((p) => p.numeric && p.points.length)?.paramDefId ??
      crossData.value.params[0]?.paramDefId
  }
  // 图表容器依赖 loading 结束后的真实布局，必须等渲染完成再初始化
  await nextTick()
  renderChart()
  await loadInsight()
}

async function loadSame() {
  if (!brandId.value || !year.value) {
    matrix.value = null
    return
  }
  loading.value = true
  try {
    matrix.value = await publicApi.generation(
      brandId.value,
      year.value,
      DEFAULT_PARAM_KEYS.map((key) => defs.value.find((d) => d.paramKey === key)?.id).filter(
        (id): id is number => typeof id === 'number',
      ),
    )
  } finally {
    loading.value = false
  }
}

function toggleParam(defId: number) {
  const index = selectedDefIds.value.indexOf(defId)
  if (index >= 0) {
    selectedDefIds.value.splice(index, 1)
    return
  }
  if (selectedDefIds.value.length >= MAX_PARAMS) {
    toast.warning(`最多同时查看 ${MAX_PARAMS} 个参数`)
    return
  }
  selectedDefIds.value.push(defId)
}

// ---------- 格式化 ----------

function formatNumber(value?: number | null) {
  if (value === null || value === undefined || !Number.isFinite(value)) return '—'
  return Number.isInteger(value) ? String(value) : value.toFixed(2).replace(/\.?0+$/, '')
}

function yearOf(date?: string | null) {
  return date ? date.slice(0, 4) : '—'
}

function priceOf(price?: number | null) {
  return price === null || price === undefined ? '—' : `¥${formatNumber(price)}`
}

function rateText(rate: number | null, viewTrend: ParamView['viewTrend']) {
  if (viewTrend === 'FLAT') return '持平'
  if (rate === null) return '—'
  const sign = rate > 0 ? '+' : ''
  return `${sign}${formatNumber(rate)}%`
}

function trendBadge(viewTrend: ParamView['viewTrend']) {
  if (viewTrend === 'UP') return 'warning' as const
  if (viewTrend === 'DOWN') return 'info' as const
  return 'secondary' as const
}

function trendIcon(viewTrend: ParamView['viewTrend']) {
  if (viewTrend === 'UP') return ArrowUpRight
  if (viewTrend === 'DOWN') return ArrowDownRight
  return ArrowRight
}

/** 文本参数卡与聚焦列表的值序列摘要，如 A15 → A18 → A19 */
function textSummary(param: EvolutionParam) {
  const values = param.points.map((point) => point.raw).filter(Boolean) as string[]
  if (!values.length) return '暂无数据'
  return values.slice(0, 4).join(' → ') + (values.length > 4 ? ' → …' : '')
}

// ---------- 聚焦主图 ----------

const charts = useECharts()

function renderChart() {
  const param = focusParam.value
  if (!param || !param.numeric || !param.visiblePoints.length) return

  const dark = theme.dark
  const axisColor = dark ? '#a1a1aa' : '#71717a'
  const splitColor = dark ? '#27272a' : '#f4f4f5'
  const color = cssVar('--chart-1', '#3b82f6')
  const names = devices.value.map((device) => device.name)
  const unit = param.unit ? ` ${param.unit}` : ''

  charts.render('focus', {
    backgroundColor: 'transparent',
    tooltip: {
      trigger: 'axis',
      backgroundColor: dark ? '#18181b' : '#ffffff',
      borderColor: dark ? '#27272a' : '#e4e4e7',
      textStyle: { color: dark ? '#fafafa' : '#18181b' },
      valueFormatter: (value: unknown) =>
        value === null || value === undefined ? '—' : `${formatNumber(Number(value))}${unit}`,
    },
    grid: { left: 56, right: 24, top: 28, bottom: 56 },
    xAxis: {
      type: 'category',
      data: names,
      axisLabel: {
        color: axisColor,
        rotate: names.length > 5 ? 30 : 0,
        formatter: (value: string) => (value.length > 10 ? `${value.slice(0, 10)}…` : value),
      },
      axisLine: { lineStyle: { color: dark ? '#3f3f46' : '#e4e4e7' } },
    },
    yAxis: {
      type: 'value',
      name: param.unit || '',
      scale: true,
      nameTextStyle: { color: axisColor },
      axisLabel: { color: axisColor },
      splitLine: { lineStyle: { color: splitColor } },
    },
    series: [
      {
        name: param.paramName,
        type: 'line',
        smooth: true,
        symbolSize: 8,
        connectNulls: true,
        data: param.visiblePoints.map((point) => point.value ?? null),
        itemStyle: { color },
        lineStyle: { width: 2 },
        areaStyle: { color, opacity: 0.14 },
        markPoint: {
          symbolSize: 40,
          label: { fontSize: 10, color: '#fff' },
          itemStyle: { color },
          data: [
            { type: 'max', name: '最高' },
            { type: 'min', name: '最低' },
          ],
        },
      },
    ],
  })
}

// ---------- 初始化与联动 ----------

onMounted(async () => {
  const [brandList, defList, seriesList] = await Promise.all([
    publicApi.brands(),
    publicApi.paramDefs(),
    publicApi.series(),
  ])
  brands.value = brandList
  defs.value = defList
  allSeries.value = seriesList

  // 聚焦参数可在 URL 指定（详情页参数行入口），但必须落在已加载集合内
  const urlFocus = parseId(route.query.paramDefId)
  const urlDefIds = parseIdList(route.query.paramDefIds).filter((id) => defList.some((d) => d.id === id))
  selectedDefIds.value = urlDefIds.length
    ? urlDefIds.slice(0, MAX_PARAMS)
    : DEFAULT_PARAM_KEYS.map((key) => defList.find((d) => d.paramKey === key)?.id).filter(
        (id): id is number => typeof id === 'number',
      )
  if (urlFocus && selectedDefIds.value.includes(urlFocus)) focusDefId.value = urlFocus

  mode.value = route.query.mode === 'same' ? 'same' : 'cross'

  // 分享链接优先还原 URL 里的系列（品牌跟随系列），其次按 URL/默认品牌取首个有机型的系列
  const urlSeries = allSeries.value.find((s) => s.id === parseId(route.query.seriesId))
  const urlBrand = brands.value.find((b) => b.id === parseId(route.query.brandId))
  if (urlSeries) {
    brandId.value = urlSeries.brandId
    seriesId.value = urlSeries.id
  } else {
    brandId.value = urlBrand?.id ?? brands.value[0]?.id
    seriesId.value = firstActiveSeries(brandId.value)?.id
  }

  const urlYear = parseId(route.query.year)
  await loadYears()
  if (urlYear && years.value.includes(urlYear)) year.value = urlYear

  if (mode.value === 'cross') await loadCross()
  else await loadSame()
  ready.value = true
  syncQuery()
})

/** 品牌切换后：系列落到该品牌首个有机型系列；同代年份重置到该品牌最新年份 */
watch(brandId, async () => {
  if (!brandId.value) return
  if (!brandSeries.value.some((s) => s.id === seriesId.value)) {
    seriesId.value = firstActiveSeries(brandId.value)?.id
  }
  await loadYears()
})

const defKey = computed(() => selectedDefIds.value.join(','))

watch([seriesId, defKey], () => {
  if (ready.value && mode.value === 'cross') loadCross()
})

watch([brandId, year], () => {
  if (ready.value && mode.value === 'same') loadSame()
})

watch(mode, async () => {
  if (!ready.value) return
  if (mode.value === 'same') {
    await loadSame()
    return
  }
  // 从同代切回跨代：期间可能换过品牌，跨代数据过期则重拉
  if (crossData.value?.seriesId !== seriesId.value) await loadCross()
  else renderChart()
})

// 聚焦参数切换只重画主图，不重新拉数据
watch(focusDefId, async () => {
  await nextTick()
  renderChart()
})

watch(
  () => theme.dark,
  async () => {
    await nextTick()
    renderChart()
  },
)

function syncQuery() {
  if (!ready.value) return
  const query: Record<string, string> = { mode: mode.value }
  if (brandId.value) query.brandId = String(brandId.value)
  if (mode.value === 'cross') {
    if (seriesId.value) query.seriesId = String(seriesId.value)
    if (defKey.value) query.paramDefIds = defKey.value
    if (focusDefId.value) query.paramDefId = String(focusDefId.value)
  } else if (year.value) {
    query.year = String(year.value)
  }
  router.replace({ query })
}

watch([mode, () => brandId.value, seriesId, year, defKey, focusDefId], syncQuery)
</script>

<template>
  <div class="space-y-5">
    <div class="flex flex-col gap-3 lg:flex-row lg:items-end lg:justify-between">
      <div>
        <h1 class="text-xl font-semibold tracking-tight sm:text-2xl">系列趋势</h1>
        <p class="text-sm text-muted-foreground">选系列看每一代的参数走势，或选年份看同代兄弟系列横比</p>
      </div>
      <div v-if="mode === 'cross'" class="flex flex-wrap gap-2">
        <Button variant="outline" :disabled="!crossData?.devices.length" @click="socialOpen = true">
          <Share2 />
          社媒导出
        </Button>
      </div>
      <Button v-else variant="outline" :disabled="!sameDevices.length" @click="addAllToCompare">
        <GitCompareArrows />
        全部加入对比
      </Button>
    </div>

    <Card>
      <CardContent class="space-y-3 p-4">
        <div class="grid grid-cols-1 gap-3 sm:grid-cols-2">
          <Select v-model="brandId" :options="brandOptions" placeholder="选择品牌" aria-label="品牌" />
          <Tabs v-model="mode" :items="modeTabs" class="justify-self-start" />
        </div>

        <!-- 系列 chips：跨代模式的横轴主体，单选 -->
        <div v-if="mode === 'cross'" class="space-y-2">
          <div class="flex flex-wrap items-center gap-1.5">
            <Button
              v-for="series in brandSeries"
              :key="series.id"
              size="sm"
              :variant="seriesId === series.id ? 'default' : 'outline'"
              :disabled="series.deviceCount === 0"
              @click="seriesId = series.id"
            >
              {{ series.name }}
            </Button>
          </div>
          <div class="flex flex-wrap items-center gap-1.5">
            <Button
              v-for="def in defs"
              :key="def.id"
              size="sm"
              :variant="selectedDefIds.includes(def.id) ? 'secondary' : 'ghost'"
              class="h-7 px-2 text-xs"
              @click="toggleParam(def.id)"
            >
              {{ def.paramName }}
            </Button>
          </div>
          <p class="text-xs text-muted-foreground">已加载 {{ selectedDefIds.length }} / {{ MAX_PARAMS }} 个参数，点击下方卡片可聚焦查看</p>
        </div>

        <!-- 年份 chips：同代模式的横轴主体 -->
        <div v-else class="space-y-2">
          <div class="flex flex-wrap items-center gap-1.5">
            <Button
              v-for="item in years"
              :key="item"
              size="sm"
              :variant="year === item ? 'default' : 'outline'"
              @click="year = item"
            >
              {{ item }}
            </Button>
          </div>
          <p class="text-xs text-muted-foreground">展示各系列在 {{ year ?? '—' }} 年的代表机型与关键参数</p>
        </div>
      </CardContent>
    </Card>

    <!-- ===================== 跨代趋势 ===================== -->
    <template v-if="mode === 'cross'">
      <div v-if="!brandSeries.length" class="rounded-xl border border-dashed">
        <Empty title="该品牌还没有系列" description="可在后台机型管理中为机型选择或新建系列，之后即可回看世代。">
          <template #icon><TrendingUp class="mb-1 h-8 w-8 text-muted-foreground/40" /></template>
        </Empty>
      </div>

      <Skeleton v-else-if="loading" class="h-96 rounded-xl" />

      <div v-else-if="!crossData?.devices.length" class="rounded-xl border border-dashed">
        <Empty title="该系列还没有机型" description="把机型归入此系列后，这里会按发布时间排成世代。">
          <template #icon><TrendingUp class="mb-1 h-8 w-8 text-muted-foreground/40" /></template>
        </Empty>
      </div>

      <motion.div
        v-else-if="crossData"
        :initial="{ opacity: 0, y: 8 }"
        :animate="{ opacity: 1, y: 0 }"
        :transition="{ duration: 0.3 }"
        class="space-y-4"
      >
        <Alert
          v-if="conflictCount"
          variant="warning"
          title="部分机型单位与参数定义不一致"
          :description="`共 ${conflictCount} 项，图表按原值换算后展示，建议核对数据来源`"
        />

        <div class="space-y-4 rounded-xl border bg-background p-4 sm:p-5">
          <!-- 横向滚动而非换行：世代是有序轴，换行会打乱阅读方向；不进截图（html2canvas 对图片与横向滚动容器测量不稳） -->
          <div class="flex gap-3 overflow-x-auto pb-1">
            <!-- 用 div 而非 button：html2canvas 会把表单控件按固有尺寸测量，导致卡片被压扁 -->
            <div
              v-for="device in devices"
              :key="device.id"
              role="button"
              tabindex="0"
              class="flex w-24 shrink-0 cursor-pointer flex-col items-center gap-1 rounded-lg border p-3 text-center transition-colors hover:bg-accent focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring sm:w-32"
              @click="router.push({ name: 'device-detail', params: { id: device.id } })"
              @keydown.enter.prevent="router.push({ name: 'device-detail', params: { id: device.id } })"
              @keydown.space.prevent="router.push({ name: 'device-detail', params: { id: device.id } })"
            >
              <DeviceAvatar
                :name="device.name"
                :thumbnail="device.thumbnail"
                :brand="crossData.brandName"
                size="sm"
                class="h-14 w-full rounded-lg bg-muted/50"
                img-class="h-14 w-auto object-contain"
              />
              <span class="w-full truncate text-xs font-medium">{{ device.name }}</span>
              <span class="text-[11px] text-muted-foreground">{{ yearOf(device.releaseDate) }}</span>
              <span class="text-xs font-semibold">{{ priceOf(device.launchPrice) }}</span>
            </div>
          </div>

          <!-- 海报产物在社媒导出弹窗内按固定画布重排，这里保留标题与聚焦图供页内阅读 -->
          <div class="capture-area space-y-3">
            <div class="text-center">
              <div class="flex flex-wrap items-center justify-center gap-2 font-semibold">
                <TrendingUp class="h-4 w-4 text-primary" />
                {{ crossData.brandName }} · {{ crossData.seriesName }}
              </div>
              <p class="mt-1 text-xs text-muted-foreground">共 {{ devices.length }} 代 · {{ generationSummary }}</p>
            </div>

            <!-- 稀疏系列（不足 2 代）不画单点图：单点折线无趋势可言，改为收录进度叙事引导补数 -->
            <div v-if="devices.length < 2" class="rounded-lg border border-dashed px-4 py-8 text-center">
              <p class="text-sm text-muted-foreground">该系列已收录 {{ devices.length }} 代，数据持续收录中</p>
              <p class="mt-1 text-xs text-muted-foreground">收录更多世代后，这里会展示参数趋势</p>
              <Button v-if="isAdmin" variant="outline" size="sm" class="mt-3" @click="router.push({ name: 'admin-agent-tasks' })">
                去后台补抓
              </Button>
            </div>

            <div v-else-if="!hasChart" class="rounded-lg border border-dashed px-4 py-8 text-center text-sm text-muted-foreground">
              所选参数暂无可绘制的数值，点击上方屏幕尺寸、电池容量等参数试试
            </div>

            <template v-else>
              <div class="flex flex-wrap items-center justify-between gap-2">
                <div class="flex items-center gap-1.5">
                  <h3 class="text-sm font-semibold">{{ focusParam?.paramName }}</h3>
                  <span v-if="focusParam?.unit" class="text-xs text-muted-foreground">（{{ focusParam.unit }}）</span>
                </div>
                <div class="flex items-center gap-1.5">
                  <Badge v-if="focusParam && focusParam.missingCount" variant="secondary">
                    缺失 {{ focusParam.missingCount }} 代
                  </Badge>
                  <Badge v-if="focusParam?.numeric" :variant="trendBadge(focusParam.viewTrend)">
                    <component :is="trendIcon(focusParam.viewTrend)" class="mr-0.5 h-3 w-3" />
                    {{ rateText(focusParam.rate, focusParam.viewTrend) }}
                  </Badge>
                </div>
              </div>

              <div v-if="focusParam?.numeric" :ref="charts.bind('focus')" class="h-72 w-full sm:h-80" />
              <div v-else class="space-y-1.5 py-2">
                <div
                  v-for="(point, index) in focusParam?.visiblePoints ?? []"
                  :key="point.deviceId"
                  class="flex items-center justify-between gap-3 text-sm"
                >
                  <span class="truncate text-muted-foreground">{{ devices[index]?.name }}</span>
                  <span class="truncate font-medium">{{ point.raw || '—' }}</span>
                </div>
              </div>
            </template>
          </div>
        </div>

        <!-- 参数卡网格：全览后聚焦，点卡切换上方主图 -->
        <div class="grid grid-cols-2 gap-3 lg:grid-cols-4">
          <div
            v-for="param in params"
            :key="param.paramDefId"
            role="button"
            tabindex="0"
            :aria-pressed="param.paramDefId === focusDefId"
            :aria-label="`聚焦 ${param.paramName}`"
            class="flex cursor-pointer flex-col gap-2 rounded-lg border p-3 transition-colors hover:bg-accent focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
            :class="param.paramDefId === focusDefId ? 'border-primary/60 bg-primary/5' : 'bg-background'"
            @click="focusDefId = param.paramDefId"
            @keydown.enter.prevent="focusDefId = param.paramDefId"
            @keydown.space.prevent="focusDefId = param.paramDefId"
          >
            <div class="flex items-start justify-between gap-2">
              <div class="min-w-0">
                <h3 class="truncate text-sm font-semibold">{{ param.paramName }}</h3>
                <span v-if="param.unit" class="text-xs text-muted-foreground">{{ param.unit }}</span>
              </div>
              <Badge v-if="param.numeric" :variant="trendBadge(param.viewTrend)" class="shrink-0">
                <component :is="trendIcon(param.viewTrend)" class="mr-0.5 h-3 w-3" />
                {{ rateText(param.rate, param.viewTrend) }}
              </Badge>
            </div>
            <ValueSparkline
              v-if="param.numeric && param.visiblePoints.length"
              :values="param.visiblePoints.map((point) => point.value ?? null)"
              :unit="param.unit"
              class="text-primary"
            />
            <p v-else-if="param.visiblePoints.length" class="truncate text-xs text-muted-foreground" :title="textSummary(param)">
              {{ textSummary(param) }}
            </p>
            <p v-else class="text-xs text-muted-foreground">暂无数据</p>
            <p v-if="param.missingCount" class="text-xs text-muted-foreground">缺失 {{ param.missingCount }} 代</p>
          </div>
        </div>

        <InsightCard
          v-if="insight"
          :insight="insight"
          :generating="insightGenerating"
          title="AI 世代回顾"
          button-text="生成回顾"
          footnote="AI 生成，仅供参考；数值以上方图表为准。"
          failed-text="回顾服务暂时不可用，稍后再试；不影响图表数据。"
          hint="按当前所选参数生成一段走势回顾，每次生成有成本，按需点击。"
          @generate="generateInsight"
        />
      </motion.div>
    </template>

    <!-- ===================== 同代横比 ===================== -->
    <template v-else>
      <div v-if="!years.length" class="rounded-xl border border-dashed">
        <Empty title="该品牌还没有机型" description="收录机型后，可按年份查看各系列的同代对比。">
          <template #icon><TrendingUp class="mb-1 h-8 w-8 text-muted-foreground/40" /></template>
        </Empty>
      </div>

      <Skeleton v-else-if="loading" class="h-80 rounded-xl" />

      <div v-else-if="!sameDevices.length" class="rounded-xl border border-dashed">
        <Empty
          title="该年份没有已归类的机型"
          description="换一个年份，或在后台把该年机型归入系列后再来看同代对比。"
        >
          <template #icon><TrendingUp class="mb-1 h-8 w-8 text-muted-foreground/40" /></template>
        </Empty>
      </div>

      <motion.div
        v-else-if="matrix"
        :initial="{ opacity: 0, y: 8 }"
        :animate="{ opacity: 1, y: 0 }"
        :transition="{ duration: 0.3 }"
      >
        <div class="rounded-xl border bg-background p-4 sm:p-5">
          <div class="mb-3 text-center">
            <div class="flex flex-wrap items-center justify-center gap-2 font-semibold">
              <TrendingUp class="h-4 w-4 text-primary" />
              {{ matrix.brandName }} · {{ matrix.year }} 年同代横比
            </div>
            <p class="mt-1 text-xs text-muted-foreground">共 {{ sameDevices.length }} 个系列有新机，点击卡片查看机型详情</p>
          </div>

          <div class="flex gap-3 overflow-x-auto pb-1">
            <div
              v-for="item in matrix.items"
              :key="item.seriesId"
              role="button"
              tabindex="0"
              class="flex w-56 shrink-0 cursor-pointer flex-col gap-2 rounded-lg border p-3 text-center transition-colors hover:bg-accent focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
              @click="item.device && router.push({ name: 'device-detail', params: { id: item.device.id } })"
              @keydown.enter.prevent="item.device && router.push({ name: 'device-detail', params: { id: item.device.id } })"
              @keydown.space.prevent="item.device && router.push({ name: 'device-detail', params: { id: item.device.id } })"
            >
              <Badge variant="outline" class="self-center">{{ item.seriesName }}</Badge>
              <template v-if="item.device">
                <DeviceAvatar
                  :name="item.device.name"
                  :thumbnail="item.device.thumbnail"
                  :brand="matrix.brandName"
                  size="sm"
                  class="h-16 w-full rounded-lg bg-muted/50"
                  img-class="h-16 w-auto object-contain"
                />
                <span class="w-full truncate text-sm font-semibold">{{ item.device.name }}</span>
                <span class="text-[11px] text-muted-foreground">{{ item.device.releaseDate || '发布日期未知' }}</span>
                <div class="space-y-1.5 border-t pt-2 text-left">
                  <div
                    v-for="param in item.params"
                    :key="param.paramDefId"
                    class="flex items-center justify-between gap-2 text-xs"
                  >
                    <span class="shrink-0 text-muted-foreground">{{ param.paramName }}</span>
                    <span class="flex items-center gap-1 font-medium">
                      {{ generationValue(param) }}
                      <Badge v-if="extremaBadge(param.paramDefId, param.value) === 'max'" variant="secondary" class="h-4 px-1 text-[10px]">最高</Badge>
                      <Badge v-else-if="extremaBadge(param.paramDefId, param.value) === 'min'" variant="secondary" class="h-4 px-1 text-[10px]">最低</Badge>
                    </span>
                  </div>
                </div>
              </template>
              <p v-else class="py-6 text-xs text-muted-foreground">该年无新机</p>
            </div>
          </div>
        </div>
      </motion.div>
    </template>

    <SocialExportDialog
      v-model:open="socialOpen"
      :data="crossData"
      :devices="devices"
      :params="params"
      :focus-param-def-id="focusDefId"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { motion } from 'motion-v'
import { ArrowDownRight, ArrowRight, ArrowUpRight, Camera, Smartphone, TrendingUp } from '@lucide/vue'
import { toast } from 'vue-sonner'
import { publicApi } from '@/api'
import { useECharts } from '@/composables/useECharts'
import { useInsight } from '@/composables/useInsight'
import InsightCard from '@/components/InsightCard.vue'
import { cssVar } from '@/lib/utils'
import type { Brand, EvolutionParam, EvolutionPoint, EvolutionResult, InsightResult, ParamDef, SeriesItem } from '@/types/api'
import { useThemeStore } from '@/stores/theme'
import { Alert, Badge, Button, Card, CardContent, Checkbox, Empty, Select, Skeleton } from '@/components/ui'
import ImageWithFallback from '@/components/ImageWithFallback.vue'

/** 图表使用的参数视图：points 已按世代区间裁剪，涨跌幅与缺失数按可见区间重算 */
interface ParamView extends EvolutionParam {
  visiblePoints: EvolutionPoint[]
  visibleMissing: number
  rate: number | null
  viewTrend: 'UP' | 'DOWN' | 'FLAT' | 'UNKNOWN'
}

const MAX_PARAMS = 8
/** 默认对比参数：屏幕尺寸、电池容量、重量、起售价 */
const DEFAULT_PARAM_KEYS = ['screen_size', 'battery_capacity', 'weight', 'launch_price']

const theme = useThemeStore()
const route = useRoute()
const router = useRouter()

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


const brands = ref<Brand[]>([])
const defs = ref<ParamDef[]>([])
const allSeries = ref<SeriesItem[]>([])
const brandId = ref<number>()
const seriesId = ref<number>()
const selectedDefIds = ref<number[]>([])
const data = ref<EvolutionResult | null>(null)
const loading = ref(false)
const capturing = ref(false)
const fromIndex = ref(0)
const toIndex = ref(0)
/** 首屏数据装载完成前不回写 URL，避免把默认值当成用户选择 */
const ready = ref(false)

const { insight, generating: insightGenerating, load: loadInsight, run: generateInsight } = useInsight(
  async () => {
    if (!seriesId.value || visibleDevices.value.length < 2) return null
    return publicApi.seriesInsight(
      seriesId.value,
      visibleDevices.value.map((device) => device.id),
      selectedDefIds.value,
    )
  },
  () =>
    publicApi.generateSeriesInsight(
      seriesId.value!,
      visibleDevices.value.map((device) => device.id),
      selectedDefIds.value,
    ),
)
const brandOptions = computed(() => brands.value.map((b) => ({ label: b.name, value: b.id })))
const brandSeries = computed(() => allSeries.value.filter((s) => s.brandId === brandId.value))

const seriesOptions = computed(() =>
  brandSeries.value.map((s) => ({
    label: `${s.name}（${s.deviceCount} 代${s.startYear ? ` · ${s.startYear}-${s.endYear}` : ''}）`,
    value: s.id,
    disabled: s.deviceCount === 0,
  })),
)

const devices = computed(() => data.value?.devices ?? [])

const generationOptions = computed(() =>
  devices.value.map((device, index) => ({ label: `${index + 1}. ${device.name}`, value: index })),
)

const visibleDevices = computed(() => devices.value.slice(fromIndex.value, toIndex.value + 1))

/** 截图里的代际摘要，替代缩略图横条（html2canvas 对图片与横向滚动容器测量不稳） */
const generationSummary = computed(() =>
  visibleDevices.value.map((device) => `${yearOf(device.releaseDate)} ${device.name}`).join(' · '),
)

/** 区间裁剪后必须按可见代际重算涨跌幅，否则徽标与折线互相矛盾 */
const visibleParams = computed<ParamView[]>(() =>
  (data.value?.params ?? []).map((param) => {
    const visiblePoints = param.points.slice(fromIndex.value, toIndex.value + 1)
    const { rate, trend } = trendOf(visiblePoints, param.numeric)
    // 全系列无数据时后端给空点列，缺失数仍以整段世代为准
    const visibleMissing = visiblePoints.length
      ? visiblePoints.filter((point) => !point.raw).length
      : param.missingCount
    return { ...param, visiblePoints, visibleMissing, rate, viewTrend: trend }
  }),
)

const hasChart = computed(() => visibleParams.value.some((param) => param.numeric))
const conflictCount = computed(
  () => visibleParams.value.reduce((sum, param) => sum + param.visiblePoints.filter((p) => p.conflict).length, 0),
)

/** 涨跌幅与趋势的唯一算法：后端不再计算，因为只有前端知道当前裁剪出的世代区间。 */
function trendOf(points: EvolutionPoint[], numeric: boolean) {
  const numbers = points
    .map((point) => point.value)
    .filter((value): value is number => typeof value === 'number' && Number.isFinite(value))
  if (!numeric || numbers.length < 2 || !numbers[0]) {
    return { rate: null, trend: 'UNKNOWN' as const }
  }
  const first = numbers[0]
  const last = numbers[numbers.length - 1]
  const rate = Math.round(((last - first) / first) * 10000) / 100
  if (Math.abs(rate) < 0.01) {
    return { rate: 0, trend: 'FLAT' as const }
  }
  return { rate, trend: rate > 0 ? ('UP' as const) : ('DOWN' as const) }
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

async function load() {
  if (!seriesId.value) {
    data.value = null
    return
  }
  // 加载态会卸载图表容器，旧实例必须销毁，否则重挂后画在已脱离文档的 canvas 上
  charts.disposeAll()
  loading.value = true
  try {
    data.value = await publicApi.evolution(seriesId.value, selectedDefIds.value)
    fromIndex.value = 0
    toIndex.value = Math.max(devices.value.length - 1, 0)
  } finally {
    loading.value = false
  }
  // 图表容器依赖 v-if 结束后的真实布局，必须等渲染完成再初始化
  await nextTick()
  renderCharts()
  await loadInsight()
}

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

function rateText(rate: number | null, trend: ParamView['viewTrend']) {
  if (trend === 'FLAT') return '持平'
  if (rate === null) return '—'
  const sign = rate > 0 ? '+' : ''
  return `${sign}${formatNumber(rate)}%`
}

function trendBadge(trend: ParamView['viewTrend']) {
  if (trend === 'UP') return 'warning' as const
  if (trend === 'DOWN') return 'info' as const
  return 'secondary' as const
}

function trendIcon(trend: ParamView['viewTrend']) {
  if (trend === 'UP') return ArrowUpRight
  if (trend === 'DOWN') return ArrowDownRight
  return ArrowRight
}

// ---------- 图表 ----------
const charts = useECharts()

function chartOption(param: ParamView) {
  const dark = theme.dark
  const axisColor = dark ? '#a1a1aa' : '#71717a'
  const splitColor = dark ? '#27272a' : '#f4f4f5'
  const color = cssVar('--chart-1', '#3b82f6')
  const names = visibleDevices.value.map((device) => device.name)
  const unit = param.unit ? ` ${param.unit}` : ''

  return {
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
  }
}

function renderCharts() {
  const drawable = visibleParams.value.filter((param) => param.numeric)
  charts.prune(new Set(drawable.map((param) => param.paramDefId)))
  for (const param of drawable) {
    charts.render(param.paramDefId, chartOption(param))
  }
}

const captureRef = ref<HTMLElement>()

async function exportImage() {
  if (!captureRef.value) return
  capturing.value = true
  await nextTick()
  try {
    // 动态加载：html2canvas 约 200KB，只有真正导出时才需要
    const html2canvas = (await import('html2canvas')).default
    const canvas = await html2canvas(captureRef.value, {
      backgroundColor: theme.dark ? '#18181b' : '#ffffff',
      scale: 2,
    })
    const link = document.createElement('a')
    link.download = `spectrace-evolution-${seriesId.value ?? 'series'}-${Date.now()}.png`
    link.href = canvas.toDataURL('image/png')
    link.click()
    toast.success('截图已下载')
  } catch {
    toast.error('导出失败，请重试')
  } finally {
    capturing.value = false
  }
}

onMounted(async () => {
  const [brandList, defList, seriesList] = await Promise.all([
    publicApi.brands(),
    publicApi.paramDefs(),
    publicApi.series(),
  ])
  brands.value = brandList
  defs.value = defList
  allSeries.value = seriesList

  const urlDefIds = parseIdList(route.query.paramDefIds).filter((id) => defList.some((d) => d.id === id))
  selectedDefIds.value = urlDefIds.length
    ? urlDefIds.slice(0, MAX_PARAMS)
    : DEFAULT_PARAM_KEYS.map((key) => defList.find((d) => d.paramKey === key)?.id).filter(
        (id): id is number => typeof id === 'number',
      )

  // 分享链接优先还原 URL 里的系列，其次取首个品牌的首个系列
  const urlSeries = allSeries.value.find((s) => s.id === parseId(route.query.seriesId))
  const fallback = allSeries.value.find((s) => s.brandId === brands.value[0]?.id) ?? allSeries.value[0]
  const target = urlSeries ?? fallback
  if (target) {
    brandId.value = target.brandId
    seriesId.value = target.id
  }

  await load()
  ready.value = true
  syncQuery()
})

/** 品牌切换后原系列不在此品牌下，自动落到该品牌第一个系列 */
watch(brandId, (id) => {
  if (!id) return
  const list = allSeries.value.filter((s) => s.brandId === id)
  if (!list.some((s) => s.id === seriesId.value)) {
    seriesId.value = list[0]?.id
  }
})

const defKey = computed(() => selectedDefIds.value.join(','))
watch([seriesId, defKey], () => {
  load()
})

// 起始世代超过结束世代时互相纠正，保证区间始终有效
watch(fromIndex, (value) => {
  if (value > toIndex.value) toIndex.value = value
})
watch(toIndex, (value) => {
  if (value < fromIndex.value) fromIndex.value = value
})

watch([fromIndex, toIndex], async () => {
  await nextTick()
  renderCharts()
  // load() 自己会拉一次，这里只处理用户后续调整区间
  if (ready.value && !loading.value) void loadInsight()
})

watch(
  () => theme.dark,
  async () => {
    await nextTick()
    renderCharts()
  },
)

function syncQuery() {
  if (!ready.value) return
  const query: Record<string, string> = {}
  if (seriesId.value) query.seriesId = String(seriesId.value)
  if (defKey.value) query.paramDefIds = defKey.value
  router.replace({ query })
}

watch([seriesId, defKey], syncQuery)

</script>

<template>
  <div class="space-y-5">
    <div class="flex flex-col gap-3 lg:flex-row lg:items-end lg:justify-between">
      <div>
        <h1 class="text-xl font-semibold tracking-tight sm:text-2xl">世代回顾</h1>
        <p class="text-sm text-muted-foreground">按产品线回看每一代的参数走势，快速定位升级与倒退</p>
      </div>
      <Button variant="outline" :disabled="!data?.devices.length || capturing" @click="exportImage">
        <Camera />
        截图导出
      </Button>
    </div>

    <Card>
      <CardContent class="space-y-3 p-4">
        <div class="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-4">
          <Select v-model="brandId" :options="brandOptions" placeholder="选择品牌" aria-label="品牌" />
          <Select v-model="seriesId" :options="seriesOptions" placeholder="选择系列" aria-label="系列" />
          <Select v-model="fromIndex" :options="generationOptions" placeholder="起始世代" aria-label="起始世代" />
          <Select v-model="toIndex" :options="generationOptions" placeholder="结束世代" aria-label="结束世代" />
        </div>

        <div class="space-y-2">
          <div class="flex max-h-36 flex-wrap items-center gap-x-4 gap-y-2 overflow-y-auto pr-1">
            <!-- 不用 label 包裹 Checkbox：点击会冒泡到 label 再转发一次，导致切换两次而失效 -->
            <div v-for="def in defs" :key="def.id" class="flex items-center gap-1.5">
              <Checkbox
                :id="`param-${def.id}`"
                :model-value="selectedDefIds.includes(def.id)"
                @update:model-value="toggleParam(def.id)"
              />
              <label :for="`param-${def.id}`" class="cursor-pointer text-sm text-muted-foreground hover:text-foreground">
                {{ def.paramName }}
              </label>
            </div>
          </div>
          <p class="text-xs text-muted-foreground">
            已选 {{ selectedDefIds.length }} / {{ MAX_PARAMS }} 个参数，勾选后图表即时刷新
          </p>
        </div>
      </CardContent>
    </Card>

    <div v-if="!seriesId" class="rounded-xl border border-dashed">
      <Empty title="该品牌还没有系列" description="可在后台机型管理中为机型选择或新建系列，之后即可回看世代。">
        <template #icon><TrendingUp class="mb-1 h-8 w-8 text-muted-foreground/40" /></template>
      </Empty>
    </div>

    <Skeleton v-else-if="loading" class="h-96 rounded-xl" />

    <div v-else-if="!data?.devices.length" class="rounded-xl border border-dashed">
      <Empty title="该系列还没有机型" description="把机型归入此系列后，这里会按发布时间排成世代。">
        <template #icon><TrendingUp class="mb-1 h-8 w-8 text-muted-foreground/40" /></template>
      </Empty>
    </div>

    <motion.div
      v-else-if="data"
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
        <div class="text-center">
          <div class="flex flex-wrap items-center justify-center gap-2 font-semibold">
            <TrendingUp class="h-4 w-4 text-primary" />
            {{ data.brandName }} · {{ data.seriesName }}
          </div>
          <p class="mt-1 text-xs text-muted-foreground">
            共 {{ data.devices.length }} 代 · 当前展示第 {{ fromIndex + 1 }} - {{ toIndex + 1 }} 代
          </p>
        </div>

        <!-- 横向滚动而非换行：世代是有序轴，换行会打乱阅读方向；小屏收窄卡片让 3 代可见，提示还能继续滑 -->
        <div class="flex gap-3 overflow-x-auto pb-1">
          <!-- 用 div 而非 button：html2canvas 会把表单控件按固有尺寸测量，导致卡片被压扁 -->
          <div
            v-for="device in visibleDevices"
            :key="device.id"
            role="button"
            tabindex="0"
            class="flex w-24 shrink-0 cursor-pointer flex-col items-center gap-1 rounded-lg border p-3 text-center transition-colors hover:bg-accent focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring sm:w-32"
            @click="router.push({ name: 'device-detail', params: { id: device.id } })"
            @keydown.enter.prevent="router.push({ name: 'device-detail', params: { id: device.id } })"
            @keydown.space.prevent="router.push({ name: 'device-detail', params: { id: device.id } })"
          >
            <ImageWithFallback
              :src="device.thumbnail"
              :alt="device.name"
              img-class="h-14 w-auto object-contain"
            >
              <template #fallback><Smartphone class="h-9 w-9 text-muted-foreground/40" /></template>
            </ImageWithFallback>
            <span class="w-full truncate text-xs font-medium">{{ device.name }}</span>
            <span class="text-[11px] text-muted-foreground">{{ yearOf(device.releaseDate) }}</span>
            <span class="text-xs font-semibold">{{ priceOf(device.launchPrice) }}</span>
          </div>
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
        hint="按当前所选参数与世代区间生成一段走势回顾，每次生成有成本，按需点击。"
        @generate="generateInsight"
      />

      <!-- 截图只包含标题与图表：缩略图与横向滚动容器会让 html2canvas 测量失准，代际用文本摘要表达 -->
      <div ref="captureRef" class="capture-area space-y-4 rounded-xl border bg-background p-4 sm:p-5">
        <div class="text-center">
          <div class="flex flex-wrap items-center justify-center gap-2 font-semibold">
            <TrendingUp class="h-4 w-4 text-primary" />
            {{ data.brandName }} · {{ data.seriesName }}
          </div>
          <p class="mt-1 text-xs text-muted-foreground">共 {{ data.devices.length }} 代 · {{ generationSummary }}</p>
        </div>

        <div v-if="!hasChart" class="rounded-lg border border-dashed px-4 py-8 text-center text-sm text-muted-foreground">
          所选参数暂无可绘制的数值，勾选屏幕尺寸、电池容量等数值型参数试试
        </div>

        <div v-else class="grid grid-cols-1 gap-4 lg:grid-cols-2">
          <div v-for="param in visibleParams" :key="param.paramDefId" class="rounded-lg border p-4">
            <div class="mb-2 flex flex-wrap items-center justify-between gap-2">
              <div class="flex items-center gap-1.5">
                <h3 class="text-sm font-semibold">{{ param.paramName }}</h3>
                <span v-if="param.unit" class="text-xs text-muted-foreground">（{{ param.unit }}）</span>
              </div>
              <div class="flex items-center gap-1.5">
                <Badge v-if="param.visibleMissing" variant="secondary">缺失 {{ param.visibleMissing }} 代</Badge>
                <Badge v-if="param.numeric" :variant="trendBadge(param.viewTrend)">
                  <component :is="trendIcon(param.viewTrend)" class="mr-0.5 h-3 w-3" />
                  {{ rateText(param.rate, param.viewTrend) }}
                </Badge>
              </div>
            </div>

            <div v-if="param.numeric" :ref="charts.bind(param.paramDefId)" class="h-56 w-full" />
            <div v-else class="space-y-1.5 py-2">
              <div
                v-for="(point, index) in param.visiblePoints"
                :key="point.deviceId"
                class="flex items-center justify-between gap-3 text-sm"
              >
                <span class="truncate text-muted-foreground">{{ visibleDevices[index]?.name }}</span>
                <span class="truncate font-medium">{{ point.raw || '—' }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </motion.div>
  </div>
</template>

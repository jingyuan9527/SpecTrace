<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import * as echarts from 'echarts/core'
import { BarChart, LineChart as EChartsLineChart } from 'echarts/charts'
import { GridComponent, LegendComponent, MarkPointComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import type { EChartsType } from 'echarts/core'
import html2canvas from 'html2canvas'
import { motion } from 'motion-v'
import { Download, FileSpreadsheet, LineChart, TrendingUp } from '@lucide/vue'
import { toast } from 'vue-sonner'
import { publicApi } from '@/api'
import { useThemeStore } from '@/stores/theme'
import { Alert, Button, Card, CardContent, Combobox, Empty, Skeleton, Tabs, type TabItem } from '@/components/ui'

interface Brand {
  id: number
  name: string
}

interface ParamDef {
  id: number
  paramName: string
  unit?: string
}

interface HistoryPoint {
  deviceId: number
  deviceName: string
  releaseDate: string
  value: string
}

interface HistorySeries {
  brandId: number
  brandName: string
  points: HistoryPoint[]
}

interface HistoryResponse {
  paramDefId: number
  paramName: string
  unit?: string
  numeric: boolean
  series: HistorySeries[]
}

const MAX_BRANDS = 4
const theme = useThemeStore()
const route = useRoute()
const router = useRouter()

/** 路由 query 里的 id 是字符串，统一转正整数。 */
function parseId(value: unknown) {
  const raw = Array.isArray(value) ? value[0] : value
  const id = Number(raw)
  return Number.isFinite(id) && id > 0 ? id : undefined
}

/** 支持 brandIds=1,2,3；旧链接的单个 brandId 仍然兼容。 */
function parseIdList(value: unknown): number[] {
  const raw = Array.isArray(value) ? value.join(',') : String(value ?? '')
  return raw
    .split(',')
    .map((item) => Number(item))
    .filter((id) => Number.isFinite(id) && id > 0)
    .slice(0, MAX_BRANDS)
}

// 按需注册图表能力，避免把整个 ECharts 打进 History 页面 chunk
echarts.use([
  EChartsLineChart,
  BarChart,
  GridComponent,
  TooltipComponent,
  LegendComponent,
  MarkPointComponent,
  CanvasRenderer,
])

const brands = ref<Brand[]>([])
const defs = ref<ParamDef[]>([])
const initialBrandIds = parseIdList(route.query.brandIds)
const legacyBrandId = parseId(route.query.brandId)
const brandIds = ref<number[]>(initialBrandIds.length ? initialBrandIds : legacyBrandId ? [legacyBrandId] : [])
const paramDefId = ref<number | undefined>(parseId(route.query.paramDefId))
const view = ref('chart')
const data = ref<HistoryResponse | null>(null)
const loading = ref(false)
const capturing = ref(false)

const views: TabItem[] = [
  { value: 'chart', label: '图表' },
  { value: 'table', label: '表格' },
]

const chartRef = ref<HTMLElement>()
const captureRef = ref<HTMLElement>()
let chart: EChartsType | null = null

const defOptions = ref<{ label: string; value: number }[]>([])
const brandKey = computed(() => brandIds.value.join(','))
const totalPoints = computed(() => data.value?.series.reduce((sum, item) => sum + item.points.length, 0) ?? 0)
const selectedBrandNames = computed(() =>
  data.value?.series.length
    ? data.value.series.map((item) => item.brandName).join(' / ')
    : brandIds.value
        .map((id) => brands.value.find((b) => b.id === id)?.name)
        .filter(Boolean)
        .join(' / '),
)

function toggleBrand(id: number) {
  const index = brandIds.value.indexOf(id)
  if (index >= 0) {
    brandIds.value.splice(index, 1)
    return
  }
  if (brandIds.value.length >= MAX_BRANDS) {
    toast.warning(`最多同时对比 ${MAX_BRANDS} 个品牌`)
    return
  }
  brandIds.value.push(id)
}

async function load() {
  if (!brandIds.value.length || !paramDefId.value) {
    data.value = null
    return
  }
  loading.value = true
  try {
    data.value = await publicApi.history(brandIds.value, paramDefId.value)
  } finally {
    loading.value = false
  }
  // 必须等 loading 关闭、图表容器真正挂载后再初始化 ECharts
  if (view.value === 'chart') {
    await nextTick()
    renderChart()
  }
}

function cssVar(name: string, fallback: string) {
  const value = getComputedStyle(document.documentElement).getPropertyValue(name).trim()
  return value ? `hsl(${value})` : fallback
}

/** 所有品牌共用一套日期轴：同一发布日期的机型落在同一刻度上。 */
function axisDates(series: HistorySeries[]) {
  return [...new Set(series.flatMap((item) => item.points.map((point) => point.releaseDate || '')))]
    .filter(Boolean)
    .sort()
}

function seriesColor(index: number) {
  return cssVar(`--chart-${(index % 5) + 1}`, '#3b82f6')
}

function renderChart() {
  if (!chartRef.value || !data.value || !data.value.series.length) return
  if (!chart) chart = echarts.init(chartRef.value)

  const dark = theme.dark
  const info = data.value
  const dates = axisDates(info.series)
  const numeric = info.numeric
  const axisColor = dark ? '#a1a1aa' : '#71717a'
  const splitColor = dark ? '#27272a' : '#f4f4f5'

  const series = info.series.map((item, index) => {
    const byDate = new Map(item.points.map((point) => [point.releaseDate || '', point]))
    const values = dates.map((date) => {
      const point = byDate.get(date)
      if (!point) return null
      return numeric ? Number(point.value) : point.value
    })
    const color = seriesColor(index)
    return {
      name: item.brandName,
      type: 'line',
      smooth: true,
      symbolSize: 7,
      connectNulls: true,
      data: values,
      itemStyle: { color },
      lineStyle: { width: 2 },
      areaStyle: info.series.length === 1 && numeric ? { color, opacity: 0.16 } : undefined,
      // 多品牌叠加上极值标注，快速定位最高/最低
      markPoint: numeric
        ? {
            symbolSize: 42,
            label: { fontSize: 10, color: '#fff' },
            itemStyle: { color },
            data: [
              { type: 'max', name: '最高' },
              { type: 'min', name: '最低' },
            ],
          }
        : undefined,
    }
  })

  chart.setOption(
    {
      backgroundColor: 'transparent',
      color: info.series.map((_, index) => seriesColor(index)),
      legend: info.series.length > 1
        ? { top: 0, textStyle: { color: axisColor }, icon: 'roundRect', itemWidth: 12, itemHeight: 4 }
        : undefined,
      tooltip: {
        trigger: 'axis',
        backgroundColor: dark ? '#18181b' : '#ffffff',
        borderColor: dark ? '#27272a' : '#e4e4e7',
        textStyle: { color: dark ? '#fafafa' : '#18181b' },
        valueFormatter: (value: unknown) => (value === null || value === undefined ? '—' : String(value)),
      },
      grid: { left: 56, right: 28, top: info.series.length > 1 ? 48 : 24, bottom: 72 },
      xAxis: {
        type: 'category',
        data: dates,
        axisLabel: {
          rotate: 30,
          color: axisColor,
          formatter: (value: string) => value.slice(0, 7),
        },
        axisLine: { lineStyle: { color: dark ? '#3f3f46' : '#e4e4e7' } },
      },
      yAxis: {
        type: numeric ? 'value' : 'category',
        name: info.unit || '',
        nameTextStyle: { color: axisColor },
        axisLabel: { color: axisColor },
        splitLine: { lineStyle: { color: splitColor } },
      },
      series,
    },
    true,
  )

  chart.resize()
  // 容器可能在入场动画结束后才完成布局，补一次 resize 触发重绘
  window.setTimeout(() => chart?.resize(), 120)
}

onMounted(async () => {
  const [brandList, defList] = await Promise.all([publicApi.brands(), publicApi.paramDefs()])
  brands.value = brandList
  defs.value = defList
  defOptions.value = defList.map((d) => ({ label: d.paramName, value: d.id }))
  // 分享链接进入时选项加载完毕后再拉数据
  if (brandIds.value.length && paramDefId.value) await load()
})

watch([brandKey, paramDefId], load)

// 选择变化同步到地址栏，便于分享与刷新还原
watch([brandKey, paramDefId], () => {
  const query: Record<string, string> = {}
  if (brandKey.value) query.brandIds = brandKey.value
  if (paramDefId.value) query.paramDefId = String(paramDefId.value)
  router.replace({ query })
})
watch(view, async () => {
  if (view.value === 'chart') {
    await nextTick()
    renderChart()
  }
})
watch(
  () => theme.dark,
  async () => {
    await nextTick()
    renderChart()
  },
)

function onResize() {
  chart?.resize()
}
window.addEventListener('resize', onResize)
onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  chart?.dispose()
  chart = null
})

async function exportImage() {
  if (!captureRef.value) return
  capturing.value = true
  await nextTick()
  try {
    const canvas = await html2canvas(captureRef.value, { backgroundColor: '#ffffff', scale: 2 })
    const link = document.createElement('a')
    link.download = `spectrace-history-${Date.now()}.png`
    link.href = canvas.toDataURL('image/png')
    link.click()
    toast.success('截图已下载')
  } catch {
    toast.error('导出失败，请重试')
  } finally {
    capturing.value = false
  }
}

function csvCell(value: unknown) {
  const text = String(value ?? '')
  return /[",\r\n]/.test(text) ? `"${text.replace(/"/g, '""')}"` : text
}

/** 导出当前已加载的全部品牌数据，Excel 直接可读（带 BOM）。 */
function exportCsv() {
  if (!data.value) return
  const rows = [['品牌', '机型', '发布时间', data.value.paramName + (data.value.unit ? `（${data.value.unit}）` : '')]]
  for (const item of data.value.series) {
    for (const point of item.points) {
      rows.push([item.brandName, point.deviceName, point.releaseDate || '', point.value])
    }
  }
  const csv = '\uFEFF' + rows.map((cols) => cols.map(csvCell).join(',')).join('\r\n')
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `spectrace-history-${data.value.paramName}-${Date.now()}.csv`
  link.click()
  URL.revokeObjectURL(url)
  toast.success('CSV 已导出')
}
</script>

<template>
  <div class="space-y-5">
    <div class="flex flex-col gap-3 lg:flex-row lg:items-end lg:justify-between">
      <div>
        <h1 class="text-xl font-semibold tracking-tight sm:text-2xl">参数历史演变</h1>
        <p class="text-sm text-muted-foreground">同一参数可叠加最多 {{ MAX_BRANDS }} 个品牌，观察历代机型的走势</p>
      </div>
      <div class="flex flex-wrap gap-2">
        <Button variant="outline" :disabled="!totalPoints" @click="exportCsv">
          <FileSpreadsheet />
          导出 CSV
        </Button>
        <Button variant="outline" :disabled="!totalPoints" @click="exportImage">
          <Download />
          截图导出
        </Button>
      </div>
    </div>

    <Card>
      <CardContent class="space-y-3 p-4">
        <div class="grid grid-cols-1 gap-3 sm:grid-cols-2">
          <Combobox v-model="paramDefId" :options="defOptions" placeholder="选择参数" search-placeholder="搜索参数…" />
          <Tabs v-model="view" :items="views" class="justify-self-start" />
        </div>
        <div class="space-y-2">
          <div class="flex flex-wrap items-center gap-1.5">
            <Button
              v-for="brand in brands"
              :key="brand.id"
              size="sm"
              :variant="brandIds.includes(brand.id) ? 'default' : 'outline'"
              @click="toggleBrand(brand.id)"
            >
              {{ brand.name }}
            </Button>
          </div>
          <p class="text-xs text-muted-foreground">已选 {{ brandIds.length }} / {{ MAX_BRANDS }} 个品牌，点击可切换</p>
        </div>
      </CardContent>
    </Card>

    <div v-if="!brandIds.length || totalPoints === 0" class="rounded-xl border border-dashed">
      <Empty
        title="选择品牌与参数后查看演变"
        description="例如：对比 Apple 与华为的屏幕尺寸演变，最多同时叠加 4 个品牌。"
      >
        <template #icon><TrendingUp class="mb-1 h-8 w-8 text-muted-foreground/40" /></template>
      </Empty>
    </div>

    <Skeleton v-else-if="loading" class="h-96 rounded-xl" />

    <motion.div v-else :initial="{ opacity: 0, y: 8 }" :animate="{ opacity: 1, y: 0 }" :transition="{ duration: 0.3 }">
      <Alert
        v-if="data && !data.numeric"
        variant="info"
        class="mb-3"
        title="部分取值不是纯数字"
        description="图表按文本比对，无法计算数值趋势；可点击「导出 CSV」查看原始值。"
      />

      <div ref="captureRef" class="capture-area rounded-xl border bg-background p-5">
        <div class="mb-2 text-center">
          <div class="flex flex-wrap items-center justify-center gap-2 font-semibold">
            <LineChart class="h-4 w-4 text-primary" />
            {{ selectedBrandNames }} · {{ data?.paramName }} 演变
          </div>
          <p class="mt-1 text-xs text-muted-foreground">共 {{ totalPoints }} 个机型数据点</p>
        </div>

        <div v-show="view === 'chart'" ref="chartRef" class="h-96 w-full" />

        <div v-show="view === 'table'" class="overflow-x-auto">
          <table class="w-full text-sm">
            <thead>
              <tr class="border-b bg-muted/50 text-left text-xs uppercase tracking-wide text-muted-foreground">
                <th class="px-3 py-2 font-medium">品牌</th>
                <th class="px-3 py-2 font-medium">发布时间</th>
                <th class="px-3 py-2 font-medium">机型</th>
                <th class="px-3 py-2 font-medium">参数值</th>
              </tr>
            </thead>
            <tbody>
              <template v-for="item in data?.series ?? []" :key="item.brandId">
                <tr v-for="point in item.points" :key="`${item.brandId}-${point.deviceId}`" class="border-b last:border-0">
                  <td class="px-3 py-2.5 text-muted-foreground">{{ item.brandName }}</td>
                  <td class="px-3 py-2.5 text-muted-foreground">{{ point.releaseDate }}</td>
                  <td class="px-3 py-2.5">{{ point.deviceName }}</td>
                  <td class="px-3 py-2.5 font-medium">{{ point.value }}</td>
                </tr>
              </template>
            </tbody>
          </table>
        </div>
      </div>
    </motion.div>
  </div>
</template>

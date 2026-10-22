<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import * as echarts from 'echarts/core'
import { BarChart, LineChart as EChartsLineChart } from 'echarts/charts'
import { GridComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import type { EChartsType } from 'echarts/core'
import html2canvas from 'html2canvas'
import { motion } from 'motion-v'
import { Download, LineChart, TrendingUp } from '@lucide/vue'
import { toast } from 'vue-sonner'
import { publicApi } from '@/api'
import { useThemeStore } from '@/stores/theme'
import { Button, Card, CardContent, Combobox, Empty, Skeleton, Tabs, type TabItem } from '@/components/ui'

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

const theme = useThemeStore()
const route = useRoute()
const router = useRouter()

/** 路由 query 里的 id 是字符串，统一转正整数。 */
function parseId(value: unknown) {
  const raw = Array.isArray(value) ? value[0] : value
  const id = Number(raw)
  return Number.isFinite(id) && id > 0 ? id : undefined
}

// 按需注册图表能力，避免把整个 ECharts 打进 History 页面 chunk
echarts.use([EChartsLineChart, BarChart, GridComponent, TooltipComponent, CanvasRenderer])

const brands = ref<Brand[]>([])
const defs = ref<ParamDef[]>([])
const brandId = ref<number | undefined>(parseId(route.query.brandId))
const paramDefId = ref<number | undefined>(parseId(route.query.paramDefId))
const view = ref('chart')
const points = ref<HistoryPoint[]>([])
const loading = ref(false)
const capturing = ref(false)

const views: TabItem[] = [
  { value: 'chart', label: '图表' },
  { value: 'table', label: '表格' },
]

const chartRef = ref<HTMLElement>()
const captureRef = ref<HTMLElement>()
let chart: EChartsType | null = null

const brandOptions = ref<{ label: string; value: number }[]>([])
const defOptions = ref<{ label: string; value: number }[]>([])

async function load() {
  if (!brandId.value || !paramDefId.value) {
    points.value = []
    return
  }
  loading.value = true
  try {
    points.value = await publicApi.history(brandId.value, paramDefId.value)
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

function renderChart() {
  if (!chartRef.value || !points.value.length) return
  if (!chart) chart = echarts.init(chartRef.value)

  const dark = theme.dark
  const primary = cssVar('--chart-1', '#3b82f6')
  const def = defs.value.find((d) => d.id === paramDefId.value)
  const numeric = points.value.map((p) => Number(p.value)).every((n) => !Number.isNaN(n))

  chart.setOption(
    {
      backgroundColor: 'transparent',
      tooltip: {
        trigger: 'axis',
        backgroundColor: dark ? '#18181b' : '#ffffff',
        borderColor: dark ? '#27272a' : '#e4e4e7',
        textStyle: { color: dark ? '#fafafa' : '#18181b' },
      },
      grid: { left: 56, right: 28, top: 44, bottom: 72 },
      xAxis: {
        type: 'category',
        data: points.value.map((p) => p.deviceName),
        axisLabel: { rotate: 30, color: dark ? '#a1a1aa' : '#71717a' },
        axisLine: { lineStyle: { color: dark ? '#3f3f46' : '#e4e4e7' } },
      },
      yAxis: {
        type: 'value',
        name: def?.unit || '',
        nameTextStyle: { color: dark ? '#a1a1aa' : '#71717a' },
        axisLabel: { color: dark ? '#a1a1aa' : '#71717a' },
        splitLine: { lineStyle: { color: dark ? '#27272a' : '#f4f4f5' } },
      },
      series: [
        {
          name: def?.paramName || '参数',
          type: numeric ? 'line' : 'bar',
          smooth: true,
          symbolSize: 7,
          data: points.value.map((p) => (numeric ? Number(p.value) : p.value)),
          label: { show: !numeric, position: 'top', color: dark ? '#a1a1aa' : '#71717a' },
          itemStyle: { color: primary },
          lineStyle: { width: 2 },
          areaStyle: numeric ? { color: primary, opacity: 0.16 } : undefined,
        },
      ],
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
  brandOptions.value = brandList.map((b) => ({ label: b.name, value: b.id }))
  defOptions.value = defList.map((d) => ({ label: d.paramName, value: d.id }))
  // 分享链接进入时选项加载完毕后再拉数据
  if (brandId.value && paramDefId.value) await load()
})

watch([brandId, paramDefId], load)

// 选择变化同步到地址栏，便于分享与刷新还原
watch([brandId, paramDefId], () => {
  const query: Record<string, string> = {}
  if (brandId.value) query.brandId = String(brandId.value)
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
</script>

<template>
  <div class="space-y-5">
    <div class="flex flex-col gap-3 lg:flex-row lg:items-end lg:justify-between">
      <div>
        <h1 class="text-xl font-semibold tracking-tight sm:text-2xl">参数历史演变</h1>
        <p class="text-sm text-muted-foreground">观察单一参数在品牌历代机型上的变化趋势</p>
      </div>
      <Button variant="outline" :disabled="!points.length" @click="exportImage">
        <Download />
        截图导出
      </Button>
    </div>

    <Card>
      <CardContent class="grid grid-cols-1 gap-3 p-4 sm:grid-cols-2 lg:grid-cols-[200px_220px_auto]">
        <Combobox v-model="brandId" :options="brandOptions" placeholder="选择品牌" search-placeholder="搜索品牌…" />
        <Combobox v-model="paramDefId" :options="defOptions" placeholder="选择参数" search-placeholder="搜索参数…" />
        <Tabs v-model="view" :items="views" class="justify-self-start" />
      </CardContent>
    </Card>

    <div v-if="!points.length && !loading" class="rounded-xl border border-dashed">
      <Empty
        title="选择品牌与参数后查看演变"
        description="例如：iPhone 的屏幕尺寸演变、某品牌电池容量的逐年变化。"
      >
        <template #icon><TrendingUp class="mb-1 h-8 w-8 text-muted-foreground/40" /></template>
      </Empty>
    </div>

    <Skeleton v-else-if="loading" class="h-96 rounded-xl" />

    <motion.div v-else :initial="{ opacity: 0, y: 8 }" :animate="{ opacity: 1, y: 0 }" :transition="{ duration: 0.3 }">
      <div ref="captureRef" class="capture-area rounded-xl border bg-background p-5">
        <div class="mb-2 text-center">
          <div class="flex items-center justify-center gap-2 font-semibold">
            <LineChart class="h-4 w-4 text-primary" />
            {{ brands.find((b) => b.id === brandId)?.name }} ·
            {{ defs.find((d) => d.id === paramDefId)?.paramName }} 演变
          </div>
          <p class="mt-1 text-xs text-muted-foreground">共 {{ points.length }} 个机型数据点</p>
        </div>

        <div v-show="view === 'chart'" ref="chartRef" class="h-96 w-full" />

        <div v-show="view === 'table'" class="overflow-x-auto">
          <table class="w-full text-sm">
            <thead>
              <tr class="border-b bg-muted/50 text-left text-xs uppercase tracking-wide text-muted-foreground">
                <th class="px-3 py-2 font-medium">发布时间</th>
                <th class="px-3 py-2 font-medium">机型</th>
                <th class="px-3 py-2 font-medium">参数值</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="point in points" :key="point.deviceId" class="border-b last:border-0">
                <td class="px-3 py-2.5 text-muted-foreground">{{ point.releaseDate }}</td>
                <td class="px-3 py-2.5">{{ point.deviceName }}</td>
                <td class="px-3 py-2.5 font-medium">{{ point.value }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </motion.div>
  </div>
</template>

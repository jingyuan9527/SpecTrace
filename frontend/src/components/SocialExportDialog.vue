<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { Copy, Download, Loader2, Sparkles } from '@lucide/vue'
import { toast } from 'vue-sonner'
import { publicApi } from '@/api'
import type { EvolutionPoint, EvolutionResult, SocialCopyResult } from '@/types/api'
import { Button, Dialog, Input, Label, Select, Tabs, type TabItem } from '@/components/ui'
import { useECharts } from '@/composables/useECharts'

/**
 * 社媒导出弹窗：把当前世代回顾渲染成可直接发布的素材——
 * 图片 Tab 按画布模板重排图表（非网页截图），文案 Tab 按平台风格生成发布稿。
 * 导出物固定亮色与品牌色：它是产出物设计，不跟随站点暗色主题。
 */

/** 与 Evolution 页 ParamView 结构兼容的子集（结构化类型直接接收） */
interface SocialParamView {
  paramDefId: number
  paramName: string
  unit?: string | null
  numeric: boolean
  visiblePoints: EvolutionPoint[]
  rate: number | null
  viewTrend: 'UP' | 'DOWN' | 'FLAT' | 'UNKNOWN'
}

const props = defineProps<{
  open: boolean
  data: EvolutionResult | null
  devices: { name: string; releaseDate?: string | null }[]
  params: SocialParamView[]
}>()

const emit = defineEmits<{ 'update:open': [value: boolean] }>()

const open = computed({
  get: () => props.open,
  set: (value) => emit('update:open', value),
})

const tabs: TabItem[] = [
  { value: 'image', label: '图片' },
  { value: 'copy', label: '文案' },
]
const tab = ref<'image' | 'copy'>('image')

// ---------- 图片模板 ----------
const CANVAS = {
  portrait: { width: 750, height: 1000, maxParams: 6, chartHeight: 132 },
  landscape: { width: 960, height: 540, maxParams: 4, chartHeight: 108 },
} as const
type CanvasStyle = keyof typeof CANVAS

const canvasStyle = ref<CanvasStyle>('portrait')
const cardTitle = ref('')
const exporting = ref(false)
const cardRef = ref<HTMLElement>()
const charts = useECharts()

const size = computed(() => CANVAS[canvasStyle.value])
/** 导出卡只放数值参数，超出画布容量的截断并提示 */
const cardParams = computed(() => props.params.filter((param) => param.numeric).slice(0, size.value.maxParams))
const truncated = computed(() => props.params.filter((param) => param.numeric).length - cardParams.value.length)

const years = computed(() => {
  const list = props.devices
    .map((device) => (device.releaseDate ?? '').slice(0, 4))
    .filter(Boolean)
  return list.length ? `${Math.min(...list.map(Number))}-${Math.max(...list.map(Number))}` : ''
})

const watermark = `SpecTrace 参数库 · ${location.host}`

watch([open, canvasStyle, () => props.params], async ([isOpen]) => {
  if (!isOpen) {
    charts.disposeAll()
    return
  }
  if (!cardTitle.value) {
    cardTitle.value = props.data ? `${props.data.seriesName} 历代参数进化` : ''
  }
  // 等弹窗布局完成再画，容器尺寸为 0 时 ECharts 会渲染成空白
  await nextTick()
  await nextTick()
  if (tab.value === 'image') renderCharts()
})

watch(tab, async (value) => {
  if (value === 'image' && open.value) {
    await nextTick()
    await nextTick()
    renderCharts()
  }
})

function rateText(rate: number | null, trend: SocialParamView['viewTrend']) {
  if (trend === 'UNKNOWN' || rate === null) return ''
  if (trend === 'FLAT') return '持平'
  return `${rate > 0 ? '+' : ''}${rate.toFixed(2)}%`
}

function rateClass(trend: SocialParamView['viewTrend']) {
  if (trend === 'UP') return 'text-rose-600'
  if (trend === 'DOWN') return 'text-emerald-600'
  return 'text-zinc-500'
}

function cardChartOption(points: EvolutionPoint[]) {
  const values = points.map((point) => (typeof point.value === 'number' ? point.value : null))
  return {
    grid: { left: 10, right: 10, top: 16, bottom: 8 },
    xAxis: { type: 'category', show: false, data: values.map((_, index) => String(index + 1)) },
    yAxis: { type: 'value', show: false, scale: true },
    series: [
      {
        type: 'line',
        data: values,
        smooth: true,
        symbol: 'circle',
        symbolSize: 5,
        connectNulls: true,
        itemStyle: { color: '#4f46e5' },
        lineStyle: { width: 2.5, color: '#4f46e5' },
        areaStyle: { color: '#4f46e5', opacity: 0.12 },
      },
    ],
  }
}

function renderCharts() {
  for (const param of cardParams.value) {
    charts.render(`card-${param.paramDefId}`, cardChartOption(param.visiblePoints))
  }
}

async function exportImage() {
  if (!cardRef.value || exporting.value) return
  exporting.value = true
  await nextTick()
  try {
    const html2canvas = (await import('html2canvas')).default
    const canvas = await html2canvas(cardRef.value, { backgroundColor: '#ffffff', scale: 2 })
    const link = document.createElement('a')
    link.download = `spectrace-social-${props.data?.seriesId ?? 'series'}-${canvasStyle.value}-${Date.now()}.png`
    link.href = canvas.toDataURL('image/png')
    link.click()
    toast.success('图片已下载')
  } catch {
    toast.error('导出失败，请重试')
  } finally {
    exporting.value = false
  }
}

// ---------- 社媒文案 ----------
const copyStyles = [
  { label: '小红书体', value: 'xiaohongshu' },
  { label: '公众号体', value: 'wechat' },
]
const copyStyle = ref<'xiaohongshu' | 'wechat'>('xiaohongshu')
const copy = ref<SocialCopyResult | null>(null)
const copyGenerating = ref(false)

async function generateCopy() {
  if (!props.data || copyGenerating.value) return
  copyGenerating.value = true
  copy.value = null
  try {
    copy.value = await publicApi.generateSocialCopy(props.data.seriesId, {
      style: copyStyle.value,
      paramDefIds: props.params.map((param) => param.paramDefId),
    })
    if (copy.value.failed) {
      toast.error('文案生成失败，稍后再试；不影响图片导出')
    } else if (!copy.value.available) {
      toast.warning('后台未配置 AI 模型，无法生成文案')
    }
  } finally {
    copyGenerating.value = false
  }
}

const copyFullText = computed(() => {
  const result = copy.value
  if (!result?.summary) return ''
  return [
    result.title || '',
    '',
    result.summary,
    ...(result.points?.length ? ['', ...result.points.map((point) => `· ${point}`)] : []),
    ...(result.tags?.length ? ['', result.tags.join(' ')] : []),
  ]
    .filter((part) => part !== '')
    .join('\n')
})

async function copyToClipboard() {
  if (!copyFullText.value) return
  await navigator.clipboard.writeText(copyFullText.value)
  toast.success('文案已复制，可直接粘贴发布')
}
</script>

<template>
  <Dialog v-model:open="open" title="社媒导出" description="把当前世代回顾变成可直接发布的图片与文案" class="max-w-3xl">
    <Tabs v-model="tab" :items="tabs" class="mb-4" />

    <!-- 图片模板：固定画布重排图表，导出物不跟随站点主题 -->
    <div v-if="tab === 'image'" class="space-y-3">
      <div class="grid grid-cols-1 gap-3 sm:grid-cols-2">
        <div class="space-y-1.5">
          <Label for="social-canvas">画布比例</Label>
          <Select
            v-model="canvasStyle"
            :options="[
              { label: '竖版 3:4（小红书）', value: 'portrait' },
              { label: '横版 16:9（公众号）', value: 'landscape' },
            ]"
          />
        </div>
        <div class="space-y-1.5">
          <Label for="social-title">卡片标题</Label>
          <Input id="social-title" v-model="cardTitle" placeholder="如 小米数字系列 历代参数进化" />
        </div>
      </div>

      <div class="max-h-[52vh] overflow-auto rounded-lg border bg-zinc-100 p-3">
        <div
          ref="cardRef"
          class="mx-auto flex flex-col bg-white text-zinc-900"
          :style="{ width: `${size.width}px`, height: `${size.height}px` }"
        >
          <!-- 头部 -->
          <div class="border-b border-zinc-100 px-10 pb-4 pt-9">
            <p class="text-xs font-medium tracking-wide text-indigo-600">
              {{ data?.brandName }} · 共 {{ devices.length }} 代<template v-if="years"> · {{ years }}</template>
            </p>
            <h1 class="mt-2 text-[30px] font-bold leading-tight">{{ cardTitle || '历代参数进化' }}</h1>
          </div>

          <!-- 图表区 -->
          <div class="grid flex-1 grid-cols-2 content-center gap-x-6 gap-y-2 px-10">
            <div v-for="param in cardParams" :key="param.paramDefId" class="flex flex-col justify-center">
              <div class="flex items-baseline justify-between gap-2">
                <span class="text-sm font-semibold">{{ param.paramName }}</span>
                <span class="text-xs font-bold" :class="rateClass(param.viewTrend)">
                  {{ rateText(param.rate, param.viewTrend) }}
                </span>
              </div>
              <div
                :ref="charts.bind(`card-${param.paramDefId}`)"
                class="w-full"
                :style="{ height: `${size.chartHeight}px` }"
              />
            </div>
            <p v-if="!cardParams.length" class="col-span-2 py-10 text-center text-sm text-zinc-400">
              当前没有数值型参数，勾选屏幕尺寸、电池容量等后再导出
            </p>
          </div>

          <!-- 水印：社媒分发的回流入口 -->
          <div class="flex items-center justify-between border-t border-zinc-100 px-10 py-4 text-[11px] text-zinc-400">
            <span class="font-semibold text-zinc-600">{{ watermark }}</span>
            <span>数据以品牌官方公布为准 · {{ new Date().toLocaleDateString('zh-CN') }}</span>
          </div>
        </div>
      </div>

      <div class="flex items-center justify-between">
        <p class="text-xs text-muted-foreground">
          <template v-if="truncated > 0">数值参数较多，卡片仅展示前 {{ cardParams.length }} 个；</template>
          下载后可直接发小红书 / 公众号
        </p>
        <Button :disabled="exporting || !cardParams.length" @click="exportImage">
          <Loader2 v-if="exporting" class="animate-spin" />
          <Download v-else />
          {{ exporting ? '生成中…' : '下载图片' }}
        </Button>
      </div>
    </div>

    <!-- 社媒文案：按平台风格生成发布稿 -->
    <div v-else class="space-y-3">
      <div class="flex items-end gap-2">
        <div class="w-44 space-y-1.5">
          <Label for="social-copy-style">文案风格</Label>
          <Select v-model="copyStyle" :options="copyStyles" />
        </div>
        <Button :disabled="copyGenerating" @click="generateCopy">
          <Loader2 v-if="copyGenerating" class="animate-spin" />
          <Sparkles v-else />
          生成文案
        </Button>
      </div>

      <div v-if="copy?.summary" class="space-y-3 rounded-lg border p-4">
        <h3 v-if="copy.title" class="text-base font-semibold">{{ copy.title }}</h3>
        <p class="whitespace-pre-wrap text-sm leading-6">{{ copy.summary }}</p>
        <ul v-if="copy.points?.length" class="space-y-1 text-sm text-muted-foreground">
          <li v-for="point in copy.points" :key="point">· {{ point }}</li>
        </ul>
        <div v-if="copy.tags?.length" class="flex flex-wrap gap-1.5">
          <span
            v-for="tag in copy.tags"
            :key="tag"
            class="rounded-full bg-primary/10 px-2.5 py-0.5 text-xs font-medium text-primary"
          >
            {{ tag }}
          </span>
        </div>
        <p v-if="copy.cached" class="text-xs text-muted-foreground">本次为缓存结果（24 小时内同区间同参数复用）</p>
        <Button variant="outline" size="sm" @click="copyToClipboard">
          <Copy />
          复制全文
        </Button>
      </div>
      <p v-else-if="!copyGenerating" class="rounded-md bg-muted/60 px-3 py-6 text-center text-sm text-muted-foreground">
        选择风格后点「生成文案」，标题/正文/话题标签按当前可见世代与参数生成
      </p>
    </div>
  </Dialog>
</template>

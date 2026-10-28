<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { Copy, Download, Loader2, Sparkles } from '@lucide/vue'
import { toast } from 'vue-sonner'
import { publicApi } from '@/api'
import type {
  AiImageGenerateResult,
  AiImagePromptOption,
  AiImageStatus,
  EvolutionPoint,
  EvolutionResult,
  SocialCopyResult,
} from '@/types/api'
import { Alert, Button, Dialog, Input, Label, Select, Tabs, type TabItem } from '@/components/ui'
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
  devices: { id?: number; name: string; releaseDate?: string | null }[]
  params: SocialParamView[]
  /** 聚焦版海报默认展示的参数（趋势页当前聚焦参数），不传则取首个数值参数 */
  focusParamDefId?: number | null
}>()

const emit = defineEmits<{ 'update:open': [value: boolean] }>()

const open = computed({
  get: () => props.open,
  set: (value) => emit('update:open', value),
})

// ---------- AI 海报 ----------
/** 可用性由后端判定：未配置供应商或模板时连入口一起隐藏 */
const imageStatus = ref<AiImageStatus | null>(null)
const imagePrompts = ref<AiImagePromptOption[]>([])
const imagePromptId = ref<number>()
const imageLoading = ref(false)
const imageGenerating = ref(false)
const imageResult = ref<AiImageGenerateResult | null>(null)
const imageError = ref('')

const tabs = computed<TabItem[]>(() => [
  { value: 'image', label: '图片' },
  { value: 'copy', label: '文案' },
  ...(imageStatus.value?.available ? [{ value: 'ai', label: 'AI 海报' }] : []),
])
const tab = ref<'image' | 'copy' | 'ai'>('image')

// ---------- 图片模板 ----------
const CANVAS = {
  portrait: { width: 750, height: 1000, maxParams: 6, chartHeight: 132 },
  landscape: { width: 960, height: 540, maxParams: 4, chartHeight: 108 },
  focus: { width: 1080, height: 1440 },
} as const
type CanvasStyle = keyof typeof CANVAS

const canvasStyle = ref<CanvasStyle>('portrait')
const cardTitle = ref('')
const exporting = ref(false)
const cardRef = ref<HTMLElement>()
const charts = useECharts()

const size = computed(() => CANVAS[canvasStyle.value])
/** 导出卡只放数值参数，超出画布容量的截断并提示；聚焦版不使用多卡布局 */
const cardParams = computed(() => {
  if (canvasStyle.value === 'focus') return []
  return props.params.filter((param) => param.numeric).slice(0, CANVAS[canvasStyle.value].maxParams)
})
const truncated = computed(() => props.params.filter((param) => param.numeric).length - cardParams.value.length)

// ---------- 聚焦版（单参数海报） ----------
const numericParams = computed(() => props.params.filter((param) => param.numeric))
const focusOptions = computed(() => numericParams.value.map((param) => ({ label: param.paramName, value: param.paramDefId })))
const focusDefId = ref<number | undefined>()
const focusParam = computed(() => numericParams.value.find((param) => param.paramDefId === focusDefId.value))

const years = computed(() => {
  const list = props.devices
    .map((device) => (device.releaseDate ?? '').slice(0, 4))
    .filter(Boolean)
  return list.length ? `${Math.min(...list.map(Number))}-${Math.max(...list.map(Number))}` : ''
})

const watermark = `SpecTrace 参数库 · ${location.host}`

watch([open, canvasStyle, () => props.params, focusDefId], async ([isOpen]) => {
  if (!isOpen) {
    charts.disposeAll()
    return
  }
  if (!cardTitle.value) {
    cardTitle.value = props.data ? `${props.data.seriesName} 历代参数进化` : ''
  }
  // 聚焦版默认参数跟随趋势页当前聚焦，切换后仍可在弹窗里改选
  if (!focusDefId.value || !focusParam.value) {
    focusDefId.value = props.focusParamDefId ?? numericParams.value[0]?.paramDefId
  }
  // 等弹窗布局完成再画，容器尺寸为 0 时 ECharts 会渲染成空白
  await nextTick()
  await nextTick()
  if (tab.value === 'image') renderCharts()
})

watch(tab, async (value) => {
  if (value === 'ai' && !imageStatus.value) loadImageOptions()
  if (value === 'image' && open.value) {
    await nextTick()
    await nextTick()
    renderCharts()
  }
})

// 每次打开重新探测可用性：后台刚配置好供应商时不必刷新整页
watch(open, (isOpen) => {
  if (!isOpen) {
    imageResult.value = null
    imageError.value = ''
    return
  }
  loadImageOptions()
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

/** 聚焦版大图：海报是静态产出物，字号与线宽按 1080 宽画布放大，不设 tooltip */
function focusChartOption(param: SocialParamView) {
  const names = props.devices.map((device) => device.name)
  const color = '#4f46e5'
  return {
    grid: { left: 24, right: 32, top: 48, bottom: 56, containLabel: true },
    xAxis: {
      type: 'category',
      data: names,
      axisLabel: { color: '#71717a', fontSize: 24, rotate: names.length > 6 ? 35 : 0 },
      axisLine: { lineStyle: { color: '#e4e4e7' } },
    },
    yAxis: {
      type: 'value',
      scale: true,
      name: param.unit || '',
      nameTextStyle: { color: '#a1a1aa', fontSize: 20 },
      axisLabel: { color: '#a1a1aa', fontSize: 20 },
      splitLine: { lineStyle: { color: '#f4f4f5' } },
    },
    series: [
      {
        name: param.paramName,
        type: 'line',
        smooth: true,
        symbolSize: 12,
        connectNulls: true,
        data: param.visiblePoints.map((point) => point.value ?? null),
        itemStyle: { color },
        lineStyle: { width: 4, color },
        areaStyle: { color, opacity: 0.12 },
        markPoint: {
          symbolSize: 56,
          label: { fontSize: 20, color: '#fff' },
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
  // 版式切换后旧容器已卸载，先把不再需要的实例清掉
  if (canvasStyle.value === 'focus') {
    charts.prune(new Set(['focus-chart']))
    if (focusParam.value) charts.render('focus-chart', focusChartOption(focusParam.value))
    return
  }
  charts.prune(new Set(cardParams.value.map((param) => `card-${param.paramDefId}`)))
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

/** 下载可用：多卡版需要有可画参数，聚焦版需要选中的聚焦参数 */
const exportDisabled = computed(() =>
  canvasStyle.value === 'focus' ? !focusParam.value : !cardParams.value.length,
)

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

// ---------- AI 海报 ----------
const imagePromptOptions = computed(() =>
  imagePrompts.value.map((prompt) => ({
    label: prompt.isDefault === 1 ? `${prompt.name}（默认）` : prompt.name,
    value: prompt.id,
  })),
)

/** 只有数值参数进提示词：文本参数在海报里既占版面又难以视觉化 */
const aiParamIds = computed(() =>
  props.params.filter((param) => param.numeric).map((param) => param.paramDefId),
)

/** 本次会带给绘图模型的数据范围：让访客清楚「选了什么就传什么」 */
const aiScopeText = computed(() => `${props.devices.length} 台机型 · ${aiParamIds.value.length} 个数值参数`)

async function loadImageOptions() {
  if (imageLoading.value) return
  imageLoading.value = true
  try {
    imageStatus.value = await publicApi.aiImageStatus()
    if (!imageStatus.value.available) {
      imagePrompts.value = []
      // 上次停在 AI 页时后台可能已关闭能力，退回图片页避免停在一个没有对应 Tab 的状态
      if (tab.value === 'ai') tab.value = 'image'
      return
    }
    imagePrompts.value = await publicApi.aiImagePrompts()
    if (!imagePrompts.value.some((prompt) => prompt.id === imagePromptId.value)) {
      imagePromptId.value =
        imagePrompts.value.find((prompt) => prompt.isDefault === 1)?.id ?? imagePrompts.value[0]?.id
    }
  } catch {
    // 探测失败按不可用处理：与其展示一个点了就报错的入口，不如不展示
    imageStatus.value = null
  } finally {
    imageLoading.value = false
  }
}

async function generateImage() {
  if (!props.data || imageGenerating.value) return
  imageGenerating.value = true
  imageError.value = ''
  imageResult.value = null
  try {
    imageResult.value = await publicApi.generateAiImage({
      seriesId: props.data.seriesId,
      deviceIds: props.devices.map((device) => device.id).filter((id): id is number => typeof id === 'number'),
      paramDefIds: aiParamIds.value,
      promptId: imagePromptId.value,
      title: cardTitle.value,
    })
    toast.success('AI 海报已生成')
  } catch (error) {
    // 请求层已弹过 toast，这里只留一条可回看的错误文案
    imageError.value = error instanceof Error ? error.message : '生成失败，请稍后再试'
  } finally {
    imageGenerating.value = false
  }
}

function downloadAiImage() {
  if (!imageResult.value) return
  const link = document.createElement('a')
  link.href = imageResult.value.imageUrl
  link.download = `spectrace-ai-${props.data?.seriesId ?? 'series'}-${Date.now()}.png`
  link.click()
}
</script>

<template>
  <Dialog v-model:open="open" title="社媒导出" description="把当前世代回顾变成可直接发布的图片与文案" class="max-w-3xl">
    <Tabs v-model="tab" :items="tabs" class="mb-4" />

    <!-- 图片模板：固定画布重排图表，导出物不跟随站点主题 -->
    <div v-if="tab === 'image'" class="space-y-3">
      <div class="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
        <div class="space-y-1.5">
          <Label for="social-canvas">画布比例</Label>
          <Select
            v-model="canvasStyle"
            :options="[
              { label: '竖版 3:4（小红书）', value: 'portrait' },
              { label: '横版 16:9（公众号）', value: 'landscape' },
              { label: '聚焦版 3:4（单参数大图）', value: 'focus' },
            ]"
          />
        </div>
        <div v-if="canvasStyle === 'focus'" class="space-y-1.5">
          <Label for="social-focus-param">聚焦参数</Label>
          <Select v-model="focusDefId" :options="focusOptions" />
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

          <!-- 聚焦版主体：参数名 + 涨跌结论 + 单参数大图 + 首末值摘要 -->
          <template v-if="canvasStyle === 'focus'">
            <div class="flex items-baseline justify-between px-10 pt-4">
              <div class="flex items-baseline gap-3">
                <span class="text-[34px] font-bold leading-none">{{ focusParam?.paramName }}</span>
                <span v-if="focusParam?.unit" class="text-lg text-zinc-400">{{ focusParam.unit }}</span>
              </div>
              <span class="text-2xl font-bold" :class="focusParam && rateClass(focusParam.viewTrend)">
                {{ focusParam ? rateText(focusParam.rate, focusParam.viewTrend) : '' }}
              </span>
            </div>
            <div :ref="charts.bind('focus-chart')" class="w-full flex-1" />
            <p v-if="focusParam" class="px-10 pb-2 text-center text-sm text-zinc-400">
              {{ devices[0]?.name }} {{ focusParam.visiblePoints[0]?.raw || '—' }}
              → {{ devices[devices.length - 1]?.name }} {{ focusParam.visiblePoints[focusParam.visiblePoints.length - 1]?.raw || '—' }}
            </p>
          </template>

          <!-- 多卡版图表区 -->
          <div v-else class="grid flex-1 grid-cols-2 content-center gap-x-6 gap-y-2 px-10">
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
                :style="{ height: `${CANVAS[canvasStyle].chartHeight}px` }"
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
          <template v-else-if="canvasStyle === 'focus'">聚焦版为单参数海报，1080×1440 固定尺寸；</template>
          下载后可直接发小红书 / 公众号
        </p>
        <Button :disabled="exporting || exportDisabled" @click="exportImage">
          <Loader2 v-if="exporting" class="animate-spin" />
          <Download v-else />
          {{ exporting ? '生成中…' : '下载图片' }}
        </Button>
      </div>
    </div>

    <!-- 社媒文案：按平台风格生成发布稿 -->
    <div v-else-if="tab === 'copy'" class="space-y-3">
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

    <!-- AI 海报：整张图交给绘图模型，数据仅用于拼提示词 -->
    <div v-else class="space-y-3">
      <div v-if="imageLoading" class="space-y-2">
        <div v-for="i in 3" :key="i" class="h-9 animate-pulse rounded-md bg-muted" />
      </div>

      <Alert
        v-else-if="!imageStatus?.available"
        variant="warning"
        title="AI 海报暂不可用"
        :description="imageStatus?.reason ?? '后台尚未完成绘图供应商与提示词配置'"
      />

      <template v-else>
        <div class="grid grid-cols-1 gap-3 sm:grid-cols-2">
          <div class="space-y-1.5">
            <Label for="social-ai-prompt">提示词模板</Label>
            <Select id="social-ai-prompt" v-model="imagePromptId" :options="imagePromptOptions" placeholder="选择模板" />
          </div>
          <div class="space-y-1.5">
            <Label>本次带入的数据</Label>
            <p class="flex h-9 items-center rounded-md border bg-muted/40 px-3 text-sm text-muted-foreground">
              {{ aiScopeText }}
            </p>
          </div>
        </div>

        <p class="text-xs text-muted-foreground">
          AI 直接绘制整张海报：机型名与参数数值由模型绘制，可能与库内数据存在偏差，发布前请核对。
        </p>

        <p v-if="!aiParamIds.length" class="text-xs text-destructive">
          当前没有可用的数值参数，先在趋势页勾选屏幕尺寸、电池容量等参数再生成。
        </p>

        <div class="flex flex-wrap items-center gap-2">
          <Button
            :disabled="imageGenerating || !imagePromptId || !aiParamIds.length"
            @click="generateImage"
          >
            <Loader2 v-if="imageGenerating" class="animate-spin" />
            <Sparkles v-else />
            {{ imageGenerating ? '生成中，约需 30-120 秒…' : '生成 AI 海报' }}
          </Button>
          <Button v-if="imageResult" variant="outline" @click="downloadAiImage">
            <Download />
            下载图片
          </Button>
          <p v-if="imageError" class="text-xs text-destructive">{{ imageError }}</p>
        </div>

        <div v-if="imageResult" class="space-y-2">
          <img :src="imageResult.imageUrl" alt="AI 生成海报" class="mx-auto max-h-[52vh] rounded-lg border" />
          <details class="rounded-md border px-3 py-2">
            <summary class="cursor-pointer text-xs text-muted-foreground">
              查看本次提示词（模板：{{ imageResult.promptName }} / 模型：{{ imageResult.model }}）
            </summary>
            <pre class="mt-2 whitespace-pre-wrap break-words text-xs text-muted-foreground">{{ imageResult.promptText }}</pre>
          </details>
        </div>
        <p
          v-else-if="!imageGenerating"
          class="rounded-md bg-muted/60 px-3 py-6 text-center text-sm text-muted-foreground"
        >
          选择模板后点「生成 AI 海报」，当前机型范围与参数会拼进提示词交给绘图模型
        </p>
      </template>
    </div>
  </Dialog>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { Cpu, Loader2, RefreshCw } from '@lucide/vue'
import { toast } from 'vue-sonner'
import { adminApi } from '@/api'
import type { ModelCallDimensions, ModelCallMetrics } from '@/types/api'
import { useECharts } from '@/composables/useECharts'
import { cssVar } from '@/lib/utils'
import { useThemeStore } from '@/stores/theme'
import type { SelectOption } from '@/components/ui'
import { Button, Card, CardContent, Empty, Input, Select, Skeleton } from '@/components/ui'

type ModelRangePreset = '1h' | '6h' | '24h' | '7d' | '30d' | 'custom'
type FixedRangePreset = Exclude<ModelRangePreset, 'custom'>

const MODEL_RANGE_MS: Record<FixedRangePreset, number> = {
  '1h': 3_600_000,
  '6h': 6 * 3_600_000,
  '24h': 24 * 3_600_000,
  '7d': 7 * 86_400_000,
  '30d': 30 * 86_400_000,
}

const rangeButtons: Array<{ value: ModelRangePreset; label: string }> = [
  { value: '1h', label: '1 小时' },
  { value: '6h', label: '6 小时' },
  { value: '24h', label: '24 小时' },
  { value: '7d', label: '7 天' },
  { value: '30d', label: '30 天' },
  { value: 'custom', label: '自定义' },
]

const refreshOptions: SelectOption[] = [
  { value: 0, label: '不自动刷新' },
  { value: 10, label: '每 10 秒' },
  { value: 30, label: '每 30 秒' },
  { value: 60, label: '每分钟' },
  { value: 300, label: '每 5 分钟' },
]

const theme = useThemeStore()
const { bind, render } = useECharts()

const dimensions = ref<ModelCallDimensions>({ providers: [] })
const metrics = ref<ModelCallMetrics | null>(null)
const loading = ref(false)
const providerKey = ref('')
const modelId = ref<string | number | undefined>(undefined)
const rangePreset = ref<ModelRangePreset>('24h')
const customStart = ref('')
const customEnd = ref('')
const refreshSeconds = ref<string | number>(0)
let refreshTimer: number | undefined
// 快速切换筛选时只采信最后一次响应，避免旧请求覆盖新条件
let loadSeq = 0
let disposed = false

const providerOptions = computed<SelectOption[]>(() => [
  { value: '', label: '全部供应商' },
  ...dimensions.value.providers.map((provider) => ({
    value: `${provider.kind}:${provider.id}`,
    label: `${provider.kind === 'IMAGE' ? '绘图' : '对话'} · ${provider.name}`,
  })),
])

const selectedProvider = computed(
  () => dimensions.value.providers.find((item) => `${item.kind}:${item.id}` === providerKey.value) ?? null,
)

const modelOptions = computed<SelectOption[]>(() => {
  const provider = selectedProvider.value
  if (!provider) return [{ value: '', label: '全部模型' }]
  if (provider.kind === 'IMAGE') return [{ value: '', label: '该供应商仅一个绘图模型' }]
  return [
    { value: '', label: '全部模型' },
    ...provider.models.map((model) => ({ value: model.id ?? model.name, label: model.name })),
  ]
})

const successRateText = computed(() =>
  metrics.value?.successRate == null ? '—' : `${Math.round(metrics.value.successRate * 100)}%`,
)

function pad2(value: number) {
  return String(value).padStart(2, '0')
}

/** datetime-local 需要本地时间文本，toISOString 会按 UTC 偏移导致区间错位。 */
function toLocalInput(date: Date) {
  return `${date.getFullYear()}-${pad2(date.getMonth() + 1)}-${pad2(date.getDate())}T${pad2(date.getHours())}:${pad2(date.getMinutes())}`
}

function toApiTime(date: Date) {
  return `${toLocalInput(date)}:${pad2(date.getSeconds())}`
}

function toApiDateTime(local: string) {
  return local.length === 16 ? `${local}:00` : local
}

function presetRange(preset: FixedRangePreset) {
  const end = new Date()
  return { start: new Date(end.getTime() - MODEL_RANGE_MS[preset]), end }
}

function initCustomRange() {
  if (customStart.value && customEnd.value) return
  const { start, end } = presetRange('24h')
  customStart.value = toLocalInput(start)
  customEnd.value = toLocalInput(end)
}

/** 返回待查询的 ISO 本地时间；自定义区间非法时给出提示并中止查询。 */
function currentRange(): { start: string; end: string } | null {
  if (rangePreset.value === 'custom') {
    if (!customStart.value || !customEnd.value) {
      toast.warning('请选择开始与结束时间')
      return null
    }
    if (new Date(customStart.value) >= new Date(customEnd.value)) {
      toast.warning('开始时间必须早于结束时间')
      return null
    }
    return { start: toApiDateTime(customStart.value), end: toApiDateTime(customEnd.value) }
  }
  const { start, end } = presetRange(rangePreset.value as FixedRangePreset)
  return { start: toApiTime(start), end: toApiTime(end) }
}

async function loadDimensions() {
  dimensions.value = await adminApi.modelCallDimensions()
}

async function loadMetrics() {
  const range = currentRange()
  if (!range) return
  const seq = ++loadSeq
  loading.value = true
  try {
    const params: Record<string, unknown> = { start: range.start, end: range.end }
    const provider = selectedProvider.value
    if (provider) {
      params.providerKind = provider.kind
      params.providerId = provider.id
      // 绘图供应商自身即模型，后端按供应商聚合即可
      if (provider.kind === 'CHAT' && typeof modelId.value === 'number') {
        params.modelId = modelId.value
      }
    }
    const data = await adminApi.modelCallMetrics(params)
    if (seq === loadSeq) metrics.value = data
  } catch (error) {
    // 全局拦截器已提示，这里只保留排查线索，不阻断页面其它区块
    console.warn('[monitor] 模型调用统计加载失败', error)
  } finally {
    if (seq === loadSeq) loading.value = false
  }
}

function applyPreset(preset: ModelRangePreset) {
  rangePreset.value = preset
  if (preset === 'custom') initCustomRange()
  void loadMetrics()
}

function onProviderChange(value: string | number | undefined) {
  providerKey.value = value == null ? '' : String(value)
  modelId.value = selectedProvider.value?.kind === 'IMAGE' ? '' : undefined
  void loadMetrics()
}

function onModelChange(value: string | number | undefined) {
  modelId.value = value
  void loadMetrics()
}

function onRefreshChange(value: string | number | undefined) {
  refreshSeconds.value = value ?? 0
  setupRefresh()
}

function setupRefresh() {
  if (refreshTimer !== undefined) {
    window.clearInterval(refreshTimer)
    refreshTimer = undefined
  }
  const seconds = Number(refreshSeconds.value)
  if (!Number.isFinite(seconds) || seconds <= 0) return
  refreshTimer = window.setInterval(() => {
    // 后台标签页不轮询；自定义绝对区间不自动刷新，避免把未「应用」的输入带进查询
    if (document.visibilityState !== 'visible' || loading.value || rangePreset.value === 'custom') return
    void loadMetrics()
  }, seconds * 1000)
}

function formatLatency(ms: number) {
  if (!ms) return '0 ms'
  return ms >= 1000 ? `${(ms / 1000).toFixed(ms >= 10_000 ? 0 : 1)} s` : `${Math.round(ms)} ms`
}

function bucketLabel(bucket: string) {
  const unit = metrics.value?.bucketUnit
  if (unit === 'DAY') return bucket.slice(5)
  if (unit === 'HOUR') return bucket.slice(5, 16)
  return bucket.slice(11, 16)
}

function renderCallChart() {
  const series = metrics.value?.series ?? []
  render('modelCalls', {
    grid: { left: 40, right: 16, top: 36, bottom: 28 },
    tooltip: { trigger: 'axis' },
    legend: { data: ['成功', '失败'], top: 0, textStyle: { color: cssVar('--foreground', '#333') } },
    xAxis: {
      type: 'category',
      data: series.map((point) => bucketLabel(point.bucket)),
      axisLabel: { color: cssVar('--muted-foreground', '#888') },
    },
    yAxis: { type: 'value', minInterval: 1, axisLabel: { color: cssVar('--muted-foreground', '#888') } },
    series: [
      {
        name: '成功',
        type: 'bar',
        stack: 'calls',
        barMaxWidth: 24,
        data: series.map((point) => point.success),
        itemStyle: { color: cssVar('--chart-1', '#2563eb') },
      },
      {
        name: '失败',
        type: 'bar',
        stack: 'calls',
        barMaxWidth: 24,
        data: series.map((point) => point.failed),
        itemStyle: { color: cssVar('--destructive', '#ef4444') },
      },
    ],
  })
}

function renderLatencyChart() {
  const series = metrics.value?.series ?? []
  render('modelLatency', {
    grid: { left: 56, right: 16, top: 36, bottom: 28 },
    tooltip: {
      trigger: 'axis',
      valueFormatter: (value: unknown) => formatLatency(Number(value)),
    },
    legend: { data: ['平均', 'P95', '最大'], top: 0, textStyle: { color: cssVar('--foreground', '#333') } },
    xAxis: {
      type: 'category',
      data: series.map((point) => bucketLabel(point.bucket)),
      axisLabel: { color: cssVar('--muted-foreground', '#888') },
    },
    yAxis: {
      type: 'value',
      axisLabel: {
        color: cssVar('--muted-foreground', '#888'),
        formatter: (value: number) => formatLatency(value),
      },
    },
    series: [
      {
        name: '平均',
        type: 'line',
        smooth: true,
        symbol: 'none',
        data: series.map((point) => point.avgMs),
        lineStyle: { width: 2, color: cssVar('--chart-1', '#2563eb') },
      },
      {
        name: 'P95',
        type: 'line',
        smooth: true,
        symbol: 'none',
        data: series.map((point) => point.p95Ms),
        lineStyle: { width: 2, color: cssVar('--chart-4', '#d97706') },
      },
      {
        name: '最大',
        type: 'line',
        smooth: true,
        symbol: 'none',
        data: series.map((point) => point.maxMs),
        lineStyle: { width: 1.5, type: 'dashed', color: cssVar('--destructive', '#ef4444') },
      },
    ],
  })
}

function renderCharts() {
  renderCallChart()
  renderLatencyChart()
}

async function init() {
  initCustomRange()
  try {
    await loadDimensions()
  } catch (error) {
    console.warn('[monitor] 模型调用筛选项加载失败', error)
  }
  await loadMetrics()
  if (!disposed) setupRefresh()
}

onMounted(() => {
  void init()
})

onBeforeUnmount(() => {
  disposed = true
  if (refreshTimer !== undefined) window.clearInterval(refreshTimer)
})

// 数据就绪与主题切换后重绘；token 颜色是实时计算的
watch([metrics, () => theme.dark], () => {
  if (metrics.value && metrics.value.total > 0) nextTick(renderCharts)
})
</script>

<template>
  <Card>
    <div class="flex flex-wrap items-center justify-between gap-2 border-b bg-muted/40 px-5 py-3">
      <h2 class="flex items-center gap-2 text-sm font-semibold">
        <Cpu class="h-4 w-4" />
        模型调用
      </h2>
      <div class="flex items-center gap-2">
        <Select
          :model-value="refreshSeconds"
          :options="refreshOptions"
          class="h-8 w-32"
          @update:model-value="onRefreshChange"
        />
        <Button variant="ghost" size="sm" :disabled="loading" @click="loadMetrics">
          <Loader2 v-if="loading" class="animate-spin" />
          <RefreshCw v-else />
          刷新
        </Button>
      </div>
    </div>
    <CardContent class="space-y-4 p-4">
      <div class="flex flex-wrap items-center gap-2">
        <Select
          :model-value="providerKey"
          :options="providerOptions"
          placeholder="全部供应商"
          class="h-9 w-full sm:w-52"
          @update:model-value="onProviderChange"
        />
        <Select
          :model-value="modelId"
          :options="modelOptions"
          :disabled="!selectedProvider || selectedProvider.kind === 'IMAGE'"
          placeholder="全部模型"
          class="h-9 w-full sm:w-44"
          @update:model-value="onModelChange"
        />
        <div class="flex flex-wrap gap-1">
          <Button
            v-for="item in rangeButtons"
            :key="item.value"
            size="sm"
            :variant="rangePreset === item.value ? 'secondary' : 'ghost'"
            @click="applyPreset(item.value)"
          >
            {{ item.label }}
          </Button>
        </div>
      </div>

      <div v-if="rangePreset === 'custom'" class="flex flex-wrap items-end gap-2">
        <div class="grid gap-1">
          <span class="text-xs text-muted-foreground">开始</span>
          <Input v-model="customStart" type="datetime-local" class="w-full sm:w-56" />
        </div>
        <span class="pb-2 text-xs text-muted-foreground">至</span>
        <div class="grid gap-1">
          <span class="text-xs text-muted-foreground">结束</span>
          <Input v-model="customEnd" type="datetime-local" class="w-full sm:w-56" />
        </div>
        <Button size="sm" :disabled="loading" @click="loadMetrics">应用</Button>
      </div>

      <div v-if="loading && !metrics" class="grid grid-cols-2 gap-3 lg:grid-cols-4">
        <Skeleton v-for="i in 4" :key="i" class="h-20 rounded-lg" />
      </div>
      <Empty
        v-else-if="metrics && metrics.total === 0"
        title="当前范围没有模型调用"
        description="调整供应商、模型或时间范围后再看。"
      />
      <template v-else-if="metrics">
        <div class="grid grid-cols-2 gap-3 lg:grid-cols-4">
          <div class="rounded-lg border p-3">
            <p class="text-xs text-muted-foreground">调用次数</p>
            <p class="mt-1 text-xl font-semibold">{{ metrics.total }}</p>
            <p class="mt-1 text-xs text-muted-foreground">
              成功 {{ metrics.success }} · 失败
              <span :class="metrics.failed > 0 ? 'text-destructive' : ''">{{ metrics.failed }}</span>
            </p>
          </div>
          <div class="rounded-lg border p-3">
            <p class="text-xs text-muted-foreground">成功率</p>
            <p class="mt-1 text-xl font-semibold">{{ successRateText }}</p>
            <p class="mt-1 text-xs text-muted-foreground">成功 / 总调用</p>
          </div>
          <div class="rounded-lg border p-3">
            <p class="text-xs text-muted-foreground">平均 / P50</p>
            <p class="mt-1 text-xl font-semibold">
              {{ formatLatency(metrics.avgMs) }}
              <span class="text-sm font-normal text-muted-foreground">
                / {{ formatLatency(metrics.p50Ms) }}
              </span>
            </p>
            <p class="mt-1 text-xs text-muted-foreground">含失败调用</p>
          </div>
          <div class="rounded-lg border p-3">
            <p class="text-xs text-muted-foreground">P95 / 最大</p>
            <p class="mt-1 text-xl font-semibold">
              {{ formatLatency(metrics.p95Ms) }}
              <span class="text-sm font-normal text-muted-foreground">/ {{ formatLatency(metrics.maxMs) }}</span>
            </p>
            <p class="mt-1 text-xs text-muted-foreground">P99 {{ formatLatency(metrics.p99Ms) }}</p>
          </div>
        </div>

        <div class="grid gap-4 lg:grid-cols-2">
          <div>
            <p class="mb-1 text-xs font-medium text-muted-foreground">调用量趋势</p>
            <div :ref="bind('modelCalls')" class="h-56 w-full" />
          </div>
          <div>
            <p class="mb-1 text-xs font-medium text-muted-foreground">延迟趋势</p>
            <div :ref="bind('modelLatency')" class="h-56 w-full" />
          </div>
        </div>

        <div>
          <p class="mb-2 text-xs font-medium text-muted-foreground">
            失败原因分布
            <span class="font-normal">（{{ metrics.failed }} 次失败）</span>
          </p>
          <div v-if="metrics.failures.length" class="grid gap-2 sm:grid-cols-2">
            <div v-for="failure in metrics.failures" :key="failure.reason" class="space-y-1 rounded-lg border p-2.5">
              <div class="flex items-center justify-between gap-2 text-sm">
                <span class="min-w-0 truncate">{{ failure.label }}</span>
                <span class="shrink-0 text-xs text-muted-foreground">
                  {{ failure.count }} 次 ·
                  {{ failure.share == null ? '—' : Math.round(failure.share * 100) + '%' }}
                </span>
              </div>
              <div class="h-1.5 overflow-hidden rounded-full bg-muted">
                <div
                  class="h-full rounded-full bg-destructive"
                  :style="{ width: `${Math.round((failure.share ?? 0) * 100)}%` }"
                />
              </div>
            </div>
          </div>
          <p v-else class="text-sm text-muted-foreground">没有失败调用。</p>
        </div>

        <p class="text-xs text-muted-foreground">
          延迟统计包含失败调用；失败按超时、连接、认证、限流、HTTP 状态与空返回 / 返回格式异常分类。
          更早区间受「模型调用明细」保留天数限制。
        </p>
      </template>
      <p v-else class="text-sm text-muted-foreground">暂无统计数据。</p>
    </CardContent>
  </Card>
</template>

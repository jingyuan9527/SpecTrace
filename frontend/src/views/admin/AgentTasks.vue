<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  Ban,
  Bot,
  CheckCircle2,
  ClipboardCheck,
  ExternalLink,
  Loader2,
  RefreshCw,
  RotateCcw,
  Send,
  Terminal,
} from '@lucide/vue'
import { toast } from 'vue-sonner'
import { adminApi } from '@/api'
import type { AiModel, SeriesDiscoveryDetail } from '@/types/api'
import { usePagedList } from '@/composables/usePagedList'
import { formatTime } from '@/lib/utils'
import {
  Badge,
  Button,
  Card,
  CardContent,
  CardHeader,
  CardTitle,
  Checkbox,
  Combobox,
  Dialog,
  Empty,
  Input,
  Label,
  Pagination,
  Select,
  Separator,
  Sheet,
  Skeleton,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
  Tabs,
} from '@/components/ui'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import PageHeader from '@/components/PageHeader.vue'



const aiModels = ref<AiModel[]>([])
const submitting = ref(false)

// 任务模式：单机型参数抓取 / 系列机型发现（发现结果建草稿档案，再逐机型抓详情）
type TaskMode = 'DEVICE_DETAIL' | 'SERIES_DISCOVERY'
const mode = ref<TaskMode>('DEVICE_DETAIL')
const modeTabs = [
  { label: '单机型抓取', value: 'DEVICE_DETAIL' },
  { label: '系列发现', value: 'SERIES_DISCOVERY' },
]
const isDiscoveryMode = computed(() => mode.value === 'SERIES_DISCOVERY')

const detailTarget = ref<any>(null)

const { list, total, loading, query, load, search } = usePagedList({
  size: 10,
  filters: () => ({ status: '' }),
  fetcher: (params) => adminApi.agentTasks({ ...params, status: params.status || undefined }),
  // 详情打开时列表仍在轮询，用最新行同步状态；抓取结束即可停掉日志轮询
  onLoaded: (res) => {
    if (!detailTarget.value) return
    const fresh = res.records.find((row: any) => row.id === detailTarget.value.id)
    if (fresh) detailTarget.value = fresh
    syncLogPolling()
  },
})

const form = reactive<{ modelName: string; brandName: string; aiModelId: number | null }>({
  modelName: '',
  brandName: '',
  aiModelId: null,
})

const statusMap: Record<string, { label: string; variant: 'default' | 'secondary' | 'destructive' | 'outline' | 'success' | 'warning' | 'info' }> = {
  QUEUED: { label: '排队中', variant: 'secondary' },
  RUNNING: { label: '抓取中', variant: 'warning' },
  PENDING_REVIEW: { label: '待审核', variant: 'info' },
  APPROVED: { label: '已通过', variant: 'success' },
  DISCARDED: { label: '已丢弃', variant: 'secondary' },
  FAILED: { label: '失败', variant: 'destructive' },
}

const statusOptions = [
  { label: '全部状态', value: '' },
  ...Object.entries(statusMap).map(([value, meta]) => ({ label: meta.label, value })),
]

const modelOptions = ref<{ label: string; value: number }[]>([])
// 品牌提交的是名称字符串（后端 TaskCreateRequest.brandName），因此下拉值直接用品牌名
const allBrandOptions = ref<{ label: string; value: string }[]>([{ label: '不限品牌（由 Agent 推断）', value: '' }])
// 系列发现必须选品牌：发现结果要落成机型档案，品牌由 Agent 推断可能命中库外品牌
const strictBrandOptions = computed(() => allBrandOptions.value.filter((option) => option.value !== ''))

async function loadBrands() {
  const brands = await adminApi.brands()
  allBrandOptions.value = [
    { label: '不限品牌（由 Agent 推断）', value: '' },
    ...brands.map((brand: any) => ({ label: brand.name, value: brand.name })),
  ]
}

// 页面可见时每 5 秒静默轮询，任务状态无需手动刷新即可从「排队中」流转
const POLL_INTERVAL_MS = 5000
const LOG_POLL_INTERVAL_MS = 3000
let pollTimer: number | undefined
let logTimer: number | undefined

function startPolling() {
  stopPolling()
  pollTimer = window.setInterval(() => {
    if (document.visibilityState === 'visible') {
      load(true)
    }
  }, POLL_INTERVAL_MS)
}

function stopPolling() {
  if (pollTimer) {
    window.clearInterval(pollTimer)
    pollTimer = undefined
  }
}

const detailLogs = ref<any[]>([])
const detailLogLoading = ref(false)
const logBox = ref<HTMLElement | null>(null)

// 抓取中的任务每 3 秒增量拉日志；任务结束后自动停下，避免空转
function startLogPolling() {
  stopLogPolling()
  logTimer = window.setInterval(() => {
    if (document.visibilityState === 'visible') loadDetailLogs(true)
  }, LOG_POLL_INTERVAL_MS)
}

function stopLogPolling() {
  if (logTimer) {
    window.clearInterval(logTimer)
    logTimer = undefined
  }
}

function syncLogPolling() {
  const status = detailTarget.value?.status
  const running = status === 'QUEUED' || status === 'RUNNING'
  if (running && !logTimer) {
    startLogPolling()
  } else if (!running && logTimer) {
    stopLogPolling()
    // 状态刚落定时再补拉一次，避免「结果已写入待审核」这类收尾日志丢在最后一次轮询之后
    loadDetailLogs(true)
  }
}

async function loadDetailLogs(append = false) {
  const task = detailTarget.value
  if (!task) return
  if (!append) detailLogLoading.value = true
  try {
    const afterId = append && detailLogs.value.length ? detailLogs.value[detailLogs.value.length - 1].id : undefined
    const lines = await adminApi.agentTaskLogs(task.id, afterId)
    if (!lines?.length) return
    detailLogs.value = afterId ? [...detailLogs.value, ...lines] : lines
    if (append) {
      await nextTick()
      logBox.value?.scrollTo({ top: logBox.value.scrollHeight })
    }
  } catch {
    // 日志是辅助信息，拉取失败不能打断任务列表的自动刷新
  } finally {
    detailLogLoading.value = false
  }
}
const detailOpen = computed({
  get: () => !!detailTarget.value,
  set: (value: boolean) => {
    if (!value) detailTarget.value = null
  },
})
// ---- 系列发现结果（任务详情里勾选创建草稿机型） ----
const discovery = ref<SeriesDiscoveryDetail | null>(null)
const discoveryLoading = ref(false)
const selectedDiscovery = ref<string[]>([])
const creatingDevices = ref(false)

function isDiscoveryTask(task: any) {
  return task?.taskType === 'SERIES_DISCOVERY'
}

/** 来源列只展示域名：完整 URL 太长会挤歪表格，完整地址放在悬停提示与任务详情里。 */
function sourceHost(url?: string | null) {
  if (!url) return ''
  try {
    return new URL(url).host.replace(/^www\./, '')
  } catch {
    return url
  }
}

const router = useRouter()

/** 单机型抓取进入待审核后可一键去审核；系列发现的结果只在任务详情里处理。 */
function canGoReview(task: any) {
  return !!task && task.status === 'PENDING_REVIEW' && !isDiscoveryTask(task)
}

function goReview(task: any) {
  detailTarget.value = null
  router.push({
    path: '/admin/pending',
    // pendingId 精确直达该条；极端情况下缺失时退回按机型名筛选，至少能定位到队列
    query: task.pendingId ? { pendingId: String(task.pendingId) } : { keyword: task.modelName },
  })
}

// 已存在同名机型的行不可勾选（库里已有档案，重复创建只会被后端跳过）
const creatableModels = computed(() => discovery.value?.models.filter((m) => !m.existing) ?? [])
const allCreatableSelected = computed(
  () =>
    creatableModels.value.length > 0 &&
    creatableModels.value.every((m) => selectedDiscovery.value.includes(m.name)),
)

async function loadDiscovery(taskId: number) {
  discoveryLoading.value = true
  try {
    discovery.value = await adminApi.agentTaskDiscovery(taskId)
    const creatable = new Set(creatableModels.value.map((m) => m.name))
    selectedDiscovery.value = selectedDiscovery.value.filter((name) => creatable.has(name))
  } finally {
    discoveryLoading.value = false
  }
}

function toggleDiscoveryModel(name: string, checked: boolean) {
  if (checked) {
    if (!selectedDiscovery.value.includes(name)) selectedDiscovery.value.push(name)
  } else {
    selectedDiscovery.value = selectedDiscovery.value.filter((n) => n !== name)
  }
}

function toggleDiscoveryAll(checked: boolean) {
  selectedDiscovery.value = checked ? creatableModels.value.map((m) => m.name) : []
}

// ---- 拒绝发现结果：任务置为已丢弃，清单不可再创建草稿；已建草稿是独立档案不受影响 ----
const discardTarget = ref<any>(null)
const discardConfirm = ref(false)
const discardSubmitting = ref(false)

function openDiscard(task: any) {
  discardTarget.value = task
  discardConfirm.value = true
}

function closeDiscard() {
  discardConfirm.value = false
  discardTarget.value = null
}

async function confirmDiscard() {
  const task = discardTarget.value
  if (!task || discardSubmitting.value) return
  discardSubmitting.value = true
  try {
    await adminApi.discardDiscovery(task.id)
    toast.success('发现结果已拒绝，任务标记为已丢弃')
    closeDiscard()
    if (detailTarget.value?.id === task.id) await loadDiscovery(task.id)
    await load(true)
  } finally {
    discardSubmitting.value = false
  }
}

async function createSelectedDevices() {
  const task = detailTarget.value
  if (!task || !selectedDiscovery.value.length) return
  creatingDevices.value = true
  try {
    const result = await adminApi.createDiscoveryDevices(task.id, selectedDiscovery.value)
    toast.success(
      `已创建 ${result.created} 个草稿机型` +
        (result.skippedExisting.length ? `，跳过已存在 ${result.skippedExisting.length} 个` : ''),
    )
    await loadDiscovery(task.id)
    await load(true)
  } finally {
    creatingDevices.value = false
  }
}

// ---- 重试弹窗：失败任务常见于模型 API 无响应，重试时可换供应商与模型 ----
const retryDialog = ref(false)
const retryTarget = ref<any>(null)
const retryModelId = ref<number | null>(null)
const retrySubmitting = ref(false)

function openRetry(row: any) {
  retryTarget.value = row
  // 默认沿用原模型；原任务未指定时回落到后台默认模型
  retryModelId.value = row.aiModelId ?? aiModels.value.find((m) => m.isDefault === 1)?.id ?? null
  retryDialog.value = true
}

async function confirmRetry() {
  const target = retryTarget.value
  if (!target || retrySubmitting.value) return
  retrySubmitting.value = true
  try {
    const task = await adminApi.retryAgentTask(target.id, retryModelId.value ?? undefined)
    toast.success(`已重新提交，新任务 #${task?.id ?? ''}`)
    retryDialog.value = false
    await load(true)
  } finally {
    retrySubmitting.value = false
  }
}

function formatDuration(ms?: number | null) {
  if (!ms || ms < 0) return '—'
  return ms < 1000 ? `${ms} ms` : `${(ms / 1000).toFixed(1)} s`
}

async function loadAiModels() {
  aiModels.value = await adminApi.aiModels()
  modelOptions.value = aiModels.value.map((m) => ({
    label: `${m.configName} / ${m.model}${m.isDefault === 1 ? '（默认）' : ''}`,
    value: m.id,
  }))
  const def = aiModels.value.find((m) => m.isDefault === 1)
  if (def && form.aiModelId === null) form.aiModelId = def.id
}

// 系列管理页「系列发现」直达：?mode=SERIES_DISCOVERY&brand=xx 预填新建卡片
const route = useRoute()

onMounted(async () => {
  await Promise.all([load(), loadAiModels(), loadBrands()])
  if (route.query.mode === 'SERIES_DISCOVERY') mode.value = 'SERIES_DISCOVERY'
  if (typeof route.query.brand === 'string' && route.query.brand) form.brandName = route.query.brand
  startPolling()
})

onBeforeUnmount(() => {
  stopPolling()
  stopLogPolling()
})

// 详情切换（或关闭）时重置日志：只按任务 id 判断，列表轮询替换对象不会打断日志
watch(
  () => detailTarget.value?.id,
  async (id) => {
    stopLogPolling()
    detailLogs.value = []
    discovery.value = null
    selectedDiscovery.value = []
    if (!id) return
    const task = detailTarget.value
    if (isDiscoveryTask(task) && ['PENDING_REVIEW', 'APPROVED', 'DISCARDED'].includes(task.status)) {
      loadDiscovery(id)
    }
    await loadDetailLogs()
    syncLogPolling()
  },
)

function logClass(level: string) {
  if (level === 'ERROR') return 'text-destructive'
  if (level === 'WARN') return 'text-amber-600 dark:text-amber-400'
  return ''
}

/** 日志只展示时:分:秒，日期在任务头部已有。 */
function timeOnly(value?: string | null) {
  const text = formatTime(value, '')
  return text.length > 10 ? text.slice(11) : ''
}

async function createTask() {
  if (!form.modelName.trim()) {
    toast.warning(isDiscoveryMode.value ? '请输入系列关键词' : '请输入机型名称')
    return
  }
  if (isDiscoveryMode.value && !form.brandName.trim()) {
    toast.warning('系列发现必须选择品牌')
    return
  }
  submitting.value = true
  try {
    await adminApi.createAgentTask({
      modelName: form.modelName.trim(),
      brandName: form.brandName.trim() || undefined,
      aiModelId: form.aiModelId ?? undefined,
      taskType: mode.value,
    })
    toast.success(isDiscoveryMode.value ? '系列发现任务已提交' : '抓取任务已提交')
    form.modelName = ''
    await load()
  } finally {
    submitting.value = false
  }
}

function modelLabel(id: number | null | undefined) {
  if (!id) return '默认模型'
  const model = aiModels.value.find((m) => m.id === id)
  return model ? `${model.configName} / ${model.model}` : `#${id}`
}
</script>

<template>
  <div class="space-y-4">
    <PageHeader title="抓取任务" description="提交机型名称，由内嵌 AgentScope Agent 检索并抽取结构化参数，结果进入待审核" />

    <Card>
      <CardHeader class="pb-3">
        <CardTitle class="flex flex-wrap items-center gap-2">
          <Bot class="h-4 w-4 text-primary" />
          新建抓取任务
          <Tabs v-model="mode" :items="modeTabs" class="ml-auto" />
        </CardTitle>
      </CardHeader>
      <CardContent class="grid gap-2 lg:grid-cols-[1fr_180px_260px_auto]">
        <Input
          v-model="form.modelName"
          :placeholder="isDiscoveryMode ? '系列关键词，如 iPhone 17' : '机型名称，如 iPhone 17 Pro'"
          @keyup.enter="createTask"
        />
        <Select
          v-model="form.brandName"
          :options="isDiscoveryMode ? strictBrandOptions : allBrandOptions"
          :placeholder="isDiscoveryMode ? '请选择品牌' : '不限品牌'"
        />
        <Combobox v-model="form.aiModelId" :options="modelOptions" placeholder="AI 模型（默认）" search-placeholder="搜索模型…" />
        <Button :disabled="submitting" @click="createTask">
          <Loader2 v-if="submitting" class="animate-spin" />
          <Send v-else />
          {{ submitting ? '提交中…' : isDiscoveryMode ? '提交发现' : '提交抓取' }}
        </Button>
        <p class="text-xs text-muted-foreground lg:col-span-4">
          <template v-if="isDiscoveryMode">
            发现该系列下的全部机型，完成后在任务详情勾选并创建为草稿机型档案（前台不可见），再到「机型管理」逐个抓取详情。
          </template>
          <template v-else>
            任务经队列投递给采集 Agent；不指定模型时使用后台设置的默认模型。抓取完成后需在「待审核数据」确认入库。
          </template>
        </p>
      </CardContent>
    </Card>

    <div class="flex flex-wrap items-center gap-2">
      <Select v-model="query.status" :options="statusOptions" class="w-40" @update:model-value="search" />
      <Button variant="outline" size="sm" @click="load">
        <RefreshCw />
        刷新
      </Button>
      <span class="ml-auto text-xs text-muted-foreground">共 {{ total }} 条任务</span>
    </div>

    <Card>
      <CardContent class="p-0">
        <div v-if="loading" class="space-y-2 p-4">
          <Skeleton v-for="i in 6" :key="i" class="h-11" />
        </div>

        <Table v-else-if="list.length">
          <TableHeader>
            <TableRow class="hover:bg-transparent">
              <TableHead class="w-20">任务</TableHead>
              <TableHead>机型 / 系列</TableHead>
              <TableHead class="w-24">品牌</TableHead>
              <TableHead class="min-w-44">AI 模型</TableHead>
              <TableHead class="w-24">状态</TableHead>
              <TableHead class="w-24">耗时</TableHead>
              <TableHead class="w-44">创建时间</TableHead>
              <TableHead class="w-56">来源 / 异常</TableHead>
              <TableHead class="w-44 text-right">操作</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            <TableRow v-for="row in list" :key="row.id">
              <TableCell class="font-mono text-xs text-muted-foreground">#{{ row.id }}</TableCell>
              <TableCell class="font-medium">
                <Badge v-if="isDiscoveryTask(row)" variant="outline" class="mr-1.5">系列</Badge>
                <Badge v-if="row.subscriptionId" variant="info" class="mr-1.5" title="由系列订阅调度自动创建">订阅</Badge>{{ row.modelName }}
              </TableCell>
              <TableCell class="text-muted-foreground">{{ row.brandName || '—' }}</TableCell>
              <TableCell class="text-xs text-muted-foreground">{{ modelLabel(row.aiModelId) }}</TableCell>
              <TableCell>
                <div class="flex flex-wrap items-center gap-1">
                  <Badge :variant="statusMap[row.status]?.variant ?? 'secondary'">
                    {{ statusMap[row.status]?.label ?? row.status }}
                  </Badge>
                  <Badge
                    v-if="row.status === 'FAILED' && row.approved"
                    variant="success"
                    title="该机型已有审核通过的抓取结果，无需重试"
                  >
                    已入库
                  </Badge>
                </div>
              </TableCell>
              <TableCell class="tabular-nums text-muted-foreground">
                {{ row.durationMs ? `${row.durationMs} ms` : '—' }}
              </TableCell>
              <TableCell class="text-xs tabular-nums text-muted-foreground">{{ formatTime(row.createdAt) }}</TableCell>
              <TableCell class="w-56 max-w-56 text-xs">
                <a
                  v-if="row.sourceUrl"
                  :href="row.sourceUrl"
                  target="_blank"
                  rel="noreferrer"
                  class="block truncate text-primary hover:underline"
                  :title="row.sourceUrl"
                >
                  {{ sourceHost(row.sourceUrl) }}
                </a>
                <span
                  v-if="row.errorMsg"
                  class="mt-1 line-clamp-2 break-words text-destructive"
                  :title="row.errorMsg"
                >
                  {{ row.errorMsg }}
                </span>
                <span v-if="!row.sourceUrl && !row.errorMsg" class="text-muted-foreground">—</span>
              </TableCell>
              <TableCell class="text-right">
                <div class="flex justify-end gap-1">
                  <Button v-if="canGoReview(row)" size="sm" variant="outline" @click="goReview(row)">
                    <ClipboardCheck />
                    去审核
                  </Button>
                  <Button variant="ghost" size="sm" @click="detailTarget = row">详情</Button>
                  <Button
                    v-if="row.status === 'FAILED' && !row.approved"
                    variant="outline"
                    size="sm"
                    @click="openRetry(row)"
                  >
                    <RotateCcw />
                    重试
                  </Button>
                  <Button
                    v-if="isDiscoveryTask(row) && row.status === 'PENDING_REVIEW'"
                    variant="ghost"
                    size="sm"
                    class="text-destructive hover:text-destructive"
                    @click="openDiscard(row)"
                  >
                    <Ban />
                    拒绝
                  </Button>
                </div>
              </TableCell>
            </TableRow>
          </TableBody>
        </Table>

        <Empty v-else title="暂无抓取任务" description="提交机型名称后，任务状态会在这里实时更新。">
          <template #icon><Terminal class="mb-1 h-8 w-8 text-muted-foreground/40" /></template>
        </Empty>
      </CardContent>
    </Card>

    <Pagination v-if="total > query.size" v-model:page="query.current" :page-size="query.size" :total="total" />

    <Sheet
      v-model:open="detailOpen"
      title="任务详情"
      :description="`#${detailTarget?.id ?? ''} ${detailTarget?.modelName ?? ''}`"
      side="right"
      class="w-[92vw] sm:w-[460px]"
    >
      <div v-if="detailTarget" class="space-y-4 text-sm">
        <div class="grid grid-cols-[88px_1fr] gap-y-2">
          <span class="text-muted-foreground">状态</span>
          <span class="flex flex-wrap items-center gap-1.5">
            <Badge :variant="statusMap[detailTarget.status]?.variant ?? 'secondary'">
              {{ statusMap[detailTarget.status]?.label ?? detailTarget.status }}
            </Badge>
            <Badge
              v-if="detailTarget.status === 'FAILED' && detailTarget.approved"
              variant="success"
              title="该机型已有审核通过的抓取结果，无需重试"
            >
              已入库
            </Badge>
          </span>
          <span class="text-muted-foreground">品牌</span>
          <span>{{ detailTarget.brandName || '—' }}</span>
          <span class="text-muted-foreground">AI 模型</span>
          <span class="break-all">{{ modelLabel(detailTarget.aiModelId) }}</span>
          <span class="text-muted-foreground">耗时</span>
          <span class="tabular-nums">{{ formatDuration(detailTarget.durationMs) }}</span>
          <span class="text-muted-foreground">创建时间</span>
          <span class="tabular-nums">{{ formatTime(detailTarget.createdAt) }}</span>
          <span class="text-muted-foreground">更新时间</span>
          <span class="tabular-nums">{{ formatTime(detailTarget.updatedAt) }}</span>
        </div>

        <Button v-if="canGoReview(detailTarget)" size="sm" class="w-full sm:w-auto" @click="goReview(detailTarget)">
          <ClipboardCheck />
          去抓取审核页处理该条结果
        </Button>

        <template v-if="isDiscoveryTask(detailTarget)">
          <Separator />

          <div class="space-y-1.5">
            <div class="flex items-center justify-between">
              <span class="text-xs font-medium text-muted-foreground">发现的机型</span>
              <label
                v-if="!discoveryLoading && creatableModels.length && detailTarget.status !== 'DISCARDED'"
                class="flex items-center gap-2 text-xs text-muted-foreground"
              >
                <Checkbox :model-value="allCreatableSelected" @update:model-value="toggleDiscoveryAll" />
                全选可创建
              </label>
            </div>
            <div v-if="discoveryLoading" class="space-y-2 py-2">
              <Skeleton v-for="i in 4" :key="i" class="h-8" />
            </div>
            <template v-else-if="discovery?.models.length">
              <div class="max-h-72 space-y-1 overflow-y-auto rounded-md border p-2">
                <label
                  v-for="model in discovery.models"
                  :key="model.name"
                  class="flex cursor-pointer items-center gap-2 rounded-sm px-2 py-1.5 hover:bg-accent"
                  :class="model.existing && 'cursor-default opacity-70'"
                >
                  <Checkbox
                    :model-value="selectedDiscovery.includes(model.name)"
                    :disabled="model.existing || detailTarget.status === 'DISCARDED'"
                    @update:model-value="toggleDiscoveryModel(model.name, $event)"
                  />
                  <span class="min-w-0 flex-1 truncate text-sm" :title="model.name">{{ model.name }}</span>
                  <span v-if="model.releaseDate" class="shrink-0 text-xs tabular-nums text-muted-foreground">
                    {{ model.releaseDate }}
                  </span>
                  <Badge v-if="model.existing" variant="secondary" class="shrink-0">已存在</Badge>
                </label>
              </div>
              <div class="flex flex-col gap-1.5 sm:flex-row sm:items-center">
                <Button
                  size="sm"
                  :disabled="!selectedDiscovery.length || creatingDevices || detailTarget.status === 'DISCARDED'"
                  @click="createSelectedDevices"
                >
                  <Loader2 v-if="creatingDevices" class="animate-spin" />
                  <CheckCircle2 v-else />
                  创建所选为草稿机型（{{ selectedDiscovery.length }}）
                </Button>
                <Button
                  v-if="detailTarget.status === 'PENDING_REVIEW'"
                  variant="outline"
                  size="sm"
                  class="text-destructive hover:text-destructive"
                  @click="openDiscard(detailTarget)"
                >
                  <Ban />
                  拒绝发现结果
                </Button>
                <span class="text-xs text-muted-foreground">
                  {{ detailTarget.status === 'DISCARDED'
                    ? '发现结果已被拒绝，无法再创建草稿；此前创建的草稿可在「机型管理」处理'
                    : '草稿机型前台不可见；在「机型管理」对其发起详情抓取，审核通过后自动上架' }}
                </span>
              </div>
            </template>
            <p
              v-else-if="detailTarget.status === 'PENDING_REVIEW' || detailTarget.status === 'APPROVED'"
              class="rounded-md bg-muted/60 px-3 py-2 text-xs text-muted-foreground"
            >
              发现结果为空
            </p>
            <p v-else class="rounded-md bg-muted/60 px-3 py-2 text-xs text-muted-foreground">
              任务完成后在这里展示机型清单
            </p>
          </div>
        </template>

        <Separator />

        <div class="space-y-1.5">
          <div class="flex items-center justify-between">
            <span class="text-xs font-medium text-muted-foreground">抓取日志</span>
            <span v-if="detailLogLoading" class="text-xs text-muted-foreground">加载中…</span>
            <span v-else-if="detailTarget.status === 'RUNNING'" class="flex items-center gap-1 text-xs text-muted-foreground">
              <Loader2 class="h-3 w-3 animate-spin" />
              抓取中，每 3 秒刷新
            </span>
          </div>
          <div
            v-if="detailLogs.length"
            ref="logBox"
            class="max-h-64 space-y-1 overflow-auto rounded-md bg-muted/60 p-3 font-mono text-xs leading-5"
          >
            <div v-for="line in detailLogs" :key="line.id" class="break-words" :class="logClass(line.level)">
              <span class="mr-1.5 text-muted-foreground/70">{{ timeOnly(line.createdAt) }}</span>{{ line.message }}
            </div>
          </div>
          <p v-else class="rounded-md bg-muted/60 px-3 py-2 text-xs text-muted-foreground">
            {{ detailTarget.status === 'QUEUED' ? '任务排队中，尚未开始抓取' : '暂无日志' }}
          </p>
        </div>

        <Separator />

        <div class="space-y-1.5">
          <div class="text-xs font-medium text-muted-foreground">来源链接</div>
          <a
            v-if="detailTarget.sourceUrl"
            :href="detailTarget.sourceUrl"
            target="_blank"
            rel="noreferrer"
            class="inline-flex items-start gap-1 break-all text-primary hover:underline"
          >
            {{ detailTarget.sourceUrl }}
            <ExternalLink class="mt-0.5 h-3 w-3 shrink-0" />
          </a>
          <span v-else class="text-muted-foreground">—</span>
        </div>

        <div v-if="detailTarget.errorMsg" class="space-y-1.5">
          <div class="text-xs font-medium text-muted-foreground">错误信息</div>
          <p class="break-words rounded-md bg-destructive/10 px-3 py-2 text-xs text-destructive">
            {{ detailTarget.errorMsg }}
          </p>
        </div>

        <div v-if="detailTarget.result" class="space-y-1.5">
          <div class="text-xs font-medium text-muted-foreground">抓取结果</div>
          <pre class="max-h-72 overflow-auto rounded-md bg-muted/60 p-3 text-xs leading-5">{{
            detailTarget.result
          }}</pre>
        </div>

        <Button v-if="detailTarget.status === 'FAILED' && !detailTarget.approved" @click="openRetry(detailTarget)">
          <RotateCcw />
          重试该任务
        </Button>
      </div>
    </Sheet>

    <Dialog
      v-model:open="retryDialog"
      title="重试抓取任务"
      :description="`#${retryTarget?.id ?? ''} ${retryTarget?.modelName ?? ''}，将按原参数新建任务`"
      class="max-w-md"
    >
      <div class="space-y-1.5">
        <Label>AI 模型（可选）</Label>
        <Combobox
          v-model="retryModelId"
          :options="modelOptions"
          placeholder="默认模型"
          search-placeholder="搜索供应商 / 模型…"
        />
        <p class="text-xs text-muted-foreground">
          超时类失败常见于模型 API 无响应，建议更换「供应商连接 / 模型名」后重试；不选择则沿用原模型
        </p>
      </div>
      <template #footer>
        <Button variant="outline" :disabled="retrySubmitting" @click="retryDialog = false">取消</Button>
        <Button :disabled="retrySubmitting" @click="confirmRetry">
          <Loader2 v-if="retrySubmitting" class="animate-spin" />
          {{ retrySubmitting ? '提交中…' : '提交重试' }}
        </Button>
      </template>
    </Dialog>

    <ConfirmDialog
      :open="discardConfirm"
      title="拒绝发现结果"
      :description="`确认拒绝「${discardTarget?.modelName ?? ''}」的整个发现清单？清单将不可再创建草稿机型；此前已创建的草稿机型不受影响，可在「机型管理」继续处理。`"
      confirm-text="拒绝"
      destructive
      :loading="discardSubmitting"
      @update:open="closeDiscard"
      @confirm="confirmDiscard"
    />
  </div>
</template>

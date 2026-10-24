<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { Bot, CheckCircle2, ExternalLink, Loader2, RefreshCw, RotateCcw, Send, Terminal } from '@lucide/vue'
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
  Empty,
  Input,
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
const retryingId = ref<number | null>(null)

// ---- 系列发现结果（任务详情里勾选创建草稿机型） ----
const discovery = ref<SeriesDiscoveryDetail | null>(null)
const discoveryLoading = ref(false)
const selectedDiscovery = ref<string[]>([])
const creatingDevices = ref(false)

function isDiscoveryTask(task: any) {
  return task?.taskType === 'SERIES_DISCOVERY'
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

async function retry(row: any) {
  retryingId.value = row.id
  try {
    const task = await adminApi.retryAgentTask(row.id)
    toast.success(`已重新提交，新任务 #${task?.id ?? ''}`)
    await load(true)
  } finally {
    retryingId.value = null
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

onMounted(async () => {
  await Promise.all([load(), loadAiModels(), loadBrands()])
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
    if (isDiscoveryTask(task) && (task.status === 'PENDING_REVIEW' || task.status === 'APPROVED')) {
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
              <TableHead class="min-w-44">来源 / 异常</TableHead>
              <TableHead class="w-32 text-right">操作</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            <TableRow v-for="row in list" :key="row.id">
              <TableCell class="font-mono text-xs text-muted-foreground">#{{ row.id }}</TableCell>
              <TableCell class="font-medium">
                <Badge v-if="isDiscoveryTask(row)" variant="outline" class="mr-1.5">系列</Badge>{{ row.modelName }}
              </TableCell>
              <TableCell class="text-muted-foreground">{{ row.brandName || '—' }}</TableCell>
              <TableCell class="text-xs text-muted-foreground">{{ modelLabel(row.aiModelId) }}</TableCell>
              <TableCell>
                <Badge :variant="statusMap[row.status]?.variant ?? 'secondary'">
                  {{ statusMap[row.status]?.label ?? row.status }}
                </Badge>
              </TableCell>
              <TableCell class="tabular-nums text-muted-foreground">
                {{ row.durationMs ? `${row.durationMs} ms` : '—' }}
              </TableCell>
              <TableCell class="text-xs tabular-nums text-muted-foreground">{{ formatTime(row.createdAt) }}</TableCell>
              <TableCell class="max-w-56 text-xs">
                <a
                  v-if="row.sourceUrl"
                  :href="row.sourceUrl"
                  target="_blank"
                  rel="noreferrer"
                  class="line-clamp-2 break-all text-primary hover:underline"
                >
                  {{ row.sourceUrl }}
                </a>
                <span v-if="row.errorMsg" class="mt-1 block break-words text-destructive">{{ row.errorMsg }}</span>
                <span v-if="!row.sourceUrl && !row.errorMsg" class="text-muted-foreground">—</span>
              </TableCell>
              <TableCell class="text-right">
                <div class="flex justify-end gap-1">
                  <Button variant="ghost" size="sm" @click="detailTarget = row">详情</Button>
                  <Button
                    v-if="row.status === 'FAILED'"
                    variant="outline"
                    size="sm"
                    :disabled="retryingId === row.id"
                    @click="retry(row)"
                  >
                    <Loader2 v-if="retryingId === row.id" class="animate-spin" />
                    <RotateCcw v-else />
                    重试
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
          <Badge :variant="statusMap[detailTarget.status]?.variant ?? 'secondary'">
            {{ statusMap[detailTarget.status]?.label ?? detailTarget.status }}
          </Badge>
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

        <template v-if="isDiscoveryTask(detailTarget)">
          <Separator />

          <div class="space-y-1.5">
            <div class="flex items-center justify-between">
              <span class="text-xs font-medium text-muted-foreground">发现的机型</span>
              <label
                v-if="!discoveryLoading && creatableModels.length"
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
                    :disabled="model.existing"
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
                  :disabled="!selectedDiscovery.length || creatingDevices"
                  @click="createSelectedDevices"
                >
                  <Loader2 v-if="creatingDevices" class="animate-spin" />
                  <CheckCircle2 v-else />
                  创建所选为草稿机型（{{ selectedDiscovery.length }}）
                </Button>
                <span class="text-xs text-muted-foreground">
                  草稿机型前台不可见；在「机型管理」对其发起详情抓取，审核通过后自动上架
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

        <Button
          v-if="detailTarget.status === 'FAILED'"
          :disabled="retryingId === detailTarget.id"
          @click="retry(detailTarget)"
        >
          <RotateCcw />
          重试该任务
        </Button>
      </div>
    </Sheet>
  </div>
</template>

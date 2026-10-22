<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { Bot, Loader2, RefreshCw, Send, Terminal } from '@lucide/vue'
import { toast } from 'vue-sonner'
import { adminApi } from '@/api'
import {
  Badge,
  Button,
  Card,
  CardContent,
  CardHeader,
  CardTitle,
  Combobox,
  Empty,
  Input,
  Pagination,
  Select,
  Skeleton,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui'
import PageHeader from '@/components/PageHeader.vue'

interface AiModel {
  id: number
  configName: string
  model: string
  isDefault: number
}

const list = ref<any[]>([])
const aiModels = ref<AiModel[]>([])
const total = ref(0)
const loading = ref(true)
const submitting = ref(false)
const query = reactive({ current: 1, size: 10, status: '' })

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

async function load() {
  loading.value = true
  try {
    const res = await adminApi.agentTasks({ ...query, status: query.status || undefined })
    list.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
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
  await Promise.all([load(), loadAiModels()])
})

watch(
  () => query.current,
  () => load(),
)

watch(
  () => query.status,
  () => {
    query.current = 1
    load()
  },
)

async function createTask() {
  if (!form.modelName.trim()) {
    toast.warning('请输入机型名称')
    return
  }
  submitting.value = true
  try {
    await adminApi.createAgentTask({
      modelName: form.modelName.trim(),
      brandName: form.brandName.trim() || undefined,
      aiModelId: form.aiModelId ?? undefined,
    })
    toast.success('抓取任务已提交')
    form.modelName = ''
    await load()
  } finally {
    submitting.value = false
  }
}

function modelLabel(id: number | null) {
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
        <CardTitle class="flex items-center gap-2">
          <Bot class="h-4 w-4 text-primary" />
          新建抓取任务
        </CardTitle>
      </CardHeader>
      <CardContent class="grid gap-2 lg:grid-cols-[1fr_180px_260px_auto]">
        <Input v-model="form.modelName" placeholder="机型名称，如 iPhone 17 Pro" @keyup.enter="createTask" />
        <Input v-model="form.brandName" placeholder="品牌（可选）" />
        <Combobox v-model="form.aiModelId" :options="modelOptions" placeholder="AI 模型（默认）" search-placeholder="搜索模型…" />
        <Button :disabled="submitting" @click="createTask">
          <Loader2 v-if="submitting" class="animate-spin" />
          <Send v-else />
          {{ submitting ? '提交中…' : '提交抓取' }}
        </Button>
        <p class="text-xs text-muted-foreground lg:col-span-4">
          任务经队列投递给采集 Agent；不指定模型时使用后台设置的默认模型。抓取完成后需在「待审核数据」确认入库。
        </p>
      </CardContent>
    </Card>

    <div class="flex flex-wrap items-center gap-2">
      <Select v-model="query.status" :options="statusOptions" class="w-40" />
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
              <TableHead>机型名称</TableHead>
              <TableHead class="w-24">品牌</TableHead>
              <TableHead class="min-w-44">AI 模型</TableHead>
              <TableHead class="w-24">状态</TableHead>
              <TableHead class="w-24">耗时</TableHead>
              <TableHead class="w-44">创建时间</TableHead>
              <TableHead class="min-w-44">来源 / 异常</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            <TableRow v-for="row in list" :key="row.id">
              <TableCell class="font-mono text-xs text-muted-foreground">#{{ row.id }}</TableCell>
              <TableCell class="font-medium">{{ row.modelName }}</TableCell>
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
              <TableCell class="text-xs text-muted-foreground">{{ row.createdAt }}</TableCell>
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
            </TableRow>
          </TableBody>
        </Table>

        <Empty v-else title="暂无抓取任务" description="提交机型名称后，任务状态会在这里实时更新。">
          <template #icon><Terminal class="mb-1 h-8 w-8 text-muted-foreground/40" /></template>
        </Empty>
      </CardContent>
    </Card>

    <Pagination v-if="total > query.size" v-model:page="query.current" :page-size="query.size" :total="total" />
  </div>
</template>

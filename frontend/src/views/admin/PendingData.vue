<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import {
  ArrowRight,
  ChevronDown,
  ChevronRight,
  ClipboardCheck,
  ExternalLink,
  Loader2,
  Plus,
  RefreshCw,
  ShieldCheck,
} from '@lucide/vue'
import { toast } from 'vue-sonner'
import { adminApi } from '@/api'
import {
  Alert,
  Badge,
  Button,
  Card,
  CardContent,
  Checkbox,
  Dialog,
  Empty,
  Input,
  Label,
  Pagination,
  Select,
  Separator,
  Skeleton,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import PageHeader from '@/components/PageHeader.vue'

const list = ref<any[]>([])
const brands = ref<any[]>([])
const defs = ref<any[]>([])
const total = ref(0)
const loading = ref(true)
const saving = ref(false)
const discarding = ref(false)

const query = reactive({ current: 1, size: 10, status: 'PENDING_REVIEW' as string | undefined })
const dialog = ref(false)
const current = ref<any>(null)
const discardTarget = ref<any>(null)

const form = reactive<any>({
  brandId: undefined,
  name: '',
  releaseDate: '',
  thumbnail: '',
  description: '',
  params: {} as Record<string, string>,
  variants: [] as { id?: number; storage: string; ram: string; price: string }[],
})

const statusOptions = [
  { label: '待审核', value: 'PENDING_REVIEW' },
  { label: '已通过', value: 'APPROVED' },
  { label: '已丢弃', value: 'DISCARDED' },
  { label: '全部状态', value: '' },
]

const statusMeta: Record<string, { label: string; variant: 'info' | 'success' | 'secondary' }> = {
  PENDING_REVIEW: { label: '待审核', variant: 'info' },
  APPROVED: { label: '已通过', variant: 'success' },
  DISCARDED: { label: '已丢弃', variant: 'secondary' },
}

async function load() {
  loading.value = true
  try {
    const res = await adminApi.pendingData({ ...query, status: query.status || undefined })
    list.value = res.records
    total.value = res.total
    // 列表刷新后旧的选择可能已不在当前页，直接清空避免误操作
    selectedIds.value = []
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  ;[brands.value, defs.value] = await Promise.all([adminApi.brands(), adminApi.paramDefs()])
  await load()
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

async function openReview(row: any) {
  const detail = await adminApi.pendingDetail(row.id)
  current.value = detail
  Object.assign(form, {
    brandId: brands.value.find((b) => b.name === detail.brandName)?.id,
    name: detail.modelName,
    releaseDate: detail.params?.['release_date'] || '',
    thumbnail: '',
    description: '',
    params: { ...(detail.params || {}) },
    variants: (detail.variants || []).map((v: any) => ({
      id: v.id,
      storage: v.storage || '',
      ram: v.ram || '',
      price: v.price === null || v.price === undefined ? '' : String(v.price),
    })),
  })
  showUnchanged.value = false
  dialog.value = true
}

async function approve() {
  if (!form.brandId) {
    toast.warning('请选择品牌')
    return
  }
  if (!form.name.trim()) {
    toast.warning('请填写机型名称')
    return
  }
  saving.value = true
  try {
    const variants = form.variants
      .filter((v: any) => v.storage.trim())
      .map((v: any, index: number) => ({
        id: v.id,
        storage: v.storage.trim(),
        ram: (v.ram || '').trim(),
        price:
          v.price === '' || v.price === null || v.price === undefined || Number.isNaN(Number(v.price))
            ? null
            : Number(v.price),
        currency: 'CNY',
        priceType: 'LAUNCH',
        sortOrder: index + 1,
      }))
    await adminApi.approvePending(current.value.id, { ...form, params: { ...form.params }, variants })
    toast.success('审核通过，已迁移到正式机型库')
    dialog.value = false
    await load()
  } finally {
    saving.value = false
  }
}

async function confirmDiscard() {
  if (!discardTarget.value) return
  discarding.value = true
  try {
    await adminApi.discardPending(discardTarget.value.id)
    toast.success('已丢弃')
    discardTarget.value = null
    dialog.value = false
    await load()
  } finally {
    discarding.value = false
  }
}

function label(key: string) {
  return defs.value.find((d) => d.paramKey === key)?.paramName || key
}

/** 库中是否已有同品牌同名机型；详情接口未返回时按新机型处理。 */
const isNewDevice = computed(() => current.value?.newDevice !== false)
const existingParams = computed<Record<string, string>>(() => current.value?.existingParams || {})
const showUnchanged = ref(false)

/** 逐参数比对抓取值与库中现值，用于「旧值 → 新值」标注。 */
// ---------- 批量操作 ----------
const selectedIds = ref<number[]>([])
const batchAction = ref<'approve' | 'discard' | null>(null)
const batchProcessing = ref(false)

const selectableIds = computed(() =>
  list.value.filter((row) => row.status === 'PENDING_REVIEW').map((row) => row.id),
)
const allSelected = computed(
  () => selectableIds.value.length > 0 && selectableIds.value.every((id) => selectedIds.value.includes(id)),
)

function toggleAll(checked: boolean) {
  selectedIds.value = checked ? [...selectableIds.value] : []
}

function toggleOne(id: number, checked: boolean) {
  if (checked) {
    if (!selectedIds.value.includes(id)) selectedIds.value.push(id)
  } else {
    selectedIds.value = selectedIds.value.filter((item) => item !== id)
  }
}

/** 单条失败不影响其余记录，失败原因拼进 toast 便于定位。 */
function reportBatchResult(result: any, action: 'approve' | 'discard') {
  const label = action === 'approve' ? '通过' : '丢弃'
  if (result.success) toast.success(`已${label} ${result.success} 条`)
  if (result.failed) {
    const detail = (result.failures || []).slice(0, 3).join('；')
    toast.error(`${result.failed} 条${label}失败：${detail}`)
  }
}

async function confirmBatch() {
  if (!batchAction.value || !selectedIds.value.length) return
  const action = batchAction.value
  batchProcessing.value = true
  try {
    const result =
      action === 'approve'
        ? await adminApi.batchApprovePending(selectedIds.value)
        : await adminApi.batchDiscardPending(selectedIds.value)
    reportBatchResult(result, action)
    batchAction.value = null
    await load()
  } finally {
    batchProcessing.value = false
  }
}

const diffRows = computed(() =>
  Object.keys(form.params).map((key) => {
    const value = form.params[key] ?? ''
    const oldValue = existingParams.value[key] ?? ''
    return { key, value, oldValue, changed: oldValue !== value }
  }),
)
const changedRows = computed(() => diffRows.value.filter((row) => row.changed))
const unchangedRows = computed(() => diffRows.value.filter((row) => !row.changed))

function addVariant() {
  form.variants.push({ storage: '', ram: '', price: '' })
}

function removeVariant(index: number) {
  form.variants.splice(index, 1)
}
</script>

<template>
  <div class="space-y-4">
    <PageHeader title="待审核数据" description="Agent 抓取结果必须人工核对后才能进入正式机型库" />

    <div class="flex flex-wrap items-center gap-2">
      <Select v-model="query.status" :options="statusOptions" class="w-40" />
      <Button variant="outline" size="sm" @click="load">
        <RefreshCw />
        刷新
      </Button>
      <span class="ml-auto text-xs text-muted-foreground">共 {{ total }} 条记录</span>
    </div>

    <div
      v-if="selectedIds.length"
      class="flex flex-wrap items-center gap-2 rounded-lg border bg-muted/40 px-3 py-2 text-sm"
    >
      <span class="font-medium">已选 {{ selectedIds.length }} 条</span>
      <Button size="sm" :disabled="batchProcessing" @click="batchAction = 'approve'">批量通过</Button>
      <Button
        size="sm"
        variant="ghost"
        class="text-destructive"
        :disabled="batchProcessing"
        @click="batchAction = 'discard'"
      >
        批量丢弃
      </Button>
      <Button size="sm" variant="ghost" @click="selectedIds = []">取消选择</Button>
      <span class="ml-auto text-xs text-muted-foreground">批量通过会直接使用抓取值入库，不经过人工修正</span>
    </div>

    <Card>
      <CardContent class="p-0">
        <div v-if="loading" class="space-y-2 p-4">
          <Skeleton v-for="i in 6" :key="i" class="h-11" />
        </div>

        <Table v-else-if="list.length">
          <TableHeader>
            <TableRow class="hover:bg-transparent">
              <TableHead class="w-10">
                <Checkbox
                  :model-value="allSelected"
                  :disabled="!selectableIds.length"
                  aria-label="全选待审核记录"
                  @update:model-value="toggleAll"
                />
              </TableHead>
              <TableHead class="w-16">ID</TableHead>
              <TableHead>机型名称</TableHead>
              <TableHead class="w-28">品牌</TableHead>
              <TableHead class="min-w-56">来源链接</TableHead>
              <TableHead class="w-44">抓取时间</TableHead>
              <TableHead class="w-24">状态</TableHead>
              <TableHead class="w-32 text-right">操作</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            <TableRow v-for="row in list" :key="row.id">
              <TableCell>
                <Checkbox
                  :model-value="selectedIds.includes(row.id)"
                  :disabled="row.status !== 'PENDING_REVIEW'"
                  :aria-label="`选择 ${row.modelName}`"
                  @update:model-value="toggleOne(row.id, $event)"
                />
              </TableCell>
              <TableCell class="text-muted-foreground">{{ row.id }}</TableCell>
              <TableCell class="font-medium">{{ row.modelName }}</TableCell>
              <TableCell class="text-muted-foreground">{{ row.brandName || '—' }}</TableCell>
              <TableCell class="max-w-64 text-xs">
                <a
                  v-if="row.sourceUrl"
                  :href="row.sourceUrl"
                  target="_blank"
                  rel="noreferrer"
                  class="inline-flex items-center gap-1 break-all text-primary hover:underline"
                >
                  {{ row.sourceUrl }}
                  <ExternalLink class="h-3 w-3 shrink-0" />
                </a>
                <span v-else class="text-muted-foreground">—</span>
              </TableCell>
              <TableCell class="text-xs text-muted-foreground">{{ row.createdAt }}</TableCell>
              <TableCell>
                <Badge :variant="statusMeta[row.status]?.variant ?? 'secondary'">
                  {{ statusMeta[row.status]?.label ?? row.status }}
                </Badge>
              </TableCell>
              <TableCell class="text-right">
                <div class="flex justify-end gap-1">
                  <Button size="sm" variant="outline" :disabled="row.status !== 'PENDING_REVIEW'" @click="openReview(row)">
                    审核
                  </Button>
                  <Button
                    size="sm"
                    variant="ghost"
                    class="text-destructive"
                    :disabled="row.status !== 'PENDING_REVIEW'"
                    @click="discardTarget = row"
                  >
                    丢弃
                  </Button>
                </div>
              </TableCell>
            </TableRow>
          </TableBody>
        </Table>

        <Empty v-else title="暂无待审核数据" description="Agent 抓取完成后，结果会进入这里等待核对。">
          <template #icon><ClipboardCheck class="mb-1 h-8 w-8 text-muted-foreground/40" /></template>
        </Empty>
      </CardContent>
    </Card>

    <Pagination v-if="total > query.size" v-model:page="query.current" :page-size="query.size" :total="total" />

    <Dialog v-model:open="dialog" title="审核 Agent 抓取数据" class="max-w-2xl">
      <div class="space-y-4">
        <Alert variant="info" :title="`来源：${current?.confidence ? `置信度 ${current.confidence}` : 'Agent 抓取结果'}`">
          <a
            v-if="current?.sourceUrl"
            :href="current.sourceUrl"
            target="_blank"
            rel="noreferrer"
            class="mt-1 inline-flex items-center gap-1 break-all text-xs underline underline-offset-2"
          >
            {{ current.sourceUrl }}
            <ExternalLink class="h-3 w-3 shrink-0" />
          </a>
        </Alert>

        <div class="grid gap-4 sm:grid-cols-2">
          <div class="space-y-1.5">
            <Label for="review-name">机型名称</Label>
            <Input id="review-name" v-model="form.name" />
          </div>
          <div class="space-y-1.5">
            <Label for="review-brand">品牌</Label>
            <Select
              id="review-brand"
              v-model="form.brandId"
              :options="brands.map((b) => ({ label: b.name, value: b.id }))"
              placeholder="选择品牌"
            />
          </div>
          <div class="space-y-1.5">
            <Label for="review-date">发布日期</Label>
            <Input id="review-date" v-model="form.releaseDate" type="date" />
          </div>
        </div>

        <div class="flex flex-wrap items-center gap-2 text-xs">
          <Badge v-if="isNewDevice" variant="info">新机型</Badge>
          <template v-else>
            <Badge variant="warning">{{ changedRows.length }} 项参数有变化</Badge>
            <span class="text-muted-foreground">审核通过后将覆盖更新已有记录 #{{ current?.existingDeviceId }}</span>
          </template>
        </div>

        <Separator />

        <div class="space-y-3">
          <div class="flex flex-wrap items-center justify-between gap-2">
            <div class="flex items-center gap-2">
              <h3 class="text-sm font-semibold">存储版本与发布价</h3>
              <Badge variant="secondary">{{ form.variants.length }} 个版本</Badge>
            </div>
            <Button variant="outline" size="sm" @click="addVariant">
              <Plus />
              添加版本
            </Button>
          </div>
          <div v-if="form.variants.length" class="space-y-2">
            <div
              v-for="(variant, index) in form.variants"
              :key="variant.id ?? index"
              class="grid grid-cols-[1fr_1fr_1fr_auto] items-end gap-2"
            >
              <div class="space-y-1">
                <Label class="text-xs">存储</Label>
                <Input v-model="variant.storage" aria-label="存储容量" placeholder="如 256GB" />
              </div>
              <div class="space-y-1">
                <Label class="text-xs">运行内存</Label>
                <Input v-model="variant.ram" aria-label="运行内存" placeholder="可空" />
              </div>
              <div class="space-y-1">
                <Label class="text-xs">发布价（元）</Label>
                <Input v-model="variant.price" aria-label="发布价" type="number" min="0" placeholder="如 5999" />
              </div>
              <Button variant="ghost" size="icon-sm" class="text-destructive" aria-label="删除版本" @click="removeVariant(index)">
                ×
              </Button>
            </div>
          </div>
          <p v-else class="text-xs text-muted-foreground">未采集到版本价格，可手动添加。</p>
        </div>

        <div class="space-y-3">
          <div class="flex flex-wrap items-center gap-2">
            <h3 class="text-sm font-semibold">参数（可直接修正）</h3>
            <Badge variant="secondary">{{ Object.keys(form.params).length }} 项</Badge>
            <span v-if="!isNewDevice" class="text-xs text-muted-foreground">变化项已标出旧值，未变化项默认折叠</span>
          </div>

          <div v-if="changedRows.length" class="grid gap-3 sm:grid-cols-2">
            <div
              v-for="row in changedRows"
              :key="row.key"
              class="space-y-1.5 rounded-md border border-l-2 border-l-amber-500 bg-amber-500/5 p-2"
            >
              <Label :for="`review-${row.key}`">{{ label(row.key) }}</Label>
              <Input :id="`review-${row.key}`" v-model="form.params[row.key]" />
              <p v-if="!isNewDevice" class="flex flex-wrap items-center gap-1 text-xs text-muted-foreground">
                <span class="line-through">{{ row.oldValue || '空' }}</span>
                <ArrowRight class="h-3 w-3" />
                <span class="font-medium text-foreground">{{ row.value || '空' }}</span>
              </p>
            </div>
          </div>

          <div v-if="unchangedRows.length" class="space-y-3">
            <Button variant="outline" size="sm" @click="showUnchanged = !showUnchanged">
              <ChevronDown v-if="showUnchanged" />
              <ChevronRight v-else />
              {{ showUnchanged ? '收起' : '展开' }} {{ unchangedRows.length }} 项未变化参数
            </Button>
            <div v-if="showUnchanged" class="grid gap-3 sm:grid-cols-2">
              <div v-for="row in unchangedRows" :key="row.key" class="space-y-1.5">
                <Label :for="`review-${row.key}`" class="text-muted-foreground">{{ label(row.key) }}</Label>
                <Input :id="`review-${row.key}`" v-model="form.params[row.key]" />
              </div>
            </div>
          </div>

          <p v-if="!diffRows.length" class="text-xs text-muted-foreground">未采集到参数，可在机型管理中补充。</p>
        </div>
      </div>

      <template #footer>
        <Button variant="outline" :disabled="saving" @click="dialog = false">取消</Button>
        <Button variant="ghost" class="text-destructive" :disabled="saving" @click="discardTarget = current">丢弃</Button>
        <Button :disabled="saving" @click="approve">
          <Loader2 v-if="saving" class="animate-spin" />
          <ShieldCheck v-else />
          {{ saving ? '入库中…' : '审核通过并入库' }}
        </Button>
      </template>
    </Dialog>

    <ConfirmDialog
      :open="!!batchAction"
      :title="batchAction === 'approve' ? '批量通过待审核数据' : '批量丢弃待审核数据'"
      :description="
        batchAction === 'approve'
          ? `将使用抓取值直接入库 ${selectedIds.length} 条记录；库中已有同名机型时覆盖更新参数。`
          : `确认丢弃已选的 ${selectedIds.length} 条抓取结果？丢弃后不可恢复。`
      "
      :confirm-text="batchAction === 'approve' ? '批量通过' : '批量丢弃'"
      :destructive="batchAction === 'discard'"
      :loading="batchProcessing"
      @update:open="batchAction = null"
      @confirm="confirmBatch"
    />

    <ConfirmDialog
      :open="!!discardTarget"
      title="丢弃抓取数据"
      :description="`确认丢弃「${discardTarget?.modelName ?? ''}」的抓取结果？丢弃后不可恢复。`"
      confirm-text="丢弃"
      destructive
      :loading="discarding"
      @update:open="discardTarget = null"
      @confirm="confirmDiscard"
    />
  </div>
</template>

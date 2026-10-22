<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { ClipboardCheck, ExternalLink, Loader2, Plus, RefreshCw, ShieldCheck } from '@lucide/vue'
import { toast } from 'vue-sonner'
import { adminApi } from '@/api'
import {
  Alert,
  Badge,
  Button,
  Card,
  CardContent,
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

    <Card>
      <CardContent class="p-0">
        <div v-if="loading" class="space-y-2 p-4">
          <Skeleton v-for="i in 6" :key="i" class="h-11" />
        </div>

        <Table v-else-if="list.length">
          <TableHeader>
            <TableRow class="hover:bg-transparent">
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
            <Label>品牌</Label>
            <Select
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
                <Input v-model="variant.storage" placeholder="如 256GB" />
              </div>
              <div class="space-y-1">
                <Label class="text-xs">运行内存</Label>
                <Input v-model="variant.ram" placeholder="可空" />
              </div>
              <div class="space-y-1">
                <Label class="text-xs">发布价（元）</Label>
                <Input v-model="variant.price" type="number" min="0" placeholder="如 5999" />
              </div>
              <Button variant="ghost" size="icon-sm" class="text-destructive" aria-label="删除版本" @click="removeVariant(index)">
                ×
              </Button>
            </div>
          </div>
          <p v-else class="text-xs text-muted-foreground">未采集到版本价格，可手动添加。</p>
        </div>

        <div class="space-y-3">
          <div class="flex items-center gap-2">
            <h3 class="text-sm font-semibold">参数（可直接修正）</h3>
            <Badge variant="secondary">{{ Object.keys(form.params).length }} 项</Badge>
          </div>
          <div class="grid gap-3 sm:grid-cols-2">
            <div v-for="(value, key) in form.params" :key="key" class="space-y-1.5">
              <Label :for="`review-${key}`">{{ label(String(key)) }}</Label>
              <Input :id="`review-${key}`" v-model="form.params[key]" />
            </div>
          </div>
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

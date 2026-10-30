<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { VueDraggable } from 'vue-draggable-plus'
import {
  BellRing,
  CloudDownload,
  Combine,
  GripVertical,
  History,
  Layers,
  Pencil,
  Plus,
  Radar,
  Sparkles,
  Trash2,
} from '@lucide/vue'
import { toast } from 'vue-sonner'
import { useRouter } from 'vue-router'
import { adminApi } from '@/api'
import { useScrapeDispatch } from '@/composables/useScrapeDispatch'
import type {
  Brand,
  DeviceSeries,
  SeriesMissingDevice,
  UnassignedDevice,
} from '@/types/api'
import ScrapeModelDialog from '@/components/admin/ScrapeModelDialog.vue'
import {
  Badge,
  Button,
  Card,
  CardContent,
  Dialog,
  Empty,
  Input,
  Label,
  Select,
  Skeleton,
  Switch,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import PageHeader from '@/components/PageHeader.vue'
import SeriesDevicesSheet from './series/SeriesDevicesSheet.vue'
import SeriesMergeDialog from './series/SeriesMergeDialog.vue'
import SeriesSuggestDialog from './series/SeriesSuggestDialog.vue'
import SeriesSubscriptionDialog from './series/SeriesSubscriptionDialog.vue'



/** 编辑表单：新建时 id 为空；brandName/统计字段由列表接口附带 */
type SeriesForm = Omit<DeviceSeries, "id" | "brandName" | "deviceCount" | "startYear" | "endYear"> & { id: number | null }

const router = useRouter()

const brands = ref<Brand[]>([])
const list = ref<DeviceSeries[]>([])
const unassigned = ref<UnassignedDevice[]>([])
const loading = ref(true)
const saving = ref(false)
const dialog = ref(false)
const form = reactive<SeriesForm>({ id: null, brandId: 0, name: '', sortOrder: 0, status: 1 })

const brandId = ref<number>()
const deleteTarget = ref<DeviceSeries | null>(null)
const deleting = ref(false)
const assigningId = ref<number>()

const mergeDialog = ref(false)
const mergeSource = ref<DeviceSeries | null>(null)

// ---- AI 推荐系列：候选与勾选由弹窗自己管理，这里只负责排序基准与创建后刷新 ----
const suggestDialog = ref(false)
/** 打开时的排序号基准：默认品牌已有系列数 */
const suggestSortOrderBase = ref(0)

function openSuggest() {
  const targetBrandId = brandId.value ?? brands.value[0]?.id
  suggestSortOrderBase.value = list.value.filter((s) => s.brandId === targetBrandId).length
  suggestDialog.value = true
}

async function onSuggestCreated(createdBrandId: number) {
  // 切到刚创建的品牌，用户能立刻看到新系列，而不是在「全部品牌」里翻
  brandId.value = createdBrandId
  await load()
}

const brandOptions = computed(() => brands.value.map((b) => ({ label: b.name, value: b.id })))

async function load() {
  loading.value = true
  try {
    const [series, devices] = await Promise.all([
      adminApi.series(brandId.value),
      adminApi.seriesUnassigned(brandId.value),
    ])
    list.value = series
    unassigned.value = devices
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  brands.value = await adminApi.brands()
  await load()
})

watch(brandId, load)

function seriesOptionsOf(targetBrandId: number) {
  return list.value
    .filter((item) => item.id && item.brandId === targetBrandId)
    .map((item) => ({ label: item.name, value: item.id as number }))
}

function openCreate() {
  Object.assign(form, {
    id: null,
    brandId: brandId.value ?? brands.value[0]?.id ?? 0,
    name: '',
    sortOrder: list.value.length,
    status: 1,
  })
  dialog.value = true
}

function openEdit(row: DeviceSeries) {
  Object.assign(form, row)
  dialog.value = true
}

async function save() {
  if (!form.name.trim()) {
    toast.warning('请填写系列名称')
    return
  }
  if (!form.brandId) {
    toast.warning('请选择品牌')
    return
  }
  saving.value = true
  try {
    // 新增时不要带 id:null，避免后端/网关把它当成显式主键
    await adminApi.saveSeries({
      ...(form.id ? { id: form.id } : {}),
      brandId: form.brandId,
      name: form.name.trim(),
      sortOrder: form.sortOrder,
      status: form.status,
    })
    toast.success('保存成功')
    dialog.value = false
    await load()
  } finally {
    saving.value = false
  }
}

async function confirmDelete() {
  if (!deleteTarget.value?.id) return
  deleting.value = true
  try {
    await adminApi.deleteSeries(deleteTarget.value.id)
    toast.success('已删除')
    deleteTarget.value = null
    await load()
  } catch {
    // 拦截器已提示「系列下还有机型」，保留弹窗让用户先合并或迁移
  } finally {
    deleting.value = false
  }
}

function openMerge(row: DeviceSeries) {
  mergeSource.value = row
  mergeDialog.value = true
}

async function onMerged() {
  await load()
}

async function assign(deviceId: number, seriesId?: number) {
  assigningId.value = deviceId
  try {
    await adminApi.assignSeries([deviceId], seriesId)
    toast.success(seriesId ? '已归类' : '已改为未归类')
    await load()
  } finally {
    assigningId.value = undefined
  }
}

/** Select 传出 string | number | undefined，统一收敛成 number 后再提交 */
function onAssignChange(deviceId: number, value: unknown) {
  assign(deviceId, typeof value === 'number' ? value : undefined)
}

// ---- 系列内机型清单：抽屉自己拉清单与执行移动，这里只负责打开与归属变更后刷新 ----
const devicesOpen = ref(false)
const devicesSeries = ref<DeviceSeries | null>(null)

function openDevices(row: DeviceSeries) {
  if (!row.id) return
  devicesSeries.value = row
  devicesOpen.value = true
}

function onDevicesChanged() {
  // 归属变更后系列列表的「带参 N/M」与未归类清单都会变，必须整体重拉
  void load()
}

/** 拖拽结束把顺序写回后端，保持列表排序与前台一致。 */
async function saveOrder() {
  try {
    await Promise.all(
      list.value.map((row, index) =>
        adminApi.saveSeries({
          id: row.id,
          brandId: row.brandId,
          name: row.name,
          status: row.status,
          sortOrder: index,
        }),
      ),
    )
    toast.success('排序已保存')
  } catch {
    await load()
  }
}

function yearRange(row: DeviceSeries) {
  if (!row.startYear) return '—'
  return row.startYear === row.endYear ? `${row.startYear}` : `${row.startYear}-${row.endYear}`
}

/** 系列发现：跳转抓取任务页并预填模式与品牌，发现结果建草稿后再回来补抓 */
function openDiscovery(row: DeviceSeries) {
  const brandName = brands.value.find((b) => b.id === row.brandId)?.name
  if (!brandName) {
    toast.warning('未找到系列所属品牌，请到抓取任务页手动选择')
    return
  }
  router.push({ name: 'admin-agent-tasks', query: { mode: 'SERIES_DISCOVERY', brand: brandName } })
}

// ---- 抓取弹窗：missing=补抓缺参机型，stale=重抓参数陈旧机型（T87），共用目标列表与模型选择 ----
const {
  open: scrapeOpen,
  targets: scrapeTargets,
  loading: scrapeTargetsLoading,
  submitting: scrapeSubmitting,
  modelId: scrapeModelId,
  modelOptions: scrapeModelOptions,
  title: scrapeTitle,
  description: scrapeDescription,
  meta: scrapeMeta,
  emptyText: scrapeEmptyText,
  submitText: scrapeSubmitText,
  start: startScrape,
  confirm: confirmScrape,
} = useScrapeDispatch({ onSubmitted: load })

// ---- 系列订阅抓取（T88）：配置读取与保存由弹窗自己完成 ----
const subDialog = ref(false)
const subSeries = ref<DeviceSeries | null>(null)

function openSubscription(row: DeviceSeries) {
  if (!row.id) return
  subSeries.value = row
  subDialog.value = true
}

/** 完善度未满 100% 才提供补抓入口 */
function needsBackfill(row: DeviceSeries) {
  return (row.deviceCount ?? 0) > (row.paramDeviceCount ?? 0)
}

async function openScrape(row: DeviceSeries) {
  await openScrapeDialog(row, 'missing')
}

/** T87 重抓全系列：目标为无参数或参数超 180 天未更新的机型 */
async function openStaleScrape(row: DeviceSeries) {
  await openScrapeDialog(row, 'stale')
}

async function openScrapeDialog(row: DeviceSeries, mode: 'missing' | 'stale') {
  if (!row.id) return
  const seriesName = row.name
  const brandName = brands.value.find((b) => b.id === row.brandId)?.name ?? ''
  const seriesId = row.id
  await startScrape({
    // 目标与模型在 start 内部并行拉取，弹窗先开、数据后到
    load: async () =>
      (mode === 'stale' ? await adminApi.seriesStaleDevices(seriesId) : await adminApi.seriesMissingDevices(seriesId))
        .map((target: SeriesMissingDevice) => ({
          id: target.id,
          name: target.name,
          brandName,
          status: target.status,
        })),
    title: mode === 'stale' ? '重抓全系列（参数陈旧）' : '补抓缺参机型',
    description:
      mode === 'stale'
        ? `对「${seriesName}」内超过 180 天未更新或没有参数值的机型重新抓取，结果进入待审核`
        : `对「${seriesName}」内没有参数值的机型发起抓取，结果进入待审核`,
    meta: 'status',
    emptyText:
      mode === 'stale' ? '系列内没有参数陈旧的机型，暂无需重抓。' : '系列内所有机型都已有参数值，无需补抓。',
    successMessage: (count) => `已为「${seriesName}」提交 ${count} 个抓取任务，结果将进入待审核`,
  })
}
</script>

<template>
  <div class="space-y-4">
    <PageHeader title="系列管理" description="按产品线维护系列，机型数决定世代回顾能否按代查看">
      <template #actions>
        <Button size="sm" variant="outline" @click="openSuggest">
          <Sparkles />
          AI 推荐系列
        </Button>
        <Button size="sm" @click="openCreate">
          <Plus />
          新增系列
        </Button>
      </template>
    </PageHeader>

    <Card>
      <CardContent class="flex flex-col gap-2 p-4 sm:flex-row sm:items-center">
        <Select v-model="brandId" :options="brandOptions" placeholder="全部品牌" class="w-full sm:w-48" clearable />
        <span class="text-xs text-muted-foreground">共 {{ list.length }} 个系列，未归类机型 {{ unassigned.length }} 台</span>
      </CardContent>
    </Card>

    <Card>
      <CardContent class="p-0">
        <div v-if="loading" class="space-y-2 p-4">
          <Skeleton v-for="i in 5" :key="i" class="h-10" />
        </div>

        <Table v-else-if="list.length">
          <TableHeader>
            <TableRow class="hover:bg-transparent">
              <TableHead class="w-10" />
              <TableHead class="w-16">ID</TableHead>
              <TableHead>系列名称</TableHead>
              <TableHead class="w-24">品牌</TableHead>
              <TableHead class="w-28">完善度</TableHead>
              <TableHead class="w-28">年份范围</TableHead>
              <TableHead class="w-20">状态</TableHead>
              <TableHead class="w-72 text-right">操作</TableHead>
            </TableRow>
          </TableHeader>
          <VueDraggable v-model="list" tag="tbody" handle=".drag-handle" class="[&_tr:last-child]:border-0" @end="saveOrder">
            <TableRow v-for="row in list" :key="row.id ?? row.name">
              <TableCell>
                <button
                  type="button"
                  class="drag-handle cursor-grab text-muted-foreground/60 hover:text-foreground active:cursor-grabbing"
                  aria-label="拖拽排序"
                >
                  <GripVertical class="h-4 w-4" />
                </button>
              </TableCell>
              <TableCell class="text-muted-foreground">{{ row.id }}</TableCell>
              <TableCell class="font-medium">{{ row.name }}</TableCell>
              <TableCell class="text-muted-foreground">{{ row.brandName || '—' }}</TableCell>
              <TableCell>
                <div class="flex items-center gap-1.5">
                  <span class="text-muted-foreground">带参 {{ row.paramDeviceCount ?? 0 }}/{{ row.deviceCount ?? 0 }}</span>
                  <Badge
                    v-if="needsBackfill(row)"
                    variant="warning"
                    title="系列内仍有机型没有参数值，可一键补抓"
                  >
                    缺参
                  </Badge>
                </div>
              </TableCell>
              <TableCell class="text-muted-foreground">{{ yearRange(row) }}</TableCell>
              <TableCell>
                <Badge :variant="row.status === 1 ? 'success' : 'secondary'">{{ row.status === 1 ? '启用' : '停用' }}</Badge>
              </TableCell>
              <TableCell class="text-right">
                <div class="flex justify-end gap-1">
                  <Button
                    variant="ghost"
                    size="icon-sm"
                    aria-label="机型清单"
                    title="查看系列内机型，逐行或批量移动归属"
                    @click="openDevices(row)"
                  >
                    <Layers />
                  </Button>
                  <Button
                    variant="ghost"
                    size="icon-sm"
                    aria-label="补抓缺参机型"
                    title="对系列内没有参数值的机型发起抓取"
                    :disabled="!needsBackfill(row)"
                    @click="openScrape(row)"
                  >
                    <CloudDownload />
                  </Button>
                  <Button
                    variant="ghost"
                    size="icon-sm"
                    aria-label="重抓全系列"
                    title="对系列内参数超过 180 天未更新或没有参数值的机型重新抓取"
                    :disabled="!row.deviceCount"
                    @click="openStaleScrape(row)"
                  >
                    <History />
                  </Button>
                  <Button
                    variant="ghost"
                    size="icon-sm"
                    aria-label="订阅抓取"
                    title="按周期自动创建系列发现任务"
                    @click="openSubscription(row)"
                  >
                    <BellRing />
                  </Button>
                  <Button
                    variant="ghost"
                    size="icon-sm"
                    aria-label="系列发现"
                    title="到抓取任务页发现该系列的更多机型"
                    @click="openDiscovery(row)"
                  >
                    <Radar />
                  </Button>
                  <Button variant="ghost" size="icon-sm" aria-label="编辑" title="编辑" @click="openEdit(row)">
                    <Pencil />
                  </Button>
                  <Button variant="ghost" size="icon-sm" aria-label="合并" title="合并到其它系列" @click="openMerge(row)">
                    <Combine />
                  </Button>
                  <Button
                    variant="ghost"
                    size="icon-sm"
                    class="text-destructive"
                    aria-label="删除"
                    title="删除"
                    @click="deleteTarget = row"
                  >
                    <Trash2 />
                  </Button>
                </div>
              </TableCell>
            </TableRow>
          </VueDraggable>
        </Table>

        <Empty v-else title="还没有系列" description="新增系列后，机型就可以按产品线归类并参与世代回顾。" />
      </CardContent>
    </Card>

    <Card>
      <CardContent class="p-0">
        <div class="flex flex-wrap items-center gap-2 border-b p-4">
          <div>
            <h2 class="text-sm font-semibold">未归类机型</h2>
            <p class="text-xs text-muted-foreground">选择系列后立即归类，未归类机型不会出现在世代回顾中</p>
          </div>
        </div>

        <Table v-if="unassigned.length">
          <TableHeader>
            <TableRow class="hover:bg-transparent">
              <TableHead class="min-w-40">机型</TableHead>
              <TableHead class="w-24">品牌</TableHead>
              <TableHead class="w-32">发布时间</TableHead>
              <TableHead class="min-w-44">归类到系列</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            <TableRow v-for="device in unassigned" :key="device.id">
              <TableCell class="font-medium">{{ device.name }}</TableCell>
              <TableCell class="text-muted-foreground">{{ device.brandName || '—' }}</TableCell>
              <TableCell class="text-muted-foreground">{{ device.releaseDate || '—' }}</TableCell>
              <TableCell>
                <Select
                  :model-value="undefined"
                  :options="seriesOptionsOf(device.brandId)"
                  :disabled="assigningId === device.id"
                  placeholder="选择系列"
                  aria-label="归类到系列"
                  @update:model-value="onAssignChange(device.id, $event)"
                />
              </TableCell>
            </TableRow>
          </TableBody>
        </Table>

        <Empty v-else title="没有未归类机型" description="所有机型都已归入系列，可回访客端「世代回顾」查看走势。" />
      </CardContent>
    </Card>

    <SeriesDevicesSheet
      :open="devicesOpen"
      :series="devicesSeries"
      :series-list="list"
      @update:open="devicesOpen = $event"
      @changed="onDevicesChanged"
    />

    <Dialog v-model:open="dialog" :title="form.id ? '编辑系列' : '新增系列'" class="max-w-md">
      <div class="space-y-4">
        <div class="space-y-1.5">
          <Label for="series-name">系列名称</Label>
          <Input id="series-name" v-model="form.name" placeholder="如 iPhone 数字系列" />
        </div>
        <div class="space-y-1.5">
          <Label for="series-brand">品牌</Label>
          <Select
            id="series-brand"
            v-model="form.brandId"
            :options="brandOptions"
            :disabled="!!form.id"
            placeholder="选择品牌"
          />
          <p v-if="form.id" class="text-xs text-muted-foreground">
            系列已有归属品牌，不可修改；如需调整请新建系列后把机型重新归类
          </p>
        </div>
        <div class="space-y-1.5">
          <Label for="series-sort">排序</Label>
          <Input id="series-sort" v-model.number="form.sortOrder" type="number" min="0" />
        </div>
        <div class="flex items-center justify-between rounded-md border p-3">
          <div>
            <div class="text-sm font-medium">启用系列</div>
            <div class="text-xs text-muted-foreground">停用后访客端世代回顾不再展示该系列</div>
          </div>
          <Switch :model-value="form.status === 1" @update:model-value="form.status = $event ? 1 : 0" />
        </div>
      </div>
      <template #footer>
        <Button variant="outline" :disabled="saving" @click="dialog = false">取消</Button>
        <Button :disabled="saving" @click="save">{{ saving ? '保存中…' : '保存' }}</Button>
      </template>
    </Dialog>

    <SeriesMergeDialog
      :open="mergeDialog"
      :source="mergeSource"
      :series-list="list"
      @update:open="mergeDialog = $event"
      @merged="onMerged"
    />

    <SeriesSuggestDialog
      :open="suggestDialog"
      :brands="brands"
      :sort-order-base="suggestSortOrderBase"
      :default-brand-id="brandId"
      @update:open="suggestDialog = $event"
      @created="onSuggestCreated"
    />

    <SeriesSubscriptionDialog
      :open="subDialog"
      :series="subSeries"
      @update:open="subDialog = $event"
    />

    <ScrapeModelDialog
      :open="scrapeOpen"
      :title="scrapeTitle"
      :description="scrapeDescription"
      :targets="scrapeTargets"
      :loading="scrapeTargetsLoading"
      :submitting="scrapeSubmitting"
      :model-id="scrapeModelId"
      :model-options="scrapeModelOptions"
      :meta="scrapeMeta"
      :empty-text="scrapeEmptyText"
      :submit-text="scrapeSubmitText"
      @update:open="scrapeOpen = $event"
      @update:model-id="scrapeModelId = $event"
      @confirm="confirmScrape"
    />

    <ConfirmDialog
      :open="!!deleteTarget"
      title="删除系列"
      description="删除后该系列不再出现在世代回顾中，该系列的订阅抓取配置会一并删除。系列下仍有在册机型时会被拒绝，请先合并或迁移。"
      confirm-text="删除"
      destructive
      :loading="deleting"
      @update:open="deleteTarget = null"
      @confirm="confirmDelete"
    />
  </div>
</template>

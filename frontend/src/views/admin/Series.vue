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
  Loader2,
  Pencil,
  Plus,
  Radar,
  RefreshCw,
  Sparkles,
  Trash2,
} from '@lucide/vue'
import { toast } from 'vue-sonner'
import { useRouter } from 'vue-router'
import { adminApi } from '@/api'
import type {
  AiModel,
  Brand,
  DeviceSeries,
  SeriesDevice,
  SeriesMissingDevice,
  SeriesSuggestion,
  UnassignedDevice,
} from '@/types/api'
import {
  Badge,
  Button,
  Card,
  CardContent,
  Checkbox,
  Combobox,
  Dialog,
  Empty,
  Input,
  Label,
  Select,
  Sheet,
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
const mergeTargetId = ref<number>()
const merging = ref(false)

// ---- AI 推荐系列：按品牌生成产品线候选，勾选后批量新建 ----
const suggestDialog = ref(false)
const suggestBrandId = ref<number>()
const suggestLoading = ref(false)
const suggestions = ref<SeriesSuggestion[]>([])
const selectedSuggestions = ref<string[]>([])
const suggestCreating = ref(false)

const suggestBrandOptions = computed(() => brands.value.map((b) => ({ label: b.name, value: b.id })))
const creatableSuggestions = computed(() => suggestions.value.filter((s) => !s.existing))
const allSuggestionsSelected = computed(
  () =>
    creatableSuggestions.value.length > 0 &&
    creatableSuggestions.value.every((s) => selectedSuggestions.value.includes(s.name)),
)

function openSuggest() {
  suggestBrandId.value = brandId.value ?? brands.value[0]?.id
  suggestions.value = []
  selectedSuggestions.value = []
  suggestDialog.value = true
}

async function fetchSuggestions() {
  if (!suggestBrandId.value) {
    toast.warning('请先选择品牌')
    return
  }
  suggestLoading.value = true
  try {
    suggestions.value = await adminApi.suggestSeries(suggestBrandId.value)
    // 默认全选可创建项；已存在项禁选
    selectedSuggestions.value = suggestions.value.filter((s) => !s.existing).map((s) => s.name)
    if (!suggestions.value.length) toast.warning('AI 未给出候选，请重试或手动新建')
  } finally {
    suggestLoading.value = false
  }
}

function toggleSuggestion(name: string, checked: boolean) {
  if (checked) {
    if (!selectedSuggestions.value.includes(name)) selectedSuggestions.value.push(name)
  } else {
    selectedSuggestions.value = selectedSuggestions.value.filter((n) => n !== name)
  }
}

async function createSelectedSeries() {
  if (!suggestBrandId.value || !selectedSuggestions.value.length) return
  suggestCreating.value = true
  try {
    // 逐个走既有 save 校验（同名拦截），排序号顺延
    let created = 0
    for (const name of selectedSuggestions.value) {
      await adminApi.saveSeries({
        brandId: suggestBrandId.value,
        name,
        sortOrder: list.value.filter((s) => s.brandId === suggestBrandId.value).length + 1 + created,
        status: 1,
      })
      created++
    }
    toast.success(`已创建 ${created} 个系列`)
    suggestDialog.value = false
    brandId.value = suggestBrandId.value
    await load()
  } finally {
    suggestCreating.value = false
  }
}

const brandOptions = computed(() => brands.value.map((b) => ({ label: b.name, value: b.id })))

const mergeOptions = computed(() =>
  list.value
    .filter((item) => item.id && item.brandId === mergeSource.value?.brandId && item.id !== mergeSource.value?.id)
    .map((item) => ({ label: item.name, value: item.id as number })),
)

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
  mergeTargetId.value = undefined
  mergeDialog.value = true
}

async function confirmMerge() {
  if (!mergeSource.value?.id || !mergeTargetId.value) {
    toast.warning('请选择目标系列')
    return
  }
  merging.value = true
  try {
    const moved = await adminApi.mergeSeries(mergeSource.value.id, mergeTargetId.value)
    toast.success(`已合并，迁移 ${moved} 台机型`)
    mergeDialog.value = false
    await load()
  } finally {
    merging.value = false
  }
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

// ---- 系列内机型清单：核对归属，把误分机型移到其它系列（复用 assign，无需新写入接口） ----
/** 系列下拉里「未归类」的哨兵值：reka-ui 不接受空串作为选项值 */
const UNASSIGNED = 0
const devicesOpen = ref(false)
const devicesSeries = ref<DeviceSeries | null>(null)
const seriesDeviceList = ref<SeriesDevice[]>([])
const devicesLoading = ref(false)
const deviceKeyword = ref('')
const movingDeviceId = ref<number>()
const selectedDevices = ref<number[]>([])
const batchTargetId = ref<number>()
const batchMoving = ref(false)

const filteredSeriesDevices = computed(() => {
  const keyword = deviceKeyword.value.trim().toLowerCase()
  return keyword ? seriesDeviceList.value.filter((d) => d.name.toLowerCase().includes(keyword)) : seriesDeviceList.value
})

const allSeriesDevicesSelected = computed(
  () =>
    filteredSeriesDevices.value.length > 0 &&
    filteredSeriesDevices.value.every((d) => selectedDevices.value.includes(d.id)),
)

/** 逐行下拉：同品牌全部系列（含当前）+ 未归类，当前项即当前归属 */
function moveOptionsOf(device: SeriesDevice) {
  return [...seriesOptionsOf(device.brandId), { label: '未归类（移出系列）', value: UNASSIGNED }]
}

/** 批量下拉排除当前系列：批量「移到本系列」没有意义 */
const batchMoveOptions = computed(() => {
  const current = devicesSeries.value
  if (!current) return []
  return [
    ...list.value
      .filter((item) => item.id !== current.id && item.brandId === current.brandId)
      .map((item) => ({ label: item.name, value: item.id as number })),
    { label: '未归类（移出系列）', value: UNASSIGNED },
  ]
})

function seriesNameOrUnassigned(id?: number) {
  if (!id) return '未归类'
  return list.value.find((item) => item.id === id)?.name ?? '目标系列'
}

async function openDevices(row: DeviceSeries) {
  if (!row.id) return
  devicesSeries.value = row
  deviceKeyword.value = ''
  selectedDevices.value = []
  batchTargetId.value = undefined
  seriesDeviceList.value = []
  devicesOpen.value = true
  await loadSeriesDevices()
}

async function loadSeriesDevices() {
  const series = devicesSeries.value
  if (!series?.id) return
  devicesLoading.value = true
  try {
    seriesDeviceList.value = await adminApi.seriesDevices(series.id)
    // 移走的机型已不在清单里，顺手剔除残留选中项，避免批量操作打到别的记录
    const ids = new Set(seriesDeviceList.value.map((d) => d.id))
    selectedDevices.value = selectedDevices.value.filter((id) => ids.has(id))
  } finally {
    devicesLoading.value = false
  }
}

function toggleDevice(id: number, checked: boolean) {
  if (checked) {
    if (!selectedDevices.value.includes(id)) selectedDevices.value.push(id)
  } else {
    selectedDevices.value = selectedDevices.value.filter((item) => item !== id)
  }
}

function toggleAllDevices(checked: boolean) {
  selectedDevices.value = checked ? filteredSeriesDevices.value.map((d) => d.id) : []
}

function clearDeviceSelection() {
  selectedDevices.value = []
  batchTargetId.value = undefined
}

/** 单台移动即时生效：Toast 写明原系列与新系列，选错可再选回去 */
async function onMoveDevice(device: SeriesDevice, value: unknown) {
  if (typeof value !== 'number') return
  if (value === (device.seriesId ?? UNASSIGNED)) return
  movingDeviceId.value = device.id
  try {
    await adminApi.assignSeries([device.id], value === UNASSIGNED ? undefined : value)
    toast.success(
      `已把「${device.name}」从「${devicesSeries.value?.name ?? '原系列'}」移到「${seriesNameOrUnassigned(value)}」`,
    )
    await Promise.all([loadSeriesDevices(), load()])
  } finally {
    movingDeviceId.value = undefined
  }
}

async function moveSelectedDevices() {
  if (!selectedDevices.value.length || batchTargetId.value === undefined || batchMoving.value) return
  const target = batchTargetId.value
  batchMoving.value = true
  try {
    const updated = await adminApi.assignSeries([...selectedDevices.value], target === UNASSIGNED ? undefined : target)
    toast.success(`已把 ${updated} 台机型从「${devicesSeries.value?.name ?? '原系列'}」移到「${seriesNameOrUnassigned(target)}」`)
    clearDeviceSelection()
    await Promise.all([loadSeriesDevices(), load()])
  } finally {
    batchMoving.value = false
  }
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
const scrapeDialog = ref(false)
const scrapeMode = ref<'missing' | 'stale'>('missing')
const scrapeSeries = ref<DeviceSeries | null>(null)
const scrapeTargets = ref<SeriesMissingDevice[]>([])
const scrapeTargetsLoading = ref(false)
const scrapeModelId = ref<number | null>(null)
const scrapeSubmitting = ref(false)
const aiModels = ref<AiModel[]>([])

const scrapeTitle = computed(() => (scrapeMode.value === 'stale' ? '重抓全系列（参数陈旧）' : '补抓缺参机型'))
const scrapeDescription = computed(() =>
  scrapeMode.value === 'stale'
    ? `对「${scrapeSeries.value?.name ?? ''}」内超过 180 天未更新或没有参数值的机型重新抓取，结果进入待审核`
    : `对「${scrapeSeries.value?.name ?? ''}」内没有参数值的机型发起抓取，结果进入待审核`,
)

// ---- 系列订阅抓取（T88）：到期自动创建系列发现任务，发布季不掉队 ----
const subDialog = ref(false)
const subSeries = ref<DeviceSeries | null>(null)
const subLoading = ref(false)
const subSaving = ref(false)
const subForm = reactive({ intervalMonths: 3, aiModelId: null as number | null, enabled: true })
const subIntervalOptions = [
  { label: '每 1 个月', value: '1' },
  { label: '每 2 个月', value: '2' },
  { label: '每 3 个月', value: '3' },
  { label: '每 6 个月', value: '6' },
  { label: '每 12 个月', value: '12' },
]

async function openSubscription(row: DeviceSeries) {
  if (!row.id) return
  subSeries.value = row
  subForm.intervalMonths = 3
  subForm.aiModelId = null
  subForm.enabled = true
  subDialog.value = true
  subLoading.value = true
  try {
    const [existing, models] = await Promise.all([
      adminApi.seriesSubscription(row.id),
      aiModels.value.length ? Promise.resolve(aiModels.value) : adminApi.aiModels(),
    ])
    aiModels.value = models
    if (existing) {
      subForm.intervalMonths = existing.intervalMonths
      subForm.aiModelId = existing.aiModelId ?? null
      subForm.enabled = existing.enabled === 1
    } else {
      const def = models.find((m) => m.isDefault === 1)
      subForm.aiModelId = def?.id ?? null
    }
  } finally {
    subLoading.value = false
  }
}

async function saveSubscription() {
  const series = subSeries.value
  if (!series || subSaving.value) return
  subSaving.value = true
  try {
    await adminApi.saveSeriesSubscription(series.id, {
      // Select 的值是字符串，落库前转回数字
      intervalMonths: Number(subForm.intervalMonths),
      aiModelId: subForm.aiModelId ?? undefined,
      enabled: subForm.enabled,
    })
    toast.success(subForm.enabled ? `已订阅「${series.name}」，到期自动发起系列发现` : '订阅已暂停')
    subDialog.value = false
  } finally {
    subSaving.value = false
  }
}

const scrapeModelOptions = computed(() =>
  aiModels.value.map((m) => ({
    label: `${m.configName} / ${m.model}${m.isDefault === 1 ? '（默认）' : ''}`,
    value: m.id,
  })),
)

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
  scrapeMode.value = mode
  scrapeSeries.value = row
  scrapeTargets.value = []
  scrapeDialog.value = true
  scrapeTargetsLoading.value = true
  try {
    const [targets, models] = await Promise.all([
      mode === 'stale' ? adminApi.seriesStaleDevices(row.id) : adminApi.seriesMissingDevices(row.id),
      aiModels.value.length ? Promise.resolve(aiModels.value) : adminApi.aiModels(),
    ])
    scrapeTargets.value = targets
    aiModels.value = models
    const def = models.find((m) => m.isDefault === 1)
    scrapeModelId.value = def?.id ?? null
  } finally {
    scrapeTargetsLoading.value = false
  }
}

async function confirmScrape() {
  const series = scrapeSeries.value
  if (!series || !scrapeTargets.value.length || scrapeSubmitting.value) return
  scrapeSubmitting.value = true
  try {
    const brandName = brands.value.find((b) => b.id === series.brandId)?.name ?? ''
    // 逐台顺序提交，与机型页批量抓取一致，避免一次并发打满任务队列
    for (const target of scrapeTargets.value) {
      await adminApi.createAgentTask({
        modelName: target.name,
        brandName,
        aiModelId: scrapeModelId.value ?? undefined,
      })
    }
    toast.success(`已为「${series.name}」提交 ${scrapeTargets.value.length} 个抓取任务，结果将进入待审核`)
    scrapeDialog.value = false
    await load()
  } finally {
    scrapeSubmitting.value = false
  }
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

    <Sheet
      v-model:open="devicesOpen"
      title="系列机型清单"
      :description="`「${devicesSeries?.name ?? ''}」共 ${seriesDeviceList.length} 台，选择其它系列即可移动归属`"
      side="right"
      class="w-[92vw] sm:w-[560px]"
    >
      <div class="space-y-3">
        <div class="flex flex-wrap items-center gap-2">
          <Input v-model="deviceKeyword" placeholder="搜索机型名" class="w-full sm:w-52" />
          <Button variant="outline" size="sm" :disabled="devicesLoading" @click="loadSeriesDevices">
            <RefreshCw />
            刷新
          </Button>
          <span class="ml-auto text-xs text-muted-foreground">
            {{ filteredSeriesDevices.length }} / {{ seriesDeviceList.length }} 台
          </span>
        </div>

        <div
          v-if="selectedDevices.length"
          class="flex flex-wrap items-center gap-2 rounded-lg border bg-muted/40 px-3 py-2 text-sm"
        >
          <span class="font-medium">已选 {{ selectedDevices.length }} 台</span>
          <Select v-model="batchTargetId" :options="batchMoveOptions" placeholder="移到系列" class="w-40" />
          <Button size="sm" :disabled="batchMoving || batchTargetId === undefined" @click="moveSelectedDevices">
            <Loader2 v-if="batchMoving" class="animate-spin" />
            移动所选
          </Button>
          <Button size="sm" variant="ghost" :disabled="batchMoving" @click="clearDeviceSelection">取消选择</Button>
        </div>

        <div v-if="devicesLoading" class="space-y-2">
          <Skeleton v-for="i in 5" :key="i" class="h-10" />
        </div>

        <Table v-else-if="filteredSeriesDevices.length">
          <TableHeader>
            <TableRow class="hover:bg-transparent">
              <TableHead class="w-10">
                <Checkbox
                  :model-value="allSeriesDevicesSelected"
                  aria-label="全选本系列机型"
                  @update:model-value="toggleAllDevices"
                />
              </TableHead>
              <TableHead>机型</TableHead>
              <TableHead class="w-20">发布年</TableHead>
              <TableHead class="w-20">状态</TableHead>
              <TableHead class="w-36">移动到</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            <TableRow v-for="device in filteredSeriesDevices" :key="device.id">
              <TableCell>
                <Checkbox
                  :model-value="selectedDevices.includes(device.id)"
                  :aria-label="`选择 ${device.name}`"
                  @update:model-value="toggleDevice(device.id, $event)"
                />
              </TableCell>
              <TableCell class="font-medium">{{ device.name }}</TableCell>
              <TableCell class="text-muted-foreground">
                {{ device.releaseDate ? device.releaseDate.slice(0, 4) : '—' }}
              </TableCell>
              <TableCell>
                <Badge :variant="device.status === 1 ? 'success' : 'secondary'">
                  {{ device.status === 1 ? '已上架' : '草稿' }}
                </Badge>
              </TableCell>
              <TableCell>
                <Select
                  :model-value="device.seriesId ?? UNASSIGNED"
                  :options="moveOptionsOf(device)"
                  :disabled="movingDeviceId === device.id"
                  :aria-label="`移动 ${device.name} 到其它系列`"
                  @update:model-value="onMoveDevice(device, $event)"
                />
              </TableCell>
            </TableRow>
          </TableBody>
        </Table>

        <Empty v-else title="该系列暂无机型" description="到机型管理或未归类清单把机型归入本系列。" />

        <p class="text-xs leading-5 text-muted-foreground">
          移动立即生效：机型会从当前系列移除并归入目标系列，选错可再选回去；「未归类」用于暂时移出。系列与机型必须同品牌，因此下拉只列同品牌系列。
        </p>
      </div>
    </Sheet>

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

    <Dialog v-model:open="mergeDialog" title="合并系列" class="max-w-md">
      <div class="space-y-3">
        <p class="text-sm text-muted-foreground">
          把「{{ mergeSource?.name }}」下的机型迁移到目标系列，然后删除当前系列。
        </p>
        <div class="space-y-1.5">
          <Label>目标系列</Label>
          <Select v-model="mergeTargetId" :options="mergeOptions" placeholder="选择同品牌系列" />
        </div>
        <p v-if="!mergeOptions.length" class="text-xs text-destructive">该品牌下没有其它可合并的系列。</p>
      </div>
      <template #footer>
        <Button variant="outline" :disabled="merging" @click="mergeDialog = false">取消</Button>
        <Button :disabled="merging || !mergeOptions.length" @click="confirmMerge">
          {{ merging ? '合并中…' : '合并' }}
        </Button>
      </template>
    </Dialog>

    <Dialog
      v-model:open="suggestDialog"
      title="AI 推荐系列"
      description="AI 按品牌产品线给出候选系列名，勾选后一键创建；同名已存在会标注且不可选"
      class="max-w-xl"
    >
      <div class="space-y-3">
        <div class="flex items-center gap-2">
          <Select v-model="suggestBrandId" :options="suggestBrandOptions" placeholder="选择品牌" class="flex-1" />
          <Button variant="outline" size="sm" :disabled="suggestLoading" @click="fetchSuggestions">
            <Loader2 v-if="suggestLoading" class="animate-spin" />
            <Sparkles v-else />
            生成推荐
          </Button>
        </div>

        <div v-if="suggestLoading" class="space-y-2 py-2">
          <Skeleton v-for="i in 5" :key="i" class="h-12" />
        </div>

        <template v-else-if="suggestions.length">
          <div class="max-h-80 space-y-1 overflow-y-auto rounded-md border p-2">
            <label
              v-for="item in suggestions"
              :key="item.name"
              class="flex items-start gap-2.5 rounded-sm px-2 py-1.5 hover:bg-accent"
              :class="item.existing && 'cursor-default opacity-60'"
            >
              <Checkbox
                class="mt-0.5"
                :model-value="selectedSuggestions.includes(item.name)"
                :disabled="item.existing"
                @update:model-value="toggleSuggestion(item.name, $event)"
              />
              <span class="min-w-0">
                <span class="flex flex-wrap items-center gap-2">
                  <span class="text-sm font-medium">{{ item.name }}</span>
                  <Badge v-if="item.existing" variant="secondary">已存在</Badge>
                </span>
                <span v-if="item.reason" class="mt-0.5 block text-xs text-muted-foreground">{{ item.reason }}</span>
              </span>
            </label>
          </div>
          <p class="text-xs text-muted-foreground">
            已选 {{ selectedSuggestions.length }} / 可创建 {{ creatableSuggestions.length }} 个
          </p>
        </template>

        <p v-else class="rounded-md bg-muted/60 px-3 py-2 text-xs text-muted-foreground">
          选择品牌后点击「生成推荐」，AI 会给出该品牌的产品线划分候选。
        </p>
      </div>
      <template #footer>
        <Button variant="outline" :disabled="suggestCreating" @click="suggestDialog = false">取消</Button>
        <Button
          :disabled="suggestCreating || !selectedSuggestions.length"
          @click="createSelectedSeries"
        >
          <Loader2 v-if="suggestCreating" class="animate-spin" />
          {{ suggestCreating ? '创建中…' : `创建所选（${selectedSuggestions.length}）` }}
        </Button>
      </template>
    </Dialog>

    <Dialog
      v-model:open="subDialog"
      title="订阅抓取"
      :description="`对「${subSeries?.name ?? ''}」按周期自动创建系列发现任务，结果照常进入人工审核`"
      class="max-w-md"
    >
      <div v-if="subLoading" class="space-y-2">
        <Skeleton v-for="i in 2" :key="i" class="h-9" />
      </div>
      <div v-else class="space-y-4">
        <div class="space-y-1.5">
          <Label>抓取周期</Label>
          <Select v-model="subForm.intervalMonths" :options="subIntervalOptions" />
        </div>
        <div class="space-y-1.5">
          <Label>AI 模型（可选）</Label>
          <Combobox
            v-model="subForm.aiModelId"
            :options="scrapeModelOptions"
            placeholder="默认模型"
            search-placeholder="搜索供应商 / 模型…"
          />
        </div>
        <div class="flex items-center justify-between">
          <Label for="subscription-enabled">启用订阅</Label>
          <Switch id="subscription-enabled" v-model="subForm.enabled" />
        </div>
        <p class="text-xs text-muted-foreground">
          每天凌晨检查到期订阅；系列名即发现关键词，发现结果照常进入人工审核。
        </p>
      </div>
      <template #footer>
        <Button variant="outline" :disabled="subSaving" @click="subDialog = false">取消</Button>
        <Button :disabled="subSaving" @click="saveSubscription">
          <Loader2 v-if="subSaving" class="animate-spin" />
          保存订阅
        </Button>
      </template>
    </Dialog>

    <Dialog
      v-model:open="scrapeDialog"
      :title="scrapeTitle"
      :description="scrapeDescription"
      class="max-w-md"
    >
      <div class="space-y-4">
        <div v-if="scrapeTargetsLoading" class="space-y-2">
          <Skeleton v-for="i in 3" :key="i" class="h-8" />
        </div>
        <template v-else>
          <div class="max-h-40 space-y-1 overflow-y-auto rounded-md border p-2">
            <div
              v-for="target in scrapeTargets"
              :key="target.id"
              class="flex items-center justify-between gap-2 px-1 py-0.5 text-sm"
            >
              <span class="min-w-0 truncate font-medium" :title="target.name">{{ target.name }}</span>
              <Badge :variant="target.status === 1 ? 'secondary' : 'outline'">
                {{ target.status === 1 ? '已上架' : '草稿' }}
              </Badge>
            </div>
            <p v-if="!scrapeTargets.length" class="px-1 py-2 text-sm text-muted-foreground">
              {{ scrapeMode === 'stale' ? '系列内没有参数陈旧的机型，暂无需重抓。' : '系列内所有机型都已有参数值，无需补抓。' }}
            </p>
          </div>
          <div class="space-y-1.5">
            <Label>AI 模型（可选）</Label>
            <Combobox
              v-model="scrapeModelId"
              :options="scrapeModelOptions"
              placeholder="默认模型"
              search-placeholder="搜索供应商 / 模型…"
            />
            <p class="text-xs text-muted-foreground">
              选项为「供应商连接 / 模型名」，不选择时使用后台设置的默认模型
            </p>
          </div>
        </template>
      </div>
      <template #footer>
        <Button variant="outline" :disabled="scrapeSubmitting" @click="scrapeDialog = false">取消</Button>
        <Button :disabled="scrapeSubmitting || !scrapeTargets.length" @click="confirmScrape">
          <Loader2 v-if="scrapeSubmitting" class="animate-spin" />
          {{ scrapeSubmitting ? '提交中…' : `提交 ${scrapeTargets.length} 个任务` }}
        </Button>
      </template>
    </Dialog>

    <ConfirmDialog
      :open="!!deleteTarget"
      title="删除系列"
      description="删除后该系列不再出现在世代回顾中。系列下仍有在册机型时会被拒绝，请先合并或迁移。"
      confirm-text="删除"
      destructive
      :loading="deleting"
      @update:open="deleteTarget = null"
      @confirm="confirmDelete"
    />
  </div>
</template>

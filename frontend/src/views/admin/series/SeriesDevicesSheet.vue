<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { Loader2, RefreshCw } from '@lucide/vue'
import { toast } from 'vue-sonner'
import { adminApi } from '@/api'
import type { DeviceSeries, SeriesDevice } from '@/types/api'
import {
  Badge,
  Button,
  Checkbox,
  Empty,
  Input,
  Select,
  Sheet,
  Skeleton,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui'

/**
 * 系列内机型清单：核对归属，把误分机型移到其它系列。
 *
 * <p>移动即时生效（复用 assignSeries），选错可再选回去；「未归类」用于暂时移出系列。
 * 每次移动后通知父级刷新系列列表，否则列表里的「带参 N/M」要等重新进页面才更新。</p>
 */
const props = defineProps<{
  open: boolean
  series: DeviceSeries | null
  seriesList: DeviceSeries[]
}>()

const emit = defineEmits<{
  'update:open': [value: boolean]
  /** 归属变更：父级据此重新拉系列列表与未归类清单 */
  changed: []
}>()

/** 系列下拉里「未归类」的哨兵值：reka-ui 不接受空串作为选项值 */
const UNASSIGNED = 0

const devices = ref<SeriesDevice[]>([])
const loading = ref(false)
const keyword = ref('')
const selected = ref<number[]>([])
const movingId = ref<number>()
const batchTargetId = ref<number>()
const batchMoving = ref(false)

const filtered = computed(() => {
  const k = keyword.value.trim().toLowerCase()
  return k ? devices.value.filter((d) => d.name.toLowerCase().includes(k)) : devices.value
})

const allSelected = computed(
  () => filtered.value.length > 0 && filtered.value.every((d) => selected.value.includes(d.id)),
)

function seriesOptionsOf(brandId: number) {
  return props.seriesList
    .filter((item) => item.id && item.brandId === brandId)
    .map((item) => ({ label: item.name, value: item.id as number }))
}

/** 逐行下拉：同品牌全部系列（含当前）+ 未归类，当前项即当前归属 */
function moveOptionsOf(device: SeriesDevice) {
  return [...seriesOptionsOf(device.brandId), { label: '未归类（移出系列）', value: UNASSIGNED }]
}

/** 批量下拉排除当前系列：批量「移到本系列」没有意义 */
const batchOptions = computed(() => {
  const current = props.series
  if (!current) return []
  return [
    ...props.seriesList
      .filter((item) => item.id !== current.id && item.brandId === current.brandId)
      .map((item) => ({ label: item.name, value: item.id as number })),
    { label: '未归类（移出系列）', value: UNASSIGNED },
  ]
})

function nameOf(id?: number) {
  if (!id) return '未归类'
  return props.seriesList.find((item) => item.id === id)?.name ?? '目标系列'
}

async function loadDevices() {
  const series = props.series
  if (!series?.id) return
  loading.value = true
  try {
    devices.value = await adminApi.seriesDevices(series.id)
    // 移走的机型已不在清单里，顺手剔除残留选中项，避免批量操作打到别的记录
    const ids = new Set(devices.value.map((d) => d.id))
    selected.value = selected.value.filter((id) => ids.has(id))
  } finally {
    loading.value = false
  }
}

watch(() => [props.open, props.series?.id], (value) => {
  if (!props.open || !props.series?.id) return
  keyword.value = ''
  selected.value = []
  batchTargetId.value = undefined
  devices.value = []
  void loadDevices()
})

function toggle(id: number, checked: boolean) {
  if (checked) {
    if (!selected.value.includes(id)) selected.value.push(id)
  } else {
    selected.value = selected.value.filter((item) => item !== id)
  }
}

function toggleAll(checked: boolean) {
  selected.value = checked ? filtered.value.map((d) => d.id) : []
}

function clearSelection() {
  selected.value = []
  batchTargetId.value = undefined
}

/** 单台移动即时生效：Toast 写明原系列与新系列，选错可再选回去 */
async function onMove(device: SeriesDevice, value: unknown) {
  if (typeof value !== 'number') return
  if (value === (device.seriesId ?? UNASSIGNED)) return
  movingId.value = device.id
  try {
    await adminApi.assignSeries([device.id], value === UNASSIGNED ? undefined : value)
    toast.success(`已把「${device.name}」从「${props.series?.name ?? '原系列'}」移到「${nameOf(value)}」`)
    await loadDevices()
    emit('changed')
  } finally {
    movingId.value = undefined
  }
}

async function moveSelected() {
  if (!selected.value.length || batchTargetId.value === undefined || batchMoving.value) return
  const target = batchTargetId.value
  batchMoving.value = true
  try {
    const updated = await adminApi.assignSeries([...selected.value], target === UNASSIGNED ? undefined : target)
    toast.success(`已把 ${updated} 台机型从「${props.series?.name ?? '原系列'}」移到「${nameOf(target)}」`)
    clearSelection()
    await loadDevices()
    emit('changed')
  } finally {
    batchMoving.value = false
  }
}
</script>

<template>
  <Sheet
    :open="props.open"
    title="系列机型清单"
    :description="`「${props.series?.name ?? ''}」共 ${devices.length} 台，选择其它系列即可移动归属`"
    side="right"
    class="w-[92vw] sm:w-[560px]"
    @update:open="emit('update:open', $event)"
  >
    <div class="space-y-3">
      <div class="flex flex-wrap items-center gap-2">
        <Input v-model="keyword" placeholder="搜索机型名" class="w-full sm:w-52" />
        <Button variant="outline" size="sm" :disabled="loading" @click="loadDevices">
          <RefreshCw />
          刷新
        </Button>
        <span class="ml-auto text-xs text-muted-foreground">
          {{ filtered.length }} / {{ devices.length }} 台
        </span>
      </div>

      <div
        v-if="selected.length"
        class="flex flex-wrap items-center gap-2 rounded-lg border bg-muted/40 px-3 py-2 text-sm"
      >
        <span class="font-medium">已选 {{ selected.length }} 台</span>
        <Select v-model="batchTargetId" :options="batchOptions" placeholder="移到系列" class="w-40" />
        <Button size="sm" :disabled="batchMoving || batchTargetId === undefined" @click="moveSelected">
          <Loader2 v-if="batchMoving" class="animate-spin" />
          移动所选
        </Button>
        <Button size="sm" variant="ghost" :disabled="batchMoving" @click="clearSelection">取消选择</Button>
      </div>

      <div v-if="loading" class="space-y-2">
        <Skeleton v-for="i in 5" :key="i" class="h-10" />
      </div>

      <Table v-else-if="filtered.length">
        <TableHeader>
          <TableRow class="hover:bg-transparent">
            <TableHead class="w-10">
              <Checkbox
                :model-value="allSelected"
                aria-label="全选本系列机型"
                @update:model-value="toggleAll"
              />
            </TableHead>
            <TableHead>机型</TableHead>
            <TableHead class="w-20">发布年</TableHead>
            <TableHead class="w-20">状态</TableHead>
            <TableHead class="w-36">移动到</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          <TableRow v-for="device in filtered" :key="device.id">
            <TableCell>
              <Checkbox
                :model-value="selected.includes(device.id)"
                :aria-label="`选择 ${device.name}`"
                @update:model-value="toggle(device.id, $event)"
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
                :disabled="movingId === device.id"
                :aria-label="`移动 ${device.name} 到其它系列`"
                @update:model-value="onMove(device, $event)"
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
</template>

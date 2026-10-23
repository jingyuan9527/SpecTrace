<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { VueDraggable } from 'vue-draggable-plus'
import { Combine, GripVertical, Pencil, Plus, Trash2 } from '@lucide/vue'
import { toast } from 'vue-sonner'
import { adminApi } from '@/api'
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

interface Brand {
  id: number
  name: string
}

interface Series {
  id: number | null
  brandId: number
  brandName?: string
  name: string
  sortOrder: number
  status: number
  deviceCount?: number
  startYear?: number | null
  endYear?: number | null
}

interface UnassignedDevice {
  id: number
  name: string
  brandId: number
  brandName?: string
  releaseDate?: string
}

const brands = ref<Brand[]>([])
const list = ref<Series[]>([])
const unassigned = ref<UnassignedDevice[]>([])
const loading = ref(true)
const saving = ref(false)
const dialog = ref(false)
const form = reactive<Series>({ id: null, brandId: 0, name: '', sortOrder: 0, status: 1 })

const brandId = ref<number>()
const deleteTarget = ref<Series | null>(null)
const deleting = ref(false)
const assigningId = ref<number>()

const mergeDialog = ref(false)
const mergeSource = ref<Series | null>(null)
const mergeTargetId = ref<number>()
const merging = ref(false)

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

function openEdit(row: Series) {
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

function openMerge(row: Series) {
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

function yearRange(row: Series) {
  if (!row.startYear) return '—'
  return row.startYear === row.endYear ? `${row.startYear}` : `${row.startYear}-${row.endYear}`
}
</script>

<template>
  <div class="space-y-4">
    <PageHeader title="系列管理" description="按产品线维护系列，机型数决定世代回顾能否按代查看">
      <template #actions>
        <Button size="sm" @click="openCreate">
          <Plus />
          新增系列
        </Button>
      </template>
    </PageHeader>

    <Card>
      <CardContent class="flex flex-col gap-2 p-4 sm:flex-row sm:items-center">
        <Select v-model="brandId" :options="brandOptions" placeholder="全部品牌" class="w-full sm:w-48" />
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
              <TableHead class="w-20">机型数</TableHead>
              <TableHead class="w-28">年份范围</TableHead>
              <TableHead class="w-20">状态</TableHead>
              <TableHead class="w-32 text-right">操作</TableHead>
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
              <TableCell class="text-muted-foreground">{{ row.deviceCount ?? 0 }}</TableCell>
              <TableCell class="text-muted-foreground">{{ yearRange(row) }}</TableCell>
              <TableCell>
                <Badge :variant="row.status === 1 ? 'success' : 'secondary'">{{ row.status === 1 ? '启用' : '停用' }}</Badge>
              </TableCell>
              <TableCell class="text-right">
                <div class="flex justify-end gap-1">
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

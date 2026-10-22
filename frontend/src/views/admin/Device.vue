<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ImagePlus, Loader2, Pencil, Plus, Search, Smartphone, Trash2 } from '@lucide/vue'
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
  Pagination,
  Select,
  Skeleton,
  Switch,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
  Textarea,
} from '@/components/ui'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import PageHeader from '@/components/PageHeader.vue'
import ImageWithFallback from '@/components/ImageWithFallback.vue'

interface Brand {
  id: number
  name: string
}

interface ParamGroup {
  id: number
  name: string
}

interface ParamDef {
  id: number
  groupId: number
  paramKey: string
  paramName: string
  paramType: string
  enumOptions?: string
  unit?: string
}

interface DeviceVariantRow {
  id?: number
  storage: string
  ram: string
  price: string
}

interface DeviceForm {
  id: number | null
  brandId?: number
  name: string
  releaseDate: string
  thumbnail: string
  description: string
  status: number
  params: Record<string, string>
  variants: DeviceVariantRow[]
}

const list = ref<any[]>([])
const brands = ref<Brand[]>([])
const groups = ref<ParamGroup[]>([])
const defs = ref<ParamDef[]>([])
const total = ref(0)
const loading = ref(true)
const saving = ref(false)
const uploading = ref(false)

const query = reactive({ current: 1, size: 10, keyword: '', brandId: undefined as number | undefined })
const dialog = ref(false)
const deleteTarget = ref<any>(null)
const deleting = ref(false)
const fileInput = ref<HTMLInputElement>()

const form = reactive<DeviceForm>({
  id: null,
  brandId: undefined,
  name: '',
  releaseDate: '',
  thumbnail: '',
  description: '',
  status: 1,
  params: {},
  variants: [],
})

const brandOptions = computed(() => brands.value.map((b) => ({ label: b.name, value: b.id })))
const defsByGroup = computed(() =>
  groups.value
    .map((group) => ({ ...group, defs: defs.value.filter((d) => d.groupId === group.id) }))
    .filter((group) => group.defs.length > 0),
)

async function load() {
  loading.value = true
  try {
    const res = await adminApi.devices({ ...query })
    list.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  ;[brands.value, groups.value, defs.value] = await Promise.all([
    adminApi.brands(),
    adminApi.paramGroups(),
    adminApi.paramDefs(),
  ])
  await load()
})

watch(
  () => query.current,
  () => load(),
)

function search() {
  query.current = 1
  load()
}

function resetForm() {
  Object.assign(form, {
    id: null,
    brandId: brands.value[0]?.id,
    name: '',
    releaseDate: '',
    thumbnail: '',
    description: '',
    status: 1,
    params: {},
    variants: [],
  })
}

function openCreate() {
  resetForm()
  dialog.value = true
}

async function openEdit(row: any) {
  const detail = await adminApi.deviceDetail(row.id)
  Object.assign(form, {
    id: detail.device.id,
    brandId: detail.device.brandId,
    name: detail.device.name,
    releaseDate: detail.device.releaseDate || '',
    thumbnail: detail.device.thumbnail || '',
    description: detail.device.description || '',
    status: detail.device.status ?? 1,
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

async function save() {
  if (!form.name.trim()) {
    toast.warning('请填写机型名称')
    return
  }
  if (!form.brandId) {
    toast.warning('请选择品牌')
    return
  }
  saving.value = true
  try {
    const variants = form.variants
      .filter((v) => v.storage.trim())
      .map((v, index) => ({
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
    await adminApi.saveDevice({ ...form, params: { ...form.params }, variants })
    toast.success('保存成功')
    dialog.value = false
    await load()
  } finally {
    saving.value = false
  }
}

async function confirmDelete() {
  if (!deleteTarget.value) return
  deleting.value = true
  try {
    await adminApi.deleteDevice(deleteTarget.value.id)
    toast.success('已删除')
    deleteTarget.value = null
    await load()
  } finally {
    deleting.value = false
  }
}

async function onFileChange(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0]
  if (!file) return
  uploading.value = true
  try {
    form.thumbnail = await adminApi.upload(file)
    toast.success('图片上传成功')
  } finally {
    uploading.value = false
    if (fileInput.value) fileInput.value.value = ''
  }
}

function addVariant() {
  form.variants.push({ storage: '', ram: '', price: '' })
}

function removeVariant(index: number) {
  form.variants.splice(index, 1)
}

function optionsOf(def: ParamDef): string[] {
  try {
    const parsed = JSON.parse(def.enumOptions || '[]')
    return Array.isArray(parsed) ? parsed.map(String) : []
  } catch {
    return []
  }
}

function brandName(id: number) {
  return brands.value.find((b) => b.id === id)?.name ?? '—'
}
</script>

<template>
  <div class="space-y-4">
    <PageHeader title="机型管理" description="维护机型基础信息与参数值，支持按品牌和关键词筛选">
      <template #actions>
        <Button size="sm" @click="openCreate">
          <Plus />
          新增机型
        </Button>
      </template>
    </PageHeader>

    <Card>
      <CardContent class="flex flex-col gap-2 p-4 lg:flex-row lg:items-center">
        <div class="relative w-full lg:w-60">
          <Search class="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
          <Input v-model="query.keyword" class="pl-9" placeholder="机型名称" @keyup.enter="search" />
        </div>
        <Select v-model="query.brandId" :options="brandOptions" placeholder="全部品牌" class="w-full lg:w-44" />
        <Button variant="outline" @click="search">查询</Button>
        <div class="ml-auto text-xs text-muted-foreground">共 {{ total }} 台机型</div>
      </CardContent>
    </Card>

    <Card>
      <CardContent class="p-0">
        <div v-if="loading" class="space-y-2 p-4">
          <Skeleton v-for="i in 6" :key="i" class="h-11" />
        </div>

        <Table v-else-if="list.length">
          <TableHeader>
            <TableRow class="hover:bg-transparent">
              <TableHead class="w-16">ID</TableHead>
              <TableHead class="w-20">图片</TableHead>
              <TableHead>机型名称</TableHead>
              <TableHead class="w-28">品牌</TableHead>
              <TableHead class="w-32">发布时间</TableHead>
              <TableHead class="w-24">状态</TableHead>
              <TableHead class="w-28 text-right">操作</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            <TableRow v-for="row in list" :key="row.id">
              <TableCell class="text-muted-foreground">{{ row.id }}</TableCell>
              <TableCell>
                <ImageWithFallback :src="row.thumbnail" :alt="row.name" img-class="h-9 max-w-16 object-contain">
                  <template #fallback><Smartphone class="h-6 w-6 text-muted-foreground/40" /></template>
                </ImageWithFallback>
              </TableCell>
              <TableCell class="font-medium">{{ row.name }}</TableCell>
              <TableCell class="text-muted-foreground">{{ brandName(row.brandId) }}</TableCell>
              <TableCell class="text-muted-foreground">{{ row.releaseDate || '—' }}</TableCell>
              <TableCell>
                <Badge :variant="row.status === 1 ? 'success' : 'secondary'">{{ row.status === 1 ? '上架' : '下架' }}</Badge>
              </TableCell>
              <TableCell class="text-right">
                <div class="flex justify-end gap-1">
                  <Button variant="ghost" size="icon-sm" aria-label="编辑" @click="openEdit(row)">
                    <Pencil />
                  </Button>
                  <Button variant="ghost" size="icon-sm" class="text-destructive" aria-label="删除" @click="deleteTarget = row">
                    <Trash2 />
                  </Button>
                </div>
              </TableCell>
            </TableRow>
          </TableBody>
        </Table>

        <Empty v-else title="暂无机型" description="新增机型后，访客端机型库会立即展示。" />
      </CardContent>
    </Card>

    <Pagination v-if="total > query.size" v-model:page="query.current" :page-size="query.size" :total="total" />

    <Dialog v-model:open="dialog" :title="form.id ? '编辑机型' : '新增机型'" class="max-w-2xl">
      <div class="space-y-5">
        <div class="grid gap-4 sm:grid-cols-2">
          <div class="space-y-1.5">
            <Label for="device-name">机型名称</Label>
            <Input id="device-name" v-model="form.name" placeholder="如 iPhone 17 Pro" />
          </div>
          <div class="space-y-1.5">
            <Label>品牌</Label>
            <Select v-model="form.brandId" :options="brandOptions" placeholder="选择品牌" />
          </div>
          <div class="space-y-1.5">
            <Label for="device-date">发布时间</Label>
            <Input id="device-date" v-model="form.releaseDate" type="date" />
          </div>
          <div class="space-y-1.5">
            <Label>缩略图</Label>
            <div class="flex items-center gap-2">
              <Button variant="outline" size="sm" :disabled="uploading" @click="fileInput?.click()">
                <Loader2 v-if="uploading" class="animate-spin" />
                <ImagePlus v-else />
                {{ uploading ? '上传中…' : '上传图片' }}
              </Button>
              <img v-if="form.thumbnail" :src="form.thumbnail" alt="缩略图预览" class="h-9 max-w-16 object-contain" />
              <input ref="fileInput" type="file" accept="image/*" class="hidden" @change="onFileChange" />
            </div>
          </div>
          <div class="space-y-1.5 sm:col-span-2">
            <Label for="device-desc">简介</Label>
            <Textarea id="device-desc" v-model="form.description" placeholder="一句话描述该机型的定位与卖点" />
          </div>
          <div class="flex items-center justify-between rounded-md border p-3 sm:col-span-2">
            <div>
              <div class="text-sm font-medium">上架展示</div>
              <div class="text-xs text-muted-foreground">下架后访客端不可见</div>
            </div>
            <Switch :model-value="form.status === 1" @update:model-value="form.status = $event ? 1 : 0" />
          </div>
        </div>

        <div class="space-y-3 rounded-md border p-3">
          <div class="flex flex-wrap items-center justify-between gap-2">
            <div>
              <h3 class="text-sm font-semibold">存储版本与发布价</h3>
              <p class="text-xs text-muted-foreground">按容量从小到大排列，价格填官方首发价（元）</p>
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
                <Trash2 />
              </Button>
            </div>
          </div>
          <p v-else class="text-xs text-muted-foreground">尚未添加版本，访客端不会展示价格。</p>
        </div>

        <div v-for="group in defsByGroup" :key="group.id" class="space-y-3">
          <div class="flex items-center gap-2">
            <h3 class="text-sm font-semibold">{{ group.name }}</h3>
            <span class="text-xs text-muted-foreground">{{ group.defs.length }} 项</span>
          </div>
          <div class="grid gap-3 sm:grid-cols-2">
            <div v-for="def in group.defs" :key="def.id" class="space-y-1.5">
              <Label :for="`param-${def.paramKey}`">
                {{ def.paramName }}
                <span v-if="def.unit" class="text-xs font-normal text-muted-foreground">（{{ def.unit }}）</span>
              </Label>
              <Select
                v-if="def.paramType === 'select'"
                :id="`param-${def.paramKey}`"
                v-model="form.params[def.paramKey]"
                :options="optionsOf(def).map((o) => ({ label: o, value: o }))"
                placeholder="请选择"
              />
              <Input
                v-else
                :id="`param-${def.paramKey}`"
                v-model="form.params[def.paramKey]"
                :type="def.paramType === 'number' ? 'number' : def.paramType === 'date' ? 'date' : 'text'"
                :placeholder="def.unit ? `单位：${def.unit}` : '请输入'"
              />
            </div>
          </div>
        </div>
      </div>

      <template #footer>
        <Button variant="outline" :disabled="saving" @click="dialog = false">取消</Button>
        <Button :disabled="saving" @click="save">{{ saving ? '保存中…' : '保存' }}</Button>
      </template>
    </Dialog>

    <ConfirmDialog
      :open="!!deleteTarget"
      title="删除机型"
      :description="`确认删除机型「${deleteTarget?.name ?? ''}」？该机型的所有参数值会一并删除。`"
      confirm-text="删除"
      destructive
      :loading="deleting"
      @update:open="deleteTarget = null"
      @confirm="confirmDelete"
    />
  </div>
</template>

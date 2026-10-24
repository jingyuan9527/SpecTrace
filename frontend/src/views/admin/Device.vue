<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { Copy, Download, FileUp, ImagePlus, Loader2, Pencil, Plus, Search, Smartphone, Trash2 } from '@lucide/vue'
import { toast } from 'vue-sonner'
import { adminApi } from '@/api'
import { usePagedList } from '@/composables/usePagedList'
import type { ParamDef } from '@/types/api'
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
import { compressImage, formatBytes } from '@/lib/image'

interface Brand {
  id: number
  name: string
}

interface Series {
  id: number
  brandId: number
  name: string
}

interface ParamGroup {
  id: number
  name: string
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
  seriesId?: number
  name: string
  releaseDate: string
  thumbnail: string
  description: string
  status: number
  params: Record<string, string>
  variants: DeviceVariantRow[]
}

const brands = ref<Brand[]>([])
const seriesList = ref<Series[]>([])
const groups = ref<ParamGroup[]>([])
const defs = ref<ParamDef[]>([])
const saving = ref(false)
const uploading = ref(false)

const dialog = ref(false)
const deleteTarget = ref<any>(null)
const deleting = ref(false)
const fileInput = ref<HTMLInputElement>()

const form = reactive<DeviceForm>({
  id: null,
  brandId: undefined,
  seriesId: undefined,
  name: '',
  releaseDate: '',
  thumbnail: '',
  description: '',
  status: 1,
  params: {},
  variants: [],
})

const brandOptions = computed(() => brands.value.map((b) => ({ label: b.name, value: b.id })))
const seriesOptions = computed(() =>
  seriesList.value.filter((s) => s.brandId === form.brandId).map((s) => ({ label: s.name, value: s.id })),
)
const defsByGroup = computed(() =>
  groups.value
    .map((group) => ({ ...group, defs: defs.value.filter((d) => d.groupId === group.id) }))
    .filter((group) => group.defs.length > 0),
)

const { list, total, loading, query, load, search } = usePagedList({
  size: 10,
  filters: () => ({ keyword: '', brandId: undefined as number | undefined }),
  fetcher: (params) => adminApi.devices(params),
  // 基础数据（品牌/系列/参数定义）先加载完再拉列表，保持原有首屏顺序
  immediate: false,
})

onMounted(async () => {
  ;[brands.value, seriesList.value, groups.value, defs.value] = await Promise.all([
    adminApi.brands(),
    // 系列是可选归类维度，接口异常时退化为「无系列」，不能让机型表单整体崩掉
    adminApi.series().catch(() => [] as any[]),
    adminApi.paramGroups(),
    adminApi.paramDefs(),
  ])
  seriesList.value ??= []
  await load()
})

// 切换品牌后原系列可能不属于新品牌，必须清空，否则后端会因跨品牌归类拒绝保存
watch(
  () => form.brandId,
  (brandId) => {
    if (form.seriesId && !seriesList.value.some((s) => s.id === form.seriesId && s.brandId === brandId)) {
      form.seriesId = undefined
    }
  },
)

function resetForm() {
  Object.assign(form, {
    id: null,
    brandId: brands.value[0]?.id,
    seriesId: undefined,
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
    seriesId: detail.device.seriesId ?? undefined,
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

/** 复制机型：带出全部参数与版本价格，名称追加「副本」、发布日期置空，保存时新增一条记录。 */
async function openCopy(row: any) {
  const detail = await adminApi.deviceDetail(row.id)
  Object.assign(form, {
    id: null,
    brandId: detail.device.brandId,
    seriesId: detail.device.seriesId ?? undefined,
    name: `${detail.device.name} 副本`,
    releaseDate: '',
    thumbnail: detail.device.thumbnail || '',
    description: detail.device.description || '',
    status: detail.device.status ?? 1,
    params: { ...(detail.params || {}) },
    variants: (detail.variants || []).map((v: any) => ({
      id: undefined,
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
    // brandId 已在上方非空校验，这里断言给请求类型
    await adminApi.saveDevice({ ...form, brandId: form.brandId, params: { ...form.params }, variants })
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

const uploadProgress = ref(0)
const uploadFailed = ref(false)
const pendingImage = ref<File | null>(null)

async function onFileChange(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0]
  if (!file) return
  pendingImage.value = file
  await uploadPendingImage()
  if (fileInput.value) fileInput.value.value = ''
}

/** 压缩后上传；失败保留原文件，用户可点重试而无需重新选择。 */
async function uploadPendingImage() {
  if (!pendingImage.value) return
  uploading.value = true
  uploadFailed.value = false
  uploadProgress.value = 0
  try {
    const compressed = await compressImage(pendingImage.value)
    if (compressed.exceeded) {
      toast.warning(`压缩后仍有 ${formatBytes(compressed.file.size)}，建议裁剪后再上传`)
    }
    const result = await adminApi.upload(compressed.file, (percent) => {
      uploadProgress.value = percent
    })
    form.thumbnail = result.url
    const dimensions = result.width && result.height ? `${result.width}×${result.height}，` : ''
    toast.success(`图片上传成功（${dimensions}${formatBytes(result.size)}）`)
    pendingImage.value = null
  } catch {
    uploadFailed.value = true
  } finally {
    uploading.value = false
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

function brandName(id?: number) {
  if (!id) return '—'
  return brands.value.find((b) => b.id === id)?.name ?? '—'
}

function seriesName(id?: number | null) {
  if (!id) return '—'
  return seriesList.value.find((s) => s.id === id)?.name ?? '—'
}

// ---------- 表单内快速新建系列 ----------
const seriesDialog = ref(false)
const newSeriesName = ref('')
const creatingSeries = ref(false)

function openSeriesCreate() {
  if (!form.brandId) {
    toast.warning('请先选择品牌')
    return
  }
  newSeriesName.value = ''
  seriesDialog.value = true
}

async function createSeries() {
  if (!form.brandId) return
  const name = newSeriesName.value.trim()
  if (!name) {
    toast.warning('请填写系列名称')
    return
  }
  creatingSeries.value = true
  try {
    const saved = await adminApi.saveSeries({
      brandId: form.brandId,
      name,
      sortOrder: seriesList.value.filter((s) => s.brandId === form.brandId).length + 1,
      status: 1,
    })
    seriesList.value.push(saved)
    form.seriesId = saved.id
    seriesDialog.value = false
    toast.success(`已新建系列「${saved.name}」`)
  } finally {
    creatingSeries.value = false
  }
}

// ---------- 批量导入 ----------
const importOpen = ref(false)
const importFile = ref<File | null>(null)
const importPreview = ref<any>(null)
const previewing = ref(false)
const importing = ref(false)
const importInput = ref<HTMLInputElement>()

function openImport() {
  importFile.value = null
  importPreview.value = null
  importOpen.value = true
}

async function downloadTemplate() {
  const blob = await adminApi.deviceImportTemplate()
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = 'device-import-template.csv'
  link.click()
  URL.revokeObjectURL(url)
}

async function onImportFileChange(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0]
  if (!file) return
  importFile.value = file
  importPreview.value = null
  previewing.value = true
  try {
    importPreview.value = await adminApi.importDevices(file, true)
  } finally {
    previewing.value = false
    if (importInput.value) importInput.value.value = ''
  }
}

async function confirmImport() {
  if (!importFile.value) return
  importing.value = true
  try {
    const result = await adminApi.importDevices(importFile.value, false)
    importPreview.value = result
    toast.success(`导入完成，成功 ${result.createdCount} 台`)
    await load()
  } finally {
    importing.value = false
  }
}

function rowMessages(row: any) {
  return [...(row.errors || []), ...(row.warnings || [])]
}
</script>

<template>
  <div class="space-y-4">
    <PageHeader title="机型管理" description="维护机型基础信息与参数值，支持按品牌和关键词筛选">
      <template #actions>
        <Button size="sm" variant="outline" @click="openImport">
          <FileUp />
          批量导入
        </Button>
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
              <TableHead class="w-36">系列</TableHead>
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
              <TableCell class="text-muted-foreground">{{ seriesName(row.seriesId) }}</TableCell>
              <TableCell class="text-muted-foreground">{{ row.releaseDate || '—' }}</TableCell>
              <TableCell>
                <Badge :variant="row.status === 1 ? 'success' : 'secondary'">{{ row.status === 1 ? '上架' : '下架' }}</Badge>
              </TableCell>
              <TableCell class="text-right">
                <div class="flex justify-end gap-1">
                  <Button variant="ghost" size="icon-sm" aria-label="编辑" title="编辑" @click="openEdit(row)">
                    <Pencil />
                  </Button>
                  <Button variant="ghost" size="icon-sm" aria-label="复制机型" title="复制机型" @click="openCopy(row)">
                    <Copy />
                  </Button>
                  <Button variant="ghost" size="icon-sm" class="text-destructive" aria-label="删除" title="删除" @click="deleteTarget = row">
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
            <Label for="device-brand">品牌</Label>
            <Select id="device-brand" v-model="form.brandId" :options="brandOptions" placeholder="选择品牌" />
          </div>
          <div class="space-y-1.5">
            <Label for="device-series">系列（可选）</Label>
            <div class="flex items-center gap-2">
              <Select
                id="device-series"
                v-model="form.seriesId"
                :options="seriesOptions"
                placeholder="未归类"
                class="flex-1"
              />
              <Button variant="outline" size="icon" aria-label="新建系列" title="新建系列" @click="openSeriesCreate">
                <Plus />
              </Button>
            </div>
          </div>
          <div class="space-y-1.5">
            <Label for="device-date">发布时间</Label>
            <Input id="device-date" v-model="form.releaseDate" type="date" />
          </div>
          <div class="space-y-1.5">
            <Label>缩略图</Label>
            <div class="flex flex-wrap items-center gap-2">
              <Button variant="outline" size="sm" :disabled="uploading" @click="fileInput?.click()">
                <Loader2 v-if="uploading" class="animate-spin" />
                <ImagePlus v-else />
                {{ uploading ? `上传中 ${uploadProgress}%` : '上传图片' }}
              </Button>
              <Button v-if="uploadFailed" variant="ghost" size="sm" @click="uploadPendingImage">重试上传</Button>
              <img v-if="form.thumbnail" :src="form.thumbnail" alt="缩略图预览" class="h-9 max-w-16 object-contain" />
              <span v-if="uploadFailed" class="text-xs text-destructive">上传失败</span>
              <span v-else class="text-xs text-muted-foreground">最长边 1600px，自动转 WebP</span>
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

    <Dialog
      v-model:open="importOpen"
      title="批量导入机型"
      description="上传 CSV 后先预览校验结果，确认无误再写入机型库；表头需包含 name、brand，可选 series、releaseDate。"
      class="max-w-3xl"
    >
      <div class="space-y-4">
        <div class="flex flex-wrap items-center gap-2">
          <Button variant="outline" size="sm" @click="downloadTemplate">
            <Download />
            下载模板
          </Button>
          <Button variant="outline" size="sm" :disabled="previewing" @click="importInput?.click()">
            <Loader2 v-if="previewing" class="animate-spin" />
            <FileUp v-else />
            {{ previewing ? '解析中…' : '选择 CSV' }}
          </Button>
          <span class="text-xs text-muted-foreground">{{ importFile?.name || '尚未选择文件' }}</span>
          <input ref="importInput" type="file" accept=".csv,text/csv" class="hidden" @change="onImportFileChange" />
        </div>

        <template v-if="importPreview">
          <div class="flex flex-wrap items-center gap-2">
            <Badge variant="info">共 {{ importPreview.total }} 行</Badge>
            <Badge variant="success">可写入 {{ importPreview.validCount }} 行</Badge>
            <Badge v-if="importPreview.invalidCount" variant="destructive">
              异常 {{ importPreview.invalidCount }} 行
            </Badge>
            <Badge v-if="!importPreview.dryRun" variant="warning">已导入 {{ importPreview.createdCount }} 台</Badge>
          </div>

          <div class="max-h-80 overflow-auto rounded-md border">
            <Table>
              <TableHeader>
                <TableRow class="hover:bg-transparent">
                  <TableHead class="w-16">行号</TableHead>
                  <TableHead class="min-w-32">机型</TableHead>
                  <TableHead class="min-w-24">品牌</TableHead>
                  <TableHead class="min-w-28">系列</TableHead>
                  <TableHead class="w-28">发布时间</TableHead>
                  <TableHead class="w-20">参数</TableHead>
                  <TableHead class="min-w-56">校验结果</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                <TableRow v-for="row in importPreview.rows" :key="row.rowNo">
                  <TableCell class="text-muted-foreground">{{ row.rowNo }}</TableCell>
                  <TableCell class="font-medium">{{ row.name || '—' }}</TableCell>
                  <TableCell class="text-muted-foreground">{{ row.brandName || '—' }}</TableCell>
                  <TableCell class="text-muted-foreground">{{ row.seriesName || '未归类' }}</TableCell>
                  <TableCell class="text-muted-foreground">{{ row.releaseDate || '—' }}</TableCell>
                  <TableCell class="text-muted-foreground">{{ Object.keys(row.params || {}).length }}</TableCell>
                  <TableCell class="text-xs">
                    <span v-if="!rowMessages(row).length" class="text-emerald-600 dark:text-emerald-400">通过</span>
                    <ul v-else class="space-y-0.5">
                      <li
                        v-for="(message, index) in rowMessages(row)"
                        :key="index"
                        :class="row.errors?.length ? 'text-destructive' : 'text-muted-foreground'"
                      >
                        {{ message }}
                      </li>
                    </ul>
                  </TableCell>
                </TableRow>
              </TableBody>
            </Table>
          </div>
        </template>

        <p v-else class="text-xs leading-5 text-muted-foreground">
          列名与参数 Key 一致即视为该参数值，例如 screen_size、battery_capacity；也可用 params 列写
          <code class="rounded bg-muted px-1">screen_size=6.3;battery_capacity=3600</code>。
          series（系列）留空表示未归类，填写后按品牌内名称匹配，不存在则自动创建。
          单次最多 500 行，品牌必须已在品牌管理中创建。
        </p>
      </div>

      <template #footer>
        <Button variant="outline" :disabled="importing" @click="importOpen = false">关闭</Button>
        <Button :disabled="importing || !importPreview?.validCount || !importPreview?.dryRun" @click="confirmImport">
          <Loader2 v-if="importing" class="animate-spin" />
          {{ importing ? '导入中…' : `确认导入 ${importPreview?.validCount ?? 0} 台` }}
        </Button>
      </template>
    </Dialog>

    <Dialog v-model:open="seriesDialog" title="新建系列" class="max-w-sm">
      <div class="space-y-1.5">
        <Label for="new-series-name">系列名称</Label>
        <Input
          id="new-series-name"
          v-model="newSeriesName"
          placeholder="如 iPhone 数字系列"
          @keyup.enter="createSeries"
        />
        <p class="text-xs text-muted-foreground">
          系列归属当前品牌「{{ brandName(form.brandId) }}」，同一品牌下不可重名。
        </p>
      </div>
      <template #footer>
        <Button variant="outline" :disabled="creatingSeries" @click="seriesDialog = false">取消</Button>
        <Button :disabled="creatingSeries" @click="createSeries">{{ creatingSeries ? '创建中…' : '创建并选中' }}</Button>
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

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { VueDraggable } from 'vue-draggable-plus'
import { GripVertical, ImagePlus, Loader2, Pencil, Plus, Trash2 } from '@lucide/vue'
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
import ImageWithFallback from '@/components/ImageWithFallback.vue'
import { compressImage, formatBytes } from '@/lib/image'

interface Brand {
  id: number | null
  name: string
  logo: string
  sortOrder: number
  status: number
}

const list = ref<Brand[]>([])
const loading = ref(true)
const dialog = ref(false)
const saving = ref(false)
const uploading = ref(false)
const form = reactive<Brand>({ id: null, name: '', logo: '', sortOrder: 0, status: 1 })
const deleteTarget = ref<Brand | null>(null)
const deleting = ref(false)
const fileInput = ref<HTMLInputElement>()

async function load() {
  loading.value = true
  try {
    list.value = await adminApi.brands()
  } finally {
    loading.value = false
  }
}

onMounted(load)

function openCreate() {
  Object.assign(form, { id: null, name: '', logo: '', sortOrder: list.value.length, status: 1 })
  dialog.value = true
}

function openEdit(row: Brand) {
  Object.assign(form, row)
  dialog.value = true
}

async function save() {
  if (!form.name.trim()) {
    toast.warning('请填写品牌名称')
    return
  }
  saving.value = true
  try {
    await adminApi.saveBrand({ ...form })
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
    await adminApi.deleteBrand(deleteTarget.value.id as number)
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
    const compressed = await compressImage(file)
    const result = await adminApi.upload(compressed)
    form.logo = result.url
    toast.success(`Logo 上传成功（${formatBytes(result.size)}）`)
  } finally {
    uploading.value = false
    if (fileInput.value) fileInput.value.value = ''
  }
}

/** 拖拽结束把顺序写回后端，保持列表排序与后台一致。 */
async function saveOrder() {
  try {
    await Promise.all(list.value.map((row, index) => adminApi.saveBrand({ ...row, sortOrder: index })))
    toast.success('排序已保存')
  } catch {
    await load()
  }
}
</script>

<template>
  <div class="space-y-4">
    <PageHeader title="品牌管理" description="维护品牌名称、Logo 与展示顺序，拖拽行可调整排序">
      <template #actions>
        <Button size="sm" @click="openCreate">
          <Plus />
          新增品牌
        </Button>
      </template>
    </PageHeader>

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
              <TableHead class="w-24">Logo</TableHead>
              <TableHead>品牌名称</TableHead>
              <TableHead class="w-20">排序</TableHead>
              <TableHead class="w-24">状态</TableHead>
              <TableHead class="w-32 text-right">操作</TableHead>
            </TableRow>
          </TableHeader>
          <VueDraggable v-model="list" tag="tbody" handle=".drag-handle" class="[&_tr:last-child]:border-0" @end="saveOrder">
            <TableRow v-for="row in list" :key="row.id ?? row.name">
              <TableCell>
                <button type="button" class="drag-handle cursor-grab text-muted-foreground/60 hover:text-foreground active:cursor-grabbing">
                  <GripVertical class="h-4 w-4" />
                </button>
              </TableCell>
              <TableCell class="text-muted-foreground">{{ row.id }}</TableCell>
              <TableCell>
                <ImageWithFallback :src="row.logo" :alt="row.name" img-class="h-7 max-w-20 object-contain">
                  <template #fallback><span class="text-xs text-muted-foreground">无</span></template>
                </ImageWithFallback>
              </TableCell>
              <TableCell class="font-medium">{{ row.name }}</TableCell>
              <TableCell class="text-muted-foreground">{{ row.sortOrder }}</TableCell>
              <TableCell>
                <Badge :variant="row.status === 1 ? 'success' : 'secondary'">{{ row.status === 1 ? '启用' : '停用' }}</Badge>
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
          </VueDraggable>
        </Table>

        <Empty v-else title="还没有品牌" description="新增第一个品牌后，机型就可以归属到品牌下。" />
      </CardContent>
    </Card>

    <Dialog v-model:open="dialog" :title="form.id ? '编辑品牌' : '新增品牌'" class="max-w-md">
      <div class="space-y-4">
        <div class="space-y-1.5">
          <Label for="brand-name">品牌名称</Label>
          <Input id="brand-name" v-model="form.name" placeholder="如 Apple / 华为" />
        </div>

        <div class="space-y-1.5">
          <Label>Logo</Label>
          <div class="flex items-center gap-3">
            <Button variant="outline" size="sm" :disabled="uploading" @click="fileInput?.click()">
              <Loader2 v-if="uploading" class="animate-spin" />
              <ImagePlus v-else />
              {{ uploading ? '上传中…' : '上传 Logo' }}
            </Button>
            <img v-if="form.logo" :src="form.logo" alt="Logo 预览" class="h-8 max-w-24 object-contain" />
            <input ref="fileInput" type="file" accept="image/*" class="hidden" @change="onFileChange" />
          </div>
        </div>

        <div class="space-y-1.5">
          <Label for="brand-sort">排序</Label>
          <Input id="brand-sort" v-model.number="form.sortOrder" type="number" min="0" />
        </div>

        <div class="flex items-center justify-between rounded-md border p-3">
          <div>
            <div class="text-sm font-medium">启用品牌</div>
            <div class="text-xs text-muted-foreground">停用后访客端不再展示该品牌</div>
          </div>
          <Switch :model-value="form.status === 1" @update:model-value="form.status = $event ? 1 : 0" />
        </div>
      </div>
      <template #footer>
        <Button variant="outline" :disabled="saving" @click="dialog = false">取消</Button>
        <Button :disabled="saving" @click="save">{{ saving ? '保存中…' : '保存' }}</Button>
      </template>
    </Dialog>

    <ConfirmDialog
      :open="!!deleteTarget"
      title="删除品牌"
      :description="`确认删除品牌「${deleteTarget?.name ?? ''}」？该品牌下的机型将失去归属。`"
      confirm-text="删除"
      destructive
      :loading="deleting"
      @update:open="deleteTarget = null"
      @confirm="confirmDelete"
    />
  </div>
</template>

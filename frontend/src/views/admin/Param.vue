<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useMediaQuery } from '@vueuse/core'
import { SplitterGroup, SplitterPanel, SplitterResizeHandle } from 'reka-ui'
import { VueDraggable } from 'vue-draggable-plus'
import { Check, GripVertical, Pencil, Plus, Trash2 } from '@lucide/vue'
import { toast } from 'vue-sonner'
import { adminApi } from '@/api'
import type { ParamDef, ParamGroup } from '@/types/api'
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
  TableHeader,
  TableBody,
  TableRow,
  TableHead,
  TableCell,
} from '@/components/ui'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import PageHeader from '@/components/PageHeader.vue'

/** 编辑表单：新建时 id 为空 */
type GroupForm = Omit<ParamGroup, 'id'> & { id: number | null }

/** 编辑表单：新建时 id 为空，下拉值为空串表示未选 */
type DefForm = Omit<ParamDef, 'id' | 'groupId' | 'enumOptions' | 'unit'> & {
  id: number | null
  groupId: number | undefined
  enumOptions: string
  unit: string
}

const groups = ref<ParamGroup[]>([])
const defs = ref<ParamDef[]>([])
const activeGroup = ref<number>()
const loading = ref(true)
const defsLoading = ref(false)

const groupDialog = ref(false)
const groupForm = reactive<GroupForm>({ id: null, name: '', sortOrder: 0 })
const groupSaving = ref(false)

const defDialog = ref(false)
const defForm = reactive<DefForm>({
  id: null,
  paramKey: '',
  paramName: '',
  groupId: undefined,
  paramType: 'text',
  enumOptions: '',
  unit: '',
  sortOrder: 0,
  enabled: 1,
})
const defSaving = ref(false)

const deleteGroupTarget = ref<ParamGroup | null>(null)
const deleteDefTarget = ref<ParamDef | null>(null)
const deleting = ref(false)

const types = [
  { label: '文本', value: 'text' },
  { label: '数值', value: 'number' },
  { label: '图片', value: 'image' },
  { label: '日期', value: 'date' },
  { label: '枚举', value: 'select' },
]

// 桌面左右分栏，移动端上下分栏，保证窄屏可用
const isDesktop = useMediaQuery('(min-width: 1024px)')

const activeGroupName = computed(() => groups.value.find((g) => g.id === activeGroup.value)?.name ?? '')

async function loadGroups() {
  groups.value = await adminApi.paramGroups()
  if (!activeGroup.value && groups.value.length) activeGroup.value = groups.value[0].id as number
}

async function loadDefs() {
  if (!activeGroup.value) {
    defs.value = []
    return
  }
  defsLoading.value = true
  try {
    defs.value = await adminApi.paramDefs(activeGroup.value)
  } finally {
    defsLoading.value = false
  }
}

onMounted(async () => {
  try {
    await loadGroups()
    await loadDefs()
  } finally {
    loading.value = false
  }
})

watch(activeGroup, loadDefs)

function openGroup(row?: ParamGroup) {
  Object.assign(groupForm, row ? { ...row } : { id: null, name: '', sortOrder: groups.value.length })
  groupDialog.value = true
}

async function saveGroup() {
  if (!groupForm.name.trim()) {
    toast.warning('请填写分组名称')
    return
  }
  groupSaving.value = true
  try {
    await adminApi.saveParamGroup({ ...groupForm })
    toast.success('保存成功')
    groupDialog.value = false
    await loadGroups()
    await loadDefs()
  } finally {
    groupSaving.value = false
  }
}

async function confirmDeleteGroup() {
  if (!deleteGroupTarget.value) return
  deleting.value = true
  try {
    await adminApi.deleteParamGroup(deleteGroupTarget.value.id as number)
    toast.success('分组已删除')
    deleteGroupTarget.value = null
    await loadGroups()
    await loadDefs()
  } finally {
    deleting.value = false
  }
}

/** 分组拖拽排序后写回 sortOrder。 */
async function saveGroupOrder() {
  try {
    await Promise.all(groups.value.map((row, index) => adminApi.saveParamGroup({ ...row, sortOrder: index })))
    toast.success('分组排序已保存')
  } catch {
    await loadGroups()
  }
}

function openDef(row?: ParamDef) {
  Object.assign(
    defForm,
    row
      ? { ...row }
      : {
          id: null,
          paramKey: '',
          paramName: '',
          groupId: activeGroup.value,
          paramType: 'text',
          enumOptions: '',
          unit: '',
          sortOrder: defs.value.length,
          enabled: 1,
        },
  )
  defDialog.value = true
}

async function saveDef() {
  if (!defForm.paramKey.trim() || !defForm.paramName.trim()) {
    toast.warning('请填写参数 Key 与名称')
    return
  }
  defForm.groupId = defForm.groupId || activeGroup.value
  defSaving.value = true
  try {
    await adminApi.saveParamDef({ ...defForm })
    toast.success('保存成功')
    defDialog.value = false
    await loadDefs()
  } finally {
    defSaving.value = false
  }
}

async function confirmDeleteDef() {
  if (!deleteDefTarget.value) return
  deleting.value = true
  try {
    await adminApi.deleteParamDef(deleteDefTarget.value.id as number)
    toast.success('参数已删除')
    deleteDefTarget.value = null
    await loadDefs()
  } finally {
    deleting.value = false
  }
}
</script>

<template>
  <div class="space-y-4">
    <PageHeader title="参数元数据" description="定义参数分组与字段；左侧分栏可拖拽调整宽度，分组可拖拽排序">
      <template #actions>
        <Button size="sm" variant="outline" @click="openGroup()">
          <Plus />
          新增分组
        </Button>
        <Button size="sm" @click="openDef()">
          <Plus />
          新增参数
        </Button>
      </template>
    </PageHeader>

    <div v-if="loading" class="grid grid-cols-1 gap-4 lg:grid-cols-[260px_1fr]">
      <Skeleton class="h-96 rounded-xl" />
      <Skeleton class="h-96 rounded-xl" />
    </div>

    <SplitterGroup
      v-else
      :direction="isDesktop ? 'horizontal' : 'vertical'"
      class="flex h-[70vh] min-h-[460px] overflow-hidden rounded-xl border bg-background lg:h-[calc(100vh-11rem)]"
    >
      <SplitterPanel :default-size="isDesktop ? 24 : 38" :min-size="isDesktop ? 18 : 25" :max-size="isDesktop ? 40 : 60" class="flex min-w-0 flex-col">
        <div class="flex items-center justify-between border-b px-4 py-3">
          <div>
            <div class="text-sm font-semibold">参数分组</div>
            <div class="text-xs text-muted-foreground">{{ groups.length }} 个分组</div>
          </div>
          <Button variant="ghost" size="icon-sm" aria-label="新增分组" @click="openGroup()">
            <Plus />
          </Button>
        </div>

        <div class="flex-1 overflow-y-auto p-2">
          <VueDraggable v-model="groups" :animation="150" handle=".group-handle" class="space-y-1" @end="saveGroupOrder">
            <div
              v-for="group in groups"
              :key="group.id ?? group.name"
              role="button"
              tabindex="0"
              class="group flex cursor-pointer items-center gap-2 rounded-md px-2 py-2 text-sm transition-colors"
              :class="activeGroup === group.id ? 'bg-secondary font-medium' : 'hover:bg-accent/60 text-muted-foreground'"
              @click="activeGroup = group.id as number"
              @keydown.enter="activeGroup = group.id as number"
            >
              <GripVertical class="group-handle h-4 w-4 shrink-0 cursor-grab text-muted-foreground/50 active:cursor-grabbing" />
              <span class="min-w-0 flex-1 truncate">{{ group.name }}</span>
              <span class="flex shrink-0 items-center gap-0.5 opacity-0 transition-opacity group-hover:opacity-100">
                <Button variant="ghost" size="icon-sm" aria-label="编辑分组" @click.stop="openGroup(group)">
                  <Pencil />
                </Button>
                <Button
                  variant="ghost"
                  size="icon-sm"
                  class="text-destructive"
                  aria-label="删除分组"
                  @click.stop="deleteGroupTarget = group"
                >
                  <Trash2 />
                </Button>
              </span>
            </div>
          </VueDraggable>

          <Empty v-if="!groups.length" title="暂无分组" description="先新增一个分组，例如「屏幕」「影像」。" />
        </div>
      </SplitterPanel>

      <SplitterResizeHandle
        :class="
          isDesktop
            ? 'relative flex w-px items-center justify-center bg-border after:absolute after:inset-y-0 after:left-1/2 after:w-2 after:-translate-x-1/2 focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring'
            : 'relative flex h-px w-full items-center justify-center bg-border after:absolute after:inset-x-0 after:top-1/2 after:h-2 after:-translate-y-1/2 focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring'
        "
      />

      <SplitterPanel :default-size="isDesktop ? 76 : 62" class="flex min-w-0 flex-col">
        <div class="flex items-center justify-between border-b px-4 py-3">
          <div>
            <div class="text-sm font-semibold">{{ activeGroupName || '参数定义' }}</div>
            <div class="text-xs text-muted-foreground">该分组下的参数字段</div>
          </div>
          <Button variant="outline" size="sm" :disabled="!activeGroup" @click="openDef()">
            <Plus />
            新增参数
          </Button>
        </div>

        <div class="flex-1 overflow-y-auto">
          <div v-if="defsLoading" class="space-y-2 p-4">
            <Skeleton v-for="i in 6" :key="i" class="h-10" />
          </div>

          <Table v-else-if="defs.length">
            <TableHeader>
              <TableRow class="hover:bg-transparent">
                <TableHead>参数 Key</TableHead>
                <TableHead>名称</TableHead>
                <TableHead class="w-24">类型</TableHead>
                <TableHead class="w-20">单位</TableHead>
                <TableHead class="w-24">状态</TableHead>
                <TableHead class="w-28 text-right">操作</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              <TableRow v-for="row in defs" :key="row.id ?? row.paramKey">
                <TableCell class="font-mono text-xs text-muted-foreground">{{ row.paramKey }}</TableCell>
                <TableCell class="font-medium">{{ row.paramName }}</TableCell>
                <TableCell>
                  <Badge variant="secondary">{{ types.find((t) => t.value === row.paramType)?.label || row.paramType }}</Badge>
                </TableCell>
                <TableCell class="text-muted-foreground">{{ row.unit || '—' }}</TableCell>
                <TableCell>
                  <Badge :variant="row.enabled === 1 ? 'success' : 'secondary'">
                    {{ row.enabled === 1 ? '启用' : '停用' }}
                  </Badge>
                </TableCell>
                <TableCell class="text-right">
                  <div class="flex justify-end gap-1">
                    <Button variant="ghost" size="icon-sm" aria-label="编辑" @click="openDef(row)">
                      <Pencil />
                    </Button>
                    <Button variant="ghost" size="icon-sm" class="text-destructive" aria-label="删除" @click="deleteDefTarget = row">
                      <Trash2 />
                    </Button>
                  </div>
                </TableCell>
              </TableRow>
            </TableBody>
          </Table>

          <Empty v-else title="该分组暂无参数" description="新增参数后，机型编辑页会按分组渲染表单。" />
        </div>
      </SplitterPanel>
    </SplitterGroup>

    <Dialog v-model:open="groupDialog" :title="groupForm.id ? '编辑分组' : '新增分组'" class="max-w-sm">
      <div class="space-y-4">
        <div class="space-y-1.5">
          <Label for="group-name">分组名称</Label>
          <Input id="group-name" v-model="groupForm.name" placeholder="如 屏幕 / 影像 / 电池" />
        </div>
        <div class="space-y-1.5">
          <Label for="group-sort">排序</Label>
          <Input id="group-sort" v-model.number="groupForm.sortOrder" type="number" min="0" />
        </div>
      </div>
      <template #footer>
        <Button variant="outline" :disabled="groupSaving" @click="groupDialog = false">取消</Button>
        <Button :disabled="groupSaving" @click="saveGroup">{{ groupSaving ? '保存中…' : '保存' }}</Button>
      </template>
    </Dialog>

    <Dialog v-model:open="defDialog" :title="defForm.id ? '编辑参数' : '新增参数'" class="max-w-lg">
      <div class="grid gap-4 sm:grid-cols-2">
        <div class="space-y-1.5">
          <Label for="def-key">参数 Key</Label>
          <Input id="def-key" v-model="defForm.paramKey" placeholder="如 screen_size" />
        </div>
        <div class="space-y-1.5">
          <Label for="def-name">参数名称</Label>
          <Input id="def-name" v-model="defForm.paramName" placeholder="如 屏幕尺寸" />
        </div>
        <div class="space-y-1.5">
          <Label for="def-group">所属分组</Label>
          <Select
            id="def-group"
            v-model="defForm.groupId"
            :options="groups.map((g) => ({ label: g.name, value: g.id as number }))"
            placeholder="选择分组"
          />
        </div>
        <div class="space-y-1.5">
          <Label for="def-type">参数类型</Label>
          <Select id="def-type" v-model="defForm.paramType" :options="types" placeholder="选择类型" />
        </div>
        <div v-if="defForm.paramType === 'select'" class="space-y-1.5 sm:col-span-2">
          <Label for="def-enum">枚举选项</Label>
          <Input id="def-enum" v-model="defForm.enumOptions" placeholder='JSON 数组，如 ["支持","不支持"]' />
        </div>
        <div class="space-y-1.5">
          <Label for="def-unit">单位</Label>
          <Input id="def-unit" v-model="defForm.unit" placeholder="如 英寸 / mAh" />
        </div>
        <div class="space-y-1.5">
          <Label for="def-sort">排序</Label>
          <Input id="def-sort" v-model.number="defForm.sortOrder" type="number" min="0" />
        </div>
        <div class="flex items-center justify-between rounded-md border p-3 sm:col-span-2">
          <div>
            <div class="text-sm font-medium">启用参数</div>
            <div class="text-xs text-muted-foreground">停用后机型表单不再展示该字段</div>
          </div>
          <Switch :model-value="defForm.enabled === 1" @update:model-value="defForm.enabled = $event ? 1 : 0" />
        </div>
      </div>
      <template #footer>
        <Button variant="outline" :disabled="defSaving" @click="defDialog = false">取消</Button>
        <Button :disabled="defSaving" @click="saveDef">{{ defSaving ? '保存中…' : '保存' }}</Button>
      </template>
    </Dialog>

    <ConfirmDialog
      :open="!!deleteGroupTarget"
      title="删除分组"
      :description="`确认删除分组「${deleteGroupTarget?.name ?? ''}」？组内参数定义也会一并删除。`"
      confirm-text="删除"
      destructive
      :loading="deleting"
      @update:open="deleteGroupTarget = null"
      @confirm="confirmDeleteGroup"
    />

    <ConfirmDialog
      :open="!!deleteDefTarget"
      title="删除参数"
      :description="`确认删除参数「${deleteDefTarget?.paramName ?? ''}」？已录入的机型参数值将无法恢复。`"
      confirm-text="删除"
      destructive
      :loading="deleting"
      @update:open="deleteDefTarget = null"
      @confirm="confirmDeleteDef"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import {
  ChevronDown,
  CloudDownload,
  KeyRound,
  Loader2,
  Pencil,
  Plus,
  Server,
  Star,
  Trash2,
} from '@lucide/vue'
import { toast } from 'vue-sonner'
import { adminApi } from '@/api'
import {
  Badge,
  Button,
  Card,
  CardContent,
  Checkbox,
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

interface Connection {
  id: number | null
  name: string
  provider: string
  baseUrl: string
  apiKey: string
  enabled: number
  apiKeySet?: boolean
}

const connections = ref<any[]>([])
const loading = ref(true)
const modelsMap = reactive<Record<number, any[]>>({})
const expanded = ref<number[]>([])
const loadingModels = ref<number[]>([])

const presets: Record<string, { label: string; baseUrl: string }> = {
  dashscope: { label: '阿里云百炼 DashScope', baseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1' },
  openai: { label: 'OpenAI', baseUrl: 'https://api.openai.com/v1' },
  deepseek: { label: 'DeepSeek', baseUrl: 'https://api.deepseek.com/v1' },
  moonshot: { label: 'Moonshot (Kimi)', baseUrl: 'https://api.moonshot.cn/v1' },
  ollama: { label: 'Ollama（本地）', baseUrl: 'http://localhost:11434/v1' },
  custom: { label: '自定义 OpenAI 兼容', baseUrl: '' },
}
const providerOptions = Object.entries(presets).map(([value, p]) => ({ label: p.label, value }))

const dialog = ref(false)
const saving = ref(false)
const form = reactive<Connection>({
  id: null,
  name: '',
  provider: 'dashscope',
  baseUrl: presets.dashscope.baseUrl,
  apiKey: '',
  enabled: 1,
})

const pullDialog = ref(false)
const pullConfig = ref<any>(null)
const remoteModels = ref<string[]>([])
const selectedModels = ref<string[]>([])
const pullLoading = ref(false)
const importLoading = ref(false)

const deleteConnection = ref<any>(null)
const deleteModel = ref<any>(null)
const deleting = ref(false)

const allSelected = computed(
  () => remoteModels.value.length > 0 && selectedModels.value.length === remoteModels.value.length,
)

async function load() {
  loading.value = true
  try {
    connections.value = await adminApi.aiConfigs()
  } finally {
    loading.value = false
  }
}

onMounted(load)

async function loadModels(id: number) {
  loadingModels.value.push(id)
  try {
    modelsMap[id] = await adminApi.aiConfigModels(id)
  } finally {
    loadingModels.value = loadingModels.value.filter((i) => i !== id)
  }
}

function toggleExpand(row: any) {
  const index = expanded.value.indexOf(row.id)
  if (index >= 0) {
    expanded.value.splice(index, 1)
    return
  }
  expanded.value.push(row.id)
  loadModels(row.id)
}

function isExpanded(id: number) {
  return expanded.value.includes(id)
}

function onProviderChange(provider: string) {
  const preset = presets[provider]
  if (preset && provider !== 'custom') form.baseUrl = preset.baseUrl
}

function openCreate() {
  Object.assign(form, {
    id: null,
    name: '',
    provider: 'dashscope',
    baseUrl: presets.dashscope.baseUrl,
    apiKey: '',
    enabled: 1,
  })
  dialog.value = true
}

function openEdit(row: any) {
  Object.assign(form, { ...row, apiKey: '' })
  dialog.value = true
}

async function save() {
  if (!form.name.trim()) {
    toast.warning('请填写连接名称')
    return
  }
  if (!form.baseUrl.trim()) {
    toast.warning('请填写接口地址')
    return
  }
  saving.value = true
  try {
    await adminApi.saveAiConfig({ ...form })
    toast.success('保存成功')
    dialog.value = false
    await load()
  } finally {
    saving.value = false
  }
}

async function confirmDeleteConnection() {
  if (!deleteConnection.value) return
  deleting.value = true
  try {
    await adminApi.deleteAiConfig(deleteConnection.value.id)
    toast.success('连接已删除')
    delete modelsMap[deleteConnection.value.id]
    deleteConnection.value = null
    await load()
  } finally {
    deleting.value = false
  }
}

async function openPull(row: any) {
  pullConfig.value = row
  selectedModels.value = []
  remoteModels.value = []
  pullDialog.value = true
  await fetchRemote()
}

async function fetchRemote() {
  pullLoading.value = true
  try {
    remoteModels.value = await adminApi.pullRemoteModels(pullConfig.value.id)
    if (!remoteModels.value.length) toast.warning('远端未返回任何模型')
  } catch {
    remoteModels.value = []
  } finally {
    pullLoading.value = false
  }
}

function toggleAll(checked: boolean) {
  selectedModels.value = checked ? [...remoteModels.value] : []
}

function toggleModel(model: string, checked: boolean) {
  if (checked) {
    if (!selectedModels.value.includes(model)) selectedModels.value.push(model)
  } else {
    selectedModels.value = selectedModels.value.filter((m) => m !== model)
  }
}

async function confirmImport() {
  if (!selectedModels.value.length) {
    toast.warning('请选择要导入的模型')
    return
  }
  importLoading.value = true
  try {
    await adminApi.importAiModels(pullConfig.value.id, { models: selectedModels.value })
    toast.success(`已导入 ${selectedModels.value.length} 个模型`)
    pullDialog.value = false
    if (!isExpanded(pullConfig.value.id)) expanded.value.push(pullConfig.value.id)
    await loadModels(pullConfig.value.id)
  } finally {
    importLoading.value = false
  }
}

async function setDefaultModel(model: any) {
  await adminApi.setDefaultAiModel(model.id)
  toast.success(`已设为默认模型：${model.model}`)
  await loadModels(model.configId)
}

async function toggleModelEnabled(model: any) {
  await adminApi.updateAiModel(model.id, { enabled: model.enabled === 1 ? 0 : 1 })
  await loadModels(model.configId)
}

async function confirmDeleteModel() {
  if (!deleteModel.value) return
  deleting.value = true
  try {
    await adminApi.deleteAiModel(deleteModel.value.id)
    toast.success('模型已删除')
    const configId = deleteModel.value.configId
    deleteModel.value = null
    await loadModels(configId)
  } finally {
    deleting.value = false
  }
}
</script>

<template>
  <div class="space-y-4">
    <PageHeader title="AI 连接与模型" description="配置 OpenAI 兼容供应商，拉取远端模型并设置采集默认模型">
      <template #actions>
        <Button size="sm" @click="openCreate">
          <Plus />
          新增连接
        </Button>
      </template>
    </PageHeader>

    <div v-if="loading" class="space-y-3">
      <Skeleton v-for="i in 3" :key="i" class="h-24 rounded-xl" />
    </div>

    <div v-else-if="connections.length" class="space-y-3">
      <Card v-for="row in connections" :key="row.id">
        <CardContent class="p-0">
          <div class="flex flex-col gap-3 p-4 lg:flex-row lg:items-center">
            <button type="button" class="flex min-w-0 flex-1 items-center gap-3 text-left" @click="toggleExpand(row)">
              <span class="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-muted">
                <Server class="h-4 w-4 text-muted-foreground" />
              </span>
              <div class="min-w-0">
                <div class="flex flex-wrap items-center gap-2">
                  <span class="truncate text-sm font-semibold">{{ row.name }}</span>
                  <Badge variant="outline">{{ presets[row.provider]?.label ?? row.provider }}</Badge>
                  <Badge :variant="row.enabled === 1 ? 'success' : 'secondary'">
                    {{ row.enabled === 1 ? '启用' : '停用' }}
                  </Badge>
                </div>
                <div class="mt-1 flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-muted-foreground">
                  <span class="truncate">{{ row.baseUrl }}</span>
                  <span class="inline-flex items-center gap-1">
                    <KeyRound class="h-3 w-3" />
                    {{ row.apiKeySet ? 'Key 已配置' : 'Key 未配置' }}
                  </span>
                </div>
              </div>
              <ChevronDown
                class="ml-auto h-4 w-4 shrink-0 text-muted-foreground transition-transform"
                :class="isExpanded(row.id) && 'rotate-180'"
              />
            </button>

            <div class="flex shrink-0 flex-wrap items-center gap-1.5">
              <Button variant="outline" size="sm" @click="openPull(row)">
                <CloudDownload />
                拉取模型
              </Button>
              <Button variant="ghost" size="icon-sm" aria-label="编辑连接" @click="openEdit(row)">
                <Pencil />
              </Button>
              <Button
                variant="ghost"
                size="icon-sm"
                class="text-destructive"
                aria-label="删除连接"
                @click="deleteConnection = row"
              >
                <Trash2 />
              </Button>
            </div>
          </div>

          <div v-if="isExpanded(row.id)" class="border-t bg-muted/20">
            <div v-if="loadingModels.includes(row.id)" class="space-y-2 p-4">
              <Skeleton v-for="i in 3" :key="i" class="h-9" />
            </div>

            <Table v-else-if="(modelsMap[row.id] || []).length">
              <TableHeader>
                <TableRow class="hover:bg-transparent">
                  <TableHead>模型名称</TableHead>
                  <TableHead class="w-20">温度</TableHead>
                  <TableHead class="w-20">默认</TableHead>
                  <TableHead class="w-24">启用</TableHead>
                  <TableHead class="w-40 text-right">操作</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                <TableRow v-for="model in modelsMap[row.id]" :key="model.id">
                  <TableCell class="font-mono text-xs">{{ model.model }}</TableCell>
                  <TableCell class="tabular-nums text-muted-foreground">{{ model.temperature ?? '—' }}</TableCell>
                  <TableCell>
                    <Badge v-if="model.isDefault === 1" variant="destructive" class="gap-1">
                      <Star class="h-3 w-3" />
                      默认
                    </Badge>
                    <span v-else class="text-xs text-muted-foreground">—</span>
                  </TableCell>
                  <TableCell>
                    <Switch
                      :model-value="model.enabled === 1"
                      @update:model-value="toggleModelEnabled(model)"
                    />
                  </TableCell>
                  <TableCell class="text-right">
                    <div class="flex justify-end gap-1">
                      <Button
                        variant="ghost"
                        size="sm"
                        :disabled="model.isDefault === 1"
                        @click="setDefaultModel(model)"
                      >
                        设为默认
                      </Button>
                      <Button
                        variant="ghost"
                        size="icon-sm"
                        class="text-destructive"
                        aria-label="删除模型"
                        @click="deleteModel = model"
                      >
                        <Trash2 />
                      </Button>
                    </div>
                  </TableCell>
                </TableRow>
              </TableBody>
            </Table>

            <Empty v-else title="暂无模型" description="点击「拉取模型」，勾选后导入。" />
          </div>
        </CardContent>
      </Card>
    </div>

    <Empty v-else title="还没有 AI 连接" description="新增一个 OpenAI 兼容连接，才能使用采集 Agent。">
      <Button variant="outline" size="sm" class="mt-2" @click="openCreate">
        <Plus />
        新增连接
      </Button>
    </Empty>

    <Dialog v-model:open="dialog" :title="form.id ? '编辑连接' : '新增连接'" class="max-w-xl">
      <div class="grid gap-4 sm:grid-cols-2">
        <div class="space-y-1.5 sm:col-span-2">
          <Label for="conn-name">连接名称</Label>
          <Input id="conn-name" v-model="form.name" placeholder="如 阿里云百炼 / 本地 Ollama" />
        </div>
        <div class="space-y-1.5">
          <Label>服务商</Label>
          <Select v-model="form.provider" :options="providerOptions" @update:model-value="onProviderChange($event as string)" />
        </div>
        <div class="space-y-1.5">
          <Label for="conn-key">API Key</Label>
          <Input
            id="conn-key"
            v-model="form.apiKey"
            type="password"
            :placeholder="form.id ? '留空表示不修改' : '请输入 API Key'"
            autocomplete="off"
          />
        </div>
        <div class="space-y-1.5 sm:col-span-2">
          <Label for="conn-url">接口地址</Label>
          <Input id="conn-url" v-model="form.baseUrl" placeholder="OpenAI 兼容 base_url" />
        </div>
        <div class="flex items-center justify-between rounded-md border p-3 sm:col-span-2">
          <div>
            <div class="text-sm font-medium">启用连接</div>
            <div class="text-xs text-muted-foreground">停用后该连接下的模型不参与任务</div>
          </div>
          <Switch :model-value="form.enabled === 1" @update:model-value="form.enabled = $event ? 1 : 0" />
        </div>
      </div>
      <template #footer>
        <Button variant="outline" :disabled="saving" @click="dialog = false">取消</Button>
        <Button :disabled="saving" @click="save">{{ saving ? '保存中…' : '保存' }}</Button>
      </template>
    </Dialog>

    <Dialog v-model:open="pullDialog" title="拉取远端模型" :description="`连接：${pullConfig?.name ?? ''}`" class="max-w-xl">
      <div class="space-y-3">
        <div class="flex items-center gap-2">
          <Button variant="outline" size="sm" :disabled="pullLoading" @click="fetchRemote">
            <Loader2 v-if="pullLoading" class="animate-spin" />
            <CloudDownload v-else />
            重新拉取
          </Button>
          <label class="ml-auto flex items-center gap-2 text-sm text-muted-foreground">
            <Checkbox :model-value="allSelected" @update:model-value="toggleAll" />
            全选
          </label>
        </div>

        <div class="max-h-80 space-y-1 overflow-y-auto rounded-md border p-2">
          <div v-if="pullLoading" class="space-y-2 p-2">
            <Skeleton v-for="i in 6" :key="i" class="h-7" />
          </div>
          <template v-else-if="remoteModels.length">
            <label
              v-for="model in remoteModels"
              :key="model"
              class="flex cursor-pointer items-center gap-2 rounded-sm px-2 py-1.5 text-sm hover:bg-accent"
            >
              <Checkbox
                :model-value="selectedModels.includes(model)"
                @update:model-value="toggleModel(model, $event)"
              />
              <span class="font-mono text-xs">{{ model }}</span>
            </label>
          </template>
          <Empty v-else title="未拉取到模型" description="请检查接口地址与 API Key 是否正确。" />
        </div>

        <p class="text-xs text-muted-foreground">已选 {{ selectedModels.length }} / {{ remoteModels.length }} 个模型</p>
      </div>

      <template #footer>
        <Button variant="outline" :disabled="importLoading" @click="pullDialog = false">取消</Button>
        <Button :disabled="importLoading || !selectedModels.length" @click="confirmImport">
          {{ importLoading ? '导入中…' : '导入所选' }}
        </Button>
      </template>
    </Dialog>

    <ConfirmDialog
      :open="!!deleteConnection"
      title="删除 AI 连接"
      :description="`确认删除连接「${deleteConnection?.name ?? ''}」及其下所有模型？`"
      confirm-text="删除"
      destructive
      :loading="deleting"
      @update:open="deleteConnection = null"
      @confirm="confirmDeleteConnection"
    />

    <ConfirmDialog
      :open="!!deleteModel"
      title="删除模型"
      :description="`确认删除模型「${deleteModel?.model ?? ''}」？`"
      confirm-text="删除"
      destructive
      :loading="deleting"
      @update:open="deleteModel = null"
      @confirm="confirmDeleteModel"
    />
  </div>
</template>

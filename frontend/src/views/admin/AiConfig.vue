<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import {
  ChevronDown,
  CloudDownload,
  Globe,
  KeyRound,
  Loader2,
  Pencil,
  PlugZap,
  Plus,
  Search,
  Server,
  Star,
  Trash2,
} from '@lucide/vue'
import { toast } from 'vue-sonner'
import { adminApi } from '@/api'
import type { ConnectivityTest, SearchConfig, SearchTestResult } from '@/types/api'
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

// ---- 采集搜索源（Tavily / SearXNG / Serper 多引擎链式切换） ----
const searchConfig = ref<SearchConfig | null>(null)
const searchTestResult = ref<SearchTestResult | null>(null)
const searchDialog = ref(false)
const searchSaving = ref(false)
const searchTesting = ref(false)
const dialogTesting = ref(false)
const dialogTestResult = ref<SearchTestResult | null>(null)
const searchForm = reactive({
  engines: [] as string[],
  tavilyApiKey: '',
  searxngBaseUrl: '',
  searxngToken: '',
  serperApiKey: '',
})
const engineDefs = [
  { value: 'tavily', label: 'Tavily', desc: 'API Key，返回内容摘要，免费档每月 1000 积分' },
  { value: 'searxng', label: 'SearXNG', desc: '自建实例（需开启 JSON 输出），Token 可选' },
  { value: 'serper', label: 'Serper', desc: 'Google 搜索结果' },
]
const engineLabels: Record<string, string> = {
  tavily: 'Tavily',
  searxng: 'SearXNG',
  serper: 'Serper',
  duckduckgo: 'DuckDuckGo',
  none: '无可用引擎',
}

async function loadSearchConfig() {
  searchConfig.value = await adminApi.getSearchConfig()
}

function toggleEngine(value: string, checked: boolean) {
  if (checked) {
    if (!searchForm.engines.includes(value)) searchForm.engines.push(value)
  } else {
    searchForm.engines = searchForm.engines.filter((e) => e !== value)
  }
}

function openSearchEdit() {
  Object.assign(searchForm, {
    engines: [...(searchConfig.value?.engines ?? [])],
    tavilyApiKey: '',
    searxngBaseUrl: searchConfig.value?.searxngBaseUrl ?? '',
    searxngToken: '',
    serperApiKey: '',
  })
  dialogTestResult.value = null
  searchDialog.value = true
}

async function saveSearch() {
  searchSaving.value = true
  try {
    searchConfig.value = await adminApi.saveSearchConfig({ ...searchForm })
    toast.success('搜索源配置已保存，下个抓取任务生效')
    searchDialog.value = false
  } finally {
    searchSaving.value = false
  }
}

function applyTestResult(result: SearchTestResult) {
  if (result.success) {
    toast.success(
      `${engineLabels[result.engine] ?? result.engine} 连通正常，返回 ${result.count} 条（${result.latencyMs} ms）` +
        (result.tavilyUsage ? `；Tavily 用量：${result.tavilyUsage}` : ''),
    )
  } else {
    toast.error(`搜索测试失败：${result.error ?? '未知错误'}`)
  }
}

/** 卡片上的测试：验证已保存配置。 */
async function testSearch() {
  if (searchTesting.value) return
  searchTesting.value = true
  try {
    const result = await adminApi.testSearchConfig()
    searchTestResult.value = result
    applyTestResult(result)
  } finally {
    searchTesting.value = false
  }
}

/** 弹窗里的测试：用当前表单值（Key 留空沿用已存值），无需先保存。 */
async function testSearchInDialog() {
  if (dialogTesting.value) return
  dialogTesting.value = true
  try {
    const result = await adminApi.testSearchConfig({ ...searchForm })
    dialogTestResult.value = result
    applyTestResult(result)
  } finally {
    dialogTesting.value = false
  }
}

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
// 远端模型可能成百上千（如 OpenRouter），按关键字过滤后再勾选
const modelKeyword = ref('')
const filteredRemoteModels = computed(() => {
  const keyword = modelKeyword.value.trim().toLowerCase()
  if (!keyword) return remoteModels.value
  return remoteModels.value.filter((m) => m.toLowerCase().includes(keyword))
})
const testingModels = reactive<Record<string, boolean>>({})
const testResults = reactive<Record<string, ConnectivityTest>>({})

const deleteConnection = ref<any>(null)
const deleteModel = ref<any>(null)
const deleting = ref(false)

// 全选只作用于当前筛选结果：搜索时勾选可见项，不吞掉之前选的其他项
const allSelected = computed(
  () =>
    filteredRemoteModels.value.length > 0 &&
    filteredRemoteModels.value.every((m) => selectedModels.value.includes(m)),
)

async function load() {
  loading.value = true
  try {
    connections.value = await adminApi.aiConfigs()
  } finally {
    loading.value = false
  }
  // 搜索源配置独立加载，失败不影响 AI 连接列表
  loadSearchConfig().catch(() => {})
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
  modelKeyword.value = ''
  Object.keys(testResults).forEach((key) => delete testResults[key])
}

async function testModel(model: string) {
  if (!pullConfig.value || testingModels[model]) return
  testingModels[model] = true
  try {
    const result = await adminApi.testAiConfig(pullConfig.value.id, model)
    testResults[model] = result
    if (result.success) {
      toast.success(`${model} 连通正常（${result.latencyMs} ms）`)
    } else {
      toast.error(`${model} 连通失败：${result.error ?? '未知错误'}`)
    }
  } finally {
    testingModels[model] = false
  }
}

function toggleAll(checked: boolean) {
  if (checked) {
    selectedModels.value = [...new Set([...selectedModels.value, ...filteredRemoteModels.value])]
  } else {
    const visible = new Set(filteredRemoteModels.value)
    selectedModels.value = selectedModels.value.filter((m) => !visible.has(m))
  }
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

    <template v-else>
      <Card v-if="searchConfig">
        <CardContent class="p-4">
          <div class="flex flex-col gap-3 lg:flex-row lg:items-center">
            <span class="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-muted">
              <Globe class="h-4 w-4 text-muted-foreground" />
            </span>
            <div class="min-w-0 flex-1">
              <div class="flex flex-wrap items-center gap-2">
                <span class="text-sm font-semibold">采集搜索源</span>
                <Badge v-for="engine in searchConfig.engines" :key="engine" variant="outline">
                  {{ engineLabels[engine] ?? engine }}
                </Badge>
                <span v-if="!searchConfig.engines?.length" class="text-xs text-muted-foreground">
                  未启用第三方引擎，走系统默认（Serper Key 或 DuckDuckGo）
                </span>
              </div>
              <div class="mt-1 flex flex-wrap gap-x-3 gap-y-1 text-xs text-muted-foreground">
                <span>Tavily Key：{{ searchConfig.tavilyApiKeySet ? '已配置' : '未配置' }}</span>
                <span v-if="searchConfig.searxngBaseUrl">
                  SearXNG：{{ searchConfig.searxngBaseUrl }}{{ searchConfig.searxngTokenSet ? '（含 Token）' : '' }}
                </span>
                <span>Serper Key：{{ searchConfig.serperApiKeySet ? '已配置' : '未配置' }}</span>
              </div>
              <p
                v-if="searchTestResult"
                class="mt-1 text-xs"
                :class="searchTestResult.success ? 'text-emerald-600 dark:text-emerald-400' : 'text-destructive'"
              >
                上次测试：{{ engineLabels[searchTestResult.engine] ?? searchTestResult.engine }}
                {{ searchTestResult.success ? `返回 ${searchTestResult.count} 条（${searchTestResult.latencyMs} ms）` : `失败——${searchTestResult.error}` }}
              </p>
            </div>
            <div class="flex shrink-0 flex-wrap items-center gap-1.5">
              <Button variant="outline" size="sm" :disabled="searchTesting" @click="testSearch">
                <Loader2 v-if="searchTesting" class="animate-spin" />
                <PlugZap v-else />
                测试
              </Button>
              <Button variant="outline" size="sm" @click="openSearchEdit">
                <Pencil />
                编辑
              </Button>
            </div>
          </div>
        </CardContent>
      </Card>

      <div v-if="connections.length" class="space-y-3">
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
    </template>

    <Dialog
      v-model:open="searchDialog"
      title="采集搜索源"
      description="可同时启用多个引擎：按 Tavily → SearXNG → Serper 顺序尝试第一个可用引擎，全部失败回落 DuckDuckGo"
      class="max-w-xl"
    >
      <div class="space-y-4">
        <div class="space-y-2">
          <label
            v-for="engine in engineDefs"
            :key="engine.value"
            class="flex cursor-pointer items-start gap-2.5 rounded-md border p-3"
            :class="searchForm.engines.includes(engine.value) && 'border-primary/40 bg-muted/40'"
          >
            <Checkbox
              :model-value="searchForm.engines.includes(engine.value)"
              class="mt-0.5"
              @update:model-value="toggleEngine(engine.value, $event)"
            />
            <span class="min-w-0">
              <span class="block text-sm font-medium">{{ engine.label }}</span>
              <span class="block text-xs text-muted-foreground">{{ engine.desc }}</span>
            </span>
          </label>
        </div>

        <div v-if="searchForm.engines.includes('tavily')" class="space-y-1.5">
          <Label for="tavily-key">Tavily API Key</Label>
          <Input
            id="tavily-key"
            v-model="searchForm.tavilyApiKey"
            type="password"
            :placeholder="searchConfig?.tavilyApiKeySet ? '已配置，留空表示不修改' : 'tvly-…'"
            autocomplete="off"
          />
        </div>
        <template v-if="searchForm.engines.includes('searxng')">
          <div class="space-y-1.5">
            <Label for="searxng-url">实例地址</Label>
            <Input id="searxng-url" v-model="searchForm.searxngBaseUrl" placeholder="如 http://192.168.1.10:8080（需开启 JSON 输出）" />
          </div>
          <div class="space-y-1.5">
            <Label for="searxng-token">访问 Token（可选）</Label>
            <Input
              id="searxng-token"
              v-model="searchForm.searxngToken"
              type="password"
              :placeholder="searchConfig?.searxngTokenSet ? '已配置，留空表示不修改' : '实例未启用鉴权可留空'"
              autocomplete="off"
            />
          </div>
        </template>
        <div v-if="searchForm.engines.includes('serper')" class="space-y-1.5">
          <Label for="serper-key">Serper API Key</Label>
          <Input
            id="serper-key"
            v-model="searchForm.serperApiKey"
            type="password"
            :placeholder="searchConfig?.serperApiKeySet ? '已配置，留空表示不修改' : '留空则沿用环境变量 SERPER_API_KEY'"
            autocomplete="off"
          />
        </div>

        <p v-if="!searchForm.engines.length" class="text-xs text-muted-foreground">
          未启用任何引擎时走系统默认链：有 Serper Key 用 Google 结果，否则 DuckDuckGo。
        </p>

        <p
          v-if="dialogTestResult"
          class="rounded-md px-3 py-2 text-xs"
          :class="dialogTestResult.success ? 'bg-emerald-500/10 text-emerald-700 dark:text-emerald-400' : 'bg-destructive/10 text-destructive'"
        >
          测试（按当前表单值）：{{ engineLabels[dialogTestResult.engine] ?? dialogTestResult.engine }}
          {{ dialogTestResult.success ? `返回 ${dialogTestResult.count} 条（${dialogTestResult.latencyMs} ms）` : `——${dialogTestResult.error}` }}
          <template v-if="dialogTestResult.tavilyUsage">；Tavily 用量：{{ dialogTestResult.tavilyUsage }}</template>
        </p>
      </div>
      <template #footer>
        <Button variant="outline" :disabled="searchSaving || dialogTesting" @click="searchDialog = false">取消</Button>
        <Button variant="outline" :disabled="dialogTesting" @click="testSearchInDialog">
          <Loader2 v-if="dialogTesting" class="animate-spin" />
          <PlugZap v-else />
          测试
        </Button>
        <Button :disabled="searchSaving" @click="saveSearch">{{ searchSaving ? '保存中…' : '保存' }}</Button>
      </template>
    </Dialog>

    <Dialog v-model:open="dialog" :title="form.id ? '编辑连接' : '新增连接'" class="max-w-xl">
      <div class="grid gap-4 sm:grid-cols-2">
        <div class="space-y-1.5 sm:col-span-2">
          <Label for="conn-name">连接名称</Label>
          <Input id="conn-name" v-model="form.name" placeholder="如 阿里云百炼 / 本地 Ollama" />
        </div>
        <div class="space-y-1.5">
          <Label for="conn-provider">服务商</Label>
          <Select
            id="conn-provider"
            v-model="form.provider"
            :options="providerOptions"
            @update:model-value="onProviderChange($event as string)"
          />
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

        <div class="relative">
          <Search class="absolute left-2.5 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
          <Input v-model="modelKeyword" placeholder="按名称筛选模型，如 qwen / max…" class="pl-8" />
        </div>

        <div class="max-h-80 space-y-1 overflow-y-auto rounded-md border p-2">
          <div v-if="pullLoading" class="space-y-2 p-2">
            <Skeleton v-for="i in 6" :key="i" class="h-7" />
          </div>
          <template v-else-if="filteredRemoteModels.length">
            <div
              v-for="model in filteredRemoteModels"
              :key="model"
              class="flex items-center gap-2 rounded-sm px-2 py-1.5 hover:bg-accent"
            >
              <label class="flex min-w-0 flex-1 cursor-pointer items-center gap-2">
                <Checkbox
                  :model-value="selectedModels.includes(model)"
                  @update:model-value="toggleModel(model, $event)"
                />
                <span class="truncate font-mono text-xs" :title="model">{{ model }}</span>
              </label>
              <span
                v-if="testResults[model]"
                class="shrink-0 text-xs tabular-nums"
                :class="
                  testResults[model].success
                    ? 'text-emerald-600 dark:text-emerald-400'
                    : 'text-destructive'
                "
                :title="testResults[model].error ?? testResults[model].reply ?? ''"
              >
                {{ testResults[model].success ? `${testResults[model].latencyMs} ms` : '失败' }}
              </span>
              <Button
                variant="ghost"
                size="icon-sm"
                class="shrink-0"
                :disabled="testingModels[model]"
                :aria-label="`测试 ${model} 连通性`"
                @click="testModel(model)"
              >
                <Loader2 v-if="testingModels[model]" class="animate-spin" />
                <PlugZap v-else />
              </Button>
            </div>
          </template>
          <Empty
            v-else-if="remoteModels.length"
            title="没有匹配的模型"
            description="换个关键字试试。"
          />
          <Empty v-else title="未拉取到模型" description="请检查接口地址与 API Key 是否正确。" />
        </div>

        <p class="text-xs text-muted-foreground">
          已选 {{ selectedModels.length }} / {{ remoteModels.length }} 个模型<template v-if="modelKeyword.trim()">
            ，筛选出 {{ filteredRemoteModels.length }} 个</template
          >；点击行尾图标可测试模型连通性
        </p>
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

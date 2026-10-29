<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { Copy, Download, Loader2, Pencil, Plus, RefreshCw, Sparkles, Trash2 } from '@lucide/vue'
import { toast } from 'vue-sonner'
import { adminApi } from '@/api'
import { usePagedList } from '@/composables/usePagedList'
import { formatTime } from '@/lib/utils'
import type { AiImageGenerateResult, AiImagePrompt, AiImageProvider, AiImageStatus } from '@/types/api'
import {
  Alert,
  Badge,
  Button,
  Card,
  CardContent,
  Checkbox,
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
  Tabs,
  Textarea,
  type TabItem,
} from '@/components/ui'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import PageHeader from '@/components/PageHeader.vue'

/**
 * AI 绘图：供应商接入、提示词模板、海报生成、绘制历史四块。
 *
 * 绘图成本高，只作为后台内部出图工具使用，访客端没有任何入口。
 * 供应商与模板都遵循「启用 + 全局唯一默认」两个开关，缺任一项时生成页直接提示缺口。
 */

const tabs: TabItem[] = [
  { value: 'providers', label: '供应商' },
  { value: 'prompts', label: '提示词模板' },
  { value: 'generate', label: '生成海报' },
  { value: 'jobs', label: '绘制历史' },
]
const tab = ref<'providers' | 'prompts' | 'generate' | 'jobs'>('providers')

// ---------- 供应商 ----------
const providers = ref<AiImageProvider[]>([])
const providersLoading = ref(true)
const providerDialog = ref(false)
const providerSaving = ref(false)
const providerForm = reactive({
  id: null as number | null,
  name: '',
  baseUrl: '',
  apiKey: '',
  model: '',
  size: '1024x1536',
  quality: '',
  enabled: 1,
})
const providerDeleteTarget = ref<AiImageProvider | null>(null)
const providerDeleting = ref(false)

async function loadProviders() {
  providersLoading.value = true
  try {
    providers.value = await adminApi.aiImageProviders()
  } finally {
    providersLoading.value = false
  }
}

function openProviderCreate() {
  Object.assign(providerForm, {
    id: null,
    name: '',
    baseUrl: '',
    apiKey: '',
    model: '',
    size: '1024x1536',
    quality: '',
    enabled: 1,
  })
  providerDialog.value = true
}

function openProviderEdit(row: AiImageProvider) {
  Object.assign(providerForm, {
    id: row.id,
    name: row.name,
    baseUrl: row.baseUrl,
    apiKey: '',
    model: row.model,
    size: row.size,
    quality: row.quality ?? '',
    enabled: row.enabled,
  })
  providerDialog.value = true
}

async function saveProvider() {
  if (!providerForm.name.trim() || !providerForm.baseUrl.trim() || !providerForm.model.trim()) {
    toast.warning('请填写名称、接口地址与绘图模型')
    return
  }
  providerSaving.value = true
  try {
    await adminApi.saveAiImageProvider({
      id: providerForm.id,
      name: providerForm.name.trim(),
      baseUrl: providerForm.baseUrl.trim(),
      apiKey: providerForm.apiKey.trim() || undefined,
      model: providerForm.model.trim(),
      size: providerForm.size.trim() || '1024x1536',
      quality: providerForm.quality.trim() || null,
      enabled: providerForm.enabled,
    })
    toast.success('已保存')
    providerDialog.value = false
    await loadProviders()
  } finally {
    providerSaving.value = false
  }
}

async function confirmDeleteProvider() {
  if (!providerDeleteTarget.value) return
  providerDeleting.value = true
  try {
    await adminApi.deleteAiImageProvider(providerDeleteTarget.value.id)
    toast.success('已删除')
    providerDeleteTarget.value = null
    await loadProviders()
  } finally {
    providerDeleting.value = false
  }
}

async function setDefaultProvider(row: AiImageProvider) {
  if (row.isDefault === 1) return
  await adminApi.setDefaultAiImageProvider(row.id)
  toast.success(`已将「${row.name}」设为默认`)
  await loadProviders()
}

// ---------- 提示词模板 ----------
const prompts = ref<AiImagePrompt[]>([])
const promptsLoading = ref(true)
const promptDialog = ref(false)
const promptSaving = ref(false)
const promptForm = reactive({
  id: null as number | null,
  name: '',
  content: '',
  enabled: 1,
})
const promptDeleteTarget = ref<AiImagePrompt | null>(null)
const promptDeleting = ref(false)

/** 模板可用变量：与后端 AiImageGenerationService 渲染时使用的键一一对应 */
const promptVariables = [
  { key: '{{brand}}', label: '品牌名' },
  { key: '{{series}}', label: '系列名' },
  { key: '{{count}}', label: '机型数量' },
  { key: '{{devices}}', label: '机型清单（多行）' },
  { key: '{{yearRange}}', label: '年份范围' },
  { key: '{{paramLines}}', label: '参数取值清单（多行）' },
  { key: '{{title}}', label: '海报标题' },
]

async function loadPrompts() {
  promptsLoading.value = true
  try {
    prompts.value = await adminApi.aiImagePrompts()
  } finally {
    promptsLoading.value = false
  }
}

function openPromptCreate() {
  Object.assign(promptForm, { id: null, name: '', content: '', enabled: 1 })
  promptDialog.value = true
}

function openPromptEdit(row: AiImagePrompt) {
  Object.assign(promptForm, { id: row.id, name: row.name, content: row.content, enabled: row.enabled })
  promptDialog.value = true
}

/** 内置模板不允许删除，复制一份再改是唯一的安全改法 */
function copyPrompt(row: AiImagePrompt) {
  Object.assign(promptForm, {
    id: null,
    name: `${row.name} 副本`,
    content: row.content,
    enabled: 1,
  })
  promptDialog.value = true
}

async function savePrompt() {
  if (!promptForm.name.trim() || !promptForm.content.trim()) {
    toast.warning('请填写模板名称与提示词正文')
    return
  }
  promptSaving.value = true
  try {
    await adminApi.saveAiImagePrompt({
      id: promptForm.id,
      name: promptForm.name.trim(),
      content: promptForm.content,
      enabled: promptForm.enabled,
    })
    toast.success('已保存')
    promptDialog.value = false
    await loadPrompts()
  } finally {
    promptSaving.value = false
  }
}

async function confirmDeletePrompt() {
  if (!promptDeleteTarget.value) return
  promptDeleting.value = true
  try {
    await adminApi.deleteAiImagePrompt(promptDeleteTarget.value.id)
    toast.success('已删除')
    promptDeleteTarget.value = null
    await loadPrompts()
  } finally {
    promptDeleting.value = false
  }
}

async function setDefaultPrompt(row: AiImagePrompt) {
  if (row.isDefault === 1) return
  await adminApi.setDefaultAiImagePrompt(row.id)
  toast.success(`已将「${row.name}」设为默认模板`)
  await loadPrompts()
}

// ---------- 绘制历史 ----------
const jobStatusOptions = [
  { label: '全部状态', value: '' },
  { label: '成功', value: 'SUCCESS' },
  { label: '失败', value: 'FAILED' },
]
const { list: jobs, total: jobTotal, loading: jobsLoading, query: jobQuery, load: loadJobs, search: searchJobs } =
  usePagedList({
    size: 20,
    immediate: false,
    filters: () => ({ status: '' }),
    fetcher: (params) =>
      adminApi.aiImageJobs({ ...params, status: params.status || undefined }),
  })

const selectedJobIds = ref<number[]>([])
const allJobsSelected = computed(
  () => jobs.value.length > 0 && jobs.value.every((job) => selectedJobIds.value.includes(job.id)),
)
const jobDeleteOpen = ref(false)
const jobDeleting = ref(false)
/** 历史列表首次进入才加载：三个接口不必在页面打开时一起打出去 */
const jobsLoaded = ref(false)

function toggleJob(id: number, checked: boolean) {
  selectedJobIds.value = checked
    ? [...selectedJobIds.value, id]
    : selectedJobIds.value.filter((item) => item !== id)
}

function toggleAllJobs(checked: boolean) {
  selectedJobIds.value = checked ? jobs.value.map((job) => job.id) : []
}

async function refreshJobs() {
  selectedJobIds.value = []
  jobsLoaded.value = true
  await loadJobs()
}

async function confirmDeleteJobs() {
  jobDeleting.value = true
  try {
    const deleted = await adminApi.deleteAiImageJobs(selectedJobIds.value)
    toast.success(`已删除 ${deleted} 条绘图记录`)
    selectedJobIds.value = []
    jobDeleteOpen.value = false
    await loadJobs()
  } finally {
    jobDeleting.value = false
  }
}

// ---------- 可用性 ----------
const providerReady = computed(() => providers.value.some((item) => item.enabled === 1))
const promptReady = computed(() => prompts.value.some((item) => item.enabled === 1))
const entryReady = computed(() => providerReady.value && promptReady.value)
const entryHint = computed(() =>
  providerReady.value ? '还没有启用的提示词模板，无法生成海报。' : '还没有启用的绘图供应商，无法生成海报。',
)

// ---------- 生成海报（内部工具） ----------
const generateForm = reactive({
  seriesId: undefined as number | undefined,
  promptId: undefined as number | undefined,
  title: '',
})
const seriesOptions = ref<{ label: string; value: number }[]>([])
const seriesLoading = ref(false)
const generateStatus = ref<AiImageStatus | null>(null)
const generating = ref(false)
const generateResult = ref<AiImageGenerateResult | null>(null)
const generateError = ref('')

const promptOptions = computed(() =>
  prompts.value
    .filter((prompt) => prompt.enabled === 1)
    .map((prompt) => ({
      label: prompt.isDefault === 1 ? `${prompt.name}（默认）` : prompt.name,
      value: prompt.id,
    })),
)

/** 选项按需加载：进页面就拉系列列表会让「看历史」这种轻操作多一次请求 */
async function loadGenerateOptions() {
  if (!generateStatus.value) {
    try {
      generateStatus.value = await adminApi.aiImageStatus()
    } catch {
      // 可用性只用于提示，失败时按「未知」处理，真正生成仍会给出明确错误
      generateStatus.value = null
    }
  }
  if (seriesOptions.value.length || seriesLoading.value) return
  seriesLoading.value = true
  try {
    const rows = await adminApi.series()
    seriesOptions.value = rows.map((row) => ({
      label: row.brandName ? `${row.brandName} · ${row.name}` : row.name,
      value: row.id,
    }))
  } finally {
    seriesLoading.value = false
  }
  if (!generateForm.promptId) {
    const enabled = prompts.value.filter((prompt) => prompt.enabled === 1)
    generateForm.promptId = (enabled.find((prompt) => prompt.isDefault === 1) ?? enabled[0])?.id
  }
}

async function generateImage() {
  if (!generateForm.seriesId || generating.value) return
  generating.value = true
  generateError.value = ''
  generateResult.value = null
  try {
    generateResult.value = await adminApi.generateAiImage({
      seriesId: generateForm.seriesId,
      promptId: generateForm.promptId,
      title: generateForm.title.trim() || undefined,
    })
    toast.success('海报已生成')
    if (jobsLoaded.value) loadJobs()
  } catch (error) {
    // 请求层已弹过 toast，这里留一条可回看的错误文案
    generateError.value = error instanceof Error ? error.message : '生成失败，请稍后再试'
  } finally {
    generating.value = false
  }
}

function downloadGenerated() {
  if (!generateResult.value) return
  const link = document.createElement('a')
  link.href = generateResult.value.imageUrl
  link.download = `spectrace-ai-${generateForm.seriesId ?? 'series'}-${Date.now()}.png`
  link.click()
}

function switchTab(value: string | number) {
  tab.value = value as typeof tab.value
  if (value === 'generate') loadGenerateOptions()
  if (value === 'jobs' && !jobsLoaded.value) {
    jobsLoaded.value = true
    loadJobs()
  }
}

onMounted(() => {
  loadProviders()
  loadPrompts()
})
</script>

<template>
  <div class="space-y-4">
    <PageHeader title="AI 绘图" description="内部出图工具：接入绘图服务、维护提示词模板并生成社媒海报（不对访客开放）">
      <template #actions>
        <Button v-if="tab === 'providers'" size="sm" @click="openProviderCreate">
          <Plus />
          新增供应商
        </Button>
        <Button v-else-if="tab === 'prompts'" size="sm" @click="openPromptCreate">
          <Plus />
          新增模板
        </Button>
        <Button v-else-if="tab === 'jobs'" variant="outline" size="sm" @click="refreshJobs">
          <RefreshCw />
          刷新
        </Button>
      </template>
    </PageHeader>

    <Tabs :model-value="tab" :items="tabs" @update:model-value="switchTab" />

    <Alert
      v-if="!providersLoading && !promptsLoading && !entryReady"
      variant="warning"
      title="AI 绘图尚不可用"
      :description="entryHint"
    />

    <!-- 生成海报：仅后台内部使用，访客端没有任何入口 -->
    <Card v-if="tab === 'generate'">
      <CardContent class="space-y-4 p-4">
        <!-- 服务端开关关闭（或供应商/模板未就绪）时先给结论，不让管理员点了才报错 -->
        <Alert
          v-if="generateStatus && !generateStatus.available"
          variant="warning"
          title="暂时无法生成"
          :description="generateStatus.reason ?? '尚未配置可用的绘图供应商或提示词模板'"
        />
        <div class="grid grid-cols-1 gap-3 sm:grid-cols-2">
          <div class="space-y-1.5">
            <Label for="ai-gen-series">系列</Label>
            <Select
              id="ai-gen-series"
              v-model="generateForm.seriesId"
              :options="seriesOptions"
              :placeholder="seriesLoading ? '加载中…' : '选择系列'"
            />
          </div>
          <div class="space-y-1.5">
            <Label for="ai-gen-prompt">提示词模板</Label>
            <Select id="ai-gen-prompt" v-model="generateForm.promptId" :options="promptOptions" placeholder="选择模板" />
          </div>
          <div class="space-y-1.5 sm:col-span-2">
            <Label for="ai-gen-title">海报标题（可选）</Label>
            <Input id="ai-gen-title" v-model="generateForm.title" maxlength="60" placeholder="如 小米数字系列 历代参数进化" />
          </div>
        </div>

        <p class="text-xs text-muted-foreground">
          机型范围取该系列全部已上架机型，参数由服务端选数值型默认项；绘图按管理员账号分钟级限流，单张约需 30–120 秒。
        </p>

        <div class="flex flex-wrap items-center gap-2">
          <Button :disabled="generating || !generateForm.seriesId || !entryReady" @click="generateImage">
            <Loader2 v-if="generating" class="animate-spin" />
            <Sparkles v-else />
            {{ generating ? '生成中，约需 30-120 秒…' : '生成海报' }}
          </Button>
          <Button v-if="generateResult" variant="outline" @click="downloadGenerated">
            <Download />
            下载图片
          </Button>
          <p v-if="generateError" class="text-xs text-destructive">{{ generateError }}</p>
        </div>

        <div v-if="generateResult" class="space-y-2">
          <img :src="generateResult.imageUrl" alt="AI 生成海报" class="mx-auto max-h-[52vh] rounded-lg border" />
          <details class="rounded-md border px-3 py-2">
            <summary class="cursor-pointer text-xs text-muted-foreground">
              查看本次提示词（模板：{{ generateResult.promptName }} / 模型：{{ generateResult.model }}）
            </summary>
            <pre class="mt-2 whitespace-pre-wrap break-words text-xs text-muted-foreground">{{ generateResult.promptText }}</pre>
          </details>
        </div>
        <p v-else-if="!generating" class="rounded-md bg-muted/60 px-3 py-6 text-center text-sm text-muted-foreground">
          选择系列与模板后点「生成海报」，产物落在 uploads，可在「绘制历史」里清理
        </p>
      </CardContent>
    </Card>

    <!-- 供应商 -->
    <Card v-if="tab === 'providers'">
      <CardContent class="p-0">
        <div v-if="providersLoading" class="space-y-2 p-4">
          <Skeleton v-for="i in 4" :key="i" class="h-10" />
        </div>

        <Table v-else-if="providers.length">
          <TableHeader>
            <TableRow class="hover:bg-transparent">
              <TableHead>供应商 / 接口地址</TableHead>
              <TableHead class="w-40">模型</TableHead>
              <TableHead class="w-28">尺寸</TableHead>
              <TableHead class="w-24">API Key</TableHead>
              <TableHead class="w-24">状态</TableHead>
              <TableHead class="w-28 text-right">操作</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            <TableRow v-for="row in providers" :key="row.id">
              <TableCell>
                <div class="flex flex-wrap items-center gap-1.5">
                  <span class="font-medium">{{ row.name }}</span>
                  <Badge v-if="row.isDefault === 1" variant="success">默认</Badge>
                </div>
                <p class="mt-0.5 break-all font-mono text-xs text-muted-foreground">{{ row.baseUrl }}</p>
              </TableCell>
              <TableCell class="font-mono text-xs">{{ row.model }}</TableCell>
              <TableCell class="text-xs text-muted-foreground">{{ row.size }}</TableCell>
              <TableCell>
                <Badge :variant="row.apiKeySet ? 'secondary' : 'outline'">{{ row.apiKeySet ? '已配置' : '未配置' }}</Badge>
              </TableCell>
              <TableCell>
                <Badge :variant="row.enabled === 1 ? 'success' : 'secondary'">{{ row.enabled === 1 ? '启用' : '停用' }}</Badge>
              </TableCell>
              <TableCell class="text-right">
                <div class="flex justify-end gap-1">
                  <Button
                    v-if="row.isDefault !== 1"
                    variant="ghost"
                    size="sm"
                    :disabled="row.enabled !== 1"
                    @click="setDefaultProvider(row)"
                  >
                    设为默认
                  </Button>
                  <Button variant="ghost" size="icon-sm" aria-label="编辑" @click="openProviderEdit(row)">
                    <Pencil />
                  </Button>
                  <Button
                    variant="ghost"
                    size="icon-sm"
                    class="text-destructive"
                    aria-label="删除"
                    @click="providerDeleteTarget = row"
                  >
                    <Trash2 />
                  </Button>
                </div>
              </TableCell>
            </TableRow>
          </TableBody>
        </Table>

        <Empty
          v-else
          title="还没有绘图供应商"
          description="新增一个 OpenAI 兼容 /images/generations 的服务后，即可在「生成海报」页出图。"
        />
      </CardContent>
    </Card>

    <!-- 提示词模板 -->
    <Card v-else-if="tab === 'prompts'">
      <CardContent class="p-0">
        <div v-if="promptsLoading" class="space-y-2 p-4">
          <Skeleton v-for="i in 4" :key="i" class="h-10" />
        </div>

        <Table v-else-if="prompts.length">
          <TableHeader>
            <TableRow class="hover:bg-transparent">
              <TableHead>模板名称</TableHead>
              <TableHead>正文预览</TableHead>
              <TableHead class="w-28">状态</TableHead>
              <TableHead class="w-44 text-right">操作</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            <TableRow v-for="row in prompts" :key="row.id">
              <TableCell>
                <div class="flex flex-wrap items-center gap-1.5">
                  <span class="font-medium">{{ row.name }}</span>
                  <Badge v-if="row.builtin === 1" variant="outline">内置</Badge>
                  <Badge v-if="row.isDefault === 1" variant="success">默认</Badge>
                </div>
              </TableCell>
              <TableCell class="max-w-md">
                <p class="line-clamp-2 text-xs text-muted-foreground">{{ row.content }}</p>
              </TableCell>
              <TableCell>
                <Badge :variant="row.enabled === 1 ? 'success' : 'secondary'">{{ row.enabled === 1 ? '启用' : '停用' }}</Badge>
              </TableCell>
              <TableCell class="text-right">
                <div class="flex justify-end gap-1">
                  <Button
                    v-if="row.isDefault !== 1"
                    variant="ghost"
                    size="sm"
                    :disabled="row.enabled !== 1"
                    @click="setDefaultPrompt(row)"
                  >
                    设为默认
                  </Button>
                  <Button variant="ghost" size="icon-sm" aria-label="复制模板" @click="copyPrompt(row)">
                    <Copy />
                  </Button>
                  <Button variant="ghost" size="icon-sm" aria-label="编辑" @click="openPromptEdit(row)">
                    <Pencil />
                  </Button>
                  <Button
                    v-if="row.builtin !== 1"
                    variant="ghost"
                    size="icon-sm"
                    class="text-destructive"
                    aria-label="删除"
                    @click="promptDeleteTarget = row"
                  >
                    <Trash2 />
                  </Button>
                </div>
              </TableCell>
            </TableRow>
          </TableBody>
        </Table>

        <Empty v-else title="还没有提示词模板" description="内置模板会在启动时自动写入；也可以新增一套自己的版式。" />
      </CardContent>
    </Card>

    <!-- 绘制历史 -->
    <template v-else>
      <div class="flex flex-wrap items-center gap-2">
        <Select v-model="jobQuery.status" :options="jobStatusOptions" class="w-36" @update:model-value="searchJobs" />
        <span class="ml-auto text-xs text-muted-foreground">共 {{ jobTotal }} 条</span>
        <Button
          variant="outline"
          size="sm"
          class="text-destructive"
          :disabled="!selectedJobIds.length"
          @click="jobDeleteOpen = true"
        >
          <Trash2 />
          删除选中<template v-if="selectedJobIds.length">（{{ selectedJobIds.length }}）</template>
        </Button>
      </div>

      <Card>
        <CardContent class="p-0">
          <div v-if="jobsLoading" class="space-y-2 p-4">
            <Skeleton v-for="i in 6" :key="i" class="h-12" />
          </div>

          <Table v-else-if="jobs.length">
            <TableHeader>
              <TableRow class="hover:bg-transparent">
                <TableHead class="w-10">
                  <Checkbox :model-value="allJobsSelected" @update:model-value="toggleAllJobs" />
                </TableHead>
                <TableHead class="w-16">预览</TableHead>
                <TableHead>提示词 / 范围</TableHead>
                <TableHead class="w-36">模型</TableHead>
                <TableHead class="w-20">状态</TableHead>
                <TableHead class="w-20">耗时</TableHead>
                <TableHead class="w-40">时间</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              <TableRow v-for="row in jobs" :key="row.id">
                <TableCell>
                  <Checkbox
                    :model-value="selectedJobIds.includes(row.id)"
                    @update:model-value="toggleJob(row.id, $event)"
                  />
                </TableCell>
                <TableCell>
                  <a v-if="row.imageUrl" :href="row.imageUrl" target="_blank" rel="noopener" class="block">
                    <img :src="row.imageUrl" alt="绘图结果" class="h-10 w-10 rounded border object-cover" />
                  </a>
                  <span v-else class="text-xs text-muted-foreground">—</span>
                </TableCell>
                <TableCell class="max-w-sm">
                  <p class="truncate text-sm font-medium">{{ row.promptName || '—' }}</p>
                  <p class="truncate text-xs text-muted-foreground">{{ row.deviceNames || '—' }}</p>
                  <p v-if="row.errorMsg" class="mt-0.5 line-clamp-2 text-xs text-destructive">{{ row.errorMsg }}</p>
                </TableCell>
                <TableCell class="font-mono text-xs text-muted-foreground">{{ row.model || '—' }}</TableCell>
                <TableCell>
                  <Badge :variant="row.status === 'SUCCESS' ? 'success' : 'secondary'">
                    {{ row.status === 'SUCCESS' ? '成功' : '失败' }}
                  </Badge>
                </TableCell>
                <TableCell class="text-xs tabular-nums text-muted-foreground">
                  {{ row.durationMs != null ? `${row.durationMs} ms` : '—' }}
                </TableCell>
                <TableCell class="text-xs tabular-nums text-muted-foreground">{{ formatTime(row.createdAt) }}</TableCell>
              </TableRow>
            </TableBody>
          </Table>

          <Empty v-else title="暂无绘图记录" description="在「生成海报」页出图后，这里会记录每次结果与失败原因。" />
        </CardContent>
      </Card>

      <Pagination
        v-if="jobTotal > jobQuery.size"
        v-model:page="jobQuery.current"
        :page-size="jobQuery.size"
        :total="jobTotal"
      />
    </template>

    <Dialog v-model:open="providerDialog" :title="providerForm.id ? '编辑绘图供应商' : '新增绘图供应商'" class="max-w-xl">
      <div class="grid gap-4 sm:grid-cols-2">
        <div class="space-y-1.5 sm:col-span-2">
          <Label for="image-provider-name">名称</Label>
          <Input id="image-provider-name" v-model="providerForm.name" placeholder="如 OpenAI 官方 / 某中转站点" />
        </div>
        <div class="space-y-1.5 sm:col-span-2">
          <Label for="image-provider-url">接口地址</Label>
          <Input id="image-provider-url" v-model="providerForm.baseUrl" placeholder="OpenAI 兼容 base_url，如 https://api.openai.com/v1" />
        </div>
        <div class="space-y-1.5">
          <Label for="image-provider-key">API Key</Label>
          <Input
            id="image-provider-key"
            v-model="providerForm.apiKey"
            type="password"
            :placeholder="providerForm.id ? '留空表示不修改' : '请输入 API Key'"
            autocomplete="off"
          />
        </div>
        <div class="space-y-1.5">
          <Label for="image-provider-model">绘图模型</Label>
          <Input id="image-provider-model" v-model="providerForm.model" placeholder="如 gpt-image-1 / dall-e-3" />
        </div>
        <div class="space-y-1.5">
          <Label for="image-provider-size">出图尺寸</Label>
          <Input id="image-provider-size" v-model="providerForm.size" placeholder="如 1024x1536（竖版海报）" />
        </div>
        <div class="space-y-1.5">
          <Label for="image-provider-quality">画质（可选）</Label>
          <Input id="image-provider-quality" v-model="providerForm.quality" placeholder="留空则不传该参数" />
        </div>
        <div class="flex items-center justify-between rounded-md border p-3 sm:col-span-2">
          <div>
            <div class="text-sm font-medium">启用供应商</div>
            <div class="text-xs text-muted-foreground">停用后不会被生成页选中</div>
          </div>
          <Switch :model-value="providerForm.enabled === 1" @update:model-value="providerForm.enabled = $event ? 1 : 0" />
        </div>
      </div>
      <template #footer>
        <Button variant="outline" :disabled="providerSaving" @click="providerDialog = false">取消</Button>
        <Button :disabled="providerSaving" @click="saveProvider">{{ providerSaving ? '保存中…' : '保存' }}</Button>
      </template>
    </Dialog>

    <Dialog v-model:open="promptDialog" :title="promptForm.id ? '编辑提示词模板' : '新增提示词模板'" class="max-w-2xl">
      <div class="space-y-4">
        <div class="space-y-1.5">
          <Label for="image-prompt-name">模板名称</Label>
          <Input id="image-prompt-name" v-model="promptForm.name" placeholder="如 小红书竖版海报" />
        </div>

        <div class="space-y-1.5">
          <Label for="image-prompt-content">提示词正文</Label>
          <Textarea
            id="image-prompt-content"
            v-model="promptForm.content"
            :rows="14"
            class="font-mono text-xs"
            placeholder="用 {{devices}}、{{paramLines}} 等变量插入当前系列的机型与参数"
          />
          <p class="text-xs text-muted-foreground">变量会在生成时替换为当前系列的机型与参数，未定义的变量会被清空。</p>
          <div class="flex flex-wrap gap-1.5">
            <span
              v-for="variable in promptVariables"
              :key="variable.key"
              class="rounded-full bg-muted px-2 py-0.5 text-xs text-muted-foreground"
              :title="variable.label"
            >
              {{ variable.key }}
            </span>
          </div>
        </div>

        <div class="flex items-center justify-between rounded-md border p-3">
          <div>
            <div class="text-sm font-medium">启用模板</div>
            <div class="text-xs text-muted-foreground">停用后生成页无法选择该模板</div>
          </div>
          <Switch :model-value="promptForm.enabled === 1" @update:model-value="promptForm.enabled = $event ? 1 : 0" />
        </div>
      </div>
      <template #footer>
        <Button variant="outline" :disabled="promptSaving" @click="promptDialog = false">取消</Button>
        <Button :disabled="promptSaving" @click="savePrompt">{{ promptSaving ? '保存中…' : '保存' }}</Button>
      </template>
    </Dialog>

    <ConfirmDialog
      :open="!!providerDeleteTarget"
      title="删除绘图供应商"
      :description="`确认删除「${providerDeleteTarget?.name ?? ''}」？删除后若仍有其它启用供应商，默认会自动转移。`"
      confirm-text="删除"
      destructive
      :loading="providerDeleting"
      @update:open="providerDeleteTarget = null"
      @confirm="confirmDeleteProvider"
    />

    <ConfirmDialog
      :open="!!promptDeleteTarget"
      title="删除提示词模板"
      :description="`确认删除模板「${promptDeleteTarget?.name ?? ''}」？内置模板不可删除。`"
      confirm-text="删除"
      destructive
      :loading="promptDeleting"
      @update:open="promptDeleteTarget = null"
      @confirm="confirmDeletePrompt"
    />

    <ConfirmDialog
      :open="jobDeleteOpen"
      title="删除绘图历史"
      description="将同时删除服务器上的图片文件，删除后无法恢复。"
      confirm-text="删除"
      destructive
      :loading="jobDeleting"
      @update:open="jobDeleteOpen = false"
      @confirm="confirmDeleteJobs"
    />
  </div>
</template>

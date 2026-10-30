<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import {
  Check,
  Copy,
  KeyRound,
  Loader2,
  Pencil,
  Plug,
  Plus,
  RefreshCw,
  RotateCw,
  ScrollText,
  Trash2,
} from '@lucide/vue'
import { toast } from 'vue-sonner'
import { adminApi } from '@/api'
import { usePagedList } from '@/composables/usePagedList'
import { useAuthStore } from '@/stores/auth'
import { formatTime } from '@/lib/utils'
import type {
  McpAgent,
  McpAgentCreated,
  McpAgentRole,
  McpCallLog,
  McpCallStatus,
  McpScope,
} from '@/types/api'
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
} from '@/components/ui'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import PageHeader from '@/components/PageHeader.vue'

const auth = useAuthStore()
/** 与后端一致：ADMIN 可读凭据与日志，签发/轮换/吊销仅超管 */
const isSuperAdmin = computed(() => auth.isSuperAdmin)

/** 模板里访问不到 window，页面来源在 setup 里取一次用于拼接接入地址 */
const pageOrigin = window.location.origin

const tab = ref('agents')
const tabs = [
  { value: 'agents', label: '接入凭据' },
  { value: 'logs', label: '调用日志' },
]

const roleOptions: { label: string; value: McpAgentRole }[] = [
  { label: '只读', value: 'READ_ONLY' },
  { label: '编辑者', value: 'EDITOR' },
  { label: '管理员', value: 'ADMIN' },
]

const roleMeta: Record<McpAgentRole, { label: string; variant: 'secondary' | 'info' | 'warning' }> = {
  READ_ONLY: { label: '只读', variant: 'secondary' },
  EDITOR: { label: '编辑者', variant: 'info' },
  ADMIN: { label: '管理员', variant: 'warning' },
}

/**
 * 按资源分组只为展示顺序服务；每个 scope 的文案与后端 McpScope 描述保持一致，
 * 避免管理员按字面意思发出多余权限。
 */
const scopeGroups: { label: string; scopes: { value: McpScope; label: string; desc: string }[] }[] = [
  {
    label: '机型',
    scopes: [
      { value: 'device:read', label: '查询机型', desc: '列表、详情与参数' },
      { value: 'device:write', label: '维护机型', desc: '增删改、批量上下架' },
    ],
  },
  {
    label: '品牌 / 系列',
    scopes: [
      { value: 'brand:read', label: '查询品牌', desc: '品牌列表与详情' },
      { value: 'brand:write', label: '维护品牌', desc: '新增、修改、删除' },
      { value: 'series:read', label: '查询系列', desc: '系列列表与详情' },
      { value: 'series:write', label: '维护系列', desc: '新增、修改、删除' },
    ],
  },
  {
    label: '参数元数据',
    scopes: [
      { value: 'param:read', label: '查询参数定义', desc: '分组与 paramKey' },
      { value: 'param:write', label: '维护参数定义', desc: '分组与参数定义增删改' },
    ],
  },
  {
    label: '抓取审核',
    scopes: [
      { value: 'review:read', label: '查询待审核', desc: '抓取结果与 diff' },
      { value: 'review:write', label: '审核通过/丢弃', desc: '写入正式机型库' },
    ],
  },
  {
    label: '纠错反馈',
    scopes: [
      { value: 'feedback:read', label: '查询纠错', desc: '访客提交的纠错' },
      { value: 'feedback:write', label: '处理纠错', desc: '标记已处理或忽略' },
    ],
  },
  {
    label: '审计',
    scopes: [{ value: 'stats:read', label: '查询统计与日志', desc: '数据概览、操作与调用日志' }],
  },
]

const allScopeValues = scopeGroups.flatMap((group) => group.scopes.map((scope) => scope.value))

// ---------- 凭据列表 ----------
const agents = ref<McpAgent[]>([])
const agentsLoading = ref(false)

async function loadAgents() {
  agentsLoading.value = true
  try {
    agents.value = await adminApi.mcpAgents()
  } catch {
    // 拦截器已提示，保留上一次结果，避免页面闪成空态
  } finally {
    agentsLoading.value = false
  }
}

onMounted(loadAgents)

// ---------- 签发 / 编辑 ----------
const formOpen = ref(false)
const editing = ref<McpAgent | null>(null)
const saving = ref(false)
const form = reactive({
  name: '',
  description: '',
  role: 'EDITOR' as McpAgentRole,
  scopes: [] as McpScope[],
  status: 1,
})

function openCreate() {
  editing.value = null
  Object.assign(form, { name: '', description: '', role: 'EDITOR', scopes: [], status: 1 })
  formOpen.value = true
}

function openEdit(row: McpAgent) {
  editing.value = row
  Object.assign(form, {
    name: row.name,
    description: row.description ?? '',
    role: row.role,
    scopes: [...row.scopes],
    status: row.status,
  })
  formOpen.value = true
}

function toggleScope(scope: McpScope, checked: boolean) {
  form.scopes = checked ? [...form.scopes, scope] : form.scopes.filter((item) => item !== scope)
}

function selectAllScopes() {
  form.scopes = [...allScopeValues]
}

function selectReadOnlyScopes() {
  form.scopes = allScopeValues.filter((scope) => scope.endsWith(':read'))
}

function clearScopes() {
  form.scopes = []
}

async function submitForm() {
  const name = form.name.trim()
  if (!name) {
    toast.warning('请填写 Agent 名称')
    return
  }
  saving.value = true
  try {
    if (editing.value) {
      await adminApi.updateMcpAgent(editing.value.id, {
        name,
        description: form.description.trim() || undefined,
        role: form.role,
        // 空数组在服务端等价于「按角色默认」，这里显式传空以便管理员一键收回自定义授权
        scopes: form.scopes,
        status: form.status,
      })
      toast.success('凭据已更新')
      formOpen.value = false
      await loadAgents()
      return
    }

    const created = await adminApi.createMcpAgent({
      name,
      description: form.description.trim() || undefined,
      role: form.role,
      scopes: form.scopes.length ? form.scopes : undefined,
      status: form.status,
    })
    formOpen.value = false
    await loadAgents()
    showIssuedKey(created, '凭据已签发')
  } finally {
    saving.value = false
  }
}

// ---------- 一次性密钥展示 ----------
const keyOpen = ref(false)
const issued = ref<McpAgentCreated | null>(null)
const keyCopied = ref(false)

function showIssuedKey(created: McpAgentCreated, message: string) {
  issued.value = created
  keyCopied.value = false
  keyOpen.value = true
  toast.success(message)
}

const clientSnippet = computed(() => {
  if (!issued.value) return ''
  return JSON.stringify(
    {
      mcpServers: {
        spectrace: {
          url: `${pageOrigin}/mcp`,
          headers: { 'X-MCP-Key': issued.value.apiKey },
        },
      },
    },
    null,
    2,
  )
})

async function copyKey() {
  if (!issued.value) return
  try {
    await navigator.clipboard.writeText(issued.value.apiKey)
    keyCopied.value = true
    toast.success('已复制到剪贴板')
  } catch {
    toast.error('复制失败，请手动选中文本复制')
  }
}

async function copySnippet() {
  try {
    await navigator.clipboard.writeText(clientSnippet.value)
    toast.success('配置片段已复制')
  } catch {
    toast.error('复制失败，请手动选中文本复制')
  }
}

// ---------- 轮换 / 吊销 ----------
const rotateTarget = ref<McpAgent | null>(null)
const rotating = ref(false)

async function confirmRotate() {
  if (!rotateTarget.value) return
  rotating.value = true
  try {
    const rotated = await adminApi.rotateMcpAgentKey(rotateTarget.value.id)
    rotateTarget.value = null
    await loadAgents()
    showIssuedKey(rotated, '密钥已轮换，旧 Key 立即失效')
  } finally {
    rotating.value = false
  }
}

const deleteTarget = ref<McpAgent | null>(null)
const deleting = ref(false)

async function confirmDelete() {
  if (!deleteTarget.value) return
  deleting.value = true
  try {
    await adminApi.deleteMcpAgent(deleteTarget.value.id)
    toast.success('凭据已吊销')
    deleteTarget.value = null
    await loadAgents()
  } finally {
    deleting.value = false
  }
}

// ---------- 调用日志 ----------
const callStatusOptions = [
  { label: '全部结果', value: '' },
  { label: '成功', value: 'SUCCESS' },
  { label: '失败', value: 'ERROR' },
  { label: '无权限', value: 'DENIED' },
]

const callStatusMeta: Record<McpCallStatus, { label: string; variant: 'success' | 'destructive' | 'warning' }> = {
  SUCCESS: { label: '成功', variant: 'success' },
  ERROR: { label: '失败', variant: 'destructive' },
  DENIED: { label: '无权限', variant: 'warning' },
}

const agentOptions = computed(() => [
  { label: '全部 Agent', value: '' },
  ...agents.value.map((agent) => ({ label: agent.name, value: String(agent.id) })),
])

const logsLoaded = ref(false)

const {
  list: logs,
  total: logTotal,
  loading: logsLoading,
  query: logQuery,
  load: loadLogs,
  search: searchLogs,
} = usePagedList<McpCallLog, { agentId: string; toolName: string; status: string }>({
  size: 20,
  immediate: false,
  filters: () => ({ agentId: '', toolName: '', status: '' }),
  fetcher: (params) =>
    adminApi.mcpCallLogs({
      current: params.current,
      size: params.size,
      agentId: params.agentId ? Number(params.agentId) : undefined,
      toolName: params.toolName.trim() || undefined,
      status: params.status || undefined,
    }),
})

async function ensureLogs() {
  if (logsLoaded.value) return
  try {
    await loadLogs()
    logsLoaded.value = true
  } catch {
    // 拦截器已提示；保留 logsLoaded=false，下次切 Tab 可重试
  }
}

watch(tab, (value) => {
  if (value === 'logs') ensureLogs()
})

const logDetail = ref<McpCallLog | null>(null)

function prettyJson(raw?: string | null) {
  if (!raw) return '—'
  try {
    return JSON.stringify(JSON.parse(raw), null, 2)
  } catch {
    // 超长被截断的历史日志不是合法 JSON，直接展示原文
    return raw
  }
}
</script>

<template>
  <div class="space-y-4">
    <PageHeader title="MCP 接入" description="为外部 Agent 签发凭据、控制授权范围，并查看每次工具调用">
      <template #actions>
        <Button v-if="isSuperAdmin" size="sm" @click="openCreate">
          <Plus />
          签发凭据
        </Button>
      </template>
    </PageHeader>

    <Tabs v-model="tab" :items="tabs" />

    <!-- 凭据列表 -->
    <template v-if="tab === 'agents'">
      <Card>
        <CardContent class="p-0">
          <div v-if="agentsLoading" class="space-y-2 p-4">
            <Skeleton v-for="i in 4" :key="i" class="h-11" />
          </div>

          <Table v-else-if="agents.length">
            <TableHeader>
              <TableRow class="hover:bg-transparent">
                <TableHead>Agent</TableHead>
                <TableHead class="w-36">Key 前缀</TableHead>
                <TableHead class="w-24">角色</TableHead>
                <TableHead class="w-24">授权范围</TableHead>
                <TableHead class="w-20">状态</TableHead>
                <TableHead class="w-40">调用情况</TableHead>
                <TableHead class="w-64 text-right">操作</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              <TableRow v-for="row in agents" :key="row.id">
                <TableCell>
                  <div class="font-medium">{{ row.name }}</div>
                  <div class="max-w-64 truncate text-xs text-muted-foreground">{{ row.description || '未填写描述' }}</div>
                </TableCell>
                <TableCell class="font-mono text-xs text-muted-foreground">{{ row.apiKeyPrefix }}…</TableCell>
                <TableCell>
                  <Badge :variant="roleMeta[row.role].variant">{{ roleMeta[row.role].label }}</Badge>
                </TableCell>
                <TableCell>
                  <span class="text-xs text-muted-foreground" :title="row.scopes.join(', ')">{{ row.scopes.length }} 项</span>
                </TableCell>
                <TableCell>
                  <Badge :variant="row.status === 1 ? 'success' : 'secondary'">
                    {{ row.status === 1 ? '启用' : '停用' }}
                  </Badge>
                </TableCell>
                <TableCell>
                  <div class="text-xs tabular-nums">{{ row.callCount }} 次</div>
                  <div class="text-xs text-muted-foreground">
                    {{ row.lastUsedAt ? formatTime(row.lastUsedAt) : '从未调用' }}
                  </div>
                </TableCell>
                <TableCell class="text-right">
                  <div v-if="isSuperAdmin" class="flex justify-end gap-1">
                    <Button variant="ghost" size="sm" @click="openEdit(row)">
                      <Pencil />
                      编辑
                    </Button>
                    <Button variant="ghost" size="sm" @click="rotateTarget = row">
                      <RotateCw />
                      轮换
                    </Button>
                    <Button
                      variant="ghost"
                      size="icon-sm"
                      class="text-destructive"
                      aria-label="吊销凭据"
                      @click="deleteTarget = row"
                    >
                      <Trash2 />
                    </Button>
                  </div>
                  <span v-else class="text-xs text-muted-foreground">仅超管可操作</span>
                </TableCell>
              </TableRow>
            </TableBody>
          </Table>

          <Empty v-else title="暂无 MCP 凭据" description="签发一份凭据后，外部 Agent 才能调用本系统数据。">
            <template #icon><Plug class="mb-1 h-8 w-8 text-muted-foreground/40" /></template>
          </Empty>
        </CardContent>
      </Card>

      <Alert
        variant="info"
        title="接入信息"
        :description="`服务地址：${pageOrigin}/mcp（Streamable HTTP），请求头 X-MCP-Key。开发环境请把端口换成后端 8080。`"
      />
    </template>

    <!-- 调用日志 -->
    <template v-else>
      <div class="flex flex-wrap items-center gap-2">
        <Select v-model="logQuery.agentId" :options="agentOptions" class="w-40" @update:model-value="searchLogs" />
        <Select v-model="logQuery.status" :options="callStatusOptions" class="w-36" @update:model-value="searchLogs" />
        <Input
          v-model="logQuery.toolName"
          placeholder="工具名，如 spectrace_device_list"
          class="w-full sm:w-64"
          @keyup.enter="searchLogs"
        />
        <Button variant="outline" size="sm" @click="searchLogs">
          <RefreshCw />
          查询
        </Button>
        <span class="ml-auto text-xs text-muted-foreground">共 {{ logTotal }} 条</span>
      </div>

      <Card>
        <CardContent class="p-0">
          <div v-if="logsLoading" class="space-y-2 p-4">
            <Skeleton v-for="i in 8" :key="i" class="h-9" />
          </div>

          <Table v-else-if="logs.length">
            <TableHeader>
              <TableRow class="hover:bg-transparent">
                <TableHead class="w-40">时间</TableHead>
                <TableHead class="w-32">Agent</TableHead>
                <TableHead>工具</TableHead>
                <TableHead class="w-20">结果</TableHead>
                <TableHead class="w-20">耗时</TableHead>
                <TableHead class="w-56">错误</TableHead>
                <TableHead class="w-32">客户端</TableHead>
                <TableHead class="w-20 text-right">详情</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              <TableRow v-for="row in logs" :key="row.id">
                <TableCell class="text-xs tabular-nums text-muted-foreground">{{ formatTime(row.createdAt) }}</TableCell>
                <TableCell class="font-medium">{{ row.agentName || '—' }}</TableCell>
                <TableCell class="font-mono text-xs">{{ row.toolName }}</TableCell>
                <TableCell>
                  <Badge :variant="callStatusMeta[row.status].variant">{{ callStatusMeta[row.status].label }}</Badge>
                </TableCell>
                <TableCell class="text-xs tabular-nums text-muted-foreground">{{ row.durationMs }} ms</TableCell>
                <TableCell class="max-w-56 text-xs text-muted-foreground">
                  <template v-if="row.errorCode">
                    <span class="font-mono">{{ row.errorCode }}</span>
                    <span class="ml-1 break-words">{{ row.errorMessage }}</span>
                  </template>
                  <span v-else>—</span>
                </TableCell>
                <TableCell class="max-w-32 truncate text-xs text-muted-foreground">{{ row.clientName || '—' }}</TableCell>
                <TableCell class="text-right">
                  <Button variant="ghost" size="sm" @click="logDetail = row">查看</Button>
                </TableCell>
              </TableRow>
            </TableBody>
          </Table>

          <Empty v-else title="暂无调用记录" description="Agent 调用任意工具后，这里会记录入参、结果与耗时。">
            <template #icon><ScrollText class="mb-1 h-8 w-8 text-muted-foreground/40" /></template>
          </Empty>
        </CardContent>
      </Card>

      <Pagination v-if="logTotal > logQuery.size" v-model:page="logQuery.current" :page-size="logQuery.size" :total="logTotal" />
    </template>

    <!-- 签发 / 编辑 -->
    <Dialog
      v-model:open="formOpen"
      :title="editing ? '编辑凭据' : '签发凭据'"
      :description="editing ? '修改授权范围后立即生效，无需重启服务' : '签发后明文 Key 只展示一次'"
      class="max-w-2xl"
    >
      <div class="space-y-4">
        <div class="grid gap-4 sm:grid-cols-2">
          <div class="space-y-1.5">
            <Label for="mcp-name">Agent 名称</Label>
            <Input id="mcp-name" v-model="form.name" placeholder="如 claude-desktop" autocomplete="off" />
          </div>
          <div class="space-y-1.5">
            <Label for="mcp-role">角色</Label>
            <Select id="mcp-role" v-model="form.role" :options="roleOptions" />
          </div>
        </div>

        <div class="space-y-1.5">
          <Label for="mcp-desc">描述</Label>
          <Input id="mcp-desc" v-model="form.description" placeholder="用途备注，便于日后辨认" />
        </div>

        <div class="flex items-center justify-between gap-3 rounded-md border p-3">
          <div>
            <div class="text-sm font-medium">启用</div>
            <div class="text-xs text-muted-foreground">停用后该 Key 立即无法调用任何工具</div>
          </div>
          <Switch :model-value="form.status === 1" @update:model-value="form.status = $event ? 1 : 0" />
        </div>

        <div class="space-y-2">
          <div class="flex flex-wrap items-center gap-2">
            <Label>授权范围</Label>
            <div class="ml-auto flex flex-wrap gap-1">
              <Button variant="ghost" size="sm" @click="selectReadOnlyScopes">全部只读</Button>
              <Button variant="ghost" size="sm" @click="selectAllScopes">全选</Button>
              <Button variant="ghost" size="sm" @click="clearScopes">清空</Button>
            </div>
          </div>

          <div class="grid gap-2 sm:grid-cols-2">
            <div v-for="group in scopeGroups" :key="group.label" class="rounded-md border p-3">
              <div class="mb-2 text-xs font-medium text-muted-foreground">{{ group.label }}</div>
              <div class="space-y-2">
                <label
                  v-for="scope in group.scopes"
                  :key="scope.value"
                  class="flex cursor-pointer items-start gap-2 text-sm"
                >
                  <Checkbox
                    class="mt-0.5"
                    :model-value="form.scopes.includes(scope.value)"
                    @update:model-value="toggleScope(scope.value, $event)"
                  />
                  <span>
                    <span class="block leading-tight">{{ scope.label }}</span>
                    <span class="block text-xs text-muted-foreground">{{ scope.desc }}</span>
                  </span>
                </label>
              </div>
            </div>
          </div>

          <p class="text-xs text-muted-foreground">
            未勾选任何权限时按角色默认授权：只读=全部查询权限；编辑者=除纠错处理外全部；管理员=全部。
          </p>
        </div>
      </div>

      <template #footer>
        <Button variant="outline" :disabled="saving" @click="formOpen = false">取消</Button>
        <Button :disabled="saving" @click="submitForm">
          <Loader2 v-if="saving" class="animate-spin" />
          {{ saving ? '提交中…' : editing ? '保存' : '签发' }}
        </Button>
      </template>
    </Dialog>

    <!-- 一次性密钥：禁用遮罩与 Esc 关闭，避免误关后再也看不到 -->
    <Dialog
      v-model:open="keyOpen"
      title="凭据已签发"
      description="明文 Key 只展示这一次"
      class="max-w-xl"
      hide-close
      prevent-close
    >
      <div v-if="issued" class="space-y-4">
        <Alert
          variant="warning"
          title="请立即保存"
          description="关闭后无法再次查看，只能轮换密钥。请复制到客户端的密钥管理或环境变量中，不要写进仓库。"
        />

        <div class="space-y-1.5">
          <div class="text-xs text-muted-foreground">
            {{ issued.agent.name }} · {{ roleMeta[issued.agent.role].label }} · {{ issued.agent.scopes.length }} 项权限
          </div>
          <div class="flex items-start gap-2">
            <code class="min-w-0 flex-1 break-all rounded-md bg-muted p-3 font-mono text-xs">{{ issued.apiKey }}</code>
            <Button variant="outline" size="icon-sm" :aria-label="keyCopied ? '已复制' : '复制密钥'" @click="copyKey">
              <Check v-if="keyCopied" />
              <Copy v-else />
            </Button>
          </div>
        </div>

        <div class="space-y-1.5">
          <div class="flex items-center justify-between gap-2">
            <div class="text-xs font-medium text-muted-foreground">Cursor / Claude Code 配置片段</div>
            <Button variant="ghost" size="sm" @click="copySnippet">
              <Copy />
              复制配置
            </Button>
          </div>
          <pre class="max-h-56 overflow-auto rounded-md bg-muted p-3 font-mono text-xs">{{ clientSnippet }}</pre>
          <p class="text-xs text-muted-foreground">
            地址取自当前页面来源；若 Agent 与后端不在同一主机，请把 host 换成可访问的域名或 IP，开发环境用后端端口 8080。
          </p>
        </div>

        <p class="text-xs text-muted-foreground">{{ issued.warning }}</p>
      </div>

      <template #footer>
        <Button @click="keyOpen = false">我已保存</Button>
      </template>
    </Dialog>

    <!-- 调用详情 -->
    <Dialog
      :open="!!logDetail"
      title="调用详情"
      :description="logDetail?.toolName"
      class="max-w-2xl"
      @update:open="logDetail = null"
    >
      <div v-if="logDetail" class="space-y-3">
        <div class="grid grid-cols-2 gap-2 text-sm">
          <div>
            <span class="text-muted-foreground">Agent：</span>{{ logDetail.agentName || '—' }}
          </div>
          <div>
            <span class="text-muted-foreground">结果：</span>
            <Badge :variant="callStatusMeta[logDetail.status].variant">{{ callStatusMeta[logDetail.status].label }}</Badge>
          </div>
          <div>
            <span class="text-muted-foreground">耗时：</span>{{ logDetail.durationMs }} ms
          </div>
          <div class="truncate">
            <span class="text-muted-foreground">客户端：</span>{{ logDetail.clientName || '—' }}
          </div>
          <div>
            <span class="text-muted-foreground">时间：</span>{{ formatTime(logDetail.createdAt) }}
          </div>
          <div class="truncate">
            <span class="text-muted-foreground">会话：</span>{{ logDetail.sessionId || '—' }}
          </div>
        </div>

        <div v-if="logDetail.errorCode" class="rounded-md border border-destructive/30 p-3 text-sm">
          <div class="font-mono text-xs text-destructive">{{ logDetail.errorCode }}</div>
          <div class="mt-1 break-words text-muted-foreground">{{ logDetail.errorMessage }}</div>
        </div>

        <div>
          <div class="mb-1 text-xs font-medium text-muted-foreground">入参（已脱敏，超长会截断）</div>
          <pre class="max-h-72 overflow-auto rounded-md bg-muted p-3 font-mono text-xs">{{ prettyJson(logDetail.argumentsJson) }}</pre>
        </div>
      </div>
    </Dialog>

    <!-- 轮换 -->
    <ConfirmDialog
      :open="!!rotateTarget"
      title="轮换密钥"
      :description="`确认轮换「${rotateTarget?.name ?? ''}」的密钥？旧 Key 会立即失效，正在使用它的 Agent 会调用失败。`"
      confirm-text="轮换"
      :loading="rotating"
      @update:open="rotateTarget = null"
      @confirm="confirmRotate"
    />

    <!-- 吊销 -->
    <ConfirmDialog
      :open="!!deleteTarget"
      title="吊销凭据"
      :description="`确认吊销「${deleteTarget?.name ?? ''}」？该 Agent 将立即无法调用任何工具，历史调用日志会保留。`"
      confirm-text="吊销"
      destructive
      :loading="deleting"
      @update:open="deleteTarget = null"
      @confirm="confirmDelete"
    />
  </div>
</template>

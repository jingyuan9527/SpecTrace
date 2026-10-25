<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { Database, Eraser, HardDriveDownload, Loader2, RefreshCw } from '@lucide/vue'
import { toast } from 'vue-sonner'
import { adminApi } from '@/api'
import { formatBytes } from '@/lib/image'
import { formatTime } from '@/lib/utils'
import PageHeader from '@/components/PageHeader.vue'
import { Alert, Badge, Button, Card, CardContent, Empty, Skeleton, Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui'

interface BackupFile {
  fileName: string
  size?: number | null
  createdAt?: string | null
}

interface BackupOverview {
  enabled: boolean
  dir: string
  keep: number
  nextRunAt?: string | null
  backups: BackupFile[]
}

interface RetentionOverview {
  enabled: boolean
  cron: string
  taskDays: number
  pendingDays: number
  operationLogDays: number
  viewDailyDays: number
  searchLogDays: number
  nextRunAt?: string | null
  counts: Record<string, number>
}

const RETENTION_TABLE_NAMES: Record<string, string> = {
  agent_task: '抓取任务',
  agent_task_log: '抓取过程日志',
  agent_pending_data: '待审核数据',
  operation_log: '操作日志',
}

const overview = ref<BackupOverview | null>(null)
const loading = ref(false)
const backingUp = ref(false)

const retention = ref<RetentionOverview | null>(null)
const retentionLoading = ref(false)
const cleaning = ref(false)

async function load() {
  loading.value = true
  try {
    overview.value = await adminApi.backups()
  } finally {
    loading.value = false
  }
}

async function loadRetention() {
  retentionLoading.value = true
  try {
    retention.value = await adminApi.retention()
  } finally {
    retentionLoading.value = false
  }
}

async function backupNow() {
  backingUp.value = true
  try {
    const file = await adminApi.triggerBackup()
    toast.success(`已生成备份 ${file.fileName}`)
    await load()
  } finally {
    backingUp.value = false
  }
}

async function cleanupNow() {
  cleaning.value = true
  try {
    const removed = await adminApi.triggerRetentionCleanup()
    const total = Object.values(removed as Record<string, number>).reduce((sum, n) => sum + n, 0)
    toast.success(total > 0 ? `已清理 ${total} 行过期数据` : '没有需要清理的数据')
    await loadRetention()
  } finally {
    cleaning.value = false
  }
}

onMounted(() => {
  load()
  loadRetention()
})
</script>

<template>
  <div class="space-y-4">
    <PageHeader title="系统运维" description="SQLite 备份查看与手动留档，仅超级管理员可操作" />

    <Alert
      variant="info"
      title="备份与恢复"
      description="备份使用 VACUUM INTO 生成完整数据库快照。恢复时停掉容器，把备份文件复制为 SPECTRACE_DB_PATH 指向的文件后重新启动即可。"
    />

    <Card>
      <CardContent class="space-y-4 p-4">
        <div v-if="loading && !overview" class="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
          <Skeleton v-for="i in 4" :key="i" class="h-14" />
        </div>
        <div v-else class="grid gap-3 text-sm sm:grid-cols-2 lg:grid-cols-4">
          <div>
            <p class="text-xs text-muted-foreground">备份状态</p>
            <Badge :variant="overview?.enabled ? 'success' : 'secondary'" class="mt-1">
              {{ overview?.enabled ? '已启用' : '已关闭' }}
            </Badge>
          </div>
          <div class="min-w-0">
            <p class="text-xs text-muted-foreground">备份目录</p>
            <p class="mt-1 break-all font-medium">{{ overview?.dir || '—' }}</p>
          </div>
          <div>
            <p class="text-xs text-muted-foreground">保留份数</p>
            <p class="mt-1 font-medium">{{ overview?.keep ?? '—' }} 份</p>
          </div>
          <div>
            <p class="text-xs text-muted-foreground">下次定时备份</p>
            <p class="mt-1 font-medium">{{ formatTime(overview?.nextRunAt) }}</p>
          </div>
        </div>

        <div class="flex flex-wrap gap-2">
          <Button :disabled="backingUp || !overview?.enabled" @click="backupNow">
            <Loader2 v-if="backingUp" class="animate-spin" />
            <HardDriveDownload v-else />
            {{ backingUp ? '备份中…' : '立即备份' }}
          </Button>
          <Button variant="outline" :disabled="loading" @click="load">
            <RefreshCw />
            刷新
          </Button>
        </div>
      </CardContent>
    </Card>

    <Card>
      <CardContent class="p-0">
        <div v-if="loading && !overview" class="space-y-2 p-4">
          <Skeleton v-for="i in 4" :key="i" class="h-11" />
        </div>

        <Table v-else-if="overview?.backups.length">
          <TableHeader>
            <TableRow class="hover:bg-transparent">
              <TableHead>文件名</TableHead>
              <TableHead class="w-28">大小</TableHead>
              <TableHead class="w-44">生成时间</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            <TableRow v-for="file in overview.backups" :key="file.fileName">
              <TableCell class="break-all font-medium">{{ file.fileName }}</TableCell>
              <TableCell class="text-muted-foreground">{{ file.size ? formatBytes(file.size) : '—' }}</TableCell>
              <TableCell class="text-muted-foreground">{{ formatTime(file.createdAt) }}</TableCell>
            </TableRow>
          </TableBody>
        </Table>

        <Empty v-else title="暂无备份文件" description="点击「立即备份」生成第一份快照，或等待每日定时任务。">
          <template #icon><Database class="mb-1 h-8 w-8 text-muted-foreground/40" /></template>
        </Empty>
      </CardContent>
    </Card>

    <Card>
      <CardContent class="space-y-4 p-4">
        <div class="flex flex-wrap items-start justify-between gap-2">
          <div>
            <h3 class="text-sm font-medium">数据保留策略</h3>
            <p class="mt-1 text-xs text-muted-foreground">
              每日定时清理终态数据，防止数据库无界膨胀：抓取任务及过程日志保留 {{ retention?.taskDays ?? '—' }} 天、
              已处理待审核保留 {{ retention?.pendingDays ?? '—' }} 天、操作日志保留 {{ retention?.operationLogDays ?? '—' }} 天、
              浏览量聚合保留 {{ retention?.viewDailyDays ?? '—' }} 天、
              搜索词聚合保留 {{ retention?.searchLogDays ?? '—' }} 天。
            </p>
          </div>
          <div class="flex gap-2">
            <Button variant="outline" size="sm" :disabled="cleaning" @click="cleanupNow">
              <Loader2 v-if="cleaning" class="animate-spin" />
              <Eraser v-else />
              立即清理
            </Button>
            <Button variant="outline" size="sm" :disabled="retentionLoading" @click="loadRetention">
              <RefreshCw />
            </Button>
          </div>
        </div>

        <div v-if="retentionLoading && !retention" class="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
          <Skeleton v-for="i in 4" :key="i" class="h-11" />
        </div>
        <Table v-else-if="retention">
          <TableHeader>
            <TableRow class="hover:bg-transparent">
              <TableHead>数据表</TableHead>
              <TableHead class="w-28">当前行数</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            <TableRow v-for="(count, table) in retention.counts" :key="table">
              <TableCell class="font-medium">{{ RETENTION_TABLE_NAMES[table] || table }}</TableCell>
              <TableCell class="text-muted-foreground">{{ count }}</TableCell>
            </TableRow>
          </TableBody>
        </Table>

        <p class="text-xs text-muted-foreground">
          定时计划：<code class="rounded bg-muted px-1 py-0.5">{{ retention?.cron || '—' }}</code>
          <template v-if="retention?.nextRunAt">，下次执行 {{ formatTime(retention.nextRunAt) }}</template>
        </p>
      </CardContent>
    </Card>
  </div>
</template>

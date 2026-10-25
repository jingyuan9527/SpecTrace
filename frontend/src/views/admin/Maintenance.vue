<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { Database, Eraser, HardDriveDownload, Loader2, RefreshCw } from '@lucide/vue'
import { toast } from 'vue-sonner'
import { adminApi } from '@/api'
import { formatBytes } from '@/lib/image'
import { formatTime } from '@/lib/utils'
import PageHeader from '@/components/PageHeader.vue'
import { Alert, Badge, Button, Card, CardContent, Empty, Skeleton, Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui'

interface CacheStat {
  name: string
  hitCount: number
  missCount: number
  hitRate?: number | null
  estimatedSize: number
  evictionCount: number
}

interface RuntimeMetrics {
  caches: CacheStat[]
  rateLimitRejected: Record<string, number>
  sqlitePath: string
  sqliteSizeBytes: number
  diskTotalBytes: number
  diskFreeBytes: number
  heapUsedBytes: number
  heapMaxBytes: number
  threads: number
  lastBackupDurationMs: number
  imagePending: number
}

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

// T105 技术运行时：缓存命中率与限流拒绝（进程内快照，重启清零）
const runtime = ref<RuntimeMetrics | null>(null)

async function loadRuntime() {
  try {
    runtime.value = await adminApi.runtimeMetrics()
  } catch {
    runtime.value = null
  }
}

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
  loadRuntime()
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

    <!-- T105 技术运行时：缓存命中与限流拒绝，进程内快照 -->
    <Card v-if="runtime">
      <div class="flex items-center justify-between border-b bg-muted/40 px-5 py-3">
        <h3 class="text-sm font-medium">运行时指标（重启清零）</h3>
        <Button variant="ghost" size="sm" @click="loadRuntime">
          <RefreshCw />
          刷新
        </Button>
      </div>
      <CardContent class="space-y-4">
        <!-- T107 运行时概要：SQLite / 磁盘 / JVM / 备份 / 图片队列 -->
        <div class="grid gap-3 sm:grid-cols-2 xl:grid-cols-4" v-if="runtime.sqlitePath !== undefined">
          <div class="rounded-lg border p-3">
            <p class="text-xs text-muted-foreground">SQLite 主库</p>
            <p class="mt-1 text-sm font-semibold">{{ formatBytes(runtime.sqliteSizeBytes) }}</p>
            <p class="mt-0.5 truncate text-[11px] text-muted-foreground" :title="runtime.sqlitePath">{{ runtime.sqlitePath }}</p>
          </div>
          <div class="rounded-lg border p-3">
            <p class="text-xs text-muted-foreground">磁盘剩余（数据盘）</p>
            <p class="mt-1 text-sm font-semibold">{{ formatBytes(runtime.diskFreeBytes) }}</p>
            <p class="mt-0.5 text-[11px] text-muted-foreground">
              共 {{ formatBytes(runtime.diskTotalBytes) }} · 告警阈值见 SPECTRACE_ALERT_DISK_FREE_MB
            </p>
          </div>
          <div class="rounded-lg border p-3">
            <p class="text-xs text-muted-foreground">JVM 堆 / 线程</p>
            <p class="mt-1 text-sm font-semibold">
              {{ formatBytes(runtime.heapUsedBytes) }} / {{ formatBytes(runtime.heapMaxBytes) }}
            </p>
            <p class="mt-0.5 text-[11px] text-muted-foreground">活跃线程 {{ runtime.threads }}</p>
          </div>
          <div class="rounded-lg border p-3">
            <p class="text-xs text-muted-foreground">备份耗时 / 图片队列</p>
            <p class="mt-1 text-sm font-semibold">
              {{ runtime.lastBackupDurationMs ? runtime.lastBackupDurationMs + ' ms' : '—' }}
            </p>
            <p class="mt-0.5 text-[11px] text-muted-foreground">待下载产品图 {{ runtime.imagePending }} 张</p>
          </div>
        </div>
        <div class="grid gap-4 lg:grid-cols-2">
        <div>
          <p class="mb-2 text-xs font-medium text-muted-foreground">公开读缓存命中率</p>
          <div class="space-y-1.5">
            <div v-for="item in runtime.caches" :key="item.name" class="flex items-center justify-between gap-2 text-sm">
              <span class="font-mono text-xs">{{ item.name }}</span>
              <span class="text-xs text-muted-foreground">
                {{ item.hitRate == null ? '暂无请求' : Math.round(item.hitRate * 100) + '%' }}
                （命中 {{ item.hitCount }} / 未中 {{ item.missCount }}）
              </span>
            </div>
          </div>
        </div>
        <div>
          <p class="mb-2 text-xs font-medium text-muted-foreground">限流拒绝（429）</p>
          <div class="space-y-1">
            <div v-for="(count, key) in runtime.rateLimitRejected" :key="key" class="flex items-center justify-between gap-2 text-sm">
              <span class="min-w-0 truncate font-mono text-xs" :title="key">{{ key }}</span>
              <span class="shrink-0 text-xs text-muted-foreground">{{ count }} 次</span>
            </div>
            <p v-if="!Object.keys(runtime.rateLimitRejected).length" class="text-sm text-muted-foreground">
              暂无限流触发。
            </p>
          </div>
        </div>
        </div>
      </CardContent>
    </Card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { Database, HardDriveDownload, Loader2, RefreshCw } from '@lucide/vue'
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

const overview = ref<BackupOverview | null>(null)
const loading = ref(false)
const backingUp = ref(false)

async function load() {
  loading.value = true
  try {
    overview.value = await adminApi.backups()
  } finally {
    loading.value = false
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

onMounted(load)
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
  </div>
</template>

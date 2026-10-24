<script setup lang="ts">
import { reactive, ref } from 'vue'
import { CheckCheck, Flag, RefreshCw, XCircle } from '@lucide/vue'
import { toast } from 'vue-sonner'
import { adminApi } from '@/api'
import { usePagedList } from '@/composables/usePagedList'
import { formatTime } from '@/lib/utils'
import {
  Badge,
  Button,
  Card,
  CardContent,
  Empty,
  Input,
  Pagination,
  Select,
  Skeleton,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui'
import PageHeader from '@/components/PageHeader.vue'

const handlingId = ref<number | null>(null)
const notes = reactive<Record<number, string>>({})

const statusOptions = [
  { label: '待处理', value: 'OPEN' },
  { label: '已处理', value: 'RESOLVED' },
  { label: '已忽略', value: 'IGNORED' },
  { label: '全部状态', value: '' },
]

const statusMeta: Record<string, { label: string; variant: 'info' | 'success' | 'secondary' }> = {
  OPEN: { label: '待处理', variant: 'info' },
  RESOLVED: { label: '已处理', variant: 'success' },
  IGNORED: { label: '已忽略', variant: 'secondary' },
}

const { list, total, loading, query, load, search } = usePagedList({
  size: 10,
  filters: () => ({ status: 'OPEN' as string }),
  fetcher: (params) => adminApi.feedback({ ...params, status: params.status || undefined }),
})

async function handle(row: any, status: 'RESOLVED' | 'IGNORED') {
  handlingId.value = row.id
  try {
    await adminApi.updateFeedbackStatus(row.id, status, notes[row.id]?.trim() || undefined)
    toast.success(status === 'RESOLVED' ? '已标记为已处理' : '已忽略该反馈')
    await load()
  } finally {
    handlingId.value = null
  }
}
</script>

<template>
  <div class="space-y-4">
    <PageHeader title="纠错反馈" description="访客对参数值提交的纠错，核实后可在机型管理中修改参数" />

    <div class="flex flex-wrap items-center gap-2">
      <Select v-model="query.status" :options="statusOptions" class="w-40" @update:model-value="search" />
      <Button variant="outline" size="sm" @click="load">
        <RefreshCw />
        刷新
      </Button>
      <span class="ml-auto text-xs text-muted-foreground">共 {{ total }} 条</span>
    </div>

    <Card>
      <CardContent class="p-0">
        <div v-if="loading" class="space-y-2 p-4">
          <Skeleton v-for="i in 6" :key="i" class="h-11" />
        </div>

        <Table v-else-if="list.length">
          <TableHeader>
            <TableRow class="hover:bg-transparent">
              <TableHead class="w-16">ID</TableHead>
              <TableHead class="min-w-40">机型</TableHead>
              <TableHead class="min-w-28">参数</TableHead>
              <TableHead class="min-w-40">当前值 → 建议值</TableHead>
              <TableHead class="min-w-56">说明 / 联系方式</TableHead>
              <TableHead class="w-32">提交时间</TableHead>
              <TableHead class="w-24">状态</TableHead>
              <TableHead class="w-52 text-right">处理</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            <TableRow v-for="row in list" :key="row.id">
              <TableCell class="text-muted-foreground">{{ row.id }}</TableCell>
              <TableCell class="font-medium">
                <router-link
                  :to="`/devices/${row.deviceId}`"
                  target="_blank"
                  class="hover:underline"
                >
                  {{ row.deviceName || `#${row.deviceId}` }}
                </router-link>
              </TableCell>
              <TableCell class="text-muted-foreground">{{ row.paramName || row.paramKey || '整机' }}</TableCell>
              <TableCell class="text-xs">
                <div class="flex flex-wrap items-center gap-1">
                  <span class="text-muted-foreground line-through">{{ row.currentValue || '空' }}</span>
                  <span class="text-muted-foreground">→</span>
                  <span class="font-medium">{{ row.suggestedValue || '—' }}</span>
                </div>
              </TableCell>
              <TableCell class="max-w-72 text-xs">
                <p class="break-words text-muted-foreground">{{ row.description || '—' }}</p>
                <p v-if="row.contact" class="mt-0.5 break-all text-muted-foreground/80">联系：{{ row.contact }}</p>
              </TableCell>
              <TableCell class="text-xs text-muted-foreground">{{ formatTime(row.createdAt) }}</TableCell>
              <TableCell>
                <Badge :variant="statusMeta[row.status]?.variant ?? 'secondary'">
                  {{ statusMeta[row.status]?.label ?? row.status }}
                </Badge>
                <p v-if="row.handlerNote" class="mt-1 text-xs text-muted-foreground">{{ row.handlerNote }}</p>
              </TableCell>
              <TableCell class="text-right">
                <div v-if="row.status === 'OPEN'" class="flex flex-col items-end gap-1.5">
                  <Input v-model="notes[row.id]" class="h-8 text-xs" placeholder="处理备注（选填）" />
                  <div class="flex gap-1">
                    <Button size="sm" variant="outline" :disabled="handlingId === row.id" @click="handle(row, 'RESOLVED')">
                      <CheckCheck />
                      已处理
                    </Button>
                    <Button size="sm" variant="ghost" :disabled="handlingId === row.id" @click="handle(row, 'IGNORED')">
                      <XCircle />
                      忽略
                    </Button>
                  </div>
                </div>
                <span v-else class="text-xs text-muted-foreground">已处理</span>
              </TableCell>
            </TableRow>
          </TableBody>
        </Table>

        <Empty v-else title="暂无纠错反馈" description="访客在机型详情页点击参数行的旗标即可提交。">
          <template #icon><Flag class="mb-1 h-8 w-8 text-muted-foreground/40" /></template>
        </Empty>
      </CardContent>
    </Card>

    <Pagination v-if="total > query.size" v-model:page="query.current" :page-size="query.size" :total="total" />
  </div>
</template>

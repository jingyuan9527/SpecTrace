<script setup lang="ts">
import { RefreshCw, ScrollText } from '@lucide/vue'
import { adminApi } from '@/api'
import { usePagedList } from '@/composables/usePagedList'
import {
  Badge,
  Button,
  Card,
  CardContent,
  Empty,
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

const types = [
  { label: '全部类型', value: '' },
  { label: '品牌', value: 'brand' },
  { label: '参数分组', value: 'param_group' },
  { label: '参数定义', value: 'param_def' },
  { label: '机型', value: 'device' },
  { label: '抓取任务', value: 'agent_task' },
  { label: '待审核数据', value: 'agent_pending_data' },
  { label: '管理员', value: 'admin_user' },
]

const { list, total, loading, query, load, search } = usePagedList({
  size: 20,
  filters: () => ({ targetType: '' }),
  fetcher: (params) => adminApi.logs({ ...params, targetType: params.targetType || undefined }),
})
</script>

<template>
  <div class="space-y-4">
    <PageHeader title="操作日志" description="记录管理端的关键写操作，便于追溯数据变更" />

    <div class="flex flex-wrap items-center gap-2">
      <Select v-model="query.targetType" :options="types" class="w-44" @update:model-value="search" />
      <Button variant="outline" size="sm" @click="load">
        <RefreshCw />
        刷新
      </Button>
      <span class="ml-auto text-xs text-muted-foreground">共 {{ total }} 条</span>
    </div>

    <Card>
      <CardContent class="p-0">
        <div v-if="loading" class="space-y-2 p-4">
          <Skeleton v-for="i in 8" :key="i" class="h-9" />
        </div>

        <Table v-else-if="list.length">
          <TableHeader>
            <TableRow class="hover:bg-transparent">
              <TableHead class="w-16">ID</TableHead>
              <TableHead class="w-28">操作人</TableHead>
              <TableHead class="w-36">操作</TableHead>
              <TableHead class="w-36">目标类型</TableHead>
              <TableHead class="w-20">目标 ID</TableHead>
              <TableHead>详情</TableHead>
              <TableHead class="w-44">时间</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            <TableRow v-for="row in list" :key="row.id">
              <TableCell class="text-muted-foreground">{{ row.id }}</TableCell>
              <TableCell class="font-medium">{{ row.adminName || '—' }}</TableCell>
              <TableCell><Badge variant="outline">{{ row.action }}</Badge></TableCell>
              <TableCell class="font-mono text-xs text-muted-foreground">{{ row.targetType }}</TableCell>
              <TableCell class="tabular-nums text-muted-foreground">{{ row.targetId ?? '—' }}</TableCell>
              <TableCell class="max-w-96 break-words text-xs text-muted-foreground">{{ row.detail || '—' }}</TableCell>
              <TableCell class="text-xs text-muted-foreground">{{ row.createdAt }}</TableCell>
            </TableRow>
          </TableBody>
        </Table>

        <Empty v-else title="暂无操作日志" description="管理端的新增、修改、删除操作会自动记录。">
          <template #icon><ScrollText class="mb-1 h-8 w-8 text-muted-foreground/40" /></template>
        </Empty>
      </CardContent>
    </Card>

    <Pagination v-if="total > query.size" v-model:page="query.current" :page-size="query.size" :total="total" />
  </div>
</template>

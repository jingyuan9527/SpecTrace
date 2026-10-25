<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { Activity, Loader2, RefreshCw } from '@lucide/vue'
import { adminApi } from '@/api'
import type { CollectMetrics } from '@/types/api'
import PageHeader from '@/components/PageHeader.vue'
import { useECharts } from '@/composables/useECharts'
import { cssVar } from '@/lib/utils'
import { useThemeStore } from '@/stores/theme'
import {
  Badge,
  Button,
  Card,
  CardContent,
  Skeleton,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui'

const theme = useThemeStore()
const { bind, render } = useECharts()

const collect = ref<CollectMetrics | null>(null)
const loading = ref(true)

async function load() {
  loading.value = true
  try {
    collect.value = await adminApi.collectMetrics(30)
  } finally {
    loading.value = false
  }
}

onMounted(load)

const successRateText = computed(() =>
  collect.value?.successRate == null ? '—' : `${Math.round(collect.value.successRate * 100)}%`,
)

function formatDuration(ms: number) {
  if (!ms) return '—'
  return ms >= 60_000 ? `${Math.round(ms / 60_000)} 分钟` : `${Math.round(ms / 1000)} 秒`
}

/** 每日任务量与失败数双折线；无数据天补零由后端保证 */
function renderDailyChart() {
  const daily = collect.value?.daily ?? []
  render('daily', {
    grid: { left: 40, right: 16, top: 36, bottom: 28 },
    tooltip: { trigger: 'axis' },
    legend: { data: ['任务数', '失败'], top: 0, textStyle: { color: cssVar('--foreground', '#333') } },
    xAxis: {
      type: 'category',
      data: daily.map((point) => point.date.slice(5)),
      axisLabel: { color: cssVar('--muted-foreground', '#888') },
    },
    yAxis: { type: 'value', minInterval: 1, axisLabel: { color: cssVar('--muted-foreground', '#888') } },
    series: [
      {
        name: '任务数',
        type: 'line',
        smooth: true,
        symbol: 'none',
        data: daily.map((point) => point.total),
        lineStyle: { width: 2, color: cssVar('--primary', '#0a0a0a') },
      },
      {
        name: '失败',
        type: 'line',
        smooth: true,
        symbol: 'none',
        data: daily.map((point) => point.failed),
        lineStyle: { width: 2, color: '#ef4444' },
      },
    ],
  })
}

// 数据就绪（图表容器随 v-else-if 挂载）与主题切换后重绘；token 颜色是实时计算的
watch([collect, () => theme.dark], () => {
  if (collect.value) {
    nextTick(renderDailyChart)
  }
})
</script>

<template>
  <div class="space-y-4">
    <PageHeader title="运行监控" description="采集链路、访客需求与数据质量的运营视图">
      <template #actions>
        <Button variant="outline" size="sm" :disabled="loading" @click="load">
          <Loader2 v-if="loading" class="animate-spin" />
          <RefreshCw v-else />
          刷新
        </Button>
      </template>
    </PageHeader>

    <div v-if="loading && !collect" class="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
      <Skeleton v-for="i in 4" :key="i" class="h-24 rounded-xl" />
    </div>

    <template v-else-if="collect">
      <div class="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <Card>
          <CardContent class="p-5">
            <p class="text-xs text-muted-foreground">近 {{ collect.windowDays }} 天任务</p>
            <p class="mt-1 text-2xl font-semibold">{{ collect.total }}</p>
            <p class="mt-1 text-xs text-muted-foreground">
              通过 {{ collect.approved }} · 丢弃 {{ collect.discarded }} · 失败 {{ collect.failed }}
            </p>
          </CardContent>
        </Card>
        <Card>
          <CardContent class="p-5">
            <p class="text-xs text-muted-foreground">成功率</p>
            <p class="mt-1 text-2xl font-semibold">{{ successRateText }}</p>
            <p class="mt-1 text-xs text-muted-foreground">通过 / (通过 + 失败)</p>
          </CardContent>
        </Card>
        <Card>
          <CardContent class="p-5">
            <p class="text-xs text-muted-foreground">耗时 P50 / P95</p>
            <p class="mt-1 text-2xl font-semibold">
              <span class="text-xl">{{ formatDuration(collect.p50Ms) }}</span>
              <span class="mx-1 text-muted-foreground">/</span>
              <span class="text-xl">{{ formatDuration(collect.p95Ms) }}</span>
            </p>
            <p class="mt-1 text-xs text-muted-foreground">
              贴近超时 {{ collect.nearTimeout }} 个
            </p>
          </CardContent>
        </Card>
        <Card>
          <CardContent class="p-5">
            <p class="text-xs text-muted-foreground">当前积压</p>
            <p class="mt-1 flex items-center gap-2 text-2xl font-semibold">
              {{ collect.pendingReview }}
              <Badge variant="secondary" class="text-xs font-normal">待审核</Badge>
            </p>
            <p class="mt-1 text-xs text-muted-foreground">
              排队 {{ collect.queued }} · 执行中 {{ collect.running }} · 图片待下载 {{ collect.imagePending }}
            </p>
          </CardContent>
        </Card>
      </div>

      <Card>
        <div class="flex items-center gap-2 border-b bg-muted/40 px-5 py-3">
          <Activity class="h-4 w-4" />
          <h2 class="text-sm font-semibold">每日任务量与失败趋势</h2>
        </div>
        <CardContent class="p-4">
          <div :ref="bind('daily')" class="h-64 w-full" />
        </CardContent>
      </Card>

      <!-- T101 模型表现：失败多的排前面，问题模型优先进入视线 -->
      <Card v-if="collect.modelStats?.length">
        <div class="flex items-center justify-between border-b bg-muted/40 px-5 py-3">
          <h2 class="text-sm font-semibold">模型表现排行</h2>
          <span class="text-xs text-muted-foreground">近 {{ collect.windowDays }} 天 · 重试换模型的决策依据</span>
        </div>
        <CardContent class="p-0">
          <Table>
            <TableHeader>
              <TableRow class="hover:bg-transparent">
                <TableHead class="min-w-44">模型</TableHead>
                <TableHead class="w-20 text-right">任务</TableHead>
                <TableHead class="w-24 text-right">成功率</TableHead>
                <TableHead class="w-20 text-right">失败</TableHead>
                <TableHead class="w-24 text-right">平均耗时</TableHead>
                <TableHead class="w-24 text-right">P95</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              <TableRow v-for="stat in collect.modelStats" :key="stat.aiModelId ?? stat.label">
                <TableCell class="font-medium">{{ stat.label }}</TableCell>
                <TableCell class="text-right text-muted-foreground">{{ stat.total }}</TableCell>
                <TableCell class="text-right">
                  <Badge
                    v-if="stat.successRate != null"
                    :variant="stat.successRate >= 0.8 ? 'success' : stat.successRate >= 0.5 ? 'warning' : 'destructive'"
                  >
                    {{ Math.round(stat.successRate * 100) }}%
                  </Badge>
                  <span v-else class="text-muted-foreground">—</span>
                </TableCell>
                <TableCell class="text-right" :class="stat.failed > 0 ? 'text-destructive' : 'text-muted-foreground'">
                  {{ stat.failed }}
                </TableCell>
                <TableCell class="text-right text-muted-foreground">{{ formatDuration(stat.avgMs) }}</TableCell>
                <TableCell class="text-right text-muted-foreground">{{ formatDuration(stat.p95Ms) }}</TableCell>
              </TableRow>
            </TableBody>
          </Table>
        </CardContent>
      </Card>
    </template>
  </div>
</template>

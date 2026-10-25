<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { Activity, Loader2, RefreshCw, SearchX, TrendingUp } from '@lucide/vue'
import { adminApi } from '@/api'
import type { CollectMetrics, SearchMetrics, TrafficMetrics } from '@/types/api'
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
const search = ref<SearchMetrics | null>(null)
const traffic = ref<TrafficMetrics | null>(null)
const loading = ref(true)

async function load() {
  loading.value = true
  try {
    ;[collect.value, search.value, traffic.value] = await Promise.all([
      adminApi.collectMetrics(30),
      adminApi.searchMetrics(30),
      adminApi.trafficMetrics(30),
    ])
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

/** PV 日趋势单折线 */
function renderPvChart() {
  const daily = traffic.value?.daily ?? []
  render('pv', {
    grid: { left: 40, right: 16, top: 20, bottom: 28 },
    tooltip: { trigger: 'axis' },
    xAxis: {
      type: 'category',
      data: daily.map((point) => point.date.slice(5)),
      axisLabel: { color: cssVar('--muted-foreground', '#888') },
    },
    yAxis: { type: 'value', minInterval: 1, axisLabel: { color: cssVar('--muted-foreground', '#888') } },
    series: [
      {
        name: 'PV',
        type: 'line',
        smooth: true,
        symbol: 'none',
        areaStyle: { opacity: 0.08 },
        data: daily.map((point) => point.pv),
        lineStyle: { width: 2, color: cssVar('--primary', '#0a0a0a') },
      },
    ],
  })
}

const wowText = computed(() => {
  const wow = traffic.value?.weekOverWeek
  if (wow == null) return '—'
  return `${wow >= 0 ? '+' : ''}${Math.round(wow * 100)}%`
})

// 数据就绪（图表容器随 v-else-if 挂载）与主题切换后重绘；token 颜色是实时计算的
watch([collect, traffic, () => theme.dark], () => {
  if (collect.value) nextTick(renderDailyChart)
  if (traffic.value) nextTick(renderPvChart)
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

      <!-- T103 访客流量：PV 趋势 / 周环比 / 对比使用 / TOP 机型 -->
      <Card v-if="traffic">
        <div class="flex flex-wrap items-center justify-between gap-2 border-b bg-muted/40 px-5 py-3">
          <h2 class="flex items-center gap-2 text-sm font-semibold">
            <TrendingUp class="h-4 w-4" />
            访客流量
          </h2>
          <span class="text-xs text-muted-foreground">
            本周 PV {{ traffic.weekPv }} · 周环比 {{ wowText }} · 对比发起 {{ traffic.compareInitiated }} 次
          </span>
        </div>
        <CardContent class="grid gap-4 p-4 lg:grid-cols-[1fr_240px]">
          <div :ref="bind('pv')" class="h-48 w-full" />
          <div>
            <p class="mb-2 text-xs font-medium text-muted-foreground">近 {{ traffic.windowDays }} 天浏览量 TOP</p>
            <div class="space-y-1">
              <div v-for="item in traffic.topDevices" :key="item.deviceId" class="flex items-center justify-between gap-2 text-sm">
                <span class="min-w-0 truncate" :title="item.deviceName">{{ item.deviceName }}</span>
                <span class="shrink-0 text-xs text-muted-foreground">{{ item.pv }}</span>
              </div>
              <p v-if="!traffic.topDevices.length" class="text-sm text-muted-foreground">暂无浏览记录。</p>
            </div>
          </div>
        </CardContent>
      </Card>

      <!-- T102 搜索需求信号：空结果词就是补录方向 -->
      <Card v-if="search">
        <div class="flex flex-wrap items-center justify-between gap-2 border-b bg-muted/40 px-5 py-3">
          <h2 class="flex items-center gap-2 text-sm font-semibold">
            <SearchX class="h-4 w-4" />
            搜索需求信号
          </h2>
          <span class="text-xs text-muted-foreground">
            近 {{ search.windowDays }} 天 {{ search.totalSearches }} 次搜索 · 空结果率
            {{ search.zeroRate == null ? '—' : Math.round(search.zeroRate * 100) + '%' }}
          </span>
        </div>
        <CardContent class="grid gap-4 p-4 md:grid-cols-2">
          <div>
            <p class="mb-2 text-xs font-medium text-muted-foreground">热门搜索</p>
            <div class="space-y-1">
              <div
                v-for="item in search.topKeywords"
                :key="item.keyword"
                class="flex items-center justify-between gap-2 text-sm"
              >
                <span class="min-w-0 truncate" :title="item.keyword">{{ item.keyword }}</span>
                <span class="shrink-0 text-xs text-muted-foreground">
                  {{ item.searchCount }} 次
                  <span v-if="item.zeroCount > 0" class="text-amber-600 dark:text-amber-400">
                    · 空结果 {{ item.zeroCount }}
                  </span>
                </span>
              </div>
              <p v-if="!search.topKeywords.length" class="text-sm text-muted-foreground">暂无搜索记录。</p>
            </div>
          </div>
          <div>
            <p class="mb-2 text-xs font-medium text-muted-foreground">空结果词排行（库里的缺口）</p>
            <div class="space-y-1">
              <div
                v-for="item in search.zeroTop"
                :key="item.keyword"
                class="flex items-center justify-between gap-2 text-sm"
              >
                <span class="min-w-0 truncate" :title="item.keyword">{{ item.keyword }}</span>
                <span class="shrink-0 text-xs text-amber-600 dark:text-amber-400">{{ item.zeroCount }} 次未命中</span>
              </div>
              <p v-if="!search.zeroTop.length" class="text-sm text-muted-foreground">没有空结果记录。</p>
            </div>
          </div>
        </CardContent>
      </Card>
    </template>
  </div>
</template>

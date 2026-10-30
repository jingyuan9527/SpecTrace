<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { Activity, Bot, ClipboardCheck, CloudDownload, Loader2, MessageSquareWarning, Percent, RefreshCw, TriangleAlert } from '@lucide/vue'
import { adminApi } from '@/api'
import type { Brand, DataHealth, DataHealthMissingDevice, DeviceSeries } from '@/types/api'
import PageHeader from '@/components/PageHeader.vue'
import ScrapeModelDialog from '@/components/admin/ScrapeModelDialog.vue'
import { useScrapeDispatch } from '@/composables/useScrapeDispatch'
import {
  Badge,
  Button,
  Card,
  CardContent,
  Empty,
  Select,
  Skeleton,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui'

const router = useRouter()

const health = ref<DataHealth | null>(null)
const loading = ref(true)

const brands = ref<Brand[]>([])
const brandId = ref<string>('')
const seriesList = ref<DeviceSeries[]>([])
const seriesId = ref<string>('')

const brandOptions = computed(() => [
  { label: '全部品牌', value: '' },
  ...brands.value.map((b) => ({ label: b.name, value: String(b.id) })),
])

const seriesOptions = computed(() => [
  { label: '全部系列', value: '' },
  { label: '未归类', value: '0' },
  ...seriesList.value.map((s) => ({ label: s.name, value: String(s.id) })),
])

async function load() {
  loading.value = true
  try {
    health.value = await adminApi.dataHealth({
      brandId: brandId.value || undefined,
      // seriesId=0 约定为「未归类」
      seriesId: seriesId.value === '' ? undefined : Number(seriesId.value),
    })
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  brands.value = await adminApi.brands()
  await load()
})

watch(brandId, async () => {
  seriesId.value = ''
  seriesList.value = brandId.value ? await adminApi.series(Number(brandId.value)) : []
  await load()
})

watch(seriesId, load)

// ---- 缺参机型补抓：行级单台与批量（当前排行可见项）共用一个弹窗 ----
const {
  open: scrapeOpen,
  targets: scrapeTargets,
  loading: scrapeLoading,
  submitting: scrapeSubmitting,
  modelId: scrapeModelId,
  modelOptions: scrapeModelOptions,
  title: scrapeTitle,
  description: scrapeDescription,
  meta: scrapeMeta,
  emptyText: scrapeEmptyText,
  submitText: scrapeSubmitText,
  start: startScrape,
  confirm: confirmScrape,
} = useScrapeDispatch({ onSubmitted: load })

function openScrape(rows: DataHealthMissingDevice[]) {
  if (!rows.length) return
  startScrape({
    targets: rows.map((row) => ({
      id: row.id,
      name: row.name,
      brandName: row.brandName ?? '',
      status: row.status,
    })),
    title: '发起抓取',
    description: '对选中的缺参机型发起抓取，结果进入待审核',
    meta: 'status',
    emptyText: '当前筛选下没有缺参机型',
    submitLabel: () => '提交抓取',
  })
}
</script>

<template>
  <!-- 宽屏下一屏看完：KPI 固定高度，两个长列表各自内部滚动，页面本身不再整体滚动 -->
  <div class="flex flex-col gap-3 xl:h-full xl:min-h-0">
    <PageHeader title="数据健康度" description="全库带参率、参数覆盖与缺参机型排行——按真实浏览量决定先补哪台">
      <template #actions>
        <Button variant="outline" size="sm" :disabled="loading" @click="load">
          <Loader2 v-if="loading" class="animate-spin" />
          <RefreshCw v-else />
          刷新
        </Button>
      </template>
    </PageHeader>

    <div v-if="loading && !health" class="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
      <Skeleton v-for="i in 4" :key="i" class="h-[6.25rem] rounded-xl" />
    </div>

    <template v-else-if="health">
      <!-- 手机竖屏两列铺开，避免 4 张卡纵向占掉一整屏 -->
      <div class="grid grid-cols-2 gap-2 sm:gap-3 xl:shrink-0 xl:grid-cols-4">
        <Card>
          <CardContent class="flex items-start gap-2.5 p-3 sm:gap-3 sm:p-4">
            <span class="hidden h-8 w-8 shrink-0 items-center justify-center rounded-md bg-primary/10 text-primary sm:flex">
              <Percent class="h-4 w-4" />
            </span>
            <div class="min-w-0">
              <p class="truncate text-xs text-muted-foreground">全库带参率</p>
              <p class="mt-0.5 text-lg font-semibold leading-none tabular-nums sm:text-xl">
                {{ Math.round(health.coverageRate * 100) }}%
              </p>
              <p class="mt-1 line-clamp-2 text-[10px] text-muted-foreground sm:text-[11px]">
                带参 {{ health.devicesWithParams }} / 共 {{ health.totalDevices }} 台（含草稿）
              </p>
            </div>
          </CardContent>
        </Card>
        <Card>
          <CardContent class="flex items-start gap-2.5 p-3 sm:gap-3 sm:p-4">
            <span
              class="hidden h-8 w-8 shrink-0 items-center justify-center rounded-md bg-amber-500/10 text-amber-600 dark:text-amber-400 sm:flex"
            >
              <TriangleAlert class="h-4 w-4" />
            </span>
            <div class="min-w-0">
              <p class="truncate text-xs text-muted-foreground">缺参机型（排行内）</p>
              <p class="mt-0.5 text-lg font-semibold leading-none tabular-nums sm:text-xl">
                {{ health.missingDevices.length }}
              </p>
              <p class="mt-1 line-clamp-2 text-[10px] text-muted-foreground sm:text-[11px]">
                启用参数 {{ health.enabledParamDefs }} 项
              </p>
            </div>
          </CardContent>
        </Card>
        <Card>
          <CardContent class="flex items-start gap-2.5 p-3 sm:gap-3 sm:p-4">
            <span
              class="hidden h-8 w-8 shrink-0 items-center justify-center rounded-md bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 sm:flex"
            >
              <ClipboardCheck class="h-4 w-4" />
            </span>
            <div class="min-w-0">
              <p class="truncate text-xs text-muted-foreground">近 30 天审核通过</p>
              <p class="mt-0.5 text-lg font-semibold leading-none tabular-nums sm:text-xl">
                {{ health.approvedLast30d }}
              </p>
              <p class="mt-1 line-clamp-2 text-[10px] text-muted-foreground sm:text-[11px]">采集入库的数据量</p>
            </div>
          </CardContent>
        </Card>
        <Card
          class="cursor-pointer transition-colors hover:border-amber-500/50 hover:bg-accent/40"
          @click="router.push('/admin/feedback')"
        >
          <CardContent class="flex items-start gap-2.5 p-3 sm:gap-3 sm:p-4">
            <span
              class="hidden h-8 w-8 shrink-0 items-center justify-center rounded-md sm:flex"
              :class="
                health.openFeedback > 0
                  ? 'bg-destructive/10 text-destructive'
                  : 'bg-muted text-muted-foreground'
              "
            >
              <MessageSquareWarning class="h-4 w-4" />
            </span>
            <div class="min-w-0">
              <p class="truncate text-xs text-muted-foreground">待处理纠错反馈</p>
              <p
                class="mt-0.5 text-lg font-semibold leading-none tabular-nums sm:text-xl"
                :class="health.openFeedback > 0 ? 'text-amber-600 dark:text-amber-400' : ''"
              >
                {{ health.openFeedback }}
              </p>
              <p class="mt-1 line-clamp-2 text-[10px] text-muted-foreground sm:text-[11px]">点击前往处理</p>
            </div>
          </CardContent>
        </Card>
      </div>

      <Card class="flex min-h-0 flex-col xl:shrink-0">
        <div class="flex flex-wrap items-center justify-between gap-x-3 gap-y-1 border-b bg-muted/40 px-4 py-2">
          <h2 class="flex flex-wrap items-baseline gap-x-2 text-sm font-semibold">
            <span class="inline-flex items-center gap-1.5">
              <Activity class="h-4 w-4" />
              参数覆盖排行
            </span>
            <span class="text-xs font-normal text-muted-foreground">{{ health.paramCoverage.length }} 项</span>
          </h2>
          <span class="text-xs text-muted-foreground">覆盖最少的排最前，参数名可到「参数元数据」维护</span>
        </div>
        <CardContent
          class="grid gap-x-6 gap-y-2 p-4 sm:max-h-52 sm:grid-cols-2 sm:overflow-y-auto 2xl:max-h-56 2xl:grid-cols-3"
        >
          <div v-for="item in health.paramCoverage" :key="item.paramDefId" class="space-y-1">
            <div class="flex items-baseline justify-between gap-2 text-sm">
              <span class="min-w-0 truncate">
                <span class="text-xs text-muted-foreground">{{ item.groupName }} / </span>
                <span class="font-medium">{{ item.paramName }}</span>
              </span>
              <span class="shrink-0 text-xs tabular-nums text-muted-foreground">
                {{ item.deviceCount }} / {{ health.totalDevices }}
              </span>
            </div>
            <div class="h-1.5 overflow-hidden rounded-full bg-muted">
              <div
                class="h-full rounded-full"
                :class="item.deviceCount === 0 ? 'bg-destructive/60' : 'bg-primary/70'"
                :style="{ width: `${health.totalDevices ? Math.max(2, (item.deviceCount / health.totalDevices) * 100) : 0}%` }"
              />
            </div>
          </div>
          <p v-if="!health.paramCoverage.length" class="text-sm text-muted-foreground">
            暂无启用参数，先到「参数元数据」启用需要采集的字段。
          </p>
        </CardContent>
      </Card>

      <!-- 视口过矮时保底高度，宁可整页滚一点，也不把排行表压成一条缝 -->
      <Card class="flex min-h-0 flex-col xl:min-h-[18rem] xl:flex-1">
        <div class="flex shrink-0 flex-wrap items-center justify-between gap-2 border-b bg-muted/40 px-4 py-2">
          <h2 class="flex flex-wrap items-baseline gap-x-2 text-sm font-semibold">
            <span>缺参机型排行</span>
            <span class="text-xs font-normal text-muted-foreground">
              {{ health.missingDevices.length }} 台 · 按近 30 天浏览量排
            </span>
          </h2>
          <div class="flex flex-wrap items-center gap-2">
            <Select v-model="brandId" :options="brandOptions" class="w-32" />
            <Select v-model="seriesId" :options="seriesOptions" class="w-36" :disabled="!brandId" />
            <Button
              v-if="health.missingDevices.length"
              variant="outline"
              size="sm"
              @click="openScrape(health.missingDevices)"
            >
              <CloudDownload />
              补抓可见机型
            </Button>
          </div>
        </div>
        <CardContent class="min-h-0 flex-1 overflow-auto p-0">
          <!-- 表头吸收在面板滚动容器上：滚动时列名常驻，且仍可横向滚动 -->
          <Table v-if="health.missingDevices.length" wrapper-class="overflow-visible">
            <TableHeader sticky>
              <TableRow class="hover:bg-transparent">
                <TableHead class="min-w-32">机型</TableHead>
                <TableHead class="w-24">品牌</TableHead>
                <TableHead class="w-28">系列</TableHead>
                <TableHead class="w-28">发布时间</TableHead>
                <TableHead class="w-20 text-right" title="近 30 天浏览量">30天PV</TableHead>
                <TableHead class="w-24">带参</TableHead>
                <TableHead class="w-16 text-right">状态</TableHead>
                <TableHead class="w-16 text-right">操作</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              <TableRow v-for="row in health.missingDevices" :key="row.id">
                <TableCell class="whitespace-nowrap font-medium">{{ row.name }}</TableCell>
                <TableCell class="whitespace-nowrap text-muted-foreground">{{ row.brandName || '—' }}</TableCell>
                <TableCell class="whitespace-nowrap text-muted-foreground">{{ row.seriesName || '未归类' }}</TableCell>
                <TableCell class="whitespace-nowrap text-muted-foreground">{{ row.releaseDate || '—' }}</TableCell>
                <TableCell class="text-right tabular-nums text-muted-foreground">{{ row.pv30d ?? '—' }}</TableCell>
                <TableCell>
                  <Badge :variant="row.haveParams === 0 ? 'destructive' : 'warning'">
                    {{ row.haveParams }}/{{ row.totalParams }}
                  </Badge>
                </TableCell>
                <TableCell class="text-right">
                  <Badge :variant="row.status === 1 ? 'success' : 'secondary'">{{ row.status === 1 ? '上架' : '草稿' }}</Badge>
                </TableCell>
                <TableCell class="text-right">
                  <Button
                    variant="ghost"
                    size="icon-sm"
                    aria-label="补抓该机型"
                    title="对该机型发起抓取"
                    @click="openScrape([row])"
                  >
                    <Bot />
                  </Button>
                </TableCell>
              </TableRow>
            </TableBody>
          </Table>
          <Empty
            v-else
            title="没有缺参机型"
            description="当前筛选下所有机型都已有参数值，保持节奏持续收录即可。"
          />
        </CardContent>
      </Card>
    </template>

    <ScrapeModelDialog
      :open="scrapeOpen"
      :title="scrapeTitle"
      :description="scrapeDescription"
      :targets="scrapeTargets"
      :loading="scrapeLoading"
      :submitting="scrapeSubmitting"
      :model-id="scrapeModelId"
      :model-options="scrapeModelOptions"
      :meta="scrapeMeta"
      :empty-text="scrapeEmptyText"
      :submit-text="scrapeSubmitText"
      @update:open="scrapeOpen = $event"
      @update:model-id="scrapeModelId = $event"
      @confirm="confirmScrape"
    />
  </div>
</template>

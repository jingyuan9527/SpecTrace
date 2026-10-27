<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { Activity, Bot, CloudDownload, Loader2, RefreshCw } from '@lucide/vue'
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
  <div class="space-y-4">
    <PageHeader title="数据健康度" description="全库带参率、参数覆盖与缺参机型排行——按真实浏览量决定先补哪台">
      <template #actions>
        <Button variant="outline" size="sm" :disabled="loading" @click="load">
          <Loader2 v-if="loading" class="animate-spin" />
          <RefreshCw v-else />
          刷新
        </Button>
      </template>
    </PageHeader>

    <div v-if="loading && !health" class="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
      <Skeleton v-for="i in 4" :key="i" class="h-24 rounded-xl" />
    </div>

    <template v-else-if="health">
      <div class="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <Card>
          <CardContent class="p-5">
            <p class="text-xs text-muted-foreground">全库带参率</p>
            <p class="mt-1 text-2xl font-semibold">{{ Math.round(health.coverageRate * 100) }}%</p>
            <p class="mt-1 text-xs text-muted-foreground">
              带参 {{ health.devicesWithParams }} / 共 {{ health.totalDevices }} 台（含草稿）
            </p>
          </CardContent>
        </Card>
        <Card>
          <CardContent class="p-5">
            <p class="text-xs text-muted-foreground">缺参机型（排行内）</p>
            <p class="mt-1 text-2xl font-semibold">{{ health.missingDevices.length }}</p>
            <p class="mt-1 text-xs text-muted-foreground">启用参数 {{ health.enabledParamDefs }} 项</p>
          </CardContent>
        </Card>
        <Card>
          <CardContent class="p-5">
            <p class="text-xs text-muted-foreground">近 30 天审核通过</p>
            <p class="mt-1 text-2xl font-semibold">{{ health.approvedLast30d }}</p>
            <p class="mt-1 text-xs text-muted-foreground">采集入库的数据量</p>
          </CardContent>
        </Card>
        <Card class="cursor-pointer transition-colors hover:bg-accent/40" @click="router.push('/admin/feedback')">
          <CardContent class="p-5">
            <p class="text-xs text-muted-foreground">待处理纠错反馈</p>
            <p class="mt-1 text-2xl font-semibold" :class="health.openFeedback > 0 ? 'text-amber-600 dark:text-amber-400' : ''">
              {{ health.openFeedback }}
            </p>
            <p class="mt-1 text-xs text-muted-foreground">点击前往处理</p>
          </CardContent>
        </Card>
      </div>

      <Card>
        <div class="flex items-center justify-between border-b bg-muted/40 px-5 py-3">
          <h2 class="flex items-center gap-2 text-sm font-semibold">
            <Activity class="h-4 w-4" />
            参数覆盖排行
          </h2>
          <span class="text-xs text-muted-foreground">覆盖最少的参数排最前，点参数名到「参数元数据」维护</span>
        </div>
        <CardContent class="p-4">
          <div class="grid gap-x-6 gap-y-3 md:grid-cols-2">
            <div v-for="item in health.paramCoverage" :key="item.paramDefId" class="space-y-1">
              <div class="flex items-baseline justify-between gap-2 text-sm">
                <span class="min-w-0 truncate">
                  <span class="text-xs text-muted-foreground">{{ item.groupName }} / </span>
                  <span class="font-medium">{{ item.paramName }}</span>
                </span>
                <span class="shrink-0 text-xs text-muted-foreground">{{ item.deviceCount }} / {{ health.totalDevices }}</span>
              </div>
              <div class="h-1.5 overflow-hidden rounded-full bg-muted">
                <div
                  class="h-full rounded-full"
                  :class="item.deviceCount === 0 ? 'bg-destructive/60' : 'bg-primary/70'"
                  :style="{ width: `${health.totalDevices ? Math.max(2, (item.deviceCount / health.totalDevices) * 100) : 0}%` }"
                />
              </div>
            </div>
          </div>
        </CardContent>
      </Card>

      <Card>
        <div class="flex flex-wrap items-center justify-between gap-2 border-b bg-muted/40 px-5 py-3">
          <h2 class="text-sm font-semibold">缺参机型排行</h2>
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
        <CardContent class="p-0">
          <Table>
            <TableHeader>
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
                <TableCell class="font-medium">{{ row.name }}</TableCell>
                <TableCell class="text-muted-foreground">{{ row.brandName || '—' }}</TableCell>
                <TableCell class="text-muted-foreground">{{ row.seriesName || '未归类' }}</TableCell>
                <TableCell class="text-muted-foreground">{{ row.releaseDate || '—' }}</TableCell>
                <TableCell class="text-right text-muted-foreground">{{ row.pv30d ?? '—' }}</TableCell>
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
            v-if="!health.missingDevices.length"
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

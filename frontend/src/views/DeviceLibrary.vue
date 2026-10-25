<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { motion } from 'motion-v'
import { ArrowRight, GitCompareArrows, SlidersHorizontal } from '@lucide/vue'
import { toast } from 'vue-sonner'
import { publicApi } from '@/api'
import type { Brand, Device, SeriesOverviewResult } from '@/types/api'
import { useCompareStore } from '@/stores/compare'
import { Badge, Button, Card, CardContent, Empty, Pagination, Select, Skeleton, Tabs, type TabItem } from '@/components/ui'
import DeviceSearch from '@/components/DeviceSearch.vue'
import DeviceAvatar from '@/components/DeviceAvatar.vue'
import TrendSparkline from '@/components/TrendSparkline.vue'

const route = useRoute()
const router = useRouter()
const compare = useCompareStore()

/** 路由 query 里的品牌/年份是字符串，统一转成正整数，非法值视为未选择。 */
function parseId(value: unknown) {
  const raw = Array.isArray(value) ? value[0] : value
  const id = Number(raw)
  return Number.isFinite(id) && id > 0 ? id : undefined
}

const brands = ref<Brand[]>([])
const devices = ref<Device[]>([])
const years = ref<number[]>([])
const total = ref(0)
const loading = ref(true)

const query = reactive({
  current: 1,
  size: 12,
  brandId: parseId(route.query.brandId),
  year: parseId(route.query.year),
  keyword: (route.query.keyword as string) || '',
})

// ---------- 视图切换：机型列表 / 系列总览（URL 同步 view） ----------
const viewTabs: TabItem[] = [
  { value: 'list', label: '机型列表' },
  { value: 'series', label: '系列总览' },
]
const view = ref<'list' | 'series'>((route.query.view as string) === 'series' ? 'series' : 'list')
const overview = ref<SeriesOverviewResult | null>(null)
const overviewLoading = ref(false)

async function loadOverview() {
  overviewLoading.value = true
  try {
    overview.value = await publicApi.seriesOverview(query.brandId)
  } finally {
    overviewLoading.value = false
  }
}

watch(view, (value) => {
  router.replace({ query: { ...route.query, view: value === 'series' ? 'series' : undefined } })
  if (value === 'series' && !overview.value) loadOverview()
})

const brandOptions = computed(() => brands.value.map((b) => ({ label: b.name, value: b.id })))
const yearOptions = computed(() => years.value.map((y) => ({ label: `${y} 年`, value: y })))

async function load() {
  loading.value = true
  try {
    const res = await publicApi.devices({
      current: query.current,
      size: query.size,
      brandId: query.brandId,
      year: query.year,
      keyword: query.keyword || undefined,
    })
    devices.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}

async function loadYears() {
  years.value = await publicApi.years(query.brandId)
}

function applyFilter() {
  query.current = 1
  load()
}

onMounted(async () => {
  brands.value = await publicApi.brands()
  // 深链 ?view=series 时 view 初始化即为目标值，不经过 watch，需要显式加载一次
  if (view.value === 'series') loadOverview()
  await Promise.all([load(), loadYears()])
})

watch(
  () => query.brandId,
  () => {
    query.year = undefined
    loadYears()
    applyFilter()
    // 总览与列表共用品牌筛选，切换品牌时两份数据都要跟上
    if (view.value === 'series') loadOverview()
  },
)

watch(
  () => query.year,
  () => applyFilter(),
)

watch(
  () => query.current,
  () => load(),
)

function goDetail(device: { id: number }) {
  router.push({ name: 'device-detail', params: { id: device.id } })
}

function brandNameOf(brandId?: number) {
  return brands.value.find((brand) => brand.id === brandId)?.name ?? null
}

function toggleCompare(device: Device) {
  if (compare.has(device.id)) {
    compare.remove(device.id)
    toast.info(`已移出对比池：${device.name}`)
    return
  }
  if (!compare.add({ id: device.id, name: device.name, thumbnail: device.thumbnail })) {
    toast.warning('对比池最多 4 台机型')
    return
  }
  toast.success(`已加入对比池：${device.name}`)
}
</script>

<template>
  <div class="space-y-5">
    <div class="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
      <div>
        <h1 class="text-xl font-semibold tracking-tight sm:text-2xl">机型库</h1>
        <p class="text-sm text-muted-foreground">
          {{ view === 'series' ? '按产品线回看收录规模与参数趋势' : '按品牌、年份与关键词筛选，勾选后进入对比' }}
        </p>
      </div>
      <div class="flex flex-wrap items-center gap-2">
        <Tabs v-model="view" :items="viewTabs" />
        <Button variant="outline" size="sm" @click="router.push({ name: 'compare' })">
          <GitCompareArrows />
          前往对比
          <Badge v-if="compare.count" class="h-5 min-w-5 justify-center px-1">{{ compare.count }}</Badge>
        </Button>
      </div>
    </div>

    <Card>
      <CardContent class="flex flex-col gap-3 p-4 lg:flex-row lg:items-center">
        <div class="flex items-center gap-2 text-sm font-medium text-muted-foreground">
          <SlidersHorizontal class="h-4 w-4" />
          筛选
        </div>
        <div class="grid flex-1 grid-cols-1 gap-2 sm:grid-cols-2 lg:grid-cols-[160px_140px_1fr_auto]">
          <Select v-model="query.brandId" :options="brandOptions" placeholder="全部品牌" clearable />
          <Select v-model="query.year" :options="yearOptions" placeholder="全部年份" clearable />
          <DeviceSearch
            v-model="query.keyword"
            placeholder="机型名称或拼音首字母"
            @submit="applyFilter"
            @select="goDetail"
          />
          <Button @click="applyFilter">搜索</Button>
        </div>
      </CardContent>
    </Card>

    <template v-if="view === 'list'">
      <div v-if="loading" class="grid grid-cols-2 gap-3 md:grid-cols-4">
        <Skeleton v-for="i in 8" :key="i" class="h-64 rounded-xl" />
      </div>

      <div v-else-if="devices.length" class="grid grid-cols-2 gap-3 md:grid-cols-4">
        <motion.div
          v-for="(device, index) in devices"
          :key="device.id"
          :initial="{ opacity: 0, y: 10 }"
          :animate="{ opacity: 1, y: 0 }"
          :transition="{ duration: 0.25, delay: Math.min(index, 8) * 0.03 }"
        >
          <Card class="group h-full overflow-hidden transition-shadow hover:shadow-md">
            <button
              type="button"
              class="h-40 w-full overflow-hidden bg-muted/50"
              @click="router.push({ name: 'device-detail', params: { id: device.id } })"
            >
              <DeviceAvatar
                :name="device.name"
                :thumbnail="device.thumbnail"
                :brand="brandNameOf(device.brandId)"
                img-class="max-h-32 object-contain transition-transform duration-300 group-hover:scale-105"
              />
            </button>
            <CardContent class="space-y-1 p-4">
              <router-link
                :to="{ name: 'device-detail', params: { id: device.id } }"
                class="line-clamp-1 text-sm font-medium hover:text-primary"
              >
                {{ device.name }}
              </router-link>
              <p class="text-xs text-muted-foreground">{{ device.releaseDate || '发布时间待补充' }}</p>
              <Button
                class="mt-2 w-full"
                size="sm"
                :variant="compare.has(device.id) ? 'secondary' : 'outline'"
                @click="toggleCompare(device)"
              >
                {{ compare.has(device.id) ? '已在对比池' : '加入对比' }}
              </Button>
            </CardContent>
          </Card>
        </motion.div>
      </div>

      <Empty v-else title="没有匹配的机型" description="试试更换品牌、年份，或清空关键词后重新搜索。" />

      <Pagination v-if="total > query.size" v-model:page="query.current" :page-size="query.size" :total="total" />
    </template>

    <!-- 系列总览：产品线卡片 + 迷你趋势，点击直达世代回顾 -->
    <template v-else>
      <div v-if="overviewLoading" class="grid grid-cols-1 gap-3 sm:grid-cols-2">
        <Skeleton v-for="i in 4" :key="i" class="h-56 rounded-xl" />
      </div>

      <template v-else-if="overview">
        <div v-if="overview.items.length" class="grid grid-cols-1 gap-3 sm:grid-cols-2">
          <motion.div
            v-for="(item, index) in overview.items"
            :key="item.id"
            :initial="{ opacity: 0, y: 10 }"
            :animate="{ opacity: 1, y: 0 }"
            :transition="{ duration: 0.25, delay: Math.min(index, 6) * 0.04 }"
          >
            <Card class="group h-full overflow-hidden transition-shadow hover:shadow-md">
              <router-link :to="{ name: 'trends', query: { mode: 'cross', seriesId: item.id } }" class="block h-full">
                <CardContent class="flex h-full flex-col gap-3 p-5">
                  <div class="flex items-center gap-3">
                    <div class="h-14 w-14 shrink-0 overflow-hidden rounded-lg bg-muted/50">
                      <DeviceAvatar
                        :name="item.latest?.name ?? item.name"
                        :thumbnail="item.latest?.thumbnail"
                        :brand="item.brandName"
                        size="sm"
                        img-class="max-h-11 object-contain"
                      />
                    </div>
                    <div class="min-w-0">
                      <p class="truncate text-sm font-semibold group-hover:text-primary">{{ item.name }}</p>
                      <p class="text-xs text-muted-foreground">
                        {{ item.brandName || '未知品牌' }} · 共 {{ item.deviceCount }} 代<template v-if="item.startYear">
                          · {{ item.startYear }}<template v-if="item.endYear && item.endYear !== item.startYear">-{{ item.endYear }}</template></template>
                      </p>
                    </div>
                  </div>
                  <p class="text-xs text-muted-foreground">
                    最新：{{ item.latest?.name ?? '待收录' }}
                  </p>

                  <div class="mt-auto space-y-2.5">
                    <div>
                      <p class="flex items-center justify-between text-xs text-muted-foreground">
                        <span>屏幕尺寸趋势</span><span>英寸</span>
                      </p>
                      <TrendSparkline
                        v-if="item.screenTrend.length"
                        :points="item.screenTrend"
                        unit="英寸"
                        class="text-primary"
                      />
                      <p v-else class="py-1 text-xs text-muted-foreground/70">暂无趋势数据</p>
                    </div>
                    <div>
                      <p class="flex items-center justify-between text-xs text-muted-foreground">
                        <span>起售价趋势</span><span>元</span>
                      </p>
                      <TrendSparkline
                        v-if="item.priceTrend.length"
                        :points="item.priceTrend"
                        unit="元"
                        class="text-muted-foreground"
                      />
                      <p v-else class="py-1 text-xs text-muted-foreground/70">暂无趋势数据</p>
                    </div>
                    <p class="flex items-center gap-1 text-xs font-medium text-primary">
                      查看系列趋势
                      <ArrowRight class="h-3.5 w-3.5 transition-transform group-hover:translate-x-0.5" />
                    </p>
                  </div>
                </CardContent>
              </router-link>
            </Card>
          </motion.div>
        </div>

        <Empty
          v-else
          title="还没有可展示的系列"
          description="在后台「系列管理」为机型归类后，这里会按产品线汇总收录规模与趋势。"
        />

        <!-- 未归类机型入口：点击切回列表视图按当前品牌查看 -->
        <button
          v-if="overview.unassignedCount > 0"
          type="button"
          class="flex w-full items-center justify-between rounded-xl border border-dashed px-5 py-4 text-left transition-colors hover:bg-muted/50"
          @click="view = 'list'"
        >
          <span class="text-sm text-muted-foreground">
            另有 <span class="font-semibold text-foreground">{{ overview.unassignedCount }}</span> 台机型未归入系列
          </span>
          <span class="flex items-center gap-1 text-xs text-primary">
            查看机型列表
            <ArrowRight class="h-3.5 w-3.5" />
          </span>
        </button>
      </template>
    </template>
  </div>
</template>

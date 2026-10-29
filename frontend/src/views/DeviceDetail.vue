<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { motion } from 'motion-v'
import { ArrowLeft, CalendarDays, Check, Clock3, Flag, GitCompareArrows, LineChart, Link2, Loader2, RefreshCw, TrendingUp } from '@lucide/vue'
import type { AxiosError } from 'axios'
import { toast } from 'vue-sonner'
import { publicApi } from '@/api'
import type { DeviceVariant, EvolutionDevice, FeedbackStatus, ParamDef, ParamGroup, ParamMeta, SeriesBrief } from '@/types/api'
import { absoluteUrl, setPageMeta, setJsonLd } from '@/lib/meta'
import { formatTime } from '@/lib/utils'
import { useCompareStore } from '@/stores/compare'
import {
  Badge,
  Button,
  Card,
  CardContent,
  Dialog,
  Empty,
  Input,
  Label,
  Separator,
  Skeleton,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
  Textarea,
} from '@/components/ui'
import DeviceAvatar from '@/components/DeviceAvatar.vue'

const route = useRoute()
const router = useRouter()
const compare = useCompareStore()

const device = ref<any>(null)
const brand = ref<any>(null)
const series = ref<SeriesBrief | null>(null)
const seriesDevices = ref<EvolutionDevice[]>([])
const params = ref<Record<string, string>>({})
const paramMeta = ref<Record<string, ParamMeta>>({})
// paramKey -> OPEN 反馈数；提交反馈后本地自增，不为一个计数重拉整页
const paramFeedback = ref<Record<string, number>>({})
const lastUpdated = ref<string | null>(null)
const variants = ref<DeviceVariant[]>([])
const groups = ref<ParamGroup[]>([])
const defs = ref<ParamDef[]>([])
const loading = ref(true)
const notFound = ref(false)
// 与 notFound 分开：后端 404 才是「已下架」，网络中断 / 5xx 属于可重试的加载失败
const loadError = ref(false)

const grouped = computed(() =>
  groups.value
    .map((group) => ({
      id: group.id,
      name: group.name,
      items: defs.value
        .filter((d) => d.groupId === group.id)
        .map((d) => ({
          ...d,
          value: params.value[d.paramKey] ?? '',
          meta: paramMeta.value[d.paramKey],
          pendingCount: paramFeedback.value[d.paramKey] ?? 0,
        }))
        .filter((d) => d.value !== ''),
    }))
    .filter((g) => g.items.length > 0),
)

/**
 * 参数元数据是准静态数据，失败只影响参数区的分组与顺序，
 * 不能连带把整页判成「机型不存在」——那会让一次元数据抖动伪装成数据缺失。
 */
async function loadParamMeta() {
  try {
    ;[groups.value, defs.value] = await Promise.all([publicApi.paramGroups(), publicApi.paramDefs()])
  } catch (error) {
    console.error('[DeviceDetail] 参数元数据加载失败，参数区降级为不分组', error)
  }
}

async function loadDetail() {
  loading.value = true
  notFound.value = false
  loadError.value = false
  try {
    const id = Number(route.params.id)
    const detail = await publicApi.deviceDetail(id)
    device.value = detail.device
    brand.value = detail.brand
    series.value = detail.series || null
    seriesDevices.value = detail.seriesDevices || []
    params.value = detail.params || {}
    paramMeta.value = detail.paramMeta || {}
    paramFeedback.value = detail.paramFeedback || {}
    lastUpdated.value = detail.lastUpdated || null
    variants.value = detail.variants || []
    // SEO：详情页动态标题与分享卡片信息
    const brandName = detail.brand?.name || ''
    setPageMeta({
      title: `${detail.device.name} 参数${brandName ? ` - ${brandName}` : ''} - SpecTrace`,
      description:
        detail.device.description ||
        `${brandName} ${detail.device.name} 的完整参数、存储版本与发布价，支持横向对比与历史演变。`,
      image: absoluteUrl(detail.device.thumbnail),
      url: location.href,
    })
    setJsonLd(buildProductJsonLd(detail))
    await loadParamMeta()
  } catch (error) {
    if ((error as AxiosError)?.response?.status === 404) {
      notFound.value = true
      return
    }
    console.error('[DeviceDetail] 机型详情加载失败', { deviceId: route.params.id }, error)
    loadError.value = true
  } finally {
    loading.value = false
  }
}

onMounted(loadDetail)

// 同路由换机型（系列条互跳）会复用组件实例，onMounted 不再触发：不重载就会出现
// 「URL 已是 iPhone 16、页面还是 iPhone 17」的错配
watch(() => route.params.id, (next, prev) => {
  if (next && next !== prev) loadDetail()
})

/** T89 结构化数据：Product schema，参数摘要取前 12 项控制体积 */
function buildProductJsonLd(detail: any) {
  const brandName = detail.brand?.name || ''
  const specs = Object.entries(detail.params || {}).slice(0, 12).map(([key, value]) => ({
    '@type': 'PropertyValue',
    name: detail.paramMeta?.[key]?.paramName || key,
    value: String(value),
  }))
  const data: Record<string, unknown> = {
    '@context': 'https://schema.org',
    '@type': 'Product',
    name: brandName ? `${brandName} ${detail.device.name}` : detail.device.name,
    url: location.href.split('?')[0],
  }
  if (brandName) data.brand = { '@type': 'Brand', name: brandName }
  if (detail.device.releaseDate) data.releaseDate = detail.device.releaseDate
  if (detail.device.thumbnail) data.image = absoluteUrl(detail.device.thumbnail)
  if (minPrice.value !== null) {
    data.offers = { '@type': 'Offer', price: minPrice.value, priceCurrency: 'CNY', availability: 'https://schema.org/InStock' }
  }
  if (specs.length) data.additionalProperty = specs
  return data
}

function formatPrice(price?: number | null, currency?: string | null) {
  if (price === null || price === undefined) return '—'
  const code = currency ?? 'CNY'
  const symbol = code === 'CNY' ? '¥' : `${code} `
  return `${symbol}${Number(price).toLocaleString('zh-CN')}`
}

const minPrice = computed(() => {
  const prices = variants.value
    .map((v) => v.price)
    .filter((p): p is number => p !== null && p !== undefined)
  return prices.length ? Math.min(...prices) : null
})

// 防御式入口：系列下已收录 ≥2 代才有回顾价值，单机系列只展示「收录中」文案
const evolutionAvailable = computed(() => seriesDevices.value.length >= 2)

function openEvolution() {
  if (!series.value) return
  router.push({ name: 'trends', query: { mode: 'cross', seriesId: String(series.value.id) } })
}

/** 参数走势入口：仅数值型参数（值以数字开头）做跨代比较才有意义，纯文本参数不展示 */
function isNumericValue(value: string) {
  return /^\d/.test(value.trim())
}

/** T87 参数陈旧度：超过 180 天未更新的参数提示可能过期（OTA、新配色、调价都会让旧值失真） */
const STALE_DAYS = 180

function isStale(updatedAt?: string | null) {
  if (!updatedAt) return false
  const time = Date.parse(updatedAt)
  return Number.isFinite(time) && Date.now() - time > STALE_DAYS * 24 * 3600 * 1000
}

function openParamHistory(paramDefId?: number | null) {
  // 走本机所属系列的跨代趋势并聚焦到该参数；未归类机型没有世代上下文，入口不展示
  if (!device.value || !paramDefId || !series.value) return
  router.push({
    name: 'trends',
    query: { mode: 'cross', seriesId: String(series.value.id), paramDefId: String(paramDefId) },
  })
}

function addCompare() {
  if (!device.value) return
  if (compare.has(device.value.id)) {
    toast.info('已在对比池')
    return
  }
  if (!compare.add({ id: device.value.id, name: device.value.name, thumbnail: device.value.thumbnail })) {
    toast.warning('对比池最多 4 台机型')
    return
  }
  toast.success('已加入对比池')
}

// ---------- 参数纠错 ----------
// form 提交表单 / success 编号回执 / query 凭编号查进度，三态共用一个弹窗
type FeedbackView = 'form' | 'success' | 'query'
const feedbackOpen = ref(false)
const feedbackView = ref<FeedbackView>('form')
const feedbackSaving = ref(false)
const feedbackTarget = ref<{ paramKey: string; paramName: string; value: string } | null>(null)
const feedbackForm = reactive({ suggestedValue: '', description: '', contact: '' })
const feedbackSubmittedCode = ref('')
const queryIdInput = ref('')
const queryResult = ref<FeedbackStatus | null>(null)
const queryLoading = ref(false)
const queryError = ref('')

function openFeedback(item: { paramKey: string; paramName: string; value: string }) {
  feedbackTarget.value = item
  Object.assign(feedbackForm, { suggestedValue: '', description: '', contact: '' })
  feedbackView.value = 'form'
  feedbackSubmittedCode.value = ''
  queryIdInput.value = ''
  queryResult.value = null
  queryError.value = ''
  feedbackOpen.value = true
}

async function submitFeedback() {
  if (!device.value || !feedbackTarget.value) return
  if (!feedbackForm.suggestedValue.trim() && !feedbackForm.description.trim()) {
    toast.warning('请填写正确值或问题说明')
    return
  }
  feedbackSaving.value = true
  try {
    const code = await publicApi.submitFeedback({
      deviceId: device.value.id,
      paramKey: feedbackTarget.value.paramKey,
      currentValue: feedbackTarget.value.value,
      suggestedValue: feedbackForm.suggestedValue.trim() || undefined,
      description: feedbackForm.description.trim() || undefined,
      contact: feedbackForm.contact.trim() || undefined,
    })
    feedbackSubmittedCode.value = code
    feedbackView.value = 'success'
    // 后端已失效详情缓存，这里只把当前参数计数补上：访客立刻看到「自己不是一个人」，
    // 整页重拉会把参数区的滚动位置和展开态一起丢掉，代价大于收益
    const key = feedbackTarget.value.paramKey
    paramFeedback.value = { ...paramFeedback.value, [key]: (paramFeedback.value[key] ?? 0) + 1 }
  } finally {
    feedbackSaving.value = false
  }
}

async function queryFeedback() {
  // 历史回执是纯数字编号，新回执是 8 位随机码，两者都交给后端按「先码后旧编号」解析
  const code = queryIdInput.value.trim().replace(/^#/, '')
  if (!code) {
    queryError.value = '请输入提交成功时返回的编号'
    queryResult.value = null
    return
  }
  queryError.value = ''
  queryLoading.value = true
  try {
    queryResult.value = await publicApi.feedbackStatus(code)
  } catch {
    // 接口错误（编号不存在等）由全局拦截器统一 toast
    queryResult.value = null
  } finally {
    queryLoading.value = false
  }
}

function feedbackStatusLabel(status: string) {
  return status === 'OPEN' ? '待处理' : status === 'RESOLVED' ? '已处理' : status === 'IGNORED' ? '已忽略' : status
}

/** 编号回执页直达进度查询：回填刚拿到的编号 */
function queryFromReceipt() {
  if (!feedbackSubmittedCode.value) return
  queryIdInput.value = feedbackSubmittedCode.value
  feedbackView.value = 'query'
}
</script>

<template>
  <div class="space-y-5">
    <Button variant="ghost" size="sm" class="-ml-2 text-muted-foreground" @click="router.back()">
      <ArrowLeft />
      返回
    </Button>

    <div v-if="loading" class="space-y-5">
      <Skeleton class="h-64 rounded-xl" />
      <Skeleton class="h-72 rounded-xl" />
    </div>

    <Empty v-else-if="loadError" title="机型加载失败" description="网络或服务暂时不可用，稍后重试通常即可恢复。">
      <Button variant="outline" size="sm" class="mt-2" @click="loadDetail">
        <RefreshCw />
        重试
      </Button>
    </Empty>

    <Empty v-else-if="notFound || !device" title="未找到该机型" description="机型可能已下架，返回机型库重新选择。">
      <Button variant="outline" size="sm" class="mt-2" @click="router.push({ name: 'devices' })">返回机型库</Button>
    </Empty>

    <template v-else>
      <motion.div :initial="{ opacity: 0, y: 8 }" :animate="{ opacity: 1, y: 0 }" :transition="{ duration: 0.3 }">
        <Card class="overflow-hidden">
          <div class="grid gap-6 p-6 md:grid-cols-[240px_1fr]">
            <div class="h-56 overflow-hidden rounded-xl bg-muted/50">
              <DeviceAvatar :name="device.name" :thumbnail="device.thumbnail" :brand="brand?.name" size="lg" img-class="max-h-44 object-contain" />
            </div>

            <div class="flex flex-col">
              <p class="text-sm font-medium text-primary">{{ brand?.name || '未知品牌' }}</p>
              <h1 class="mt-1 text-2xl font-semibold tracking-tight">{{ device.name }}</h1>
              <p v-if="minPrice !== null" class="mt-1 text-sm text-muted-foreground">
                <span class="text-lg font-semibold text-primary">{{ formatPrice(minPrice) }}</span> 起
              </p>
              <p class="mt-2 max-w-2xl text-sm leading-6 text-muted-foreground">
                {{ device.description || '暂无机型简介。' }}
              </p>

              <div class="mt-4 flex flex-wrap items-center gap-x-4 gap-y-1.5 text-sm text-muted-foreground">
                <span class="flex items-center gap-1.5">
                  <CalendarDays class="h-4 w-4" />
                  发布时间：{{ device.releaseDate || '待补充' }}
                </span>
                <span v-if="lastUpdated" class="flex items-center gap-1.5">
                  <Clock3 class="h-4 w-4" />
                  最后更新：{{ formatTime(lastUpdated) }}
                  <span
                    v-if="isStale(lastUpdated)"
                    class="rounded bg-amber-500/10 px-1.5 py-0.5 text-xs text-amber-600 dark:text-amber-400"
                  >
                    超过 {{ STALE_DAYS }} 天未更新 · 可能过期
                  </span>
                </span>
              </div>

              <Separator class="my-5" />

              <div class="flex flex-wrap gap-2">
                <Button @click="addCompare">
                  <GitCompareArrows />
                  {{ compare.has(device.id) ? '已在对比池' : '加入对比' }}
                </Button>
                <Button variant="outline" @click="router.push({ name: 'compare' })">查看对比池</Button>
              </div>
            </div>
          </div>
        </Card>
      </motion.div>

      <Card v-if="series" class="overflow-hidden">
        <div class="flex flex-wrap items-center justify-between gap-2 border-b bg-muted/40 px-5 py-3">
          <h2 class="text-sm font-semibold">
            所属系列 · <span class="text-primary">{{ series.name }}</span>
          </h2>
          <Button v-if="evolutionAvailable" variant="outline" size="sm" @click="openEvolution">
            <TrendingUp />
            系列趋势
          </Button>
          <span v-else class="text-xs text-muted-foreground">已收录 {{ seriesDevices.length }} 代 · 数据持续收录中</span>
        </div>
        <CardContent class="p-0">
          <!-- 移动端横向滚动浏览同系列机型，当前机型高亮锁定 -->
          <div class="flex gap-3 overflow-x-auto p-4">
            <button
              v-for="item in seriesDevices"
              :key="item.id"
              type="button"
              class="w-28 shrink-0 space-y-1.5 rounded-lg border p-2.5 text-center transition-colors"
              :class="item.id === device.id ? 'border-primary bg-primary/5' : 'hover:bg-muted/60'"
              :aria-current="item.id === device.id ? 'true' : undefined"
              :disabled="item.id === device.id"
              @click="router.push({ name: 'device-detail', params: { id: item.id } })"
            >
              <div class="h-14 overflow-hidden rounded-lg bg-muted/50">
                <DeviceAvatar
                  :name="item.name"
                  :thumbnail="item.thumbnail"
                  :brand="brand?.name"
                  size="sm"
                  img-class="max-h-12 object-contain"
                />
              </div>
              <div class="truncate text-xs font-medium">{{ item.name }}</div>
              <div class="text-xs text-muted-foreground">{{ item.releaseDate?.slice(0, 4) || '年份待补充' }}</div>
            </button>
          </div>
        </CardContent>
      </Card>

      <Card v-if="variants.length" class="overflow-hidden">
        <div class="flex flex-wrap items-center justify-between gap-2 border-b bg-muted/40 px-5 py-3">
          <h2 class="text-sm font-semibold">存储版本与发布价</h2>
          <span class="text-xs text-muted-foreground">
            {{ variants.length }} 个版本
            <template v-if="minPrice !== null"> · 起售价 {{ formatPrice(minPrice) }}</template>
          </span>
        </div>
        <CardContent class="p-0">
          <Table>
            <TableHeader>
              <TableRow class="hover:bg-transparent">
                <TableHead>存储容量</TableHead>
                <TableHead>运行内存</TableHead>
                <TableHead class="text-right">官方发布价</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              <TableRow v-for="(variant, index) in variants" :key="variant.id ?? `${variant.storage}-${variant.ram}-${index}`">
                <TableCell class="font-medium">{{ variant.storage }}</TableCell>
                <TableCell class="text-muted-foreground">{{ variant.ram || '—' }}</TableCell>
                <TableCell class="text-right font-medium">{{ formatPrice(variant.price, variant.currency) }}</TableCell>
              </TableRow>
            </TableBody>
          </Table>
        </CardContent>
      </Card>

      <div v-if="grouped.length" class="space-y-4">
        <Card v-for="group in grouped" :key="group.id" class="overflow-hidden">
          <div class="flex items-center justify-between border-b bg-muted/40 px-5 py-3">
            <h2 class="text-sm font-semibold">{{ group.name }}</h2>
            <span class="text-xs text-muted-foreground">{{ group.items.length }} 项</span>
          </div>
          <CardContent class="p-0">
            <Table>
              <TableHeader>
                <TableRow class="hover:bg-transparent">
                  <TableHead class="w-40">参数</TableHead>
                  <TableHead>值</TableHead>
                  <TableHead class="w-12 text-right">
                    <span class="sr-only">纠错</span>
                  </TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                <TableRow v-for="item in group.items" :key="item.paramKey">
                  <TableCell class="align-top text-muted-foreground">{{ item.paramName }}</TableCell>
                  <TableCell class="font-medium">
                    {{ item.value }}
                    <span v-if="item.unit" class="ml-1 text-xs font-normal text-muted-foreground">{{ item.unit }}</span>
                    <div
                      v-if="
                        item.meta?.sourceUrl ||
                        item.meta?.updatedAt ||
                        item.meta?.confidence != null ||
                        item.pendingCount > 0
                      "
                      class="mt-1 flex flex-wrap items-center gap-x-3 gap-y-1 text-xs font-normal text-muted-foreground"
                    >
                      <!-- 手机宽度下值列只有 ~190px，完整时间会被拆成两三行，小屏只留日期 -->
                      <span v-if="item.meta?.updatedAt" class="whitespace-nowrap">
                        更新于
                        <span class="hidden sm:inline">{{ formatTime(item.meta.updatedAt) }}</span>
                        <span class="sm:hidden">{{ formatTime(item.meta.updatedAt).slice(0, 10) }}</span>
                        <span
                          v-if="isStale(item.meta.updatedAt)"
                          class="ml-1 text-amber-600 dark:text-amber-400"
                          title="该参数已超过 180 天未更新，数值可能已因 OTA 或调价变化"
                        >
                          · 可能过期
                        </span>
                      </span>
                      <span
                        v-if="item.meta?.confidence != null"
                        :class="item.meta.confidence < 0.6 ? 'text-amber-600 dark:text-amber-400' : ''"
                      >
                        置信度 {{ Math.round(item.meta.confidence * 100) }}%{{
                          item.meta.confidence < 0.6 ? ' · 待核实' : ''
                        }}
                      </span>
                      <a
                        v-if="item.meta?.sourceUrl"
                        :href="item.meta.sourceUrl"
                        target="_blank"
                        rel="noreferrer"
                        class="inline-flex items-center gap-0.5 text-primary hover:underline"
                      >
                        <Link2 class="h-3 w-3" />
                        来源
                      </a>
                      <!-- 同参数已有 N 人反馈存疑：让人知道不是自己一个人看错，处理完自然消失 -->
                      <span
                        v-if="item.pendingCount > 0"
                        class="whitespace-nowrap"
                        :title="`该参数已有 ${item.pendingCount} 条待核实的纠错反馈`"
                      >
                        {{ item.pendingCount }} 人待核实
                      </span>
                    </div>
                  </TableCell>
                  <TableCell class="text-right align-top">
                    <div class="flex items-center justify-end gap-0.5">
                      <Button
                        v-if="series && item.meta?.paramDefId && isNumericValue(item.value)"
                        variant="ghost"
                        size="icon-sm"
                        class="text-muted-foreground hover:text-foreground"
                        aria-label="查看参数走势"
                        title="查看该参数在本系列的跨代走势"
                        @click="openParamHistory(item.meta?.paramDefId)"
                      >
                        <LineChart />
                      </Button>
                      <Button
                        variant="ghost"
                        size="icon-sm"
                        class="text-muted-foreground hover:text-foreground"
                        aria-label="报告参数错误"
                        title="报告参数错误"
                        @click="openFeedback(item)"
                      >
                        <Flag />
                      </Button>
                    </div>
                  </TableCell>
                </TableRow>
              </TableBody>
            </Table>
          </CardContent>
        </Card>
      </div>

      <Empty v-else title="暂无参数数据" description="该机型尚未录入参数，可在管理后台补充。" />
    </template>

    <Dialog
      v-model:open="feedbackOpen"
      :title="feedbackView === 'query' ? '查询反馈进度' : '报告参数错误'"
      :description="
        feedbackView === 'query'
          ? '输入提交成功时返回的编号，查看当前处理状态。'
          : '提交后会进入后台人工核对；重复提交会受频率限制。'
      "
      class="max-w-lg"
    >
      <!-- 编号回执：告知访客查询凭据，闭环「提交后石沉大海」 -->
      <div v-if="feedbackView === 'success' && feedbackSubmittedCode" class="space-y-4 py-2 text-center">
        <div class="mx-auto flex h-10 w-10 items-center justify-center rounded-full bg-emerald-500/10 text-emerald-600 dark:text-emerald-400">
          <Check class="h-5 w-5" />
        </div>
        <div>
          <p class="text-sm font-medium">反馈已提交，感谢你的帮助</p>
          <p class="mt-1 font-mono text-2xl font-semibold tracking-wide">{{ feedbackSubmittedCode }}</p>
          <p class="mt-1 text-xs text-muted-foreground">记下这个编号，可随时回来查询处理进度。</p>
        </div>
      </div>

      <!-- 凭编号查进度 -->
      <div v-else-if="feedbackView === 'query'" class="space-y-4">
        <div class="space-y-1.5">
          <Label for="feedback-query-id">反馈编号</Label>
          <div class="flex gap-2">
            <Input
              id="feedback-query-id"
              v-model="queryIdInput"
              placeholder="如 A1b2C3d4"
              @keyup.enter="queryFeedback"
            />
            <Button variant="outline" :disabled="queryLoading" @click="queryFeedback">
              <Loader2 v-if="queryLoading" class="animate-spin" />
              查询
            </Button>
          </div>
          <p v-if="queryError" class="text-xs text-destructive">{{ queryError }}</p>
        </div>
        <div v-if="queryResult" class="rounded-md bg-muted/50 px-3 py-2.5 text-sm">
          <div class="flex items-center justify-between gap-2">
            <span class="font-mono font-medium">反馈 {{ queryResult.code }}</span>
            <Badge :variant="queryResult.status === 'OPEN' ? 'warning' : 'success'">
              {{ feedbackStatusLabel(queryResult.status) }}
            </Badge>
          </div>
          <p class="mt-1 text-xs text-muted-foreground">
            提交于 {{ formatTime(queryResult.createdAt) }}
            <template v-if="queryResult.status !== 'OPEN'"> · 处理于 {{ formatTime(queryResult.updatedAt) }}</template>
          </p>
        </div>
      </div>

      <!-- 提交表单 -->
      <div v-else class="space-y-4">
        <div class="rounded-md bg-muted/50 px-3 py-2 text-sm">
          <div class="text-xs text-muted-foreground">{{ feedbackTarget?.paramName }}</div>
          <div class="font-medium">{{ feedbackTarget?.value || '—' }}</div>
        </div>
        <div class="space-y-1.5">
          <Label for="feedback-suggested">正确的值</Label>
          <Input id="feedback-suggested" v-model="feedbackForm.suggestedValue" placeholder="如 5000mAh" />
        </div>
        <div class="space-y-1.5">
          <Label for="feedback-desc">问题说明</Label>
          <Textarea id="feedback-desc" v-model="feedbackForm.description" placeholder="补充来源链接或说明，便于核实" />
        </div>
        <div class="space-y-1.5">
          <Label for="feedback-contact">联系方式（选填）</Label>
          <Input id="feedback-contact" v-model="feedbackForm.contact" placeholder="邮箱 / 手机号，便于回访" />
        </div>
        <button type="button" class="text-xs text-primary hover:underline" @click="feedbackView = 'query'">
          已有编号？查询处理进度
        </button>
      </div>

      <template #footer>
        <template v-if="feedbackView === 'success'">
          <Button
            variant="outline"
            :disabled="!feedbackSubmittedCode"
            @click="queryFromReceipt"
          >
            查询进度
          </Button>
          <Button @click="feedbackOpen = false">完成</Button>
        </template>
        <template v-else-if="feedbackView === 'query'">
          <Button variant="outline" @click="feedbackView = 'form'">返回填写</Button>
        </template>
        <template v-else>
          <Button variant="outline" :disabled="feedbackSaving" @click="feedbackOpen = false">取消</Button>
          <Button :disabled="feedbackSaving" @click="submitFeedback">
            <Loader2 v-if="feedbackSaving" class="animate-spin" />
            {{ feedbackSaving ? '提交中…' : '提交反馈' }}
          </Button>
        </template>
      </template>
    </Dialog>
  </div>
</template>

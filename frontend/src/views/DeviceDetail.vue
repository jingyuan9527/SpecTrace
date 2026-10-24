<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { motion } from 'motion-v'
import { ArrowLeft, CalendarDays, Clock3, Flag, GitCompareArrows, Link2, Loader2, Smartphone } from '@lucide/vue'
import { toast } from 'vue-sonner'
import { publicApi } from '@/api'
import type { DeviceVariant, ParamDef, ParamGroup, ParamMeta } from '@/types/api'
import { absoluteUrl, setPageMeta } from '@/lib/meta'
import { formatTime } from '@/lib/utils'
import { useCompareStore } from '@/stores/compare'
import {
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
import ImageWithFallback from '@/components/ImageWithFallback.vue'

const route = useRoute()
const router = useRouter()
const compare = useCompareStore()

const device = ref<any>(null)
const brand = ref<any>(null)
const params = ref<Record<string, string>>({})
const paramMeta = ref<Record<string, ParamMeta>>({})
const lastUpdated = ref<string | null>(null)
const variants = ref<DeviceVariant[]>([])
const groups = ref<ParamGroup[]>([])
const defs = ref<ParamDef[]>([])
const loading = ref(true)
const notFound = ref(false)

const grouped = computed(() =>
  groups.value
    .map((group) => ({
      id: group.id,
      name: group.name,
      items: defs.value
        .filter((d) => d.groupId === group.id)
        .map((d) => ({ ...d, value: params.value[d.paramKey] ?? '', meta: paramMeta.value[d.paramKey] }))
        .filter((d) => d.value !== ''),
    }))
    .filter((g) => g.items.length > 0),
)

onMounted(async () => {
  try {
    const id = Number(route.params.id)
    const detail = await publicApi.deviceDetail(id)
    device.value = detail.device
    brand.value = detail.brand
    params.value = detail.params || {}
    paramMeta.value = detail.paramMeta || {}
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
    ;[groups.value, defs.value] = await Promise.all([publicApi.paramGroups(), publicApi.paramDefs()])
  } catch {
    notFound.value = true
  } finally {
    loading.value = false
  }
})

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
const feedbackOpen = ref(false)
const feedbackSaving = ref(false)
const feedbackTarget = ref<{ paramKey: string; paramName: string; value: string } | null>(null)
const feedbackForm = reactive({ suggestedValue: '', description: '', contact: '' })

function openFeedback(item: { paramKey: string; paramName: string; value: string }) {
  feedbackTarget.value = item
  Object.assign(feedbackForm, { suggestedValue: '', description: '', contact: '' })
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
    await publicApi.submitFeedback({
      deviceId: device.value.id,
      paramKey: feedbackTarget.value.paramKey,
      currentValue: feedbackTarget.value.value,
      suggestedValue: feedbackForm.suggestedValue.trim() || undefined,
      description: feedbackForm.description.trim() || undefined,
      contact: feedbackForm.contact.trim() || undefined,
    })
    toast.success('反馈已提交，感谢你的帮助')
    feedbackOpen.value = false
  } finally {
    feedbackSaving.value = false
  }
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

    <Empty v-else-if="notFound || !device" title="未找到该机型" description="机型可能已下架，返回机型库重新选择。">
      <Button variant="outline" size="sm" class="mt-2" @click="router.push({ name: 'devices' })">返回机型库</Button>
    </Empty>

    <template v-else>
      <motion.div :initial="{ opacity: 0, y: 8 }" :animate="{ opacity: 1, y: 0 }" :transition="{ duration: 0.3 }">
        <Card class="overflow-hidden">
          <div class="grid gap-6 p-6 md:grid-cols-[240px_1fr]">
            <div class="flex h-56 items-center justify-center rounded-xl bg-muted/50 p-6">
              <ImageWithFallback :src="device.thumbnail" :alt="device.name" img-class="max-h-44 object-contain">
                <template #fallback><Smartphone class="h-12 w-12 text-muted-foreground/40" /></template>
              </ImageWithFallback>
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
                      v-if="item.meta?.sourceUrl || item.meta?.updatedAt || item.meta?.confidence != null"
                      class="mt-1 flex flex-wrap items-center gap-x-3 gap-y-1 text-xs font-normal text-muted-foreground"
                    >
                      <!-- 手机宽度下值列只有 ~190px，完整时间会被拆成两三行，小屏只留日期 -->
                      <span v-if="item.meta?.updatedAt" class="whitespace-nowrap">
                        更新于
                        <span class="hidden sm:inline">{{ formatTime(item.meta.updatedAt) }}</span>
                        <span class="sm:hidden">{{ formatTime(item.meta.updatedAt).slice(0, 10) }}</span>
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
                    </div>
                  </TableCell>
                  <TableCell class="text-right align-top">
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
      title="报告参数错误"
      description="提交后会进入后台人工核对；重复提交会受频率限制。"
      class="max-w-lg"
    >
      <div class="space-y-4">
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
      </div>
      <template #footer>
        <Button variant="outline" :disabled="feedbackSaving" @click="feedbackOpen = false">取消</Button>
        <Button :disabled="feedbackSaving" @click="submitFeedback">
          <Loader2 v-if="feedbackSaving" class="animate-spin" />
          {{ feedbackSaving ? '提交中…' : '提交反馈' }}
        </Button>
      </template>
    </Dialog>
  </div>
</template>

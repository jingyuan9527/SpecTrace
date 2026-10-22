<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import html2canvas from 'html2canvas'
import { motion } from 'motion-v'
import { ChevronDown, ChevronRight, Copy, Download, GitCompareArrows, Image, Plus, Share2, Smartphone, X } from '@lucide/vue'
import { toast } from 'vue-sonner'
import { publicApi } from '@/api'
import { useCompareStore } from '@/stores/compare'
import {
  Badge,
  Button,
  Card,
  CardContent,
  Combobox,
  Empty,
  Separator,
  Skeleton,
  Switch,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui'
import ImageWithFallback from '@/components/ImageWithFallback.vue'

interface Device {
  id: number
  name: string
  thumbnail?: string
}

const route = useRoute()
const router = useRouter()
const compare = useCompareStore()
const result = ref<any>(null)
const loading = ref(false)
const watermark = ref(true)
const onlyDiff = ref(true)
const capturing = ref(false)
const captureRef = ref<HTMLElement>()
const candidates = ref<Device[]>([])
const pickId = ref<number | undefined>()
const collapsed = ref<number[]>([])

/** 解析 `?ids=1,2,3`，最多取 4 个正整数。 */
function parseIds(): number[] {
  const raw = Array.isArray(route.query.ids) ? route.query.ids[0] : route.query.ids
  return String(raw || '')
    .split(',')
    .map((item) => Number(item))
    .filter((id) => Number.isFinite(id) && id > 0)
    .slice(0, 4)
}

function syncUrl() {
  const ids = compare.ids
  router.replace({ query: ids.length ? { ids: ids.join(',') } : {} })
}

async function load() {
  if (compare.count < 2) {
    result.value = null
    return
  }
  loading.value = true
  try {
    result.value = await publicApi.compare(compare.ids)
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  // 分享链接进入且本地对比池为空时，用 URL 里的机型还原对比池
  const ids = parseIds()
  if (!compare.count && ids.length >= 2) {
    const data = await publicApi.compare(ids)
    for (const device of data.devices || []) {
      compare.add({ id: device.id, name: device.name, thumbnail: device.thumbnail })
    }
  }
  await load()
  const res = await publicApi.devices({ current: 1, size: 200 })
  candidates.value = res.records
  syncUrl()
})

watch(() => compare.ids.join(','), syncUrl)

const available = computed(() =>
  candidates.value
    .filter((c) => !compare.has(c.id))
    .map((c) => ({ label: c.name, value: c.id })),
)

/** 按“仅显示差异”过滤分组，空分组不渲染。 */
const displayedGroups = computed(() => {
  if (!result.value) return []
  return result.value.groups
    .map((group: any) => ({
      ...group,
      params: onlyDiff.value ? group.params.filter((param: any) => !param.same) : group.params,
    }))
    .filter((group: any) => group.params.length > 0)
})

function formatPrice(price?: number | null, currency = 'CNY') {
  if (price === null || price === undefined) return '—'
  return `${currency === 'CNY' ? '¥' : `${currency} `}${Number(price).toLocaleString('zh-CN')}`
}

function toggleGroup(groupId: number) {
  const index = collapsed.value.indexOf(groupId)
  if (index >= 0) collapsed.value.splice(index, 1)
  else collapsed.value.push(groupId)
}

/** 截图时临时展开全部分组，避免导出的图片缺内容。 */
function isCollapsed(groupId: number) {
  return !capturing.value && collapsed.value.includes(groupId)
}

function addSelected() {
  const device = candidates.value.find((c) => c.id === pickId.value)
  if (!device) return
  if (!compare.add({ id: device.id, name: device.name, thumbnail: device.thumbnail })) {
    toast.warning('对比池最多 4 台机型')
    return
  }
  pickId.value = undefined
  load()
}

function remove(id: number) {
  compare.remove(id)
  load()
}

async function share() {
  syncUrl()
  await nextTick()
  try {
    await navigator.clipboard.writeText(location.href)
    toast.success('分享链接已复制')
  } catch {
    toast.error('复制失败，请手动复制地址栏链接')
  }
}

async function captureCanvas() {
  if (!captureRef.value) throw new Error('无可截图区域')
  capturing.value = true
  await nextTick()
  try {
    return await html2canvas(captureRef.value, { backgroundColor: '#ffffff', scale: 2, useCORS: true })
  } finally {
    capturing.value = false
    await nextTick()
  }
}

async function download() {
  try {
    const canvas = await captureCanvas()
    const link = document.createElement('a')
    link.download = `spectrace-compare-${Date.now()}.png`
    link.href = canvas.toDataURL('image/png')
    link.click()
    toast.success('截图已下载')
  } catch {
    toast.error('导出失败，请重试')
  }
}

async function copyToClipboard() {
  try {
    const canvas = await captureCanvas()
    const blob: Blob = await new Promise((resolve) => canvas.toBlob((b) => resolve(b as Blob), 'image/png'))
    await navigator.clipboard.write([new ClipboardItem({ 'image/png': blob })])
    toast.success('已复制到剪贴板，可直接粘贴分享')
  } catch {
    toast.error('复制失败，请改用下载图片')
  }
}
</script>

<template>
  <div class="space-y-5">
    <div class="flex flex-col gap-3 lg:flex-row lg:items-end lg:justify-between">
      <div>
        <h1 class="text-xl font-semibold tracking-tight sm:text-2xl">机型对比</h1>
        <p class="text-sm text-muted-foreground">最多 4 台，差异项自动高亮，结果可通过链接分享</p>
      </div>

      <div class="flex flex-wrap items-center gap-2">
        <Combobox v-model="pickId" :options="available" placeholder="添加机型" search-placeholder="搜索机型…" class="w-52" />
        <Button variant="outline" :disabled="!pickId" @click="addSelected">
          <Plus />
          添加
        </Button>
        <Separator orientation="vertical" class="mx-1 hidden h-6 sm:block" />
        <label class="flex items-center gap-2 text-sm text-muted-foreground">
          <Switch v-model="onlyDiff" />
          仅看差异
        </label>
        <label class="flex items-center gap-2 text-sm text-muted-foreground">
          <Switch v-model="watermark" />
          水印
        </label>
        <Button variant="outline" :disabled="compare.count < 2" @click="share">
          <Share2 />
          分享链接
        </Button>
        <Button variant="outline" :disabled="!result" @click="download">
          <Download />
          下载图片
        </Button>
        <Button :disabled="!result" @click="copyToClipboard">
          <Copy />
          复制图片
        </Button>
      </div>
    </div>

    <div v-if="compare.count < 2" class="rounded-xl border border-dashed">
      <Empty
        title="对比池至少需要 2 台机型"
        description="从机型库勾选，或在上方搜索框添加。选择结果会自动保存在本地，也可通过链接分享。"
      >
        <Button variant="outline" size="sm" class="mt-2" @click="$router.push({ name: 'devices' })">
          <GitCompareArrows />
          去机型库挑选
        </Button>
      </Empty>
    </div>

    <Skeleton v-else-if="loading" class="h-96 rounded-xl" />

    <motion.div
      v-else-if="result"
      :initial="{ opacity: 0, y: 8 }"
      :animate="{ opacity: 1, y: 0 }"
      :transition="{ duration: 0.3 }"
    >
      <div ref="captureRef" class="capture-area overflow-hidden rounded-xl border bg-background">
        <div class="border-b px-5 py-4 text-center">
          <div class="flex items-center justify-center gap-2 text-base font-semibold">
            <Image class="h-4 w-4 text-primary" />
            SpecTrace · 机型参数对比
          </div>
          <p class="mt-1 text-xs text-muted-foreground">{{ new Date().toLocaleDateString('zh-CN') }}</p>
        </div>

        <div v-if="displayedGroups.length" class="overflow-x-auto">
          <Table class="min-w-[640px]">
            <TableHeader>
              <TableRow class="hover:bg-transparent">
                <TableHead class="sticky left-0 z-10 w-40 bg-muted/60">参数</TableHead>
                <TableHead v-for="device in result.devices" :key="device.id" class="bg-muted/60 text-center">
                  <div class="flex flex-col items-center gap-1 py-1">
                    <ImageWithFallback :src="device.thumbnail" :alt="device.name" img-class="h-14 object-contain">
                      <template #fallback><Smartphone class="h-8 w-8 text-muted-foreground/40" /></template>
                    </ImageWithFallback>
                    <span class="text-sm font-semibold normal-case tracking-normal">{{ device.name }}</span>
                    <Button
                      v-if="!capturing"
                      variant="ghost"
                      size="sm"
                      class="h-6 px-2 text-xs text-muted-foreground hover:text-destructive"
                      @click="remove(device.id)"
                    >
                      <X class="h-3 w-3" />
                      移除
                    </Button>
                  </div>
                </TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              <TableRow>
                <TableCell class="sticky left-0 z-10 bg-background text-muted-foreground">存储 / 发布价</TableCell>
                <TableCell v-for="device in result.devices" :key="device.id" class="text-center align-top">
                  <div v-if="device.variants?.length" class="space-y-1 text-xs">
                    <div
                      v-for="variant in device.variants"
                      :key="variant.id ?? `${variant.storage}-${variant.ram}`"
                      class="flex flex-wrap items-center justify-center gap-1"
                    >
                      <span class="text-muted-foreground">{{ variant.ram ? `${variant.ram}+` : '' }}{{ variant.storage }}</span>
                      <span class="font-medium">{{ formatPrice(variant.price, variant.currency) }}</span>
                    </div>
                  </div>
                  <span v-else class="text-muted-foreground">—</span>
                </TableCell>
              </TableRow>
              <template v-for="group in displayedGroups" :key="group.groupId">
                <TableRow class="cursor-pointer hover:bg-muted/40" @click="toggleGroup(group.groupId)">
                  <TableCell :colspan="result.devices.length + 1" class="bg-muted/30 py-2">
                    <div class="flex items-center gap-1.5 text-xs font-semibold text-muted-foreground">
                      <ChevronDown v-if="!isCollapsed(group.groupId)" class="h-3.5 w-3.5" />
                      <ChevronRight v-else class="h-3.5 w-3.5" />
                      {{ group.groupName }}
                      <span class="font-normal">· {{ group.params.length }} 项</span>
                    </div>
                  </TableCell>
                </TableRow>
                <template v-if="!isCollapsed(group.groupId)">
                  <TableRow v-for="param in group.params" :key="param.paramKey">
                    <TableCell class="sticky left-0 z-10 bg-background text-muted-foreground">{{ param.paramName }}</TableCell>
                    <TableCell
                      v-for="(value, index) in param.values"
                      :key="index"
                      class="text-center"
                      :class="param.same ? 'text-muted-foreground' : 'bg-amber-500/10 font-medium'"
                    >
                      {{ value || '—' }}
                      <span v-if="param.unit && value" class="ml-1 text-xs font-normal text-muted-foreground">{{ param.unit }}</span>
                    </TableCell>
                  </TableRow>
                </template>
              </template>
            </TableBody>
          </Table>
        </div>

        <Empty v-else title="没有差异项" description="关闭「仅看差异」可查看全部参数。" />

        <div v-if="watermark" class="flex justify-end border-t px-4 py-2 text-[11px] text-muted-foreground/60">
          SpecTrace · 参数以官方公布为准
        </div>
      </div>

      <p class="mt-3 flex items-center gap-2 text-xs text-muted-foreground">
        <Badge variant="warning">差异项</Badge>
        不同机型取值不一致的参数以浅黄底色标记；点击分组标题可折叠。
      </p>
    </motion.div>

    <Empty v-else title="暂无对比数据" description="请确认所选机型已录入参数。" />
  </div>
</template>

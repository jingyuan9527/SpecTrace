<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { motion } from 'motion-v'
import { GitCompareArrows, Search, Smartphone, SlidersHorizontal } from '@lucide/vue'
import { toast } from 'vue-sonner'
import { publicApi } from '@/api'
import { useCompareStore } from '@/stores/compare'
import { Badge, Button, Card, CardContent, Empty, Input, Pagination, Select, Skeleton } from '@/components/ui'
import ImageWithFallback from '@/components/ImageWithFallback.vue'

interface Brand {
  id: number
  name: string
}

interface Device {
  id: number
  name: string
  thumbnail?: string
  releaseDate?: string
  brandId?: number
}

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
  await Promise.all([load(), loadYears()])
})

watch(
  () => query.brandId,
  () => {
    query.year = undefined
    loadYears()
    applyFilter()
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

function toggleCompare(device: Device) {
  if (compare.has(device.id)) {
    compare.remove(device.id)
    toast.info(`已移出对比池：${device.name}`)
    return
  }
  compare.add({ id: device.id, name: device.name, thumbnail: device.thumbnail })
  toast.success(`已加入对比池：${device.name}`)
}
</script>

<template>
  <div class="space-y-5">
    <div class="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
      <div>
        <h1 class="text-xl font-semibold tracking-tight sm:text-2xl">机型库</h1>
        <p class="text-sm text-muted-foreground">按品牌、年份与关键词筛选，勾选后进入对比</p>
      </div>
      <Button variant="outline" size="sm" @click="router.push({ name: 'compare' })">
        <GitCompareArrows />
        前往对比
        <Badge v-if="compare.count" class="h-5 min-w-5 justify-center px-1">{{ compare.count }}</Badge>
      </Button>
    </div>

    <Card>
      <CardContent class="flex flex-col gap-3 p-4 lg:flex-row lg:items-center">
        <div class="flex items-center gap-2 text-sm font-medium text-muted-foreground">
          <SlidersHorizontal class="h-4 w-4" />
          筛选
        </div>
        <div class="grid flex-1 grid-cols-1 gap-2 sm:grid-cols-2 lg:grid-cols-[160px_140px_1fr_auto]">
          <Select v-model="query.brandId" :options="brandOptions" placeholder="全部品牌" />
          <Select v-model="query.year" :options="yearOptions" placeholder="全部年份" />
          <div class="relative">
            <Search class="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
            <Input v-model="query.keyword" class="pl-9" placeholder="机型名称" @keyup.enter="applyFilter" />
          </div>
          <Button @click="applyFilter">搜索</Button>
        </div>
      </CardContent>
    </Card>

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
            class="flex h-40 w-full items-center justify-center bg-muted/50 p-4"
            @click="router.push({ name: 'device-detail', params: { id: device.id } })"
          >
            <ImageWithFallback
              :src="device.thumbnail"
              :alt="device.name"
              img-class="max-h-32 object-contain transition-transform duration-300 group-hover:scale-105"
            >
              <template #fallback><Smartphone class="h-10 w-10 text-muted-foreground/40" /></template>
            </ImageWithFallback>
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
  </div>
</template>

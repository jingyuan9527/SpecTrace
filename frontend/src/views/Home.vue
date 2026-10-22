<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { motion } from 'motion-v'
import { ArrowRight, GitCompareArrows, Search, Smartphone, Sparkles, TrendingUp } from '@lucide/vue'
import { toast } from 'vue-sonner'
import { publicApi } from '@/api'
import { useCompareStore } from '@/stores/compare'
import { Badge, Button, Card, CardContent, Empty, Input, Skeleton } from '@/components/ui'
import ImageWithFallback from '@/components/ImageWithFallback.vue'

interface Brand {
  id: number
  name: string
  logo?: string
}

interface Device {
  id: number
  name: string
  thumbnail?: string
  releaseDate?: string
}

const router = useRouter()
const compare = useCompareStore()

const brands = ref<Brand[]>([])
const hot = ref<Device[]>([])
const keyword = ref('')
const loading = ref(true)

onMounted(async () => {
  try {
    const [brandList, hotList] = await Promise.all([publicApi.brands(), publicApi.hot(8)])
    brands.value = brandList
    hot.value = hotList
  } finally {
    loading.value = false
  }
})

function search() {
  router.push({ name: 'devices', query: keyword.value ? { keyword: keyword.value } : {} })
}

function addCompare(device: Device) {
  if (compare.has(device.id)) {
    toast.info(`${device.name} 已在对比池`)
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
  <div class="space-y-10">
    <!-- Hero -->
    <section class="relative overflow-hidden rounded-2xl border bg-gradient-to-br from-primary/[0.07] via-background to-background">
      <div class="pointer-events-none absolute inset-0 bg-[linear-gradient(to_right,hsl(var(--border))_1px,transparent_1px),linear-gradient(to_bottom,hsl(var(--border))_1px,transparent_1px)] bg-[size:44px_44px] opacity-40 [mask-image:radial-gradient(ellipse_at_top_right,black,transparent_70%)]" />
      <div class="relative grid gap-8 p-6 sm:p-10 lg:grid-cols-[1.15fr_0.85fr] lg:p-12">
        <div class="max-w-2xl">
          <Badge variant="secondary" class="mb-4 gap-1.5">
            <Sparkles class="h-3.5 w-3.5" />
            全品牌参数库 · 人工审核入库
          </Badge>
          <motion.h1
            :initial="{ opacity: 0, y: 12 }"
            :animate="{ opacity: 1, y: 0 }"
            :transition="{ duration: 0.4 }"
            class="text-3xl font-semibold tracking-tight sm:text-4xl lg:text-5xl"
          >
            手机参数，<span class="text-primary">一屏看透</span>
          </motion.h1>
          <motion.p
            :initial="{ opacity: 0, y: 12 }"
            :animate="{ opacity: 1, y: 0 }"
            :transition="{ duration: 0.4, delay: 0.08 }"
            class="mt-3 text-sm leading-6 text-muted-foreground sm:text-base"
          >
            横向对比多台机型、追踪单参数跨代演变，一键导出对比图分享。参数冲突以品牌官网来源优先。
          </motion.p>

          <div class="mt-6 flex max-w-xl flex-col gap-2 sm:flex-row">
            <div class="relative flex-1">
              <Search class="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
              <Input
                v-model="keyword"
                class="h-10 pl-9"
                placeholder="搜索机型，如 iPhone 17"
                @keyup.enter="search"
              />
            </div>
            <Button size="lg" @click="search">搜索机型</Button>
          </div>

          <div class="mt-5 flex flex-wrap gap-2">
            <Button variant="outline" size="sm" @click="router.push({ name: 'compare' })">
              <GitCompareArrows />
              机型对比
            </Button>
            <Button variant="outline" size="sm" @click="router.push({ name: 'history' })">
              <TrendingUp />
              参数历史演变
            </Button>
            <Button variant="ghost" size="sm" @click="router.push({ name: 'devices' })">
              浏览全部机型
              <ArrowRight />
            </Button>
          </div>
        </div>

        <div class="hidden items-center justify-center lg:flex">
          <div class="grid w-full max-w-sm grid-cols-2 gap-3">
            <Card class="col-span-2 bg-background/70 backdrop-blur">
              <CardContent class="flex items-center gap-3 p-4">
                <span class="flex h-10 w-10 items-center justify-center rounded-lg bg-primary/10 text-primary">
                  <Smartphone class="h-5 w-5" />
                </span>
                <div>
                  <div class="text-2xl font-semibold tabular-nums">{{ brands.length }}</div>
                  <div class="text-xs text-muted-foreground">收录品牌</div>
                </div>
              </CardContent>
            </Card>
            <Card class="bg-background/70 backdrop-blur">
              <CardContent class="p-4">
                <div class="text-2xl font-semibold tabular-nums">{{ hot.length }}</div>
                <div class="text-xs text-muted-foreground">热门机型</div>
              </CardContent>
            </Card>
            <Card class="bg-background/70 backdrop-blur">
              <CardContent class="p-4">
                <div class="text-2xl font-semibold tabular-nums">{{ compare.count }}</div>
                <div class="text-xs text-muted-foreground">对比池</div>
              </CardContent>
            </Card>
          </div>
        </div>
      </div>
    </section>

    <!-- 品牌导航 -->
    <section class="space-y-4">
      <div class="flex items-end justify-between">
        <div>
          <h2 class="text-lg font-semibold tracking-tight">品牌导航</h2>
          <p class="text-sm text-muted-foreground">按品牌快速筛选机型</p>
        </div>
        <Button variant="ghost" size="sm" @click="router.push({ name: 'devices' })">
          全部机型
          <ArrowRight />
        </Button>
      </div>

      <div v-if="loading" class="grid grid-cols-3 gap-3 sm:grid-cols-6">
        <Skeleton v-for="i in 6" :key="i" class="h-24 rounded-xl" />
      </div>
      <div v-else-if="brands.length" class="grid grid-cols-3 gap-3 sm:grid-cols-6">
        <motion.button
          v-for="(brand, index) in brands"
          :key="brand.id"
          type="button"
          :initial="{ opacity: 0, y: 8 }"
          :animate="{ opacity: 1, y: 0 }"
          :transition="{ duration: 0.3, delay: index * 0.03 }"
          class="group flex flex-col items-center justify-center gap-2 rounded-xl border bg-card p-4 transition-colors hover:border-primary/40 hover:bg-accent/40"
          @click="router.push({ name: 'devices', query: { brandId: brand.id } })"
        >
          <ImageWithFallback
            :src="brand.logo"
            :alt="brand.name"
            img-class="h-8 max-w-[70%] object-contain opacity-80 transition-opacity group-hover:opacity-100"
          >
            <template #fallback>
              <span class="flex h-8 w-8 items-center justify-center rounded-full bg-muted text-xs font-semibold text-muted-foreground">
                {{ brand.name.slice(0, 1) }}
              </span>
            </template>
          </ImageWithFallback>
          <span class="text-xs font-medium">{{ brand.name }}</span>
        </motion.button>
      </div>
      <Empty v-else title="暂无品牌数据" description="请先在管理后台添加品牌与机型。" />
    </section>

    <!-- 热门机型 -->
    <section class="space-y-4">
      <div class="flex items-end justify-between">
        <div>
          <h2 class="text-lg font-semibold tracking-tight">热门机型</h2>
          <p class="text-sm text-muted-foreground">最近收录与访问较多的机型</p>
        </div>
      </div>

      <div v-if="loading" class="grid grid-cols-2 gap-3 md:grid-cols-4">
        <Skeleton v-for="i in 4" :key="i" class="h-64 rounded-xl" />
      </div>
      <div v-else-if="hot.length" class="grid grid-cols-2 gap-3 md:grid-cols-4">
        <motion.div
          v-for="(device, index) in hot"
          :key="device.id"
          :initial="{ opacity: 0, y: 10 }"
          :animate="{ opacity: 1, y: 0 }"
          :transition="{ duration: 0.3, delay: index * 0.04 }"
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
              <div class="flex gap-2 pt-2">
                <Button size="sm" variant="outline" class="flex-1" @click="addCompare(device)">
                  {{ compare.has(device.id) ? '已在对比池' : '加入对比' }}
                </Button>
                <Button size="sm" variant="ghost" @click="router.push({ name: 'device-detail', params: { id: device.id } })">
                  详情
                </Button>
              </div>
            </CardContent>
          </Card>
        </motion.div>
      </div>
      <Empty v-else title="暂无热门机型" description="机型上架后会展示在这里。" />
    </section>
  </div>
</template>

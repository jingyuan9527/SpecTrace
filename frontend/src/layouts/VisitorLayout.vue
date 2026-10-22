<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  GitCompareArrows,
  History,
  House,
  Laptop,
  Menu,
  Moon,
  Smartphone,
  Sun,
} from '@lucide/vue'
import { useThemeStore } from '@/stores/theme'
import { useCompareStore } from '@/stores/compare'
import { Badge, Button, Sheet } from '@/components/ui'

const route = useRoute()
const router = useRouter()
const theme = useThemeStore()
const compare = useCompareStore()
const mobileOpen = ref(false)

const tabs = [
  { name: 'home', label: '首页', icon: House },
  { name: 'devices', label: '机型库', icon: Smartphone },
  { name: 'compare', label: '机型对比', icon: GitCompareArrows },
  { name: 'history', label: '参数历史', icon: History },
]

/** 详情页属于机型库分支，导航高亮要保持在同一父级。 */
const activeName = computed(() => (route.name === 'device-detail' ? 'devices' : route.name))

function go(name: string) {
  mobileOpen.value = false
  router.push({ name })
}

function goAdmin() {
  mobileOpen.value = false
  router.push('/admin/login')
}
</script>

<template>
  <div class="flex min-h-full flex-col bg-background">
    <header class="sticky top-0 z-30 w-full border-b bg-background/80 backdrop-blur supports-[backdrop-filter]:bg-background/60">
      <div class="container flex h-14 items-center gap-3">
        <router-link to="/" class="flex shrink-0 items-center gap-2">
          <span class="flex h-8 w-8 items-center justify-center rounded-lg bg-primary text-primary-foreground">
            <Laptop class="h-4 w-4" />
          </span>
          <span class="text-base font-semibold tracking-tight">SpecTrace</span>
        </router-link>

        <nav class="ml-2 hidden items-center gap-1 md:flex">
          <router-link
            v-for="tab in tabs"
            :key="tab.name"
            :to="{ name: tab.name }"
            class="inline-flex h-8 items-center gap-1.5 rounded-md px-3 text-sm font-medium transition-colors"
            :class="
              activeName === tab.name
                ? 'bg-secondary text-foreground'
                : 'text-muted-foreground hover:bg-accent hover:text-foreground'
            "
          >
            <component :is="tab.icon" class="h-4 w-4" />
            {{ tab.label }}
          </router-link>
        </nav>

        <div class="ml-auto flex items-center gap-1">
          <Button variant="outline" size="sm" class="hidden sm:inline-flex" @click="router.push({ name: 'compare' })">
            <GitCompareArrows />
            对比池
            <Badge v-if="compare.count" class="h-5 min-w-5 justify-center px-1 tabular-nums">{{ compare.count }}</Badge>
          </Button>
          <Button variant="ghost" size="icon" :aria-label="theme.dark ? '切换到亮色' : '切换到暗色'" @click="theme.toggle()">
            <Sun v-if="theme.dark" />
            <Moon v-else />
          </Button>
          <Button variant="ghost" size="sm" class="hidden sm:inline-flex" @click="router.push('/admin/login')">后台</Button>
          <Button variant="ghost" size="icon" class="md:hidden" aria-label="打开菜单" @click="mobileOpen = true">
            <Menu />
          </Button>
        </div>
      </div>
    </header>

    <main class="container w-full flex-1 py-6 sm:py-8">
      <router-view />
    </main>

    <footer class="border-t py-6">
      <div class="container flex flex-col items-center justify-between gap-2 text-xs text-muted-foreground sm:flex-row">
        <span>SpecTrace · 手机参数对比平台</span>
        <span>参数以品牌官方公布为准，冲突处以官网来源优先</span>
      </div>
    </footer>

    <Sheet v-model:open="mobileOpen" title="导航" side="right">
      <nav class="flex flex-col gap-1">
        <button
          v-for="tab in tabs"
          :key="tab.name"
          type="button"
          class="flex items-center gap-2 rounded-md px-3 py-2 text-sm font-medium transition-colors"
          :class="activeName === tab.name ? 'bg-secondary' : 'text-muted-foreground hover:bg-accent hover:text-foreground'"
          @click="go(tab.name)"
        >
          <component :is="tab.icon" class="h-4 w-4" />
          {{ tab.label }}
          <Badge v-if="tab.name === 'compare' && compare.count" class="ml-auto">{{ compare.count }}</Badge>
        </button>
      </nav>
      <div class="mt-auto border-t pt-4">
        <Button variant="outline" size="sm" class="w-full" @click="goAdmin">进入管理后台</Button>
      </div>
    </Sheet>
  </div>
</template>

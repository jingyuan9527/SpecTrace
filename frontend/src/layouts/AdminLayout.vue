<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  Activity,
  Bot,
  Gauge,
  ChevronDown,
  ClipboardCheck,
  Database,
  Flag,
  House,
  ImagePlus,
  KeyRound,
  Laptop,
  Layers,
  LogOut,
  Menu,
  Moon,
  ScrollText,
  SlidersHorizontal,
  Smartphone,
  Sparkles,
  Sun,
  Tags,
  Users,
} from '@lucide/vue'
import { useThemeStore } from '@/stores/theme'
import { adminApi } from '@/api'
import type { AdminBadges } from '@/types/api'
import { Badge, Button, DropdownMenu, Sheet, type MenuItem } from '@/components/ui'
import ConfirmDialog from '@/components/ConfirmDialog.vue'

const route = useRoute()
const router = useRouter()
const theme = useThemeStore()

const mobileOpen = ref(false)
const logoutOpen = ref(false)

// 待处理反馈数：进入后台与路由切换时刷新，处理完成后离开页面即归零
const badges = ref<AdminBadges | null>(null)

async function loadBadges() {
  try {
    badges.value = await adminApi.badges()
  } catch {
    // 徽标是辅助信息，拉取失败不打扰后台使用
    badges.value = null
  }
}

onMounted(loadBadges)
watch(() => route.path, loadBadges)

// 按使用频率排序：核心工作流（机型 → 抓取 → 审核 → 数据口径）在前，配置居中，管理/运维垫底
const menus = [
  { path: '/admin/devices', label: '机型管理', icon: Smartphone },
  { path: '/admin/agent-tasks', label: '抓取任务', icon: Bot },
  { path: '/admin/pending', label: '抓取审核', icon: ClipboardCheck },
  { path: '/admin/monitor', label: '运行监控', icon: Gauge },
  { path: '/admin/ai-config', label: 'AI 配置', icon: Sparkles },
  { path: '/admin/ai-image', label: 'AI 绘图', icon: ImagePlus },
  { path: '/admin/brands', label: '品牌管理', icon: Tags },
  { path: '/admin/series', label: '系列管理', icon: Layers },
  { path: '/admin/health', label: '数据健康度', icon: Activity },
  { path: '/admin/params', label: '参数元数据', icon: SlidersHorizontal },
  { path: '/admin/feedback', label: '纠错反馈', icon: Flag },
  { path: '/admin/users', label: '账号权限', icon: Users },
  { path: '/admin/logs', label: '操作日志', icon: ScrollText },
  { path: '/admin/maintenance', label: '系统运维', icon: Database },
]

const currentLabel = computed(() => menus.find((m) => route.path.startsWith(m.path))?.label ?? '管理后台')
const user = computed<{ nickname?: string; username?: string }>(() => {
  try {
    return JSON.parse(localStorage.getItem('spectrace-user') || '{}')
  } catch {
    return {}
  }
})

const userMenu: MenuItem[] = [
  {
    label: '修改密码',
    icon: KeyRound,
    onSelect: () => {
      router.push('/admin/change-password')
    },
  },
  {
    label: '返回前台',
    icon: House,
    onSelect: () => {
      router.push('/')
    },
  },
  {
    label: '退出登录',
    icon: LogOut,
    destructive: true,
    separatorBefore: true,
    onSelect: () => {
      logoutOpen.value = true
    },
  },
]

function doLogout() {
  localStorage.removeItem('spectrace-token')
  localStorage.removeItem('spectrace-user')
  logoutOpen.value = false
  router.push('/admin/login')
}
</script>

<template>
  <div class="flex min-h-screen bg-muted/25">
    <aside class="hidden w-60 shrink-0 flex-col border-r bg-background md:flex">
      <div class="flex h-14 items-center gap-2 border-b px-4">
        <span class="flex h-8 w-8 items-center justify-center rounded-lg bg-primary text-primary-foreground">
          <Laptop class="h-4 w-4" />
        </span>
        <div class="leading-tight">
          <div class="text-sm font-semibold">SpecTrace</div>
          <div class="text-[11px] text-muted-foreground">管理后台</div>
        </div>
      </div>

      <nav class="flex-1 space-y-1 overflow-y-auto p-3">
        <router-link
          v-for="item in menus"
          :key="item.path"
          :to="item.path"
          class="flex items-center gap-2.5 rounded-md px-3 py-2 text-sm font-medium transition-colors"
          :class="
            route.path.startsWith(item.path)
              ? 'bg-secondary text-foreground'
              : 'text-muted-foreground hover:bg-accent hover:text-foreground'
          "
        >
          <component :is="item.icon" class="h-4 w-4" />
          {{ item.label }}
          <Badge
            v-if="item.path === '/admin/feedback' && badges && badges.openFeedback > 0"
            variant="destructive"
            class="ml-auto px-1.5"
          >
            {{ badges.openFeedback }}
          </Badge>
          <Badge
            v-if="item.path === '/admin/pending' && badges && badges.pendingReview > 0"
            variant="info"
            class="ml-auto px-1.5"
            :title="`最老一条已等待 ${badges.pendingOldestHours} 小时`"
          >
            {{ badges.pendingReview }}
          </Badge>
        </router-link>
      </nav>

      <div class="border-t p-3">
        <Button variant="ghost" size="sm" class="w-full justify-start text-muted-foreground" @click="router.push('/')">
          <House />
          返回访客端
        </Button>
      </div>
    </aside>

    <div class="flex min-w-0 flex-1 flex-col">
      <header class="sticky top-0 z-20 flex h-14 items-center gap-2 border-b bg-background/85 px-4 backdrop-blur">
        <Button variant="ghost" size="icon" class="md:hidden" aria-label="打开菜单" @click="mobileOpen = true">
          <Menu />
        </Button>
        <div class="truncate text-sm font-medium">{{ currentLabel }}</div>

        <div class="ml-auto flex items-center gap-1">
          <Button variant="ghost" size="icon" :aria-label="theme.dark ? '切换到亮色' : '切换到暗色'" @click="theme.toggle()">
            <Sun v-if="theme.dark" />
            <Moon v-else />
          </Button>
          <DropdownMenu :items="userMenu">
            <template #trigger>
              <Button variant="ghost" size="sm" class="gap-1.5">
                <span class="hidden max-w-24 truncate sm:inline">{{ user.nickname || user.username || '管理员' }}</span>
                <ChevronDown class="h-3.5 w-3.5 opacity-60" />
              </Button>
            </template>
          </DropdownMenu>
        </div>
      </header>

      <main class="flex-1 p-4 sm:p-6">
        <router-view />
      </main>
    </div>

    <Sheet v-model:open="mobileOpen" title="管理后台" side="left">
      <nav class="flex flex-col gap-1">
        <router-link
          v-for="item in menus"
          :key="item.path"
          :to="item.path"
          class="flex items-center gap-2.5 rounded-md px-3 py-2 text-sm font-medium transition-colors"
          :class="
            route.path.startsWith(item.path)
              ? 'bg-secondary text-foreground'
              : 'text-muted-foreground hover:bg-accent hover:text-foreground'
          "
          @click="mobileOpen = false"
        >
          <component :is="item.icon" class="h-4 w-4" />
          {{ item.label }}
          <Badge
            v-if="item.path === '/admin/feedback' && badges && badges.openFeedback > 0"
            variant="destructive"
            class="ml-auto px-1.5"
          >
            {{ badges.openFeedback }}
          </Badge>
          <Badge
            v-if="item.path === '/admin/pending' && badges && badges.pendingReview > 0"
            variant="info"
            class="ml-auto px-1.5"
            :title="`最老一条已等待 ${badges.pendingOldestHours} 小时`"
          >
            {{ badges.pendingReview }}
          </Badge>
        </router-link>
      </nav>
    </Sheet>

    <ConfirmDialog
      v-model:open="logoutOpen"
      title="退出登录"
      description="退出后需要重新输入账号密码才能进入管理后台。"
      confirm-text="退出"
      destructive
      @confirm="doLogout"
    />
  </div>
</template>

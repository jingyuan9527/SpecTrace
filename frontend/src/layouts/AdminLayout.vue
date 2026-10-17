<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useThemeStore } from '@/stores/theme'
import { ElMessageBox } from 'element-plus'

const route = useRoute()
const router = useRouter()
const theme = useThemeStore()

const menus = [
  { path: '/admin/brands', label: '品牌管理', icon: 'CollectionTag' },
  { path: '/admin/params', label: '参数元数据', icon: 'SetUp' },
  { path: '/admin/devices', label: '机型管理', icon: 'Iphone' },
  { path: '/admin/agent-tasks', label: '抓取任务', icon: 'Cpu' },
  { path: '/admin/ai-config', label: 'AI 配置', icon: 'MagicStick' },
  { path: '/admin/pending', label: '待审核数据', icon: 'DocumentChecked' },
  { path: '/admin/users', label: '账号权限', icon: 'User' },
  { path: '/admin/logs', label: '操作日志', icon: 'Tickets' },
]

const active = computed(() => route.path)

async function logout() {
  await ElMessageBox.confirm('确认退出登录？', '提示', { type: 'warning' })
  localStorage.removeItem('spectrace-token')
  localStorage.removeItem('spectrace-user')
  router.push('/admin/login')
}
</script>

<template>
  <el-container class="h-screen">
    <el-aside width="220px" class="border-r border-slate-200 dark:border-slate-700">
      <div class="h-16 flex items-center gap-2 px-5 font-bold text-brand text-lg">
        <span>📱</span> SpecTrace
      </div>
      <el-menu :default-active="active" router>
        <el-menu-item v-for="m in menus" :key="m.path" :index="m.path">
          <el-icon><component :is="m.icon" /></el-icon>
          <span>{{ m.label }}</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="flex items-center border-b border-slate-200 dark:border-slate-700">
        <span class="font-semibold">管理后台</span>
        <div class="ml-auto flex items-center gap-3">
          <el-button size="small" circle @click="theme.toggle()">
            <el-icon><Moon v-if="!theme.dark" /><Sunny v-else /></el-icon>
          </el-button>
          <el-button size="small" text @click="router.push('/')">前台</el-button>
          <el-button size="small" type="danger" plain @click="logout">退出</el-button>
        </div>
      </el-header>

      <el-main class="bg-slate-50 dark:bg-slate-900">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

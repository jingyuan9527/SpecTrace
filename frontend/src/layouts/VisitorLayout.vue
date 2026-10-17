<script setup lang="ts">
import { useRouter } from 'vue-router'
import { useThemeStore } from '@/stores/theme'
import { useCompareStore } from '@/stores/compare'

const router = useRouter()
const theme = useThemeStore()
const compare = useCompareStore()

const tabs = [
  { name: 'home', label: '首页' },
  { name: 'devices', label: '机型库' },
  { name: 'compare', label: '机型对比' },
  { name: 'history', label: '参数历史' },
]
</script>

<template>
  <div class="min-h-full flex flex-col bg-slate-50 dark:bg-slate-900 text-slate-800 dark:text-slate-100">
    <header class="sticky top-0 z-20 border-b border-slate-200 dark:border-slate-700 bg-white/90 dark:bg-slate-800/90 backdrop-blur">
      <div class="mx-auto max-w-7xl px-4 h-16 flex items-center gap-8">
        <router-link to="/" class="flex items-center gap-2 text-lg font-bold text-brand">
          <span class="text-2xl">📱</span> SpecTrace
        </router-link>

        <nav class="flex items-center gap-1">
          <router-link
            v-for="t in tabs"
            :key="t.name"
            :to="{ name: t.name }"
            class="px-3 py-2 rounded-md text-sm hover:bg-slate-100 dark:hover:bg-slate-700"
            active-class="text-brand font-semibold"
          >
            {{ t.label }}
          </router-link>
        </nav>

        <div class="ml-auto flex items-center gap-3">
          <el-badge :value="compare.count" :hidden="compare.count === 0">
            <el-button size="small" @click="router.push('/compare')">
              <el-icon><Grid /></el-icon>
              <span class="ml-1">对比池</span>
            </el-button>
          </el-badge>
          <el-button size="small" circle @click="theme.toggle()">
            <el-icon><Moon v-if="!theme.dark" /><Sunny v-else /></el-icon>
          </el-button>
          <el-button size="small" text type="info" @click="router.push('/admin/login')">后台</el-button>
        </div>
      </div>
    </header>

    <main class="flex-1 mx-auto w-full max-w-7xl px-4 py-6">
      <router-view />
    </main>

    <footer class="border-t border-slate-200 dark:border-slate-700 py-6 text-center text-sm text-slate-400">
      SpecTrace · 手机参数对比平台 · 参数以官方公布为准
    </footer>
  </div>
</template>

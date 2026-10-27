<script setup lang="ts">
import { onMounted } from 'vue'
import { useThemeStore } from '@/stores/theme'
import { Toaster } from '@/components/ui'
import AppErrorBoundary from '@/components/AppErrorBoundary.vue'

const theme = useThemeStore()
onMounted(() => theme.apply())
</script>

<template>
  <!-- 错误边界包住整个路由出口：单页渲染失败只降级该页，不牵连站点骨架 -->
  <AppErrorBoundary>
    <router-view />
  </AppErrorBoundary>
  <Toaster />
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useThemeStore } from '@/stores/theme'
import { Toaster } from '@/components/ui'

const theme = useThemeStore()
onMounted(() => theme.apply())
</script>

<template>
  <router-view />
  <Toaster />
</template>

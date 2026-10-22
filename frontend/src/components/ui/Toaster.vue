<script setup lang="ts">
import { Toaster as SonnerToaster } from 'vue-sonner'
import { computed } from 'vue'
import { useThemeStore } from '@/stores/theme'

const theme = useThemeStore()
const mode = computed(() => (theme.dark ? 'dark' : 'light'))
</script>

<template>
  <SonnerToaster :theme="mode" position="top-center" :rich-colors="true" close-button :duration="3200" />
</template>

<script setup lang="ts">
defineProps<{ title: string; description?: string }>()
</script>

<template>
  <!-- 页头只占两行：标题与主操作同一行，说明文字压缩，把首屏高度让给列表与看板 -->
  <div class="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
    <div class="min-w-0 space-y-0.5">
      <h1 class="truncate text-lg font-semibold tracking-tight sm:text-xl">{{ title }}</h1>
      <p v-if="description" class="text-xs text-muted-foreground sm:text-sm">{{ description }}</p>
    </div>
    <div v-if="$slots.actions" class="flex shrink-0 flex-wrap items-center gap-2">
      <slot name="actions" />
    </div>
  </div>
</template>

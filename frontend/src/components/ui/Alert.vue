<script setup lang="ts">
import { AlertCircle, CheckCircle2, Info, TriangleAlert } from '@lucide/vue'
import { computed } from 'vue'
import { cn } from '@/lib/utils'

const props = withDefaults(
  defineProps<{
    variant?: 'default' | 'destructive' | 'info' | 'warning' | 'success'
    title?: string
    description?: string
    class?: string
  }>(),
  { variant: 'default' },
)

const styles = computed(
  () =>
    ({
      default: 'bg-background text-foreground',
      destructive: 'border-destructive/50 text-destructive [&>svg]:text-destructive',
      info: 'border-sky-500/40 bg-sky-500/5 text-sky-700 dark:text-sky-300 [&>svg]:text-sky-500',
      warning: 'border-amber-500/40 bg-amber-500/5 text-amber-700 dark:text-amber-300 [&>svg]:text-amber-500',
      success: 'border-emerald-500/40 bg-emerald-500/5 text-emerald-700 dark:text-emerald-300 [&>svg]:text-emerald-500',
    })[props.variant],
)

const icon = computed(
  () => ({ destructive: AlertCircle, info: Info, warning: TriangleAlert, success: CheckCircle2, default: Info })[props.variant],
)
</script>

<template>
  <div
    role="alert"
    :class="cn('relative w-full rounded-lg border p-4 [&>svg+div]:translate-y-[-3px] [&>svg]:absolute [&>svg]:left-4 [&>svg]:top-4 [&>svg~*]:pl-7', styles, props.class)"
  >
    <component :is="icon" class="h-4 w-4" />
    <div>
      <div v-if="props.title" class="mb-1 text-sm font-medium leading-none">{{ props.title }}</div>
      <div v-if="props.description" class="text-sm opacity-90">{{ props.description }}</div>
      <slot />
    </div>
  </div>
</template>

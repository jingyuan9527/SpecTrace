<script setup lang="ts">
import { Dialog, Button } from '@/components/ui'

withDefaults(
  defineProps<{
    open: boolean
    title?: string
    description?: string
    confirmText?: string
    cancelText?: string
    destructive?: boolean
    loading?: boolean
  }>(),
  {
    title: '确认操作',
    confirmText: '确认',
    cancelText: '取消',
    destructive: false,
    loading: false,
  },
)
const emit = defineEmits<{ 'update:open': [value: boolean]; confirm: [] }>()
</script>

<template>
  <Dialog :open="open" :title="title" :description="description" class="max-w-md" @update:open="emit('update:open', $event)">
    <slot />
    <template #footer>
      <Button variant="outline" :disabled="loading" @click="emit('update:open', false)">{{ cancelText }}</Button>
      <Button :variant="destructive ? 'destructive' : 'default'" :disabled="loading" @click="emit('confirm')">
        {{ loading ? '处理中…' : confirmText }}
      </Button>
    </template>
  </Dialog>
</template>

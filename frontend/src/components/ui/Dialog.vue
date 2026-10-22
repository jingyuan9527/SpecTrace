<script setup lang="ts">
import {
  DialogClose,
  DialogContent,
  DialogDescription,
  DialogOverlay,
  DialogPortal,
  DialogRoot,
  DialogTitle,
  VisuallyHidden,
} from 'reka-ui'
import { X } from '@lucide/vue'
import { cn } from '@/lib/utils'

const props = withDefaults(
  defineProps<{
    open: boolean
    title?: string
    description?: string
    class?: string
    hideClose?: boolean
  }>(),
  { hideClose: false },
)
const emit = defineEmits<{ 'update:open': [value: boolean] }>()
</script>

<template>
  <DialogRoot :open="props.open" @update:open="emit('update:open', $event)">
    <DialogPortal>
      <DialogOverlay
        class="fixed inset-0 z-50 bg-black/70 backdrop-blur-[2px] data-[state=open]:animate-in data-[state=closed]:animate-out data-[state=closed]:fade-out-0 data-[state=open]:fade-in-0"
      />
      <DialogContent
        :class="
          cn(
            'fixed left-1/2 top-1/2 z-50 grid max-h-[90vh] w-[calc(100%-2rem)] max-w-lg -translate-x-1/2 -translate-y-1/2 gap-4 overflow-y-auto rounded-xl border bg-background p-6 shadow-lg duration-200 data-[state=open]:animate-in data-[state=closed]:animate-out data-[state=closed]:fade-out-0 data-[state=open]:fade-in-0 data-[state=closed]:zoom-out-95 data-[state=open]:zoom-in-95',
            props.class,
          )
        "
      >
        <div class="flex flex-col space-y-1.5 pr-8">
          <DialogTitle v-if="props.title" class="text-lg font-semibold leading-none tracking-tight">
            {{ props.title }}
          </DialogTitle>
          <VisuallyHidden v-else><DialogTitle>对话框</DialogTitle></VisuallyHidden>
          <DialogDescription v-if="props.description" class="text-sm text-muted-foreground">
            {{ props.description }}
          </DialogDescription>
          <!-- reka-ui 要求每个 Dialog 都有 Description，缺省时用隐藏文本满足可访问性 -->
          <VisuallyHidden v-else><DialogDescription>对话框内容</DialogDescription></VisuallyHidden>
        </div>

        <slot />

        <div v-if="$slots.footer" class="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
          <slot name="footer" />
        </div>

        <DialogClose
          v-if="!props.hideClose"
          class="absolute right-4 top-4 rounded-sm opacity-70 ring-offset-background transition-opacity hover:opacity-100 focus:outline-none focus:ring-1 focus:ring-ring disabled:pointer-events-none"
        >
          <X class="h-4 w-4" />
          <span class="sr-only">关闭</span>
        </DialogClose>
      </DialogContent>
    </DialogPortal>
  </DialogRoot>
</template>

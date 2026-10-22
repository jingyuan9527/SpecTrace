<script setup lang="ts">
import { DialogClose, DialogContent, DialogDescription, DialogOverlay, DialogPortal, DialogRoot, DialogTitle, VisuallyHidden } from 'reka-ui'
import { X } from '@lucide/vue'
import { computed } from 'vue'
import { cn } from '@/lib/utils'

const props = withDefaults(
  defineProps<{ open: boolean; title?: string; description?: string; side?: 'left' | 'right'; class?: string }>(),
  { side: 'left' },
)
const emit = defineEmits<{ 'update:open': [value: boolean] }>()

const sideClass = computed(() =>
  props.side === 'left'
    ? 'inset-y-0 left-0 h-full w-[280px] border-r data-[state=open]:slide-in-from-left data-[state=closed]:slide-out-to-left'
    : 'inset-y-0 right-0 h-full w-[280px] border-l data-[state=open]:slide-in-from-right data-[state=closed]:slide-out-to-right',
)
</script>

<template>
  <DialogRoot :open="props.open" @update:open="emit('update:open', $event)">
    <DialogPortal>
      <DialogOverlay class="fixed inset-0 z-50 bg-black/60 data-[state=open]:animate-in data-[state=closed]:animate-out data-[state=closed]:fade-out-0 data-[state=open]:fade-in-0" />
      <DialogContent
        :class="
          cn(
            'fixed z-50 flex flex-col gap-4 overflow-y-auto bg-background p-5 shadow-lg transition ease-in-out data-[state=open]:animate-in data-[state=closed]:animate-out data-[state=closed]:duration-300 data-[state=open]:duration-300',
            sideClass,
            props.class,
          )
        "
      >
        <div class="flex flex-col space-y-1">
          <DialogTitle v-if="props.title" class="text-base font-semibold">{{ props.title }}</DialogTitle>
          <VisuallyHidden v-else><DialogTitle>菜单</DialogTitle></VisuallyHidden>
          <DialogDescription v-if="props.description" class="text-sm text-muted-foreground">{{ props.description }}</DialogDescription>
          <!-- 与 Dialog 一致：缺少描述时补隐藏文本，避免 reka-ui 运行时告警 -->
          <VisuallyHidden v-else><DialogDescription>侧边面板内容</DialogDescription></VisuallyHidden>
        </div>
        <slot />
        <DialogClose class="absolute right-4 top-4 rounded-sm opacity-70 transition-opacity hover:opacity-100 focus:outline-none">
          <X class="h-4 w-4" />
          <span class="sr-only">关闭</span>
        </DialogClose>
      </DialogContent>
    </DialogPortal>
  </DialogRoot>
</template>

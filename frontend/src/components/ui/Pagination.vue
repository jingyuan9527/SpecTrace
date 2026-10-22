<script setup lang="ts">
import { ChevronLeft, ChevronRight } from '@lucide/vue'
import { computed } from 'vue'
import Button from './Button.vue'

const props = withDefaults(defineProps<{ page: number; pageSize: number; total: number; class?: string }>(), {
  pageSize: 10,
})
const emit = defineEmits<{ 'update:page': [value: number] }>()

const pageCount = computed(() => Math.max(1, Math.ceil(props.total / props.pageSize)))

/** 页码窗口：始终保留首尾页，中间用省略号压缩，避免页码撑爆移动端。 */
const pages = computed<(number | '…')[]>(() => {
  const count = pageCount.value
  const current = props.page
  if (count <= 7) return Array.from({ length: count }, (_, i) => i + 1)
  const result: (number | '…')[] = [1]
  const start = Math.max(2, current - 1)
  const end = Math.min(count - 1, current + 1)
  if (start > 2) result.push('…')
  for (let i = start; i <= end; i++) result.push(i)
  if (end < count - 1) result.push('…')
  result.push(count)
  return result
})

function go(page: number) {
  const next = Math.min(Math.max(1, page), pageCount.value)
  if (next !== props.page) emit('update:page', next)
}
</script>

<template>
  <div :class="['flex flex-col items-center justify-between gap-3 sm:flex-row', props.class]">
    <p class="text-sm text-muted-foreground">
      共 {{ props.total }} 条 · 第 {{ props.page }} / {{ pageCount }} 页
    </p>
    <div class="flex items-center gap-1">
      <Button variant="outline" size="icon-sm" :disabled="props.page <= 1" @click="go(props.page - 1)">
        <ChevronLeft />
      </Button>
      <template v-for="(item, index) in pages" :key="`${item}-${index}`">
        <span v-if="item === '…'" class="px-1 text-sm text-muted-foreground">…</span>
        <Button
          v-else
          :variant="item === props.page ? 'default' : 'outline'"
          size="icon-sm"
          class="tabular-nums"
          @click="go(item as number)"
        >
          {{ item }}
        </Button>
      </template>
      <Button variant="outline" size="icon-sm" :disabled="props.page >= pageCount" @click="go(props.page + 1)">
        <ChevronRight />
      </Button>
    </div>
  </div>
</template>

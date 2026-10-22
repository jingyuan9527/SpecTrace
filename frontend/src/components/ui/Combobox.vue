<script setup lang="ts">
import { PopoverContent, PopoverPortal, PopoverRoot, PopoverTrigger } from 'reka-ui'
import { Check, ChevronDown, Search } from '@lucide/vue'
import { computed, ref } from 'vue'
import { cn } from '@/lib/utils'
import type { SelectOption } from './types'

const props = withDefaults(
  defineProps<{
    modelValue?: string | number | null
    options: SelectOption[]
    placeholder?: string
    searchPlaceholder?: string
    disabled?: boolean
    id?: string
    class?: string
  }>(),
  { placeholder: '请选择', searchPlaceholder: '搜索…', disabled: false },
)
const emit = defineEmits<{ 'update:modelValue': [value: string | number | undefined] }>()

const open = ref(false)
const keyword = ref('')

const selected = computed(() => props.options.find((o) => o.value === props.modelValue))
const filtered = computed(() => {
  const k = keyword.value.trim().toLowerCase()
  if (!k) return props.options
  return props.options.filter((o) => o.label.toLowerCase().includes(k))
})

function select(option: SelectOption) {
  emit('update:modelValue', option.value)
  open.value = false
  keyword.value = ''
}
</script>

<template>
  <PopoverRoot v-model:open="open">
    <PopoverTrigger as-child :disabled="props.disabled">
      <button
        :id="props.id"
        type="button"
        :class="
          cn(
            'flex h-9 w-full items-center justify-between gap-2 whitespace-nowrap rounded-md border border-input bg-transparent px-3 py-2 text-sm shadow-sm ring-offset-background focus:outline-none focus:ring-1 focus:ring-ring disabled:cursor-not-allowed disabled:opacity-50',
            props.class,
          )
        "
      >
        <span :class="cn('truncate text-left', !selected && 'text-muted-foreground')">
          {{ selected?.label ?? props.placeholder }}
        </span>
        <ChevronDown class="h-4 w-4 shrink-0 opacity-50" />
      </button>
    </PopoverTrigger>
    <PopoverPortal>
      <PopoverContent
        align="start"
        :side-offset="4"
        class="z-50 w-[var(--reka-popover-trigger-width)] min-w-[12rem] overflow-hidden rounded-md border bg-popover p-0 text-popover-foreground shadow-md data-[state=open]:animate-in data-[state=closed]:animate-out data-[state=closed]:fade-out-0 data-[state=open]:fade-in-0 data-[state=closed]:zoom-out-95 data-[state=open]:zoom-in-95"
      >
        <div class="flex items-center gap-2 border-b px-3">
          <Search class="h-4 w-4 shrink-0 text-muted-foreground" />
          <input
            v-model="keyword"
            :placeholder="props.searchPlaceholder"
            class="flex h-9 w-full bg-transparent py-2 text-sm outline-none placeholder:text-muted-foreground"
            @keydown.esc="open = false"
          />
        </div>
        <div class="max-h-64 overflow-y-auto p-1">
          <button
            v-for="option in filtered"
            :key="option.value"
            type="button"
            :disabled="option.disabled"
            class="flex w-full items-center justify-between gap-2 rounded-sm px-2 py-1.5 text-left text-sm hover:bg-accent hover:text-accent-foreground disabled:pointer-events-none disabled:opacity-50"
            @click="select(option)"
          >
            <span class="truncate">{{ option.label }}</span>
            <Check v-if="option.value === props.modelValue" class="h-4 w-4 shrink-0" />
          </button>
          <p v-if="!filtered.length" class="px-2 py-6 text-center text-sm text-muted-foreground">无匹配项</p>
        </div>
      </PopoverContent>
    </PopoverPortal>
  </PopoverRoot>
</template>

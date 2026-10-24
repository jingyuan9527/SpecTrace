<script setup lang="ts">
import {
  SelectContent,
  SelectIcon,
  SelectItem,
  SelectItemIndicator,
  SelectItemText,
  SelectPortal,
  SelectRoot,
  SelectTrigger,
  SelectValue,
  SelectViewport,
} from 'reka-ui'
import { Check, ChevronDown, X } from '@lucide/vue'
import { computed } from 'vue'
import { cn } from '@/lib/utils'
import type { SelectOption } from './types'

const props = withDefaults(
  defineProps<{
    modelValue?: string | number | null
    options: SelectOption[]
    placeholder?: string
    disabled?: boolean
    id?: string
    class?: string
    /** 有值时展示清空按钮：筛选场景需要能回到「全部」语义 */
    clearable?: boolean
  }>(),
  { placeholder: '请选择', disabled: false, clearable: false },
)
const emit = defineEmits<{ 'update:modelValue': [value: string | number | undefined] }>()

// reka-ui 不允许 SelectItem 使用空字符串作为 value（空串用于清空选择）。
// 这里用内部哨兵值承载「全部」这类空值选项，对外仍暴露原始空字符串。
const EMPTY_VALUE = '__empty__'

function encode(value: string | number) {
  return value === '' ? EMPTY_VALUE : value
}

const modelValue = computed(() => (props.modelValue === '' ? EMPTY_VALUE : (props.modelValue ?? undefined)))

const hasValue = computed(
  () => props.modelValue !== undefined && props.modelValue !== null && props.modelValue !== '',
)

function clear(event: MouseEvent) {
  // 阻止冒泡：SelectTrigger 的 click 会展开下拉
  event.stopPropagation()
  emit('update:modelValue', undefined)
}

function decode(value: unknown) {
  return value === EMPTY_VALUE ? '' : (value as string | number | undefined)
}
</script>

<template>
  <SelectRoot
    :model-value="modelValue"
    :disabled="props.disabled"
    @update:model-value="emit('update:modelValue', decode($event))"
  >
    <SelectTrigger
      :id="props.id"
      :class="
        cn(
          'flex h-9 w-full items-center justify-between whitespace-nowrap rounded-md border border-input bg-transparent px-3 py-2 text-sm shadow-sm ring-offset-background placeholder:text-muted-foreground focus:outline-none focus:ring-1 focus:ring-ring disabled:cursor-not-allowed disabled:opacity-50 [&>span]:line-clamp-1',
          props.class,
        )
      "
    >
      <SelectValue :placeholder="props.placeholder" />
      <span class="flex items-center gap-0.5">
        <!-- SelectTrigger 本身是 button，清空用 span 承载避免按钮嵌套 -->
        <span
          v-if="props.clearable && hasValue"
          role="button"
          tabindex="-1"
          aria-label="清空选择"
          class="rounded-sm p-0.5 text-muted-foreground hover:bg-accent hover:text-foreground"
          @click.stop="clear"
        >
          <X class="h-3.5 w-3.5" />
        </span>
        <SelectIcon as-child>
          <ChevronDown class="h-4 w-4 opacity-50" />
        </SelectIcon>
      </span>
    </SelectTrigger>
    <SelectPortal>
      <SelectContent
        position="popper"
        :side-offset="4"
        class="relative z-50 max-h-80 min-w-[8rem] overflow-hidden rounded-md border bg-popover text-popover-foreground shadow-md data-[state=open]:animate-in data-[state=closed]:animate-out data-[state=closed]:fade-out-0 data-[state=open]:fade-in-0 data-[state=closed]:zoom-out-95 data-[state=open]:zoom-in-95"
      >
        <SelectViewport class="p-1">
          <SelectItem
            v-for="option in props.options"
            :key="option.value"
            :value="encode(option.value)"
            :disabled="option.disabled"
            class="relative flex w-full cursor-default select-none items-center rounded-sm py-1.5 pl-2 pr-8 text-sm outline-none focus:bg-accent focus:text-accent-foreground data-[disabled]:pointer-events-none data-[disabled]:opacity-50"
          >
            <SelectItemText>{{ option.label }}</SelectItemText>
            <span class="absolute right-2 flex h-3.5 w-3.5 items-center justify-center">
              <SelectItemIndicator><Check class="h-4 w-4" /></SelectItemIndicator>
            </span>
          </SelectItem>
        </SelectViewport>
      </SelectContent>
    </SelectPortal>
  </SelectRoot>
</template>

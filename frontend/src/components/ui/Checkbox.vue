<script setup lang="ts">
import { CheckboxIndicator, CheckboxRoot } from 'reka-ui'
import { Check } from '@lucide/vue'
import { cn } from '@/lib/utils'

const props = withDefaults(
  defineProps<{ modelValue?: boolean; disabled?: boolean; class?: string; id?: string }>(),
  { modelValue: false, disabled: false },
)
const emit = defineEmits<{ 'update:modelValue': [value: boolean] }>()
</script>

<template>
  <!-- reka-ui 的受控属性是 modelValue（Radix 的 checked 在这里不存在），写错会退化成非受控且点不动 -->
  <CheckboxRoot
    :id="props.id"
    :model-value="props.modelValue"
    :disabled="props.disabled"
    :class="
      cn(
        'peer h-4 w-4 shrink-0 rounded-sm border border-primary shadow focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring disabled:cursor-not-allowed disabled:opacity-50 data-[state=checked]:bg-primary data-[state=checked]:text-primary-foreground',
        props.class,
      )
    "
    @update:model-value="emit('update:modelValue', $event === true)"
  >
    <CheckboxIndicator class="flex items-center justify-center text-current">
      <Check class="h-3.5 w-3.5" />
    </CheckboxIndicator>
  </CheckboxRoot>
</template>

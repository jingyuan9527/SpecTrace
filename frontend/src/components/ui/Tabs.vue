<script setup lang="ts">
import { TabsList, TabsRoot, TabsTrigger } from 'reka-ui'
import { cn } from '@/lib/utils'
import type { TabItem } from './types'

const props = defineProps<{ modelValue: string; items: TabItem[]; class?: string }>()
const emit = defineEmits<{ 'update:modelValue': [value: string | number] }>()
</script>

<template>
  <TabsRoot :model-value="props.modelValue" @update:model-value="emit('update:modelValue', $event)">
    <TabsList
      :class="
        cn(
          'inline-flex h-9 items-center justify-center rounded-lg bg-muted p-1 text-muted-foreground',
          props.class,
        )
      "
    >
      <TabsTrigger
        v-for="item in props.items"
        :key="item.value"
        :value="item.value"
        class="inline-flex items-center justify-center whitespace-nowrap rounded-md px-3 py-1 text-sm font-medium ring-offset-background transition-all focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring disabled:pointer-events-none disabled:opacity-50 data-[state=active]:bg-background data-[state=active]:text-foreground data-[state=active]:shadow"
      >
        {{ item.label }}
      </TabsTrigger>
    </TabsList>
    <slot />
  </TabsRoot>
</template>

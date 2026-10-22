<script setup lang="ts">
import {
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuPortal,
  DropdownMenuRoot,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from 'reka-ui'
import type { MenuItem } from './types'
import { cn } from '@/lib/utils'

const props = defineProps<{ items: MenuItem[]; align?: 'start' | 'center' | 'end'; class?: string }>()
</script>

<template>
  <DropdownMenuRoot>
    <DropdownMenuTrigger as-child>
      <slot name="trigger" />
    </DropdownMenuTrigger>
    <DropdownMenuPortal>
      <DropdownMenuContent
        :align="props.align ?? 'end'"
        :side-offset="6"
        :class="
          cn(
            'z-50 min-w-[10rem] overflow-hidden rounded-md border bg-popover p-1 text-popover-foreground shadow-md data-[state=open]:animate-in data-[state=closed]:animate-out data-[state=closed]:fade-out-0 data-[state=open]:fade-in-0 data-[state=closed]:zoom-out-95 data-[state=open]:zoom-in-95',
            props.class,
          )
        "
      >
        <template v-for="item in props.items" :key="item.label">
          <DropdownMenuSeparator v-if="item.separatorBefore" class="-mx-1 my-1 h-px bg-border" />
          <DropdownMenuItem
            :class="
              cn(
                'relative flex cursor-pointer select-none items-center gap-2 rounded-sm px-2 py-1.5 text-sm outline-none transition-colors focus:bg-accent focus:text-accent-foreground',
                item.destructive && 'text-destructive focus:bg-destructive/10 focus:text-destructive',
              )
            "
            @select="item.onSelect()"
          >
            <component :is="item.icon" v-if="item.icon" class="h-4 w-4" />
            {{ item.label }}
          </DropdownMenuItem>
        </template>
      </DropdownMenuContent>
    </DropdownMenuPortal>
  </DropdownMenuRoot>
</template>

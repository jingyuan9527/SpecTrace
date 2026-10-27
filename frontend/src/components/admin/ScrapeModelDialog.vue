<script setup lang="ts">
import { Loader2 } from '@lucide/vue'
import type { ScrapeTarget } from '@/composables/useScrapeDispatch'
import { Badge, Button, Combobox, Dialog, Label, Skeleton } from '@/components/ui'

/**
 * 发起抓取的统一弹窗：机型页、数据健康度、系列页共用。
 *
 * <p>三处原本各写一遍「目标列表 + 模型下拉 + 顺序提交」，文案与默认模型选择逻辑逐渐分叉；
 * 收成一个组件后，交互只在这里改。目标行右侧展示什么由 `meta` 决定：
 * 机型页看品牌（确认抓的是哪家），健康度与系列页看状态（确认抓的是草稿还是已上架）。</p>
 */
const props = withDefaults(defineProps<{
  open: boolean
  title: string
  description: string
  targets: ScrapeTarget[]
  modelId: number | null
  modelOptions: Array<{ label: string; value: string | number }>
  loading?: boolean
  submitting?: boolean
  meta?: 'brand' | 'status'
  emptyText?: string
  submitText?: string
}>(), {
  loading: false,
  submitting: false,
  meta: 'status',
  emptyText: '没有需要抓取的机型',
  submitText: '提交',
})

const emit = defineEmits<{
  'update:open': [value: boolean]
  'update:modelId': [value: number | null]
  confirm: []
}>()
</script>

<template>
  <Dialog
    :open="props.open"
    :title="props.title"
    :description="props.description"
    class="max-w-md"
    @update:open="emit('update:open', $event)"
  >
    <div class="space-y-4">
      <div v-if="props.loading" class="space-y-2">
        <Skeleton v-for="i in 3" :key="i" class="h-8" />
      </div>
      <template v-else>
        <div class="max-h-40 space-y-1 overflow-y-auto rounded-md border p-2">
          <div
            v-for="target in props.targets"
            :key="target.id"
            class="flex items-center justify-between gap-2 px-1 py-0.5 text-sm"
          >
            <span class="min-w-0 truncate font-medium" :title="target.name">{{ target.name }}</span>
            <span v-if="props.meta === 'brand'" class="shrink-0 text-xs text-muted-foreground">
              {{ target.brandName || '—' }}
            </span>
            <Badge v-else :variant="target.status === 1 ? 'secondary' : 'outline'">
              {{ target.status === 1 ? '已上架' : '草稿' }}
            </Badge>
          </div>
          <p v-if="!props.targets.length" class="px-1 py-2 text-sm text-muted-foreground">
            {{ props.emptyText }}
          </p>
        </div>
        <div class="space-y-1.5">
          <Label>AI 模型（可选）</Label>
          <Combobox
            :model-value="props.modelId"
            :options="props.modelOptions"
            placeholder="默认模型"
            search-placeholder="搜索供应商 / 模型…"
            @update:model-value="emit('update:modelId', typeof $event === 'number' ? $event : null)"
          />
          <p class="text-xs text-muted-foreground">
            选项为「供应商连接 / 模型名」，不选择时使用后台设置的默认模型
          </p>
        </div>
      </template>
    </div>
    <template #footer>
      <Button variant="outline" :disabled="props.submitting" @click="emit('update:open', false)">取消</Button>
      <Button :disabled="props.submitting || !props.targets.length" @click="emit('confirm')">
        <Loader2 v-if="props.submitting" class="animate-spin" />
        {{ props.submitting ? '提交中…' : props.submitText }}
      </Button>
    </template>
  </Dialog>
</template>

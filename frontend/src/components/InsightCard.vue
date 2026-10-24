<script setup lang="ts">
import { Loader2, Sparkles } from '@lucide/vue'
import { Badge, Button, Card, CardContent } from '@/components/ui'
import type { InsightResult } from '@/types/api'

/**
 * AI 文案卡片：对比差异摘要与世代回顾共用。
 *
 * <p>行为契约统一在 {@link '@/composables/useInsight'}：available=false 整块不渲染、
 * failed 降级文案、429 不重复报错。这里只负责展示与「按需生成」入口。</p>
 */
const props = withDefaults(
  defineProps<{
    insight: InsightResult
    generating: boolean
    title: string
    buttonText: string
    /** 生成结果脚注与各状态文案，两处页面语义不同 */
    footnote: string
    failedText: string
    hint: string
  }>(),
  {},
)

const emit = defineEmits<{ generate: [] }>()
</script>

<template>
  <Card v-if="props.insight.available" class="border-dashed">
    <CardContent class="space-y-3 p-4">
      <div class="flex flex-wrap items-center justify-between gap-2">
        <div class="flex items-center gap-1.5 text-sm font-medium">
          <Sparkles class="h-4 w-4 text-primary" />
          {{ props.title }}
          <Badge v-if="props.insight.cached" variant="secondary">缓存</Badge>
        </div>
        <Button
          v-if="!props.insight.summary && !props.insight.failed"
          size="sm"
          variant="outline"
          :disabled="props.generating"
          @click="emit('generate')"
        >
          <Loader2 v-if="props.generating" class="animate-spin" />
          {{ props.generating ? '生成中…' : props.buttonText }}
        </Button>
      </div>

      <template v-if="props.insight.summary">
        <p class="text-sm leading-relaxed">{{ props.insight.summary }}</p>
        <ul v-if="props.insight.points?.length" class="space-y-1">
          <li v-for="(point, index) in props.insight.points" :key="index" class="text-sm text-muted-foreground">
            · {{ point }}
          </li>
        </ul>
        <p class="text-xs text-muted-foreground">{{ props.footnote }}</p>
      </template>

      <p v-else-if="props.insight.failed" class="text-sm text-muted-foreground">{{ props.failedText }}</p>

      <p v-else class="text-sm text-muted-foreground">{{ props.hint }}</p>
    </CardContent>
  </Card>
</template>

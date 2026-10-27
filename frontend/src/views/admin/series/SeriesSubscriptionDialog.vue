<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { Loader2 } from '@lucide/vue'
import { toast } from 'vue-sonner'
import { adminApi } from '@/api'
import { useAiModels } from '@/composables/useAiModels'
import type { DeviceSeries } from '@/types/api'
import { Button, Combobox, Dialog, Label, Select, Skeleton, Switch } from '@/components/ui'

/**
 * 系列订阅抓取（T88）：到期自动创建系列发现任务。
 *
 * <p>打开即拉取已有配置与模型列表；没有配置时用默认模型，用户直接保存即可生效。</p>
 */
const props = defineProps<{
  open: boolean
  series: DeviceSeries | null
}>()

const emit = defineEmits<{
  'update:open': [value: boolean]
}>()

const { options: aiModelOptions, load: ensureAiModels } = useAiModels()

const loading = ref(false)
const saving = ref(false)
const form = reactive({ intervalMonths: 3, aiModelId: null as number | null, enabled: true })

const intervalOptions = [
  { label: '每 1 个月', value: '1' },
  { label: '每 2 个月', value: '2' },
  { label: '每 3 个月', value: '3' },
  { label: '每 6 个月', value: '6' },
  { label: '每 12 个月', value: '12' },
]

watch(() => [props.open, props.series?.id], async () => {
  if (!props.open || !props.series?.id) return
  const seriesId = props.series.id
  form.intervalMonths = 3
  form.aiModelId = null
  form.enabled = true
  loading.value = true
  try {
    const [existing, models] = await Promise.all([
      adminApi.seriesSubscription(seriesId),
      ensureAiModels(),
    ])
    if (existing) {
      form.intervalMonths = existing.intervalMonths
      form.aiModelId = existing.aiModelId ?? null
      form.enabled = existing.enabled === 1
    } else {
      form.aiModelId = models.find((m) => m.isDefault === 1)?.id ?? null
    }
  } finally {
    loading.value = false
  }
})

async function save() {
  const series = props.series
  if (!series || saving.value) return
  saving.value = true
  try {
    await adminApi.saveSeriesSubscription(series.id, {
      // Select 的值是字符串，落库前转回数字
      intervalMonths: Number(form.intervalMonths),
      aiModelId: form.aiModelId ?? undefined,
      enabled: form.enabled,
    })
    toast.success(form.enabled ? `已订阅「${series.name}」，到期自动发起系列发现` : '订阅已暂停')
    emit('update:open', false)
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <Dialog
    :open="props.open"
    title="订阅抓取"
    :description="`对「${props.series?.name ?? ''}」按周期自动创建系列发现任务，结果照常进入人工审核`"
    class="max-w-md"
    @update:open="emit('update:open', $event)"
  >
    <div v-if="loading" class="space-y-2">
      <Skeleton v-for="i in 2" :key="i" class="h-9" />
    </div>
    <div v-else class="space-y-4">
      <div class="space-y-1.5">
        <Label>抓取周期</Label>
        <Select v-model="form.intervalMonths" :options="intervalOptions" />
      </div>
      <div class="space-y-1.5">
        <Label>AI 模型（可选）</Label>
        <Combobox
          v-model="form.aiModelId"
          :options="aiModelOptions"
          placeholder="默认模型"
          search-placeholder="搜索供应商 / 模型…"
        />
      </div>
      <div class="flex items-center justify-between">
        <Label for="subscription-enabled">启用订阅</Label>
        <Switch id="subscription-enabled" v-model="form.enabled" />
      </div>
      <p class="text-xs text-muted-foreground">
        每天凌晨检查到期订阅；系列名即发现关键词，发现结果照常进入人工审核。
      </p>
    </div>
    <template #footer>
      <Button variant="outline" :disabled="saving" @click="emit('update:open', false)">取消</Button>
      <Button :disabled="saving" @click="save">
        <Loader2 v-if="saving" class="animate-spin" />
        保存订阅
      </Button>
    </template>
  </Dialog>
</template>

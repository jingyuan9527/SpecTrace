<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { toast } from 'vue-sonner'
import { adminApi } from '@/api'
import type { DeviceSeries } from '@/types/api'
import { Button, Dialog, Label, Select } from '@/components/ui'

/**
 * 合并系列：把来源系列下的机型迁到目标系列，然后删除来源系列。
 *
 * <p>只列同品牌系列——跨品牌合并会让机型与系列归属不一致，后端也会拒绝。</p>
 */
const props = defineProps<{
  open: boolean
  source: DeviceSeries | null
  seriesList: DeviceSeries[]
}>()

const emit = defineEmits<{
  'update:open': [value: boolean]
  merged: []
}>()

const targetId = ref<number>()
const merging = ref(false)

const options = computed(() =>
  props.seriesList
    .filter((item) => item.id && item.brandId === props.source?.brandId && item.id !== props.source?.id)
    .map((item) => ({ label: item.name, value: item.id as number })),
)

watch(() => props.open, (open) => {
  if (open) targetId.value = undefined
})

async function confirm() {
  if (!props.source?.id || !targetId.value) {
    toast.warning('请选择目标系列')
    return
  }
  merging.value = true
  try {
    const moved = await adminApi.mergeSeries(props.source.id, targetId.value)
    toast.success(`已合并，迁移 ${moved} 台机型`)
    emit('update:open', false)
    emit('merged')
  } finally {
    merging.value = false
  }
}
</script>

<template>
  <Dialog
    :open="props.open"
    title="合并系列"
    class="max-w-md"
    @update:open="emit('update:open', $event)"
  >
    <div class="space-y-3">
      <p class="text-sm text-muted-foreground">
        把「{{ props.source?.name }}」下的机型迁移到目标系列，然后删除当前系列。
      </p>
      <div class="space-y-1.5">
        <Label>目标系列</Label>
        <Select v-model="targetId" :options="options" placeholder="选择同品牌系列" />
      </div>
      <p v-if="!options.length" class="text-xs text-destructive">该品牌下没有其它可合并的系列。</p>
    </div>
    <template #footer>
      <Button variant="outline" :disabled="merging" @click="emit('update:open', false)">取消</Button>
      <Button :disabled="merging || !options.length" @click="confirm">
        {{ merging ? '合并中…' : '合并' }}
      </Button>
    </template>
  </Dialog>
</template>

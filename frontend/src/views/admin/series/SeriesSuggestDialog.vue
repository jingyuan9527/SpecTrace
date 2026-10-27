<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { Loader2, Sparkles } from '@lucide/vue'
import { toast } from 'vue-sonner'
import { adminApi } from '@/api'
import type { Brand, SeriesSuggestion } from '@/types/api'
import { Badge, Button, Checkbox, Dialog, Select, Skeleton } from '@/components/ui'

/**
 * AI 推荐系列：按品牌生成产品线候选，勾选后批量新建。
 *
 * <p>排序号以打开时的现有数量为基准顺延，避免新建的系列插到已有系列前面。</p>
 */
const props = defineProps<{
  open: boolean
  brands: Brand[]
  /** 打开时该品牌已有系列数，作为排序号基准 */
  sortOrderBase: number
  defaultBrandId?: number
}>()

const emit = defineEmits<{
  'update:open': [value: boolean]
  /** 创建完成后回传品牌 id，父级据此切换筛选并刷新列表 */
  created: [brandId: number]
}>()

const brandId = ref<number>()
const suggestions = ref<SeriesSuggestion[]>([])
const selected = ref<string[]>([])
const loading = ref(false)
const creating = ref(false)

const brandOptions = computed(() => props.brands.map((b) => ({ label: b.name, value: b.id })))
const creatable = computed(() => suggestions.value.filter((s) => !s.existing))

watch(() => props.open, (open) => {
  if (!open) return
  brandId.value = props.defaultBrandId ?? props.brands[0]?.id
  suggestions.value = []
  selected.value = []
})

async function generate() {
  if (!brandId.value) {
    toast.warning('请先选择品牌')
    return
  }
  loading.value = true
  try {
    suggestions.value = await adminApi.suggestSeries(brandId.value)
    // 默认全选可创建项；已存在项禁选
    selected.value = suggestions.value.filter((s) => !s.existing).map((s) => s.name)
    if (!suggestions.value.length) toast.warning('AI 未给出候选，请重试或手动新建')
  } finally {
    loading.value = false
  }
}

function toggle(name: string, checked: boolean) {
  if (checked) {
    if (!selected.value.includes(name)) selected.value.push(name)
  } else {
    selected.value = selected.value.filter((n) => n !== name)
  }
}

async function createSelected() {
  if (!brandId.value || !selected.value.length) return
  const targetBrandId = brandId.value
  creating.value = true
  try {
    // 逐个走既有 save 校验（同名拦截），排序号顺延
    let created = 0
    for (const name of selected.value) {
      await adminApi.saveSeries({
        brandId: targetBrandId,
        name,
        sortOrder: props.sortOrderBase + 1 + created,
        status: 1,
      })
      created++
    }
    toast.success(`已创建 ${created} 个系列`)
    emit('update:open', false)
    emit('created', targetBrandId)
  } finally {
    creating.value = false
  }
}
</script>

<template>
  <Dialog
    :open="props.open"
    title="AI 推荐系列"
    description="AI 按品牌产品线给出候选系列名，勾选后一键创建；同名已存在会标注且不可选"
    class="max-w-xl"
    @update:open="emit('update:open', $event)"
  >
    <div class="space-y-3">
      <div class="flex items-center gap-2">
        <Select v-model="brandId" :options="brandOptions" placeholder="选择品牌" class="flex-1" />
        <Button variant="outline" size="sm" :disabled="loading" @click="generate">
          <Loader2 v-if="loading" class="animate-spin" />
          <Sparkles v-else />
          生成推荐
        </Button>
      </div>

      <div v-if="loading" class="space-y-2 py-2">
        <Skeleton v-for="i in 5" :key="i" class="h-12" />
      </div>

      <template v-else-if="suggestions.length">
        <div class="max-h-80 space-y-1 overflow-y-auto rounded-md border p-2">
          <label
            v-for="item in suggestions"
            :key="item.name"
            class="flex items-start gap-2.5 rounded-sm px-2 py-1.5 hover:bg-accent"
            :class="item.existing && 'cursor-default opacity-60'"
          >
            <Checkbox
              class="mt-0.5"
              :model-value="selected.includes(item.name)"
              :disabled="item.existing"
              @update:model-value="toggle(item.name, $event)"
            />
            <span class="min-w-0">
              <span class="flex flex-wrap items-center gap-2">
                <span class="text-sm font-medium">{{ item.name }}</span>
                <Badge v-if="item.existing" variant="secondary">已存在</Badge>
              </span>
              <span v-if="item.reason" class="mt-0.5 block text-xs text-muted-foreground">{{ item.reason }}</span>
            </span>
          </label>
        </div>
        <p class="text-xs text-muted-foreground">
          已选 {{ selected.length }} / 可创建 {{ creatable.length }} 个
        </p>
      </template>

      <p v-else class="rounded-md bg-muted/60 px-3 py-2 text-xs text-muted-foreground">
        选择品牌后点击「生成推荐」，AI 会给出该品牌的产品线划分候选。
      </p>
    </div>
    <template #footer>
      <Button variant="outline" :disabled="creating" @click="emit('update:open', false)">取消</Button>
      <Button :disabled="creating || !selected.length" @click="createSelected">
        <Loader2 v-if="creating" class="animate-spin" />
        {{ creating ? '创建中…' : `创建所选（${selected.length}）` }}
      </Button>
    </template>
  </Dialog>
</template>

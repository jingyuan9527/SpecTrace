<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import type * as PinyinPro from 'pinyin-pro'
import { Search, Smartphone } from '@lucide/vue'
import { publicApi } from '@/api'
import { Input } from '@/components/ui'
import ImageWithFallback from '@/components/ImageWithFallback.vue'

/**
 * 机型搜索框：输入即联想。
 *
 * 联想来源两路合并：
 * 1. 本地索引（最多 500 台）负责拼音全拼/首字母匹配，输入 `pg` 可命中「苹果」；
 * 2. 服务端 /devices/suggest 兜底中文关键词与超出本地索引的机型。
 *
 * 拼音词典约 300KB，随包静态引入会拖慢首页加载，因此在首次聚焦时动态加载，
 * 本地索引与词典都在那一刻才构建；远程联想在此之前一直可用。
 */
interface SuggestDevice {
  id: number
  name: string
  brandName?: string | null
  thumbnail?: string | null
  keywordText?: string
}

const props = withDefaults(defineProps<{ modelValue: string; placeholder?: string; inputClass?: string }>(), {
  placeholder: '搜索机型，如 iPhone 17',
})

const emit = defineEmits<{
  'update:modelValue': [value: string]
  submit: [value: string]
  select: [device: SuggestDevice]
}>()

const rootRef = ref<HTMLElement>()
const open = ref(false)
const activeIndex = ref(-1)
const remoteMatches = ref<SuggestDevice[]>([])
const index = ref<SuggestDevice[]>([])
const indexReady = ref(false)

let indexLoading = false
let debounceTimer: number | undefined
let requestSeq = 0

let pinyinFn: typeof PinyinPro.pinyin | null = null
async function ensurePinyin() {
  if (!pinyinFn) {
    pinyinFn = (await import('pinyin-pro')).pinyin
  }
  return pinyinFn
}

/** 拼音全拼 + 首字母 + 原文拼成一条可搜索文本，避免每次按键重复转换。 */
function buildKeywordText(name: string, brandName: string | null | undefined, pinyin: typeof PinyinPro.pinyin) {
  const text = `${name} ${brandName ?? ''}`
  const full = pinyin(text, { toneType: 'none', type: 'array' }).join('').toLowerCase()
  const first = pinyin(text, { pattern: 'first', toneType: 'none', type: 'array' }).join('').toLowerCase()
  return `${text} ${full} ${first}`.toLowerCase()
}

async function loadIndex() {
  if (indexLoading) return
  indexLoading = true
  try {
    const [res, brands, pinyin] = await Promise.all([
      publicApi.devices({ current: 1, size: 500 }),
      publicApi.brands(),
      ensurePinyin(),
    ])
    const brandNames = new Map<number, string>(brands.map((brand: any) => [brand.id, brand.name]))
    index.value = (res.records as any[]).map((device) => {
      const brandName = brandNames.get(device.brandId) ?? ''
      return { ...device, brandName, keywordText: buildKeywordText(device.name, brandName, pinyin) }
    })
  } finally {
    indexReady.value = true
  }
}

const localMatches = computed(() => {
  const keyword = props.modelValue.trim().toLowerCase()
  if (!keyword) return []
  return index.value.filter((device) => device.keywordText?.includes(keyword))
})

const suggestions = computed(() => {
  const merged = new Map<number, SuggestDevice>()
  for (const device of localMatches.value) merged.set(device.id, device)
  for (const device of remoteMatches.value) {
    if (!merged.has(device.id)) merged.set(device.id, device)
  }
  return [...merged.values()].slice(0, 8)
})

async function fetchRemote(keyword: string) {
  const seq = ++requestSeq
  try {
    const res = await publicApi.suggest(keyword)
    // 丢弃过期响应，避免快速输入时出现闪烁的旧结果
    if (seq === requestSeq) remoteMatches.value = res
  } catch {
    if (seq === requestSeq) remoteMatches.value = []
  }
}

function onInput(value: string) {
  emit('update:modelValue', value)
  activeIndex.value = -1
  open.value = true
  if (debounceTimer) window.clearTimeout(debounceTimer)
  const keyword = value.trim()
  if (!keyword) {
    remoteMatches.value = []
    return
  }
  debounceTimer = window.setTimeout(() => fetchRemote(keyword), 200)
}

function move(step: number) {
  if (!suggestions.value.length) return
  open.value = true
  const total = suggestions.value.length
  activeIndex.value = (activeIndex.value + step + total) % total
}

function choose(device: SuggestDevice) {
  emit('update:modelValue', device.name)
  emit('select', device)
  open.value = false
  activeIndex.value = -1
}

function onEnter() {
  if (open.value && activeIndex.value >= 0 && suggestions.value[activeIndex.value]) {
    choose(suggestions.value[activeIndex.value])
    return
  }
  open.value = false
  emit('submit', props.modelValue)
}

function onFocus() {
  // 首次交互才构建本地拼音索引：词典与 500 台机型列表都在此刻才拉取
  if (!indexReady.value) {
    loadIndex()
  }
  if (props.modelValue.trim() && suggestions.value.length) open.value = true
}

function onDocumentPointerDown(event: MouseEvent) {
  if (!rootRef.value?.contains(event.target as Node)) open.value = false
}

onMounted(() => {
  document.addEventListener('pointerdown', onDocumentPointerDown)
})

onBeforeUnmount(() => {
  document.removeEventListener('pointerdown', onDocumentPointerDown)
  if (debounceTimer) window.clearTimeout(debounceTimer)
})

watch(
  () => props.modelValue,
  (value) => {
    if (!value.trim()) remoteMatches.value = []
  },
)
</script>

<template>
  <div ref="rootRef" class="relative">
    <Search class="pointer-events-none absolute left-3 top-1/2 z-10 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
    <Input
      :model-value="modelValue"
      :class="inputClass"
      class="pl-9"
      :placeholder="placeholder"
      role="combobox"
      aria-autocomplete="list"
      :aria-expanded="open && suggestions.length > 0"
      @update:model-value="onInput"
      @focus="onFocus"
      @keydown.down.prevent="move(1)"
      @keydown.up.prevent="move(-1)"
      @keydown.enter.prevent="onEnter"
      @keydown.esc="open = false"
    />

    <div
      v-if="open && suggestions.length"
      class="absolute left-0 right-0 top-full z-30 mt-1 max-h-80 overflow-y-auto rounded-md border bg-popover p-1 shadow-lg"
      role="listbox"
    >
      <button
        v-for="(device, index) in suggestions"
        :key="device.id"
        type="button"
        role="option"
        :aria-selected="index === activeIndex"
        class="flex w-full items-center gap-2 rounded-sm px-2 py-1.5 text-left text-sm transition-colors hover:bg-accent"
        :class="index === activeIndex ? 'bg-accent' : ''"
        @mouseenter="activeIndex = index"
        @click="choose(device)"
      >
        <ImageWithFallback :src="device.thumbnail" :alt="device.name" img-class="h-8 max-w-10 object-contain">
          <template #fallback><Smartphone class="h-4 w-4 text-muted-foreground/40" /></template>
        </ImageWithFallback>
        <span class="min-w-0 flex-1 truncate">{{ device.name }}</span>
        <span class="shrink-0 text-xs text-muted-foreground">{{ device.brandName }}</span>
      </button>
    </div>
  </div>
</template>

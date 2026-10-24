<script setup lang="ts">
import { computed } from 'vue'
import type { SeriesTrendPoint } from '@/types/api'

/**
 * 系列总览卡片用的迷你趋势线：单条 polyline + 原生 title 提示。
 * 刻意不用 ECharts——机型库是首屏页面，为几十像素的 sparkline 拉起图表库得不偿失（T51/T52 瘦身口径）。
 */
const props = defineProps<{
  points: SeriesTrendPoint[]
  unit?: string | null
}>()

const W = 100
const H = 28
const PAD = 3

const coords = computed(() => {
  const list = props.points
  if (!list.length) return []
  const values = list.map((point) => point.value)
  const min = Math.min(...values)
  const max = Math.max(...values)
  const span = max - min || 1
  return list.map((point, index) => ({
    x: list.length === 1 ? W / 2 : PAD + (index * (W - PAD * 2)) / (list.length - 1),
    y: H - PAD - ((point.value - min) / span) * (H - PAD * 2),
    point,
  }))
})

// 单点画不出折线，退化为短横线占位；preserveAspectRatio=none 下用非缩放描边保持线宽
const linePoints = computed(() => {
  if (coords.value.length === 1) {
    const only = coords.value[0]
    return `${only.x - 1.5},${only.y} ${only.x + 1.5},${only.y}`
  }
  return coords.value.map((c) => `${c.x.toFixed(1)},${c.y.toFixed(1)}`).join(' ')
})

const tip = computed(() => {
  const list = props.points
  if (!list.length) return ''
  if (list.length === 1) return `${list[0].year} · ${list[0].deviceName}：${list[0].value}${props.unit ?? ''}`
  const first = list[0]
  const last = list[list.length - 1]
  return `${first.year} ${first.value}${props.unit ?? ''} → ${last.year} ${last.value}${props.unit ?? ''}`
})
</script>

<template>
  <svg
    v-if="coords.length"
    :viewBox="`0 0 ${W} ${H}`"
    class="h-7 w-full"
    preserveAspectRatio="none"
    aria-hidden="true"
  >
    <polyline
      :points="linePoints"
      fill="none"
      stroke="currentColor"
      stroke-width="1.5"
      stroke-linejoin="round"
      stroke-linecap="round"
      vector-effect="non-scaling-stroke"
    >
      <title>{{ tip }}</title>
    </polyline>
  </svg>
</template>

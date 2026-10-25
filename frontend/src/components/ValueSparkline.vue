<script setup lang="ts">
import { computed } from 'vue'

/**
 * 参数卡的迷你折线：入参是按世代顺序排列的数值序列（null 表示该代缺失，画断点）。
 * 与 TrendSparkline 同一视觉语言（100×28、currentColor、非缩放描边），刻意不引 ECharts——
 * 卡片网格一次渲染几十条 sparkline，图表库开销不可接受。
 */
const props = defineProps<{
  values: (number | null)[]
  unit?: string | null
}>()

const W = 100
const H = 28
const PAD = 3

interface Coord {
  x: number
  y: number
  value: number | null
}

/** x 轴按世代序号均匀分布，与聚焦主图的横轴口径一致；缺失代照样占位，断线位置才对得上 */
const coords = computed<Coord[]>(() => {
  const list = props.values
  if (!list.length) return []
  const numbers = list.filter((value): value is number => typeof value === 'number')
  if (!numbers.length) return []
  const min = Math.min(...numbers)
  const max = Math.max(...numbers)
  const span = max - min || 1
  return list.map((value, index) => ({
    x: list.length === 1 ? W / 2 : PAD + (index * (W - PAD * 2)) / (list.length - 1),
    y: typeof value === 'number' ? H - PAD - ((value - min) / span) * (H - PAD * 2) : H / 2,
    value,
  }))
})

/**
 * null 两侧断开成多段 polyline；单点退化为短横线。
 * 空值不画点，避免把缺失代误读成 0 或中值。
 */
const segments = computed(() => {
  const list = coords.value
  if (!list.length) return []
  if (list.length === 1) {
    const only = list[0]
    return [`${only.x - 1.5},${only.y} ${only.x + 1.5},${only.y}`]
  }
  const result: string[] = []
  let current: string[] = []
  for (const coord of list) {
    if (coord.value === null) {
      if (current.length > 1) result.push(current.join(' '))
      current = []
      continue
    }
    current.push(`${coord.x.toFixed(1)},${coord.y.toFixed(1)}`)
  }
  if (current.length > 1) result.push(current.join(' '))
  return result
})

const tip = computed(() => {
  const numbers = props.values.filter((value): value is number => typeof value === 'number')
  if (!numbers.length) return ''
  const unit = props.unit ?? ''
  return `${numbers[0]}${unit} → ${numbers[numbers.length - 1]}${unit}（共 ${numbers.length} 代）`
})
</script>

<template>
  <svg
    v-if="segments.length"
    :viewBox="`0 0 ${W} ${H}`"
    class="h-7 w-full"
    preserveAspectRatio="none"
    aria-hidden="true"
  >
    <polyline
      v-for="(points, index) in segments"
      :key="index"
      :points="points"
      fill="none"
      stroke="currentColor"
      stroke-width="1.5"
      stroke-linejoin="round"
      stroke-linecap="round"
      vector-effect="non-scaling-stroke"
    >
      <title v-if="index === 0">{{ tip }}</title>
    </polyline>
  </svg>
</template>

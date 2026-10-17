<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts'
import html2canvas from 'html2canvas'
import { ElMessage } from 'element-plus'
import { publicApi } from '@/api'

const brands = ref<any[]>([])
const defs = ref<any[]>([])
const brandId = ref<number>()
const paramDefId = ref<number>()
const view = ref<'table' | 'chart'>('chart')
const points = ref<any[]>([])
const capturing = ref(false)

const chartRef = ref<HTMLElement>()
const captureRef = ref<HTMLElement>()
let chart: echarts.ECharts | null = null

async function load() {
  if (!brandId.value || !paramDefId.value) {
    points.value = []
    return
  }
  points.value = await publicApi.history(brandId.value, paramDefId.value)
  if (view.value === 'chart') {
    await nextTick()
    renderChart()
  }
}

function renderChart() {
  if (!chartRef.value) return
  if (!chart) chart = echarts.init(chartRef.value)
  const def = defs.value.find((d) => d.id === paramDefId.value)
  const numeric = points.value.map((p) => Number(p.value)).every((n) => !Number.isNaN(n))
  chart.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: 50, right: 30, top: 40, bottom: 60 },
    xAxis: { type: 'category', data: points.value.map((p) => p.deviceName), axisLabel: { rotate: 30 } },
    yAxis: { type: 'value', name: def?.unit || '' },
    series: [
      {
        name: def?.paramName || '参数',
        type: numeric ? 'line' : 'bar',
        smooth: true,
        data: points.value.map((p) => (numeric ? Number(p.value) : p.value)),
        label: { show: !numeric, position: 'top' },
        areaStyle: numeric ? {} : undefined,
      },
    ],
  })
}

onMounted(async () => {
  ;[brands.value, defs.value] = await Promise.all([publicApi.brands(), publicApi.paramDefs()])
})

watch([brandId, paramDefId], load)
watch(view, async () => {
  if (view.value === 'chart') {
    await nextTick()
    renderChart()
  }
})

function onResize() {
  chart?.resize()
}
window.addEventListener('resize', onResize)
onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  chart?.dispose()
})

async function exportImage() {
  if (!captureRef.value) return
  capturing.value = true
  await nextTick()
  try {
    const canvas = await html2canvas(captureRef.value, { backgroundColor: '#ffffff', scale: 2 })
    const link = document.createElement('a')
    link.download = `spectrace-history-${Date.now()}.png`
    link.href = canvas.toDataURL('image/png')
    link.click()
    ElMessage.success('截图已下载')
  } finally {
    capturing.value = false
  }
}
</script>

<template>
  <div class="space-y-5">
    <div class="flex flex-wrap items-center gap-3">
      <h1 class="text-xl font-semibold">参数历史演变</h1>
      <el-select v-model="brandId" placeholder="选择品牌" class="w-40">
        <el-option v-for="b in brands" :key="b.id" :label="b.name" :value="b.id" />
      </el-select>
      <el-select v-model="paramDefId" placeholder="选择参数" class="w-52" filterable>
        <el-option v-for="d in defs" :key="d.id" :label="d.paramName" :value="d.id" />
      </el-select>
      <el-radio-group v-model="view">
        <el-radio-button label="chart">图表</el-radio-button>
        <el-radio-button label="table">表格</el-radio-button>
      </el-radio-group>
      <el-button class="ml-auto" @click="exportImage" :disabled="!points.length">
        <el-icon><Download /></el-icon><span class="ml-1">截图导出</span>
      </el-button>
    </div>

    <el-alert v-if="!points.length" type="info" :closable="false" show-icon
      title="请选择品牌与参数，查看该参数跨机型的历史演变（例：iPhone 屏幕尺寸演变）" />

    <div v-else ref="captureRef" class="capture-area rounded-xl border border-slate-200 dark:border-slate-700 p-6">
      <div class="text-center mb-4">
        <div class="font-bold">
          {{ brands.find((b) => b.id === brandId)?.name }} ·
          {{ defs.find((d) => d.id === paramDefId)?.paramName }} 演变
        </div>
      </div>

      <div v-show="view === 'chart'" ref="chartRef" class="h-96 w-full"></div>

      <table v-show="view === 'table'" class="w-full text-sm border-collapse">
        <thead>
          <tr class="bg-slate-100 dark:bg-slate-700">
            <th class="p-3 text-left">发布时间</th>
            <th class="p-3 text-left">机型</th>
            <th class="p-3 text-left">参数值</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="p in points" :key="p.deviceId" class="border-t border-slate-100 dark:border-slate-700">
            <td class="p-3">{{ p.releaseDate }}</td>
            <td class="p-3">{{ p.deviceName }}</td>
            <td class="p-3 font-medium">{{ p.value }}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

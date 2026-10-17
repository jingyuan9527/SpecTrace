<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue'
import html2canvas from 'html2canvas'
import { ElMessage } from 'element-plus'
import { publicApi } from '@/api'
import { useCompareStore } from '@/stores/compare'

const compare = useCompareStore()
const result = ref<any>(null)
const loading = ref(false)
const watermark = ref(false)
const capturing = ref(false)
const captureRef = ref<HTMLElement>()
const candidates = ref<any[]>([])
const pickId = ref<number>()

async function load() {
  if (compare.count < 2) {
    result.value = null
    return
  }
  loading.value = true
  try {
    result.value = await publicApi.compare(compare.ids)
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  await load()
  const res = await publicApi.devices({ current: 1, size: 100 })
  candidates.value = res.records
})

function remove(id: number) {
  compare.remove(id)
  load()
}

function addSelected() {
  const d = candidates.value.find((c) => c.id === pickId.value)
  if (d) {
    compare.add({ id: d.id, name: d.name, thumbnail: d.thumbnail })
    pickId.value = undefined
    load()
  }
}

const available = computed(() => candidates.value.filter((c) => !compare.has(c.id)))

async function captureCanvas() {
  if (!captureRef.value) throw new Error('无可截图区域')
  capturing.value = true
  await nextTick()
  try {
    return await html2canvas(captureRef.value, { backgroundColor: '#ffffff', scale: 2, useCORS: true })
  } finally {
    capturing.value = false
    await nextTick()
  }
}

async function download() {
  const canvas = await captureCanvas()
  const link = document.createElement('a')
  link.download = `spectrace-compare-${Date.now()}.png`
  link.href = canvas.toDataURL('image/png')
  link.click()
  ElMessage.success('截图已下载')
}

async function copyToClipboard() {
  try {
    const canvas = await captureCanvas()
    const blob: Blob = await new Promise((resolve) => canvas.toBlob((b) => resolve(b as Blob), 'image/png'))
    await navigator.clipboard.write([new ClipboardItem({ 'image/png': blob })])
    ElMessage.success('已复制到剪贴板，可直接粘贴分享')
  } catch {
    ElMessage.error('复制失败，请改用下载图片')
  }
}
</script>

<template>
  <div class="space-y-5">
    <div class="flex flex-wrap items-center gap-3">
      <h1 class="text-xl font-semibold">机型对比</h1>
      <el-select v-model="pickId" placeholder="添加机型" class="w-48" filterable>
        <el-option v-for="c in available" :key="c.id" :label="c.name" :value="c.id" />
      </el-select>
      <el-button @click="addSelected" :disabled="!pickId">添加</el-button>
      <div class="ml-auto flex items-center gap-3">
        <el-switch v-model="watermark" active-text="网站水印" />
        <el-button @click="download" :disabled="!result">
          <el-icon><Download /></el-icon><span class="ml-1">下载图片</span>
        </el-button>
        <el-button type="primary" @click="copyToClipboard" :disabled="!result">
          <el-icon><CopyDocument /></el-icon><span class="ml-1">复制图片</span>
        </el-button>
      </div>
    </div>

    <el-alert v-if="compare.count < 2" type="info" :closable="false" show-icon
      title="请先从机型库选择至少 2 台机型加入对比池" />

    <div v-loading="loading" v-if="result" ref="captureRef" class="capture-area relative p-6 rounded-xl border border-slate-200 dark:border-slate-700">
      <div class="text-center mb-5">
        <div class="text-lg font-bold">SpecTrace · 机型参数对比</div>
        <div class="text-xs text-slate-400">{{ new Date().toLocaleDateString() }}</div>
      </div>

      <table class="w-full text-sm border-collapse">
        <thead>
          <tr>
            <th class="p-3 text-left w-40 bg-slate-100 dark:bg-slate-700 sticky left-0">参数</th>
            <th v-for="d in result.devices" :key="d.id" class="p-3 bg-slate-100 dark:bg-slate-700">
              <div class="flex flex-col items-center gap-1">
                <img :src="d.thumbnail" class="h-16 object-contain" />
                <span class="font-semibold">{{ d.name }}</span>
                <el-button v-if="!capturing" size="small" text type="danger" @click="remove(d.id)">移除</el-button>
              </div>
            </th>
          </tr>
        </thead>
        <tbody v-for="g in result.groups" :key="g.groupId">
          <tr>
            <td :colspan="result.devices.length + 1" class="p-2 font-semibold bg-slate-50 dark:bg-slate-800">{{ g.groupName }}</td>
          </tr>
          <tr v-for="p in g.params" :key="p.paramKey" class="border-t border-slate-100 dark:border-slate-700">
            <td class="p-3 text-slate-400">{{ p.paramName }}</td>
            <td
              v-for="(v, i) in p.values"
              :key="i"
              class="p-3 text-center"
              :class="p.same ? 'bg-slate-50 dark:bg-slate-800 text-slate-400' : 'bg-amber-50 dark:bg-amber-500/10 font-medium'"
            >
              {{ v || '—' }}<span v-if="p.unit && v" class="text-slate-400 ml-1">{{ p.unit }}</span>
            </td>
          </tr>
        </tbody>
      </table>

      <div v-if="watermark" class="absolute bottom-3 right-4 text-xs text-slate-300 select-none">SpecTrace</div>
    </div>

    <el-empty v-else-if="compare.count >= 2 && !loading" description="暂无对比数据" />
  </div>
</template>

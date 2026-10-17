<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { publicApi } from '@/api'
import { useCompareStore } from '@/stores/compare'
import { ElMessage } from 'element-plus'

const route = useRoute()
const router = useRouter()
const compare = useCompareStore()

const device = ref<any>(null)
const brand = ref<any>(null)
const params = ref<Record<string, string>>({})
const groups = ref<any[]>([])
const defs = ref<any[]>([])

const grouped = computed(() =>
  groups.value
    .map((g) => ({
      name: g.name,
      items: defs.value
        .filter((d) => d.groupId === g.id)
        .map((d) => ({ ...d, value: params.value[d.paramKey] ?? '' }))
        .filter((d) => d.value !== ''),
    }))
    .filter((g) => g.items.length),
)

onMounted(async () => {
  const id = Number(route.params.id)
  const detail = await publicApi.deviceDetail(id)
  device.value = detail.device
  brand.value = detail.brand
  params.value = detail.params || {}
  ;[groups.value, defs.value] = await Promise.all([publicApi.paramGroups(), publicApi.paramDefs()])
})

function addCompare() {
  compare.add({ id: device.value.id, name: device.value.name, thumbnail: device.value.thumbnail })
  ElMessage.success('已加入对比池')
}
</script>

<template>
  <div v-if="device" class="space-y-6">
    <div class="rounded-2xl border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 p-6 flex flex-col md:flex-row gap-6">
      <div class="w-full md:w-64 h-64 bg-slate-100 dark:bg-slate-700 rounded-xl flex items-center justify-center">
        <img :src="device.thumbnail" :alt="device.name" class="max-h-60 object-contain" />
      </div>
      <div class="flex-1">
        <div class="text-sm text-slate-400">{{ brand?.name }}</div>
        <h1 class="text-2xl font-bold">{{ device.name }}</h1>
        <p class="mt-2 text-slate-500 dark:text-slate-400">{{ device.description }}</p>
        <div class="mt-2 text-sm text-slate-400">发布时间：{{ device.releaseDate }}</div>
        <div class="mt-4 flex gap-2">
          <el-button type="primary" @click="addCompare">加入对比</el-button>
          <el-button @click="router.push('/compare')">查看对比池</el-button>
        </div>
      </div>
    </div>

    <div
      v-for="g in grouped"
      :key="g.name"
      class="rounded-xl border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 overflow-hidden"
    >
      <div class="px-4 py-3 font-semibold bg-slate-100 dark:bg-slate-700">{{ g.name }}</div>
      <table class="w-full text-sm">
        <tbody>
          <tr v-for="item in g.items" :key="item.paramKey" class="border-t border-slate-100 dark:border-slate-700">
            <td class="px-4 py-2 w-40 text-slate-400">{{ item.paramName }}</td>
            <td class="px-4 py-2">{{ item.value }}<span v-if="item.unit" class="text-slate-400 ml-1">{{ item.unit }}</span></td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

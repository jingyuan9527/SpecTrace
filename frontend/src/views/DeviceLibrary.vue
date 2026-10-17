<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { publicApi } from '@/api'
import { useCompareStore } from '@/stores/compare'
import { ElMessage } from 'element-plus'

const route = useRoute()
const router = useRouter()
const compare = useCompareStore()

const brands = ref<any[]>([])
const devices = ref<any[]>([])
const total = ref(0)
const loading = ref(false)

const query = reactive({
  current: 1,
  size: 12,
  brandId: undefined as number | undefined,
  year: undefined as number | undefined,
  keyword: (route.query.keyword as string) || '',
})
const years = ref<number[]>([])

async function load() {
  loading.value = true
  try {
    const res = await publicApi.devices({ ...query })
    devices.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}

async function loadYears() {
  years.value = await publicApi.years(query.brandId)
}

onMounted(async () => {
  brands.value = await publicApi.brands()
  await load()
  await loadYears()
})

watch(
  () => [query.brandId, query.year],
  () => {
    query.current = 1
    load()
    loadYears()
  },
)

function toggleCompare(d: any) {
  if (compare.has(d.id)) {
    compare.remove(d.id)
  } else {
    compare.add({ id: d.id, name: d.name, thumbnail: d.thumbnail })
    ElMessage.success(`已加入对比池：${d.name}`)
  }
}
</script>

<template>
  <div class="space-y-5">
    <div class="rounded-xl border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 p-4 flex flex-wrap items-center gap-3">
      <el-select v-model="query.brandId" placeholder="全部品牌" clearable class="w-40">
        <el-option v-for="b in brands" :key="b.id" :label="b.name" :value="b.id" />
      </el-select>
      <el-select v-model="query.year" placeholder="全部年份" clearable class="w-32">
        <el-option v-for="y in years" :key="y" :label="`${y} 年`" :value="y" />
      </el-select>
      <el-input v-model="query.keyword" placeholder="机型名称" class="w-56" clearable @keyup.enter="query.current = 1; load()" />
      <el-button type="primary" @click="query.current = 1; load()">
        <el-icon><Search /></el-icon><span class="ml-1">搜索</span>
      </el-button>
      <el-button class="ml-auto" @click="router.push('/compare')">
        前往对比 ({{ compare.count }})
      </el-button>
    </div>

    <div v-loading="loading" class="grid grid-cols-2 md:grid-cols-4 gap-4 min-h-[200px]">
      <div
        v-for="d in devices"
        :key="d.id"
        class="rounded-xl border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 overflow-hidden hover:shadow-md transition"
      >
        <div class="h-40 bg-slate-100 dark:bg-slate-700 flex items-center justify-center cursor-pointer" @click="router.push(`/devices/${d.id}`)">
          <img :src="d.thumbnail" :alt="d.name" class="max-h-36 object-contain" />
        </div>
        <div class="p-3">
          <div class="font-medium cursor-pointer hover:text-brand" @click="router.push(`/devices/${d.id}`)">{{ d.name }}</div>
          <div class="text-xs text-slate-400 mt-1">{{ d.releaseDate }}</div>
          <el-button
            class="mt-3 w-full"
            size="small"
            :type="compare.has(d.id) ? 'success' : 'default'"
            @click="toggleCompare(d)"
          >
            {{ compare.has(d.id) ? '已在对比池' : '加入对比' }}
          </el-button>
        </div>
      </div>
    </div>

    <div class="flex justify-center">
      <el-pagination
        v-model:current-page="query.current"
        :page-size="query.size"
        :total="total"
        layout="prev, pager, next, total"
        @current-change="load"
      />
    </div>
  </div>
</template>

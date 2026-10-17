<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { publicApi } from '@/api'
import { useCompareStore } from '@/stores/compare'
import { ElMessage } from 'element-plus'

const router = useRouter()
const compare = useCompareStore()

const brands = ref<any[]>([])
const hot = ref<any[]>([])
const keyword = ref('')

onMounted(async () => {
  ;[brands.value, hot.value] = await Promise.all([publicApi.brands(), publicApi.hot(8)])
})

function search() {
  router.push({ name: 'devices', query: { keyword: keyword.value } })
}

function addCompare(d: any) {
  compare.add({ id: d.id, name: d.name, thumbnail: d.thumbnail })
  ElMessage.success(`已加入对比池：${d.name}`)
}
</script>

<template>
  <div class="space-y-10">
    <!-- Hero -->
    <section class="rounded-2xl bg-gradient-to-br from-brand to-indigo-500 text-white p-10">
      <h1 class="text-3xl font-bold">手机参数，一屏看透</h1>
      <p class="mt-2 text-white/80">收录全品牌机型参数，横向对比、跨代演变、一键截图分享</p>
      <div class="mt-6 flex gap-2 max-w-xl">
        <el-input v-model="keyword" size="large" placeholder="搜索机型，如 iPhone 17" @keyup.enter="search" clearable />
        <el-button size="large" type="primary" plain @click="search">
          <el-icon><Search /></el-icon>
        </el-button>
      </div>
      <div class="mt-6 flex flex-wrap gap-3">
        <el-button @click="router.push('/compare')">⚖️ 机型对比</el-button>
        <el-button @click="router.push('/history')">📈 参数历史演变</el-button>
      </div>
    </section>

    <!-- 品牌导航 -->
    <section>
      <h2 class="text-xl font-semibold mb-4">品牌导航</h2>
      <div class="grid grid-cols-3 sm:grid-cols-6 gap-4">
        <button
          v-for="b in brands"
          :key="b.id"
          class="rounded-xl border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 p-4 hover:shadow-md transition"
          @click="router.push({ name: 'devices', query: { brandId: b.id } })"
        >
          <img :src="b.logo" :alt="b.name" class="h-8 mx-auto object-contain" />
          <div class="mt-2 text-sm">{{ b.name }}</div>
        </button>
      </div>
    </section>

    <!-- 热门机型 -->
    <section>
      <h2 class="text-xl font-semibold mb-4">热门机型</h2>
      <div class="grid grid-cols-2 md:grid-cols-4 gap-4">
        <div
          v-for="d in hot"
          :key="d.id"
          class="rounded-xl border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 overflow-hidden hover:shadow-md transition"
        >
          <div class="h-40 bg-slate-100 dark:bg-slate-700 flex items-center justify-center">
            <img :src="d.thumbnail" :alt="d.name" class="max-h-36 object-contain" />
          </div>
          <div class="p-3">
            <div class="font-medium cursor-pointer hover:text-brand" @click="router.push(`/devices/${d.id}`)">
              {{ d.name }}
            </div>
            <div class="text-xs text-slate-400 mt-1">{{ d.releaseDate }}</div>
            <div class="mt-3 flex gap-2">
              <el-button size="small" @click="addCompare(d)">加入对比</el-button>
              <el-button size="small" text @click="router.push(`/devices/${d.id}`)">详情</el-button>
            </div>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

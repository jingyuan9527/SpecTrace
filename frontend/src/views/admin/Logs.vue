<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { adminApi } from '@/api'

const list = ref<any[]>([])
const total = ref(0)
const loading = ref(false)
const query = reactive({ current: 1, size: 20, targetType: '' })

const types = ['brand', 'param_group', 'param_def', 'device', 'agent_task', 'agent_pending_data', 'admin_user']

async function load() {
  loading.value = true
  try {
    const res = await adminApi.logs({ ...query })
    list.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}
onMounted(load)
</script>

<template>
  <div class="space-y-4">
    <div class="flex items-center gap-3">
      <h2 class="text-lg font-semibold">操作日志</h2>
      <el-select v-model="query.targetType" placeholder="全部类型" clearable class="w-48 ml-auto" @change="query.current = 1; load()">
        <el-option v-for="t in types" :key="t" :label="t" :value="t" />
      </el-select>
      <el-button @click="load">刷新</el-button>
    </div>

    <el-table v-loading="loading" :data="list" border stripe>
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="adminName" label="操作人" width="120" />
      <el-table-column prop="action" label="操作" width="180" />
      <el-table-column prop="targetType" label="目标类型" width="150" />
      <el-table-column prop="targetId" label="目标ID" width="100" />
      <el-table-column prop="detail" label="详情" min-width="200" />
      <el-table-column prop="createdAt" label="时间" width="180" />
    </el-table>

    <div class="flex justify-center">
      <el-pagination v-model:current-page="query.current" :page-size="query.size" :total="total"
        layout="prev, pager, next, total" @current-change="load" />
    </div>
  </div>
</template>

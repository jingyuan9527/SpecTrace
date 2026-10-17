<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { adminApi } from '@/api'

const list = ref<any[]>([])
const aiModels = ref<any[]>([])
const total = ref(0)
const loading = ref(false)
const query = reactive({ current: 1, size: 10, status: '' })
const form = reactive<{ modelName: string; brandName: string; aiModelId: number | null }>({
  modelName: '', brandName: '', aiModelId: null,
})

const statusMap: Record<string, { label: string; type: string }> = {
  QUEUED: { label: '排队中', type: 'info' },
  RUNNING: { label: '抓取中', type: 'warning' },
  PENDING_REVIEW: { label: '待审核', type: 'primary' },
  APPROVED: { label: '已通过', type: 'success' },
  DISCARDED: { label: '已丢弃', type: 'info' },
  FAILED: { label: '失败', type: 'danger' },
}

async function load() {
  loading.value = true
  try {
    const res = await adminApi.agentTasks({ ...query })
    list.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}

onMounted(load)

async function loadAiModels() {
  aiModels.value = await adminApi.aiModels()
  const def = aiModels.value.find((m) => m.isDefault === 1)
  if (def && form.aiModelId === null) form.aiModelId = def.id
}
onMounted(loadAiModels)

function modelLabel(id: number | null) {
  if (!id) return '默认模型'
  const m = aiModels.value.find((x) => x.id === id)
  return m ? `${m.configName} / ${m.model}` : `#${id}`
}

async function createTask() {
  if (!form.modelName.trim()) {
    ElMessage.warning('请输入机型名称')
    return
  }
  await adminApi.createAgentTask({ ...form, aiModelId: form.aiModelId ?? undefined })
  ElMessage.success('抓取任务已提交')
  form.modelName = ''
  load()
}
</script>

<template>
  <div class="space-y-4">
    <div class="rounded-xl border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 p-4">
      <h2 class="text-lg font-semibold mb-3">新建抓取任务</h2>
      <div class="flex flex-wrap gap-3">
        <el-input v-model="form.modelName" placeholder="机型名称，如 iPhone 17" class="w-64" @keyup.enter="createTask" />
        <el-input v-model="form.brandName" placeholder="品牌（可选）" class="w-48" />
        <el-select v-model="form.aiModelId" placeholder="AI 模型（默认）" clearable class="w-64">
          <el-option v-for="m in aiModels" :key="m.id" :label="`${m.configName} / ${m.model}${m.isDefault === 1 ? '（默认）' : ''}`" :value="m.id" />
        </el-select>
        <el-button type="primary" @click="createTask">
          <el-icon><Cpu /></el-icon><span class="ml-1">提交抓取</span>
        </el-button>
        <span class="text-xs text-slate-400 self-center">任务经队列投递给 AgentScope 采集 Agent；不选模型则用默认模型</span>
      </div>
    </div>

    <div class="flex items-center gap-3">
      <el-select v-model="query.status" placeholder="全部状态" clearable class="w-40" @change="query.current = 1; load()">
        <el-option v-for="(v, k) in statusMap" :key="k" :label="v.label" :value="k" />
      </el-select>
      <el-button @click="load">刷新</el-button>
    </div>

    <el-table v-loading="loading" :data="list" border stripe>
      <el-table-column prop="id" label="任务ID" width="90" />
      <el-table-column prop="modelName" label="机型名称" />
      <el-table-column prop="brandName" label="品牌" width="120" />
      <el-table-column label="AI 模型" min-width="180">
        <template #default="{ row }">{{ modelLabel(row.aiModelId) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="110">
        <template #default="{ row }">
          <el-tag :type="statusMap[row.status]?.type || 'info'">{{ statusMap[row.status]?.label || row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="durationMs" label="耗时(ms)" width="110" />
      <el-table-column prop="createdAt" label="创建时间" width="180" />
      <el-table-column label="来源" min-width="200">
        <template #default="{ row }">
          <a v-if="row.sourceUrl" :href="row.sourceUrl" target="_blank" class="text-brand break-all">{{ row.sourceUrl }}</a>
        </template>
      </el-table-column>
      <el-table-column label="异常" min-width="160">
        <template #default="{ row }"><span class="text-red-500">{{ row.errorMsg }}</span></template>
      </el-table-column>
    </el-table>

    <div class="flex justify-center">
      <el-pagination v-model:current-page="query.current" :page-size="query.size" :total="total"
        layout="prev, pager, next, total" @current-change="load" />
    </div>
  </div>
</template>

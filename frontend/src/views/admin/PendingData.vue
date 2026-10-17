<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminApi } from '@/api'

const list = ref<any[]>([])
const total = ref(0)
const loading = ref(false)
const query = reactive({ current: 1, size: 10, status: 'PENDING_REVIEW' as string | undefined })
const brands = ref<any[]>([])
const defs = ref<any[]>([])

const dialog = ref(false)
const current = ref<any>(null)
const form = reactive<any>({ brandId: undefined, name: '', releaseDate: '', thumbnail: '', description: '', params: {} as Record<string, string> })

async function load() {
  loading.value = true
  try {
    const res = await adminApi.pendingData({ ...query })
    list.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  ;[brands.value, defs.value] = await Promise.all([adminApi.brands(), adminApi.paramDefs()])
  load()
})

async function openReview(row: any) {
  const detail = await adminApi.pendingDetail(row.id)
  current.value = detail
  Object.assign(form, {
    brandId: brands.value.find((b) => b.name === detail.brandName)?.id,
    name: detail.modelName,
    releaseDate: detail.params['release_date'] || '',
    thumbnail: '',
    description: '',
    params: { ...detail.params },
  })
  dialog.value = true
}

async function approve() {
  if (!form.brandId) {
    ElMessage.warning('请选择品牌')
    return
  }
  await adminApi.approvePending(current.value.id, { ...form })
  ElMessage.success('审核通过，已迁移到正式机型库')
  dialog.value = false
  load()
}

async function discard(row: any) {
  await ElMessageBox.confirm(`确认丢弃「${row.modelName}」的抓取数据？`, '提示', { type: 'warning' })
  await adminApi.discardPending(row.id)
  ElMessage.success('已丢弃')
  load()
}

function label(key: string) {
  return defs.value.find((d) => d.paramKey === key)?.paramName || key
}
</script>

<template>
  <div class="space-y-4">
    <div class="flex items-center gap-3">
      <h2 class="text-lg font-semibold">待审核数据</h2>
      <el-select v-model="query.status" placeholder="全部状态" clearable class="w-40 ml-auto" @change="query.current = 1; load()">
        <el-option label="待审核" value="PENDING_REVIEW" />
        <el-option label="已通过" value="APPROVED" />
        <el-option label="已丢弃" value="DISCARDED" />
      </el-select>
      <el-button @click="load">刷新</el-button>
    </div>

    <el-table v-loading="loading" :data="list" border stripe>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="modelName" label="机型名称" />
      <el-table-column prop="brandName" label="品牌" width="120" />
      <el-table-column label="来源链接" min-width="220">
        <template #default="{ row }">
          <a v-if="row.sourceUrl" :href="row.sourceUrl" target="_blank" class="text-brand break-all">{{ row.sourceUrl }}</a>
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="抓取时间" width="180" />
      <el-table-column label="操作" width="180">
        <template #default="{ row }">
          <el-button size="small" type="primary" @click="openReview(row)" :disabled="row.status !== 'PENDING_REVIEW'">审核</el-button>
          <el-button size="small" type="danger" @click="discard(row)" :disabled="row.status !== 'PENDING_REVIEW'">丢弃</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="flex justify-center">
      <el-pagination v-model:current-page="query.current" :page-size="query.size" :total="total"
        layout="prev, pager, next, total" @current-change="load" />
    </div>

    <el-dialog v-model="dialog" title="审核 Agent 抓取数据" width="720" top="5vh">
      <div class="mb-3 text-sm text-slate-400">
        来源：
        <a v-if="current?.sourceUrl" :href="current.sourceUrl" target="_blank" class="text-brand break-all">{{ current.sourceUrl }}</a>
      </div>
      <el-form :model="form" label-width="110px">
        <el-form-item label="机型名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="品牌">
          <el-select v-model="form.brandId" class="w-full" filterable>
            <el-option v-for="b in brands" :key="b.id" :label="b.name" :value="b.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="发布日期">
          <el-date-picker v-model="form.releaseDate" type="date" value-format="YYYY-MM-DD" class="w-full" />
        </el-form-item>
        <el-divider content-position="left">参数（可修改修正）</el-divider>
        <el-form-item v-for="(val, key) in form.params" :key="key" :label="label(key as string)">
          <el-input v-model="form.params[key]" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="danger" plain @click="discard(current)">丢弃</el-button>
        <el-button type="primary" @click="approve">审核通过并入库</el-button>
      </template>
    </el-dialog>
  </div>
</template>

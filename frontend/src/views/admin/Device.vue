<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { UploadRequestOptions } from 'element-plus'
import { adminApi } from '@/api'

const list = ref<any[]>([])
const brands = ref<any[]>([])
const groups = ref<any[]>([])
const defs = ref<any[]>([])
const total = ref(0)
const loading = ref(false)
const query = reactive({ current: 1, size: 10, keyword: '', brandId: undefined as number | undefined })

const dialog = ref(false)
const form = reactive<any>({ id: null, brandId: undefined, name: '', releaseDate: '', thumbnail: '', description: '', status: 1, params: {} as Record<string, string> })

const defsByGroup = computed(() =>
  groups.value
    .map((g) => ({ ...g, defs: defs.value.filter((d) => d.groupId === g.id) }))
    .filter((g) => g.defs.length),
)

async function load() {
  loading.value = true
  try {
    const res = await adminApi.devices({ ...query })
    list.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  ;[brands.value, groups.value, defs.value] = await Promise.all([
    adminApi.brands(), adminApi.paramGroups(), adminApi.paramDefs(),
  ])
  load()
})

function openCreate() {
  Object.assign(form, { id: null, brandId: brands.value[0]?.id, name: '', releaseDate: '', thumbnail: '', description: '', status: 1, params: {} })
  dialog.value = true
}

async function openEdit(row: any) {
  const detail = await adminApi.deviceDetail(row.id)
  Object.assign(form, {
    id: detail.device.id,
    brandId: detail.device.brandId,
    name: detail.device.name,
    releaseDate: detail.device.releaseDate,
    thumbnail: detail.device.thumbnail,
    description: detail.device.description,
    status: detail.device.status,
    params: { ...detail.params },
  })
  dialog.value = true
}

async function save() {
  await adminApi.saveDevice({ ...form })
  ElMessage.success('保存成功')
  dialog.value = false
  load()
}

async function remove(row: any) {
  await ElMessageBox.confirm(`确认删除机型「${row.name}」？`, '提示', { type: 'warning' })
  await adminApi.deleteDevice(row.id)
  ElMessage.success('已删除')
  load()
}

async function uploadThumb(options: UploadRequestOptions) {
  form.thumbnail = await adminApi.upload(options.file as File)
  ElMessage.success('图片上传成功')
}

function optionsOf(def: any): string[] {
  try {
    return JSON.parse(def.enumOptions || '[]')
  } catch {
    return []
  }
}
</script>

<template>
  <div>
    <div class="flex flex-wrap items-center gap-3 mb-4">
      <h2 class="text-lg font-semibold">机型管理</h2>
      <el-input v-model="query.keyword" placeholder="机型名称" class="w-48" clearable @keyup.enter="query.current = 1; load()" />
      <el-select v-model="query.brandId" placeholder="全部品牌" clearable class="w-40">
        <el-option v-for="b in brands" :key="b.id" :label="b.name" :value="b.id" />
      </el-select>
      <el-button @click="query.current = 1; load()">查询</el-button>
      <el-button type="primary" class="ml-auto" @click="openCreate">新增机型</el-button>
    </div>

    <el-table v-loading="loading" :data="list" border stripe>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column label="缩略图" width="90">
        <template #default="{ row }"><img :src="row.thumbnail" class="h-10 object-contain" /></template>
      </el-table-column>
      <el-table-column prop="name" label="机型名称" />
      <el-table-column label="品牌" width="120">
        <template #default="{ row }">{{ brands.find((b) => b.id === row.brandId)?.name }}</template>
      </el-table-column>
      <el-table-column prop="releaseDate" label="发布时间" width="130" />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '上架' : '下架' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="160">
        <template #default="{ row }">
          <el-button size="small" @click="openEdit(row)">编辑</el-button>
          <el-button size="small" type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="flex justify-center mt-4">
      <el-pagination v-model:current-page="query.current" :page-size="query.size" :total="total"
        layout="prev, pager, next, total" @current-change="load" />
    </div>

    <el-dialog v-model="dialog" :title="form.id ? '编辑机型' : '新增机型'" width="760" top="5vh">
      <el-form :model="form" label-width="100px">
        <el-form-item label="机型名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="品牌">
          <el-select v-model="form.brandId" class="w-full">
            <el-option v-for="b in brands" :key="b.id" :label="b.name" :value="b.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="发布时间">
          <el-date-picker v-model="form.releaseDate" type="date" value-format="YYYY-MM-DD" class="w-full" />
        </el-form-item>
        <el-form-item label="缩略图">
          <el-upload :show-file-list="false" :http-request="uploadThumb"><el-button>上传图片</el-button></el-upload>
          <img v-if="form.thumbnail" :src="form.thumbnail" class="h-12 ml-3 object-contain" />
        </el-form-item>
        <el-form-item label="简介"><el-input v-model="form.description" type="textarea" /></el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" active-text="上架" inactive-text="下架" />
        </el-form-item>

        <el-divider content-position="left">参数</el-divider>
        <div v-for="g in defsByGroup" :key="g.id" class="mb-3">
          <div class="text-sm font-semibold text-slate-400 mb-2">{{ g.name }}</div>
          <el-form-item v-for="d in g.defs" :key="d.id" :label="d.paramName">
            <el-input-number v-if="d.paramType === 'number'" v-model.number="form.params[d.paramKey]" :controls="false" class="w-full" />
            <el-date-picker v-else-if="d.paramType === 'date'" v-model="form.params[d.paramKey]" type="date" value-format="YYYY-MM-DD" class="w-full" />
            <el-select v-else-if="d.paramType === 'select'" v-model="form.params[d.paramKey]" class="w-full" clearable>
              <el-option v-for="o in optionsOf(d)" :key="o" :label="o" :value="o" />
            </el-select>
            <el-input v-else v-model="form.params[d.paramKey]" :placeholder="d.unit ? `单位：${d.unit}` : ''" />
          </el-form-item>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

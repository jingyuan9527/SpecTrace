<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { UploadRequestOptions } from 'element-plus'
import { adminApi } from '@/api'

const list = ref<any[]>([])
const dialog = ref(false)
const form = reactive<any>({ id: null, name: '', logo: '', sortOrder: 0, status: 1 })

async function load() {
  list.value = await adminApi.brands()
}

function openCreate() {
  Object.assign(form, { id: null, name: '', logo: '', sortOrder: 0, status: 1 })
  dialog.value = true
}

function openEdit(row: any) {
  Object.assign(form, row)
  dialog.value = true
}

async function save() {
  await adminApi.saveBrand({ ...form })
  ElMessage.success('保存成功')
  dialog.value = false
  load()
}

async function remove(row: any) {
  await ElMessageBox.confirm(`确认删除品牌「${row.name}」？`, '提示', { type: 'warning' })
  await adminApi.deleteBrand(row.id)
  ElMessage.success('已删除')
  load()
}

async function uploadLogo(options: UploadRequestOptions) {
  form.logo = await adminApi.upload(options.file as File)
  ElMessage.success('Logo 上传成功')
}

onMounted(load)
</script>

<template>
  <div>
    <div class="flex items-center mb-4">
      <h2 class="text-lg font-semibold">品牌管理</h2>
      <el-button type="primary" class="ml-auto" @click="openCreate">新增品牌</el-button>
    </div>

    <el-table :data="list" border stripe>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column label="Logo" width="100">
        <template #default="{ row }"><img :src="row.logo" class="h-8 object-contain" /></template>
      </el-table-column>
      <el-table-column prop="name" label="品牌名称" />
      <el-table-column prop="sortOrder" label="排序" width="90" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '启用' : '停用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="160">
        <template #default="{ row }">
          <el-button size="small" @click="openEdit(row)">编辑</el-button>
          <el-button size="small" type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialog" :title="form.id ? '编辑品牌' : '新增品牌'" width="480">
      <el-form :model="form" label-width="90px">
        <el-form-item label="品牌名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="Logo">
          <el-upload :show-file-list="false" :http-request="uploadLogo">
            <el-button>上传 Logo</el-button>
          </el-upload>
          <img v-if="form.logo" :src="form.logo" class="h-8 ml-3 object-contain" />
        </el-form-item>
        <el-form-item label="排序"><el-input-number v-model="form.sortOrder" :min="0" /></el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

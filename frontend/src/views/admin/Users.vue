<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminApi } from '@/api'

const list = ref<any[]>([])
const total = ref(0)
const query = reactive({ current: 1, size: 10 })
const dialog = ref(false)
const form = reactive({ username: '', password: '', nickname: '', role: 'ADMIN' })

async function load() {
  const res = await adminApi.users({ ...query })
  list.value = res.records
  total.value = res.total
}
onMounted(load)

async function create() {
  await adminApi.createUser({ ...form })
  ElMessage.success('账号已创建')
  dialog.value = false
  Object.assign(form, { username: '', password: '', nickname: '', role: 'ADMIN' })
  load()
}

async function changeRole(row: any, role: string) {
  await adminApi.updateUserRole(row.id, role)
  ElMessage.success('角色已更新')
  load()
}

async function toggle(row: any) {
  await adminApi.toggleUserStatus(row.id)
  load()
}

async function resetPwd(row: any) {
  const { value } = await ElMessageBox.prompt(`为「${row.username}」设置新密码`, '重置密码', { inputType: 'password' })
  await adminApi.resetUserPassword(row.id, value)
  ElMessage.success('密码已重置')
}

async function remove(row: any) {
  await ElMessageBox.confirm(`确认删除账号「${row.username}」？`, '提示', { type: 'warning' })
  await adminApi.deleteUser(row.id)
  load()
}
</script>

<template>
  <div class="space-y-4">
    <div class="flex items-center">
      <h2 class="text-lg font-semibold">账号权限管理</h2>
      <el-button type="primary" class="ml-auto" @click="dialog = true">新增管理员</el-button>
    </div>

    <el-table :data="list" border stripe>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="username" label="用户名" />
      <el-table-column prop="nickname" label="昵称" />
      <el-table-column label="角色" width="180">
        <template #default="{ row }">
          <el-select :model-value="row.role" size="small" @change="(v: string) => changeRole(row, v)">
            <el-option label="超级管理员" value="SUPER_ADMIN" />
            <el-option label="普通管理员" value="ADMIN" />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="110">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '启用' : '禁用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="260">
        <template #default="{ row }">
          <el-button size="small" @click="toggle(row)">{{ row.status === 1 ? '禁用' : '启用' }}</el-button>
          <el-button size="small" @click="resetPwd(row)">重置密码</el-button>
          <el-button size="small" type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="flex justify-center">
      <el-pagination v-model:current-page="query.current" :page-size="query.size" :total="total"
        layout="prev, pager, next, total" @current-change="load" />
    </div>

    <el-dialog v-model="dialog" title="新增管理员" width="440">
      <el-form :model="form" label-width="90px">
        <el-form-item label="用户名"><el-input v-model="form.username" /></el-form-item>
        <el-form-item label="密码"><el-input v-model="form.password" type="password" show-password /></el-form-item>
        <el-form-item label="昵称"><el-input v-model="form.nickname" /></el-form-item>
        <el-form-item label="角色">
          <el-select v-model="form.role" class="w-full">
            <el-option label="超级管理员" value="SUPER_ADMIN" />
            <el-option label="普通管理员" value="ADMIN" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" @click="create">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

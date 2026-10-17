<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { authApi } from '@/api'

const router = useRouter()
const loading = ref(false)
const form = reactive({ username: 'admin', password: 'admin123' })

async function submit() {
  loading.value = true
  try {
    const res = await authApi.login(form)
    localStorage.setItem('spectrace-token', res.token)
    localStorage.setItem('spectrace-user', JSON.stringify(res))
    ElMessage.success('登录成功')
    router.push('/admin/devices')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="min-h-screen flex items-center justify-center bg-slate-100 dark:bg-slate-900">
    <div class="w-96 rounded-2xl bg-white dark:bg-slate-800 p-8 shadow-lg">
      <div class="text-center mb-6">
        <div class="text-3xl">📱</div>
        <h1 class="text-xl font-bold mt-2 text-brand">SpecTrace 管理后台</h1>
      </div>
      <el-form :model="form" @submit.prevent="submit">
        <el-form-item>
          <el-input v-model="form.username" placeholder="用户名" size="large">
            <template #prefix><el-icon><User /></el-icon></template>
          </el-input>
        </el-form-item>
        <el-form-item>
          <el-input v-model="form.password" type="password" placeholder="密码" size="large" show-password @keyup.enter="submit">
            <template #prefix><el-icon><Lock /></el-icon></template>
          </el-input>
        </el-form-item>
        <el-button type="primary" size="large" class="w-full" :loading="loading" @click="submit">登 录</el-button>
      </el-form>
      <p class="mt-4 text-center text-xs text-slate-400">默认账号 admin / admin123</p>
    </div>
  </div>
</template>

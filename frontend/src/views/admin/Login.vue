<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowLeft, Laptop, Loader2, Lock, User } from '@lucide/vue'
import { toast } from 'vue-sonner'
import { authApi } from '@/api'
import { Button, Card, CardContent, Input, Label } from '@/components/ui'

const router = useRouter()
const loading = ref(false)
// 不预填任何账号：默认凭据写在代码里会被登录页直接暴露给访客（生产验证发现）
const form = reactive({ username: '', password: '' })

async function submit() {
  if (!form.username || !form.password) {
    toast.warning('请输入用户名和密码')
    return
  }
  loading.value = true
  try {
    const res = await authApi.login(form)
    localStorage.setItem('spectrace-token', res.token)
    localStorage.setItem('spectrace-user', JSON.stringify(res))
    toast.success('登录成功')
    // 初始密码或被重置的密码，先进改密页；守卫也会兜底拦截其它后台路由
    router.push(res.mustChangePassword ? '/admin/change-password' : '/admin/devices')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="flex min-h-screen items-center justify-center bg-muted/30 p-4">
    <div class="w-full max-w-sm space-y-4">
      <div class="flex flex-col items-center gap-2 text-center">
        <span class="flex h-11 w-11 items-center justify-center rounded-xl bg-primary text-primary-foreground">
          <Laptop class="h-5 w-5" />
        </span>
        <div>
          <h1 class="text-lg font-semibold tracking-tight">SpecTrace 管理后台</h1>
          <p class="text-sm text-muted-foreground">登录后管理品牌、机型、参数与采集任务</p>
        </div>
      </div>

      <Card>
        <CardContent class="p-5">
          <form class="space-y-4" @submit.prevent="submit">
            <div class="space-y-1.5">
              <Label for="username">用户名</Label>
              <div class="relative">
                <User class="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
                <Input id="username" v-model="form.username" class="pl-9" placeholder="请输入用户名" autocomplete="username" />
              </div>
            </div>

            <div class="space-y-1.5">
              <Label for="password">密码</Label>
              <div class="relative">
                <Lock class="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
                <Input
                  id="password"
                  v-model="form.password"
                  class="pl-9"
                  type="password"
                  placeholder="请输入密码"
                  autocomplete="current-password"
                />
              </div>
            </div>

            <Button type="submit" class="w-full" :disabled="loading">
              <Loader2 v-if="loading" class="animate-spin" />
              {{ loading ? '登录中…' : '登录' }}
            </Button>
          </form>
        </CardContent>
      </Card>

      <div class="text-center">
        <Button variant="link" size="sm" class="text-muted-foreground" @click="router.push('/')">
          <ArrowLeft />
          返回访客端
        </Button>
      </div>
    </div>
  </div>
</template>

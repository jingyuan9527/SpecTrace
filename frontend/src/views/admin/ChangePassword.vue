<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowLeft, KeyRound, Loader2, LogOut } from '@lucide/vue'
import { toast } from 'vue-sonner'
import { authApi } from '@/api'
import { Button, Card, CardContent, Input, Label } from '@/components/ui'

const router = useRouter()
const loading = ref(false)
const form = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })

/** 初始密码 / 被重置的密码：此时其它后台页面会被守卫拦回来 */
const forced = ref(readUser().mustChangePassword === true)
const username = readUser().username as string | undefined

function readUser(): Record<string, any> {
  try {
    return JSON.parse(localStorage.getItem('spectrace-user') || '{}')
  } catch {
    return {}
  }
}

async function submit() {
  if (!form.oldPassword || !form.newPassword) {
    toast.warning('请填写原密码与新密码')
    return
  }
  if (form.newPassword.length < 6) {
    toast.warning('新密码至少 6 位')
    return
  }
  if (form.newPassword !== form.confirmPassword) {
    toast.warning('两次输入的新密码不一致')
    return
  }
  loading.value = true
  try {
    await authApi.changePassword({ oldPassword: form.oldPassword, newPassword: form.newPassword })
    // token 与密码无关，改完无需重新登录，只要把本地的强制改密标记摘掉
    localStorage.setItem('spectrace-user', JSON.stringify({ ...readUser(), mustChangePassword: false }))
    forced.value = false
    toast.success('密码已更新')
    router.replace('/admin/devices')
  } finally {
    loading.value = false
  }
}

function logout() {
  localStorage.removeItem('spectrace-token')
  localStorage.removeItem('spectrace-user')
  router.replace('/admin/login')
}
</script>

<template>
  <div class="flex min-h-screen items-center justify-center bg-muted/30 p-4">
    <div class="w-full max-w-sm space-y-4">
      <div class="flex flex-col items-center gap-2 text-center">
        <span class="flex h-11 w-11 items-center justify-center rounded-xl bg-primary text-primary-foreground">
          <KeyRound class="h-5 w-5" />
        </span>
        <div>
          <h1 class="text-lg font-semibold tracking-tight">
            {{ forced ? '请先修改初始密码' : '修改密码' }}
          </h1>
          <p class="text-sm text-muted-foreground">
            {{
              forced
                ? `账号 ${username ?? ''} 仍在使用初始或管理员重置的密码，修改后才能进入后台`
                : '修改后立即生效，其它设备上的登录状态不受影响'
            }}
          </p>
        </div>
      </div>

      <Card>
        <CardContent class="p-5">
          <form class="space-y-4" @submit.prevent="submit">
            <div class="space-y-1.5">
              <Label for="old-password">原密码</Label>
              <Input
                id="old-password"
                v-model="form.oldPassword"
                type="password"
                placeholder="请输入当前密码"
                autocomplete="current-password"
              />
            </div>

            <div class="space-y-1.5">
              <Label for="new-password">新密码</Label>
              <Input
                id="new-password"
                v-model="form.newPassword"
                type="password"
                placeholder="至少 6 位"
                autocomplete="new-password"
              />
            </div>

            <div class="space-y-1.5">
              <Label for="confirm-password">确认新密码</Label>
              <Input
                id="confirm-password"
                v-model="form.confirmPassword"
                type="password"
                placeholder="再次输入新密码"
                autocomplete="new-password"
              />
            </div>

            <Button type="submit" class="w-full" :disabled="loading">
              <Loader2 v-if="loading" class="animate-spin" />
              {{ loading ? '提交中…' : '确认修改' }}
            </Button>
          </form>
        </CardContent>
      </Card>

      <div class="flex justify-center gap-2">
        <Button v-if="!forced" variant="link" size="sm" class="text-muted-foreground" @click="router.push('/admin/devices')">
          <ArrowLeft />
          返回后台
        </Button>
        <Button variant="link" size="sm" class="text-muted-foreground" @click="logout">
          <LogOut />
          退出登录
        </Button>
      </div>
    </div>
  </div>
</template>

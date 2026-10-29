<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { KeyRound, Loader2, Plus, Power, Shield, Trash2 } from '@lucide/vue'
import { toast } from 'vue-sonner'
import { adminApi } from '@/api'
import { usePagedList } from '@/composables/usePagedList'
import {
  Badge,
  Button,
  Card,
  CardContent,
  Dialog,
  Empty,
  Input,
  Label,
  Pagination,
  Select,
  Skeleton,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import PageHeader from '@/components/PageHeader.vue'

const { list, total, loading, query, load } = usePagedList({
  size: 10,
  fetcher: (params) => adminApi.users(params),
})

const dialog = ref(false)
const saving = ref(false)
const form = reactive({ username: '', password: '', nickname: '', role: 'ADMIN' })

const resetDialog = ref(false)
const resetTarget = ref<any>(null)
const resetPassword = ref('')
const resetting = ref(false)

const deleteTarget = ref<any>(null)
const deleting = ref(false)

const roles = [
  { label: '超级管理员', value: 'SUPER_ADMIN' },
  { label: '普通管理员', value: 'ADMIN' },
]

const roleLabel = (role: string) => (role === 'SUPER_ADMIN' ? '超级管理员' : '普通管理员')
/** 账号写操作仅超管可用：ADMIN 保留只读列表，避免页面展示一堆点了就 403 的按钮 */
const isSuperAdmin = computed(() => {
  try {
    return JSON.parse(localStorage.getItem('spectrace-user') || '{}').role === 'SUPER_ADMIN'
  } catch {
    return false
  }
})

function openCreate() {
  Object.assign(form, { username: '', password: '', nickname: '', role: 'ADMIN' })
  dialog.value = true
}

async function create() {
  if (!form.username.trim() || !form.password.trim()) {
    toast.warning('请填写用户名与密码')
    return
  }
  saving.value = true
  try {
    await adminApi.createUser({ ...form })
    toast.success('账号已创建')
    dialog.value = false
    await load()
  } finally {
    saving.value = false
  }
}

async function changeRole(row: any, role: string) {
  await adminApi.updateUserRole(row.id, role)
  toast.success('角色已更新')
  await load()
}

async function toggleStatus(row: any) {
  await adminApi.toggleUserStatus(row.id)
  toast.success(row.status === 1 ? '账号已禁用' : '账号已启用')
  await load()
}

function openReset(row: any) {
  resetTarget.value = row
  resetPassword.value = ''
  resetDialog.value = true
}

async function confirmReset() {
  if (resetPassword.value.length < 6) {
    toast.warning('密码至少 6 位')
    return
  }
  resetting.value = true
  try {
    await adminApi.resetUserPassword(resetTarget.value.id, resetPassword.value)
    toast.success('密码已重置')
    resetDialog.value = false
  } finally {
    resetting.value = false
  }
}

async function confirmDelete() {
  if (!deleteTarget.value) return
  deleting.value = true
  try {
    await adminApi.deleteUser(deleteTarget.value.id)
    toast.success('账号已删除')
    deleteTarget.value = null
    await load()
  } finally {
    deleting.value = false
  }
}
</script>

<template>
  <div class="space-y-4">
    <PageHeader title="账号权限" description="管理后台账号、角色与启用状态">
      <template #actions>
        <Button v-if="isSuperAdmin" size="sm" @click="openCreate">
          <Plus />
          新增管理员
        </Button>
      </template>
    </PageHeader>

    <Card>
      <CardContent class="p-0">
        <div v-if="loading" class="space-y-2 p-4">
          <Skeleton v-for="i in 5" :key="i" class="h-11" />
        </div>

        <Table v-else-if="list.length">
          <TableHeader>
            <TableRow class="hover:bg-transparent">
              <TableHead class="w-16">ID</TableHead>
              <TableHead>用户名</TableHead>
              <TableHead>昵称</TableHead>
              <TableHead class="w-44">角色</TableHead>
              <TableHead class="w-24">状态</TableHead>
              <TableHead class="w-64 text-right">操作</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            <TableRow v-for="row in list" :key="row.id">
              <TableCell class="text-muted-foreground">{{ row.id }}</TableCell>
              <TableCell class="font-medium">{{ row.username }}</TableCell>
              <TableCell class="text-muted-foreground">{{ row.nickname || '—' }}</TableCell>
              <TableCell>
                <Select
                  v-if="isSuperAdmin"
                  :model-value="row.role"
                  :options="roles"
                  class="h-8 w-36"
                  @update:model-value="changeRole(row, $event as string)"
                />
                <Badge v-else variant="secondary">{{ roleLabel(row.role) }}</Badge>
              </TableCell>
              <TableCell>
                <Badge :variant="row.status === 1 ? 'success' : 'secondary'">
                  {{ row.status === 1 ? '启用' : '禁用' }}
                </Badge>
              </TableCell>
              <TableCell class="text-right">
                <div v-if="isSuperAdmin" class="flex justify-end gap-1">
                  <Button variant="ghost" size="sm" @click="toggleStatus(row)">
                    <Power />
                    {{ row.status === 1 ? '禁用' : '启用' }}
                  </Button>
                  <Button variant="ghost" size="sm" @click="openReset(row)">
                    <KeyRound />
                    重置密码
                  </Button>
                  <Button
                    variant="ghost"
                    size="icon-sm"
                    class="text-destructive"
                    aria-label="删除账号"
                    @click="deleteTarget = row"
                  >
                    <Trash2 />
                  </Button>
                </div>
                <span v-else class="text-xs text-muted-foreground">仅超管可操作</span>
              </TableCell>
            </TableRow>
          </TableBody>
        </Table>

        <Empty v-else title="暂无管理员账号" description="新增账号并分配角色后可登录后台。">
          <template #icon><Shield class="mb-1 h-8 w-8 text-muted-foreground/40" /></template>
        </Empty>
      </CardContent>
    </Card>

    <Pagination v-if="total > query.size" v-model:page="query.current" :page-size="query.size" :total="total" />

    <Dialog v-model:open="dialog" title="新增管理员" class="max-w-md">
      <div class="space-y-4">
        <div class="space-y-1.5">
          <Label for="user-name">用户名</Label>
          <Input id="user-name" v-model="form.username" placeholder="登录用户名" autocomplete="off" />
        </div>
        <div class="space-y-1.5">
          <Label for="user-password">初始密码</Label>
          <Input id="user-password" v-model="form.password" type="password" placeholder="至少 6 位" autocomplete="new-password" />
        </div>
        <div class="space-y-1.5">
          <Label for="user-nick">昵称</Label>
          <Input id="user-nick" v-model="form.nickname" placeholder="展示名称" />
        </div>
        <div class="space-y-1.5">
          <Label for="user-role">角色</Label>
          <Select id="user-role" v-model="form.role" :options="roles" />
        </div>
      </div>
      <template #footer>
        <Button variant="outline" :disabled="saving" @click="dialog = false">取消</Button>
        <Button :disabled="saving" @click="create">{{ saving ? '创建中…' : '创建' }}</Button>
      </template>
    </Dialog>

    <Dialog v-model:open="resetDialog" title="重置密码" :description="`为「${resetTarget?.username ?? ''}」设置新密码`" class="max-w-sm">
      <div class="space-y-1.5">
        <Label for="reset-password">新密码</Label>
        <Input id="reset-password" v-model="resetPassword" type="password" placeholder="至少 6 位" autocomplete="new-password" />
      </div>
      <template #footer>
        <Button variant="outline" :disabled="resetting" @click="resetDialog = false">取消</Button>
        <Button :disabled="resetting" @click="confirmReset">
          <Loader2 v-if="resetting" class="animate-spin" />
          {{ resetting ? '重置中…' : '确认重置' }}
        </Button>
      </template>
    </Dialog>

    <ConfirmDialog
      :open="!!deleteTarget"
      title="删除账号"
      :description="`确认删除账号「${deleteTarget?.username ?? ''}」？该账号将立即失去登录权限。`"
      confirm-text="删除"
      destructive
      :loading="deleting"
      @update:open="deleteTarget = null"
      @confirm="confirmDelete"
    />
  </div>
</template>

import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import type { LoginResponse } from '@/types/api'

const TOKEN_KEY = 'spectrace-token'
const USER_KEY = 'spectrace-user'

export interface AuthUser {
  userId?: number
  username?: string
  nickname?: string | null
  /** SUPER_ADMIN / ADMIN；缺失时按最低权限处理 */
  role?: string
  mustChangePassword?: boolean
}

function readToken(): string {
  return localStorage.getItem(TOKEN_KEY) ?? ''
}

function readUser(): AuthUser {
  try {
    return JSON.parse(localStorage.getItem(USER_KEY) || '{}')
  } catch {
    return {}
  }
}

/**
 * 后台登录态的唯一来源。
 *
 * <p>用响应式状态而不是每次读 localStorage：401 清理后，页面上的管理员入口与菜单要立刻消失。
 * 落盘仍保留 localStorage，刷新后由这里恢复；其它模块不要再直接读写这两个 key。</p>
 */
export const useAuthStore = defineStore('auth', () => {
  const token = ref(readToken())
  const user = ref<AuthUser>(readUser())

  const isLoggedIn = computed(() => !!token.value)
  const isSuperAdmin = computed(() => user.value.role === 'SUPER_ADMIN')

  function setSession(res: LoginResponse) {
    token.value = res.token
    user.value = {
      userId: res.userId,
      username: res.username,
      nickname: res.nickname ?? null,
      role: res.role,
      mustChangePassword: res.mustChangePassword === true,
    }
    localStorage.setItem(TOKEN_KEY, res.token)
    localStorage.setItem(USER_KEY, JSON.stringify(user.value))
  }

  /** 服务端校验后回写：昵称 / 角色 / 改密标记以库内最新为准 */
  function updateUser(patch: Partial<AuthUser>) {
    user.value = { ...user.value, ...patch }
    localStorage.setItem(USER_KEY, JSON.stringify(user.value))
  }

  function clear() {
    token.value = ''
    user.value = {}
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(USER_KEY)
  }

  /** 其它标签页登录 / 登出 / 改资料后，按 localStorage 最新值对齐本地状态 */
  function syncFromStorage() {
    token.value = readToken()
    user.value = readUser()
  }

  // storage 事件只在「其它」标签页触发，本标签页写入收不到，不会造成回环；
  // key 为 null 表示 localStorage.clear()，一并按登出处理
  if (typeof window !== 'undefined') {
    window.addEventListener('storage', (event) => {
      if (event.key === TOKEN_KEY || event.key === USER_KEY || event.key === null) {
        syncFromStorage()
      }
    })
  }

  return { token, user, isLoggedIn, isSuperAdmin, setSession, updateUser, clear, syncFromStorage }
})

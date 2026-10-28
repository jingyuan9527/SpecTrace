import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { toast } from 'vue-sonner'
import { authApi } from '@/api'
import { resetPageMeta } from '@/lib/meta'

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    component: () => import('@/layouts/VisitorLayout.vue'),
    children: [
      { path: '', name: 'home', component: () => import('@/views/Home.vue') },
      { path: 'devices', name: 'devices', component: () => import('@/views/DeviceLibrary.vue') },
      { path: 'devices/:id', name: 'device-detail', component: () => import('@/views/DeviceDetail.vue') },
      { path: 'compare', name: 'compare', component: () => import('@/views/Compare.vue') },
      { path: 'trends', name: 'trends', component: () => import('@/views/Trends.vue') },
      // 旧链接重定向：/evolution 的系列上下文可以映射，/history 的品牌维度已废弃，落到默认视图
      {
        path: 'evolution',
        redirect: (to) => ({
          name: 'trends',
          query: to.query.seriesId ? { mode: 'cross', seriesId: String(to.query.seriesId) } : { mode: 'cross' },
        }),
      },
      { path: 'history', redirect: { name: 'trends', query: { mode: 'cross' } } },
      // 兜底 404：保留访客导航，未知路径不再渲染空白页
      { path: ':pathMatch(.*)*', name: 'not-found', component: () => import('@/views/NotFound.vue') },
    ],
  },
  {
    path: '/admin/login',
    name: 'admin-login',
    component: () => import('@/views/admin/Login.vue'),
  },
  {
    // 独立于 AdminLayout：处于强制改密状态时，后台任何页面都会被守卫挡回来
    path: '/admin/change-password',
    name: 'admin-change-password',
    component: () => import('@/views/admin/ChangePassword.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/admin',
    component: () => import('@/layouts/AdminLayout.vue'),
    meta: { requiresAuth: true },
    children: [
      { path: '', redirect: '/admin/devices' },
      { path: 'brands', name: 'admin-brands', component: () => import('@/views/admin/Brand.vue') },
      { path: 'series', name: 'admin-series', component: () => import('@/views/admin/Series.vue') },
      { path: 'health', name: 'admin-health', component: () => import('@/views/admin/Health.vue') },
      { path: 'params', name: 'admin-params', component: () => import('@/views/admin/Param.vue') },
      { path: 'devices', name: 'admin-devices', component: () => import('@/views/admin/Device.vue') },
      { path: 'agent-tasks', name: 'admin-agent-tasks', component: () => import('@/views/admin/AgentTasks.vue') },
      { path: 'ai-config', name: 'admin-ai-config', component: () => import('@/views/admin/AiConfig.vue') },
      { path: 'ai-image', name: 'admin-ai-image', component: () => import('@/views/admin/AiImage.vue') },
      { path: 'pending', name: 'admin-pending', component: () => import('@/views/admin/PendingData.vue') },
      { path: 'monitor', name: 'admin-monitor', component: () => import('@/views/admin/Monitor.vue') },
      { path: 'feedback', name: 'admin-feedback', component: () => import('@/views/admin/Feedback.vue') },
      { path: 'users', name: 'admin-users', component: () => import('@/views/admin/Users.vue') },
      { path: 'logs', name: 'admin-logs', component: () => import('@/views/admin/Logs.vue') },
      { path: 'maintenance', name: 'admin-maintenance', component: () => import('@/views/admin/Maintenance.vue') },
    ],
  },
]

const router = createRouter({
  // history 模式便于分享与搜索引擎收录；生产环境由后端把未知路径回退到 index.html
  history: createWebHistory(),
  routes,
})

/**
 * 本会话是否已向服务端确认过 token。
 * 整页刷新会重置，因此每次加载后台都校验一次；SPA 内跳转不重复请求，避免每个页面都多一次往返。
 */
let sessionChecked = false

function readUser(): Record<string, any> {
  try {
    return JSON.parse(localStorage.getItem('spectrace-user') || '{}')
  } catch {
    return {}
  }
}

router.beforeEach(async (to) => {
  if (!to.meta.requiresAuth) return true
  if (!localStorage.getItem('spectrace-token')) return { name: 'admin-login' }
  if (!sessionChecked) {
    try {
      // localStorage 里的 token 可能已过期或被吊销，仅凭它存在就放行会让后台先渲染再逐个请求报错
      const me = await authApi.me()
      sessionChecked = true
      // 昵称 / 角色 / 强制改密标记可能在后台被改过，以服务端返回为准刷新本地缓存
      localStorage.setItem(
        'spectrace-user',
        JSON.stringify({
          ...readUser(),
          username: me.username ?? readUser().username,
          userId: me.adminId ?? readUser().userId,
          role: me.role ?? readUser().role,
          mustChangePassword: me.mustChangePassword === true,
        }),
      )
    } catch {
      localStorage.removeItem('spectrace-token')
      localStorage.removeItem('spectrace-user')
      toast.error('登录已失效，请重新登录')
      return { name: 'admin-login' }
    }
  }
  // 初始密码或被管理员重置过：只放行改密页，避免没改密就先逛后台
  if (readUser().mustChangePassword === true && to.name !== 'admin-change-password') {
    return { name: 'admin-change-password' }
  }
  return true
})

// 详情页会在数据加载后覆盖标题，这里负责把其它页面的标题复位，避免残留
router.afterEach(() => resetPageMeta())

/**
 * 动态 import 失败（发版后旧 chunk 已被替换、或资源短暂不可达）时自动刷新一次。
 *
 * <p>用时间窗口而不是布尔标记：标记清得太早会无限刷新，清得太晚会让后续故障失去自愈机会。
 * 10 秒内重复失败说明不是缓存问题，改为提示用户手动刷新。</p>
 */
const CHUNK_RELOAD_AT = 'spectrace-chunk-reload-at'
router.onError((error) => {
  const message = String((error as Error)?.message ?? '')
  const isChunkError = /dynamically imported module|Importing a module script failed|ChunkLoadError/i.test(message)
  if (!isChunkError) return

  const now = Date.now()
  const last = Number(sessionStorage.getItem(CHUNK_RELOAD_AT) ?? 0)
  if (now - last < 10_000) {
    toast.error('页面资源加载失败，请手动刷新')
    return
  }
  console.warn('[router] 资源加载失败，刷新后重试', { message })
  sessionStorage.setItem(CHUNK_RELOAD_AT, String(now))
  window.location.reload()
})

export default router

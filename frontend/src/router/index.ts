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
      { path: 'history', name: 'history', component: () => import('@/views/History.vue') },
      { path: 'evolution', name: 'evolution', component: () => import('@/views/Evolution.vue') },
    ],
  },
  {
    path: '/admin/login',
    name: 'admin-login',
    component: () => import('@/views/admin/Login.vue'),
  },
  {
    path: '/admin',
    component: () => import('@/layouts/AdminLayout.vue'),
    meta: { requiresAuth: true },
    children: [
      { path: '', redirect: '/admin/devices' },
      { path: 'brands', name: 'admin-brands', component: () => import('@/views/admin/Brand.vue') },
      { path: 'series', name: 'admin-series', component: () => import('@/views/admin/Series.vue') },
      { path: 'params', name: 'admin-params', component: () => import('@/views/admin/Param.vue') },
      { path: 'devices', name: 'admin-devices', component: () => import('@/views/admin/Device.vue') },
      { path: 'agent-tasks', name: 'admin-agent-tasks', component: () => import('@/views/admin/AgentTasks.vue') },
      { path: 'ai-config', name: 'admin-ai-config', component: () => import('@/views/admin/AiConfig.vue') },
      { path: 'pending', name: 'admin-pending', component: () => import('@/views/admin/PendingData.vue') },
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

router.beforeEach(async (to) => {
  if (!to.meta.requiresAuth) return true
  if (!localStorage.getItem('spectrace-token')) return { name: 'admin-login' }
  if (sessionChecked) return true
  try {
    // localStorage 里的 token 可能已过期或被吊销，仅凭它存在就放行会让后台先渲染再逐个请求报错
    const me = await authApi.me()
    sessionChecked = true
    // 昵称 / 角色可能在后台被改过，以服务端返回为准刷新本地缓存
    const cached = JSON.parse(localStorage.getItem('spectrace-user') || '{}')
    localStorage.setItem(
      'spectrace-user',
      JSON.stringify({ ...cached, username: me.username ?? cached.username, userId: me.adminId ?? cached.userId }),
    )
  } catch {
    localStorage.removeItem('spectrace-token')
    localStorage.removeItem('spectrace-user')
    toast.error('登录已失效，请重新登录')
    return { name: 'admin-login' }
  }
  return true
})

// 详情页会在数据加载后覆盖标题，这里负责把其它页面的标题复位，避免残留
router.afterEach(() => resetPageMeta())

export default router

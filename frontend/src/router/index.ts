import { createRouter, createWebHashHistory, type RouteRecordRaw } from 'vue-router'

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
      { path: 'params', name: 'admin-params', component: () => import('@/views/admin/Param.vue') },
      { path: 'devices', name: 'admin-devices', component: () => import('@/views/admin/Device.vue') },
      { path: 'agent-tasks', name: 'admin-agent-tasks', component: () => import('@/views/admin/AgentTasks.vue') },
      { path: 'ai-config', name: 'admin-ai-config', component: () => import('@/views/admin/AiConfig.vue') },
      { path: 'pending', name: 'admin-pending', component: () => import('@/views/admin/PendingData.vue') },
      { path: 'users', name: 'admin-users', component: () => import('@/views/admin/Users.vue') },
      { path: 'logs', name: 'admin-logs', component: () => import('@/views/admin/Logs.vue') },
    ],
  },
]

const router = createRouter({
  history: createWebHashHistory(),
  routes,
})

router.beforeEach((to) => {
  if (to.meta.requiresAuth && !localStorage.getItem('spectrace-token')) {
    return { name: 'admin-login' }
  }
  return true
})

export default router

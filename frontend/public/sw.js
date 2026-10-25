/**
 * SpecTrace Service Worker（T89）：只做保守缓存，策略宁缺毋滥。
 *
 * - 静态资产（构建产物 / 品牌 logo / 机型图 / 上传图）：cache-first，命中即回；
 * - 页面导航：network-first，失败回落缓存的 index.html（history 路由壳）；
 * - /api 一律不缓存：限流、新鲜度与降级语义都由后端负责，SW 不掺和。
 */
const CACHE = 'spectrace-v1'
const ASSET_PATTERN = /^\/(assets\/|brands\/|device-images\/|uploads\/)/

self.addEventListener('install', () => {
  self.skipWaiting()
})

self.addEventListener('activate', (event) => {
  event.waitUntil(
    (async () => {
      const keys = await caches.keys()
      await Promise.all(keys.filter((key) => key !== CACHE).map((key) => caches.delete(key)))
      await self.clients.claim()
    })(),
  )
})

self.addEventListener('fetch', (event) => {
  const request = event.request
  if (request.method !== 'GET') return
  const url = new URL(request.url)
  if (url.origin !== location.origin) return
  if (url.pathname.startsWith('/api/')) return

  if (ASSET_PATTERN.test(url.pathname)) {
    event.respondWith(cacheFirst(request))
    return
  }
  if (request.mode === 'navigate') {
    event.respondWith(networkFirst(request))
  }
})

async function cacheFirst(request) {
  const cached = await caches.match(request)
  if (cached) return cached
  const response = await fetch(request)
  if (response.ok) {
    const cache = await caches.open(CACHE)
    cache.put(request, response.clone())
  }
  return response
}

async function networkFirst(request) {
  try {
    const response = await fetch(request)
    const cache = await caches.open(CACHE)
    cache.put('/index.html', response.clone())
    return response
  } catch (error) {
    const cached = (await caches.match(request)) || (await caches.match('/index.html'))
    if (cached) return cached
    throw error
  }
}

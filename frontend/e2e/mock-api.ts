import type { Page, Route } from '@playwright/test'

/**
 * E2E 用的内存版后端：拦截 /api/** 请求，让前端主链路可以脱离真实服务与数据库运行。
 * 数据结构保持与后端 VO 一致，接口按需增量实现。
 */
interface MockBrand {
  id: number
  name: string
  logo?: string
  sortOrder: number
  status: number
}

interface MockDevice {
  id: number
  brandId: number
  name: string
  releaseDate: string
  thumbnail: string
  status: number
}

const PARAMS: Record<number, Record<string, string>> = {
  7: { release_date: '2025-09-19', screen_size: '6.3', battery_capacity: '3600' },
  4: { release_date: '2024-09-20', screen_size: '6.1', battery_capacity: '3561' },
  9: { release_date: '2024-11-26', screen_size: '6.7', battery_capacity: '5000' },
}

export async function mockApi(page: Page) {
  const state = {
    brands: [
      { id: 1, name: 'Apple', logo: '/devices/placeholder.svg', sortOrder: 1, status: 1 },
      { id: 2, name: '华为', logo: '/devices/placeholder.svg', sortOrder: 2, status: 1 },
    ] as MockBrand[],
    devices: [
      { id: 7, brandId: 1, name: 'iPhone 17', releaseDate: '2025-09-19', thumbnail: '/devices/placeholder.svg', status: 1 },
      { id: 4, brandId: 1, name: 'iPhone 16', releaseDate: '2024-09-20', thumbnail: '/devices/placeholder.svg', status: 1 },
      { id: 9, brandId: 2, name: 'Mate 70', releaseDate: '2024-11-26', thumbnail: '/devices/placeholder.svg', status: 1 },
    ] as MockDevice[],
    pending: [
      {
        id: 1,
        taskId: 1,
        modelName: 'iPhone 18',
        brandName: 'Apple',
        sourceUrl: 'https://example.com/iphone-18',
        status: 'PENDING_REVIEW',
        createdAt: '2026-10-22T10:00:00',
        params: { release_date: '2026-09-18', screen_size: '6.4' },
      },
    ],
    tasks: [] as any[],
  }
  let nextBrandId = 10
  let nextDeviceId = 100

  const pageResult = (records: unknown[]) => ({
    total: records.length,
    current: 1,
    size: 12,
    records,
  })

  const handle = async (route: Route) => {
    const request = route.request()
    const url = new URL(request.url())
    const path = url.pathname.replace(/^\/api/, '')
    const method = request.method()
    const ok = (data: unknown) => route.fulfill({ json: { code: 0, message: 'success', data } })
    const keyword = (url.searchParams.get('keyword') || '').toLowerCase()
    const numberId = Number(path.split('/').filter(Boolean).pop())

    // ---------- 鉴权 ----------
    if (path === '/auth/login' && method === 'POST') {
      return ok({ token: 'e2e-token', username: 'admin', nickname: '测试管理员', role: 'SUPER_ADMIN' })
    }

    // ---------- 访客端 ----------
    if (path === '/public/brands') return ok(state.brands)
    if (path === '/public/param-groups') {
      return ok([
        { id: 1, name: '基础信息', sortOrder: 1 },
        { id: 2, name: '屏幕', sortOrder: 2 },
        { id: 3, name: '电池充电', sortOrder: 3 },
        { id: 4, name: '性能', sortOrder: 4 },
      ])
    }
    if (path === '/public/param-defs') {
      return ok([
        { id: 1, paramKey: 'release_date', paramName: '发布时间', groupId: 1, paramType: 'date' },
        { id: 3, paramKey: 'screen_size', paramName: '屏幕尺寸', groupId: 2, paramType: 'number', unit: '英寸' },
        { id: 11, paramKey: 'battery_capacity', paramName: '电池容量', groupId: 3, paramType: 'number', unit: 'mAh' },
        { id: 7, paramKey: 'chipset', paramName: '芯片', groupId: 4, paramType: 'text' },
      ])
    }
    if (path === '/public/devices/hot') return ok(state.devices.slice(0, 4))
    if (path === '/public/devices/suggest') {
      const matched = state.devices.filter((device) => device.name.toLowerCase().includes(keyword))
      return ok(matched.slice(0, 8).map((device) => ({ ...device, brandName: brandNameOf(device.brandId) })))
    }
    if (path === '/public/devices') {
      const brandId = Number(url.searchParams.get('brandId') || 0)
      const records = state.devices.filter(
        (device) =>
          (!brandId || device.brandId === brandId) && (!keyword || device.name.toLowerCase().includes(keyword)),
      )
      return ok(pageResult(records))
    }
    if (/^\/public\/devices\/\d+$/.test(path)) {
      const device = state.devices.find((item) => item.id === numberId)!
      return ok({
        device,
        brand: state.brands.find((brand) => brand.id === device.brandId),
        params: PARAMS[device.id] ?? {},
        paramMeta: { screen_size: { sourceUrl: 'https://example.com/spec', updatedAt: '2026-10-20T09:00:00' } },
        lastUpdated: '2026-10-20T09:00:00',
        variants: [{ id: 1, storage: '256GB', price: 5999, currency: 'CNY', priceType: 'LAUNCH' }],
      })
    }
    if (path === '/public/compare') {
      const ids = (url.searchParams.get('ids') || '').split(',').filter(Boolean).map(Number)
      const devices = ids.map((id) => state.devices.find((device) => device.id === id)!).filter(Boolean)
      const values = (key: string) => devices.map((device) => PARAMS[device.id]?.[key] ?? '')
      return ok({
        devices: devices.map((device) => ({
          ...device,
          brandName: brandNameOf(device.brandId),
          variants: [{ id: 1, storage: '256GB', price: 5999, currency: 'CNY' }],
          minPrice: 5999,
        })),
        groups: [
          {
            groupId: 2,
            groupName: '屏幕',
            params: [
              {
                paramDefId: 3,
                paramKey: 'screen_size',
                paramName: '屏幕尺寸',
                paramType: 'number',
                unit: '英寸',
                values: values('screen_size'),
                same: new Set(values('screen_size')).size <= 1,
              },
            ],
          },
        ],
      })
    }
    if (path === '/public/years') return ok([2025, 2024])
    if (path === '/public/history') {
      return ok({
        paramDefId: 3,
        paramName: '屏幕尺寸',
        unit: '英寸',
        numeric: true,
        series: [
          {
            brandId: 1,
            brandName: 'Apple',
            points: [
              { deviceId: 4, deviceName: 'iPhone 16', releaseDate: '2024-09-20', value: '6.1' },
              { deviceId: 7, deviceName: 'iPhone 17', releaseDate: '2025-09-19', value: '6.3' },
            ],
          },
        ],
      })
    }
    if (path === '/public/feedback' && method === 'POST') return ok(1)

    // ---------- 后台 ----------
    if (path === '/admin/brands') {
      if (method === 'POST') {
        const body = request.postDataJSON()
        state.brands.push({ id: nextBrandId++, sortOrder: state.brands.length + 1, status: 1, ...body })
      }
      return ok(state.brands)
    }
    if (/^\/admin\/brands\/\d+$/.test(path)) {
      if (method === 'DELETE') state.brands = state.brands.filter((brand) => brand.id !== numberId)
      return ok(true)
    }
    if (path === '/admin/param-groups') return ok([{ id: 1, name: '基础信息', sortOrder: 1 }])
    if (path === '/admin/param-defs') {
      return ok([{ id: 1, paramKey: 'release_date', paramName: '发布时间', groupId: 1, paramType: 'date', sortOrder: 1 }])
    }
    if (path === '/admin/devices') {
      if (method === 'POST') {
        const body = request.postDataJSON()
        const created = {
          id: nextDeviceId++,
          brandId: body.brandId,
          name: body.name,
          releaseDate: body.releaseDate || '',
          thumbnail: body.thumbnail || '',
          status: 1,
        }
        state.devices.push(created)
        return ok(created)
      }
      return ok(pageResult(state.devices))
    }
    if (path === '/admin/devices/import/template') return ok('')
    if (/^\/admin\/devices\/\d+$/.test(path)) {
      const device = state.devices.find((item) => item.id === numberId) ?? state.devices[0]
      return ok({ device, brand: state.brands[0], params: PARAMS[device.id] ?? {}, variants: [] })
    }
    if (path === '/admin/ai-models') return ok([])
    if (path === '/admin/agent-tasks') {
      if (method === 'POST') {
        const body = request.postDataJSON()
        const task = {
          id: state.tasks.length + 1,
          modelName: body.modelName,
          brandName: body.brandName ?? '',
          status: 'QUEUED',
          createdAt: '2026-10-22T10:00:00',
        }
        state.tasks.unshift(task)
        return ok(task)
      }
      return ok(pageResult(state.tasks))
    }
    if (path === '/admin/pending-data') return ok(pageResult(state.pending))
    if (/^\/admin\/pending-data\/\d+$/.test(path)) {
      const pending = state.pending.find((item) => item.id === numberId) ?? state.pending[0]
      // 新机型：库中没有同名记录，审核页按「新机型」展示
      return ok({ ...pending, newDevice: true, paramNames: { screen_size: '屏幕尺寸' }, variants: [] })
    }
    if (/^\/admin\/pending-data\/\d+\/approve$/.test(path)) return ok(7)
    if (/^\/admin\/pending-data\/\d+\/discard$/.test(path)) return ok(null)
    if (/^\/admin\/pending-data\/batch-/.test(path)) return ok({ success: 1, failed: 0, failures: [] })
    if (path === '/admin/users' || path === '/admin/logs' || path === '/admin/feedback') return ok(pageResult([]))
    if (path === '/admin/files/upload') {
      return ok({ url: '/uploads/e2e.webp', width: 800, height: 600, size: 120_000, contentType: 'image/webp' })
    }

    return ok(null)
  }

  function brandNameOf(brandId: number) {
    return state.brands.find((brand) => brand.id === brandId)?.name ?? ''
  }

  // 用 URL 谓词而不是 **/api/** 通配符：后者会连 Vite 的 /src/api/*.ts 模块一起拦掉
  await page.route((url) => url.pathname.startsWith('/api/'), handle)
}

import type { Page, Route } from '@playwright/test'
import type { AdminUser, Brand, Device, DeviceSeries, Feedback, ParamDef, ParamGroup, PendingData } from '../src/types/api'

/**
 * E2E 用的内存版后端：拦截 /api/** 请求，让前端主链路可以脱离真实服务与数据库运行。
 *
 * <p>数据结构直接引用 {@link '../src/types/api'} 的接口契约——后端 VO 改字段名时，
 * 这里会与前端一起在 typecheck 阶段报错，避免 mock 与真实后端悄悄漂移。</p>
 */
const PARAMS: Record<number, Record<string, string>> = {
  7: { release_date: '2025-09-19', screen_size: '6.3', battery_capacity: '3600', chipset: 'A19 Pro' },
  4: { release_date: '2024-09-20', screen_size: '6.1', battery_capacity: '3561' },
  9: { release_date: '2024-11-26', screen_size: '6.7', battery_capacity: '5000' },
  11: { release_date: '2025-09-19', screen_size: '6.3', battery_capacity: '3900', chipset: 'A19 Pro' },
}

export async function mockApi(page: Page, options: { mustChangePassword?: boolean } = {}) {
  // 强制改密标记必须可变：改密接口成功后置 false，/auth/me 才会放行
  let mustChangePassword = options.mustChangePassword === true
  const state = {
    brands: [
      { id: 1, name: 'Apple', logo: '/devices/placeholder.svg', sortOrder: 1, status: 1 },
      { id: 2, name: '华为', logo: '/devices/placeholder.svg', sortOrder: 2, status: 1 },
    ] as Brand[],
    series: [
      { id: 1, brandId: 1, name: 'iPhone 数字系列', sortOrder: 1, status: 1 },
      { id: 2, brandId: 2, name: 'Mate 系列', sortOrder: 2, status: 1 },
      { id: 3, brandId: 1, name: 'iPhone Pro 系列', sortOrder: 3, status: 1 },
    ] as DeviceSeries[],
    devices: [
      { id: 7, brandId: 1, name: 'iPhone 17', releaseDate: '2025-09-19', thumbnail: '/devices/placeholder.svg', status: 1, seriesId: 1 },
      { id: 4, brandId: 1, name: 'iPhone 16', releaseDate: '2024-09-20', thumbnail: '/devices/placeholder.svg', status: 1, seriesId: 1 },
      { id: 9, brandId: 2, name: 'Mate 70', releaseDate: '2024-11-26', thumbnail: '/devices/placeholder.svg', status: 1, seriesId: 2 },
      { id: 11, brandId: 1, name: 'iPhone 17 Pro', releaseDate: '2025-09-19', thumbnail: '/devices/placeholder.svg', status: 1, seriesId: 3 },
    ] as Device[],
    // 列表行附带 params，供审核详情直接复用
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
        riskLevel: 'HIGH',
      },
    ] as (PendingData & { params: Record<string, string> })[],
    tasks: [] as any[],
    users: [
      { id: 1, username: 'admin', nickname: '超级管理员', role: 'SUPER_ADMIN', status: 1 },
      { id: 2, username: 'editor', nickname: '内容编辑', role: 'ADMIN', status: 1 },
    ] as AdminUser[],
    groups: [{ id: 1, name: '基础信息', sortOrder: 1 }] as ParamGroup[],
    defs: [
      { id: 1, paramKey: 'release_date', paramName: '发布时间', groupId: 1, paramType: 'date', sortOrder: 1, enabled: 1 },
    ] as ParamDef[],
    subscriptions: [] as ({ id: number; seriesId: number; intervalMonths: number; aiModelId?: number | null; enabled: number; lastRunAt?: string | null })[],
    feedback: [
      {
        id: 1,
        deviceId: 7,
        deviceName: 'iPhone 17',
        paramName: '电池容量',
        currentValue: '3600',
        suggestedValue: '3650',
        description: '官网标注 3650mAh',
        status: 'OPEN',
        createdAt: '2026-10-22T09:00:00',
      },
    ] as Feedback[],
  }
  let nextBrandId = 10
  let nextDeviceId = 100
  let nextSeriesId = 10
  let nextUserId = 10
  let nextGroupId = 10
  let nextDefId = 10
  let nextSubscriptionId = 10

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
      return ok({
        token: 'e2e-token',
        username: 'admin',
        nickname: '测试管理员',
        role: 'SUPER_ADMIN',
        mustChangePassword,
      })
    }
    // 后台路由守卫会用 /auth/me 校验 token，缺了它所有后台用例都会被踢回登录页
    if (path === '/auth/me') {
      return ok({ adminId: 1, username: 'admin', role: 'SUPER_ADMIN', mustChangePassword, authenticated: true })
    }
    if (path === '/auth/password' && method === 'PUT') {
      mustChangePassword = false
      return ok(true)
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
        { id: 2, paramKey: 'launch_price', paramName: '首发价格', groupId: 1, paramType: 'number', unit: '元' },
        { id: 11, paramKey: 'battery_capacity', paramName: '电池容量', groupId: 3, paramType: 'number', unit: 'mAh' },
        { id: 18, paramKey: 'weight', paramName: '机身重量', groupId: 4, paramType: 'number', unit: 'g' },
        { id: 7, paramKey: 'chipset', paramName: '芯片', groupId: 4, paramType: 'text' },
        { id: 5, paramKey: 'refresh_rate', paramName: '屏幕刷新率', groupId: 2, paramType: 'number', unit: 'Hz' },
        { id: 12, paramKey: 'charging_power', paramName: '充电功率', groupId: 3, paramType: 'number', unit: 'W' },
        { id: 13, paramKey: 'camera_main', paramName: '主摄', groupId: 4, paramType: 'number', unit: '万像素' },
        { id: 14, paramKey: 'ram', paramName: '运行内存', groupId: 4, paramType: 'number', unit: 'GB' },
        { id: 15, paramKey: 'storage', paramName: '存储容量', groupId: 4, paramType: 'number', unit: 'GB' },
      ])
    }
    if (path === '/public/series') {
      const brandId = Number(url.searchParams.get('brandId') || 0)
      return ok(
        [
          { id: 1, brandId: 1, brandName: 'Apple', name: 'iPhone 数字系列', deviceCount: 2, startYear: 2024, endYear: 2025 },
          { id: 2, brandId: 2, brandName: '华为', name: 'Mate 系列', deviceCount: 1, startYear: 2024, endYear: 2024 },
          { id: 3, brandId: 1, brandName: 'Apple', name: 'iPhone Pro 系列', deviceCount: 1, startYear: 2025, endYear: 2025 },
        ].filter((series) => !brandId || series.brandId === brandId),
      )
    }
    if (path === '/public/series/overview') {
      const brandId = Number(url.searchParams.get('brandId') || 0)
      const published = state.devices.filter((device) => device.status === 1)
      const items = state.series
        .filter((series) => !brandId || series.brandId === brandId)
        .sort((left, right) => left.sortOrder - right.sortOrder)
        .map((series) => {
          const members = published
            .filter((device) => device.seriesId === series.id)
            .sort((left, right) => (left.releaseDate ?? '').localeCompare(right.releaseDate ?? ''))
          const years = members
            .map((device) => Number((device.releaseDate ?? '').slice(0, 4)))
            .filter((year) => Number.isFinite(year) && year > 0)
          const last = members[members.length - 1]
          return {
            id: series.id,
            brandId: series.brandId,
            brandName: brandNameOf(series.brandId),
            name: series.name,
            deviceCount: members.length,
            startYear: years.length ? Math.min(...years) : null,
            endYear: years.length ? Math.max(...years) : null,
            latest: last
              ? { id: last.id, name: last.name, thumbnail: last.thumbnail, releaseDate: last.releaseDate, launchPrice: 5999 }
              : null,
            screenTrend: members
              .filter((device) => device.releaseDate && PARAMS[device.id]?.screen_size)
              .map((device) => ({
                year: Number(device.releaseDate!.slice(0, 4)),
                value: Number(PARAMS[device.id]!.screen_size),
                deviceName: device.name,
              })),
            priceTrend: members
              .filter((device) => device.releaseDate)
              .map((device) => ({ year: Number(device.releaseDate!.slice(0, 4)), value: 5999, deviceName: device.name })),
          }
        })
      return ok({
        items,
        unassignedCount: published.filter((device) => !device.seriesId && (!brandId || device.brandId === brandId))
          .length,
      })
    }
    if (/^\/public\/series\/\d+\/evolution$/.test(path)) {
      const seriesId = Number(path.split('/')[3])
      const defIds = (url.searchParams.get('paramDefIds') || '')
        .split(',')
        .map(Number)
        .filter(Boolean)
      const seriesMeta: Record<number, { name: string; brandId: number; brandName: string; devices: { id: number; name: string; releaseDate: string; thumbnail: string; launchPrice: number }[] }> = {
        1: {
          name: 'iPhone 数字系列', brandId: 1, brandName: 'Apple',
          devices: [
            { id: 4, name: 'iPhone 16', releaseDate: '2024-09-20', thumbnail: '/devices/placeholder.svg', launchPrice: 5999 },
            { id: 7, name: 'iPhone 17', releaseDate: '2025-09-19', thumbnail: '/devices/placeholder.svg', launchPrice: 5999 },
          ],
        },
        2: {
          name: 'Mate 系列', brandId: 2, brandName: '华为',
          devices: [{ id: 9, name: 'Mate 70', releaseDate: '2024-11-26', thumbnail: '/devices/placeholder.svg', launchPrice: 5499 }],
        },
        3: {
          name: 'iPhone Pro 系列', brandId: 1, brandName: 'Apple',
          devices: [{ id: 11, name: 'iPhone 17 Pro', releaseDate: '2025-09-19', thumbnail: '/devices/placeholder.svg', launchPrice: 8999 }],
        },
      }
      const meta = seriesMeta[seriesId] ?? seriesMeta[1]
      const devices = meta.devices
      // 数值参数与文本参数分开造：数值覆盖折线，文本覆盖值序列展示分支，其余按「无数据参数」返回
      const numericMeta: Record<number, { paramKey: string; paramName: string; unit: string; values: number[] }> = {
        3: { paramKey: 'screen_size', paramName: '屏幕尺寸', unit: '英寸', values: [6.1, 6.3] },
        11: { paramKey: 'battery_capacity', paramName: '电池容量', unit: 'mAh', values: [3561, 3600] },
        18: { paramKey: 'weight', paramName: '机身重量', unit: 'g', values: [170, 172] },
        2: { paramKey: 'launch_price', paramName: '首发价格', unit: '元', values: [5999, 5999] },
      }
      const textMeta: Record<number, { paramKey: string; paramName: string; values: string[] }> = {
        7: { paramKey: 'chipset', paramName: '芯片', values: ['A18', 'A19 Pro'] },
      }
      const params = defIds.map((defId) => {
        const numeric = numericMeta[defId]
        const text = textMeta[defId]
        if (numeric) {
          return {
            paramDefId: defId,
            paramKey: numeric.paramKey,
            paramName: numeric.paramName,
            unit: numeric.unit,
            numeric: true,
            missingCount: 0,
            points: devices.map((device, index) => ({
              deviceId: device.id,
              raw: String(numeric.values[index]),
              value: numeric.values[index],
              conflict: false,
            })),
          }
        }
        if (text) {
          return {
            paramDefId: defId,
            paramKey: text.paramKey,
            paramName: text.paramName,
            numeric: false,
            missingCount: 0,
            points: devices.map((device, index) => ({
              deviceId: device.id,
              raw: text.values[index],
              value: null,
              conflict: false,
            })),
          }
        }
        return {
          paramDefId: defId,
          paramName: '未收录参数',
          numeric: false,
          missingCount: devices.length,
          points: devices.map((device) => ({ deviceId: device.id, raw: null, value: null, conflict: false })),
        }
      })
      return ok({
        seriesId,
        seriesName: meta.name,
        brandId: meta.brandId,
        brandName: meta.brandName,
        devices,
        params,
      })
    }
    if (path === '/public/series/generation') {
      const brandId = Number(url.searchParams.get('brandId') || 0)
      const year = Number(url.searchParams.get('year') || 0)
      // 与真实后端同口径：按系列占位，当年无新机的系列 device=null 仍返回空列
      const deviceOf = (id: number, name: string, date: string, launchPrice: number) => ({
        id, name, releaseDate: date, thumbnail: '/devices/placeholder.svg', launchPrice,
      })
      const param = (paramDefId: number, paramName: string, unit: string, value: number | null) => ({
        paramDefId,
        paramName,
        unit,
        numeric: value !== null,
        value,
        raw: value === null ? null : String(value),
        conflict: false,
      })
      const items =
        brandId === 1
          ? [
              {
                seriesId: 1,
                seriesName: 'iPhone 数字系列',
                device: year === 2025 ? deviceOf(7, 'iPhone 17', '2025-09-19', 5999) : deviceOf(4, 'iPhone 16', '2024-09-20', 5999),
                params:
                  year === 2025
                    ? [param(3, '屏幕尺寸', '英寸', 6.3), param(11, '电池容量', 'mAh', 3600), param(18, '机身重量', 'g', 172), param(2, '首发价格', '元', 5999)]
                    : [param(3, '屏幕尺寸', '英寸', 6.1), param(11, '电池容量', 'mAh', 3561), param(18, '机身重量', 'g', 170), param(2, '首发价格', '元', 5999)],
              },
              {
                seriesId: 3,
                seriesName: 'iPhone Pro 系列',
                device: year === 2025 ? deviceOf(11, 'iPhone 17 Pro', '2025-09-19', 8999) : null,
                params:
                  year === 2025
                    ? [param(3, '屏幕尺寸', '英寸', 6.3), param(11, '电池容量', 'mAh', 3900), param(18, '机身重量', 'g', 199), param(2, '首发价格', '元', 8999)]
                    : [param(3, '屏幕尺寸', '英寸', null), param(11, '电池容量', 'mAh', null), param(18, '机身重量', 'g', null), param(2, '首发价格', '元', null)],
              },
            ]
          : [
              {
                seriesId: 2,
                seriesName: 'Mate 系列',
                device: year === 2024 ? deviceOf(9, 'Mate 70', '2024-11-26', 5499) : null,
                params:
                  year === 2024
                    ? [param(3, '屏幕尺寸', '英寸', 6.7), param(11, '电池容量', 'mAh', 5000), param(18, '机身重量', 'g', 209), param(2, '首发价格', '元', 5499)]
                    : [param(3, '屏幕尺寸', '英寸', null), param(11, '电池容量', 'mAh', null), param(18, '机身重量', 'g', null), param(2, '首发价格', '元', null)],
              },
            ]
      return ok({ brandId, brandName: brandId === 1 ? 'Apple' : '华为', year, items })
    }
    if (/^\/public\/series\/\d+\/social-copy$/.test(path)) {
      return ok({
        available: true,
        cached: false,
        style: 'xiaohongshu',
        title: 'iPhone 数字系列 2 代进化',
        summary: '屏幕由 6.1 增至 6.3 英寸，电池容量提升 39mAh。',
        points: ['屏幕尺寸 +3.3%'],
        tags: ['#iPhone', '#数码'],
      })
    }
    if (/^\/public\/series\/\d+\/insight$/.test(path)) {
      // GET 只读缓存：未生成过时只有 available，前端据此决定是否展示入口
      if (method === 'GET') return ok({ available: true })
      return ok({
        available: true,
        cached: false,
        summary: '屏幕由 6.1 增至 6.3 英寸，电池容量小幅提升 39mAh。',
        points: ['屏幕尺寸提升明显，增幅约 3.3%', '电池容量提升有限，仅增加 39mAh'],
        generatedAt: '2026-10-23T10:00:00',
      })
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
      // 与真实后端同口径：系列只取已上架成员，按发布时间升序（含本机）
      const members = state.devices
        .filter((item) => item.seriesId && item.seriesId === device.seriesId && item.status === 1)
        .sort((left, right) => (left.releaseDate ?? '').localeCompare(right.releaseDate ?? ''))
      const series = state.series.find((item) => item.id === device.seriesId)
      return ok({
        device,
        brand: state.brands.find((brand) => brand.id === device.brandId),
        series: series ? { id: series.id, name: series.name } : null,
        seriesDevices: members.map((item) => ({
          id: item.id,
          name: item.name,
          thumbnail: item.thumbnail,
          releaseDate: item.releaseDate,
          launchPrice: 5999,
        })),
        params: PARAMS[device.id] ?? {},
        paramMeta: {
          screen_size: { paramDefId: 3, sourceUrl: 'https://example.com/spec', updatedAt: '2026-10-20T09:00:00' },
          battery_capacity: { paramDefId: 11, updatedAt: '2026-10-20T09:00:00' },
        },
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
    if (path === '/public/compare/insight') {
      // 未生成时只告诉前端入口可用；生成后按真实后端的「差异不足」分支返回固定结论
      if (method === 'GET') return ok({ available: true, failed: false, cached: false, summary: null, points: [] })
      return ok({
        available: true,
        failed: false,
        cached: false,
        summary: '两台机型的参数高度接近，差异主要集中在屏幕尺寸，选购时按手感与预算取舍即可。',
        points: ['屏幕尺寸相差 0.2 英寸', '电池容量与发布时间接近'],
        generatedAt: '2026-10-23T10:00:00',
      })
    }
    if (path === '/public/years') return ok([2025, 2024])
    if (path === '/public/feedback' && method === 'POST') {
      const body = request.postDataJSON() as Record<string, unknown>
      const id = Math.max(0, ...state.feedback.map((item) => item.id)) + 1
      const device = state.devices.find((item) => item.id === body.deviceId)
      state.feedback.push({
        id,
        deviceId: (body.deviceId as number) ?? device?.id ?? 0,
        deviceName: device?.name ?? null,
        paramKey: (body.paramKey as string) ?? null,
        currentValue: (body.currentValue as string) ?? null,
        suggestedValue: (body.suggestedValue as string) ?? null,
        description: (body.description as string) ?? null,
        status: 'OPEN',
        createdAt: '2026-10-25T10:00:00',
        updatedAt: '2026-10-25T10:00:00',
      } as Feedback)
      return ok(id)
    }
    if (/^\/public\/feedback\/\d+$/.test(path)) {
      const target = state.feedback.find((item) => item.id === numberId)
      if (!target) return route.fulfill({ status: 404, json: { code: 404, message: '反馈不存在，请核对编号' } })
      // 对外只暴露状态与时间，与真实后端的 FeedbackStatusVO 同口径
      return ok({ id: target.id, status: target.status, createdAt: target.createdAt, updatedAt: target.updatedAt })
    }

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
    if (path === '/admin/series/unassigned') {
      return ok(
        state.devices
          .filter((device) => !device.seriesId)
          .map((device) => ({ ...device, brandName: brandNameOf(device.brandId) })),
      )
    }
    if (path === '/admin/series/assign' && method === 'POST') {
      const body = request.postDataJSON()
      const ids: number[] = body.deviceIds ?? []
      state.devices.forEach((device) => {
        if (ids.includes(device.id)) device.seriesId = body.seriesId ?? undefined
      })
      return ok(ids.length)
    }
    if (/^\/admin\/series\/\d+\/merge$/.test(path)) {
      const sourceId = Number(path.split('/')[3])
      const body = request.postDataJSON()
      const moved = state.devices.filter((device) => device.seriesId === sourceId)
      moved.forEach((device) => {
        device.seriesId = body.targetId
      })
      state.series = state.series.filter((series) => series.id !== sourceId)
      return ok(moved.length)
    }
    if (path === '/admin/series') {
      if (method === 'POST') {
        const body = request.postDataJSON()
        // id 放在展开之后：真实后端会忽略请求体里的 id，mock 也要保证主键由服务端生成
        const created: DeviceSeries = { sortOrder: state.series.length + 1, status: 1, ...body, id: nextSeriesId++ }
        state.series.push(created)
        return ok(created)
      }
      return ok(
        [...state.series]
          .sort((left, right) => left.sortOrder - right.sortOrder || left.id - right.id)
          .map((series) => {
            const members = state.devices.filter((device) => device.seriesId === series.id)
            const years = members
              .map((device) => Number((device.releaseDate ?? '').slice(0, 4)))
              .filter((year) => Number.isFinite(year) && year > 0)
            return {
              ...series,
              brandName: brandNameOf(series.brandId),
              deviceCount: members.length,
              // 后台完善度口径与真实后端一致：含草稿，有任一参数值即计入
              paramDeviceCount: members.filter(
                (device) => Object.keys(PARAMS[device.id] ?? {}).length > 0,
              ).length,
              startYear: years.length ? Math.min(...years) : null,
              endYear: years.length ? Math.max(...years) : null,
            }
          }),
      )
    }
    if (/^\/admin\/series\/\d+\/devices-without-params$/.test(path)) {
      const seriesId = Number(path.split('/')[3])
      return ok(
        state.devices
          .filter((device) => device.seriesId === seriesId && Object.keys(PARAMS[device.id] ?? {}).length === 0)
          .map((device) => ({
            id: device.id,
            name: device.name,
            status: device.status,
            releaseDate: device.releaseDate,
            thumbnail: device.thumbnail,
          })),
      )
    }
    if (/^\/admin\/series\/\d+\/subscription$/.test(path)) {
      const seriesId = Number(path.split('/')[3])
      if (method === 'PUT') {
        const body = request.postDataJSON()
        const existing = state.subscriptions.find((item) => item.seriesId === seriesId)
        const target =
          existing ?? ({ id: nextSubscriptionId++, seriesId, intervalMonths: 3, enabled: 1, lastRunAt: null } as (typeof state.subscriptions)[number])
        Object.assign(target, body, { enabled: body.enabled ? 1 : 0 })
        if (!existing) state.subscriptions.push(target)
        return ok(target)
      }
      return ok(state.subscriptions.find((item) => item.seriesId === seriesId) ?? null)
    }
    if (/^\/admin\/series\/\d+\/stale-devices$/.test(path)) {
      // T87 重抓全系列：固定把 iPhone 16 当作「陈旧」目标，验证弹窗与提交链路
      const seriesId = Number(path.split('/')[3])
      const member = state.devices.find((device) => device.seriesId === seriesId && device.id === 4)
      return ok(
        member
          ? [
              {
                id: member.id,
                name: member.name,
                status: member.status,
                releaseDate: member.releaseDate,
                thumbnail: member.thumbnail,
              },
            ]
          : [],
      )
    }
    if (/^\/admin\/series\/\d+$/.test(path)) {
      if (method === 'PUT') {
        const body = request.postDataJSON()
        const target = state.series.find((series) => series.id === numberId)
        if (target) Object.assign(target, body)
        return ok(target ?? true)
      }
      if (method === 'DELETE') state.series = state.series.filter((series) => series.id !== numberId)
      return ok(true)
    }
    if (path === '/admin/param-groups') {
      if (method === 'POST') {
        const body = request.postDataJSON()
        const created = { id: nextGroupId++, name: body.name, sortOrder: body.sortOrder ?? state.groups.length + 1 }
        state.groups.push(created)
        return ok(created)
      }
      return ok(state.groups)
    }
    if (/^\/admin\/param-groups\/\d+$/.test(path)) {
      const id = Number(path.split('/')[3])
      if (method === 'PUT') {
        const target = state.groups.find((group) => group.id === id)
        if (target) Object.assign(target, request.postDataJSON())
        return ok(target ?? true)
      }
      if (method === 'DELETE') state.groups = state.groups.filter((group) => group.id !== id)
      return ok(true)
    }
    if (path === '/admin/param-defs') {
      if (method === 'POST') {
        const body = request.postDataJSON()
        const created = {
          id: nextDefId++,
          paramKey: body.paramKey,
          paramName: body.paramName,
          groupId: body.groupId,
          paramType: body.paramType ?? 'text',
          unit: body.unit ?? '',
          sortOrder: body.sortOrder ?? 99,
          enabled: body.enabled ?? 1,
        }
        state.defs.push(created)
        return ok(created)
      }
      const groupId = Number(url.searchParams.get('groupId') || 0)
      return ok(state.defs.filter((def) => !groupId || def.groupId === groupId))
    }
    if (/^\/admin\/param-defs\/\d+$/.test(path)) {
      const id = Number(path.split('/')[3])
      if (method === 'PUT') {
        const target = state.defs.find((def) => def.id === id)
        if (target) Object.assign(target, request.postDataJSON())
        return ok(target ?? true)
      }
      if (method === 'DELETE') state.defs = state.defs.filter((def) => def.id !== id)
      return ok(true)
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
          status: body.status ?? 1,
          seriesId: body.seriesId ?? undefined,
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
          taskType: body.taskType ?? 'DEVICE_DETAIL',
          status: body.status ?? 'QUEUED',
          createdAt: '2026-10-22T10:00:00',
        }
        state.tasks.unshift(task)
        return ok(task)
      }
      return ok(pageResult(state.tasks))
    }
    if (/^\/admin\/agent-tasks\/\d+\/discovery$/.test(path)) {
      return ok({
        taskId: numberId,
        seriesName: 'iPhone 17 系列',
        models: [
          { name: '脏数据机型', releaseDate: '2025-01-01' },
          { name: '库中已有机型', releaseDate: '2024-09-20', existing: true },
        ],
      })
    }
    if (/^\/admin\/agent-tasks\/\d+\/discard-discovery$/.test(path)) {
      const taskId = Number(path.split('/')[3])
      const task = state.tasks.find((item) => item.id === taskId)
      if (task) task.status = 'DISCARDED'
      return ok(true)
    }
    if (path === '/admin/pending-data') return ok(pageResult(state.pending))
    if (/^\/admin\/pending-data\/\d+$/.test(path)) {
      const pending = state.pending.find((item) => item.id === numberId) ?? state.pending[0]
      // 新机型：库中没有同名记录，审核页按「新机型」展示；T90 附带采集产品图验证预览
      return ok({
        ...pending,
        newDevice: true,
        paramNames: { screen_size: '屏幕尺寸' },
        variants: [],
        imageUrl: '/devices/placeholder.svg',
      })
    }
    if (/^\/admin\/pending-data\/\d+\/approve$/.test(path)) return ok(7)
    if (/^\/admin\/pending-data\/\d+\/discard$/.test(path)) return ok(null)
    if (/^\/admin\/pending-data\/batch-/.test(path)) return ok({ success: 1, failed: 0, failures: [] })
    if (path === '/admin/users') {
      if (method === 'POST') {
        const body = request.postDataJSON()
        const created = {
          id: nextUserId++,
          username: body.username,
          nickname: body.nickname ?? '',
          role: body.role ?? 'ADMIN',
          status: 1,
        }
        state.users.push(created)
        return ok(created)
      }
      return ok(pageResult(state.users))
    }
    if (/^\/admin\/users\/\d+\/(role|status)$/.test(path)) {
      const id = Number(path.split('/')[3])
      const target = state.users.find((user) => user.id === id)
      if (target) {
        if (path.endsWith('/role')) target.role = url.searchParams.get('role') ?? target.role
        else target.status = target.status === 1 ? 0 : 1
      }
      return ok(target ?? true)
    }
    if (/^\/admin\/users\/\d+\/password$/.test(path)) return ok(true)
    if (/^\/admin\/users\/\d+$/.test(path)) {
      if (method === 'DELETE') state.users = state.users.filter((user) => user.id !== numberId)
      return ok(true)
    }
    if (path === '/admin/badges') {
      return ok({
        openFeedback: state.feedback.filter((item) => item.status === 'OPEN').length,
        pendingReview: state.pending.filter((item) => item.status === 'PENDING_REVIEW').length,
        pendingOldestHours: 2,
      })
    }
    if (path === '/admin/metrics/collect') {
      const today = new Date('2026-10-25')
      const daily = Array.from({ length: 30 }, (_, index) => {
        const day = new Date(today.getTime() - (29 - index) * 86400_000).toISOString().slice(0, 10)
        const total = index === 20 ? 4 : 2
        return { date: day, total, approved: total - 1, discarded: 0, failed: 1 }
      })
      return ok({
        windowDays: 30,
        total: 62,
        approved: 32,
        discarded: 0,
        failed: 30,
        queued: 1,
        running: 0,
        pendingReview: 1,
        successRate: 0.52,
        p50Ms: 61_000,
        p95Ms: 240_000,
        nearTimeout: 2,
        imagePending: 1,
        daily,
        modelStats: [
          { aiModelId: 1, label: 'DashScope / qwen-max', total: 40, approved: 32, failed: 8, successRate: 0.8, avgMs: 55_000, p95Ms: 180_000, nearTimeout: 1 },
          { aiModelId: 2, label: 'OpenRouter / random', total: 22, approved: 0, failed: 22, successRate: 0, avgMs: 290_000, p95Ms: 299_000, nearTimeout: 1 },
        ],
      })
    }
    if (path === '/admin/maintenance/backups') {
      return ok({ enabled: true, dir: '/data/backups', keep: 7, nextRunAt: '2026-10-26T03:30:00', backups: [] })
    }
    if (path === '/admin/maintenance/retention') {
      return ok({
        enabled: true,
        cron: '0 0 4 * * *',
        taskDays: 30,
        pendingDays: 30,
        operationLogDays: 90,
        viewDailyDays: 90,
        searchLogDays: 90,
        nextRunAt: '2026-10-26T04:00:00',
        counts: {},
      })
    }
    if (path === '/admin/metrics/runtime') {
      return ok({
        caches: [
          { name: 'brands', hitCount: 40, missCount: 2, hitRate: 0.95, estimatedSize: 7, evictionCount: 0 },
          { name: 'paramMeta', hitCount: 30, missCount: 3, hitRate: 0.91, estimatedSize: 46, evictionCount: 0 },
        ],
        rateLimitRejected: { '429:/api/public/devices': 3 },
      })
    }
    if (path === '/admin/metrics/traffic') {
      const today = new Date('2026-10-25')
      const daily = Array.from({ length: 30 }, (_, index) => ({
        date: new Date(today.getTime() - (29 - index) * 86400_000).toISOString().slice(0, 10),
        pv: 10 + (index % 7),
      }))
      return ok({
        windowDays: 30,
        daily,
        weekPv: 70,
        lastWeekPv: 60,
        weekOverWeek: 0.17,
        topDevices: [
          { deviceId: 7, deviceName: 'iPhone 17', pv: 32 },
          { deviceId: 9, deviceName: 'Mate 70', pv: 21 },
        ],
        compareInitiated: 8,
      })
    }
    if (path === '/admin/metrics/search') {
      return ok({
        windowDays: 30,
        totalSearches: 42,
        zeroSearches: 9,
        zeroRate: 0.21,
        topKeywords: [
          { keyword: 'iPhone 18', searchCount: 20, zeroCount: 0 },
          { keyword: 'Mate 80', searchCount: 12, zeroCount: 6 },
          { keyword: '小米 17', searchCount: 10, zeroCount: 3 },
        ],
        zeroTop: [
          { keyword: 'Mate 80', searchCount: 12, zeroCount: 6 },
          { keyword: '小米 17', searchCount: 10, zeroCount: 3 },
        ],
      })
    }
    if (path === '/admin/data-health') {
      // 机型 7 视为已有参数，其余进缺参排行；Mate 70 给 PV 以验证排序字段回显
      const devices = state.devices
      return ok({
        totalDevices: devices.length,
        publishedDevices: devices.filter((item) => item.status === 1).length,
        devicesWithParams: 1,
        coverageRate: Number((1 / Math.max(devices.length, 1)).toFixed(4)),
        enabledParamDefs: 3,
        approvedLast30d: 1,
        openFeedback: state.feedback.filter((item) => item.status === 'OPEN').length,
        paramCoverage: [
          { paramDefId: 1, paramKey: 'release_date', paramName: '发布时间', groupName: '基础信息', deviceCount: 4 },
          { paramDefId: 3, paramKey: 'screen_size', paramName: '屏幕尺寸', groupName: '基础信息', deviceCount: 3 },
          { paramDefId: 11, paramKey: 'battery_capacity', paramName: '电池容量', groupName: '基础信息', deviceCount: 2 },
        ],
        missingDevices: devices
          .filter((item) => item.id !== 7)
          .map((item) => ({
            id: item.id,
            name: item.name,
            brandId: item.brandId,
            brandName: brandNameOf(item.brandId),
            seriesId: item.seriesId ?? null,
            seriesName: state.series.find((series) => series.id === item.seriesId)?.name ?? null,
            status: item.status,
            releaseDate: item.releaseDate,
            pv30d: item.id === 9 ? 5 : null,
            haveParams: 0,
            totalParams: 3,
          })),
      })
    }
    if (path === '/admin/feedback') return ok(pageResult(state.feedback))
    if (/^\/admin\/feedback\/\d+\/status$/.test(path)) {
      const id = Number(path.split('/')[3])
      const target = state.feedback.find((item) => item.id === id)
      if (target) {
        target.status = url.searchParams.get('status') ?? target.status
        target.handlerNote = url.searchParams.get('note') ?? undefined
        target.updatedAt = '2026-10-25T12:00:00'
      }
      return ok(target ?? true)
    }
    if (path === '/admin/logs') return ok(pageResult([]))
    if (path === '/admin/files/upload') {
      return ok({ url: '/uploads/e2e.webp', width: 800, height: 600, size: 120_000, contentType: 'image/webp' })
    }

    // 未 mock 的接口直接失败：静默 ok(null) 会让「真实后端新增/改名接口而 mock 未跟上」在测试里隐形
    console.error(`[mock-api] 未实现的接口: ${method} ${path}`)
    return route.fulfill({ status: 404, json: { code: 404, message: `[mock-api] 未实现的接口: ${method} ${path}` } })
  }

  function brandNameOf(brandId: number) {
    return state.brands.find((brand) => brand.id === brandId)?.name ?? ''
  }

  // 用 URL 谓词而不是 **/api/** 通配符：后者会连 Vite 的 /src/api/*.ts 模块一起拦掉
  await page.route((url) => url.pathname.startsWith('/api/'), handle)
}

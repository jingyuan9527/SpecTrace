import { expect, test } from '@playwright/test'
import { mockApi } from './mock-api'

/** 后台系列管理：新增 → 编辑 → 合并（迁移机型并删源系列）→ 删除空系列。 */
test('后台：系列新增、编辑、合并、删除', async ({ page }) => {
  await mockApi(page)

  await page.goto('/admin/login')
  await page.getByLabel('用户名').fill('admin')
  await page.getByLabel('密码').fill('admin123')
  await page.getByRole('button', { name: '登录' }).click()
  await expect(page).toHaveURL(/\/admin\/devices/)

  await page.goto('/admin/series')
  await expect(page.getByRole('heading', { name: '系列管理' })).toBeVisible()
  await expect(page.getByRole('row', { name: /iPhone 数字系列/ })).toContainText('2024-2025')

  // 新增
  await page.getByRole('button', { name: '新增系列' }).click()
  await page.getByLabel('系列名称').fill('测试系列')
  await page.getByRole('button', { name: '保存' }).click()
  await expect(page.getByText('保存成功')).toBeVisible()
  await expect(page.getByRole('cell', { name: '测试系列' })).toBeVisible()

  // 编辑首行：品牌固定，只允许改名称/排序/状态
  await page.getByRole('button', { name: '编辑' }).first().click()
  await expect(page.getByRole('dialog').getByRole('combobox')).toBeDisabled()
  await page.getByLabel('系列名称').fill('iPhone 数字系列 Pro')
  await page.getByRole('button', { name: '保存' }).click()
  await expect(page.getByRole('cell', { name: 'iPhone 数字系列 Pro' })).toBeVisible()

  // 拖拽排序：末位系列拖到首位，刷新后顺序保持
  await page.getByRole('button', { name: '新增系列' }).click()
  await page.getByLabel('系列名称').fill('拖拽排序系列')
  await page.getByRole('button', { name: '保存' }).click()
  await expect(page.getByRole('cell', { name: '拖拽排序系列' })).toBeVisible()
  await page
    .getByRole('row', { name: /拖拽排序系列/ })
    .locator('.drag-handle')
    .dragTo(page.locator('tbody tr').first(), { targetPosition: { x: 60, y: 2 } })
  await expect(page.getByText('排序已保存')).toBeVisible()
  await page.reload()
  await expect(page.getByRole('heading', { name: '系列管理' })).toBeVisible()
  await expect(page.locator('tbody tr').first()).toContainText('拖拽排序系列')

  // 合并：测试系列 → iPhone 数字系列 Pro
  await page.getByRole('row', { name: /测试系列/ }).getByRole('button', { name: '合并' }).click()
  const mergeDialog = page.getByRole('dialog')
  await mergeDialog.getByRole('combobox').click()
  await page.getByRole('option', { name: 'iPhone 数字系列 Pro' }).click()
  await mergeDialog.getByRole('button', { name: '合并' }).click()
  await expect(page.getByText(/已合并/)).toBeVisible()
  await expect(page.getByRole('cell', { name: '测试系列' })).toHaveCount(0)

  // 删除空系列
  await page.getByRole('button', { name: '新增系列' }).click()
  await page.getByLabel('系列名称').fill('待删系列')
  await page.getByRole('button', { name: '保存' }).click()
  await expect(page.getByRole('cell', { name: '待删系列' })).toBeVisible()
  await page.getByRole('row', { name: /待删系列/ }).getByRole('button', { name: '删除' }).click()
  await page.getByRole('dialog').getByRole('button', { name: '删除' }).click()
  await expect(page.getByText('已删除')).toBeVisible()
  await expect(page.getByRole('cell', { name: '待删系列' })).toHaveCount(0)
})

/** 跨代趋势：默认关键参数、聚焦主图、点卡切焦、文本参数与 AI 回顾。 */
test('访客：跨代趋势默认视图与参数聚焦', async ({ page }) => {
  await mockApi(page)

  await page.goto('/trends')
  await expect(page.getByText('Apple · iPhone 数字系列')).toBeVisible()
  await expect(page).toHaveURL(/\/trends\?mode=cross&brandId=1&seriesId=1&paramDefIds=3,11,18,2&paramDefId=3/)
  // 聚焦主图 1 张 + 4 张参数卡各一条迷你趋势线
  await expect(page.locator('.capture-area canvas')).toHaveCount(1)
  await expect(page.locator('.grid svg[viewBox="0 0 100 28"]')).toHaveCount(4)
  await expect(page.locator('.capture-area h3')).toHaveText('屏幕尺寸')

  // AI 回顾：先只展示入口，点按钮才生成
  await expect(page.getByText('AI 世代回顾')).toBeVisible()
  await page.getByRole('button', { name: '生成回顾' }).click()
  await expect(page.getByText('屏幕由 6.1 增至 6.3 英寸')).toBeVisible()
  await expect(page.getByText('电池容量提升有限，仅增加 39mAh')).toBeVisible()

  // 点电池容量卡聚焦，URL 与标题同步
  await page.getByRole('button', { name: '聚焦 电池容量' }).click()
  await expect(page).toHaveURL(/paramDefId=11/)
  await expect(page.locator('.capture-area h3')).toHaveText('电池容量')

  // 加入文本参数芯片：卡片显示值序列，聚焦后按世代列出
  await page.getByRole('button', { name: '芯片', exact: true }).click()
  await expect(page.getByText('已加载 5 / 8 个参数')).toBeVisible()
  await expect(page.getByRole('button', { name: '聚焦 芯片' })).toContainText('A18 → A19 Pro')
  await page.getByRole('button', { name: '聚焦 芯片' }).click()
  await expect(page.locator('.capture-area').getByText('A18')).toBeVisible()
  await expect(page.locator('.capture-area').getByText('A19 Pro')).toBeVisible()
})

/** 参数 chips：上限 8 的拒绝提示与全部取消后的占位。 */
test('访客：跨代趋势参数上限与空状态', async ({ page }) => {
  await mockApi(page)

  await page.goto('/trends')
  await expect(page.getByText('Apple · iPhone 数字系列')).toBeVisible()

  // 默认 4 个 + 再点 4 个 = 8，第 9 个被拒绝
  for (const name of ['芯片', '屏幕刷新率', '充电功率', '主摄']) {
    await page.getByRole('button', { name, exact: true }).click()
  }
  await expect(page.getByText('已加载 8 / 8 个参数')).toBeVisible()
  await page.getByRole('button', { name: '运行内存', exact: true }).click()
  await expect(page.getByText('最多同时查看 8 个参数')).toBeVisible()

  // 全部取消后给出占位提示
  for (const name of ['屏幕尺寸', '电池容量', '机身重量', '首发价格', '芯片', '屏幕刷新率', '充电功率', '主摄']) {
    await page.getByRole('button', { name, exact: true }).click()
  }
  await expect(page.getByText('已加载 0 / 8 个参数')).toBeVisible()
  await expect(page.getByText(/所选参数暂无可绘制的数值/)).toBeVisible()
})

/** 同代横比：年份切换、无新机系列占位、最值徽标与一键加入对比。 */
test('访客：同代横比与全部加入对比', async ({ page }) => {
  await mockApi(page)

  await page.goto('/trends?mode=same')
  await expect(page.getByText('Apple · 2025 年同代横比')).toBeVisible()
  await expect(page.getByText('共 2 个系列有新机，点击卡片查看机型详情')).toBeVisible()
  await expect(page.getByText('iPhone 17', { exact: true }).first()).toBeVisible()
  await expect(page.getByText('iPhone 17 Pro', { exact: true }).first()).toBeVisible()

  // 最值徽标：电池 3900 对 3600 标出最高/最低；屏幕两系列同为 6.3 不标
  await expect(page.getByText('最高')).toHaveCount(3)
  await expect(page.getByText('最低')).toHaveCount(3)

  // 切到 2024：Pro 系列当年无新机，占位仍保留一列
  await page.getByRole('button', { name: '2024', exact: true }).click()
  await expect(page.getByText('Apple · 2024 年同代横比')).toBeVisible()
  await expect(page.getByText('iPhone 16', { exact: true }).first()).toBeVisible()
  await expect(page.getByText('该年无新机')).toBeVisible()

  // 切回 2025 全部加入对比：进入对比页且两台机型都在池中
  await page.getByRole('button', { name: '2025', exact: true }).click()
  await page.getByRole('button', { name: '全部加入对比' }).click()
  await expect(page).toHaveURL(/\/compare\?ids=7,11/)
  await expect(page.getByRole('button', { name: /对比池 2/ })).toBeVisible()
})

/** 社媒导出：模板卡图片 Tab（多卡版 + 聚焦版）与平台风格文案生成。 */
test('访客：社媒导出弹窗与文案生成', async ({ page }) => {
  await mockApi(page)

  await page.goto('/trends')
  await expect(page.getByText('Apple · iPhone 数字系列')).toBeVisible()
  // 页面聚焦电池容量时打开弹窗，聚焦版应默认带到该参数
  await page.getByRole('button', { name: '聚焦 电池容量' }).click()
  await page.getByRole('button', { name: '社媒导出' }).click()

  const dialog = page.getByRole('dialog', { name: '社媒导出' })
  await expect(dialog).toBeVisible()
  // 图片 Tab：默认标题 + 模板卡 + 下载按钮
  await expect(dialog.getByText('iPhone 数字系列 历代参数进化')).toBeVisible()
  await expect(dialog.getByRole('button', { name: '下载图片' })).toBeEnabled()

  // 聚焦版：单参数海报画布 1080×1440，当前聚焦参数自动带入
  await dialog.getByRole('combobox').first().click()
  await page.getByRole('option', { name: '聚焦版 3:4（单参数大图）' }).click()
  await expect(dialog.locator('.mx-auto.bg-white')).toHaveAttribute('style', expect.stringContaining('width: 1080px'))
  await expect(dialog.getByText('mAh')).toBeVisible()
  await expect(dialog.getByRole('button', { name: '下载图片' })).toBeEnabled()

  // 文案 Tab：生成后渲染标题/正文/话题
  await dialog.getByRole('tab', { name: '文案' }).click()
  await dialog.getByRole('button', { name: '生成文案' }).click()
  await expect(dialog.getByText('iPhone 数字系列 2 代进化')).toBeVisible()
  await expect(dialog.getByText(/电池容量提升 39mAh/)).toBeVisible()
  await expect(dialog.getByText('#iPhone')).toBeVisible()
  await expect(dialog.getByRole('button', { name: '复制全文' })).toBeVisible()
})

/** 系列趋势移动端：参数卡保持双列、无横向溢出。 */
test('访客：系列趋势移动端参数卡双列', async ({ page }) => {
  await mockApi(page)
  await page.setViewportSize({ width: 390, height: 844 })

  await page.goto('/trends')
  await expect(page.locator('.capture-area canvas').first()).toBeVisible()

  const columns = await page
    .locator('.grid.grid-cols-2 > div')
    .evaluateAll((cards) => cards.map((card) => Math.round(card.getBoundingClientRect().x)))
  expect(new Set(columns).size).toBe(2)
})

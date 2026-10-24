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

/** 世代回顾：默认四图 → 取消参数 → 裁剪区间 → 参数上限 → 全部取消的占位。 */
test('访客：世代回顾参数、区间与空状态', async ({ page }) => {
  await mockApi(page)

  await page.goto('/evolution')
  // 页面标题与截图区标题同名，取第一个
  await expect(page.getByText('Apple · iPhone 数字系列').first()).toBeVisible()
  await expect(page).toHaveURL(/seriesId=1/)
  await expect(page).toHaveURL(/paramDefIds=3,11,18,2/)
  await expect(page.locator('.capture-area canvas')).toHaveCount(4)

  // AI 回顾：先只展示入口，点按钮才生成
  await expect(page.getByText('AI 世代回顾')).toBeVisible()
  await page.getByRole('button', { name: '生成回顾' }).click()
  await expect(page.getByText('屏幕由 6.1 增至 6.3 英寸')).toBeVisible()
  await expect(page.getByText('电池容量提升有限，仅增加 39mAh')).toBeVisible()

  // 取消重量后只剩三张图，URL 同步
  await page.getByRole('checkbox', { name: '机身重量' }).click()
  await expect(page.getByRole('checkbox', { name: '机身重量' })).not.toBeChecked()
  await expect(page.locator('.capture-area canvas')).toHaveCount(3)
  await expect(page).toHaveURL(/paramDefIds=3,11,2/)

  // 起止世代裁剪到第 2 代
  await page.locator('[role=combobox]').nth(2).click()
  await page.getByRole('option', { name: '2. iPhone 17' }).click()
  await expect(page.getByText(/当前展示第 2 - 2 代/)).toBeVisible()

  // 勾满 8 个后再点第 9 个应被拒绝
  for (const name of ['屏幕刷新率', '充电功率', '主摄', '运行内存', '存储容量']) {
    await page.getByRole('checkbox', { name }).click()
    await expect(page.getByRole('checkbox', { name })).toBeChecked()
  }
  await expect(page.getByText('已选 8 / 8 个参数')).toBeVisible()
  await page.getByRole('checkbox', { name: '芯片' }).click()
  await expect(page.getByText('最多同时查看 8 个参数')).toBeVisible()

  // 全部取消后给出占位提示
  for (const name of ['屏幕尺寸', '首发价格', '电池容量', '屏幕刷新率', '充电功率', '主摄', '运行内存', '存储容量']) {
    await page.getByRole('checkbox', { name }).click()
    await expect(page.getByRole('checkbox', { name })).not.toBeChecked()
  }
  await expect(page.getByText(/所选参数暂无可绘制的数值/)).toBeVisible()
})


/** 社媒导出：模板卡图片 Tab 与平台风格文案生成。 */
test('访客：社媒导出弹窗与文案生成', async ({ page }) => {
  await mockApi(page)

  await page.goto('/evolution')
  await expect(page.getByText('Apple · iPhone 数字系列').first()).toBeVisible()
  await page.getByRole('button', { name: '社媒导出' }).click()

  const dialog = page.getByRole('dialog', { name: '社媒导出' })
  await expect(dialog).toBeVisible()
  // 图片 Tab：默认标题 + 模板卡 + 下载按钮
  await expect(dialog.getByText('iPhone 数字系列 历代参数进化')).toBeVisible()
  await expect(dialog.getByRole('button', { name: '下载图片' })).toBeEnabled()

  // 文案 Tab：生成后渲染标题/正文/话题
  await dialog.getByRole('tab', { name: '文案' }).click()
  await dialog.getByRole('button', { name: '生成文案' }).click()
  await expect(dialog.getByText('iPhone 数字系列 2 代进化')).toBeVisible()
  await expect(dialog.getByText(/电池容量提升 39mAh/)).toBeVisible()
  await expect(dialog.getByText('#iPhone')).toBeVisible()
  await expect(dialog.getByRole('button', { name: '复制全文' })).toBeVisible()
})

/** 世代回顾移动端：图表卡片纵向单列。 */
test('访客：世代回顾移动端单列', async ({ page }) => {
  await mockApi(page)
  await page.setViewportSize({ width: 390, height: 844 })

  await page.goto('/evolution')
  await expect(page.locator('.capture-area canvas').first()).toBeVisible()

  const columns = await page
    .locator('.capture-area .grid > div')
    .evaluateAll((cards) => cards.map((card) => Math.round(card.getBoundingClientRect().x)))
  expect(new Set(columns).size).toBe(1)
})

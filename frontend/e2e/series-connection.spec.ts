import { expect, test } from '@playwright/test'
import { mockApi } from './mock-api'

/**
 * P11 机型库 × 参数历史/世代回顾 的连接链路：
 * 详情页系列条与两个入口、稀疏系列的空态叙事与补抓 CTA、后台完善度看板。
 */

/** 访客：详情页系列条、世代回顾入口与参数历史跳转。 */
test('访客：详情页系列条与参数历史入口', async ({ page }) => {
  await mockApi(page)

  // iPhone 17 属于 2 代的 iPhone 数字系列：系列条 + 世代回顾入口都要出现
  await page.goto('/devices/7')
  await expect(page.getByRole('heading', { name: '所属系列 · iPhone 数字系列' })).toBeVisible()
  await expect(page.getByRole('button', { name: '世代回顾' })).toBeVisible()
  // 当前机型在系列条中禁用高亮，同系列其它机型可点互跳
  await expect(page.getByRole('button', { name: 'iPhone 17 iPhone 17 2025' })).toBeDisabled()
  await page.getByRole('button', { name: 'iPhone 16 iPhone 16 2024' }).click()
  await expect(page).toHaveURL(/\/devices\/4/)

  // 世代回顾入口直达该系列
  await page.goto('/devices/7')
  await page.getByRole('button', { name: '世代回顾' }).click()
  await expect(page).toHaveURL(/\/evolution\?seriesId=1/)

  // 数值参数行带历史入口，点击后历史页自动选中品牌与参数
  await page.goto('/devices/7')
  const screenRow = page.getByRole('row', { name: /屏幕尺寸/ })
  await screenRow.getByRole('button', { name: '查看参数历史' }).click()
  await expect(page).toHaveURL(/\/history\?brandId=1&paramDefId=3/)
  await expect(page.getByText('共 2 个机型数据点')).toBeVisible()

  // 文本参数（芯片）没有历史入口
  const chipRow = page.getByRole('row', { name: /芯片/ })
  await expect(chipRow.getByRole('button', { name: '查看参数历史' })).toHaveCount(0)

  // 单机系列（Mate 70）：只有系列条与收录中文案，不放入口
  await page.goto('/devices/9')
  await expect(page.getByRole('heading', { name: '所属系列 · Mate 系列' })).toBeVisible()
  await expect(page.getByRole('button', { name: '世代回顾' })).toHaveCount(0)
  await expect(page.getByText('已收录 1 代 · 数据持续收录中')).toBeVisible()
})

/** 访客：稀疏系列回顾页的空态叙事与管理员补抓 CTA 两态。 */
test('访客：稀疏系列空态与管理员 CTA', async ({ page }) => {
  await mockApi(page)

  // Mate 系列只有 1 代：显示收录进度文案，未登录无补抓按钮
  await page.goto('/evolution?seriesId=2')
  await expect(page.getByText('该系列已收录 1 代，数据持续收录中')).toBeVisible()
  await expect(page.getByRole('button', { name: '去后台补抓' })).toHaveCount(0)

  // 登录态（本地有 token）：出现 CTA，点击进入后台抓取任务页
  await page.addInitScript(() => localStorage.setItem('spectrace-token', 'e2e-token'))
  await page.goto('/evolution?seriesId=2')
  await expect(page.getByRole('button', { name: '去后台补抓' })).toBeVisible()
  await page.getByRole('button', { name: '去后台补抓' }).click()
  await expect(page).toHaveURL(/\/admin\/agent-tasks/)
})


/** 后台：拒绝系列发现结果——任务置为已丢弃，清单不可再创建草稿。 */
test('后台：拒绝系列发现结果', async ({ page }) => {
  await mockApi(page)

  await page.goto('/admin/login')
  await page.getByLabel('用户名').fill('admin')
  await page.getByLabel('密码').fill('admin123')
  await page.getByRole('button', { name: '登录' }).click()
  await expect(page).toHaveURL(/\/admin\/devices/)

  await page.goto('/admin/agent-tasks')
  // 造一个已完成待审的系列发现任务
  await page.evaluate(async () => {
    await fetch('/api/admin/agent-tasks', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        taskType: 'SERIES_DISCOVERY',
        modelName: 'Mate 70 系列',
        brandName: '华为',
        status: 'PENDING_REVIEW',
      }),
    })
  })
  await page.reload()
  await expect(page.getByRole('row', { name: /Mate 70 系列/ })).toBeVisible()

  // 行级「拒绝」→ 确认弹窗 → 状态变为已丢弃，拒绝入口消失
  await page.getByRole('row', { name: /Mate 70 系列/ }).getByRole('button', { name: '拒绝' }).click()
  const confirmDialog = page.getByRole('dialog', { name: '拒绝发现结果' })
  await expect(confirmDialog).toContainText('此前已创建的草稿机型不受影响')
  await confirmDialog.getByRole('button', { name: '拒绝' }).click()
  await expect(page.getByText('发现结果已拒绝')).toBeVisible()
  await expect(page.getByRole('row', { name: /Mate 70 系列/ })).toContainText('已丢弃')
  await expect(page.getByRole('row', { name: /Mate 70 系列/ }).getByRole('button', { name: '拒绝' })).toHaveCount(0)

  // 详情抽屉：清单只读并提示已拒绝
  await page.getByRole('row', { name: /Mate 70 系列/ }).getByRole('button', { name: '详情' }).click()
  await expect(page.getByText('发现结果已被拒绝，无法再创建草稿')).toBeVisible()
  await expect(page.getByRole('button', { name: /创建所选为草稿机型/ })).toBeDisabled()
})

/** 后台：完善度看板、补抓缺参弹窗与系列发现预填。 */
test('后台：系列完善度与补抓缺参', async ({ page }) => {
  await mockApi(page)

  await page.goto('/admin/login')
  await page.getByLabel('用户名').fill('admin')
  await page.getByLabel('密码').fill('admin123')
  await page.getByRole('button', { name: '登录' }).click()
  await expect(page).toHaveURL(/\/admin\/devices/)

  // 造一台无参数的系列内草稿（模拟系列发现 → 建草稿后的状态）
  await page.evaluate(async () => {
    await fetch('/api/admin/devices', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ brandId: 2, name: 'Mate 70 Pro', seriesId: 2, status: 0, params: {} }),
    })
  })

  await page.goto('/admin/series')
  // 满编系列：完善度 2/2，补抓按钮禁用
  const iphoneRow = page.getByRole('row', { name: /iPhone 数字系列/ })
  await expect(iphoneRow).toContainText('带参 2/2')
  await expect(iphoneRow.getByRole('button', { name: '补抓缺参机型' })).toBeDisabled()
  // 缺参系列：完善度 1/2 + 徽标，补抓按钮可用
  const mateRow = page.getByRole('row', { name: /Mate 系列/ })
  await expect(mateRow).toContainText('带参 1/2')
  await expect(mateRow.getByText('缺参')).toBeVisible()

  // 补抓弹窗列出缺参草稿并支持选择模型
  await mateRow.getByRole('button', { name: '补抓缺参机型' }).click()
  const dialog = page.getByRole('dialog', { name: '补抓缺参机型' })
  await expect(dialog.getByText('Mate 70 Pro')).toBeVisible()
  await expect(dialog.getByText('草稿')).toBeVisible()
  await expect(dialog.getByRole('button', { name: /提交 1 个任务/ })).toBeEnabled()
  await dialog.getByRole('button', { name: '取消' }).click()

  // 系列发现直达抓取任务页并预填模式与品牌
  await mateRow.getByRole('button', { name: '系列发现' }).click()
  await expect(page).toHaveURL(/\/admin\/agent-tasks\?mode=SERIES_DISCOVERY&brand=/)
  await expect(page.getByRole('tab', { name: '系列发现' })).toHaveAttribute('aria-selected', 'true')
  await expect(page.locator('[role=combobox]').filter({ hasText: '华为' })).toBeVisible()
})

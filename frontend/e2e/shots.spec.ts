import { expect, test } from '@playwright/test'
import { mockApi } from './mock-api'

/**
 * README 配图维护脚本：用 e2e 的 mock 数据渲染页面并截图到 docs/screenshots/。
 *
 * <p>不是测试：默认（含 CI）整体跳过，仅手动执行——
 * `SHOTS=1 npx playwright test e2e/shots.spec.ts`，产物直接可提交。</p>
 */
test.skip(!process.env.SHOTS, 'README 截图工具，需 SHOTS=1 手动执行')

test('截图：系列趋势（跨代）', async ({ page }) => {
  await mockApi(page)
  await page.setViewportSize({ width: 1280, height: 800 })
  await page.goto('/trends')
  await expect(page.getByText('Apple · iPhone 数字系列')).toBeVisible()
  await expect(page.locator('.capture-area canvas')).toHaveCount(1)
  await page.screenshot({ path: '../docs/screenshots/trends.png' })
})

test('截图：后台运行监控与数据健康度', async ({ page }) => {
  await mockApi(page)
  await page.setViewportSize({ width: 1280, height: 800 })
  await page.goto('/admin/login')
  await page.getByLabel('用户名').fill('admin')
  await page.getByLabel('密码').fill('admin123')
  await page.getByRole('button', { name: '登录' }).click()
  await expect(page).toHaveURL(/\/admin\/devices/)

  await page.goto('/admin/monitor')
  await expect(page.getByRole('heading', { name: '运行监控' })).toBeVisible()
  await expect(page.getByText('模型表现排行')).toBeVisible()
  await page.screenshot({ path: '../docs/screenshots/admin-monitor.png' })

  await page.goto('/admin/health')
  await expect(page.getByRole('heading', { name: '数据健康度' })).toBeVisible()
  await expect(page.getByRole('cell', { name: 'Mate 70' })).toBeVisible()
  await page.screenshot({ path: '../docs/screenshots/admin-health.png' })
})

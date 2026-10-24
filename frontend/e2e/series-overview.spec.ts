import { expect, test } from '@playwright/test'
import { mockApi } from './mock-api'

/** P12 机型库系列总览：Tab 切换、卡片趋势与跳转、未归类卡与 URL 同步。 */
test('访客：机型库系列总览视图', async ({ page }) => {
  await mockApi(page)

  // 默认仍是机型列表；造一台未归类的已上架机型（页面加载后才能发相对请求），验证总览底部的未归类卡
  await page.goto('/devices')
  await expect(page.getByRole('link', { name: 'iPhone 17' }).first()).toBeVisible()
  await page.evaluate(async () => {
    await fetch('/api/admin/devices', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ brandId: 1, name: 'iPhone SE3', status: 1, params: {} }),
    })
  })
  await page.getByRole('tab', { name: '系列总览' }).click()
  await expect(page).toHaveURL(/\/devices\?view=series/)

  // 系列卡片：收录规模、年份范围、最新一代与两条趋势线
  const iphoneCard = page.getByRole('link').filter({ hasText: 'iPhone 数字系列' })
  await expect(iphoneCard).toContainText('共 2 代')
  await expect(iphoneCard).toContainText('2024-2025')
  await expect(iphoneCard).toContainText('最新：iPhone 17')
  await expect(iphoneCard.locator('svg[viewBox="0 0 100 28"]')).toHaveCount(2)

  // 点卡片直达该系列的世代回顾
  await iphoneCard.click()
  await expect(page).toHaveURL(/\/evolution\?seriesId=1/)

  // 未归类卡：切回列表视图并去掉 view 参数
  await page.goto('/devices?view=series')
  await expect(page.getByText('1 台机型未归入系列')).toBeVisible()
  await page.getByRole('button', { name: /查看机型列表/ }).click()
  await expect(page).not.toHaveURL(/view=series/)
  await expect(page.getByRole('link', { name: 'iPhone 17' }).first()).toBeVisible()
})

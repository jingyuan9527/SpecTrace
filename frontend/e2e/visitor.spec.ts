import { expect, test } from '@playwright/test'
import { mockApi } from './mock-api'

/** 主链路一：首页 → 品牌筛选 → 加入对比 → 对比页 → 复制分享链接。 */
test('访客：品牌筛选到对比分享', async ({ page, context }) => {
  await context.grantPermissions(['clipboard-read', 'clipboard-write'])
  await mockApi(page)

  await page.goto('/')
  await expect(page.getByRole('heading', { name: /手机参数/ })).toBeVisible()

  // 品牌导航进入机型库
  await page.getByRole('button', { name: /Apple/ }).click()
  await expect(page).toHaveURL(/\/devices\?brandId=1/)
  await expect(page.getByRole('link', { name: 'iPhone 17' }).first()).toBeVisible()

  // 关键词筛选
  await page.getByPlaceholder('机型名称或拼音首字母').fill('iPhone')
  await page.getByRole('button', { name: '搜索' }).click()
  await expect(page.getByRole('link', { name: 'iPhone 16' }).first()).toBeVisible()

  // 加入两台机型到对比池
  const addButtons = page.getByRole('button', { name: '加入对比' })
  await addButtons.nth(0).click()
  await addButtons.nth(0).click()
  await expect(page.getByRole('button', { name: '前往对比' })).toContainText('2')

  // 进入对比页
  await page.getByRole('button', { name: '前往对比' }).click()
  await expect(page).toHaveURL(/\/compare/)
  await expect(page.getByText('iPhone 17').first()).toBeVisible()
  await expect(page.getByText('iPhone 16').first()).toBeVisible()

  // 分享链接写入地址栏并复制到剪贴板
  await page.getByRole('button', { name: '分享链接' }).click()
  await expect(page).toHaveURL(/ids=\d+(,\d+)+/)
  const clipboard = await page.evaluate(() => navigator.clipboard.readText())
  expect(clipboard).toContain('/compare?ids=')
})

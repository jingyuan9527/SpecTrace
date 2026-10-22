import { expect, test } from '@playwright/test'
import { mockApi } from './mock-api'

/** 主链路二：登录 → 新增品牌 → 新增机型 → 提交抓取 → 审核通过。 */
test('后台：从登录到审核入库', async ({ page }) => {
  await mockApi(page)

  // 登录
  await page.goto('/admin/login')
  await page.getByLabel('用户名').fill('admin')
  await page.getByLabel('密码').fill('admin123')
  await page.getByRole('button', { name: '登录' }).click()
  await expect(page).toHaveURL(/\/admin\/devices/)
  await expect(page.getByRole('heading', { name: '机型管理' })).toBeVisible()

  // 新增品牌
  await page.goto('/admin/brands')
  await page.getByRole('button', { name: '新增品牌' }).click()
  await page.getByLabel('品牌名称').fill('测试品牌')
  await page.getByRole('button', { name: '保存' }).click()
  await expect(page.getByText('保存成功')).toBeVisible()
  await expect(page.getByText('测试品牌')).toBeVisible()

  // 新增机型
  await page.goto('/admin/devices')
  await page.getByRole('button', { name: '新增机型' }).click()
  await page.getByLabel('机型名称').fill('iPhone 18 Pro')
  await page.getByRole('button', { name: '保存' }).click()
  await expect(page.getByText('保存成功')).toBeVisible()

  // 提交抓取任务
  await page.goto('/admin/agent-tasks')
  await page.getByPlaceholder('机型名称，如 iPhone 17 Pro').fill('iPhone 18')
  await page.getByRole('button', { name: '提交抓取' }).click()
  await expect(page.getByText('抓取任务已提交')).toBeVisible()
  await expect(page.getByText('iPhone 18')).toBeVisible()

  // 审核通过
  await page.goto('/admin/pending')
  await page.getByRole('button', { name: '审核' }).first().click()
  await expect(page.getByText('新机型')).toBeVisible()
  await page.getByRole('button', { name: '审核通过并入库' }).click()
  await expect(page.getByText('审核通过，已迁移到正式机型库')).toBeVisible()
})

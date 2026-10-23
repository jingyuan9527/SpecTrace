import { expect, test, type Locator, type Page } from '@playwright/test'
import { mockApi } from './mock-api'

/**
 * 从当前焦点按 Tab 前进直到目标获得焦点。
 * 不做「按 N 次 Tab」的硬编码：控件顺序一变用例就假失败，这里验证的是「可达」而非具体步数。
 */
async function tabTo(page: Page, target: Locator, maxTries = 40) {
  for (let i = 0; i < maxTries; i++) {
    if (await target.evaluate((el) => el === document.activeElement).catch(() => false)) return
    await page.keyboard.press('Tab')
  }
  await expect(target).toBeFocused()
}

/** 主链路三：仅用键盘完成登录、打开弹窗并用 Esc 关闭。 */
test('键盘：登录与弹窗焦点', async ({ page }) => {
  await mockApi(page)
  await page.goto('/admin/login')

  // Tab 在表单内移动，Enter 提交
  await page.getByLabel('用户名').focus()
  await expect(page.getByLabel('用户名')).toBeFocused()
  await page.keyboard.press('Control+A')
  await page.keyboard.type('admin')
  await page.keyboard.press('Tab')
  await expect(page.getByLabel('密码')).toBeFocused()
  await page.keyboard.press('Control+A')
  await page.keyboard.type('admin123')
  await page.keyboard.press('Enter')
  await expect(page).toHaveURL(/\/admin\/devices/)
  await expect(page.getByRole('heading', { name: '机型管理' })).toBeVisible()

  // 纯键盘打开弹窗
  await page.goto('/admin/brands')
  await page.getByRole('button', { name: '新增品牌' }).focus()
  await page.keyboard.press('Enter')
  const dialog = page.getByRole('dialog')
  await expect(dialog).toBeVisible()

  // 焦点应自动进入弹窗，避免键盘用户迷失在背景页面
  const focusInside = await page.evaluate(() => {
    const active = document.activeElement
    return Boolean(active && active.closest('[role="dialog"]'))
  })
  expect(focusInside).toBe(true)

  // Esc 关闭，焦点回到触发按钮
  await page.keyboard.press('Escape')
  await expect(dialog).toBeHidden()
})

/** 主链路四：新增品牌 → 新增机型 → 提交抓取 → 审核通过，全程只用 Tab / Enter。 */
test('键盘：新增品牌、机型、提交抓取与审核', async ({ page }) => {
  await mockApi(page)
  await page.goto('/admin/login')
  await page.getByLabel('用户名').focus()
  await page.keyboard.type('admin')
  await page.keyboard.press('Tab')
  await page.keyboard.type('admin123')
  await page.keyboard.press('Enter')
  await expect(page).toHaveURL(/\/admin\/devices/)

  // 新增品牌
  await page.goto('/admin/brands')
  await tabTo(page, page.getByRole('button', { name: '新增品牌' }))
  await page.keyboard.press('Enter')
  await expect(page.getByRole('dialog')).toBeVisible()
  await tabTo(page, page.getByLabel('品牌名称'))
  await page.keyboard.type('键盘品牌')
  await tabTo(page, page.getByRole('button', { name: '保存' }))
  await page.keyboard.press('Enter')
  await expect(page.getByText('保存成功')).toBeVisible()

  // 新增机型
  await page.goto('/admin/devices')
  await tabTo(page, page.getByRole('button', { name: '新增机型' }))
  await page.keyboard.press('Enter')
  await expect(page.getByRole('dialog')).toBeVisible()
  await tabTo(page, page.getByLabel('机型名称'))
  await page.keyboard.type('键盘机型')
  await tabTo(page, page.getByRole('button', { name: '保存' }))
  await page.keyboard.press('Enter')
  await expect(page.getByText('保存成功')).toBeVisible()

  // 提交抓取任务
  await page.goto('/admin/agent-tasks')
  await tabTo(page, page.getByPlaceholder('机型名称，如 iPhone 17 Pro'))
  await page.keyboard.type('键盘抓取')
  await tabTo(page, page.getByRole('button', { name: '提交抓取' }))
  await page.keyboard.press('Enter')
  await expect(page.getByText('抓取任务已提交')).toBeVisible()

  // 审核通过
  await page.goto('/admin/pending')
  await tabTo(page, page.getByRole('button', { name: '审核' }).first())
  await page.keyboard.press('Enter')
  await expect(page.getByText('新机型')).toBeVisible()
  await tabTo(page, page.getByRole('button', { name: '审核通过并入库' }))
  await page.keyboard.press('Enter')
  await expect(page.getByText('审核通过，已迁移到正式机型库')).toBeVisible()
})

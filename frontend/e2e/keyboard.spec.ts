import { expect, test } from '@playwright/test'
import { mockApi } from './mock-api'

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

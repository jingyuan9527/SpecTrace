import { expect, test, type Page } from '@playwright/test'
import { mockApi } from './mock-api'

/** 后台低频页面：接口由 mock 提供，只验入口、关键写操作与状态回显。 */
async function login(page: Page) {
  await page.goto('/admin/login')
  await page.getByLabel('用户名').fill('admin')
  await page.getByLabel('密码').fill('admin123')
  await page.getByRole('button', { name: '登录' }).click()
  await expect(page).toHaveURL(/\/admin\/devices/)
}

test('后台：账号新增、禁用与删除', async ({ page }) => {
  await mockApi(page)
  await login(page)

  await page.goto('/admin/users')
  await expect(page.getByRole('heading', { name: '账号权限' })).toBeVisible()
  await expect(page.getByRole('cell', { name: 'editor' })).toBeVisible()

  // 新增账号
  await page.getByRole('button', { name: '新增管理员' }).click()
  await page.getByLabel('用户名').fill('ops')
  await page.getByLabel('初始密码').fill('ops123456')
  await page.getByRole('button', { name: '创建' }).click()
  await expect(page.getByText('账号已创建')).toBeVisible()
  await expect(page.getByRole('cell', { name: 'ops' })).toBeVisible()

  // 禁用后状态列变为「禁用」
  const opsRow = page.getByRole('row', { name: /ops/ })
  await opsRow.getByRole('button', { name: '禁用' }).click()
  await expect(page.getByText('账号已禁用')).toBeVisible()
  await expect(opsRow.getByText('禁用')).toBeVisible()

  // 删除需要二次确认
  await opsRow.getByRole('button', { name: '删除账号' }).click()
  await page.getByRole('dialog').getByRole('button', { name: '删除' }).click()
  await expect(page.getByText('账号已删除')).toBeVisible()
  await expect(page.getByRole('cell', { name: 'ops' })).toHaveCount(0)
})

test('后台：参数分组与参数定义维护', async ({ page }) => {
  await mockApi(page)
  await login(page)

  await page.goto('/admin/params')
  await expect(page.getByRole('heading', { name: '参数元数据' })).toBeVisible()
  await expect(page.getByRole('cell', { name: '发布时间' })).toBeVisible()

  // 新增分组后左侧列表出现
  await page.getByRole('button', { name: '新增分组' }).first().click()
  await page.getByLabel('分组名称').fill('影像')
  await page.getByRole('button', { name: '保存' }).click()
  await expect(page.getByText('保存成功')).toBeVisible()

  // 切到新分组：该分组下暂无参数
  await page.getByRole('button', { name: '影像' }).first().click()
  await expect(page.getByText('该分组暂无参数')).toBeVisible()

  // 新增参数后出现在右侧表格
  await page.getByRole('button', { name: '新增参数' }).last().click()
  await page.getByLabel('参数 Key').fill('camera_count')
  await page.getByLabel('参数名称').fill('摄像头数量')
  await page.getByRole('button', { name: '保存' }).click()
  await expect(page.getByRole('cell', { name: '摄像头数量' })).toBeVisible()
})

test('后台：纠错反馈状态流转', async ({ page }) => {
  await mockApi(page)
  await login(page)

  await page.goto('/admin/feedback')
  await expect(page.getByRole('heading', { name: '纠错反馈' })).toBeVisible()
  await expect(page.getByRole('cell', { name: 'iPhone 17' })).toBeVisible()

  await page.getByRole('button', { name: '已处理' }).click()
  await expect(page.getByText('已标记为已处理')).toBeVisible()
  // 处理完成后该行不再提供操作按钮
  await expect(page.getByRole('button', { name: '忽略' })).toHaveCount(0)
})

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

test('后台：系列订阅抓取配置', async ({ page }) => {
  await mockApi(page)
  await login(page)

  await page.goto('/admin/series')
  await page.getByRole('row', { name: /iPhone 数字系列/ }).getByRole('button', { name: '订阅抓取' }).click()
  await expect(page.getByRole('dialog')).toBeVisible()
  // 默认 3 个月 + 启用，直接保存
  await page.getByRole('dialog').getByRole('button', { name: '保存订阅' }).click()
  await expect(page.getByText(/已订阅「iPhone 数字系列」/)).toBeVisible()

  // 再次打开回显已保存配置并暂停
  await page.getByRole('row', { name: /iPhone 数字系列/ }).getByRole('button', { name: '订阅抓取' }).click()
  await page.getByRole('dialog').getByLabel('启用订阅').click()
  await page.getByRole('dialog').getByRole('button', { name: '保存订阅' }).click()
  await expect(page.getByText('订阅已暂停')).toBeVisible()
})

test('后台：系列重抓全系列（参数陈旧）', async ({ page }) => {
  await mockApi(page)
  await login(page)

  await page.goto('/admin/series')
  await expect(page.getByRole('heading', { name: '系列管理' })).toBeVisible()
  await page.getByRole('row', { name: /iPhone 数字系列/ }).getByRole('button', { name: '重抓全系列' }).click()
  await expect(page.getByRole('dialog')).toBeVisible()
  await expect(page.getByRole('dialog')).toContainText('重抓全系列（参数陈旧）')
  // mock 把 iPhone 16 圈定为陈旧目标
  await expect(page.getByRole('dialog')).toContainText('iPhone 16')
  await page.getByRole('dialog').getByRole('button', { name: '提交 1 个任务' }).click()
  await expect(page.getByText(/提交 1 个抓取任务/)).toBeVisible()
})

test('后台：数据健康度看板与缺参补抓', async ({ page }) => {
  await mockApi(page)
  await login(page)

  await page.goto('/admin/health')
  await expect(page.getByRole('heading', { name: '数据健康度' })).toBeVisible()
  await expect(page.getByText('全库带参率', { exact: true })).toBeVisible()
  // 缺参排行有数据，Mate 70 带 PV 回显
  await expect(page.getByRole('cell', { name: 'Mate 70' })).toBeVisible()
  await expect(page.getByRole('row', { name: /Mate 70/ }).getByText('5')).toBeVisible()

  // 行级补抓：弹窗选默认模型后提交
  await page.getByRole('row', { name: /Mate 70/ }).getByRole('button', { name: '补抓该机型' }).click()
  await expect(page.getByRole('dialog')).toBeVisible()
  await page.getByRole('dialog').getByRole('button', { name: '提交抓取' }).click()
  await expect(page.getByText('已提交 1 个抓取任务，结果将进入待审核')).toBeVisible()
})

test('后台：AI 预审打标与批量通过护栏', async ({ page }) => {
  await mockApi(page)
  await login(page)

  await page.goto('/admin/pending')
  await expect(page.getByRole('cell', { name: 'iPhone 18', exact: true })).toBeVisible()
  // 列表显示预审徽标（mock 行为 HIGH = 建议核实）
  await expect(page.getByText('建议核实', { exact: true }).first()).toBeVisible()

  // 勾选可疑记录后点批量通过：先出现护栏确认，放行后进入既有批量确认，再次确认才执行
  await page.getByLabel('选择 iPhone 18').check()
  await page.getByRole('button', { name: '批量通过' }).click()
  await expect(page.getByText('选中有建议人工核实的记录')).toBeVisible()
  await page.getByRole('button', { name: '仍要批量通过' }).click()
  await page.getByRole('dialog', { name: '批量通过待审核数据' }).getByRole('button', { name: '批量通过' }).click()
  await expect(page.getByText('已通过 1 条')).toBeVisible()
})

test('后台：初始密码必须先改密才能进入', async ({ page }) => {
  await mockApi(page, { mustChangePassword: true })

  await page.goto('/admin/login')
  await page.getByLabel('用户名').fill('admin')
  await page.getByLabel('密码').fill('admin123')
  await page.getByRole('button', { name: '登录' }).click()

  // 登录直接落到改密页，其它后台路由也会被守卫挡回来
  await expect(page).toHaveURL(/\/admin\/change-password/)
  await page.goto('/admin/users')
  await expect(page).toHaveURL(/\/admin\/change-password/)

  await page.getByLabel('原密码').fill('admin123')
  await page.getByLabel('新密码', { exact: true }).fill('newpass123')
  await page.getByLabel('确认新密码').fill('newpass123')
  await page.getByRole('button', { name: '确认修改' }).click()

  await expect(page.getByText('密码已更新')).toBeVisible()
  await expect(page).toHaveURL(/\/admin\/devices/)
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

test('纠错反馈闭环：编号回执、进度查询与侧栏徽标', async ({ page }) => {
  await mockApi(page)

  // 访客提交纠错，拿到编号回执
  await page.goto('/devices/7')
  await page.getByRole('button', { name: '报告参数错误' }).first().click()
  await page.getByLabel('正确的值').fill('4000')
  await page.getByRole('dialog').getByRole('button', { name: '提交反馈' }).click()
  await expect(page.getByRole('dialog')).toContainText('#2')

  // 直接凭回执查进度：待处理
  await page.getByRole('dialog').getByRole('button', { name: '查询进度' }).click()
  await page.getByRole('dialog').getByRole('button', { name: '查询' }).click()
  await expect(page.getByRole('dialog').getByText('待处理')).toBeVisible()

  // 后台侧栏出现未读徽标（种子 1 条 + 新提交 1 条）
  await login(page)
  const feedbackLink = page.locator('aside').getByRole('link', { name: /纠错反馈/ })
  await expect(feedbackLink).toContainText('2')

  // 逐条处理（新提交 + 种子）后徽标归零
  await page.goto('/admin/feedback')
  await page.getByRole('row', { name: /4000/ }).getByRole('button', { name: '已处理' }).click()
  await expect(page.getByText('已标记为已处理')).toBeVisible()
  await page.getByRole('button', { name: '已处理' }).click()
  await page.goto('/admin/devices')
  await expect(feedbackLink).toHaveAccessibleName('纠错反馈')

  // 访客再查同一编号：已处理并带处理时间
  await page.goto('/devices/7')
  await page.getByRole('button', { name: '报告参数错误' }).first().click()
  await page.getByRole('button', { name: '已有编号？查询处理进度' }).click()
  await page.getByLabel('反馈编号').fill('2')
  await page.getByRole('dialog').getByRole('button', { name: '查询' }).click()
  await expect(page.getByRole('dialog').getByText('已处理')).toBeVisible()
  await expect(page.getByRole('dialog')).toContainText('处理于')
})

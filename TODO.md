# SpecTrace 优化任务清单

> 基于 2026-10-22 的现状评估拆分。优先级：P0 直接影响可用性，P1 建立数据可信度，P2 提升后台效率，P3 长期增强。
> 每项按「改动点 → 验收标准 → 预估」组织，默认一个任务一个 commit。

## 完成情况（2026-10-22）

| 任务 | 状态 | 落地要点 |
| --- | --- | --- |
| T5 字段级来源 | ✅ | `device_param_value` 增 `source_url` / `confidence`（`SqliteSchemaMigration` 兼容老库）；审核回写来源，人工改值清空来源并刷新 `updated_at`；详情页逐行「更新于 / 来源」，页头「最后更新」 |
| T6 待审核 diff | ✅ | `pendingDetail` 返回同品牌同名机型现有参数与 `newDevice`；弹窗「旧值 → 新值」，未变化项折叠；同名机型审核通过改为覆盖更新 |
| T7 参数纠错 | ✅ | `feedback` 表 + `POST /api/public/feedback`（同来源同机型 1 分钟限流）+ 后台「纠错反馈」页 |
| T8 批量导入 | ✅ | CSV 模板按 `param_def` 动态生成；`/admin/devices/import?dryRun=` 预览逐行校验（行号、品牌不存在、重复机型），确认后逐行落库，上限 500 行 |
| T9 复制机型 | ✅ | 列表复制按钮带出全部参数与版本价格，名称加「副本」、发布日期置空 |
| T10 任务实时状态 | ✅ | 页面可见时 5 秒静默轮询；失败任务重试接口；详情抽屉展示来源/耗时/错误/结果 + 过程日志（`agent_task_log`，抓取中每 3 秒增量拉取）；新建任务品牌从品牌表下拉选择 |
| T11 待审核批量操作 | ✅ | 多选 + 批量通过 / 批量丢弃，单条失败不影响其余并回传原因 |
| T12 上传优化 | ✅ | canvas 压缩最长边 1600px 转 WebP；后端文件头校验 + 大小限制 + 返回尺寸；进度显示与失败重试 |
| T13 历史多品牌 | ✅ | 最多 4 品牌叠加、日期对齐的双/多折线、极值标注、单位与数值校验提示、CSV 导出 |
| T14 搜索联想 | ✅ | `/public/devices/suggest` + 前端 `pinyin-pro` 全拼/首字母本地匹配，下拉可键盘选择 |
| T15 SEO / history 路由 | ✅ | `createWebHistory`；`ResourceConfig` SPA 回退（`/api`、带扩展名不回落）；详情页动态 title/description/OG |
| T16 可访问性 | ✅ | Dialog/Sheet 缺省 Description 用隐藏文本兜底；`Select`/`Combobox` 支持 `id` 与 `label for` 对齐；表头 `scope="col"`、对比表 `caption` |
| T17 E2E | ✅ | Playwright 两条主链路（访客对比分享、后台审核入库），接口由 `e2e/mock-api.ts` 拦截，失败保留 trace/截图/视频 |
| T18 运维与备份 | ✅ | Actuator health/metrics；`VACUUM INTO` 每日备份保留 7 份；采集连续失败写 `AGENT_CONSECUTIVE_FAILURE` 日志并可选 webhook |
| T19 AI 对比摘要 | ✅ | 已定方案（对比差异摘要）：按需生成 + 参数值指纹缓存 24h、同来源每分钟 5 次限流、未配置模型隐藏入口、失败静默降级；复用后台默认模型，未引入第二套配置 |

> P1 / P2 / P3 正文保留原始需求描述，作为验收依据。

## P0 · 第一批（已完成 2026-10-22）

### T1 图片资产本地化 ✅

**问题**：`data.sql` 中机型缩略图与品牌 Logo 指向 `cdn.spectrace.dev`，该域名当前无法解析，首页/列表/详情/对比大量显示占位图。

**改动**
- 种子数据改为本地路径：品牌 `/brands/*.png`，机型 `/uploads/devices/*.png`
- 为 7 台种子机型补统一占位图（或品牌色 + 机型名生成）
- 确认 `backend/data/uploads` 可写、`/uploads/**` 静态映射正常
- 前端 `ImageWithFallback` 已具备降级能力，无需改动

**验收**：断外网访问首页、机型库、详情、对比，均无破图；新增机型上传后立即显示。

**预估**：0.5 天

---

### T2 对比池上限与差异过滤 ✅

**问题**：对比池可无限添加，列多后表格横向撑开；没有“只看差异”开关，相同项占满屏幕。

**改动**
- Pinia `compare.add` 限制最多 4 台，超出 toast 提示
- 对比页增加“仅显示差异”Switch（默认开启）
- 参数分组支持折叠/展开
- 差异单元格底色加深，增加左侧色条

**验收**：添加第 5 台被拦截；关闭“仅显示差异”显示全部参数；折叠状态在切换机型后保留。

**预估**：0.5 天

---

### T3 对比结果可分享 ✅

**问题**：对比选择只存在 localStorage，链接发给别人无法还原。

**改动**
- `Compare.vue` 初始化读取 `route.query.ids`，写入对比池并请求
- 对比池变化时 `router.replace({ query: { ids } })`
- 增加“复制分享链接”按钮

**验收**：新无痕窗口打开 `#/compare?ids=7,4,5`，还原同样 3 台机型与对比表。

**预估**：0.5 天

---

### T4 历史页 URL 化 ✅

**问题**：参数历史选择状态无法分享、刷新丢失。

**改动**
- 读取 `route.query.brandId` / `paramDefId` 初始化
- 选择变化时 `router.replace` 同步 query

**验收**：复制 URL 到新窗口，品牌、参数、图表均还原。

**预估**：0.5 天

---

## P1 · 第二批（建议第 2 周）

### T20 存储版本与发布价格（用户新增需求）✅

**需求**：每次抓取机型时，同时抓取各存储版本与发布价格，例如 `iPhone 17 256G 5999 元 / 512G 7999 元`。

**数据模型**
- 新增 `device_variant` 表：`id`、`device_id`、`storage`、`ram`、`price`、`currency`（默认 CNY）、`price_type`（LAUNCH/CURRENT）、`sort_order`
- 删除机型时级联删除版本；同一机型「存储 + 内存」唯一

**抓取链路**
- 运行时 `backend/src/main/resources/AGENTS.md` 的输出 JSON 增加 `variants: [{ storage, ram, price, currency, priceType }]`，并要求覆盖全部存储版本
- Agent 结果以 `_variants` 键存入 `agent_pending_data.params_json`（无需新字段），待审核接口单独解析返回
- 待审核页展示版本与价格，可增删改；审核通过写入 `device_variant`

**展示**
- 机型详情页新增「存储版本与发布价」卡片，按容量升序，并展示起售价
- 对比页表头下方固定一行「存储 / 发布价」，逐版本列出价格
- 管理端机型表单可维护版本列表

**验收**：抓取 iPhone 17 后待审核能看到 256G/512G 两档价格；通过后详情页展示价格表；对比页显示各版本价格；编辑机型可改版本。

**预估**：3 天（实际 2026-10-22 当日完成）

**已完成（2026-10-22）**：
- 后端：`device_variant` 建表 + 种子价、`DeviceVariant`/Mapper、详情与对比接口返回版本与起售价、保存时全量覆盖、采集回调与审核通过双路径写入
- 采集：persona 提示词要求输出 `variants`，并同步到 `backend/.agentscope/workspace/AGENTS.md`
- 前端：详情页价格表 + 起售价、对比页「存储 / 发布价」行、管理端机型表单与待审核弹窗的版本编辑
- 验证：`mvn package`、`npm run typecheck`、`npm run build` 均通过；iPhone 17 详情返回 256GB ¥5999 / 512GB ¥7999，对比页与后台编辑弹窗均已实测

---

### T5 参数字段级来源与更新时间 ✅

**问题**：正式入库的参数值没有来源与更新时间，用户无法判断可信度和时效。

**改动**
- `device_param_value` 增加 `source_url`、`confidence`、`updated_at`（SQLite 需迁移脚本，兼容已有库）
- 审核通过时把 `agent_pending_data.source_url` 写入对应参数值
- 人工编辑时刷新 `updated_at`
- 详情页每行参数展示来源链接与“更新于”
- 机型头部展示“最后更新”

**验收**：抓取入库的参数能看到来源；手工改一个值后更新时间变化；旧数据字段为空时 UI 不报错。

**预估**：1.5 天

---

### T6 待审核 diff ✅

**问题**：审核页只展示抓取值，看不出与库中现有值的差异。

**改动**
- `pendingDetail` 返回同品牌同名机型的现有参数
- 审核弹窗标出变化项：`旧值 → 新值`
- 无变化项折叠显示

**验收**：对已有 iPhone 17 再抓一次，弹窗能标出 3 处变化；新增机型显示“新机型”。

**预估**：0.5 天

---

### T7 参数纠错入口 ✅

**问题**：访客发现参数错误只能离开，数据缺少反馈闭环。

**改动**
- 详情页参数行增加“报错”入口
- 弹窗字段：参数、当前值、建议值、说明（可选联系方式）
- 后端新增 `feedback` 表与 `POST /api/public/feedback`，做简单限流
- 管理端新增“纠错反馈”列表（可并入待审核页 Tab）

**验收**：未登录可提交；后台能看到记录并标记处理状态；重复提交 1 分钟内被限流。

**预估**：1.5 天

---

## P2 · 第三批（建议第 3 周）

### T8 后台批量导入机型 ✅

**改动**
- 提供 CSV 模板（机型、品牌、发布日期、参数 Key）
- 上传 → 解析预览 → 错误行提示 → 确认导入
- 复用 `saveDevice`，逐条事务或批量事务

**验收**：导入 10 台机型成功；含错误品牌的行被跳过并提示行号。

**预估**：2 天

---

### T9 复制机型 ✅

**改动**
- 机型列表/编辑弹窗增加“复制”
- 带出全部参数，名称追加“副本”，发布日期置空

**验收**：复制后生成新机型，参数完整，原机型不受影响。

**预估**：0.5 天

---

### T10 抓取任务实时状态与重试 ✅

**改动**
- 列表在页面可见时每 5 秒轮询（或 SSE）
- 失败任务增加“重试”按钮，复用创建任务接口
- 任务详情抽屉展示来源链接、耗时、错误信息

**验收**：提交任务后状态自动从“排队中”变为“已完成/失败”，无需手动刷新；重试生成新任务。

**预估**：1 天

---

### T11 待审核批量操作 ✅

**改动**
- 表格增加多选 Checkbox
- 批量通过 / 批量丢弃（通过时使用抓取值直接入库）

**验收**：勾选 5 条批量通过，列表与机型库同步更新。

**预估**：1 天

---

### T12 图片上传优化 ✅

**改动**
- 前端 canvas 压缩：最长边 1600px，转 WebP
- 后端限制大小与 MIME，返回尺寸
- 上传进度与失败重试

**验收**：上传 5MB 原图后落盘小于 500KB，页面显示正常。

**预估**：1 天

---

## P3 · 第四批（第 4 周及以后）

### T13 参数历史多品牌叠加 ✅

**改动**
- 品牌改为多选（最多 4 个）
- 按品牌分组 series，统一单位校验
- 极值点标注、导出 CSV

**验收**：同时选 Apple 与华为，图表出现两条折线；单位不一致时提示。

**预估**：1.5 天

---

### T14 搜索联想与拼音 ✅

**改动**
- 新增 `GET /api/public/devices/suggest?keyword=`
- 首页与机型库接入联想下拉
- 支持拼音首字母（可引入 `pinyin-pro`）

**验收**：输入 `pg` 能找到“苹果”相关机型；输入 `iph` 出现 iPhone 列表。

**预估**：1.5 天

---

### T15 SEO 与 history 路由 ✅

**改动**
- `createWebHashHistory` 改 `createWebHistory`
- 后端增加 SPA fallback（非 `/api` 路径回退 `index.html`）
- 机型详情动态 `title` / `description` / OG 标签
- 可选：sitemap.xml

**验收**：`/devices/7` 可直接访问；分享卡片有标题与描述。

**预估**：1 天

---

### T16 可访问性修复 ✅

**改动**
- Dialog 补 `Description`（消除 reka-ui 运行时警告）
- 表单 `label for` 与控件 `id` 全量对齐
- 对比表增加 `caption`、表头 `scope`
- 键盘焦点顺序与 Esc 关闭检查

**验收**：控制台无 Dialog 警告；Tab 可完成登录与新增品牌流程。

**预估**：0.5 天

---

### T17 E2E 测试 ✅

**改动**
- Playwright 覆盖两条主链路：
  1. 首页 → 点品牌 → 筛选 → 加入对比 → 对比页 → 复制分享链接
  2. 登录 → 新增品牌 → 新增机型 → 提交抓取（mock）→ 审核通过

**验收**：CI 可跑，失败截图与 trace 保留。

**预估**：1.5 天

---

### T18 运维与备份 ✅

**改动**
- 接入 Actuator `health` / `metrics`
- SQLite 每日备份脚本（`VACUUM INTO`），保留 7 份
- Agent 任务连续失败告警（日志关键字或 webhook）

**验收**：健康检查可访问；备份文件可恢复到新库。

**预估**：1 天

---

### T19 AI 增强（已确认：只做对比差异摘要，详见 P4）✅

**候选**
- 自然语言选机：“5000 元以内拍照好的手机” → 筛选条件
- 对比页差异摘要：一句话总结主要差异
- 参数解读：对普通用户解释参数含义

**验收**：按选定场景单独定义；需先确认模型与调用成本。

**预估**：2–3 天

---

## P4 · 后续补强（2026-10-23 新增，按建议优先级排序）

> 来源：P1–P3 交付后的缺口复盘。除 T19 外均为已评估、可直接开工的项。

### T21 参数级 confidence 落库（优先级 1）✅

**已完成（2026-10-23）**：Agent 输出可选 `paramConfidence`，缺省回落到 `confidence` 等级（high/medium/low → 0.9/0.7/0.4）；置信度与 `_variants` 一样收口到 `params_json._confidence`，审核通过时沿用、管理员在审核页改动过的参数自动清空；`device_param_value.confidence` 改用显式 set 保证可置空；详情页展示置信度，审核页对低于 60% 的参数标注「建议核实」。

**问题**：`device_param_value.confidence` 已建列、`ParamMetaVO` 已返回，但没有写入路径，实际恒为 `null`，溯源信息不完整。

**改动**
- 明确采集置信度粒度：优先参数级（`params` 每项带 confidence），否则退化为整次抓取统一值
- 待审核 JSON 保留置信度映射（参考 `_variants` 的做法，用 `_confidence` 键，不改表结构）
- `saveParamValues` 增加 confidence 入参：抓取写入、人工修改置空
- 详情页参数行展示置信度，低于阈值（如 0.6）打「待核实」标记

**验收**：抓取入库的参数能看到置信度；手工改值后置信度清空；旧数据为 null 时 UI 不报错。

**预估**：0.5 天

---

### T22 备份手动触发与后台可见（优先级 2）✅

**已完成（2026-10-23）**：新增 `GET/POST /api/admin/maintenance/backups`（仅 `SUPER_ADMIN`）与后台「系统运维」页，展示备份状态、目录、保留份数、下次执行时间与备份列表，支持立即备份；手动备份写入操作日志。

**问题**：备份只有每日 cron，无法临时留档，也看不到历史备份是否成功。

**改动**
- `POST /api/admin/maintenance/backup` 立即执行一次备份
- `GET /api/admin/maintenance/backups` 返回文件名 / 大小 / 生成时间
- 后台新增「系统运维」页：备份列表 + 立即备份按钮 + 恢复步骤说明，并在页头展示下次执行时间
- 备份失败返回明确错误（目录不可写、磁盘满）

**验收**：点按钮后列表出现新文件；连续触发不覆盖旧文件；非超管账号无权调用。

**预估**：0.5–1 天

---

### T23 sitemap.xml 与 robots.txt（优先级 3）✅

**已完成（2026-10-23）**：新增 `GET /sitemap.xml`（首页 / 机型库 / 参数历史 / 全部在售机型，带 `lastmod`、`changefreq`、`priority`）与 `GET /robots.txt`（屏蔽 `/admin/`、`/api/`、`/uploads/` 并指向 sitemap）；对外域名取 `SPECTRACE_PUBLIC_BASE_URL`，未配置时回退请求域名；两者均公开访问并带 1 小时缓存。

**问题**：history 路由与动态 meta 已就绪，但搜索引擎缺少入口。

**改动**
- 后端按品牌 + 机型生成 `/sitemap.xml`（含首页、机型库、每台机型详情）
- 新增 `SPECTRACE_PUBLIC_BASE_URL`（站点对外域名），未配置时回退请求域名并记 debug 日志
- `/robots.txt` 指向 sitemap 并屏蔽 `/admin/**`

**验收**：`curl /sitemap.xml` 返回合法 XML 且 URL 完整；搜索结果收录详情页。

**预估**：0.5 天

---

### T24 参数值单位解析与一致性校验（优先级 4）✅

**已完成（2026-10-23）**：新增 `ParamValueParser`，拆分数值与单位并做同量纲换算（长度/存储/电池/重量/频率/功率/倍率）；历史接口返回换算后的数值与 `unitConflicts`，前端提示冲突并按原值展示；审核详情返回 `paramWarnings`、导入预览把单位冲突写入行级 warnings。

**问题**：目前只判断「是否纯数字」，`6.1英寸` 与 `6.1寸` 这类混写无法对比，历史图表只能降级为文本。

**改动**
- 解析值中的数值 + 单位，与 `paramDef.unit` 比对，可换算的换算、不可换算的标记冲突
- 审核页与导入预览提示单位冲突行，避免脏数据入库
- 历史图表使用解析后的数值，保留原始值用于展示

**验收**：`6.1英寸` / `6.1寸` 能正确对比；单位无法识别时给出明确提示而非静默按文本处理。

**预估**：1–1.5 天

---

### T25 上传体积硬约束（优先级 5）✅

**已完成（2026-10-23）**：`compressImage` 改为按目标体积（500KB）逐级重压：质量 0.85 → 0.7 → 0.55、最长边 1600 → 1280，命中即停；仍超限时返回 `exceeded`，机型与品牌上传均提示「建议裁剪后再上传」而不是静默失败。后端硬上限保持可配置（`SPECTRACE_UPLOAD_MAX_BYTES`，默认 8MB）。压缩结果体积由返回的 `size` 展示。

**问题**：「5MB 原图落盘 <500KB」当前只是大概率结果，压缩后仍可能超 500KB。

**改动**
- 压缩后超过目标体积时逐级降质/降尺寸重压（最多 3 次）
- 仍超限则提示用户裁剪，后端保留可配置硬上限
- 上传结果返回最终体积，前端提示实际大小

**验收**：5MB 原图上传后落盘稳定 <500KB；极端图片给出可读提示而非静默失败。

**预估**：0.5 天

---

### T26 键盘可达性走查（优先级 6）✅

**已完成（2026-10-23）**：确认 shadcn 组件已统一 `focus-visible:ring` 焦点样式，未发现缺失；新增 `e2e/keyboard.spec.ts` 覆盖「Tab 切换用户名/密码 → Enter 提交登录」与「键盘打开弹窗 → 焦点进入弹窗 → Esc 关闭」，纳入 `npm run test:e2e`。逐页 Tab 顺序的人工复核仍建议在真机/读屏环境补一次。

**问题**：已补语义与 `label for`，但未系统验证键盘路径。

**改动**
- 逐页检查 Tab 顺序、焦点可见、Esc 关闭弹窗、对比表横向滚动可键盘操作
- 修复焦点陷阱与不可见焦点（`focus-visible` 样式缺失处）
- 记录走查结论到 README 的「开发规约」

**验收**：仅用键盘可完成登录、新增品牌、新增机型、提交抓取、审核通过。

**预估**：0.5 天

---

### T19 AI 对比差异摘要 ✅

**已确认方案**：对比页按需生成 2-3 句差异摘要；不做自然语言选机（依赖参数语义映射，当前机型库样本不足），不做参数解读（通用知识用静态文案即可覆盖）。

**落地**
- 后端 `AiChatService` 复用后台默认模型的 OpenAI 兼容 `/chat/completions`；`CompareInsightService` 只取「有实际取值且不同」的参数行（上限 30 行），把模型输出解析为 `summary + points` 并做长度裁剪
- 缓存：key = 排序后的机型 id + 按机型归档的参数值指纹，默认 24 小时（`SPECTRACE_AI_INSIGHT_CACHE_HOURS`）；换机型顺序仍命中，参数变更自然失效，不需要额外失效逻辑
- 护栏：同来源每分钟 5 次生成（命中缓存也计入生成请求，第 6 次返回 HTTP 429）；未配置模型或开关关闭时返回 `available=false`，前端整块入口隐藏；调用失败降级为 `failed=true`（HTTP 200），不弹错误、不阻塞对比表
- 前端：对比页顶部卡片，只有点「生成摘要」才调用；展示「AI 生成，仅供参考」；摘要区不进入截图范围
- 顺带修复：`ai_model` 的默认标记改为全局唯一（此前每个连接都可各有一个默认，`resolve` 可能取到非预期模型）

**验收**：本地假模型端到端验证——首次 `cached=false`、反序机型组合 `cached=true`、第 6 次生成返回 429、接口地址不可达时 `failed=true` 且 HTTP 200、未配置模型时 `available=false`；e2e 覆盖入口展示与生成结果。

---

## P5 · 世代回顾（2026-10-23 完成）

> 场景：选择 iPhone 11 → iPhone 17，一次看到屏幕尺寸、电池容量等多张趋势图，回顾历代变化。
> 已确认：系列按**产品线**人工维护（不做自动识别）；x 轴按发布时间，允许同代多机型并存；一期只做图表，AI 文字回顾放二期。

| 任务 | 状态 | 落地要点 |
| --- | --- | --- |
| T27 数据模型与种子 | ✅ | 新增 `device_series` 表与 `device.series_id`（逻辑归属、可空）；`SqliteSchemaMigration` 为老库补列并建索引；种子由 `SeriesInitializer` 在补列后写入，只在系列首次创建时归类，不覆盖后台调整 |
| T28 后台系列管理与归类 | ✅ | `/api/admin/series` 增 CRUD + `unassigned` / `assign` / `{id}/merge`；有机型在册时删除被拒绝；后台「系列管理」页支持品牌筛选、拖拽排序、机型数与年份范围、编辑/合并/删除、未归类机型即时归类；机型表单可按品牌选系列并就地新建 |
| T29 导入支持系列列 | ✅ | CSV 模板增 `series` 列（兼容「系列」「产品线」）；按品牌内名称匹配、不存在则创建，预览提示「将新建系列 xxx」；留空视为未归类 |
| T30 世代回顾聚合接口 | ✅ | `GET /api/public/series` 返回机型数与起止年份；`GET /api/public/series/{id}/evolution` 按发布时间升序返回机型序列、换算后的取值点、涨跌幅/趋势、缺失计数；`launch_price` 取变体最低价，无数据参数返回空 points，参数上限 8 |
| T31 世代回顾页面 | ✅ | 新增 `/evolution` 与导航；品牌 → 系列 → 参数多选（默认屏幕尺寸/电池容量/重量/起售价）→ 起止世代；世代横条可跳详情；图表 2 列（移动端 1 列）含极值标注与涨跌幅徽标；整块截图导出；URL 同步 `seriesId` / `paramDefIds` |

### T27 系列数据模型与种子 ✅

**改动**
- 新增 `device_series`（brand_id / name / sort_order / status），`device.series_id` 可空外键（逻辑归属，不做物理约束）
- `SqliteSchemaMigration` 为老库补 `device.series_id`
- 种子：`iPhone 数字系列`（13~17）、`小米数字系列`（小米 14）、`Mate 系列`（Mate 60 Pro），仅给 `series_id IS NULL` 的机型赋值，不覆盖后台调整

**验收**：新库与老库启动后都有 `device_series` 表与 `series_id` 列；种子机型正确归属。

**预估**：0.5 天

---

### T28 后台系列管理与归类 ✅

**改动**
- `GET/POST/PUT/DELETE /api/admin/series` + `POST /{id}/merge` + `POST /assign` + `GET /unassigned`
- 删除有在册机型的系列时拒绝；合并把机型迁到目标系列后删除源系列
- 后台「系列管理」页：品牌筛选、拖拽排序、机型数与年份范围、编辑/合并/删除
- 「未归类机型」区：逐行选择系列即时归类，支持按品牌筛选
- 机型表单增加系列下拉（按所选品牌过滤）+ 就地新建系列

**验收**：新建/改名/合并/删除可用；未归类机型可一键挂到系列；机型表单能新建系列并选中。

**预估**：1.5 天

---

### T29 导入支持系列列 ✅

**改动**
- CSV 模板增加 `series` 列（中文表头「系列」兼容）
- 导入时按「品牌内系列名」匹配，不存在则自动创建；预览行提示「将新建系列 xxx」
- 合并/空值行允许留空，视为未归类

**验收**：导入 3 行、其中 1 个新系列，导入后系列与机型归属正确，预览能看到新建提示。

**预估**：0.5 天

---

### T30 世代回顾聚合接口 ✅

**改动**
- `GET /api/public/series?brandId=` 返回系列列表（机型数、起止年份）
- `GET /api/public/series/{id}/evolution?paramDefIds=` 返回：机型序列（按发布时间升序）、每个参数的取值点（复用 `ParamValueParser` 换算）、涨跌幅与趋势、缺失计数
- `launch_price` 取 `device_variant` 起售价；参数个数上限 8

**验收**：iPhone 数字系列 + 屏幕尺寸/电池容量 返回完整点列；同代多机型各自成点；无数据参数返回空 points。

**预估**：0.5–1 天

---

### T31 世代回顾页面 ✅

**改动**
- 新增 `/evolution`（导航「世代回顾」）
- 筛选：品牌 → 系列 → 参数多选（默认屏幕尺寸 / 电池容量 / 重量 / 起售价）→ 起止世代
- 世代横条：缩略图 + 名称 + 年份 + 起售价，可跳详情
- 图表网格：2 列（移动端 1 列），折线 + 极值 + 涨跌幅徽标；整块截图导出
- URL 同步（`seriesId` / `paramDefIds`），可分享

**验收**：选 iPhone 数字系列能同时看到 4 张趋势图；切换参数与区间即时刷新；移动端单列可读。

**预估**：1.5–2 天

---

## P6 · 收尾补强（2026-10-23 完成）

> 来源：TODO 清理后对代码残留的复盘，均为已确认方案的小改动。

| 任务 | 状态 | 落地要点 |
| --- | --- | --- |
| T32 世代回顾涨跌幅单一算法 | ✅ | 删除后端 `EvolutionParamVO` 无人使用的 `firstValue` / `lastValue` / `changeRate` / `trend` 与 `EvolutionService.trendOf`；涨跌幅只在前端按可见区间计算，避免区间裁剪后两套口径互相矛盾 |
| T33 后台会话校验 | ✅ | 路由守卫调用 `/api/auth/me` 确认 token 仍有效，失效清凭证回登录页；每次整页加载校验一次，校验请求静默失败（`skipAuthRedirect`）不弹全局错误 |
| T34 键盘走查补强 | ✅ | 新增 e2e：纯 Tab + Enter 完成登录、新增品牌、新增机型、提交抓取、审核通过；真机与读屏环境的人工复核仍需发布前补一次 |
| T35 世代回顾 AI 文案 | ✅ | `GET/POST /api/public/series/{id}/insight`，按需生成 + 趋势评价；抽出 `AiInsightSupport` 统一限流/缓存/解析，对比摘要同步改用它；缓存 key 含可见机型与参数值，换区间自然失效 |

### T36 当前价（`priceType=CURRENT`）采集 ❌ 不做（2026-10-24 决策）

**现状**：`device_variant` 已有 `price_type` / `currency` 字段，但写入路径、采集提示词、种子数据三处都只产生 `LAUNCH` / `CNY`，`CURRENT` 没有数据源。

**决策**：不采集「当前价」——涉及电商价格波动、来源合法性与更新频率，维护成本与数据可信度不成正比。`price_type` 字段保留，若未来需要可降级为「用户提交当前价 + 人工审核」的众包模式再立项。

---

## P7 · 质量补强（2026-10-23 完成）

> 来源：T1–T35 全量完成后的工程复盘。

| 任务 | 状态 | 落地要点 |
| --- | --- | --- |
| T37 CI 测试校验 | ✅ | 新增 `.github/workflows/ci.yml`：PR 与主干推送跑前端 typecheck / build / e2e、后端 `mvn -B package`（含单测）；npm 与 Maven 走缓存，失败上传 trace / 截图 / 视频 |
| T38 后端纯逻辑单测 | ✅ | 新增 23 个用例：`ParamValueParser`（换算、别名、g 消歧、冲突）、`AiInsightSupport`（解析兜底、裁剪、缓存过期、限流）、`DeviceImportService`（表头兼容、行号、重复、单位警告）、`EvolutionService`（排序、缺失点、最低价、参数上限） |
| T39 e2e 后台页面缺口 | ✅ | 新增 `admin-quality.spec.ts` 覆盖账号新增/禁用/删除、参数分组与定义维护、纠错反馈状态流转；mock 补对应写接口 |

### T37 CI 增加测试校验（优先级 1）✅

**已完成（2026-10-23）**：`.github/workflows/ci.yml` 分两个 job——前端 `npm ci` → typecheck → build → `playwright install chromium` → e2e，后端 `mvn -B package`（含单测）；失败时上传 `playwright-report` 与 `test-results`，保留 7 天。

**问题**：`.github/workflows` 只有 `docker-publish.yml`，PR 仅做镜像构建校验，不跑 typecheck / build / 后端编译 / e2e；目前全靠提交前手动执行，人一多必然漏。

**改动**
- 新增 PR 与 push 触发的工作流：前端 `typecheck` + `build`、后端 `mvn -DskipTests package`、e2e（Playwright 走 mock 接口，不需要数据库）
- 依赖缓存：npm、Maven、Playwright 浏览器
- 失败保留 e2e 的 trace / 截图 / 视频

**验收**：故意引入一个类型错误，PR 检查变红且能定位到具体文件；恢复后变绿。

**预估**：0.5 天

---

### T38 后端关键纯逻辑单测（优先级 2）✅

**已完成（2026-10-23）**：`ParamValueParserTest`（7）、`AiInsightSupportTest`（5）、`DeviceImportServiceTest`（6，dryRun 分支）、`EvolutionServiceTest`（5，聚合与排序），共 23 个用例，`mvn test` 全绿。MyBatis-Plus 的 lambda 查询在脱离容器时需 `TableInfoHelper` 显式注册，测试基类已处理。

**问题**：`backend/src` 只有 `main`，没有任何测试，CI 即使跑起来也只能验编译。

**改动**
- `ParamValueParser`：同量纲换算（长度 / 存储 / 电池 / 重量 / 频率 / 功率 / 倍率）、无单位、量纲冲突、非数值兜底
- `DeviceImportService` 的 CSV 解析：表头兼容（`series` / 系列 / 产品线）、空行、错误行号、单位冲突写入 warnings
- `EvolutionService` 聚合：发布时间排序（空值排末尾）、缺失点、`launch_price` 取变体最低价
- `AiInsightSupport.parse`：合法 JSON、非 JSON 纯文本兜底、超长裁剪

**验收**：`mvn test` 全绿，覆盖上述四类分支；不追求整体覆盖率。

**预估**：1–1.5 天

---

### T39 e2e 后台页面缺口（优先级 3）✅

**已完成（2026-10-23）**：新增 `e2e/admin-quality.spec.ts` 三个用例（账号新增/禁用/删除、参数分组与参数定义维护、纠错反馈标记已处理），`mock-api.ts` 补齐 users / param-groups / param-defs / feedback 的写接口；全量 10 条 e2e 通过。AI 配置、日志、运维三个只读页仍未加断言，优先级低。

**问题**：后台 12 个页面中，参数管理、AI 配置、纠错反馈、用户、日志、运维没有用例。

**改动**
- 按风险补：用户管理（角色 / 禁用）、参数管理（影响全站展示）、纠错反馈（状态流转）
- 其余页面（AI 配置、日志、运维）只要有入口与空态断言即可

**验收**：新增用例纳入 `npm run test:e2e` 并全部通过。

**预估**：1 天

---

## P8 · 账号安全（2026-10-23 完成）

| 任务 | 状态 | 落地要点 |
| --- | --- | --- |
| T40 初始密码强制修改 | ✅ | `admin_user.must_change_password` 标记初始密码与管理员重置的密码；登录响应与 `/auth/me` 均返回该标记，路由守卫把用户锁在 `/admin/change-password`；自助改密 `PUT /api/auth/password` 校验原密码并清除标记；`SqliteSchemaMigration` 补列并对仍匹配初始口令的账号打标记；启动日志不再打印明文口令 |

---

## P9 · 可靠性与安全收口（2026-10-24 新增）

> 来源：全量代码复盘（后端核心链路 / 安全面 / 前端工程三项并行审查）。按建议执行顺序排序：先修正确性与安全（T41–T45），再做前端重构与性能治理。后端为主的一项一个 commit，全部小改动。

| 任务 | 状态 | 落地要点 |
| --- | --- | --- |
| T41 采集任务可靠性 | ✅ | `CollectorWorker.handle` 兜底 try/catch（异常落 FAILED，消费线程不退出）；启动时 `recoverInterruptedTasks`——RUNNING 置失败、内存队列丢失的 QUEUED 重新入队（Redis 持久不重复入队）；`createTask` 入队失败落 FAILED；`TaskQueue.isPersistent()` 区分队列持久性 |
| T42 批量审核事务 | ✅ | `batchApprove` / `batchDiscard` 拆掉外层 `@Transactional`，改用 `TransactionTemplate` 逐条独立事务，失败条目不再残留半截写入 |
| T43 JWT 密钥 | ✅ | secret 改 `${SPECTRACE_JWT_SECRET:}`，`JwtUtil` 启动校验为空拒绝启动；README 快速启动与 `.env.example` 同步 |
| T44 登录防爆破 | ✅ | 新增 `LoginAttemptGuard`：同账号 10 分钟内失败 5 次锁定（成功登录清零，锁定期正确密码也拒绝，单测覆盖）；`JwtAuthenticationFilter` 每请求回查 `admin_user`——禁用/删除 token 即失效、角色以库内为准、未改密 token 只放行 `/api/auth/**`；登录失败写操作日志 |
| T45 SQL 日志 | ✅ | 删除 `log-impl: StdOutImpl`，杜绝 AI API Key / BCrypt 哈希随 SQL 参数打进 stdout |
| T46 CORS 白名单 | ✅ | `SPECTRACE_CORS_ORIGINS` 逗号分隔白名单，未配置保持任意来源（自部署友好），README 提示公网必配 |
| T47 公开限流 | ✅ | 通用 `MemoryRateLimiter` + `PublicRateLimitFilter`（`/api/public/**` 单 IP 每分钟 120 次，`SPECTRACE_PUBLIC_QPM` 可配）；`/devices` size 收敛 100、suggest/hot limit 收敛 50；三处重复的 X-Forwarded-For 解析收敛到 `ClientIps` |
| T49 公开读缓存 | ✅ | 引入 Caffeine：brands / paramMeta（分组+定义）/ seriesList / deviceDetail 五分钟 TTL + 写路径 `@CacheEvict` 主动失效；`releaseYears` 改 SQL `strftime` 聚合；顺带修复机型删除不清理参数值的孤儿数据问题（补 `@Transactional`） |
| T50 数据保留策略 | ✅ | 新增 `DataRetentionService`：终态任务（含过程日志）、已处理待审核、操作日志按天定时清理（默认 04:00，排在备份后）；`/api/admin/maintenance/retention` 概览与手动清理（仅超管、写操作日志）；「系统运维」页新增保留策略卡片 |
| T48 usePagedList | ✅ | 新增 `src/composables/usePagedList.ts`（query / load / silent 轮询 / onLoaded 钩子 / seq 竞态兜底）；Logs、Feedback、Users、PendingData、AgentTasks、Device 六页迁移；筛选变更合并为单次 load，修复 PendingData / AgentTasks 非首页切状态的双请求竞态 |
| T52 pinyin-pro 按需加载 | ✅ | DeviceSearch 首次聚焦才动态 `import('pinyin-pro')` 并构建本地索引，DeviceSearch chunk 从 221KB 降到 4KB，词典变为异步 chunk |
| T53 404 与对比候选 | ✅ | 路由补 `/:pathMatch(.*)*` + `NotFound.vue`（保留访客导航）；Combobox 增加 `remote` 模式与 `search` 事件（向后兼容），Compare 候选改为默认最近 50 台 + suggest 按需联想，不再一次拉 200 台 |
| T51 截图库按需加载 | ✅ | html2canvas 三页导出函数内动态 import；`useECharts`（图表生命周期 + 按需注册 + prune/dispose + 自适应 resize）与 `useInsight` + `InsightCard.vue`（AI 文案状态机与 UI）抽取完成，Compare / Evolution / History 三页迁移，页面 chunk 降到 12–15KB |
| T54 类型契约与 mock 绑定 | ✅ | 新增 `src/types/api.ts`（实体/VO/请求类型，以后端 VO 为准）；`api/index.ts` 全量类型化；12 个视图本地接口删除改用共享类型；e2e `mock-api.ts` 引用共享类型，未匹配接口从静默 `ok(null)` 改为 404 报错；新增 `tsconfig.e2e.json` 并把 e2e 类型检查并入 `npm run typecheck` |
| T55 SQLite 写连接池收敛 | ✅ | Hikari `maximum-pool-size` 收敛为 4（WAL 单写者下大池放大写锁竞争），配置内注释说明理由 |

### T41 采集任务可靠性收口（优先级 1）✅

**问题**：三处叠加造成「幽灵任务」：
1. `CollectorWorker` 消费循环只对 `poll` 做 try/catch，`handle()` 裸调——worker 线程遇一次 DB 异常（如 SQLITE_BUSY）即永久退出，之后所有任务永远排队且无告警；
2. 内存队列重启即丢，DB 中任务卡 QUEUED，而 `retry()` 仅允许 FAILED 重试，卡死任务无法挽回；
3. 应用启动时无 RUNNING/QUEUED 状态重扫，崩溃后任务永久卡中间态。

**改动**
- `handle()` 包 try/catch：任何异常把当前任务置 FAILED（带错误信息），worker 线程绝不退出
- 启动时重扫：RUNNING → FAILED（标记「应用重启中断」）；内存队列模式下未完成任务不再永久卡死
- `retry()` 放开 QUEUED / RUNNING 卡死态
- `createTask` 加固：入队失败时任务置 FAILED，不留 QUEUED 僵尸

**验收**：模拟 handle 抛异常后 worker 仍能继续消费下一条；杀进程重启后无永久 QUEUED/RUNNING 任务；QUEUED 卡死任务可重试。

**预估**：1 天

---

### T42 batchApprove 单条独立事务（优先级 2）✅

**问题**：`AgentTaskService.batchApprove` 外层 `@Transactional` + 循环内 catch + `this.approve()` 自调用不走事务传播——某条中途失败时，它已执行的半截写入随外层事务一起提交，产生「半入库」脏数据，与「单条失败不影响其余」的注释语义矛盾。

**改动**
- 拆掉外层 `@Transactional`，每条走独立事务（注入自身代理 `REQUIRES_NEW` 或抽独立 bean）
- `batchDiscard` 同样排查修正

**验收**：构造中途失败场景，失败条目在库中无任何残留写入；成功条目不受影响。

**预估**：0.5 天

---

### T43 JWT 密钥强制外部配置（优先级 3）✅

**问题**：`application.yml` 硬编码默认 secret（其它配置都用 `${ENV:default}` 形式，唯独它是纯字面量），部署忘改即可伪造 SUPER_ADMIN token。

**改动**
- secret 改为 `${SPECTRACE_JWT_SECRET:}`，为空时启动失败并提示配置项名
- README 与 `.env.example` 同步说明

**验收**：未配置时启动报错且信息明确；配置后正常签发 token。

**预估**：0.5 天

---

### T44 登录防爆破与改密后端强制（优先级 4）✅

**问题**：登录无失败锁定/限流；`must_change_password` 仅前端路由守卫拦截，后端过滤器不拦——持 token 不改密仍可调全部管理接口；账号被禁用后 token 最长 24h 仍有效（过滤器只信 claim）。

**改动**
- 登录失败限流：同账号维度内存计数（复用 FeedbackService 的内存限流模式），连续失败 5 次锁定 10 分钟，错误信息不泄露账号是否存在
- `JwtAuthenticationFilter` 每请求回查 `admin_user`：禁用即 401；`must_change_password` 标记的 token 仅放行 `/api/auth/**`（顺带让禁用/降权即时生效）
- 登录失败写操作日志，便于追溯爆破

**验收**：连续错 5 次密码被锁定并提示；未改密账号带 token 调管理接口被拒；禁用账号 token 立即失效。

**预估**：1 天

---

### T45 移除 MyBatis StdOutImpl 日志（优先级 5）✅

**问题**：`application.yml` 的 `log-impl: StdOutImpl` 把 SQL 参数（明文 AI API Key、BCrypt 哈希）直打 stdout，绕过日志框架与级别控制；高并发下也是性能开销。

**改动**：删除该行，MyBatis 默认走 SLF4J。需要调试 SQL 时临时加回。

**验收**：启动与保存 AI 配置时 stdout 无 SQL 参数输出。

**预估**：0.1 天

---

### T46 CORS 白名单可配置（优先级 6）✅

**问题**：`WebCorsConfig` `allowedOriginPatterns("*")` + `allowCredentials(true)`，任意站点可携带凭据跨域探测。

**改动**
- 新增 `SPECTRACE_CORS_ORIGINS`（逗号分隔白名单）：配置后仅放行名单内 Origin
- 未配置保持现行为（自部署/局域网友好），README 提示公网部署应配置

**验收**：配置白名单后域外预检被拒；未配置时行为不变。

**预估**：0.5 天

---

### T47 公开接口统一限流与 size 上限（优先级 7）✅

**问题**：search/suggest/compare/history/series 等公开接口全无限流；`/devices` 的 `size` 无上限可一次爬全库；仅 feedback / insight 各有限流且实现分散。

**改动**
- 抽通用内存限流组件（同 IP 每分钟 N 次，阈值可配），公开查询接口统一接入；feedback / insight 迁移复用
- `/devices` size 上限收敛（如 100）
- X-Forwarded-For 信任现状保留（部署在反代后），README 说明

**验收**：超限返回 429 且有明确提示；size 超上限被收敛；e2e 不受影响（mock 拦截）。

**预估**：1 天

---

### T48 usePagedList composable 收敛 admin 列表页（优先级 8）✅

**问题**：6+ 个 admin 列表页重复同一段 query/load/watch/Pagination/Skeleton 样板（60 处 `finally`、7 处相同 Skeleton）；PendingData / AgentTasks 的 `watch(status){ current=1; load() }` 在非第 1 页切状态时与 current-watch 并发触发两次 load，存在竞态。

**改动**
- 新增 `src/composables/usePagedList.ts`：统一 query、load、loading、finally、分页 watch、筛选变更合并为单次 load（重置页码后只发一次请求）
- 逐页迁移 Device / PendingData / AgentTasks / Users / Logs / Feedback

**验收**：各列表页功能与交互不变（e2e 全绿）；切筛选不再并发双请求。

**预估**：1–1.5 天

---

### T49 公开接口 Caffeine 缓存与 SQL 聚合（优先级 9）✅

**问题**：brands / param-defs / series / detail 等准静态数据每次请求直打 SQLite，无任何缓存；`releaseYears` 全表载入内存做 Java distinct 而非 SQL 聚合，公开端可低成本制造慢查询。

**改动**
- 引入 Caffeine：brands / param-defs / series / 设备详情等短 TTL（默认 5 分钟，可配），管理端写操作主动失效对应 key
- `releaseYears` 改 SQL 聚合
- 公开 GET 视情况加 Cache-Control 头

**验收**：二次请求命中缓存（日志可验证）；后台改数据后公开侧即时或短延迟可见；`mvn test` 全绿。

**预估**：1–1.5 天

---

### T50 增长表清理策略（优先级 10）✅

**问题**：`agent_task.result` 存整份参数 JSON、`agent_task_log`、`operation_log`、已处理的 `agent_pending_data` 只进不出，SQLite 文件无界膨胀直接拖慢查询与 `VACUUM INTO` 备份。

**改动**
- 每日定时清理（与备份同一调度）：已完成任务及其日志保留 N 天（默认 30，可配）、`operation_log` 保留 90 天、已处理 pending 保留 30 天
- 「系统运维」页展示各增长表行数与保留策略，支持手动触发清理并写操作日志

**验收**：超过保留期的记录被清理且日志可查；手动触发生效；非超管无权调用。

**预估**：1 天

---

### T51 useECharts / InsightCard 收敛与 html2canvas 按需加载（优先级 11）✅

**问题**：Evolution / History / Compare 三页各自维护一份 ECharts 生命周期（初始化、resize、dispose、暗色重渲染）与 AI insight 块（UI + 取缓存 + 生成 + 429 豁免 + 降级）；html2canvas 静态 import 进三个路由 chunk（各约 200KB）。

**改动**
- 抽 `useECharts` composable 与 `InsightCard.vue` + `useInsight` composable，三页收敛复用
- html2canvas 改为导出函数内动态 `import()`

**验收**：三页图表、AI 摘要、截图导出行为不变（e2e 全绿）；构建后首屏与三路由 chunk 不再静态含 html2canvas。

**预估**：1.5 天

---

### T52 pinyin-pro 动态加载（优先级 12）✅

**问题**：约 300KB 拼音词典随 DeviceSearch 静态打进首页加载链路。

**改动**：DeviceSearch 在首次聚焦/展开时动态 `import('pinyin-pro')`。

**验收**：首屏 chunk 不含 pinyin-pro；搜索联想（全拼/首字母）功能不变。

**预估**：0.5 天

---

### T53 路由 404 与对比候选优化（优先级 13）✅

**问题**：路由无 catch-all，未知路径渲染空白页；Compare 为填充添加框候选全量拉 200 台机型。

**改动**
- 路由补 `/:pathMatch(.*)*` 404 页
- Compare 候选改用 suggest 接口按需搜索

**验收**：未知路径显示 404；对比添加机型可搜索选择且大库不卡。

**预估**：0.5 天

---

### T54 前后端类型契约与 e2e mock 绑定（优先级 14）✅

**问题**：`api/index.ts` 几乎全 `any`；`e2e/mock-api.ts` 手写 VO 靠人肉对齐后端，且未匹配路径兜底 `ok(null)`——接口变更时前端编译期与 e2e 均不报警。

**改动**
- 建立前后端共享 VO 类型（前端 `types/api.ts` 起步，或后端 OpenAPI 导出生成）
- `mock-api.ts` 以类型约束实现，未匹配路径从静默 `ok(null)` 改为抛错
- `api/index.ts` 逐步补齐返回类型

**验收**：后端改字段名时 `typecheck` 报错；e2e 遇未 mock 接口直接失败而非静默通过。

**预估**：2 天

---

## P10 · 采集体验批次（2026-10-24 新增）

### T56 后台列表时间格式化 ✅

**问题**：操作日志 / 抓取任务 / 待审核三个后台列表的「时间」列直接渲染后端 ISO 字符串（`2026-10-24T12:34:56`）。

**改动**：三处统一用 `formatTime` 显示 `YYYY-MM-DD HH:mm:ss`。

### T57 AI 配置模型筛选与连通性测试 ✅

**改动**
- 拉取模型弹窗新增关键字搜索（全选只作用于筛选结果，不吞掉已选项）
- 新增 `POST /api/admin/ai-configs/{id}/test?model=`：向 `{baseUrl}/chat/completions` 发最小对话请求，返回延迟 / 回复 / 错误详情；拉取列表每行可测，避免「拉到了列表、导入后调用才报错」
- 测试动作写操作日志（TEST_AI_CONFIG）

### T58 系列机型发现 + 草稿档案闭环 ✅

**决策**（2026-10-24 用户拍板）：发现结果建草稿机型档案；机型页行级 + 批量抓取一起做；系列发现品牌必选。

**流程**：抓取任务页提交「系列发现」（品牌必选）→ Agent 搜系列机型清单 → 任务详情勾选创建草稿机型（status=0，前台不可见）→ 机型管理页行级「抓取详情」或勾选批量发起现有单机型抓取 → 待审核通过后覆盖更新并自动上架（复用既有 approve 覆盖逻辑，审核代码零改动）。

**改动**
- `agent_task` 新增 `task_type` 列（DEVICE_DETAIL / SERIES_DISCOVERY，存量默认前者），`SqliteSchemaMigration` 老库补列
- `CollectService` 按任务类型分流；系列发现走独立提示词与输出结构（机型名 + 发布日期），不进待审核库
- 新增 `GET /agent-tasks/{id}/discovery`（清单 + 库中同名标注）与 `POST /agent-tasks/{id}/create-devices`（批量建草稿，已存在跳过、幂等）
- 抓取任务页：新建卡片 Tabs 切换两种模式；详情面板展示发现清单（已存在机型标注且不可勾选）
- 机型管理页：新增勾选列与「抓取选中」批量按钮、行级「抓取详情」按钮

**遗留**：系列发现的 Agent 输出质量依赖提示词，首跑建议用小系列（如 iPhone 17）验证清单准确度。

### T59 机型页抓取弹窗可选供应商与模型 ✅

**问题**：机型管理页的行级「抓取详情」与批量抓取直接用默认模型提交，无法像抓取任务页那样指定供应商连接 / 模型。

**改动**：两类入口统一改为先弹确认窗——展示目标机型清单，Combobox 选择 AI 模型（选项为「供应商连接 / 模型名」，默认选中默认模型），确认后逐台顺序提交。

### T60 机型批量上架 / 下架 ✅

**改动**：新增 `PUT /api/admin/devices/batch-status`（只改 status 与更新时间，不走 save 全量覆盖，避免误清参数与版本；写 BATCH_ONLINE_DEVICE / BATCH_OFFLINE_DEVICE 操作日志）；机型管理页勾选后工具栏出现「上架 / 下架」按钮，系列发现的草稿机型可批量上架。

### T61 采集过程日志线程修复 + 超时诊断 + 重试换模型 ✅

**问题**（生产反馈）：任务超时 180s 但任务日志只有起止两行，看不到原因；重试只能沿用原模型。

**根因**：`TaskLogContext` 是普通 ThreadLocal，绑定在 worker 线程；工具方法实际由 Agent 运行时调度到 Reactor boundedElastic 线程，取不到上下文，**全部工具过程日志被静默丢弃**。task 7 的超时则是模型 API（openrouter）180 秒无响应，首轮调用都未返回。

**改动**
- `TaskLogContext` 增加静态兜底值（消费单线程串行，任一时刻只有一个任务在执行，静态值安全；多线程消费时须改显式传参），工具日志恢复写入
- 超时错误信息附带初判：无过程日志 → 疑似模型 API 无响应、建议换模型；有 → 提示条数与查看入口
- `POST /agent-tasks/{id}/retry?aiModelId=` 支持覆盖模型；前端重试改为弹窗选择（默认沿用原模型）

**遗留**：403 反爬工具层硬化已随 T62 落地；单次模型请求超时（`MODEL_TIMEOUT_SECONDS=90`）已接入 OkHttpTransport 配置，但冒烟中挂死端点场景未能观察到快速失败（疑似框架内部重试或该路径未走 transport 配置），任务级超时兜底已验证可靠，此点列为已知限制继续观察。

---

### T62 采集时间预算与反爬韧性 ✅（2026-10-24 用户批准）

**背景**：task 7 实测——慢模型（openrouter-random）每轮往返 8~13s，同义搜索 7 次、同 URL 抓取 3 次烧掉 40~60s，180s 超时颗粒无收；另有知乎 403 类站点反复无效尝试。

**改动**
- 新增 `CrawlSession`（任务级会话）：剩余预算、工具结果去重缓存（同 query / 同 URL 直接命中）、失败域名集合
- `CollectTools`：预算闸门（剩余 < 25s 时工具拒绝执行，返回交卷指令逼模型立即输出）；`fetch_page` 补 Referer / Accept / Accept-Language 请求头；401/403 定性拒绝并熔断域名（本任务内跳过该域名其余 URL）
- `CollectService`：会话随任务 begin/end；LLM 传输层超时（connect 10s / response 90s，`MODEL_TIMEOUT_SECONDS`）；任务摘要带 `failedSources`（被熔断域名），审核端可见
- persona（`resources/AGENTS.md`）新增第 6~9 条：同信息最多 2 组关键词、勿重复抓取、403 换源不重试、收到交卷指令立即输出
- 任务超时默认 180s → 300s（`TASK_TIMEOUT_SECONDS`）
- 任务摘要解析输出失败来源域名行

**验证**：编译 + 28 测试全绿；冒烟——挂死端点任务 300.286s 准时失败，错误信息精准标注「期间无任何工具调用记录，疑似模型 API 无响应，建议重试时更换模型」。

**遗留**：预算闸门 / 缓存 / 熔断的真实效果需生产任务观察（冒烟无法驱动真实模型走完整链路）；`MODEL_TIMEOUT_SECONDS` 在挂死场景未见快速失败，列为已知限制。

---

### T63 采集搜索源可配置（Tavily / SearXNG / Serper）✅（2026-10-24 用户批准）

**决策**：Serper 一并搬进后台（env 作默认值）；SearXNG 自建、Token 非必选；单选引擎 + 失败自动回落；Tavily 必做。

**改动**
- 新表 `agent_search_config`（单行）：provider（system/tavily/searxng）+ 各引擎 Key / 地址 / Token，后台改完下个任务热生效
- 新 `WebSearchService`：四引擎统一入口（Tavily POST / SearXNG `format=json`+token 查询参数 / Serper / DuckDuckGo jsoup），所选引擎失败自动回落 DDG，DDG 也失败返回空结果不抛异常；Tavily 与 SearXNG 返回 snippet
- `CollectTools.webSearch` 改为委托新服务（工具层只保留预算闸门 / 缓存 / 任务日志语义），任务日志带实际引擎名
- 新接口 `GET/PUT /api/admin/search-config`（Key 留空不改、回显只带已配置标记）与 `POST /search-config/test`（真实搜一次返回引擎/条数/延迟）；写 SAVE_SEARCH_CONFIG / TEST_SEARCH_CONFIG 操作日志
- AI 配置页顶部新增「采集搜索源」卡片：引擎徽标与配置摘要、编辑弹窗（按引擎显隐字段）、一键测试

**验证**：编译 + 28 测试、typecheck/build/e2e 11 全绿；冒烟——默认配置返回 system；searxng 坏地址保存后测试，日志出现「搜索引擎 searxng 失败，回落 DuckDuckGo」证明路由与回落链路；tavily 缺 Key / 非法 provider 均被 400 拦截。

**遗留**：Tavily / SearXNG 真实可用性待生产配置后用「测试」按钮验证（本机无法连通外网搜索引擎）。

---

### T64 搜索源多引擎 + 重试弹窗修复（2026-10-24 用户反馈五项）✅

**问题**（生产反馈）：①失败任务点重试无反应；②模型选择应放进重试弹窗；③④希望多引擎配置、一个不可用自动切换；⑤想看 Tavily 剩余次数。

**根因（①）**：重试弹窗用了 `Dialog`/`Label` 组件但未在 AgentTasks.vue 导入（本项目无组件自动导入），弹窗整块静默渲染失败——typecheck 与 e2e 均未覆盖该路径，浏览器级验证此前缺失。

**改动**
- 修复：AgentTasks.vue 补导入；本轮起所有后台弹窗改动用真实浏览器走查
- 多引擎：`provider` 列语义改为引擎 CSV（存量单值自然兼容，`system` 视为未启用）；`WebSearchService` 按 Tavily → SearXNG → Serper 依次尝试第一个可用引擎，全部失败回落 DuckDuckGo；弹窗改为三引擎勾选卡，按勾选显隐凭据字段
- 测试增强：`POST /search-config/test` 可传未保存的表单值（Key 留空沿用已存值），弹窗内新增「测试」按钮（无需先保存）
- Tavily 用量：测试时调用 `GET api.tavily.com/usage`（限 10 次/10 分钟，仅测试场景调用），返回「计划：已用 X / Y 积分；本 Key：…」附在测试结果里

**验证**：编译 + 28 测试、typecheck/build/e2e 11 全绿；**真实浏览器走查**——登录→改密→提交任务→失败→点重试弹窗正常弹出且含模型 Combobox；AI 配置页搜索源弹窗三引擎勾选、勾选 Tavily 后 Key 字段出现、弹窗内测试（缺 Key）被 400 拦截并 toast 提示。

**CI 事故与教训（2026-10-24）**：T64 首推 CI 失败——save() 操作日志仍调已删除的 `SearchConfigVO.getProvider()`；本地 `mvn package` 增量编译用旧 .class 兜住符号未报错，CI 干净构建暴露。已修复并改用 `mvn clean package` 本地对齐 CI 验证（后续一律 clean 构建）。

### T65 机型列表「抓取待审」徽标（2026-10-24 用户反馈）✅

**问题**：机型抓取完成进入待审核后，机型管理列表毫无提示，管理员需在两个页面间人工对机型名。

**改动**
- 机型分页接口附带 `pendingReviewCount`（`Device` 实体 `@TableField(exist=false)` 瞬态字段，`AgentTaskService.pendingCountByDevice` 按「同品牌同名 + PENDING_REVIEW」批量标注）
- 机型管理列表状态列新增「抓取待审 N」徽标，点击直达待审核页并按机型名筛好
- 待审核列表新增 keyword（机型名/品牌名模糊）筛选，支持从 URL `?keyword=` 初始化

**验证**：`mvn clean package` + typecheck/build/e2e 11 全绿；接口冒烟——插入 2 条同机型待审核 + 1 条已丢弃后，机型列表计数=2、`keyword+status` 组合筛选命中 2 条、无关注键词 0 条。

### T66 审核页全量展示 + 菜单频率排序（2026-10-24 用户反馈）✅

**反馈**：①审核页应进来直接看到所有数据，菜单「待审核数据」名不副实；②机型管理是否有分页（答：已有，每页 10 + Pagination 组件）；③左侧菜单按使用频率排序。

**改动**
- 「待审核数据」菜单与页面定位更名为「**抓取审核**」；默认状态筛选从「待审核」改为「**全部状态**」（徽标跳转场景由 keyword 精准定位，不受影响）
- 左侧菜单按使用频率重排：机型管理 → 抓取任务 → 抓取审核 → AI 配置 → 品牌/系列/参数（数据口径）→ 纠错反馈 → 账号权限 → 操作日志 → 系统运维

### T67 系列 AI 推荐 + 机型按系列筛选与批量归类（2026-10-24 用户需求）✅

**改动**
- 新 `GET /api/admin/series/suggest?brandId=`：复用 `AiChatService` 单轮对话生成品牌产品线候选（4~8 个，已存在的同名系列标注 existing 由前端禁选）；JSON 容错解析，AI 异常时业务提示降级手动新建
- 系列管理页「AI 推荐系列」弹窗：选品牌 → 生成 → 勾选（默认全选可创建项）→ 逐个走既有 save 校验批量创建，排序号顺延
- 机型分页接口新增 `seriesId` 筛选，`seriesId=0` 约定为「未归类」；机型管理页品牌旁新增系列下拉（跟随品牌联动清空，含「未归类」）
- 机型管理页勾选多台 →「加入系列」：跨品牌勾选拦截，弹窗选目标系列走既有 assign 接口（同品牌校验在后端兜底）

**验证**：`mvn clean package` + typecheck/build/e2e 11 全绿；接口冒烟——按系列筛选命中 4 台、未归类筛选 0 台、批量归类 2 台后未归类归零；AI 推荐无配置时按预期报 400（真实推荐效果待生产配置模型后验证）。

---

## P11 · 机型库 × 参数历史/世代回顾 连接（2026-10-24 完成）

> 背景：参数历史、世代回顾与机型库割裂——详情接口不返回系列信息、全站唯一互链是回顾→详情、直接挂入口会因库稀疏（部分系列仅 1 台）露怯。
> 确认方案：详情页防御式互链（有数据才点亮）+ 系列完善度补数闭环，分两批：T68–T70/T72「连接」，T71「增长」。

| 任务 | 状态 | 落地要点 |
| --- | --- | --- |
| T68 详情接口返回系列信息 | ✅ | `detail` 返回 `series`（未归类/停用系列为 null）与 `seriesDevices`（同系列已上架机型，发布时间升序含起售价，复用 `EvolutionDeviceVO`）；`ParamMetaVO` 增 `paramDefId`；系列 save/delete/assign/merge 的缓存失效补 `DEVICE_DETAIL`；顺带统一公开口径——`EvolutionService.devicesOf` 与公开系列列表只统计已上架机型，与详情系列条一致 |
| T69 详情页系列条 + 回顾入口 | ✅ | 详情页新增「所属系列」卡：系列徽章 + 同系列机型横向条（当前机禁用高亮、可点互跳）；≥2 台显示「世代回顾」按钮，仅 1 台显示「已收录 N 代 · 数据持续收录中」 |
| T70 参数行「参数历史」入口 | ✅ | 值以数字开头的参数行显示历史图标 → `/history?brandId=&paramDefId=`（History.vue 已兼容单数 brandId，零后端改动）；芯片名/IP68 等文本参数不显示 |
| T71 系列完善度看板 + 一键补抓 | ✅ | `SeriesVO` 增 `paramDeviceCount`（后台口径含草稿，`listOverview` 增 `publishedOnly` 参数区分前后台）；系列管理页「机型数」列升级为「完善度 带参 N/M」+ 缺参徽标；行级「补抓缺参机型」弹窗（新 `GET /admin/series/{id}/devices-without-params`，含草稿，复用机型页选模型模式逐台提交）、「系列发现」直达抓取任务页并预填 `mode/brand` |
| T72 稀疏空态叙事 | ✅ | 回顾页可见世代 <2 时图表区显示收录进度文案替代单点图，管理员登录可见「去后台补抓」跳抓取任务页；历史页单点时标注「已收录 1 款 · 数据持续收录中」 |

**验证（2026-10-24）**：`mvn clean package`（含 28 单测）+ typecheck/build/e2e 11 全绿；接口冒烟——详情返回 series/seriesDevices/paramDefId，完善度 4/4·1/1·1/1，造缺参草稿机后 1/2 且缺参清单命中、删除还原；真实浏览器走查——详情系列条与两个入口跳转、历史页自动选中、回顾页稀疏空态访客/管理员两态（登出前后 CTA 显隐正确）、完善度列与补抓弹窗（草稿目标 + 默认模型）、系列发现预填，全部符合预期。

### T73 连接链路 e2e ✅（2026-10-24 完成）

**改动**
- mock 详情接口带 `series` / `seriesDevices` / `paramDefId`，机型 POST 尊重 `status`（可造草稿）；系列列表带 `paramDeviceCount`，新增缺参机型接口
- 新 `e2e/series-connection.spec.ts` 三条用例：访客详情系列条/回顾入口/参数历史跳转（含文本参数无入口、单机系列无入口）、稀疏空态访客与管理员两态 CTA、后台完善度看板 + 补抓弹窗 + 系列发现预填

**验证**：e2e 14 条全绿、typecheck 通过。

---

## P12 · 机型库系列总览（2026-10-24 完成）

> 目标：机型库从平面列表升级为「列表 / 系列总览」双视图，访客在机型库内直接看到各产品线的收录规模与趋势缩略，把「品牌 → 系列 → 世代回顾」的动线串起来。
> 已定决策：总览做在机型库页内 Tab（不新增路由）；趋势用轻量 SVG sparkline 而非 ECharts（避免机型库引入 200KB 图表依赖，违背 T51/T52 的瘦身方向）；趋势参数固定「屏幕尺寸 + 起售价」两条（卡片空间有限，其余参数进世代回顾看）。

| 任务 | 状态 | 落地要点 |
| --- | --- | --- |
| T74 系列总览聚合接口 | ✅ | `GET /api/public/series/overview?brandId=`：系列项（公开口径只计已上架）+ 最新一代代表机型 + 屏幕尺寸/起售价两条迷你趋势（`SeriesTrendPointVO`，换算后数值按年对齐，无日期或非数值跳过）+ 未归类已上架机型数；缓存随 `SERIES_LIST` 失效；`EvolutionServiceTest` 增 2 用例（共 30 个单测全绿） |
| T75 机型库「系列总览」Tab | ✅ | 机型库页 Tabs 双视图，URL 同步 `view=series`（深链直达已修：初始化时显式加载总览）；系列卡片网格（桌面 2 列 / 移动端 1 列）：代表机型缩略图、共 N 代 · 年份范围、最新一代、两条 `TrendSparkline`（内联 SVG polyline，单点退化短横线，title 提示首尾数值）、点击直达世代回顾；品牌筛选联动重拉总览 |
| T76 未归类与空态 + e2e | ✅ | 总览底部「未归类机型 N 台」虚线卡（点击切回列表视图，计数随品牌筛选）；品牌无系列时空态引导；mock 补 overview 接口；新增 `e2e/series-overview.spec.ts`（Tab 切换与 URL 同步、卡片数据与跳转、sparkline 数量、未归类卡回切列表） |

**验证（2026-10-24）**：`mvn clean package`（30 单测）+ typecheck/build/e2e 15 全绿；接口冒烟——三个系列返回规模/年份/最新代/趋势点，小米 14 屏幕尺寸换算 6.36、Mate 起售价 6499 均正确；真实浏览器走查——深链直达、Tab 切换 URL 同步、卡片跳回顾、品牌筛选联动、移动端 390px 单列截图确认。

---

### T68 详情接口返回系列信息（后端地基）

**改动**
- `DeviceService.detail` 返回 `series`（id/name，未归类为 null）与 `seriesDevices`（同系列已上架机型 id/name/thumbnail/releaseDate，按发布时间升序，含本机）
- `ParamMetaVO` 增加 `paramDefId`（T70 参数行历史链接需要）

**验收**：iPhone 13 详情返回系列与同系列机型；未归类机型 series=null；`mvn clean package` / typecheck / e2e 全绿。

**预估**：0.5 天

---

### T69 详情页系列条 + 世代回顾入口

**改动**
- 详情页新增「所属系列」卡：系列徽章 + 同系列机型横向条（缩略图+年份，当前机高亮可点）
- 同系列 ≥2 台时显示「世代回顾」按钮 → `/evolution?seriesId=`；仅 1 台时显示「已收录 1 代 · 数据持续收录中」，不放入口

**验收**：iPhone 13 详情有入口且跳转直接出图；Mate 60 Pro 只有系列条没有入口；移动端单列正常。

**预估**：0.5–1 天

---

### T70 参数行「参数历史」入口

**改动**
- 数值型参数行（值以数字开头）加「历史」入口 → `/history?brandId={brandId}&paramDefId={paramDefId}`，后端零改动（History.vue 已兼容 `brandId` 单数参数）

**验收**：屏幕尺寸行点击后历史页选中品牌+参数并出图；纯文本参数（芯片名等）不显示入口。

**预估**：0.5 天

---

### T71 系列完善度看板 + 一键补抓（后台闭环）

**改动**
- admin 系列列表返回 `paramDeviceCount`（系列内带参数值的已上架机型数）
- 系列管理页每行显示「带参 N/M」完善度；行级「补抓缺参机型」——对无参数机型复用 T59 选模型弹窗批量发起抓取；「系列发现」跳转抓取任务页并预填品牌
- 完善度计数随审核通过的缓存失效即时刷新

**验收**：数字与库内一致；一键补抓生成任务；审核通过后完善度上升。

**预估**：1–1.5 天

---

### T72 稀疏空态叙事

**改动**
- 世代回顾：可见世代 <2 时图表区显示「已收录 N 代 · 数据持续收录中」替代单点图，管理员登录可见「去补抓」按钮；世代横条保留可跳详情
- 参数历史：仅 1 个数据点时标注「已收录 1 款」

**验收**：单机系列回顾页不再是单点图；管理员可见 CTA；e2e 全绿。

**预估**：0.5 天

---

### T77 登录页凭据移除 + iPhone 全系列目录整理（2026-10-24 生产验证批次）✅

**改动**
- P0：登录页不再预填 admin/admin123、删除「默认账号」提示文案——凭据写在登录页代码里等于向访客公开初始口令（生产走查发现）
- iPhone 全系列目录：新增「iPhone SE 系列」（SE/SE 2/SE 3），iPhone 数字系列补齐 2007-2025 全部 40 台缺失机型（草稿态、待用户抓取参数），全库未归类清零；2026 新一代（iPhone 18）留给「系列发现」实跑确认后再建

**验证**：生产接口确认——数字系列 48 台（带参 8/48）、SE 系列 3 台、未归类 0；e2e 登录用例显式填凭据不受影响。

---

## P13 · 系列趋势页重构（2026-10-25 完成）

> 背景：参数历史（选品牌看单参数走势）与世代回顾（选系列看多参数）是同一件事的两个半成品视角，控件区重（4 下拉 + checkbox 云 + 起止世代裁剪），导航里难分彼此。已定决策：主语从「品牌」改为「系列」，两页合并为 `/trends` 单页双模式，形态采用「参数卡网格 + 聚焦主图」；「选品牌看趋势」概念与世代区间裁剪废弃。

| 任务 | 状态 | 说明 |
| --- | --- | --- |
| T78 同代横比矩阵接口 | ✅ | `GET /api/public/series/generation?brandId=&year=&paramDefIds=`：某品牌某年各兄弟系列的代表机型（同年多款取发布最晚，无新机系列 device=null 占位，未归类机型不参与）+ 参数单点值；复用 `EvolutionService` 的价格/参数值提取，`launch_price` 特判取变体最低价；新增 `GenerationMatrixVO/GenerationSeriesVO/GenerationParamVO`，单测覆盖同年取晚、占位与价格口径 |
| T79 系列趋势页（跨代 + 同代） | ✅ | 新增 `/trends`：品牌 + 页内 Tab；跨代 = 系列 chips → 世代缩略图条 + 参数卡网格（`ValueSparkline` 内联 SVG 迷你趋势 + 涨跌徽标，chips 增换上限 8）+ 点卡聚焦 ECharts 大图 + AI 回顾 + 社媒/截图导出；同代 = 年份 chips → 兄弟系列并排卡（中性「最高/最低」标注）+ 一键全部加入对比页。URL 同步 mode/brandId/seriesId/year/paramDefIds/paramDefId |
| T80 旧页面与入口收编 | ✅ | 删除 History.vue / Evolution.vue；`/history` `/evolution` 路由重定向（evolution 保留 seriesId 映射）；导航两项合一；首页/机型库系列卡/详情页系列条与参数行入口全部改指 `/trends`（参数行入口带 paramDefId 直达聚焦）；后端 `/public/history` 接口保留不动（API 兼容），sitemap 输出改 `/trends`，Security 白名单加 `/trends` 并保留旧路径放行 |
| T81 e2e 与文档同步 | ✅ | `series-evolution.spec.ts` 重写为 `series-trends.spec.ts`（跨代聚焦/参数上限/同代横比/社媒导出/移动端双列），连接与总览用例改断言新 URL；mock 新增 generation、补 Apple 兄弟系列与文本参数；README 路由/功能段/SEO/e2e 描述同步 |
| T82 导出改为模板卡海报 | ✅ | 「截图导出」（html2canvas 截网页 DOM，产物随窗口尺寸与主题变化）下线；社媒导出弹窗新增**聚焦版**版式：1080×1440 固定画布、亮色、单参数 ECharts 大图（字号线宽按海报比例放大、极值标注）+ 首末值摘要 + 涨跌结论，自动带入趋势页当前聚焦参数并可在弹窗内改选；页面导出入口收敛为「社媒导出」一个 |

**验证**：`mvn test` 42 例全绿（新增同代矩阵与安全白名单断言）；`npm run typecheck` / `build` 通过；`npm run test:e2e` 19 例全绿。

---

## P14 · 数据增长与运营闭环（2026-10-25 用户确认 T83–T90 全部纳入，当日完成）

> 来源：T1–T82 全量交付后的业务复盘。核心判断：功能骨架已完整，短板在「数据从哪来、往哪去」两端——增长靠手动推、审核是人工瓶颈（数字系列 48 台仅 8 台带参），访客侧无任何行为数据回流，运营决策盲飞。
> 本批铁律不变：抓取结果必须人工审核，任何提效手段都不做「自动通过」。
> T90 附加约束（用户确认）：图片链路不得影响采集 / 审核主流程——任何环节失败只降级，绝不拖垮参数入库与任务执行。
> 落地记录：T83 `9a26b54` / T84 `7459fda` / T85 `b0b57c4` / T86 `0ea4270` / T87 `96cafe6` / T88 `2ed0297` / T89 `10f8a34` / T90 `1411773`；每项过编译 + 42 单测、typecheck / build、e2e 全绿（批次结束时 25 例）并做真实后端接口冒烟，明细见各任务「已完成」段。
> 执行顺序：T83 / T84 并行起步 → T85 依赖 T84 的 PV 数据 → T88 依赖 T86 的审核提效，其余互相独立。

| 任务 | 名称 | 优先级 | 状态 | 一句话 |
| --- | --- | --- | --- | --- |
| T83 | 反馈闭环补全 | P0 | ✅ | 管理员未读徽标 + 访客反馈编号查询，接通两处断裂 |
| T84 | 访客行为统计 | P0 | ✅ | PV 按天聚合表 + 热门口径修正，终结运营盲飞 |
| T85 | 数据健康度看板 | P1 | ✅ | 全库覆盖率矩阵 + 缺参排行 + 一键补抓 |
| T86 | AI 预审打标与审核排序 | P1 | ✅ | 可疑置顶、批量通过加护栏，人力花在刀刃 |
| T87 | 参数陈旧度与系列重抓 | P2 | ✅ | updated_at 消费起来，时效性可见 |
| T88 | 系列订阅抓取 | P2 | ✅ | 指定系列周期自动发现，发布季不掉队 |
| T89 | SEO 与分发中级补强 | P2 | ✅ | JSON-LD / 缓存头 / RSS / PWA |
| T90 | 机型图片采集落库 | P3 | ✅ | Agent 产出图片 URL，异步下载落库；与主流程完全解耦，失败只降级 |

---

### T83 反馈闭环补全（P0 · 推荐首选）

**已完成（2026-10-25）**：后端新增 `GET /api/admin/badges`（`BadgeVO`，当前仅 `openFeedback`，后续健康度指标可扩）与 `GET /api/public/feedback/{id}`（`FeedbackStatusVO` 只暴露状态与时间，不含提交内容；404 语义为「反馈不存在，请核对编号」）；前端后台侧栏「纠错反馈」菜单挂待处理数徽标（桌面 + 移动 Sheet 两处，进入后台与路由切换时刷新，拉取失败静默降级）；详情页纠错弹窗改三态——提交成功展示编号回执、可一键带编号查进度、表单内「已有编号？」入口，状态以 Badge 呈现（待处理 / 已处理 / 已忽略）；mock 补齐三接口且 POST 真实入列返回递增编号；e2e 新增闭环用例（提交→回执→查询→后台徽标 2→逐条处理→徽标归零→再查已处理）。
**验证**：`mvn clean package`（42 单测）、typecheck / build、e2e 20 例全绿；真实后端接口冒烟——提交返回 #1、公开查询 OPEN、未知编号 400、badges 1→处理→0、再查 RESOLVED。
**遗留**：详情页参数行「N 条待核实」聚合展示（可选增强）未做，后续视需要并入 T84/T85。

**问题**（代码已证实两处断裂）：

1. `feedback` 只有 OPEN/RESOLVED/IGNORED 三态（`FeedbackService.java`），管理员侧无未读提醒——访客提交后没人知道有新反馈；
2. 访客提交后仅有 toast「反馈已提交」，`PublicFeedbackController` 只有 POST，无任何查询处理结果的接口。

**改动**

- 管理员侧：侧栏「纠错反馈」菜单加 OPEN 数徽标，复用抓取待审 `pendingReviewCount` 的批量统计模式；侧栏需要一个轻量汇总接口（如 `GET /api/admin/badges`）
- 访客侧：提交成功弹窗展示「反馈编号 #N」（接口已返回 id，纯前端改动）；新增 `GET /api/public/feedback/{id}`（只返回状态与处理时间，不暴露内容与他条记录）；详情页参数行可选展示「N 条待核实」（同机型 OPEN 数聚合）
- 不引入注册/登录依赖，编号即凭据

**验收**：访客提交后能凭编号查询到「待处理 / 已处理」；管理员侧栏出现未读数、处理后归零；既有 1 分钟限流不回归。

**预估**：1 天

**推荐理由**：纠错反馈是全站唯一的免费数据质量众包来源，断链等于白收；两端改动都极小（徽标复用既有模式、查询只读一条），是本批性价比最高的项。

---

### T84 访客行为统计（P0 · 本批数据地基）

**已完成（2026-10-25）**：新表 `device_view_daily`（device+date 唯一，schema.sql 建表，新表无需迁移）；`DeviceViewDailyMapper` 注解 SQL——SQLite 原子 upsert 累加、近窗口 PV 排序、批量合计、按窗口滚动删除；`DeviceViewService` 收口全部读写且**尽力而为**（写入/统计失败只记 warn 日志并回落，绝不阻塞公开接口）；公开详情与对比接口旁路计数；`/devices/hot` 改近 30 天 PV 倒序，无数据回落发布时间倒序、PV 命中不足 limit 用最新机型补齐；机型管理列表新增「30天PV」列（`Device.pv30d` 瞬态字段，控制器批量标注，公开列表不带）；保留策略纳入聚合表（`SPECTRACE_RETENTION_VIEW_DAILY_DAYS` 默认 90 天），运维页展示同步。
**验证**：`mvn clean package` / typecheck / build / e2e 20 例全绿；接口冒烟——等就绪后详情连击 5 次 pv30d=5（upsert 累加正确）、对比计数命中、hot 按 PV 排序（PV 机型置顶，发布时间口径补齐缺位）、保留概览含 device_view_daily 行数与 viewDailyDays=90。

**问题**：`/devices/hot` 实际按 `release_date` 倒序（`DeviceService.java`），「热门」名不副实；全库无 view_count / 埋点表，浏览、搜索、对比零统计——补录优先级、首页运营位、未来用户体系立项全凭感觉。

**改动**

- 新表 `device_view_daily`（device_id / view_date / pv，device+date 唯一键）：上报点为机型详情、对比发起、系列趋势跳详情；复用 `MemoryRateLimiter` 防刷；写路径做 upsert（`INSERT ON CONFLICT`），必要时内存聚合定时落库防写放大
- `/devices/hot` 改为近 30 天 PV 倒序；无 PV 数据窗口（新库）回落 `release_date` 倒序，行为兼容
- 纳入 `DataRetentionService`：按天聚合天然不膨胀，明细仅保留近 90 天
- 后台机型列表可选展示「近 30 天 PV」列

**验收**：访问详情页后 `device_view_daily` 出现记录且重复访问累加；hot 接口按 PV 排序；新库无数据时行为与现在一致；retention 生效。

**预估**：1–1.5 天

**推荐理由**：一张表同时修正热门口径、支撑 T85 的补录优先级、并为「要不要做用户体系」提供回访率依据——本批所有战略决策的地基。零新依赖（SQLite upsert + 既有限流器）。

---

### T85 数据健康度看板（P1 · 依赖 T84）

**已完成（2026-10-25）**：新 `GET /api/admin/data-health`（`DataHealthVO` + 覆盖/缺参两个子 VO）：全库带参率（含草稿口径）、各启用参数的有值机型数排行（升序最缺在前，带分组名）、缺参机型排行（缺参 = 有值参数数 < 启用参数数，按近 30 天 PV 倒序、无 PV 按发布时间，上限 50，品牌/系列可筛且 seriesId=0 为未归类）、近 30 天审核通过数与待处理反馈数；统计 SQL 收在 `DeviceParamValueMapper`（COUNT DISTINCT 三连）；后台新增「数据健康度」页（侧栏排在系列管理后）：四张指标卡、参数覆盖进度条网格、缺参排行表 + 行级/批量补抓（复用选模型弹窗模式，逐台顺序提交）。
**验证**：`mvn clean package` / typecheck / build / e2e 21 例全绿（新增健康度看板与补抓用例）；接口冒烟——种子库带参率 1.0（6/6）、46 项启用参数、覆盖升序（机身尺寸/5G支持/后置摄像头为 0 在前）、缺参排行命中「有值参数 < 启用参数」口径。

**问题**：只有系列级 `paramDeviceCount`（T71），无全库视图——哪个参数最缺、哪个品牌最薄、该先补哪台，没有数据依据。数字系列 48 台仅 8 台带参暴露的就是这个问题。

**改动**

- `GET /api/admin/data-health`（仅管理员）：全库带参率、各 `param_def` 覆盖机型数排行（升序找最缺）、缺参机型排行（可按品牌/系列筛选，按「近 30 天 PV × 缺参」排序——依赖 T84）、近 30 天审核通过量、OPEN 反馈数
- 后台新增「数据健康度」页（复用 `usePagedList` 与既有卡片风格）；缺参机型行级「补抓」复用 T59 选模型弹窗
- 结果走 Caffeine 缓存，管理端写路径失效

**验收**：带参数与库内一致；缺参排行命中造数场景；一键补抓生成任务；非管理员无权访问。

**预估**：1.5 天

**推荐理由**：把 T71 的「点」（单系列完善度）升成「面」（全库覆盖率），并第一次用真实 PV 给补录排优先级——让有限的审核人力投到访客真的在看的地方。

---

### T86 AI 预审打标与审核队列排序（P1）

**已完成（2026-10-25）**：后端 `riskLevelOf` 纯本地评估（不发外部请求）——低置信度（<0.6）或单位冲突判 HIGH、与库中现有值有差异判 MEDIUM、全部参数 ≥0.9 且无冲突判 PASS、其余 NORMAL；`pagePending` 在 status=PENDING_REVIEW 时整体载入内存按风险置顶排序（HIGH→MEDIUM→NORMAL→PASS，同级按创建时间倒序）后分页，其他状态保持时间倒序；`PendingDataVO` 带 riskLevel。前端审核列表新增「预审」徽标列（建议核实/值有变更/可信）；批量通过含可疑项时先弹护栏确认（N 项建议人工核实）再进既有批量确认；审核弹窗新增「建议核实 N 项 / 建议直接通过 M 项」分组徽标。
**验证**：编译 + 42 单测、typecheck / build / e2e 22 例全绿（新增预审徽标与批量护栏用例）；接口冒烟——PENDING_REVIEW 筛选返回 riskLevel=NORMAL（种子行无置信度数据，符合口径）、全部状态不计算。

**问题**：审核是数据规模化的瓶颈；confidence（T21）已落库但只展示不参与流转，管理员逐条平铺审，低置信度、单位冲突、与旧值冲突项没有优先级。

**改动**

- 审核列表默认排序改「风险优先」：低置信度（<0.6）> 单位冲突（`paramWarnings`）> 与旧值冲突 > 普通
- 前端先行（零后端改动）：待审核详情已有置信度、警告、diff 数据，弹窗加「建议核实 N 项 / 建议直接通过 M 项」分组徽标
- 批量通过加护栏：勾选含低置信度或冲突项时弹确认，提示「N 项建议人工核实」
- 可选后端增强：`agent_pending_data` 写入时把「全部参数 ≥0.9 且无警告」标为 `aiVerdict=PASS`，列表可一键筛「可信项」

**验收**：低置信度记录置顶；批量通过含可疑项出现确认弹窗；「可信项」筛选命中；不改变必须人工审核的铁律。

**预估**：0.5–1 天

**推荐理由**：在「人工审核是产品差异（可信、有来源）」定位下提效的唯一正道——让审核员注意力按风险分配而非按提交顺序；先做纯前端分组（数据都在），后端 verdict 是增强项。

---

### T87 参数陈旧度标记与系列重抓（P2）

**已完成（2026-10-25）**：新 `GET /api/admin/series/{id}/stale-devices?days=180`——系列内无参数或参数最近更新早于 N 天前的机型（含草稿），`DeviceParamValueMapper` 增 MAX(updated_at) 批量查询，时间解析失败按陈旧处理（宁多抓不漏抓）；系列管理行级新增「重抓全系列」按钮（复用补抓弹窗，模式区分 missing/stale，空态文案随之切换）；详情页参数行「更新于」与页头「最后更新」超过 180 天显示「可能过期」琥珀色提示（纯前端常量 `STALE_DAYS`，无接口变更）。
**验证**：编译 + 42 单测、typecheck / build / e2e 23 例全绿（新增重抓弹窗用例）；接口冒烟——days=180 种子库（参数全新鲜）返回空、days=0 圈定全系列 4 台，语义正确。

**问题**：`device_param_value.updated_at` 已落库（T5）但零消费（全库仅备份、清理两个定时任务）；手机参数会因 OTA、新配色、调价变化，访客与管理员都看不出数据多旧。

**改动**

- 详情页参数行「更新于」超阈值（默认 180 天，可配）追加「可能过期」灰标；机型页头「最后更新」同理；纯展示层比较，不改表结构
- 系列管理行级「重抓全系列」：复用 T59 选模型弹窗对系列内机型批量提交，跳过 N 天内已抓过的机型

**验收**：造一条 `updated_at` 超期数据显示灰标；重抓弹窗列出目标机型且排除近期已抓；审核通过后时间刷新、灰标消失。

**预估**：0.5–1 天

**推荐理由**：时效性是参数站的隐性可信度，「来源 + 更新时间 + 过期提示」三件套才算完整；成本极低（展示层 + 复用批量抓取）。

---

### T88 系列订阅抓取（P2 · 依赖 T86）

**已完成（2026-10-25）**：新表 `agent_subscription`（series 唯一，schema.sql 建表）；`agent_task` 补列 `subscription_id`（`SqliteSchemaMigration` 兼容老库）；`AgentSubscriptionService` 每日 `SPECTRACE_SUBSCRIPTION_CRON`（默认 05:00，排在备份/清理之后）检查到期订阅——到期即以系列名+所属品牌自动创建「系列发现」任务并回填订阅来源，单条失败只记日志，创建失败不更新 lastRunAt 下个检查日重试，系列已删自动停用订阅，Agent 关闭时跳过调度；接口 `GET/PUT /api/admin/series/{id}/subscription`（保存写 SAVE_SUBSCRIPTION 操作日志）；系列管理行级「订阅抓取」弹窗（周期 1/2/3/6/12 个月 + 可选模型 + 启用开关）；抓取任务列表对订阅来源任务显示「订阅」徽标。
**验证**：编译 + 42 单测、typecheck / build / e2e 24 例全绿（新增订阅配置用例）；接口冒烟——PUT/GET 回读一致，把 cron 指到下一分钟实跑：到点自动创建 SERIES_DISCOVERY 任务且 subscriptionId=1、modelName=系列名、agent 正常消费（无模型配置按预期 FAILED）。
**遗留**：订阅以系列名为发现关键词，「iPhone 数字系列」这类命名搜索效果待生产观察；必要时后续给系列加独立「发现关键词」字段。

**问题**：任务全部手动提交（`AdminAgentController`），无任何周期采集；9/10 月新机发布季靠人记得来点按钮。

**改动**

- 新表 `agent_subscription`（series_id / 周期（月）/ 模型 / enabled / last_run_at）；定时任务到期自动创建「系列发现」任务——复用既有队列与审核链路，零新链路
- 系列管理行级「订阅」开关；抓取任务页标注订阅来源；配置数据不纳入 retention 清理

**验收**：周期临时调成「每天」验证自动创建任务；禁用后不再创建；重启后订阅不丢（落库）。

**预估**：1 天

**推荐理由**：把「人记得来抓」变成「系统到点就抓」，发布季不掉队；依赖 T86 先提效审核——否则自动化只是把手动堆料变成自动堆料，审核队列先爆。

---

### T89 SEO 与分发中级补强（P2 · 可拆散零做）

**已完成（2026-10-25）**：①详情页注入 JSON-LD Product schema（品牌/型号/发布日期/起售价 Offer/参数摘要 12 项，`setJsonLd` 随路由复位清除）；②`PublicCacheHeaderFilter` 给公开 GET 加 `Cache-Control: public, max-age=300`（feedback 查询与 POST 豁免，SPA/sitemap 不波及）；③`ShallowEtagHeaderFilter` 注册到 `/api/public/*`，二次请求命中 304；④新 `GET /rss.xml` 近 30 天新收录机型（RFC-822 pubDate、1h 缓存，进安全白名单）；⑤PWA——`manifest.webmanifest` + SVG 图标 + 保守 `sw.js`（静态资产 cache-first、导航 network-first 回落 index.html、**/api 一律不缓存**），仅生产构建注册。
**验证**：编译 + 42 单测、typecheck / build / e2e 24 例全绿；接口冒烟——RSS 输出合法 XML 条目、公开接口 300 + ETag 命中 304、feedback/POST/首页不误伤缓存头（顺带修掉过滤器初版误罩 SPA 的 bug）。

**问题**：详情页动态 OG/meta 已有（`meta.ts`），但无 JSON-LD 结构化数据；公开 GET 无 `Cache-Control` / `ETag`（仅 sitemap / robots 有）；无 RSS；无 PWA。

**改动**

- 详情页注入 JSON-LD Product（品牌 / 型号 / 参数摘录 / 起售价）
- 公开 GET 补 `Cache-Control: public, max-age=300`（与 Caffeine TTL 对齐）；详情接口补 ETag（按参数最新 `updated_at` 指纹）
- `GET /rss.xml`：近 30 天新收录机型（复用 sitemap 的公开口径与域名解析逻辑）
- PWA：`manifest.json` + 轻量 SW 壳（静态资源 runtime 缓存），不做复杂离线策略

**验收**：Rich Results Test 通过 Product 校验；二次请求命中 304；RSS 可订阅；Lighthouse 判定可安装。

**预估**：1–1.5 天

**推荐理由**：全是低成本稳定复利项——结构化数据直接影响搜索富结果展现，缓存头顺手补齐 HTTP 层（Caffeine 已有进程缓存），RSS 给「持续收录」一个免费回访入口。

---

### T90 机型图片采集落库（P3）

**已完成（2026-10-25）**：persona 输出 JSON 增加可选 `imageUrl`（官方产品图直链，省略不猜）；采集回写收口保留键 `_imageUrl`；`device` 表补列 `thumbnail_pending_url`（`SqliteSchemaMigration` 兼容老库）。**解耦硬约束落地**：审核通过事务内零网络请求，只对待下载机型写 URL（已有真实上传图——非 SVG 占位——不覆盖），事务提交后由独立单线程 `DeviceImageFetchService` 下载（Jsoup 15s 超时、文件头校验类型、大小上限复用上传配置、浏览器 UA）；失败/超时/403/超限一律清标记保留占位图并写操作日志，不做自动重试，兜底手动上传；启动恢复重扫待下载机型重新入队；审核页弹窗展示采集图预览与版权提示（加载失败自动隐藏）。
**验证**：编译 + 42 单测、typecheck / build / e2e 25 例全绿（新增审核页预览用例）；真实后端冒烟——预置好图/坏图两条待下载记录重启：启动恢复 2 条入队，好图下载成功回填 `/uploads/*.png`、坏图失败后清标记且原占位图完好，全程未阻塞其它链路。

**问题**：采集链路完全不抓图（`CollectTools` 无 image 逻辑），线上靠占位 SVG 与手动上传，视觉是转化硬伤。

**改动**

- persona 输出 JSON 增加可选 `imageUrl`（官方产品图 URL）；字段解析失败不影响待审核写入
- **解耦设计（硬约束）**：审核通过的事务内零网络请求，只落库图片 URL 与「待下载」状态；下载由独立线程池异步执行——校验真实类型与大小（复用上传上限）→ 落 `uploads/devices/` → 回填 `device.thumbnail`
- 下载失败 / 超时 / 403 / 超限一律降级为占位图，仅写操作与任务日志，不自动重试；机型上架与后续采集任务不受任何影响，兜底仍是既有手动上传；版权与准确性由审核人工把关——预览为必看项

**验收**：抓取带图机型 → 审核页见图 → 通过后详情页异步出现真实图；人为制造下载挂死 / 404，审核通过、机型上架与后续采集任务均不受影响；操作日志可查。

**预估**：1 天

**推荐理由**：访客第一眼是图不是参数，占位图对可信度与分享率伤害直接；风险（版权、盗链）通过「审核预览 + 本地化落库」双闸控制，与人工审核铁律一致。

---

### 决策储备（不排期，触发条件明确再立项）

| 方向 | 触发条件 | 触发指标（怎么看） | 说明 |
| --- | --- | --- | --- |
| 用户体系（收藏 / 关注系列 / 新机通知 / 反馈进度跟踪） | 有稳定回访的实证 | PV 周环比连续 4 周为正（`device_view_daily` 按周聚合）；间接信号：纠错反馈留联系方式占比、RSS 订阅数、反馈编号查询次数。**注意**：PV 表只有机型维度没有访客维度，量化「回访」需先加匿名访客标识（localStorage UUID 上报，一行埋点），这是用户体系的最小前置步 | `member_user` + JWT 复用；隐私合规先行（注册协议 / 个人信息收集告知） |
| 自然语言选机 | 参数库足够稠密 | 带参机型 ≥ 300 台 且 核心参数（屏幕/电池/芯片/起售价）覆盖率 ≥ 60%——两项都可在「数据健康度」页直接读 | T19 曾因样本不足否决，重启前先补 `param_def` 语义标签（拍照→传感器/光圈/OIS，性能→芯片/内存/散热） |
| 开放数据 API | 出现真实外部调用需求 | 限流日志里出现规律性外部 UA、429 命中持续增长，或有人主动来谈 | 复用 `SPECTRACE_PUBLIC_QPM` 配额体系 + API Key + 使用条款 |
| SQLite → MySQL/PG | 规模或拓扑越界 | `device_param_value` 行数 > 30 万，或 `VACUUM INTO` 备份耗时明显变长（系统运维页可看），或需要部署第二个实例 | `sql/` 旧脚本已留参考，当前规模不动 |
| 国际化 / 出海 | 出海产品决策（先产品后技术） | 产品决策输入：RSS/PV 的境外流量占比 | 全站硬编码中文，i18n 是重构级工程 |

---

## P15 · 生产观察批（2026-10-25 新增；非开发待办——跑生产、看数据、结论写回本表后再定加码，T97 是唯一开发项随时可做）

> 来源：T83–T90 交付后的遗留项清点。多为**观察 / 验证类**——功能已上线，需要真实生产数据确认效果再决定是否加码；T97 是可直接开工的小增强。与 P14 不同，本批多数任务不是写代码，而是「跑一段生产、看数据、下结论」，结论写回本表。

| 任务 | 名称 | 类型 | 状态 | 一句话 |
| --- | --- | --- | --- | --- |
| T91 | 订阅发现关键词效果验证 | 观察 | ⬜ | 实跑一条订阅，清单质量差则给系列加「发现关键词」字段 |
| T92 | 产品图采集命中率观察 | 观察 | 🔧 | 已定位解析端漏接 imageUrl 并修复，待部署复验
| T93 | AI 预审阈值校准 | 观察 | ⬜ | 看待审队列 HIGH/PASS 占比，必要时调 0.6/0.9 阈值 |
| T94 | 热门口径稳定性观察 | 观察 | ⬜ | 上线两周后核对 hot 排序是否符合直觉、回落逻辑是否频繁触发 |
| T95 | 采集搜索源真实验证（T63 遗留） | 验证 | ✅ | 生产 Tavily 实跑通过（5 条 / 2375ms） |
| T96 | 模型挂死快速失败复核（T61/T62 遗留） | 观察 | ✅ | 已定位为超时预算算错并修复（储备改为模型超时 + 15s，模型超时 90→45），待部署复验 |
| T97 | 详情页参数行「N 条待核实」（T83 遗留） | 开发 | ✅ | 同机型 OPEN 反馈数按参数聚合到参数行，访客可见数据存疑提示 |
| T98 | 发布前真机 / 读屏人工复核（T26/T34 遗留） | 人工 | ⬜ | 每次发布前走查键盘路径与读屏，自动化替代不了 |

### T91 订阅发现关键词效果验证（观察）

**背景**：T88 订阅以系列名（如「iPhone 数字系列」）作为发现关键词，产品线命名带「系列」二字可能干扰搜索引擎命中率。
**动作**：挑 1~2 个系列启用订阅（周期可临时调成最短观察节奏），看任务产出的机型清单是否命中该系列真实新机。
**结论出口**：命中良好→维持现状；清单离谱→立项小改：`device_series` 加 `discovery_keyword` 列（可空，空则沿用系列名），订阅调度优先用它。

### T92 产品图采集命中率观察（观察）

**背景**：T90 上线前只有 mock 与自托管图验证，真实外网图的产出率（Agent 愿不愿意给、URL 是否直链）与下载成功率（防盗链 / 403 比例）未知。
**动作**：跑 10~20 次真实抓取，统计：返回 imageUrl 的任务占比、下载成功占比、失败原因分布（日志关键字 `产品图下载失败`）。
**结论出口**：产出率 > 50% 且成功 > 70% → 维持；产出率低 → 优化 persona 第 10 条措辞或把 imageUrl 提到输出 schema 更显眼处；403 比例高 → 评估加 Referer 白名单或退回纯手动上传。

**根因（2026-10-29 复验定位）**：不是模型不配合，而是**解析端漏接字段**。persona 已要求模型在输出 JSON 顶层给出 `imageUrl`，`AgentCallbackService` 也已具备「收 `_imageUrl` → 审核通过后异步下载」的完整消费链路，但 `CollectService.buildCollectResult()` 只解析了 modelName / brandName / sourceUrl / params / paramConfidence / variants / confidence / notes，**从未读取 `data.path("imageUrl")`，从未调用 `setImageUrl()`**。模型即使返回了直链也会在解析阶段被静默丢弃，这是 163 台机型 0 张网络图的直接原因。
**修复**：`buildCollectResult` 增加 `result.setImageUrl(nullableText(data, "imageUrl"))`（正常路径与抢救路径共用该方法，一处生效两处覆盖）；空串由下游 `StringUtils.hasText` 拦截，不会污染 `_imageUrl`。本地 `mvn clean package` 68 例全绿。
**待复验**：部署后新跑 1~2 台曾无图的机型，确认待审核记录带 `_imageUrl`、审核通过后异步下载落到 `/uploads/...`。

### T93 AI 预审阈值校准（观察）

**背景**：T86 的 HIGH（<0.6 置信或单位冲突）/ PASS（全参数 ≥0.9）阈值是拍的，真实分布未知。HIGH 占比过高会狼来了，PASS 过少则分组无意义。
**动作**：积累 50+ 条待审核记录后看分布：HIGH / MEDIUM / NORMAL / PASS 各占比，以及 HIGH 里人工最终否决的比例（HIGH 且被丢弃 = 打标准了）。
**结论出口**：HIGH > 50% → 提高阈值（如 0.5）或把「与旧值差异」降权；HIGH 且否决率 < 20% → 阈值过严，下调。

### T94 热门口径稳定性观察（观察）

**动作**：上线两周后人工核对首页 hot 列表是否符合访问直觉；检查新库/低流量站点是否频繁触发「回落发布时间」逻辑（日志无直接记录，可对比 hot 结果与 PV 排序）。异常再考虑给 hot 加 `fallback` 标记字段便于诊断。

### T95 采集搜索源真实验证（验证，T63 遗留）✅ 生产已通过（2026-10-29）

**动作**：生产「AI 配置-采集搜索源」配好 Tavily Key（或 SearXNG 地址）→ 弹窗内「测试」→ 记录引擎 / 条数 / 延迟；跑 2~3 个真实抓取任务看任务日志里引擎名与回落情况。

### T96 模型挂死快速失败复核（观察，T61/T62 遗留）✅ 已定位并修复（2026-10-29）

**背景**：`MODEL_TIMEOUT_SECONDS=90` 在挂死端点场景曾观察不到快速失败（疑似框架内部重试或未走 transport 配置），当前靠任务级 300s 兜底。

**生产取证（98 条采集任务）**：失败 24 条，其中 **16 条是抓取超时**，10 条耗时精确停在 300.0xx s；剩余 8 条为「模型未输出 JSON」5 / 重试耗尽 2 / 模型 API 403 1。任务过程日志显示大量 90~162 秒无工具日志的空档，说明单次模型往返实际耗时远超 90s 的假设。

**根因**：三个参数互相不匹配，而非模型端挂死。

| 参数 | 原值 | 问题 |
| --- | --- | --- |
| 任务硬上限 | 300s | 超时即抛异常，已抓正文全部作废 |
| 预算储备（工具闸门） | 固定 25s | 远小于一次慢模型往返（实测 50~162s），闸门在 275s 才触发，模型收到「交卷」后调用本身又要 50s+，直接撞墙 |
| 单次模型超时 | 90s | 一次挂死吃掉 30% 预算，且与储备无联动 |

日志中**从未出现**「剩余时间不足」告警，反证闸门始终没触发。

**修复（仅调参，2026-10-29）**：
- 预算储备改为按模型超时推导：`储备 = MODEL_TIMEOUT_SECONDS + 15s`（[CollectTools.java](file:///d:/IdeaProject/ai/SpecTrace/backend/src/main/java/com/spectrace/agent/tool/CollectTools.java) `budgetGuard`），默认 45+15=60s，闸门在 240s 触发，给交卷留足窗口
- `MODEL_TIMEOUT_SECONDS` 90 → 45（[AgentProperties.java](file:///d:/IdeaProject/ai/SpecTrace/backend/src/main/java/com/spectrace/agent/config/AgentProperties.java) 与 [application.yml](file:///d:/IdeaProject/ai/SpecTrace/backend/src/main/resources/application.yml)），挂死调用最多吃掉 45s
- README 环境变量表同步两者联动关系

**验证（第一轮）**：`mvn test` 68 例全绿。生产复跑 3 个机型：iPhone SE 3（238s，35 项参数）、iPhone 14 Pro Max（151s）成功转待审核；iPhone 17 Pro 仍 300s 撞墙。

**第一轮暴露的盲区**：闸门已能触发（日志出现「剩余时间不足」），但它只挂在「工具调用」上，模型两轮工具之间的思考时间（实测 27~75s）不受控 —— 最后一次工具调用时剩余 58s，等模型想完要调工具时只剩 30s，交卷窗口被思考吃掉。静态储备无法覆盖这种粒度问题。

**修复 B（超时抢救，2026-10-29）**：
- [CrawlSession.java](file:///d:/IdeaProject/ai/SpecTrace/backend/src/main/java/com/spectrace/agent/CrawlSession.java) 新增 `fetchedPages()`，暴露本任务已成功抓取的正文
- [CollectService.java](file:///d:/IdeaProject/ai/SpecTrace/backend/src/main/java/com/spectrace/agent/service/CollectService.java) 新增 `salvageAfterTimeout()`：捕获超时后，用已抓正文（单页 6000 字、总量 24000 字封顶）发一次「只输出 JSON」的短请求，超时取 `max(60s, 2×模型超时)`
  - 抢救成功 → 正常转待审核，摘要标注「仅基于已抓正文二次抽取，未做交叉核验」
  - 抢救失败 → 仍转待审核，参数为空并附 `sources` 来源清单，人工补齐
  - 无任何已抓正文 → 维持 FAILED
- [AgentCallbackService.java](file:///d:/IdeaProject/ai/SpecTrace/backend/src/main/java/com/spectrace/service/AgentCallbackService.java) 加护栏：本次抓取产出空参数时不覆盖同机型已有的有效待审核记录
- 顺带把正常路径与抢救路径的结果收敛逻辑抽为 `buildCollectResult()`，避免两套口径
- `TASK_TIMEOUT_SECONDS` 300 → 420（[AgentProperties.java](file:///d:/IdeaProject/ai/SpecTrace/backend/src/main/java/com/spectrace/agent/config/AgentProperties.java) 与 [application.yml](file:///d:/IdeaProject/ai/SpecTrace/backend/src/main/resources/application.yml)），储备仍为 60s，闸门在 360s 触发

**验证（第二轮）**：`mvn test` 68 例全绿。

**验证（第三轮，生产复跑 2026-10-29）**：iPhone 17 Pro 此前连续 300s 撞墙，本次 **219s 正常收敛**，39 项参数、置信度 high、来源 `support.apple.com/zh-cn/125090`，直接转待审核 #63，**未触发抢救路径**——420s 预算本身已足够。
连续三台机型（SE 3 238s / 14 Pro Max 151s / 17 Pro 219s）全部成功，近期 `nearTimeout` 归零。
**遗留**：抢救分支（超时后用已抓正文二次抽取）在生产上尚未被真实触发过，仅有编译与本地用例保障。若要验证，可临时把 `TASK_TIMEOUT_SECONDS` 调到 90s 制造一次超时，观察「超时抢救启动 / 抢救成功或失败」日志后再调回。

### T97 详情页参数行「N 条待核实」（开发，T83 遗留）✅

**改动**：详情接口对带参数的行附带该机型 OPEN 状态反馈数（`feedback` 按 device_id + status 聚合，可挂进现有 detail 缓存失效链）；前端参数行 meta 区显示「N 人待核实」灰标。访客提交反馈后立即可见自己不是一个人在报，管理员处理完自然消失。
**预估**：0.5 天（后端聚合 + 前端一行展示）。

**已完成（2026-10-27）**：`DeviceDetailVO.paramFeedback` 返回「paramKey → OPEN 反馈数」，`openFeedbackCounts` 按 `device_id + status=OPEN + param_key` 分组计数，空 key 与非数字计数直接跳过；计数随 `detail` 进缓存，提交反馈按机型 evict、处理反馈全量 evict，故访客提交后即时可见、管理员处理后即时消失；详情页参数行 meta 区展示「N 人待核实」灰标（`title` 给出完整说明），提交成功后本地自增而非重拉整页，保住滚动位置。
**验证**：`DeviceServiceTest` 3 例锁住 selectMaps 计数类型兼容（Integer/Long/BigDecimal）与脏行跳过；`mvn test` 49 例全绿、typecheck / build 通过、全量 e2e 27 passed / 2 skipped（`series-connection` 补灰标断言：屏幕尺寸显示 2 人待核实、芯片行不显示）。

### T98 发布前真机 / 读屏人工复核（人工，T26/T34 遗留）

**动作**：每次对外发布前，用真机（iOS Safari / Android Chrome）走一遍主链路（首页→详情→对比→趋势），读屏软件过一遍登录与审核表单；e2e 只覆盖桌面 Chromium 键盘路径，替代不了这一步。

---

## P16 · 运行监控批次（2026-10-25 用户拍板全量开工，当日完成）

> 落地记录：三个决策点按推荐执行——业务监控新建「运行监控」页 + 技术指标并入「系统运维」页；告警复用 `AGENT_ALERT_WEBHOOK`；搜索词只记「词 + 次数 + 空结果次数」不记 IP/访客标识、保留 90 天。
> 验证口径：每项过编译 + 42 单测、typecheck / build、e2e 全绿（批次结束时 27 例），关键链路真实后端冒烟（搜索记录 upsert 与空结果、告警触发、runtime/quality 指标）。
> 后端汇总接口：`/api/admin/metrics/{collect,search,traffic,quality,runtime}`；新增表 `search_log`（词日聚合）与 `metric_daily`（对比发起等单值计数），二者均纳入保留策略；`badges` 扩展待审核条数与最老滞留。

> 来源：管理员视角监控项评审。原则：先业务链路后技术指标；先用现成表聚合、唯一值得的埋点是搜索词；不引入外部监控栈（Prometheus/Grafana 违背零外部服务架构，Micrometer + 后台自绘够用）。
> **每项附推荐意见（★推荐 = 建议做 / ○可缓 = 观察后再定 / ✕不建议），由用户逐项拍板。**

### 决策清单

| 任务 | 名称 | 数据来源 | 预估 | 推荐意见 |
| --- | --- | --- | --- | --- |
| T99 | 运行监控页 + 采集漏斗与成功率 | `agent_task` 现成 | 1.5 天 | ✅ 0477ee3 |
| T100 | 待审核积压与滞留时长 + 侧栏徽标升级 | `agent_pending_data` 现成 | 0.5 天 | ✅ f322054 |
| T101 | 按模型成功率/耗时排行 | `agent_task`×`ai_model` 现成 | 0.5 天 | ✅ 30e339f |
| T102 | 搜索词记录与空结果率 | **需新增 `search_log` 埋点** | 1 天 | ✅ 7b5bc0f |
| T103 | PV 趋势与对比使用率卡片 | `device_view_daily` 现成 | 0.5 天 | ✅ 0e43cae |
| T104 | 告警规则三条（复用 webhook） | 现成 + 阈值 | 0.5–1 天 | ✅ dae61b4 |
| T105 | Caffeine 命中率与限流 429 计数 | 需开 `recordStats` + 计数器 | 1 天 | ✅ 56394a6 |
| T106 | 数据质量观测卡片（陈旧度分布/反馈时效/报错参数排行） | 现成聚合 | 1 天 | ✅ f7e7a98 |
| T107 | 运维页运行时卡片（SQLite 大小/备份耗时/磁盘/JVM/图片队列） | File API + Micrometer | 1 天 | ✅ d37c8ae |
| — | 外部监控栈（Prometheus/Grafana） | — | — | ✕不建议（违背零外部服务架构） |
| — | 前端 JS 错误监控 / 访客 UV 追踪 | — | — | ✕不建议（规模不值得 / 隐私负担，随用户体系储备项一并决策） |

### 三个需要拍板的决策点

1. **监控页归属**：建议业务监控（采集/积压/搜索词/PV）新建「运行监控」页，技术指标（缓存/限流/JVM/磁盘）并入现有「系统运维」页——业务与运维受众不同。备选：全部塞进系统运维页（页面会很长，不推荐）。
2. **告警渠道**：建议复用现有 `AGENT_ALERT_WEBHOOK`（钉钉），不新建渠道；站内通知（管理端徽标闪烁/通知中心）后续有需要再立项。
3. **搜索词隐私与保留**：`search_log` 只记「关键词 + 命中数 + 日期」，不记 IP 与用户标识；纳入 retention 默认保留 90 天。

### T99 运行监控页 + 采集漏斗与成功率（★推荐）

**改动**：新后台页「运行监控」（菜单位于抓取审核之后）；`GET /api/admin/metrics/collect`（仅管理员）按天聚合近 30 天任务状态漏斗（QUEUED/RUNNING/PENDING_REVIEW/APPROVED/DISCARDED/FAILED）、成功率折线、耗时 P50/P95 与顶格超时占比、产品图待下载数（`device.thumbnail_pending_url` 计数顺带返回）；前端复用 `useECharts`。
**验收**：页面可见漏斗与趋势；造失败/超时任务数据后数字正确。
**预估**：1.5 天

### T100 待审核积压与滞留时长 + 徽标升级（★推荐）

**改动**：`GET /api/admin/badges` 扩展返回 `pendingReview`（条数）与 `pendingOldestHours`（最老一条 PENDING_REVIEW 的小时数）；侧栏「抓取审核」挂徽标；运行监控页展示积压曲线。
**价值**：滞留时长是审核瓶颈与 T88 订阅自动化的安全阀量化。
**预估**：0.5 天

### T101 按模型成功率/耗时排行（★推荐）

**改动**：同 T99 接口扩展：按 `ai_model` 聚合成功率、平均/P95 耗时、失败 TOP 原因；表格呈现。
**价值**：哪个模型又慢又错一目了然，是 T59「重试换模型」的决策依据。
**预估**：0.5 天

### T102 搜索词记录与空结果率（★推荐·本批唯一埋点）

**改动**：新表 `search_log`（keyword / hit_count / search_date，keyword+date 唯一 upsert 计数）；`/public/devices/suggest` 命中后旁路写入（失败只记日志）；`GET /api/admin/metrics/search`：TOP 关键词、空结果率、空结果词排行；纳入 retention（默认 90 天）。
**价值**：唯一能回答「访客在找什么而库里没有什么」的信号，比缺参排行更前置地指导补录与「系列发现」选型。
**验收**：搜索后 TOP 榜更新；空结果词出现在排行；retention 清理生效。
**预估**：1 天

### T103 PV 趋势与对比使用率卡片（★推荐）

**改动**：`GET /api/admin/metrics/traffic`：日 PV 近 30 天折线 + 周环比、Top 机型 PV、对比发起次数；运行监控页卡片。
**预估**：0.5 天

### T104 告警规则三条（★推荐）

**改动**：扩展现有告警发送链（`AGENT_ALERT_WEBHOOK`）：①待审核积压 > N 条或最老 > 48h；②备份失败（BackupService catch 内补发）；③磁盘剩余 < 阈值（数据目录所在盘）。每小时评估一次（复用/新增轻量 scheduled job），阈值走环境变量，0 关闭。
**验收**：造积压后 webhook 收到消息；阈值可关。
**预估**：0.5–1 天

### T105 Caffeine 命中率与限流 429 计数（○可缓）

**改动**：Caffeine `recordStats()` 开启并在运维页展示各缓存命中率；`MemoryRateLimiter` 加命中计数按接口汇总。
**预估**：1 天

### T106 数据质量观测卡片（○可缓，与 P15 观察项可合并交付）

**改动**：陈旧度分布（>180 天机型数趋势）、反馈时效（OPEN 平均滞留/周处理量）、被报错最多参数 TOP10。
**预估**：1 天

### T107 运维页运行时卡片（○可缓）

**改动**：系统运维页新增：SQLite 文件大小、备份耗时趋势、磁盘剩余、JVM 堆/线程（Micrometer）、图片下载队列数。
**预估**：1 天

---

## P17 · 上线安全与容错（2026-10-27 完成）

> 来源：全量代码复盘后的 P0 三条（安全底线、白屏兜底、主链路错误分型）。验证：`mvn test` 44 例全绿、`npm run typecheck` / `build`、`npx playwright test` 27 passed / 2 skipped；真实后端冒烟——`/api/public/devices/999999` 返回 HTTP 404 且 body `code=404`，正常机型仍 200；`/api/public/compare?ids=abc` 返回「错误编号 3ea3b77fff59」，日志同一编号带 `/api/public/compare` 与堆栈。T120 补齐状态码后复跑：`mvn test` 46 例全绿。

| 任务 | 状态 | 落地要点 |
| --- | --- | --- |
| T108 JWT 密钥无默认值 | ✅ | `docker-compose.yml` 改 `${JWT_SECRET:?...}`（缺失或为空 compose 直接退出），`.env.example` 留空并注明必填，README 部署段同步；补上「compose 默认值绕过后端拒绝启动」这道缺口 |
| T109 异常信息不回流客户端 | ✅ | `handleOther` 只回「服务器内部错误（错误编号 xxx）」，traceId 同时写进日志与响应；新增单测断言 message 不含 SQL / 字段名 |
| T110 404 语义与详情页错误分型 | ✅ | 新增 `NotFoundException`（回真实 404），`DeviceService.detail` 改抛它；详情页拆出 `loadError` 态与重试，参数元数据加载失败只降级参数区、不再整页判成「机型不存在」；顺带修同路由换机型（系列条互跳）不重载——组件被复用后 URL 变了内容没变，e2e 补断言锁住 |
| T111 前端渲染期兜底 | ✅ | 新增 `AppErrorBoundary`（`onErrorCaptured` + 重建子树重试，路由切换复位）、`app.config.errorHandler` 兜边界外异常、`router.onError` 处理发版后 chunk 失效（10 秒窗口内只自动刷新一次） |
| T120 异常统一真实状态码 | ✅ | `handleBiz` 按 code 回真实 4xx/5xx（非 HTTP 码回退 400）、`handleValid` / `handleTypeMismatch` / `handleUnreadable` 回 400、`handleOther` 回 500，body 结构不变；前端拦截器本就按 `error.response.data.message` 取提示，无需改动；单测补 400/500 状态断言 |

**遗留已清零**：此前业务异常一律 HTTP 200 + `body.code`，网关 5xx 告警、APM 错误率与日志巡检都读不到失败，已由 T120 统一。

---

## P18 · AgentTaskService 拆分（2026-10-27 完成）

> 来源：`AgentTaskService` 1160 行承担任务调度、回调落库、待审核读取、审核入库、风险打分、系列发现六类职责，且是全站最需要稳定、测试覆盖最薄的模块。本轮只搬不改，行为零变更。

| 任务 | 状态 | 落地要点 |
| --- | --- | --- |
| T112 按职责拆四类 + 状态写入器 | ✅ | `AgentTaskService` 369 行（提交/重试/列表/重启恢复/系列发现）、`AgentCallbackService` 235 行（回调落库与幂等覆盖）、`AgentReviewService` 533 行（待审核读与审、风险打标）、`AgentTaskStatusService` 38 行（状态唯一写入口）；调用点同步切分：`AdminAgentController` / `AdminBadgeController` / `AdminDeviceController` / `CollectorWorker` / `MetricsAlertService` |
| T113 机型匹配下沉 | ✅ | `DeviceService.findByName` 两个重载承接「品牌名+机型名 → 已有机型」，审核覆盖判断与系列发现去重共用一套口径，不再各写一份查询 |
| T114 采集日期解析共用 | ✅ | 抽出 `CollectedDate.parse`，审核入库与系列建档共用；解析失败降级为空值且留 warn 日志 |
| T115 测试随迁 | ✅ | `AgentTaskServiceApproveTest` → `AgentReviewServiceApproveTest`（原文件已删除，用例与会话断言不变，仅把机型查找的桩点改到 `DeviceService.findByName`） |

**未再细拆的原因**：`AgentReviewService` 533 行虽超过「300 行量级」，但仍是「待审核」单一职责；读侧的风险打标与写侧的置信度收敛共用同一套置信度/单位解析，按读写再拆会形成双向调用。如后续审核规则继续增长，再考虑把「风险预审」抽为独立评估器。

**验证**：`mvn test` 44 例全绿；打包后以临时库真实启动（无循环依赖），登录后验证——新建任务返回 QUEUED 且列表可见；`/api/admin/pending-data` 风险排序与非排序两条分支均正常；`/api/admin/devices` 待审核徽标计数正常；对种子待审核数据真实执行审核通过，生成 deviceId=7、待审核置 APPROVED、公开详情页 200、`badges.pendingReview` 归零。

---

## P19 · 抓取入口归一 && Series 页拆分（2026-10-27 完成）

> 来源：`Series.vue` 998 行并列 7 个场景；「选模型 → 批量抓取」在机型页 / 数据健康度 / 系列页各写一遍，文案与默认模型选择逐渐分叉。

| 任务 | 状态 | 落地要点 |
| --- | --- | --- |
| T116 抓取弹窗三合一 | ✅ | 新增 `ScrapeModelDialog`（目标列表 + 模型下拉 + 提交）与 `useScrapeDispatch`（状态、默认模型、逐台顺序提交、成功提示）；三处入口只保留「目标归一 + 文案」；健康度保留「提交抓取」按钮文案，系列页保留「已为「X」提交 N 个任务」提示 |
| T117 AI 模型选项缓存 | ✅ | `useAiModels` 进程内缓存 60s + 请求去重，替代系列页三处 `aiModels.value.length ? ... : adminApi.aiModels()`；加载失败降级为空列表（仍可用后台默认模型），不阻断弹窗 |
| T118 Series 页拆分 | ✅ | 998 → 557 行；抽出 `SeriesDevicesSheet`（230）、`SeriesSuggestDialog`（143）、`SeriesSubscriptionDialog`（114）、`SeriesMergeDialog`（71）；页面只保留列表、未归类清单、编辑表单与删除确认，子组件变更通过 `changed/merged/created` 事件回传刷新 |
| T119 机型页首屏减负 | ✅ | 模型下拉改为按需加载（打开弹窗时拉），机型页首屏少一次请求；提交成功后仍清空勾选 |

**行为差异（有意）**：系列页抓取弹窗改为「先开弹窗、目标与模型并行加载」，原来要先等两个请求返回再打开；慢网络下用户能立刻看到反馈，代价是弹窗内会先出现骨架。

**验证**：`npm run typecheck`、`npm run build` 通过；`playwright test admin-quality + series-connection + admin` 17 例全绿（含订阅、重抓、补抓、AI 推荐相关链路）。T97 交付后已补跑全量套件：27 passed / 2 skipped（2026-10-27），此处缺口已闭环。

---

## P20 · 访客可访问范围收口（2026-10-29 完成）

> 来源：AI 生图成本高，需要明确未登录访客能看到什么、能用什么；同时把「登录」的语义收敛为「后台员工身份」。

**决策**
- 不引入 C 端注册 / 会员体系（无回访实证，注册与合规都是净成本）；访客端不出现「登录解锁」类引导。
- AI 生图从访客功能降级为后台内部工具：公开接口下线，访客端零入口，后台「AI 绘图」新增生成页。
- 后台入口从主导航移到页脚，访客导航只保留四类公开内容。

| 任务 | 状态 | 落地要点 |
| --- | --- | --- |
| T120 生图转内部工具 | ✅ | 删除 `PublicAiImageController`；新增 `GET/POST /api/admin/ai-image/{status,generate}`（管理端鉴权覆盖），限流键改为管理员账号；后台「AI 绘图」新增「生成海报」页（系列 + 模板 + 标题） |
| T121 访客端入口下线 | ✅ | `SocialExportDialog` 去掉「AI 海报」Tab 与全部绘图调用，只保留模板卡与文案；e2e 断言访客端不存在该 Tab |
| T122 登录回跳 | ✅ | 路由守卫未登录跳转携带 `redirect`；登录页只接受 `/admin/` 内部路径，待改密仍先进改密页 |
| T123 角色门控 | ✅ | 「系统运维」仅 SUPER_ADMIN 可见并由路由层拦截；「账号权限」对 ADMIN 只读（隐藏新增 / 改角色 / 启停 / 重置 / 删除） |
| T124 入口收敛 | ✅ | 访客端顶部与移动端 Sheet 移除「后台」，页脚增加「管理入口」弱链接 |

**未做**：AI 文本（对比摘要 / 世代回顾 / 社媒文案）维持公开 + 5 次/分钟/IP + 24h 缓存；如需硬预算，另起任务做全局日配额与超限降级文案。

**验证**：`mvn -DskipTests compile`（JDK 21）、`npm run typecheck`、`playwright test series-trends.spec.ts` 6 例全绿。

---

## P21 · 公开可见性口径收口（2026-10-29 完成）

> 来源：列表、详情、对比都不过滤 `device.status`，与联想/热门/趋势/sitemap 的「只统计上架」口径不一致，草稿与下架机型可被游客直达读取。

**决策**
- 草稿 / 下架机型对访客一律不可见（与 README「草稿前台不可见」对齐）；后台仍可见可编辑。
- 后台 AI 海报继续以 UUID 文件名在 `/uploads` 公开访问（后台直链预览需要，不额外加鉴权）。

| 任务 | 状态 | 落地要点 |
| --- | --- | --- |
| T125 列表口径 | ✅ | 公开分页重载默认 `status=1`；管理端仍走 7 参重载按筛选传值 |
| T126 详情口径 | ✅ | `PublicController.detail` 调 `requirePublished`，非上架机型 404，且不计入 PV |
| T127 对比口径 | ✅ | `compare` 先过滤非上架再判缺失，包含草稿时整单拒绝；变体查询改用可见 id |
| T128 验证 | ✅ | 新增 `DeviceCompareVisibilityTest` 2 例、`DeviceServiceTest` +2 例；`mvn test` 59 例全绿 |
| T129 重复登录 | ✅ | 登录页检测到本地会话时直接跳 `redirect` 或后台首页，token 失效由守卫清凭证后带回本页 |

**验证**：公开列表不再出现草稿；`GET /api/public/devices/{draftId}` 返回 404；`GET /api/public/compare?ids=published,draft` 返回 400「部分机型不存在或已下架」；后台机型管理仍能看到并编辑草稿。

---

## P22 · 登录态单一入口（2026-10-29 完成）

> 来源：token / 角色在路由、拦截器、布局与页面里各自读 localStorage，判定口径分散；401 清理后页面状态不会即时同步。

**决策**：后台登录态收敛到 `frontend/src/stores/auth.ts`，其它模块只消费 store，不再直接读写 `spectrace-token` / `spectrace-user`。

| 任务 | 状态 | 落地要点 |
| --- | --- | --- |
| T130 store | ✅ | 响应式 `token` / `user` + `isLoggedIn` / `isSuperAdmin`，`setSession` / `updateUser` / `clear` 统一落盘 |
| T131 调用方替换 | ✅ | 路由守卫、axios 拦截器、`AdminLayout`、`Trends`、`Users`、`Login`、`ChangePassword` 全部改为读 store |
| T132 401 即时生效 | ✅ | 拦截器清 store 后，访客页的管理员入口随响应式状态立即隐藏，无需刷新 |
| T133 验证 | ✅ | `npm run typecheck` 通过；`admin` / `admin-quality` / `series-trends` 共 19 例 e2e 全绿 |

**未做**：跨标签页登录态同步（`storage` 事件）；反馈编号随机化仍待拍板。
> 二者已于 P23 落地（2026-10-29 用户拍板：随机公开码方案）。

---

## P23 · 成本护栏与反馈凭据收口（2026-10-29 完成）

> 来源：P20/P22 两处「未做」清账。用户拍板：AI 文本日配额取**全站 100 次/天**；反馈编号采用**随机公开码**方案（8 位 base62，老数据回填，历史纯数字编号兼容查询）。

| 任务 | 状态 | 落地要点 |
| --- | --- | --- |
| T134 AI 文本全站日配额 | ✅ | `AiInsightSupport` 新增自然日计数（`SPECTRACE_AI_INSIGHT_DAILY_QUOTA`，默认 100，0 不限），对比摘要 / 世代回顾 / 社媒文案三处真实模型调用共享额度；只计成功调用（缓存命中与失败不消耗）；检查在调用方 try-catch 之外，超限回 429「今日 AI 生成次数已达上限」，前端沿用拦截器提示语义零改动；管理端「AI 推荐系列」不计入访客额度 |
| T135 跨标签页登录态同步 | ✅ | `stores/auth.ts` 监听 `storage` 事件（key 命中 token/user 或 null 时按最新 localStorage 对齐），其它标签页登录/登出/改资料即时生效，访客页管理员入口同步显隐；本标签页写入不触发事件，无回环 |
| T136 反馈编号随机公开码 | ✅ | `feedback.public_code`（schema + 迁移补列 + 老数据 SecureRandom 回填 + 唯一索引），提交返回 8 位 base62 随机码；`GET /api/public/feedback/{code}` 先按码查、纯数字回落旧 id（历史回执不失效）；回执与查询结果改等宽字体展示码值，去掉 `#` 前缀 |
| T137 发布走查清单 | ✅ | 新增 `docs/release-checklist.md`：真机主链路 14 步、读屏 5 场景、键盘路径与记录表，T98 每次发布前照单执行 |

**验证**：`mvn test` 60 例全绿（新增日配额用例）；typecheck / build / e2e 全绿（mock 同步公开码口径，回执断言改 `E2E0002`）。README 环境变量表、访问范围与纠错反馈条目同步。

---

## P24 · 生产验证缺陷（2026-10-29 两轮验证，已全部闭环）

> 来源：P15 首轮生产远程验证（已完成两轮：首轮旧构建 + 复验新构建）。

| 编号 | 缺陷 | 优先级 | 结论 | 预估 |
| --- | --- | --- | --- | --- |
| T138 | `GET /admin/auth/me` 返 500 | ~~高~~ | ✅ **撤回**：路径写错，真实接口是 `/api/auth/me`，复验 200 正常 | 无 |
| T139 | `pending-data` 60 条 vs `metrics.pendingReview` 0 | ~~高~~ | ✅ **撤回**：60 条全为历史（58 通过 / 2 丢弃），`status=PENDING_REVIEW` 确为 0 | 无 |
| T140 | `GET /admin/metrics/model-calls` 返 500 | 中 | ✅ **已随部署修复**：复验 200，含 total/success/failed/p50/p95/p99/series/failures | 已闭环 |
| T141 | `GET /admin/ai-image/status` 返 500 | 中 | ✅ **已随部署修复**：复验 200，`available=true, enabled=true` | 已闭环 |
| T142 | 生产构建落后 HEAD | **高** | ✅ **已部署到 HEAD**（用户 2026-10-29 重新部署） | 已闭环 |

**教训**：首轮把「猜测的接口路径」当成真实缺陷报出（T138），把「不同 status 口径」当成数据矛盾（T139）。远程验证必须先核对前端实际调用路径与接口参数语义，再判定缺陷。

---

## P15 · 生产观察批执行记录（2026-10-29 启动）

> 执行方式：远程验证生产环境 `https://spec.8988666.xyz/`（ego-browser 登录超管账号；首轮旧构建、次轮部署到 HEAD 后复验）。

| 任务 | 结论 | 日期 |
| --- | --- | --- |
| T95 采集搜索源真实验证 | ✅ **通过**：Tavily 真实返回 5 条 / 2375ms，用量 297/1000 积分；引擎顺序 `tavily → searxng`，SearXNG 走匿名（`searxngTokenSet=false`） | 2026-10-29 |
| T91 订阅发现关键词效果验证 | ⬜ 未开始：生产无启用中的系列订阅，需先建 1~2 个系列订阅实跑 | |
| T92 产品图采集命中率观察 | ❌ **未生效**：163 台机型抽样，**0 张来自网络采集**；95% 用 `placeholder.svg`，5% 为手工本地 SVG（`/device-images/*.svg`），无外链图。历史批准均发生在新构建之前 → 需新提交 1 条数据重跑才能判定 T90 是否生效 | 2026-10-29 |
| T93 AI 预审阈值校准 | ⚠️ **无样本**：待审核队列为 0（60 条已全部处理），`riskLevel` 只在 `status=PENDING_REVIEW` 时计算。需先制造 3~5 条待审核数据再校准 | 2026-10-29 |
| T94 热门口径稳定性观察 | ⚠️ **样本不足**：`/public/devices/hot` 正常返回 8 条（小米 6 + iPhone 2），但 90 天 PV 仅 2，热度信号无统计意义 | 2026-10-29 |
| T96 模型挂死快速失败复核 | ✅ **已修复（两轮）**：① 储备改「模型超时 + 15s」、模型超时 90→45，生产复跑 2/3 成功；② 超时抢救——用已抓正文二次抽取，失败转待审核留来源，任务上限 300→420s。`mvn test` 68 例全绿，待部署复验 | 2026-10-29 |
| T98 真机/读屏人工复核 | ⬜ 按 `docs/release-checklist.md` 执行（e2e 模拟视口预检已通过） | |

### 量化现状（复验后）

| 指标 | 值 | 判读 |
| --- | --- | --- |
| 机型总量 | 163 | 访客端 11 品牌 / 8 热门正常 |
| 采集任务（90 天） | 98 次：通过 62 / 丢弃 12 / 失败 24 | 成功率 72.1%，失败率 24.5% 偏高 |
| 采集耗时 | p50 114s、p95 300s、均值 139s | p95 顶到超时上限，10% 任务 near-timeout |
| 活跃采集日 | 仅 10-24（33）、10-27（53）、10-28（12） | 采集为人工触发，非持续 |
| 待审核积压 | 0 条（历史 58 通过 / 2 丢弃） | 无积压，与 `pendingReview=0` 一致 |
| 产品图覆盖 | 10/163（6.1%），均为手工 SVG；网络采集 0 | **T90 实际未产出** |
| 访客流量 | 90 天 PV 合计 2 | 未对外推广，观察类指标暂无可比性 |
| 数据质量 | staleDevices 0、feedbackOpen 0 | 无积压 |

---

## 依赖与并行建议

- T1 独立，最先做，当天可完成
- T2 / T3 / T4 均为前端改动，可并行
- T5 涉及 SQLite 迁移，先写迁移脚本再改业务
- T6 依赖 T5 的来源字段
- T7 需要新表与公开接口，注意限流
- T8 / T9 / T12 共用机型表单与上传逻辑，建议同一人连续做
- T10 / T11 均在后台列表页，可并行
- T13 依赖 T5 的单位与来源数据质量
- T15 会改动路由模式，放在功能稳定后做
- T21 与 T6 共用审核链路，需先定 confidence 粒度
- T22 直接复用 T18 的 `BackupService`，可与 T23 并行
- T24 影响审核、导入、历史三处展示，改动面最大，单独排期
- T25 / T26 均为局部改动，可与 T24 并行
- T32 / T33 相互独立，可并行
- T34 只动 e2e，可与前端改动并行；真机复核需在发布前安排人工
- T35 依赖 T32 定下的口径（涨跌幅只在前端算），但接口与展示互不影响，可后置
- T37 与 T38 互补：CI 先上能立刻拦住编译与构建类回归，单测随后自动接入同一条流水线
- T39 与 T37 无依赖，但建议等 CI 就绪后再补，否则新用例只能靠手动跑

## 每项完成定义（DoD）

- `npm run typecheck`、`npm run build` 通过
- 后端 `mvn -DskipTests package` 通过（JDK 21）
- 手动验收路径走通，截图或录屏留档
- 同步更新 README / AGENTS.md 中受影响的行为说明
- 提交信息中文，一个任务一个 commit

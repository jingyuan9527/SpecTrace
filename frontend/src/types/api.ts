/**
 * 前后端共享的接口类型契约。
 *
 * <p>字段以后端 VO / 实体为准（Jackson `non_null`：null 字段不输出，因此均可选）；
 * 后端改字段名时这里与 `api/index.ts` 会在 `typecheck` 阶段拦住前端调用方，
 * e2e 的 `mock-api.ts` 同样引用这些类型，mock 与真实后端的漂移也会在编译期暴露。
 * 时间为 Jackson ISO 序列化后的字符串（`2026-10-24T12:00:00`）。
 */

// ---------- 通用 ----------
export interface PageResult<T> {
  total: number
  current: number
  size: number
  records: T[]
}

export interface BatchResult {
  success: number
  failed: number
  failures: string[]
}

// ---------- 实体（管理端与公开端共用） ----------
export interface Brand {
  id: number
  name: string
  logo?: string | null
  sortOrder: number
  status: number
  createdAt?: string
  updatedAt?: string
}

export interface ParamGroup {
  id: number
  name: string
  sortOrder: number
  createdAt?: string
}

export interface ParamDef {
  id: number
  paramKey: string
  paramName: string
  groupId: number
  /** date / number / text / enum */
  paramType: string
  enumOptions?: string | null
  unit?: string | null
  sortOrder: number
  enabled: number
  createdAt?: string
}

export interface DeviceVariant {
  id?: number
  storage: string
  ram?: string | null
  price?: number | null
  currency?: string | null
  priceType?: string | null
  sortOrder?: number | null
}

export interface Device {
  id: number
  brandId: number
  seriesId?: number | null
  name: string
  releaseDate?: string | null
  thumbnail?: string | null
  description?: string | null
  status: number
  /** 同品牌同名机型的待审核抓取数据条数（管理端列表徽标，0/空为无） */
  pendingReviewCount?: number | null
  /** 近 30 天访客浏览量（管理端列表用，统计不可用为 null） */
  pv30d?: number | null
  createdAt?: string
  updatedAt?: string
}

/** AI 推荐的系列名候选（existing 表示同名系列已存在） */
export interface SeriesSuggestion {
  name: string
  reason?: string | null
  existing?: boolean
}

export interface DeviceSeries {
  id: number
  brandId: number
  name: string
  sortOrder: number
  status: number
  /** 后台列表附带字段 */
  brandName?: string
  deviceCount?: number
  /** 已有参数值的机型数，完善度 = paramDeviceCount / deviceCount */
  paramDeviceCount?: number
  startYear?: number | null
  endYear?: number | null
}

/** 系列内缺参数的机型（含草稿），「补抓缺参机型」的目标 */
export interface SeriesMissingDevice {
  id: number
  name: string
  /** 0 草稿 1 上架 */
  status: number
  releaseDate?: string | null
  thumbnail?: string | null
}

export interface AdminUser {
  id: number
  username: string
  nickname?: string | null
  /** SUPER_ADMIN / ADMIN */
  role: string
  status: number
  mustChangePassword?: number
  createdAt?: string
}

export interface Feedback {
  id: number
  deviceId: number
  deviceName?: string | null
  paramDefId?: number | null
  paramKey?: string | null
  paramName?: string | null
  currentValue?: string | null
  suggestedValue?: string | null
  description?: string | null
  contact?: string | null
  /** OPEN / RESOLVED / IGNORED */
  status: string
  handlerNote?: string | null
  createdAt?: string
  updatedAt?: string
}

/** 访客凭编号查询反馈进度的对外视图，不含提交内容 */
export interface FeedbackStatus {
  id: number
  /** OPEN / RESOLVED / IGNORED */
  status: string
  createdAt?: string
  updatedAt?: string
}

/** 后台侧栏徽标计数汇总 */
export interface AdminBadges {
  openFeedback: number
}

/** 单个参数的全库覆盖情况（数据健康度） */
export interface DataHealthParamCoverage {
  paramDefId: number
  paramKey: string
  paramName: string
  groupName?: string | null
  deviceCount: number
}

/** 缺参机型排行项：浏览量越高、缺参越多越优先补录 */
export interface DataHealthMissingDevice {
  id: number
  name: string
  brandId?: number | null
  brandName?: string | null
  seriesId?: number | null
  seriesName?: string | null
  status: number
  releaseDate?: string | null
  pv30d?: number | null
  haveParams: number
  totalParams: number
}

/** 数据健康度总览（T85） */
export interface DataHealth {
  totalDevices: number
  publishedDevices: number
  devicesWithParams: number
  /** 带参率 0~1 */
  coverageRate: number
  enabledParamDefs: number
  approvedLast30d: number
  openFeedback: number
  /** 参数覆盖排行：升序，覆盖最少的在前 */
  paramCoverage: DataHealthParamCoverage[]
  /** 缺参机型排行：近 30 天 PV 倒序 */
  missingDevices: DataHealthMissingDevice[]
}

export interface OperationLog {
  id: number
  adminId?: number | null
  adminName?: string | null
  action: string
  targetType?: string | null
  targetId?: number | null
  detail?: string | null
  createdAt?: string
}

/** QUEUED / RUNNING / PENDING_REVIEW / APPROVED / DISCARDED / FAILED */
export type AgentTaskStatus =
  | 'QUEUED'
  | 'RUNNING'
  | 'PENDING_REVIEW'
  | 'APPROVED'
  | 'DISCARDED'
  | 'FAILED'

export interface AgentTask {
  id: number
  modelName: string
  brandName?: string | null
  aiModelId?: number | null
  /** DEVICE_DETAIL（单机型参数抓取）/ SERIES_DISCOVERY（系列机型发现），缺省按 DEVICE_DETAIL 处理 */
  taskType?: string | null
  /** 来源订阅 id（T88，可空）：非空表示由系列订阅调度自动创建 */
  subscriptionId?: number | null
  status: AgentTaskStatus
  sourceUrl?: string | null
  result?: string | null
  errorMsg?: string | null
  durationMs?: number | null
  createdAt?: string
  updatedAt?: string
}

/** 系列订阅抓取配置（T88） */
export interface SeriesSubscription {
  id: number
  seriesId: number
  /** 抓取周期（月） */
  intervalMonths: number
  aiModelId?: number | null
  /** 1 启用 0 暂停 */
  enabled: number
  lastRunAt?: string | null
}

export interface SeriesSubscriptionSaveRequest {
  intervalMonths: number
  aiModelId?: number | null
  enabled: boolean
}

export interface AgentTaskLog {
  id: number
  taskId: number
  /** INFO / WARN / ERROR */
  level: string
  message: string
  createdAt?: string
}

export interface AiConfig {
  id: number
  name: string
  provider: string
  baseUrl?: string | null
  /** 响应中恒为 null，仅用 apiKeySet 表示是否已配置 */
  apiKey?: null
  apiKeySet?: boolean
  enabled: number
  createdAt?: string
  updatedAt?: string
}

export interface AiModel {
  id: number
  configId: number
  configName?: string
  provider?: string
  baseUrl?: string
  model: string
  temperature?: number | null
  isDefault: number
  enabled: number
}

/** AI 连通性测试结果（请求层失败也正常返回，success=false 携带错误详情） */
export interface ConnectivityTest {
  success: boolean
  latencyMs: number
  model: string
  reply?: string | null
  error?: string | null
}

/** 采集搜索源配置（Key 只回是否已配置） */
export interface SearchConfig {
  /** 启用的引擎（tavily/searxng/serper，按尝试顺序），空表示系统默认链 */
  engines: string[]
  tavilyApiKeySet: boolean
  searxngBaseUrl?: string | null
  searxngTokenSet: boolean
  serperApiKeySet: boolean
  updatedAt?: string
}

export interface SearchConfigSaveRequest {
  engines: string[]
  tavilyApiKey?: string
  searxngBaseUrl?: string
  searxngToken?: string
  serperApiKey?: string
}

export interface SearchTestResult {
  success: boolean
  engine: string
  count: number
  latencyMs: number
  error?: string | null
  /** Tavily 用量摘要（仅启用 Tavily 且查询成功时返回） */
  tavilyUsage?: string | null
}

// ---------- 访客端 VO ----------
export interface DeviceSuggest {
  id: number
  name: string
  brandId: number
  brandName?: string | null
  thumbnail?: string | null
}

export interface ParamMeta {
  paramDefId?: number | null
  sourceUrl?: string | null
  confidence?: number | null
  updatedAt?: string | null
}

/** 机型详情附带的最小系列信息，未归类为 null */
export interface SeriesBrief {
  id: number
  name: string
}

export interface DeviceDetail {
  device: Device
  brand?: Brand | null
  series?: SeriesBrief | null
  seriesDevices: EvolutionDevice[]
  params: Record<string, string>
  paramMeta: Record<string, ParamMeta>
  lastUpdated?: string | null
  variants: DeviceVariant[]
}

export interface CompareParam {
  paramDefId: number
  paramKey: string
  paramName: string
  paramType: string
  unit?: string | null
  values: string[]
  same: boolean
}

export interface CompareGroup {
  groupId: number
  groupName: string
  params: CompareParam[]
}

export interface CompareDevice {
  id: number
  name: string
  brandId: number
  brandName?: string | null
  thumbnail?: string | null
  releaseDate?: string | null
  variants: DeviceVariant[]
  minPrice?: number | null
}

export interface CompareResult {
  devices: CompareDevice[]
  groups: CompareGroup[]
}

export interface SeriesItem extends DeviceSeries {
  brandName: string
  deviceCount: number
  startYear?: number | null
  endYear?: number | null
}

export interface EvolutionDevice {
  id: number
  name: string
  thumbnail?: string | null
  releaseDate?: string | null
  launchPrice?: number | null
}

export interface EvolutionPoint {
  deviceId: number
  raw?: string | null
  value?: number | null
  conflict: boolean
}

export interface EvolutionParam {
  paramDefId: number
  paramKey?: string
  paramName: string
  unit?: string | null
  numeric: boolean
  points: EvolutionPoint[]
  missingCount: number
}

export interface EvolutionResult {
  seriesId: number
  seriesName: string
  brandId: number
  brandName: string
  devices: EvolutionDevice[]
  params: EvolutionParam[]
}

/** 同代横比：参数在某年代表机型上的单点取值，口径同 EvolutionPoint */
export interface GenerationParam {
  paramDefId: number
  paramName: string
  unit?: string | null
  numeric: boolean
  value?: number | null
  raw?: string | null
  conflict: boolean
}

/** 同代横比中的一列：某系列在指定年份的代表机型，当年无新机时 device 为 null */
export interface GenerationSeries {
  seriesId: number
  seriesName: string
  device: EvolutionDevice | null
  params: GenerationParam[]
}

export interface GenerationMatrix {
  brandId: number
  brandName: string
  year: number
  items: GenerationSeries[]
}

/** 系列总览迷你趋势的单点：x 轴按发布年份 */
export interface SeriesTrendPoint {
  year: number
  value: number
  deviceName: string
}

/** 系列总览卡片：收录规模沿用公开口径（只计已上架），趋势固定屏幕尺寸 + 起售价 */
export interface SeriesOverviewItem {
  id: number
  brandId: number
  brandName?: string | null
  name: string
  deviceCount: number
  startYear?: number | null
  endYear?: number | null
  latest?: EvolutionDevice | null
  screenTrend: SeriesTrendPoint[]
  priceTrend: SeriesTrendPoint[]
}

export interface SeriesOverviewResult {
  items: SeriesOverviewItem[]
  unassignedCount: number
}

/** 对比差异摘要与世代回顾文案共用 */
export interface InsightResult {
  available: boolean
  failed?: boolean
  cached?: boolean
  summary?: string | null
  points?: string[]
  generatedAt?: string | null
}

/** 社媒文案：按平台风格生成，可直接发布 */
export interface SocialCopyResult {
  available: boolean
  failed?: boolean
  cached?: boolean
  style?: string
  title?: string | null
  summary?: string | null
  points?: string[]
  tags?: string[]
  generatedAt?: string | null
}

export interface UnassignedDevice {
  id: number
  name: string
  brandId: number
  brandName: string
  releaseDate?: string | null
  thumbnail?: string | null
}

// ---------- 后台 VO ----------
export interface PendingData {
  id: number
  taskId?: number | null
  modelName: string
  brandName?: string | null
  sourceUrl?: string | null
  status: string
  createdAt?: string
  /** AI 预审风险等级（T86，仅待审核状态计算）：HIGH / MEDIUM / NORMAL / PASS */
  riskLevel?: string | null
}

export interface PendingDataDetail extends PendingData {
  params: Record<string, string>
  paramConfidence?: Record<string, number>
  paramWarnings?: Record<string, string>
  /** 官方产品图 URL（T90，可空）：审核页预览，通过后异步下载本地化 */
  imageUrl?: string | null
  variants?: DeviceVariant[]
  paramNames?: Record<string, string>
  newDevice?: boolean
  existingDeviceId?: number
  existingParams?: Record<string, string>
  existingVariants?: DeviceVariant[]
}

export interface DeviceImportRow {
  rowNo?: number
  name?: string
  brandName?: string
  seriesName?: string
  releaseDate?: string
  params?: Record<string, string>
  errors?: string[]
  warnings?: string[]
}

export interface DeviceImportResult {
  dryRun: boolean
  total: number
  validCount: number
  invalidCount: number
  createdCount: number
  rows: DeviceImportRow[]
}

export interface LoginResponse {
  token: string
  userId?: number
  username: string
  nickname?: string | null
  role: string
  mustChangePassword?: boolean
}

export interface AuthMe {
  adminId?: number | null
  username?: string | null
  role?: string | null
  mustChangePassword?: boolean
  authenticated: boolean
}

export interface BackupFile {
  fileName: string
  size?: number | null
  createdAt?: string | null
}

export interface BackupOverview {
  enabled: boolean
  dir: string
  keep: number
  nextRunAt?: string | null
  backups: BackupFile[]
}

export interface RetentionOverview {
  enabled: boolean
  cron: string
  taskDays: number
  pendingDays: number
  operationLogDays: number
  /** 机型浏览量按天聚合的保留天数 */
  viewDailyDays: number
  nextRunAt?: string | null
  counts: Record<string, number>
}

export interface UploadResult {
  url: string
  width?: number | null
  height?: number | null
  size: number
  contentType: string
}

// ---------- 请求体 ----------
export interface LoginRequest {
  username: string
  password: string
}

export interface AgentTaskCreateRequest {
  modelName: string
  brandName?: string
  aiModelId?: number | null
  /** 缺省 DEVICE_DETAIL；SERIES_DISCOVERY 时 brandName 必填 */
  taskType?: string
}

/** 系列发现得到的机型项（existing 表示库中已有同品牌同名机型） */
export interface SeriesDiscoveredModel {
  name: string
  releaseDate?: string | null
  existing?: boolean
  deviceId?: number | null
}

export interface SeriesDiscoveryDetail {
  taskId: number
  brandName?: string | null
  seriesName?: string | null
  sourceUrl?: string | null
  status?: string | null
  models: SeriesDiscoveredModel[]
}

export interface SeriesCreateResult {
  created: number
  skippedExisting: string[]
  deviceIds: number[]
}

export interface BrandSaveRequest extends Partial<Omit<Brand, 'id'>> {
  /** 表单里常用 null 表示「新建」，与 undefined 等价 */
  id?: number | null
}

export interface SeriesSaveRequest extends Partial<Omit<DeviceSeries, 'id' | 'brandName' | 'deviceCount' | 'startYear' | 'endYear'>> {
  id?: number | null
}

export interface DeviceSaveRequest {
  id?: number | null
  brandId: number
  seriesId?: number | null
  name: string
  releaseDate?: string | null
  thumbnail?: string | null
  description?: string | null
  status?: number
  params?: Record<string, string> | null
  paramConfidence?: Record<string, number> | null
  variants?: DeviceVariant[] | null
}

export interface ApproveRequest {
  brandId: number
  name: string
  releaseDate?: string | null
  thumbnail?: string | null
  description?: string | null
  params?: Record<string, string>
  variants?: DeviceVariant[] | null
}

export interface UserCreateRequest {
  username: string
  password: string
  nickname?: string
  role: string
}

export interface FeedbackSubmitRequest {
  deviceId: number
  paramDefId?: number
  paramKey?: string
  currentValue?: string
  suggestedValue?: string
  description?: string
  contact?: string
}

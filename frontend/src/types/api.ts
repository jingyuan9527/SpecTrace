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

/** 系列内机型清单（后台移动系列用） */
export interface SeriesDevice {
  id: number
  name: string
  brandId: number
  seriesId?: number | null
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
  /** 对外随机公开码，访客凭它查询进度 */
  publicCode?: string | null
  createdAt?: string
  updatedAt?: string
}

/** 访客凭编号查询反馈进度的对外视图，不含提交内容 */
export interface FeedbackStatus {
  /** 随机公开码，即提交回执编号 */
  code: string
  /** OPEN / RESOLVED / IGNORED */
  status: string
  createdAt?: string
  updatedAt?: string
}

/** 后台侧栏徽标计数汇总 */
export interface AdminBadges {
  openFeedback: number
  /** 待审核抓取数据条数（T100） */
  pendingReview: number
  /** 最老一条待审核已等待的小时数，无积压为 0 */
  pendingOldestHours: number
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

/** 采集监控的单日数据点（只统计终态任务） */
export interface CollectDayPoint {
  date: string
  total: number
  approved: number
  discarded: number
  failed: number
}

/** 单个 AI 模型的采集表现（T101） */
export interface ModelStat {
  aiModelId?: number | null
  /** 供应商连接名 / 模型名 */
  label: string
  total: number
  approved: number
  failed: number
  successRate?: number | null
  avgMs: number
  p95Ms: number
  nearTimeout: number
}

/** 单日全站浏览量（T103） */
export interface DailyPv {
  date: string
  pv: number
}

/** 窗口期 PV TOP 机型（T103） */
export interface TopDevicePv {
  deviceId: number
  deviceName: string
  pv: number
}

/** 单个 Caffeine 缓存的命中统计（T105） */
export interface CacheStat {
  name: string
  hitCount: number
  missCount: number
  /** 命中率 0~1，无请求为 null */
  hitRate?: number | null
  estimatedSize: number
  evictionCount: number
}

/** 被报错最多的参数统计行（T106） */
export interface FeedbackParamStat {
  paramName: string
  paramKey: string
  count: number
}

/** 数据质量观测（T106） */
export interface QualityMetrics {
  staleDevices: number
  staleThresholdDays: number
  feedbackOpen: number
  /** 待处理反馈平均滞留小时数，无待处理为 null */
  feedbackAvgAgeHours?: number | null
  feedbackResolved7d: number
  topErrorParams: FeedbackParamStat[]
}

/** 技术运行时指标（T105 起；T107 扩充 SQLite/磁盘/JVM/备份） */
export interface RuntimeMetrics {
  caches: CacheStat[]
  /** 限流拒绝计数（key 为归一化路径），重启清零 */
  rateLimitRejected: Record<string, number>
  sqlitePath: string
  sqliteSizeBytes: number
  diskTotalBytes: number
  diskFreeBytes: number
  heapUsedBytes: number
  heapMaxBytes: number
  threads: number
  /** 最近一次备份耗时（毫秒），重启后为 0 */
  lastBackupDurationMs: number
  /** 产品图待下载数量 */
  imagePending: number
}

/** 访客流量监控（T103） */
export interface TrafficMetrics {
  windowDays: number
  daily: DailyPv[]
  weekPv: number
  lastWeekPv: number
  /** 周环比（正负小数），上周为 0 为 null */
  weekOverWeek?: number | null
  topDevices: TopDevicePv[]
  compareInitiated: number
}

/** 搜索词统计行（T102） */
export interface SearchKeywordStat {
  keyword: string
  searchCount: number
  zeroCount: number
}

/** 搜索需求信号（T102） */
export interface SearchMetrics {
  windowDays: number
  totalSearches: number
  zeroSearches: number
  /** 空结果率 0~1，无搜索为 null */
  zeroRate?: number | null
  topKeywords: SearchKeywordStat[]
  zeroTop: SearchKeywordStat[]
}

/** 采集链路监控（T99） */
export interface CollectMetrics {
  windowDays: number
  total: number
  approved: number
  discarded: number
  failed: number
  queued: number
  running: number
  pendingReview: number
  /** 成功率 = approved/(approved+failed)，无终态任务为 null */
  successRate?: number | null
  p50Ms: number
  p95Ms: number
  nearTimeout: number
  imagePending: number
  daily: CollectDayPoint[]
  /** 按模型维度的表现排行（T101） */
  modelStats: ModelStat[]
}

/** 模型调用统计的供应商筛选项：对话连接或绘图供应商 */
export interface ModelCallProvider {
  kind: 'CHAT' | 'IMAGE'
  id: number
  name: string
  /** 绘图供应商只有一个模型且没有 ai_model.id */
  models: Array<{ id?: number | null; name: string }>
}

export interface ModelCallDimensions {
  providers: ModelCallProvider[]
}

/** 模型调用趋势的单个时间桶 */
export interface ModelCallPoint {
  bucket: string
  total: number
  success: number
  failed: number
  avgMs: number
  p95Ms: number
  maxMs: number
}

export interface ModelCallFailure {
  reason: string
  label: string
  count: number
  /** count / failed，无失败时后端不返回 */
  share?: number | null
}

/** 模型调用统计读数（延迟口径包含失败调用） */
export interface ModelCallMetrics {
  start: string
  end: string
  bucketUnit: 'MINUTE' | 'HOUR' | 'DAY'
  total: number
  success: number
  failed: number
  successRate?: number | null
  avgMs: number
  p50Ms: number
  p95Ms: number
  p99Ms: number
  maxMs: number
  series: ModelCallPoint[]
  failures: ModelCallFailure[]
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
  /** 对应待审核记录 id（后端列表补充）：待审核任务可直达抓取审核页 */
  pendingId?: number | null
  /** 失败任务是否已有同品牌同名机型的审核通过记录（后端补充，true 时不再提供重试） */
  approved?: boolean | null
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

/** AI 绘图供应商（OpenAI 兼容 /images/generations） */
export interface AiImageProvider {
  id: number
  name: string
  baseUrl: string
  /** 响应中恒为 null，仅用 apiKeySet 表示是否已配置 */
  apiKey?: null
  apiKeySet?: boolean
  model: string
  /** 出图尺寸，如 1024x1536 */
  size: string
  quality?: string | null
  enabled: number
  isDefault: number
  createdAt?: string
  updatedAt?: string
}

/** AI 绘图提示词模板：内置模板不可删除，正文用 {{变量}} 占位 */
export interface AiImagePrompt {
  id: number
  name: string
  content: string
  builtin: number
  isDefault: number
  enabled: number
  createdAt?: string
  updatedAt?: string
}

/** 访客端可选的提示词模板 */
export interface AiImagePromptOption {
  id: number
  name: string
  content: string
  isDefault: number
}

/** AI 绘图历史：成功与失败都记录，promptText 为渲染后的快照 */
export interface AiImageJob {
  id: number
  providerId?: number | null
  providerName?: string | null
  model?: string | null
  promptId?: number | null
  promptName?: string | null
  promptText?: string | null
  deviceNames?: string | null
  paramNames?: string | null
  /** SUCCESS / FAILED */
  status: string
  imageUrl?: string | null
  errorMsg?: string | null
  durationMs?: number | null
  createdAt?: string
}

export interface AiImageStatus {
  available: boolean
  enabled: boolean
  reason?: string | null
}

export interface AiImageGenerateRequest {
  seriesId: number
  /** 机型范围（系列内子集），不传表示全部 */
  deviceIds?: number[]
  /** 参数范围，不传由服务端选择 */
  paramDefIds?: number[]
  /** 本次使用的模板，不传用默认模板 */
  promptId?: number
  title?: string
}

export interface AiImageGenerateResult {
  jobId?: number
  imageUrl: string
  promptId: number
  promptName: string
  promptText: string
  model: string
  durationMs?: number
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

/** Tavily 本月用量（后端已算好剩余积分，前端不再做减法） */
export interface TavilyUsage {
  configured: boolean
  plan?: string | null
  used?: number | null
  /** 本月额度，null 表示不限量或未返回 */
  limit?: number | null
  remaining?: number | null
  keyUsed?: number | null
  keyLimit?: number | null
  /** 用量数据获取时间（命中缓存时为上次真实查询时间） */
  fetchedAt?: string | null
  cached: boolean
  error?: string | null
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
  /** paramKey -> 待核实（OPEN）反馈数，无反馈的参数不出现 */
  paramFeedback?: Record<string, number>
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
  /** 访客搜索词聚合的保留天数 */
  searchLogDays: number
  /** 模型调用明细的保留天数 */
  aiCallDays: number
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

/** MCP 工具授权范围，与后端 McpScope 一一对应 */
export type McpScope =
  | 'device:read'
  | 'device:write'
  | 'brand:read'
  | 'brand:write'
  | 'series:read'
  | 'series:write'
  | 'param:read'
  | 'param:write'
  | 'review:read'
  | 'review:write'
  | 'feedback:read'
  | 'feedback:write'
  | 'stats:read'

/** READ_ONLY / EDITOR / ADMIN；仅决定默认 scope，实际鉴权以 scopes 为准 */
export type McpAgentRole = 'READ_ONLY' | 'EDITOR' | 'ADMIN'

export interface McpAgent {
  id: number
  name: string
  description?: string | null
  /** 明文 Key 的前 15 位，用于辨认凭据 */
  apiKeyPrefix: string
  role: McpAgentRole
  scopes: McpScope[]
  status: number
  callCount: number
  lastUsedAt?: string | null
  createdAt?: string
}

/** apiKey 只在创建/轮换响应里出现一次 */
export interface McpAgentCreated {
  agent: McpAgent
  apiKey: string
  warning: string
}

export interface McpAgentCreateRequest {
  name: string
  description?: string
  role: McpAgentRole
  scopes?: McpScope[]
  status?: number
}

export interface McpAgentUpdateRequest {
  name?: string
  description?: string
  role?: McpAgentRole
  scopes?: McpScope[]
  status?: number
}

/** SUCCESS 成功 / ERROR 业务或参数失败 / DENIED 缺少 scope */
export type McpCallStatus = 'SUCCESS' | 'ERROR' | 'DENIED'

export interface McpCallLog {
  id: number
  agentId?: number | null
  agentName?: string | null
  toolName: string
  /** 已脱敏并截断的入参 JSON */
  argumentsJson?: string | null
  status: McpCallStatus
  errorCode?: string | null
  errorMessage?: string | null
  durationMs: number
  clientName?: string | null
  sessionId?: string | null
  createdAt?: string
}

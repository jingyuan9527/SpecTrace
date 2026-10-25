import request from './request'
import type {
  AgentTask,
  AgentTaskCreateRequest,
  AgentTaskLog,
  AdminBadges,
  AiConfig,
  AiModel,
  ApproveRequest,
  AuthMe,
  BackupOverview,
  BackupFile,
  BatchResult,
  Brand,
  BrandSaveRequest,
  CollectMetrics,
  CompareResult,
  ConnectivityTest,
  DataHealth,
  Device,
  DeviceDetail,
  DeviceImportResult,
  DeviceSaveRequest,
  DeviceSuggest,
  DeviceSeries,
  DeviceVariant,
  EvolutionResult,
  Feedback,
  FeedbackStatus,
  FeedbackSubmitRequest,
  GenerationMatrix,
  InsightResult,
  SocialCopyResult,
  LoginRequest,
  LoginResponse,
  OperationLog,
  PageResult,
  ParamDef,
  ParamGroup,
  PendingData,
  PendingDataDetail,
  RetentionOverview,
  SearchMetrics,
  SeriesSuggestion,
  RuntimeMetrics,
  SearchConfig,
  TrafficMetrics,
  SearchConfigSaveRequest,
  SearchTestResult,
  SeriesCreateResult,
  SeriesDiscoveryDetail,
  SeriesItem,
  SeriesMissingDevice,
  SeriesOverviewResult,
  SeriesSaveRequest,
  SeriesSubscription,
  SeriesSubscriptionSaveRequest,
  UnassignedDevice,
  UploadResult,
  UserCreateRequest,
  AdminUser,
} from '@/types/api'

export type { PageResult } from '@/types/api'

// 拦截器已把 R<code,message,data> 解包为 data，因此第二泛型直接写业务返回类型

// ---------- 访客端 ----------
export const publicApi = {
  brands: () => request.get<unknown, Brand[]>('/public/brands'),
  paramGroups: () => request.get<unknown, ParamGroup[]>('/public/param-groups'),
  paramDefs: () => request.get<unknown, ParamDef[]>('/public/param-defs'),
  devices: (params: Record<string, unknown>) =>
    request.get<unknown, PageResult<Device>>('/public/devices', { params }),
  hot: (limit = 8) => request.get<unknown, Device[]>('/public/devices/hot', { params: { limit } }),
  suggest: (keyword: string, limit = 8) =>
    request.get<unknown, DeviceSuggest[]>('/public/devices/suggest', { params: { keyword, limit } }),
  deviceDetail: (id: number) => request.get<unknown, DeviceDetail>(`/public/devices/${id}`),
  compare: (ids: number[]) =>
    request.get<unknown, CompareResult>('/public/compare', { params: { ids: ids.join(',') } }),
  compareInsight: (ids: number[]) =>
    request.get<unknown, InsightResult>('/public/compare/insight', { params: { ids: ids.join(',') } }),
  generateCompareInsight: (ids: number[]) =>
    request.post<unknown, InsightResult>('/public/compare/insight', { ids }),
  years: (brandId?: number) => request.get<unknown, number[]>('/public/years', { params: { brandId } }),
  series: (brandId?: number) => request.get<unknown, SeriesItem[]>('/public/series', { params: { brandId } }),
  seriesOverview: (brandId?: number) =>
    request.get<unknown, SeriesOverviewResult>('/public/series/overview', { params: { brandId } }),
  evolution: (seriesId: number, paramDefIds: number[]) =>
    request.get<unknown, EvolutionResult>(`/public/series/${seriesId}/evolution`, {
      params: { paramDefIds: paramDefIds.join(',') },
    }),
  generation: (brandId: number, year: number, paramDefIds: number[]) =>
    request.get<unknown, GenerationMatrix>('/public/series/generation', {
      params: { brandId, year, paramDefIds: paramDefIds.join(',') },
    }),
  seriesInsight: (seriesId: number, deviceIds: number[], paramDefIds: number[]) =>
    request.get<unknown, InsightResult>(`/public/series/${seriesId}/insight`, {
      params: { deviceIds: deviceIds.join(','), paramDefIds: paramDefIds.join(',') },
    }),
  generateSeriesInsight: (seriesId: number, deviceIds: number[], paramDefIds: number[]) =>
    request.post<unknown, InsightResult>(`/public/series/${seriesId}/insight`, { deviceIds, paramDefIds }),
  generateSocialCopy: (
    seriesId: number,
    body: { deviceIds?: number[]; paramDefIds?: number[]; style?: string },
  ) => request.post<unknown, SocialCopyResult>(`/public/series/${seriesId}/social-copy`, body),
  submitFeedback: (data: FeedbackSubmitRequest) => request.post<unknown, number>('/public/feedback', data),
  feedbackStatus: (id: number) => request.get<unknown, FeedbackStatus>(`/public/feedback/${id}`),
}

// ---------- 鉴权 ----------
export const authApi = {
  login: (data: LoginRequest) => request.post<unknown, LoginResponse>('/auth/login', data),
  me: () => request.get<unknown, AuthMe>('/auth/me', { skipAuthRedirect: true }),
  changePassword: (data: { oldPassword: string; newPassword: string }) =>
    request.put<unknown, void>('/auth/password', data),
}

// ---------- 后台 ----------
export const adminApi = {
  // 侧栏徽标计数（当前仅纠错反馈待处理数）
  badges: () => request.get<unknown, AdminBadges>('/admin/badges'),
  // 数据健康度看板（brandId/seriesId 只作用于缺参机型排行）
  dataHealth: (params?: Record<string, unknown>) =>
    request.get<unknown, DataHealth>('/admin/data-health', { params }),
  // 运行监控（T99 起）：采集 / 流量 / 搜索 / 质量
  collectMetrics: (windowDays = 30) =>
    request.get<unknown, CollectMetrics>('/admin/metrics/collect', { params: { windowDays } }),
  searchMetrics: (windowDays = 30) =>
    request.get<unknown, SearchMetrics>('/admin/metrics/search', { params: { windowDays } }),
  trafficMetrics: (windowDays = 30) =>
    request.get<unknown, TrafficMetrics>('/admin/metrics/traffic', { params: { windowDays } }),
  runtimeMetrics: () => request.get<unknown, RuntimeMetrics>('/admin/metrics/runtime'),

  brands: () => request.get<unknown, Brand[]>('/admin/brands'),
  saveBrand: (data: BrandSaveRequest) =>
    data.id ? request.put<unknown, Brand>(`/admin/brands/${data.id}`, data) : request.post<unknown, Brand>('/admin/brands', data),
  deleteBrand: (id: number) => request.delete<unknown, void>(`/admin/brands/${id}`),

  series: (brandId?: number) => request.get<unknown, DeviceSeries[]>('/admin/series', { params: { brandId } }),
  seriesUnassigned: (brandId?: number) =>
    request.get<unknown, UnassignedDevice[]>('/admin/series/unassigned', { params: { brandId } }),
  assignSeries: (deviceIds: number[], seriesId?: number) =>
    request.post<unknown, number>('/admin/series/assign', { deviceIds, seriesId }),
  mergeSeries: (id: number, targetId: number) =>
    request.post<unknown, number>(`/admin/series/${id}/merge`, { targetId }),
  seriesMissingDevices: (id: number) =>
    request.get<unknown, SeriesMissingDevice[]>(`/admin/series/${id}/devices-without-params`),
  // 系列内参数陈旧的机型（无参数或超过 days 天未更新），供「重抓全系列」
  seriesStaleDevices: (id: number, days = 180) =>
    request.get<unknown, SeriesMissingDevice[]>(`/admin/series/${id}/stale-devices`, { params: { days } }),
  // 系列订阅抓取（T88）：到期自动创建系列发现任务
  seriesSubscription: (id: number) =>
    request.get<unknown, SeriesSubscription | null>(`/admin/series/${id}/subscription`),
  saveSeriesSubscription: (id: number, data: SeriesSubscriptionSaveRequest) =>
    request.put<unknown, SeriesSubscription>(`/admin/series/${id}/subscription`, data),
  saveSeries: (data: SeriesSaveRequest) =>
    data.id
      ? request.put<unknown, DeviceSeries>(`/admin/series/${data.id}`, data)
      : request.post<unknown, DeviceSeries>('/admin/series', data),
  deleteSeries: (id: number) => request.delete<unknown, void>(`/admin/series/${id}`),

  paramGroups: () => request.get<unknown, ParamGroup[]>('/admin/param-groups'),
  saveParamGroup: (data: Omit<Partial<ParamGroup>, 'id'> & { id?: number | null }) =>
    data.id ? request.put<unknown, ParamGroup>(`/admin/param-groups/${data.id}`, data) : request.post<unknown, ParamGroup>('/admin/param-groups', data),
  deleteParamGroup: (id: number) => request.delete<unknown, void>(`/admin/param-groups/${id}`),

  paramDefs: (groupId?: number) => request.get<unknown, ParamDef[]>('/admin/param-defs', { params: { groupId } }),
  saveParamDef: (data: Omit<Partial<ParamDef>, 'id'> & { id?: number | null }) =>
    data.id ? request.put<unknown, ParamDef>(`/admin/param-defs/${data.id}`, data) : request.post<unknown, ParamDef>('/admin/param-defs', data),
  deleteParamDef: (id: number) => request.delete<unknown, void>(`/admin/param-defs/${id}`),

  devices: (params: Record<string, unknown>) =>
    request.get<unknown, PageResult<Device>>('/admin/devices', { params }),
  deviceImportTemplate: () =>
    request.get<unknown, Blob>('/admin/devices/import/template', { responseType: 'blob' }),
  importDevices: (file: File, dryRun: boolean) => {
    const form = new FormData()
    form.append('file', file)
    return request.post<unknown, DeviceImportResult>('/admin/devices/import', form, { params: { dryRun } })
  },
  deviceDetail: (id: number) => request.get<unknown, DeviceDetail>(`/admin/devices/${id}`),
  // 机型列表 seriesId=0 约定为「未归类」筛选
  suggestSeries: (brandId: number) =>
    request.get<unknown, SeriesSuggestion[]>('/admin/series/suggest', { params: { brandId } }),
  saveDevice: (data: DeviceSaveRequest) =>
    data.id ? request.put<unknown, Device>(`/admin/devices/${data.id}`, data) : request.post<unknown, Device>('/admin/devices', data),
  deleteDevice: (id: number) => request.delete<unknown, void>(`/admin/devices/${id}`),
  batchDeviceStatus: (ids: number[], status: number) =>
    request.put<unknown, number>('/admin/devices/batch-status', { ids, status }),

  agentTasks: (params: Record<string, unknown>) =>
    request.get<unknown, PageResult<AgentTask>>('/admin/agent-tasks', { params }),
  createAgentTask: (data: AgentTaskCreateRequest) => request.post<unknown, AgentTask>('/admin/agent-tasks', data),
  retryAgentTask: (id: number, aiModelId?: number) =>
    request.post<unknown, AgentTask>(`/admin/agent-tasks/${id}/retry`, undefined, {
      params: aiModelId ? { aiModelId } : undefined,
    }),
  // 过程日志按 id 增量拉取，afterId 传已拿到的最后一条
  agentTaskLogs: (id: number, afterId?: number) =>
    request.get<unknown, AgentTaskLog[]>(`/admin/agent-tasks/${id}/logs`, { params: { afterId } }),
  agentTaskDiscovery: (id: number) =>
    request.get<unknown, SeriesDiscoveryDetail>(`/admin/agent-tasks/${id}/discovery`),
  discardDiscovery: (id: number) => request.post<unknown, void>(`/admin/agent-tasks/${id}/discard-discovery`),
  createDiscoveryDevices: (id: number, names: string[]) =>
    request.post<unknown, SeriesCreateResult>(`/admin/agent-tasks/${id}/create-devices`, { names }),

  // AI 连接（供应商）
  aiConfigs: () => request.get<unknown, AiConfig[]>('/admin/ai-configs'),
  saveAiConfig: (data: Omit<Partial<AiConfig>, 'id' | 'apiKey'> & { id?: number | null; apiKey?: string }) =>
    data.id ? request.put<unknown, AiConfig>(`/admin/ai-configs/${data.id}`, data) : request.post<unknown, AiConfig>('/admin/ai-configs', data),
  deleteAiConfig: (id: number) => request.delete<unknown, void>(`/admin/ai-configs/${id}`),
  // 连接下的模型
  aiConfigModels: (configId: number) => request.get<unknown, AiModel[]>(`/admin/ai-configs/${configId}/models`),
  pullRemoteModels: (configId: number) => request.get<unknown, string[]>(`/admin/ai-configs/${configId}/remote-models`),
  // 连通性测试：model 不传时后端用该连接首个启用模型
  testAiConfig: (configId: number, model?: string) =>
    request.post<unknown, ConnectivityTest>(`/admin/ai-configs/${configId}/test`, undefined, {
      params: model ? { model } : undefined,
    }),
  importAiModels: (configId: number, data: { models: string[]; temperature?: number }) =>
    request.post<unknown, void>(`/admin/ai-configs/${configId}/import-models`, data),
  // 采集搜索源（多引擎链式切换，Key 留空表示不修改；test 可传未保存的表单值）
  getSearchConfig: () => request.get<unknown, SearchConfig>('/admin/search-config'),
  saveSearchConfig: (data: SearchConfigSaveRequest) =>
    request.put<unknown, SearchConfig>('/admin/search-config', data),
  testSearchConfig: (data?: Partial<SearchConfigSaveRequest>) =>
    request.post<unknown, SearchTestResult>('/admin/search-config/test', data ?? {}),
  // AI 模型
  aiModels: () => request.get<unknown, AiModel[]>('/admin/ai-models'),
  setDefaultAiModel: (id: number) => request.post<unknown, void>(`/admin/ai-models/${id}/default`),
  updateAiModel: (id: number, params: { temperature?: number; enabled?: number }) =>
    request.put<unknown, void>(`/admin/ai-models/${id}`, null, { params }),
  deleteAiModel: (id: number) => request.delete<unknown, void>(`/admin/ai-models/${id}`),

  pendingData: (params: Record<string, unknown>) =>
    request.get<unknown, PageResult<PendingData>>('/admin/pending-data', { params }),
  pendingDetail: (id: number) => request.get<unknown, PendingDataDetail>(`/admin/pending-data/${id}`),
  approvePending: (id: number, data: ApproveRequest) => request.post<unknown, number>(`/admin/pending-data/${id}/approve`, data),
  discardPending: (id: number) => request.post<unknown, void>(`/admin/pending-data/${id}/discard`),
  batchApprovePending: (ids: number[]) => request.post<unknown, BatchResult>('/admin/pending-data/batch-approve', { ids }),
  batchDiscardPending: (ids: number[]) => request.post<unknown, BatchResult>('/admin/pending-data/batch-discard', { ids }),

  users: (params: Record<string, unknown>) => request.get<unknown, PageResult<AdminUser>>('/admin/users', { params }),
  createUser: (data: UserCreateRequest) => request.post<unknown, AdminUser>('/admin/users', data),
  updateUserRole: (id: number, role: string) => request.put<unknown, void>(`/admin/users/${id}/role`, null, { params: { role } }),
  toggleUserStatus: (id: number) => request.put<unknown, void>(`/admin/users/${id}/status`),
  resetUserPassword: (id: number, password: string) =>
    request.put<unknown, void>(`/admin/users/${id}/password`, null, { params: { password } }),
  deleteUser: (id: number) => request.delete<unknown, void>(`/admin/users/${id}`),

  feedback: (params: Record<string, unknown>) =>
    request.get<unknown, PageResult<Feedback>>('/admin/feedback', { params }),
  updateFeedbackStatus: (id: number, status: string, note?: string) =>
    request.put<unknown, void>(`/admin/feedback/${id}/status`, null, { params: { status, note } }),

  logs: (params: Record<string, unknown>) => request.get<unknown, PageResult<OperationLog>>('/admin/logs', { params }),

  backups: () => request.get<unknown, BackupOverview>('/admin/maintenance/backups'),
  triggerBackup: () => request.post<unknown, BackupFile>('/admin/maintenance/backups'),
  retention: () => request.get<unknown, RetentionOverview>('/admin/maintenance/retention'),
  triggerRetentionCleanup: () => request.post<unknown, Record<string, number>>('/admin/maintenance/retention/cleanup'),

  upload: (file: File, onProgress?: (percent: number) => void) => {
    const form = new FormData()
    form.append('file', file)
    return request.post<unknown, UploadResult>('/admin/files/upload', form, {
      onUploadProgress: (event) => {
        if (onProgress && event.total) {
          onProgress(Math.min(100, Math.round((event.loaded / event.total) * 100)))
        }
      },
    })
  },
}

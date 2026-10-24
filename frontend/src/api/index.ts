import request from './request'

export interface PageResult<T> {
  total: number
  current: number
  size: number
  records: T[]
}

export interface UploadResult {
  url: string
  width?: number | null
  height?: number | null
  size: number
  contentType: string
}

// ---------- 访客端 ----------
export const publicApi = {
  brands: () => request.get<any[], any[]>('/public/brands'),
  paramGroups: () => request.get<any[], any[]>('/public/param-groups'),
  paramDefs: () => request.get<any[], any[]>('/public/param-defs'),
  devices: (params: Record<string, unknown>) => request.get<PageResult<any>, PageResult<any>>('/public/devices', { params }),
  hot: (limit = 8) => request.get<any[], any[]>('/public/devices/hot', { params: { limit } }),
  suggest: (keyword: string, limit = 8) =>
    request.get<any[], any[]>('/public/devices/suggest', { params: { keyword, limit } }),
  deviceDetail: (id: number) => request.get<any, any>(`/public/devices/${id}`),
  compare: (ids: number[]) => request.get<any, any>('/public/compare', { params: { ids: ids.join(',') } }),
  compareInsight: (ids: number[]) =>
    request.get<any, any>('/public/compare/insight', { params: { ids: ids.join(',') } }),
  generateCompareInsight: (ids: number[]) =>
    request.post<any, any>('/public/compare/insight', { ids }),
  history: (brandIds: number[], paramDefId: number) =>
    request.get<any, any>('/public/history', { params: { brandIds: brandIds.join(','), paramDefId } }),
  years: (brandId?: number) => request.get<number[], number[]>('/public/years', { params: { brandId } }),
  series: (brandId?: number) => request.get<any[], any[]>('/public/series', { params: { brandId } }),
  evolution: (seriesId: number, paramDefIds: number[]) =>
    request.get<any, any>(`/public/series/${seriesId}/evolution`, {
      params: { paramDefIds: paramDefIds.join(',') },
    }),
  seriesInsight: (seriesId: number, deviceIds: number[], paramDefIds: number[]) =>
    request.get<any, any>(`/public/series/${seriesId}/insight`, {
      params: { deviceIds: deviceIds.join(','), paramDefIds: paramDefIds.join(',') },
    }),
  generateSeriesInsight: (seriesId: number, deviceIds: number[], paramDefIds: number[]) =>
    request.post<any, any>(`/public/series/${seriesId}/insight`, { deviceIds, paramDefIds }),
  submitFeedback: (data: {
    deviceId: number
    paramDefId?: number
    paramKey?: string
    currentValue?: string
    suggestedValue?: string
    description?: string
    contact?: string
  }) => request.post<number, number>('/public/feedback', data),
}

// ---------- 鉴权 ----------
export const authApi = {
  login: (data: { username: string; password: string }) => request.post<any, any>('/auth/login', data),
  me: () => request.get<any, any>('/auth/me', { skipAuthRedirect: true }),
  changePassword: (data: { oldPassword: string; newPassword: string }) =>
    request.put<any, any>('/auth/password', data),
}

// ---------- 后台 ----------
export const adminApi = {
  brands: () => request.get<any[], any[]>('/admin/brands'),
  saveBrand: (data: any) => (data.id ? request.put(`/admin/brands/${data.id}`, data) : request.post('/admin/brands', data)),
  deleteBrand: (id: number) => request.delete(`/admin/brands/${id}`),

  series: (brandId?: number) => request.get<any[], any[]>('/admin/series', { params: { brandId } }),
  seriesUnassigned: (brandId?: number) =>
    request.get<any[], any[]>('/admin/series/unassigned', { params: { brandId } }),
  assignSeries: (deviceIds: number[], seriesId?: number) =>
    request.post<any, any>('/admin/series/assign', { deviceIds, seriesId }),
  mergeSeries: (id: number, targetId: number) =>
    request.post<any, any>(`/admin/series/${id}/merge`, { targetId }),
  saveSeries: (data: any) =>
    data.id
      ? request.put<any, any>(`/admin/series/${data.id}`, data)
      : request.post<any, any>('/admin/series', data),
  deleteSeries: (id: number) => request.delete(`/admin/series/${id}`),

  paramGroups: () => request.get<any[], any[]>('/admin/param-groups'),
  saveParamGroup: (data: any) =>
    data.id ? request.put(`/admin/param-groups/${data.id}`, data) : request.post('/admin/param-groups', data),
  deleteParamGroup: (id: number) => request.delete(`/admin/param-groups/${id}`),

  paramDefs: (groupId?: number) => request.get<any[], any[]>('/admin/param-defs', { params: { groupId } }),
  saveParamDef: (data: any) =>
    data.id ? request.put(`/admin/param-defs/${data.id}`, data) : request.post('/admin/param-defs', data),
  deleteParamDef: (id: number) => request.delete(`/admin/param-defs/${id}`),

  devices: (params: Record<string, unknown>) => request.get<PageResult<any>, PageResult<any>>('/admin/devices', { params }),
  deviceImportTemplate: () =>
    request.get<Blob, Blob>('/admin/devices/import/template', { responseType: 'blob' }),
  importDevices: (file: File, dryRun: boolean) => {
    const form = new FormData()
    form.append('file', file)
    return request.post<any, any>('/admin/devices/import', form, { params: { dryRun } })
  },
  deviceDetail: (id: number) => request.get<any, any>(`/admin/devices/${id}`),
  saveDevice: (data: any) =>
    data.id ? request.put(`/admin/devices/${data.id}`, data) : request.post('/admin/devices', data),
  deleteDevice: (id: number) => request.delete(`/admin/devices/${id}`),

  agentTasks: (params: Record<string, unknown>) =>
    request.get<PageResult<any>, PageResult<any>>('/admin/agent-tasks', { params }),
  createAgentTask: (data: { modelName: string; brandName?: string; aiModelId?: number | null }) =>
    request.post('/admin/agent-tasks', data),
  retryAgentTask: (id: number) => request.post<any, any>(`/admin/agent-tasks/${id}/retry`),
  // 过程日志按 id 增量拉取，afterId 传已拿到的最后一条
  agentTaskLogs: (id: number, afterId?: number) =>
    request.get<any[], any[]>(`/admin/agent-tasks/${id}/logs`, { params: { afterId } }),

  // AI 连接（供应商）
  aiConfigs: () => request.get<any[], any[]>('/admin/ai-configs'),
  saveAiConfig: (data: any) =>
    data.id ? request.put(`/admin/ai-configs/${data.id}`, data) : request.post('/admin/ai-configs', data),
  deleteAiConfig: (id: number) => request.delete(`/admin/ai-configs/${id}`),
  // 连接下的模型
  aiConfigModels: (configId: number) => request.get<any[], any[]>(`/admin/ai-configs/${configId}/models`),
  pullRemoteModels: (configId: number) => request.get<string[], string[]>(`/admin/ai-configs/${configId}/remote-models`),
  importAiModels: (configId: number, data: { models: string[]; temperature?: number }) =>
    request.post(`/admin/ai-configs/${configId}/import-models`, data),
  // AI 模型
  aiModels: () => request.get<any[], any[]>('/admin/ai-models'),
  setDefaultAiModel: (id: number) => request.post(`/admin/ai-models/${id}/default`),
  updateAiModel: (id: number, params: { temperature?: number; enabled?: number }) =>
    request.put(`/admin/ai-models/${id}`, null, { params }),
  deleteAiModel: (id: number) => request.delete(`/admin/ai-models/${id}`),

  pendingData: (params: Record<string, unknown>) =>
    request.get<PageResult<any>, PageResult<any>>('/admin/pending-data', { params }),
  pendingDetail: (id: number) => request.get<any, any>(`/admin/pending-data/${id}`),
  approvePending: (id: number, data: any) => request.post(`/admin/pending-data/${id}/approve`, data),
  discardPending: (id: number) => request.post(`/admin/pending-data/${id}/discard`),
  batchApprovePending: (ids: number[]) => request.post<any, any>('/admin/pending-data/batch-approve', { ids }),
  batchDiscardPending: (ids: number[]) => request.post<any, any>('/admin/pending-data/batch-discard', { ids }),

  users: (params: Record<string, unknown>) => request.get<PageResult<any>, PageResult<any>>('/admin/users', { params }),
  createUser: (data: any) => request.post('/admin/users', data),
  updateUserRole: (id: number, role: string) => request.put(`/admin/users/${id}/role`, null, { params: { role } }),
  toggleUserStatus: (id: number) => request.put(`/admin/users/${id}/status`),
  resetUserPassword: (id: number, password: string) =>
    request.put(`/admin/users/${id}/password`, null, { params: { password } }),
  deleteUser: (id: number) => request.delete(`/admin/users/${id}`),

  feedback: (params: Record<string, unknown>) =>
    request.get<PageResult<any>, PageResult<any>>('/admin/feedback', { params }),
  updateFeedbackStatus: (id: number, status: string, note?: string) =>
    request.put(`/admin/feedback/${id}/status`, null, { params: { status, note } }),

  logs: (params: Record<string, unknown>) => request.get<PageResult<any>, PageResult<any>>('/admin/logs', { params }),

  backups: () => request.get<any, any>('/admin/maintenance/backups'),
  triggerBackup: () => request.post<any, any>('/admin/maintenance/backups'),

  upload: (file: File, onProgress?: (percent: number) => void) => {
    const form = new FormData()
    form.append('file', file)
    return request.post<UploadResult, UploadResult>('/admin/files/upload', form, {
      onUploadProgress: (event) => {
        if (onProgress && event.total) {
          onProgress(Math.min(100, Math.round((event.loaded / event.total) * 100)))
        }
      },
    })
  },
}

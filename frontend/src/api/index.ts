import request from './request'

export interface PageResult<T> {
  total: number
  current: number
  size: number
  records: T[]
}

// ---------- 访客端 ----------
export const publicApi = {
  brands: () => request.get<any[], any[]>('/public/brands'),
  paramGroups: () => request.get<any[], any[]>('/public/param-groups'),
  paramDefs: () => request.get<any[], any[]>('/public/param-defs'),
  devices: (params: Record<string, unknown>) => request.get<PageResult<any>, PageResult<any>>('/public/devices', { params }),
  hot: (limit = 8) => request.get<any[], any[]>('/public/devices/hot', { params: { limit } }),
  deviceDetail: (id: number) => request.get<any, any>(`/public/devices/${id}`),
  compare: (ids: number[]) => request.get<any, any>('/public/compare', { params: { ids: ids.join(',') } }),
  history: (brandId: number, paramDefId: number) =>
    request.get<any[], any[]>('/public/history', { params: { brandId, paramDefId } }),
  years: (brandId?: number) => request.get<number[], number[]>('/public/years', { params: { brandId } }),
}

// ---------- 鉴权 ----------
export const authApi = {
  login: (data: { username: string; password: string }) => request.post<any, any>('/auth/login', data),
}

// ---------- 后台 ----------
export const adminApi = {
  brands: () => request.get<any[], any[]>('/admin/brands'),
  saveBrand: (data: any) => (data.id ? request.put(`/admin/brands/${data.id}`, data) : request.post('/admin/brands', data)),
  deleteBrand: (id: number) => request.delete(`/admin/brands/${id}`),

  paramGroups: () => request.get<any[], any[]>('/admin/param-groups'),
  saveParamGroup: (data: any) =>
    data.id ? request.put(`/admin/param-groups/${data.id}`, data) : request.post('/admin/param-groups', data),
  deleteParamGroup: (id: number) => request.delete(`/admin/param-groups/${id}`),

  paramDefs: (groupId?: number) => request.get<any[], any[]>('/admin/param-defs', { params: { groupId } }),
  saveParamDef: (data: any) =>
    data.id ? request.put(`/admin/param-defs/${data.id}`, data) : request.post('/admin/param-defs', data),
  deleteParamDef: (id: number) => request.delete(`/admin/param-defs/${id}`),

  devices: (params: Record<string, unknown>) => request.get<PageResult<any>, PageResult<any>>('/admin/devices', { params }),
  deviceDetail: (id: number) => request.get<any, any>(`/admin/devices/${id}`),
  saveDevice: (data: any) =>
    data.id ? request.put(`/admin/devices/${data.id}`, data) : request.post('/admin/devices', data),
  deleteDevice: (id: number) => request.delete(`/admin/devices/${id}`),

  agentTasks: (params: Record<string, unknown>) =>
    request.get<PageResult<any>, PageResult<any>>('/admin/agent-tasks', { params }),
  createAgentTask: (data: { modelName: string; brandName?: string; aiModelId?: number | null }) =>
    request.post('/admin/agent-tasks', data),

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

  users: (params: Record<string, unknown>) => request.get<PageResult<any>, PageResult<any>>('/admin/users', { params }),
  createUser: (data: any) => request.post('/admin/users', data),
  updateUserRole: (id: number, role: string) => request.put(`/admin/users/${id}/role`, null, { params: { role } }),
  toggleUserStatus: (id: number) => request.put(`/admin/users/${id}/status`),
  resetUserPassword: (id: number, password: string) =>
    request.put(`/admin/users/${id}/password`, null, { params: { password } }),
  deleteUser: (id: number) => request.delete(`/admin/users/${id}`),

  logs: (params: Record<string, unknown>) => request.get<PageResult<any>, PageResult<any>>('/admin/logs', { params }),

  upload: (file: File) => {
    const form = new FormData()
    form.append('file', file)
    return request.post<string, string>('/admin/files/upload', form)
  },
}

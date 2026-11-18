// 后台管理 API。所有请求带 Bearer token，服务端会校验 ADMIN 角色。
import { getToken } from './auth'

const BASE = '/api/admin'

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const token = getToken()
  const res = await fetch(`${BASE}${path}`, {
    ...options,
    headers: {
      ...(options.body ? { 'Content-Type': 'application/json' } : {}),
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...((options.headers as Record<string, string>) || {})
    }
  })
  const data = (await res.json().catch(() => ({}))) as { message?: string } & T
  if (!res.ok) throw new Error(data.message || `请求失败（${res.status}）`)
  return data
}

export interface AdminUser {
  id: number
  name: string
  email: string
  phone?: string | null
  role: 'ADMIN' | 'USER'
  enabled: boolean
  faceBound: boolean
  createdAt: string
}

export interface AdminOverview {
  users: number
  admins: number
  operations: number
  knowledge: number
  visionConfigured: boolean
  visionModel: string
  memoryUsedMb: number
  memoryMaxMb: number
  uptimeMinutes: number
  diskFreeMb: number
  diskTotalMb: number
}

export interface SettingItem {
  key: string
  label: string
  secret: boolean
  value: string
  configured: boolean
  overridden: boolean
  hint: string
}

export const fetchOverview = () => request<AdminOverview>('/overview')

export const fetchUsers = (keyword = '', page = 1, size = 50) =>
  request<{ total: number; page: number; size: number; items: AdminUser[] }>(
    `/users?keyword=${encodeURIComponent(keyword)}&page=${page}&size=${size}`
  )

export const updateUser = (id: number, patch: Record<string, unknown>) =>
  request<AdminUser>(`/users/${id}`, { method: 'PATCH', body: JSON.stringify(patch) })

export const resetPassword = (id: number) =>
  request<{ email: string; password: string }>(`/users/${id}/reset-password`, {
    method: 'POST',
    body: JSON.stringify({})
  })

export const deleteUser = (id: number) => request<{ deleted: number }>(`/users/${id}`, { method: 'DELETE' })

export const fetchSettings = () =>
  request<{ items: SettingItem[]; visionConfigured: boolean }>('/settings')

export const saveSettings = (values: Record<string, string>) =>
  request<{ saved: boolean; visionConfigured: boolean }>('/settings', {
    method: 'PUT',
    body: JSON.stringify(values)
  })

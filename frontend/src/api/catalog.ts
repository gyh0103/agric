// 作物与病害字典。识别面板、问答侧栏与后台共用。
import { getToken } from './auth'

const BASE = '/api'

export interface CropProfile {
  id: string
  name: string
  aliases?: string | null
  category?: string | null
  stages?: string | null
  visionTasks?: string | null
  notes?: string | null
  enabled?: boolean
  sortOrder?: number
}

export interface DiseaseProfile {
  id?: number
  crop: string
  name: string
  aliases?: string | null
  symptoms?: string | null
  advice?: string | null
  severity?: string | null
  enabled?: boolean
  sortOrder?: number
}

async function request<T>(path: string): Promise<T> {
  const token = getToken()
  const res = await fetch(`${BASE}${path}`, {
    headers: token ? { Authorization: `Bearer ${token}` } : {}
  })
  const data = (await res.json().catch(() => ({}))) as { message?: string } & T
  if (!res.ok) throw new Error(data.message || `请求失败（${res.status}）`)
  return data
}

export const fetchCatalogCrops = () => request<CropProfile[]>('/catalog/crops')
export const fetchCatalogDiseases = (crop = '') =>
  request<DiseaseProfile[]>(`/catalog/diseases?crop=${encodeURIComponent(crop)}`)

/** 解析 "leaf:叶片症状,head:穗部检测" 格式的识别任务 */
export function parseVisionTasks(value?: string | null): { id: string; label: string }[] {
  if (!value) return []
  return value
    .split(/[,，]/)
    .map(item => item.trim())
    .filter(Boolean)
    .map(item => {
      const [id, label] = item.split(':')
      return { id: (id || '').trim(), label: (label || id || '').trim() }
    })
    .filter(item => item.id)
}

/** 用一个地块的作物名去匹配作物档案（支持名称与别名包含匹配） */
export function matchCropProfile(profiles: CropProfile[], plotCrop?: string | null): CropProfile | undefined {
  if (!plotCrop) return undefined
  const text = plotCrop.trim()
  if (!text) return undefined
  return profiles.find(profile => {
    const names = [profile.name, ...(profile.aliases || '').split(/[,，]/)]
      .map(name => name.trim())
      .filter(Boolean)
    return names.some(name => text.includes(name))
  })
}

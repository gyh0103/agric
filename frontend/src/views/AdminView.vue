<script setup lang="ts">
import { computed, onMounted, onBeforeUnmount, reactive, ref } from 'vue'
import {
  createAdminCrop,
  createAdminDisease,
  deleteAdminCrop,
  deleteAdminDisease,
  deleteUser,
  fetchAdminCrops,
  fetchAdminDiseases,
  fetchAdminPlots,
  fetchOverview,
  fetchSettings,
  fetchUsers,
  resetPassword,
  saveSettings,
  updateAdminCrop,
  updateAdminDisease,
  updateAdminPlot,
  updateUser,
  type AdminCrop,
  type AdminDisease,
  type AdminOverview,
  type AdminPlot,
  type AdminUser,
  type SettingItem
} from '@/api/admin'
import {
  createFarmDevice,
  deleteFarmDevice,
  fetchDashboard,
  fetchObservationHistory,
  setDeviceEnabled,
  updateFarmDevice
} from '@/api/dashboard'
import { fetchLiveWeather, type LiveWeather } from '@/api/weather'
import type { DashboardSnapshot, DeviceInput, DeviceRecord } from '@/types'

type Tab = 'overview' | 'users' | 'settings' | 'crops' | 'diseases' | 'plots' | 'devices' | 'weather'

const tab = ref<Tab>('overview')
const overview = ref<AdminOverview | null>(null)
const users = ref<AdminUser[]>([])
const total = ref(0)
const keyword = ref('')
const loading = ref(false)
const notice = ref('')
const error = ref('')

const settingsItems = ref<SettingItem[]>([])
const form = reactive<Record<string, string>>({})
const apiKeyInput = ref('')
const saving = ref(false)

const visionItem = computed(() => settingsItems.value.find(item => item.key === 'vision.cloud.api-key'))

// ---------- 作物档案 ----------
const crops = ref<AdminCrop[]>([])
const cropForm = reactive<AdminCrop>(emptyCrop())
const cropEditing = ref(false)
const cropIdLocked = ref(false)

function emptyCrop(): AdminCrop {
  return {
    id: '', name: '', aliases: '', category: '大田', stages: '', visionTasks: '',
    notes: '', enabled: true, sortOrder: crops.value.length * 10 + 10
  }
}

function editCrop(item?: AdminCrop) {
  Object.assign(cropForm, item
    ? {
        ...item,
        aliases: item.aliases || '', category: item.category || '大田', stages: item.stages || '',
        visionTasks: item.visionTasks || '', notes: item.notes || ''
      }
    : emptyCrop())
  cropEditing.value = !!item
  cropIdLocked.value = !!item
}

async function loadCrops() {
  loading.value = true
  try {
    crops.value = await fetchAdminCrops()
  } catch (e) {
    fail(e)
  } finally {
    loading.value = false
  }
}

async function submitCrop() {
  if (!cropForm.id.trim() || !cropForm.name.trim()) {
    fail(new Error('请填写作物编号和名称'))
    return
  }
  try {
    const payload = { ...cropForm, id: cropForm.id.trim(), name: cropForm.name.trim() }
    if (cropEditing.value) await updateAdminCrop(payload.id, payload)
    else await createAdminCrop(payload)
    flash('作物档案已保存')
    editCrop()
    await loadCrops()
  } catch (e) {
    fail(e)
  }
}

async function removeCrop(item: AdminCrop) {
  if (!window.confirm(`删除作物「${item.name}」后，识别面板将不再显示它，确定继续吗？`)) return
  try {
    await deleteAdminCrop(item.id)
    flash('作物已删除')
    await loadCrops()
  } catch (e) {
    fail(e)
  }
}

// ---------- 病害知识库 ----------
const diseaseList = ref<AdminDisease[]>([])
const diseaseFilter = ref('')
const diseaseForm = reactive<AdminDisease>(emptyDisease())
const diseaseEditing = ref(false)

function emptyDisease(): AdminDisease {
  return {
    crop: '', name: '', aliases: '', symptoms: '', advice: '',
    severity: '中度', enabled: true, sortOrder: diseaseList.value.length * 10 + 10
  }
}

const diseaseRows = computed(() =>
  diseaseFilter.value
    ? diseaseList.value.filter(item => item.crop === diseaseFilter.value)
    : diseaseList.value
)

const diseaseCrops = computed(() => Array.from(new Set(diseaseList.value.map(item => item.crop))))

function editDisease(item?: AdminDisease) {
  Object.assign(diseaseForm, item
    ? {
        ...item,
        aliases: item.aliases || '', symptoms: item.symptoms || '',
        advice: item.advice || '', severity: item.severity || '中度'
      }
    : emptyDisease())
  diseaseEditing.value = !!item
}

async function loadDiseases() {
  loading.value = true
  try {
    diseaseList.value = await fetchAdminDiseases()
  } catch (e) {
    fail(e)
  } finally {
    loading.value = false
  }
}

async function submitDisease() {
  if (!diseaseForm.crop.trim() || !diseaseForm.name.trim()) {
    fail(new Error('请填写作物和病害名称'))
    return
  }
  try {
    const payload = { ...diseaseForm, crop: diseaseForm.crop.trim(), name: diseaseForm.name.trim() }
    if (diseaseEditing.value && payload.id) await updateAdminDisease(payload.id, payload)
    else await createAdminDisease({ ...payload, id: undefined })
    flash('病害条目已保存')
    editDisease()
    await loadDiseases()
    await loadOverview()
  } catch (e) {
    fail(e)
  }
}

async function removeDisease(item: AdminDisease) {
  if (!item.id) return
  if (!window.confirm(`删除「${item.crop} · ${item.name}」，确定继续吗？`)) return
  try {
    await deleteAdminDisease(item.id)
    flash('病害条目已删除')
    await loadDiseases()
    await loadOverview()
  } catch (e) {
    fail(e)
  }
}

// ---------- 地块 ----------
const plots = ref<AdminPlot[]>([])
const plotForm = reactive({ id: '', name: '', crop: '', area: '', growthStage: '', environmentSummary: '', health: 90 })
const plotEditing = ref('')

async function loadPlots() {
  loading.value = true
  try {
    plots.value = await fetchAdminPlots()
  } catch (e) {
    fail(e)
  } finally {
    loading.value = false
  }
}

function editPlot(item: AdminPlot) {
  plotEditing.value = item.id
  plotForm.id = item.id
  plotForm.name = item.name || ''
  plotForm.crop = item.crop || ''
  plotForm.area = item.area || ''
  plotForm.growthStage = item.growthStage || ''
  plotForm.environmentSummary = item.environmentSummary || ''
  plotForm.health = item.health ?? 90
}

async function submitPlot() {
  try {
    await updateAdminPlot(plotForm.id, {
      name: plotForm.name,
      crop: plotForm.crop,
      area: plotForm.area,
      growthStage: plotForm.growthStage,
      environmentSummary: plotForm.environmentSummary,
      health: Number(plotForm.health)
    })
    flash('地块信息已更新，刷新工作台即可看到')
    plotEditing.value = ''
    await loadPlots()
  } catch (e) {
    fail(e)
  }
}

// ---------- 设备 ----------
const devices = ref<DeviceRecord[]>([])
const deviceForm = reactive<DeviceInput>({
  id: '', entityId: '', name: '', category: 'sensor', location: '', online: true, enabled: true, currentValue: ''
})
const deviceEditing = ref(false)

function editDevice(item?: DeviceRecord) {
  Object.assign(deviceForm, item
    ? { id: item.id, entityId: item.entityId, name: item.name, category: item.category, location: item.location, online: item.online, enabled: item.enabled, currentValue: item.value }
    : { id: '', entityId: '', name: '', category: 'sensor', location: '', online: true, enabled: true, currentValue: '' })
  deviceEditing.value = !!item
}

async function loadDevices() {
  loading.value = true
  try {
    const snapshot = await fetchDashboard('zhangye-01')
    devices.value = snapshot.devices
  } catch (e) {
    fail(e)
  } finally {
    loading.value = false
  }
}

async function submitDevice() {
  if (!deviceForm.id.trim() || !deviceForm.entityId.trim() || !deviceForm.name.trim()) {
    fail(new Error('请填写设备编号、实体编号和名称'))
    return
  }
  try {
    const payload: DeviceInput = { ...deviceForm }
    if (deviceEditing.value) await updateFarmDevice(payload.entityId, payload, 'zhangye-01')
    else await createFarmDevice(payload, 'zhangye-01')
    flash('设备已保存')
    editDevice()
    await loadDevices()
  } catch (e) {
    fail(e)
  }
}

async function toggleDevice(item: DeviceRecord) {
  try {
    await setDeviceEnabled(item.entityId, !item.enabled, 'zhangye-01')
    await loadDevices()
  } catch (e) {
    fail(e)
  }
}

async function removeDevice(item: DeviceRecord) {
  if (!window.confirm(`删除设备「${item.name}」，确定继续吗？`)) return
  try {
    await deleteFarmDevice(item.entityId, 'zhangye-01')
    flash('设备已删除')
    await loadDevices()
  } catch (e) {
    fail(e)
  }
}

// ---------- 天气与环境 ----------
const weather = ref<LiveWeather | null>(null)
const weatherError = ref('')
const metrics = ref<DashboardSnapshot['environmentMetrics']>([])
const alerts = ref<DashboardSnapshot['alerts']>([])
const irrigationUnits = ref<DashboardSnapshot['irrigationUnits']>([])
const farmSummary = ref<DashboardSnapshot['summary'] | null>(null)
const metricCharts = ref<Record<string, { metricKey: string; points: { label: string; value: number; unit: string }[] }>>({})
let weatherTimer: ReturnType<typeof setInterval> | undefined

// 天气位置设置（从系统配置迁到天气与环境页签）
const weatherLocForm = ref({ city: '', latitude: '', longitude: '' })
const weatherLocLoading = ref(false)
const weatherLocSaving = ref(false)

async function loadWeatherLocation() {
  weatherLocLoading.value = true
  try {
    const data = await fetchSettings()
    const read = (k: string) => data.items.find(i => i.key === k)?.value ?? ''
    weatherLocForm.value = {
      city: read('weather.city'),
      latitude: read('weather.latitude'),
      longitude: read('weather.longitude'),
    }
  } catch (e) {
    fail(e)
  } finally {
    weatherLocLoading.value = false
  }
}

async function saveWeatherLocation() {
  weatherLocSaving.value = true
  try {
    const lat = parseFloat(weatherLocForm.value.latitude.trim())
    const lon = parseFloat(weatherLocForm.value.longitude.trim())
    if (Number.isNaN(lat) || lat < 18 || lat > 54) throw new Error('纬度需在 18 ~ 54 之间')
    if (Number.isNaN(lon) || lon < 73 || lon > 135) throw new Error('经度需在 73 ~ 135 之间')
    await saveSettings({
      'weather.city': weatherLocForm.value.city.trim() || '张掖',
      'weather.latitude': String(lat),
      'weather.longitude': String(lon),
    })
    flash('天气位置已更新，立即刷新')
    await loadWeatherLocation()
    await loadWeather()
  } catch (e) {
    fail(e)
  } finally {
    weatherLocSaving.value = false
  }
}

// 常用河西走廊位置一键填充
const PRESET_CITIES: { city: string; lat: string; lon: string }[] = [
  { city: '张掖', lat: '38.9262', lon: '100.4484' },
  { city: '酒泉', lat: '39.7392', lon: '98.4941' },
  { city: '武威', lat: '37.9284', lon: '102.6397' },
  { city: '金昌', lat: '38.5157', lon: '102.1870' },
  { city: '嘉峪关', lat: '39.7726', lon: '98.2894' },
  { city: '敦煌', lat: '40.1531', lon: '94.6626' },
]
function applyPreset(c: typeof PRESET_CITIES[number]) {
  weatherLocForm.value = { city: c.city, latitude: c.lat, longitude: c.lon }
}

function weatherText(code: number) {
  // WMO weather interpretation codes
  const map: Record<number, string> = {
    0: '晴', 1: '大部晴朗', 2: '局部多云', 3: '阴天',
    45: '雾', 48: '冻雾',
    51: '小毛毛雨', 53: '毛毛雨', 55: '强毛毛雨',
    61: '小雨', 63: '中雨', 65: '大雨',
    71: '小雪', 73: '中雪', 75: '大雪',
    80: '阵雨', 81: '阵雨', 82: '强阵雨',
    95: '雷暴', 96: '雷暴伴冰雹', 99: '强雷暴伴冰雹'
  }
  return map[code] ?? `天气代码 ${code}`
}

async function loadWeather() {
  try {
    weather.value = await fetchLiveWeather()
    weatherError.value = ''
  } catch (e) {
    weatherError.value = (e as Error).message
  }
}

async function loadMetrics() {
  try {
    const snap = await fetchDashboard('zhangye-01')
    metrics.value = snap.environmentMetrics || []
    alerts.value = snap.alerts || []
    irrigationUnits.value = snap.irrigationUnits || []
    farmSummary.value = snap.summary || null
    // 拉每个指标最近 12 个观测点，用于迷你曲线
    const topKeys = (snap.environmentMetrics || []).slice(0, 6).map(m => m.key)
    const results = await Promise.all(topKeys.map(async key => {
      try {
        const hist = await fetchObservationHistory(key, 'zhangye-01')
        return [key, { metricKey: key, points: hist.points.slice(-12).map(p => ({ label: p.label, value: p.value, unit: p.unit })) }] as const
      } catch { return [key, { metricKey: key, points: [] }] as const }
    }))
    metricCharts.value = Object.fromEntries(results)
  } catch (e) {
    fail(e)
  }
}

async function refreshAll() {
  loading.value = true
  try {
    await Promise.all([loadWeather(), loadMetrics()])
  } finally {
    loading.value = false
  }
}

async function startWeatherPolling() {
  await loadWeather()
  await loadMetrics()
  stopWeatherPolling()
  weatherTimer = setInterval(async () => {
    await loadWeather()
    if (tab.value === 'weather') await loadMetrics()
  }, 60000)
}

function stopWeatherPolling() {
  if (weatherTimer) { clearInterval(weatherTimer); weatherTimer = undefined }
}

function miniChartPoints(metricKey: string) {
  const series = metricCharts.value[metricKey]?.points
  if (!series || series.length < 2) return ''
  const vals = series.map(p => p.value)
  const vMin = Math.min(...vals), vMax = Math.max(...vals)
  const range = Math.max(vMax - vMin, 0.0001)
  return series.map((p, i) => {
    const x = (i / (series.length - 1)) * 116 + 2
    const y = 32 - ((p.value - vMin) / range) * 28
    return `${x.toFixed(1)},${y.toFixed(1)}`
  }).join(' ')
}

function flash(text: string) {
  notice.value = text
  error.value = ''
  window.setTimeout(() => (notice.value = ''), 4000)
}

function fail(e: unknown) {
  error.value = e instanceof Error ? e.message : String(e)
  notice.value = ''
}

async function loadOverview() {
  loading.value = true
  try {
    overview.value = await fetchOverview()
  } catch (e) {
    fail(e)
  } finally {
    loading.value = false
  }
}

async function loadUsers() {
  loading.value = true
  try {
    const data = await fetchUsers(keyword.value)
    users.value = data.items
    total.value = data.total
  } catch (e) {
    fail(e)
  } finally {
    loading.value = false
  }
}

async function loadSettings() {
  loading.value = true
  try {
    const data = await fetchSettings()
    settingsItems.value = data.items
    data.items.forEach(item => {
      form[item.key] = item.secret ? '' : item.value
    })
    apiKeyInput.value = ''
  } catch (e) {
    fail(e)
  } finally {
    loading.value = false
  }
}

async function switchTab(next: Tab) {
  tab.value = next
  if (next === 'overview') await loadOverview()
  if (next === 'users') await loadUsers()
  if (next === 'settings') await loadSettings()
  if (next === 'crops') await loadCrops()
  if (next === 'diseases') await loadDiseases()
  if (next === 'plots') await loadPlots()
  if (next === 'devices') await loadDevices()
  if (next === 'weather') {
    await Promise.all([loadWeather(), loadMetrics(), loadWeatherLocation()])
  }
}

async function toggleRole(user: AdminUser) {
  const nextRole = user.role === 'ADMIN' ? 'USER' : 'ADMIN'
  if (!window.confirm(`确定将 ${user.name}（${user.email}）设为 ${nextRole === 'ADMIN' ? '管理员' : '普通用户'} 吗？`)) return
  try {
    await updateUser(user.id, { role: nextRole })
    flash('角色已更新')
    await loadUsers()
  } catch (e) {
    fail(e)
  }
}

async function toggleEnabled(user: AdminUser) {
  const next = !user.enabled
  if (!window.confirm(`确定${next ? '启用' : '禁用'} ${user.name}（${user.email}）吗？`)) return
  try {
    await updateUser(user.id, { enabled: next })
    flash(next ? '账号已启用' : '账号已禁用')
    await loadUsers()
  } catch (e) {
    fail(e)
  }
}

async function doReset(user: AdminUser) {
  if (!window.confirm(`确定重置 ${user.name}（${user.email}）的密码吗？`)) return
  try {
    const data = await resetPassword(user.id)
    try {
      await navigator.clipboard.writeText(data.password)
      flash(`新密码已复制到剪贴板：${data.password}`)
    } catch {
      flash(`新密码：${data.password}`)
    }
  } catch (e) {
    fail(e)
  }
}

async function doDelete(user: AdminUser) {
  if (!window.confirm(`删除 ${user.name}（${user.email}）后不可恢复，确定继续吗？`)) return
  try {
    await deleteUser(user.id)
    flash('用户已删除')
    await loadUsers()
  } catch (e) {
    fail(e)
  }
}

async function submitSettings() {
  saving.value = true
  try {
    const payload: Record<string, string> = { ...form }
    if (apiKeyInput.value.trim()) payload['vision.cloud.api-key'] = apiKeyInput.value.trim()
    const result = await saveSettings(payload)
    flash(result.visionConfigured ? '配置已保存，云端识别已启用' : '配置已保存，但 API Key 仍为空')
    await loadSettings()
    await loadOverview()
  } catch (e) {
    fail(e)
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  await loadOverview()
  startWeatherPolling()
})
onBeforeUnmount(stopWeatherPolling)
</script>

<template>
  <div class="admin">
    <header class="top">
      <div class="brand">
        <span class="dot"></span>
        <div>
          <h1>后台管理</h1>
          <p>绿洲丰语 · 河西走廊智慧农业平台</p>
        </div>
      </div>
      <a class="back" href="/platform/workspaces/zhangye-01">返回工作台 ↗</a>
    </header>

    <nav class="tabs">
      <button :class="{ active: tab === 'overview' }" @click="switchTab('overview')">总览</button>
      <button :class="{ active: tab === 'users' }" @click="switchTab('users')">用户管理</button>
      <button :class="{ active: tab === 'crops' }" @click="switchTab('crops')">作物档案</button>
      <button :class="{ active: tab === 'diseases' }" @click="switchTab('diseases')">病害知识库</button>
      <button :class="{ active: tab === 'plots' }" @click="switchTab('plots')">地块</button>
      <button :class="{ active: tab === 'devices' }" @click="switchTab('devices')">设备</button>
      <button :class="{ active: tab === 'weather' }" @click="switchTab('weather')">天气与环境</button>
      <button :class="{ active: tab === 'settings' }" @click="switchTab('settings')">系统配置</button>
    </nav>

    <p v-if="notice" class="notice">{{ notice }}</p>
    <p v-if="error" class="error">{{ error }}</p>

    <section v-if="tab === 'overview'" class="panel">
      <div v-if="overview" class="cards">
        <article><span>用户总数</span><strong>{{ overview.users }}</strong><small>管理员 {{ overview.admins }} 人</small></article>
        <article><span>农事记录</span><strong>{{ overview.operations }}</strong><small>知识库 {{ overview.knowledge }} 条</small></article>
        <article><span>云端识别</span><strong :class="overview.visionConfigured ? 'ok' : 'warn'">{{ overview.visionConfigured ? '已启用' : '未配置' }}</strong><small>{{ overview.visionModel }}</small></article>
        <article><span>内存占用</span><strong>{{ overview.memoryUsedMb }} MB</strong><small>上限 {{ overview.memoryMaxMb }} MB</small></article>
        <article><span>磁盘可用</span><strong>{{ Math.round(overview.diskFreeMb / 1024) }} GB</strong><small>共 {{ Math.round(overview.diskTotalMb / 1024) }} GB</small></article>
        <article><span>运行时长</span><strong>{{ overview.uptimeMinutes }} 分钟</strong><small>进程内统计</small></article>
      </div>
      <p v-else class="placeholder">正在加载…</p>
    </section>

    <section v-if="tab === 'users'" class="panel">
      <div class="toolbar">
        <input v-model="keyword" placeholder="按姓名或邮箱搜索" @keyup.enter="loadUsers" />
        <button class="primary" :disabled="loading" @click="loadUsers">搜索</button>
        <span class="count">共 {{ total }} 人</span>
      </div>
      <div class="table-wrap">
        <table>
          <thead>
            <tr><th>姓名</th><th>邮箱</th><th>角色</th><th>状态</th><th>注册时间</th><th>操作</th></tr>
          </thead>
          <tbody>
            <tr v-for="user in users" :key="user.id">
              <td>{{ user.name }}</td>
              <td>{{ user.email }}</td>
              <td><span class="pill" :class="user.role === 'ADMIN' ? 'pill-admin' : ''">{{ user.role === 'ADMIN' ? '管理员' : '普通用户' }}</span></td>
              <td><span class="pill" :class="user.enabled ? 'pill-ok' : 'pill-off'">{{ user.enabled ? '正常' : '已禁用' }}</span></td>
              <td class="muted">{{ user.createdAt?.replace('T', ' ').slice(0, 16) }}</td>
              <td class="ops">
                <button @click="toggleRole(user)">{{ user.role === 'ADMIN' ? '降为普通' : '设为管理员' }}</button>
                <button @click="toggleEnabled(user)">{{ user.enabled ? '禁用' : '启用' }}</button>
                <button @click="doReset(user)">重置密码</button>
                <button class="danger" @click="doDelete(user)">删除</button>
              </td>
            </tr>
            <tr v-if="!users.length"><td colspan="6" class="placeholder">没有匹配的用户</td></tr>
          </tbody>
        </table>
      </div>
    </section>

    <section v-if="tab === 'settings'" class="panel">
      <div class="vision-head">
        <div>
          <h2>云端病害识别</h2>
          <p>兼容 OpenAI 协议，支持阿里云百炼 / 火山方舟 / 智谱。保存后立即生效，无需重启服务。</p>
        </div>
        <span class="pill" :class="visionItem?.configured ? 'pill-ok' : 'pill-off'">
          {{ visionItem?.configured ? '已启用' : '未配置' }}
        </span>
      </div>

      <div class="form">
        <label v-for="item in settingsItems" :key="item.key">
          <span>{{ item.label }}</span>
          <template v-if="item.secret">
            <input
              v-model="apiKeyInput"
              type="password"
              autocomplete="new-password"
              :placeholder="item.configured ? `已配置（${item.value}），留空则不修改` : '粘贴 API Key'"
            />
          </template>
          <input v-else v-model="form[item.key]" :placeholder="item.hint" />
          <small>{{ item.hint }}<em v-if="item.overridden"> · 已被后台覆盖</em></small>
        </label>
      </div>

      <div class="actions">
        <button class="primary" :disabled="saving" @click="submitSettings">{{ saving ? '保存中…' : '保存配置' }}</button>
        <button :disabled="saving" @click="loadSettings">放弃修改</button>
      </div>

      <div class="tips">
        <strong>常见配置</strong>
        <ul>
          <li>阿里云百炼：地址 <code>https://dashscope.aliyuncs.com/compatible-mode/v1</code>，模型 <code>qwen-vl-plus</code></li>
          <li>智谱：地址 <code>https://open.bigmodel.cn/api/paas/v4</code>，模型 <code>glm-4v-flash</code></li>
          <li>火山方舟：地址 <code>https://ark.cn-beijing.volces.com/api/v3</code>，模型填 Endpoint ID</li>
        </ul>
      </div>
    </section>

    <section v-if="tab === 'crops'" class="panel">
      <div class="vision-head">
        <div>
          <h2>作物档案</h2>
          <p>识别面板的作物与识别对象来自这里；别名用于匹配地块里填写的品种名（例如「郑单 958 制种」会匹配到制种玉米）。</p>
        </div>
        <button class="primary" @click="editCrop()">新增作物</button>
      </div>

      <div class="form-grid">
        <label><span>作物编号</span><input v-model="cropForm.id" :disabled="cropIdLocked" placeholder="如 seed-corn" /><small>字母、数字和连字符，保存后不可修改</small></label>
        <label><span>作物名称</span><input v-model="cropForm.name" placeholder="如 制种玉米" /></label>
        <label><span>别名（逗号分隔）</span><input v-model="cropForm.aliases" placeholder="玉米,郑单958" /></label>
        <label><span>类别</span><select v-model="cropForm.category"><option>大田</option><option>设施</option><option>果园</option></select></label>
        <label><span>生育期（逗号分隔）</span><input v-model="cropForm.stages" placeholder="苗期,拔节期,抽雄期" /></label>
        <label><span>识别任务（id:名称,...）</span><input v-model="cropForm.visionTasks" placeholder="leaf:叶片症状,head:穗部检测" /><small>留空表示暂不支持照片识别</small></label>
        <label class="wide"><span>栽培要点</span><textarea v-model="cropForm.notes" rows="2" placeholder="作为背景信息提供给问答与识别"></textarea></label>
        <label><span>排序</span><input v-model.number="cropForm.sortOrder" type="number" /></label>
        <label class="check"><input v-model="cropForm.enabled" type="checkbox" /> 启用</label>
      </div>

      <div class="actions">
        <button class="primary" @click="submitCrop">{{ cropEditing ? '保存修改' : '新增作物' }}</button>
        <button @click="editCrop()">清空表单</button>
      </div>

      <div class="table-wrap">
        <table>
          <thead><tr><th>编号</th><th>名称</th><th>别名</th><th>类别</th><th>识别任务</th><th>状态</th><th>操作</th></tr></thead>
          <tbody>
            <tr v-for="item in crops" :key="item.id">
              <td class="muted">{{ item.id }}</td>
              <td>{{ item.name }}</td>
              <td class="muted">{{ item.aliases }}</td>
              <td>{{ item.category }}</td>
              <td class="muted">{{ item.visionTasks || '—' }}</td>
              <td><span class="pill" :class="item.enabled ? 'pill-ok' : 'pill-off'">{{ item.enabled ? '启用' : '停用' }}</span></td>
              <td class="ops"><button @click="editCrop(item)">编辑</button><button class="danger" @click="removeCrop(item)">删除</button></td>
            </tr>
            <tr v-if="!crops.length"><td colspan="7" class="placeholder">还没有作物档案</td></tr>
          </tbody>
        </table>
      </div>
    </section>

    <section v-if="tab === 'diseases'" class="panel">
      <div class="vision-head">
        <div>
          <h2>病害知识库</h2>
          <p>识别照片时，该作物的病害条目会作为参考注入模型；智能问农也会引用这些名称。症状与防治建议请写得具体一些。</p>
        </div>
        <button class="primary" @click="editDisease()">新增病害</button>
      </div>

      <div class="form-grid">
        <label><span>作物</span><input v-model="diseaseForm.crop" placeholder="如 制种玉米" /></label>
        <label><span>病害名称</span><input v-model="diseaseForm.name" placeholder="如 玉米大斑病" /></label>
        <label><span>别名 / 代号</span><input v-model="diseaseForm.aliases" placeholder="leaf_blight,大斑" /></label>
        <label><span>严重度</span><select v-model="diseaseForm.severity"><option>轻度</option><option>中度</option><option>重度</option></select></label>
        <label class="wide"><span>典型症状</span><textarea v-model="diseaseForm.symptoms" rows="2" placeholder="照片上能看到的特征"></textarea></label>
        <label class="wide"><span>防治建议</span><textarea v-model="diseaseForm.advice" rows="2" placeholder="结合河西走廊气候与水肥条件的处置办法"></textarea></label>
        <label><span>排序</span><input v-model.number="diseaseForm.sortOrder" type="number" /></label>
        <label class="check"><input v-model="diseaseForm.enabled" type="checkbox" /> 启用</label>
      </div>

      <div class="actions">
        <button class="primary" @click="submitDisease">{{ diseaseEditing ? '保存修改' : '新增病害' }}</button>
        <button @click="editDisease()">清空表单</button>
      </div>

      <div class="toolbar">
        <select v-model="diseaseFilter">
          <option value="">全部作物</option>
          <option v-for="crop in diseaseCrops" :key="crop" :value="crop">{{ crop }}</option>
        </select>
        <span class="count">共 {{ diseaseRows.length }} 条</span>
      </div>

      <div class="table-wrap">
        <table>
          <thead><tr><th>作物</th><th>病害</th><th>严重度</th><th>典型症状</th><th>状态</th><th>操作</th></tr></thead>
          <tbody>
            <tr v-for="item in diseaseRows" :key="item.id">
              <td>{{ item.crop }}</td>
              <td>{{ item.name }}</td>
              <td>{{ item.severity }}</td>
              <td class="muted clamp">{{ item.symptoms }}</td>
              <td><span class="pill" :class="item.enabled ? 'pill-ok' : 'pill-off'">{{ item.enabled ? '启用' : '停用' }}</span></td>
              <td class="ops"><button @click="editDisease(item)">编辑</button><button class="danger" @click="removeDisease(item)">删除</button></td>
            </tr>
            <tr v-if="!diseaseRows.length"><td colspan="6" class="placeholder">没有匹配的病害条目</td></tr>
          </tbody>
        </table>
      </div>
    </section>

    <section v-if="tab === 'plots'" class="panel">
      <div class="vision-head">
        <div>
          <h2>地块</h2>
          <p>修改地块的作物、面积、生育期和健康度，保存后刷新工作台即可生效。地块边界与位置请在三维工作台里调整。</p>
        </div>
      </div>

      <div v-if="plotEditing" class="form-grid edit-block">
        <label><span>地块名称</span><input v-model="plotForm.name" /></label>
        <label><span>作物</span><input v-model="plotForm.crop" placeholder="如 郑单 958 制种" /></label>
        <label><span>面积</span><input v-model="plotForm.area" placeholder="如 280 亩" /></label>
        <label><span>生育期</span><input v-model="plotForm.growthStage" placeholder="如 拔节期" /></label>
        <label><span>环境摘要</span><input v-model="plotForm.environmentSummary" placeholder="如 土壤湿度 42%" /></label>
        <label><span>健康度</span><input v-model.number="plotForm.health" type="number" min="0" max="100" /></label>
        <div class="actions wide">
          <button class="primary" @click="submitPlot">保存地块</button>
          <button @click="plotEditing = ''">取消</button>
        </div>
      </div>

      <div class="table-wrap">
        <table>
          <thead><tr><th>编号</th><th>名称</th><th>类型</th><th>作物</th><th>面积</th><th>生育期</th><th>健康度</th><th>操作</th></tr></thead>
          <tbody>
            <tr v-for="item in plots" :key="item.id">
              <td class="muted">{{ item.id }}</td>
              <td>{{ item.name }}</td>
              <td>{{ item.type === 'greenhouse' ? '温室' : '大田' }}</td>
              <td>{{ item.crop || '—' }}</td>
              <td>{{ item.area || '—' }}</td>
              <td>{{ item.growthStage || '—' }}</td>
              <td>{{ item.health ?? '—' }}</td>
              <td class="ops"><button @click="editPlot(item)">编辑</button></td>
            </tr>
            <tr v-if="!plots.length"><td colspan="8" class="placeholder">没有地块数据</td></tr>
          </tbody>
        </table>
      </div>
    </section>

    <section v-if="tab === 'devices'" class="panel">
      <div class="vision-head">
        <div>
          <h2>设备</h2>
          <p>设备增删改会直接影响工作台的设施与监控模块。实体编号用于关联地块或场景对象，建议保持不变。</p>
        </div>
        <button class="primary" @click="editDevice()">新增设备</button>
      </div>

      <div class="form-grid">
        <label><span>设备编号</span><input v-model="deviceForm.id" placeholder="如 dev-weather" /></label>
        <label><span>实体编号</span><input v-model="deviceForm.entityId" placeholder="如 weather-01" /></label>
        <label><span>设备名称</span><input v-model="deviceForm.name" placeholder="如 田间气象站 01" /></label>
        <label><span>类别</span><select v-model="deviceForm.category"><option value="sensor">传感器</option><option value="actuator">执行设备</option><option value="camera">摄像头</option><option value="robot">机器人</option></select></label>
        <label><span>位置</span><input v-model="deviceForm.location" placeholder="如 西侧露天区" /></label>
        <label><span>当前读数</span><input v-model="deviceForm.currentValue" placeholder="如 28.3°C · 2.1m/s" /></label>
        <label class="check"><input v-model="deviceForm.online" type="checkbox" /> 在线</label>
        <label class="check"><input v-model="deviceForm.enabled" type="checkbox" /> 启用</label>
      </div>

      <div class="actions">
        <button class="primary" @click="submitDevice">{{ deviceEditing ? '保存修改' : '新增设备' }}</button>
        <button @click="editDevice()">清空表单</button>
      </div>

      <div class="table-wrap">
        <table>
          <thead><tr><th>名称</th><th>实体编号</th><th>类别</th><th>位置</th><th>读数</th><th>状态</th><th>操作</th></tr></thead>
          <tbody>
            <tr v-for="item in devices" :key="item.entityId">
              <td>{{ item.name }}</td>
              <td class="muted">{{ item.entityId }}</td>
              <td>{{ item.category }}</td>
              <td>{{ item.location }}</td>
              <td class="muted">{{ item.value }}</td>
              <td><span class="pill" :class="item.online ? 'pill-ok' : 'pill-off'">{{ item.online ? (item.enabled ? '在线' : '已停用') : '离线' }}</span></td>
              <td class="ops">
                <button @click="editDevice(item)">编辑</button>
                <button @click="toggleDevice(item)">{{ item.enabled ? '停用' : '启用' }}</button>
                <button class="danger" @click="removeDevice(item)">删除</button>
              </td>
            </tr>
            <tr v-if="!devices.length"><td colspan="7" class="placeholder">没有设备数据</td></tr>
          </tbody>
        </table>
      </div>
    </section>

    <section v-if="tab === 'weather'" class="panel">
      <div class="vision-head">
        <div>
          <h2>天气与环境</h2>
          <p>实时数据每 60 秒刷新一次；天气来源 Open‑Meteo，位置可在下方自定义。</p>
        </div>
        <button class="primary" :disabled="loading" @click="refreshAll">{{ loading ? '刷新中…' : '立即刷新' }}</button>
      </div>

      <div class="weather-loc-card">
        <div class="weather-loc-head">
          <strong>天气位置设置</strong>
          <span class="muted small">保存后立即生效，无需重启服务器</span>
        </div>
        <div class="preset-row">
          <button v-for="c in PRESET_CITIES" :key="c.city" :class="{ active: weatherLocForm.city === c.city }" @click="applyPreset(c)">{{ c.city }}</button>
        </div>
        <div class="form-grid compact">
          <label><span>位置名称</span><input :value="weatherLocForm.city" @input="weatherLocForm.city = ($event.target as HTMLInputElement).value" placeholder="如 张掖 / 酒泉 / 武威" /></label>
          <label><span>纬度（WGS84）</span><input :value="weatherLocForm.latitude" @input="weatherLocForm.latitude = ($event.target as HTMLInputElement).value" placeholder="18 ~ 54" /></label>
          <label><span>经度（WGS84）</span><input :value="weatherLocForm.longitude" @input="weatherLocForm.longitude = ($event.target as HTMLInputElement).value" placeholder="73 ~ 135" /></label>
          <div class="actions"><button class="primary" :disabled="weatherLocSaving || weatherLocLoading" @click="saveWeatherLocation">{{ weatherLocSaving ? '保存中…' : '保存位置' }}</button></div>
        </div>
      </div>

      <div class="weather-grid">
        <div class="weather-card">
          <small class="src">{{ weather?.source || 'Open‑Meteo' }} · {{ weather?.observedAt ? new Date(weather.observedAt).toLocaleString('zh-CN') : '加载中' }}</small>
          <div class="weather-main">
            <strong class="temp">{{ weather ? weather.temperature.toFixed(1) : '—' }}°C</strong>
            <span class="state">{{ weather ? weatherText(weather.weatherCode) : '—' }}</span>
          </div>
          <div class="weather-stats">
            <div><span>湿度</span><strong>{{ weather ? weather.humidity.toFixed(0) : '—' }}%</strong></div>
            <div><span>风速</span><strong>{{ weather ? weather.windSpeed.toFixed(1) : '—' }} m/s</strong></div>
            <div><span>辐照</span><strong>{{ weather ? weather.radiation.toFixed(0) : '—' }} W/m²</strong></div>
          </div>
          <p v-if="weatherError" class="error small">{{ weatherError }}</p>
        </div>

        <div v-if="farmSummary" class="farm-card">
          <h3>农场总览</h3>
          <ul>
            <li><span>健康度</span><strong>{{ farmSummary.health }}</strong></li>
            <li><span>设备</span><strong>{{ farmSummary.onlineDevices }}/{{ farmSummary.totalDevices }} 在线</strong></li>
            <li><span>运行中</span><strong>{{ farmSummary.runningDevices }}</strong></li>
            <li><span>水位</span><strong>{{ farmSummary.waterLevel.toFixed(1) }} m</strong></li>
            <li><span>今日用水</span><strong>{{ farmSummary.todayWaterUsage.toFixed(0) }} m³</strong></li>
            <li><span>待处理告警</span><strong :class="farmSummary.openAlerts > 0 ? 'warn' : ''">{{ farmSummary.openAlerts }}</strong></li>
          </ul>
        </div>
      </div>

      <h3 class="section-title">环境传感器</h3>
      <div class="metric-grid">
        <article v-for="m in metrics" :key="m.key" class="metric-card" :class="'tone-' + m.tone">
          <div class="metric-top">
            <span class="metric-label">{{ m.label }}</span>
            <span class="metric-delta" :class="(m.delta || '').startsWith('+') ? 'up' : (m.delta || '').startsWith('-') ? 'down' : ''">{{ m.delta || '—' }}</span>
          </div>
          <div class="metric-body">
            <strong class="metric-value">{{ m.numericValue.toFixed(m.numericValue % 1 === 0 ? 0 : 1) }}</strong>
            <span class="metric-unit">{{ m.unit }}</span>
          </div>
          <svg v-if="metricCharts[m.key]?.points.length > 1" class="mini-chart" viewBox="0 0 120 36" preserveAspectRatio="none" aria-hidden="true">
            <polyline fill="none" stroke="#3e7c4f" stroke-width="1.5" stroke-linejoin="round" stroke-linecap="round"
              :points="miniChartPoints(m.key)" />
          </svg>
          <small class="metric-time">更新于 {{ m.measuredAt ? new Date(m.measuredAt).toLocaleTimeString('zh-CN') : '—' }}</small>
        </article>
        <div v-if="!metrics.length" class="placeholder wide">暂未采集到环境数据</div>
      </div>

      <h3 class="section-title">告警</h3>
      <div class="table-wrap">
        <table>
          <thead><tr><th>时间</th><th>标题</th><th>级别</th><th>状态</th><th>关联实体</th></tr></thead>
          <tbody>
            <tr v-for="a in alerts" :key="a.id">
              <td class="muted">{{ a.time }}</td>
              <td>{{ a.title }}</td>
              <td><span class="pill" :class="'pill-' + ({ high: 'warn', medium: 'warn', low: '' } as Record<string,string>)[a.level]">{{ a.level }}</span></td>
              <td><span class="pill" :class="a.status === 'handled' ? 'pill-off' : 'pill-ok'">{{ a.status }}</span></td>
              <td class="muted">{{ a.entityId }}</td>
            </tr>
            <tr v-if="!alerts.length"><td colspan="5" class="placeholder">当前无告警</td></tr>
          </tbody>
        </table>
      </div>

      <h3 class="section-title">灌溉单元</h3>
      <div class="irrigation-grid">
        <div v-for="u in irrigationUnits" :key="u.id" class="metric-card" :class="u.enabled ? 'tone-ok' : 'tone-off'">
          <div class="metric-top">
            <span class="metric-label">{{ u.name }}</span>
            <span class="pill" :class="u.enabled ? 'pill-ok' : 'pill-off'">{{ u.enabled ? '运行中' : '已关闭' }}</span>
          </div>
          <div class="metric-body">
            <strong class="metric-value">{{ u.flow }}</strong>
            <span class="metric-unit">{{ u.kind }} · {{ u.durationMinutes }} min</span>
          </div>
          <small class="metric-time">目标：{{ u.target }}</small>
        </div>
        <div v-if="!irrigationUnits.length" class="placeholder wide">没有灌溉单元</div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.admin { min-height: 100dvh; background: #f2f5f1; padding: 28px clamp(16px, 4vw, 56px) 60px; font-family: var(--font-sans, system-ui, sans-serif); color: #16261c; }
.top { display: flex; align-items: center; justify-content: space-between; gap: 16px; margin-bottom: 22px; }
.brand { display: flex; align-items: center; gap: 12px; }
.dot { width: 34px; height: 34px; border-radius: 12px; background: linear-gradient(135deg, #3e7c4f, #7fb069); }
.brand h1 { margin: 0; font-size: 20px; letter-spacing: .01em; }
.brand p { margin: 3px 0 0; font-size: 12px; color: #7b9184; }
.back { font-size: 12px; color: #3e7c4f; text-decoration: none; border: 1px solid #cfe0d2; background: #fff; padding: 9px 14px; border-radius: 9px; }
.tabs { display: flex; gap: 8px; margin-bottom: 18px; }
.tabs button { border: 0; background: transparent; padding: 9px 16px; border-radius: 9px; font-size: 13px; color: #5f7769; cursor: pointer; }
.tabs button.active { background: #3e7c4f; color: #fff; }
.notice, .error { margin: 0 0 14px; padding: 11px 14px; border-radius: 10px; font-size: 12px; }
.notice { background: #e6f3e9; color: #2f6a41; border: 1px solid #c8e3d0; }
.error { background: #fdeaea; color: #a33a3a; border: 1px solid #f3cccc; }
.panel { background: #fff; border: 1px solid #e2eae2; border-radius: 16px; padding: 22px; box-shadow: 0 14px 40px rgba(24, 68, 45, .05); }
.cards { display: grid; gap: 14px; grid-template-columns: repeat(auto-fit, minmax(168px, 1fr)); }
.cards article { border: 1px solid #e6ede7; border-radius: 12px; padding: 16px; display: flex; flex-direction: column; gap: 6px; background: #fbfdfb; }
.cards span { font-size: 11px; color: #7b9184; letter-spacing: .04em; }
.cards strong { font-size: 22px; font-weight: 600; }
.cards small { font-size: 11px; color: #93a79a; }
.cards strong.ok { color: #2f7a45; }
.cards strong.warn { color: #b4762a; }
.toolbar { display: flex; align-items: center; gap: 10px; margin-bottom: 14px; flex-wrap: wrap; }
.toolbar input { flex: 1; min-width: 200px; padding: 10px 12px; border: 1px solid #dbe6dd; border-radius: 9px; font: inherit; font-size: 13px; }
.count { font-size: 12px; color: #7b9184; }
button.primary { background: #3e7c4f; color: #fff; border: 0; padding: 10px 16px; border-radius: 9px; font: inherit; font-size: 13px; cursor: pointer; }
button:disabled { opacity: .6; cursor: not-allowed; }
.table-wrap { overflow-x: auto; }
table { width: 100%; border-collapse: collapse; font-size: 13px; }
th, td { text-align: left; padding: 11px 10px; border-bottom: 1px solid #eef3ee; white-space: nowrap; }
th { font-size: 11px; font-weight: 600; color: #7b9184; letter-spacing: .04em; }
td.muted { color: #8ea396; font-size: 12px; }
td.ops { display: flex; gap: 6px; flex-wrap: wrap; }
td.ops button { border: 1px solid #dbe6dd; background: #fff; color: #3c5a47; padding: 6px 10px; border-radius: 7px; font: inherit; font-size: 12px; cursor: pointer; }
td.ops button:hover { border-color: #3e7c4f; color: #3e7c4f; }
td.ops button.danger { color: #a94442; border-color: #f0d3d1; }
.pill { display: inline-block; padding: 4px 9px; border-radius: 999px; font-size: 11px; background: #eef3ee; color: #55705f; }
.pill-admin { background: #e7f0fc; color: #2f5f9e; }
.pill-ok { background: #e6f3e9; color: #2f7a45; }
.pill-off { background: #f4efe4; color: #927238; }
.placeholder { padding: 24px; text-align: center; font-size: 13px; color: #93a79a; }
.vision-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; margin-bottom: 18px; }
.vision-head h2 { margin: 0 0 6px; font-size: 16px; }
.vision-head p { margin: 0; font-size: 12px; color: #7b9184; line-height: 1.7; max-width: 640px; }
.form { display: grid; gap: 16px; max-width: 620px; }
.form label { display: flex; flex-direction: column; gap: 7px; }
.form label span { font-size: 12px; font-weight: 600; color: #3c5a47; }
.form input { padding: 11px 12px; border: 1px solid #dbe6dd; border-radius: 9px; font: inherit; font-size: 13px; }
.form small { font-size: 11px; color: #93a79a; }
.form small em { font-style: normal; color: #b4762a; }
.actions { display: flex; gap: 10px; margin-top: 20px; }
.actions button { border: 1px solid #dbe6dd; background: #fff; color: #3c5a47; padding: 10px 16px; border-radius: 9px; font: inherit; font-size: 13px; cursor: pointer; }
.tips { margin-top: 26px; padding: 16px; background: #f7faf7; border: 1px dashed #d7e5d8; border-radius: 12px; font-size: 12px; color: #6f8a79; }
.tips strong { display: block; margin-bottom: 8px; color: #3c5a47; }
.tips ul { margin: 0; padding-left: 18px; line-height: 2; }
.tips code { background: #e9f1ea; padding: 1px 5px; border-radius: 4px; font-size: 11px; }
.form-grid { display: grid; gap: 14px; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); margin-bottom: 6px; }
.form-grid label { display: flex; flex-direction: column; gap: 6px; }
.form-grid label > span { font-size: 12px; font-weight: 600; color: #3c5a47; }
.form-grid input, .form-grid select, .form-grid textarea { padding: 10px 12px; border: 1px solid #dbe6dd; border-radius: 9px; font: inherit; font-size: 13px; background: #fff; color: inherit; }
.form-grid input:disabled { background: #f4f7f4; color: #8ea396; }
.form-grid textarea { resize: vertical; }
.form-grid small { font-size: 11px; color: #93a79a; }
.form-grid .wide { grid-column: 1 / -1; }
.form-grid .check { flex-direction: row; align-items: center; gap: 8px; font-size: 13px; }
.form-grid .check input { width: auto; }
.edit-block { padding: 16px; margin-bottom: 18px; background: #f7faf7; border: 1px dashed #d7e5d8; border-radius: 12px; }
.actions.wide { grid-column: 1 / -1; margin-top: 4px; }
.toolbar select { padding: 9px 12px; border: 1px solid #dbe6dd; border-radius: 9px; font: inherit; font-size: 13px; background: #fff; }
td.clamp { max-width: 320px; overflow: hidden; text-overflow: ellipsis; }

.weather-grid { display: grid; grid-template-columns: 1.4fr 1fr; gap: 16px; margin-bottom: 24px; }
@media (max-width: 720px) { .weather-grid { grid-template-columns: 1fr; } }
.weather-card, .farm-card { border-radius: 14px; padding: 22px; background: linear-gradient(135deg, #edf5ee 0%, #f7faf7 100%); border: 1px solid #dbe6dd; }
.weather-card .src { color: #7b9184; font-size: 11px; }
.weather-main { display: flex; align-items: center; gap: 12px; margin: 10px 0 14px; }
.weather-main .temp { font-size: 56px; font-weight: 600; letter-spacing: -0.03em; color: #275434; line-height: 1; }
.weather-main .state { font-size: 15px; color: #3e6f4c; }
.weather-stats { display: grid; grid-template-columns: repeat(3, 1fr); gap: 10px; }
.weather-stats > div { background: rgba(255,255,255,0.7); border-radius: 10px; padding: 10px 12px; display: flex; flex-direction: column; gap: 4px; }
.weather-stats span { font-size: 11px; color: #7b9184; }
.weather-stats strong { font-size: 16px; color: #275434; }
.weather-card .error.small { margin-top: 12px; font-size: 11px; }

.farm-card { background: #fff; }
.farm-card h3 { margin: 0 0 12px; font-size: 14px; color: #275434; }
.farm-card ul { margin: 0; padding: 0; list-style: none; display: grid; grid-template-columns: repeat(2, 1fr); gap: 10px; }
.farm-card li { background: #f7faf7; border-radius: 9px; padding: 9px 12px; display: flex; flex-direction: column; gap: 3px; }
.farm-card li span { font-size: 11px; color: #7b9184; }
.farm-card li strong { font-size: 14px; color: #275434; }
.farm-card li strong.warn { color: #b4762a; }

.section-title { margin: 22px 0 12px; font-size: 14px; color: #275434; font-weight: 600; }

.metric-grid, .irrigation-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 12px; margin-bottom: 22px; }
.metric-card { border-radius: 12px; padding: 14px 16px; background: #fff; border: 1px solid #e6ede7; display: flex; flex-direction: column; gap: 8px; }
.metric-card.tone-good { border-color: #c8e3d0; background: #f2faf3; }
.metric-card.tone-warn { border-color: #efe0b9; background: #fdf9ef; }
.metric-card.tone-alert { border-color: #f0c0bc; background: #fef0ef; }
.metric-card.tone-ok { border-color: #c8e3d0; background: #f2faf3; }
.metric-card.tone-off { border-color: #e0d6c2; background: #f7f4ee; }
.metric-top { display: flex; align-items: center; justify-content: space-between; }
.metric-label { font-size: 12px; color: #5f7769; font-weight: 600; }
.metric-delta { font-size: 11px; color: #8ea396; }
.metric-delta.up { color: #b45a2a; }
.metric-delta.down { color: #2f7a45; }
.metric-body { display: flex; align-items: baseline; gap: 6px; }
.metric-value { font-size: 28px; font-weight: 600; color: #1e3a26; letter-spacing: -0.02em; }
.metric-unit { font-size: 12px; color: #7b9184; }
.mini-chart { width: 100%; height: 36px; }
.metric-time { font-size: 10px; color: #93a79a; }

.placeholder.wide { grid-column: 1 / -1; }

/* 天气位置设置卡片 */
.weather-loc-card { border: 1px solid #d5e2d8; border-radius: 12px; background: #f5faf5; padding: 16px 18px 14px; margin-bottom: 18px; }
.weather-loc-head { display: flex; align-items: baseline; justify-content: space-between; margin-bottom: 10px; }
.weather-loc-head strong { font-size: 13px; color: #2f5d3d; }
.preset-row { display: flex; gap: 6px; flex-wrap: wrap; margin-bottom: 12px; }
.preset-row button { border: 1px solid #cfe0d2; background: #fff; padding: 5px 12px; border-radius: 16px; font-size: 12px; color: #3e7c4f; cursor: pointer; transition: .15s; }
.preset-row button:hover { background: #e6f3e9; }
.preset-row button.active { background: #3e7c4f; color: #fff; border-color: #3e7c4f; }
.form-grid.compact { grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 10px 14px; }
.form-grid.compact label input { padding: 8px 10px; font-size: 13px; }
.form-grid.compact .actions { grid-column: auto; align-self: end; }
</style>

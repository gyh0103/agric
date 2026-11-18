<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import {
  deleteUser,
  fetchOverview,
  fetchSettings,
  fetchUsers,
  resetPassword,
  saveSettings,
  updateUser,
  type AdminOverview,
  type AdminUser,
  type SettingItem
} from '@/api/admin'

type Tab = 'overview' | 'users' | 'settings'

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

onMounted(loadOverview)
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
</style>

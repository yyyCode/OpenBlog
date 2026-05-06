<template>
  <div class="console-page">
    <header class="console-page-header">
      <div class="console-page-title">
        <h1>站点概览</h1>
      </div>
    </header>

    <div class="console-card console-inner-card">
      <div v-if="loading" style="color: var(--console-muted, var(--muted))">加载中...</div>
      <template v-else>
        <div v-if="isAdmin && pendingUsers !== null" class="admin-section" style="margin-bottom: 22px">
          <div style="font-weight: 900; font-size: 15px; margin-bottom: 10px">待审核注册（读者）</div>
          <div v-if="pendingUsers.length === 0" style="color: var(--console-muted, var(--muted)); font-size: 13px">
            暂无待审核账号
          </div>
          <div v-else class="pending-users">
            <div v-for="u in pendingUsers" :key="u.userId" class="pending-user-row">
              <div class="pending-user-meta">
                <div style="font-weight: 850">{{ u.username }}</div>
                <div style="font-size: 12px; color: var(--console-muted, var(--muted))">
                  {{ u.email }}
                  <span v-if="u.createdAt"> · {{ formatTime(u.createdAt) }}</span>
                </div>
              </div>
              <button type="button" class="btn primary" :disabled="approvingId === u.userId" @click="approve(u.userId)">
                {{ approvingId === u.userId ? '处理中…' : '通过审核' }}
              </button>
            </div>
          </div>
          <div v-if="pendingError" class="error" style="margin-top: 10px">{{ pendingError }}</div>
        </div>

        <div class="console-kv-grid">
          <div class="console-kv-row">
            <span class="console-k">站点版本</span>
            <span class="console-v">{{ versionLabel }}</span>
          </div>
          <div class="console-kv-row">
            <span class="console-k">已发布文章</span>
            <span class="console-v">{{ fmt(stats?.articleCount) }}</span>
          </div>
          <div class="console-kv-row">
            <span class="console-k">已通过评论</span>
            <span class="console-v">{{ fmt(stats?.commentCount) }}</span>
          </div>
          <div class="console-kv-row">
            <span class="console-k">全站浏览量（去重 IP 累计）</span>
            <span class="console-v">{{ fmt(stats?.siteVisitCount) }}</span>
          </div>
          <div class="console-kv-row">
            <span class="console-k">最近发文时间</span>
            <span class="console-v">{{ lastActivityText }}</span>
          </div>
        </div>
      </template>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { fetchSiteStats, fetchSiteVersion } from '../api/site'
import { fetchMe } from '../api/admin'
import { fetchPendingReaders, approveReader } from '../api/userAdmin'
import { showMessage } from '../utils/message'

const loading = ref(true)
const stats = ref(null)
const version = ref('')
const me = ref(null)
const pendingUsers = ref(null)
const pendingError = ref('')
const approvingId = ref(null)

const isAdmin = computed(() => me.value?.role === 'ADMIN')

const versionLabel = computed(() => {
  const v = (version.value || '').trim()
  if (!v) return '—'
  return v.startsWith('v') ? v : `v${v}`
})

const lastActivityText = computed(() => {
  const t = stats.value?.lastActivityAt
  if (!t) return '—'
  try {
    return new Date(t).toLocaleString('zh-CN')
  } catch {
    return '—'
  }
})

function fmt(v) {
  if (v == null || Number.isNaN(Number(v))) return '—'
  return Number(v).toLocaleString('zh-CN')
}

function formatTime(iso) {
  if (!iso) return ''
  try {
    return new Date(iso).toLocaleString('zh-CN')
  } catch {
    return ''
  }
}

async function loadPending() {
  pendingError.value = ''
  if (!isAdmin.value) {
    pendingUsers.value = null
    return
  }
  try {
    pendingUsers.value = await fetchPendingReaders()
  } catch (e) {
    pendingUsers.value = []
    pendingError.value = e?.message || '加载待审核列表失败'
  }
}

async function approve(userId) {
  pendingError.value = ''
  approvingId.value = userId
  try {
    await approveReader(userId)
    showMessage('已通过审核')
    await loadPending()
  } catch (e) {
    pendingError.value = e?.message || '操作失败'
  } finally {
    approvingId.value = null
  }
}

onMounted(async () => {
  loading.value = true
  try {
    const [s, ver] = await Promise.all([fetchSiteStats(), fetchSiteVersion()])
    stats.value = s
    if (ver?.version) version.value = String(ver.version)
  } catch {
    stats.value = null
  }
  try {
    me.value = await fetchMe()
  } catch {
    me.value = null
  }
  await loadPending()
  loading.value = false
})
</script>

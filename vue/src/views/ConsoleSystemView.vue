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
        <p v-if="showReviewHint" class="console-prose" style="margin-top: 18px">
          前台新用户注册需在
          <router-link class="console-link" to="/console/users/pending">用户审核</router-link>
          中通过后方可登录。
        </p>
      </template>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { fetchSiteStats, fetchSiteVersion } from '../api/site'
import { fetchMe } from '../api/admin'

const loading = ref(true)
const stats = ref(null)
const version = ref('')
const me = ref(null)

const showReviewHint = computed(() => {
  const r = me.value?.role
  return r === 'ADMIN' || r === 'AUTHOR'
})

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
  loading.value = false
})
</script>

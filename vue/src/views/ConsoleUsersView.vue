<template>
  <div class="console-page">
    <header class="console-page-header">
      <div class="console-page-title">
        <h1>用户列表</h1>
      </div>
      <p class="console-prose" style="margin-top: 6px">
        列出全部注册用户的基础信息；详细资料与操作可在后续版本中扩展。
      </p>
    </header>

    <div class="console-card console-inner-card">
      <div v-if="loading && users.length === 0" style="color: var(--console-muted, var(--muted))">加载中...</div>
      <template v-else-if="!canAccess">
        <div style="color: var(--console-muted, var(--muted)); font-size: 14px">当前账号无权限查看用户列表。</div>
      </template>
      <template v-else>
        <div v-if="users.length === 0" style="color: var(--console-muted, var(--muted)); font-size: 14px">
          暂无用户
        </div>
        <div v-else class="admin-user-list">
          <div v-for="u in users" :key="u.userId" class="admin-user-row">
            <img
              class="admin-user-row-avatar"
              :src="u.avatarUrl || defaultAvatar"
              :alt="u.displayName || u.username || ''"
            />
            <div class="admin-user-row-meta">
              <div class="admin-user-row-name">{{ u.displayName || u.username || '—' }}</div>
              <div class="admin-user-row-sub">
                <span>{{ u.email || '—' }}</span>
                <template v-if="u.createdAt">
                  <span> · 注册于 {{ formatTime(u.createdAt) }}</span>
                </template>
                <template v-if="u.role || u.status">
                  <span> · {{ roleLabel(u.role) }} · {{ statusLabel(u.status) }}</span>
                </template>
              </div>
            </div>
          </div>
        </div>
        <div v-if="hasMore" style="margin-top: 16px; text-align: center">
          <button type="button" class="btn" :disabled="loadingMore" @click="loadMore">
            {{ loadingMore ? '加载中…' : '加载更多' }}
          </button>
        </div>
        <div v-if="error" class="error" style="margin-top: 14px">{{ error }}</div>
      </template>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { fetchMe } from '../api/admin'
import { fetchAdminUsers } from '../api/userAdmin'

const PAGE_SIZE = 24

const loading = ref(true)
const loadingMore = ref(false)
const me = ref(null)
const users = ref([])
const total = ref(0)
const page = ref(0)
const error = ref('')

const defaultAvatar = 'https://via.placeholder.com/80x80.png?text=OB'

const canAccess = computed(() => {
  const r = me.value?.role
  return r === 'ADMIN' || r === 'AUTHOR'
})

const hasMore = computed(() => users.value.length < total.value)

function formatTime(iso) {
  if (!iso) return ''
  try {
    return new Date(iso).toLocaleString('zh-CN')
  } catch {
    return ''
  }
}

function roleLabel(role) {
  if (role === 'ADMIN') return '管理员'
  if (role === 'AUTHOR') return '作者'
  if (role === 'READER') return '读者'
  return role || '—'
}

function statusLabel(status) {
  if (status === 'ACTIVE') return '正常'
  if (status === 'PENDING') return '待审核'
  if (status === 'BANNED') return '已封禁'
  return status || '—'
}

async function loadPage(nextPage, append) {
  error.value = ''
  if (!canAccess.value) {
    users.value = []
    total.value = 0
    return
  }
  const resp = await fetchAdminUsers(nextPage, PAGE_SIZE)
  const items = resp?.items || []
  total.value = Number(resp?.total) || 0
  page.value = nextPage
  users.value = append ? [...users.value, ...items] : items
}

async function loadMore() {
  if (!hasMore.value || loadingMore.value) return
  loadingMore.value = true
  try {
    await loadPage(page.value + 1, true)
  } catch (e) {
    error.value = e?.message || '加载失败'
  } finally {
    loadingMore.value = false
  }
}

onMounted(async () => {
  loading.value = true
  try {
    try {
      me.value = await fetchMe()
    } catch {
      me.value = null
    }
    await loadPage(0, false)
  } catch (e) {
    users.value = []
    error.value = e?.message || '加载用户列表失败'
  } finally {
    loading.value = false
  }
})
</script>

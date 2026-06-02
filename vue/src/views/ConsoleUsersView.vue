<template>
  <div class="console-page">
    <header class="console-page-header">
      <div class="console-page-title">
        <h1>用户列表</h1>
      </div>
      <p class="console-prose" style="margin-top: 6px">
        管理注册用户：搜索、筛选、封禁、限制互动、变更角色。
      </p>
    </header>

    <div class="console-card" style="margin-bottom: 16px">
      <div class="admin-user-filters">
        <input
          v-model="filters.keyword"
          type="text"
          class="form-input"
          placeholder="搜索用户名或邮箱…"
          style="flex: 1; min-width: 180px"
          @keyup.enter="applySearch"
        />
        <select v-model="filters.status" class="form-input" style="width: 130px">
          <option value="">全部状态</option>
          <option value="ACTIVE">活跃</option>
          <option value="BANNED">封禁</option>
          <option value="RESTRICTED">限制互动</option>
          <option value="PENDING">待审核</option>
        </select>
        <select v-model="filters.role" class="form-input" style="width: 110px">
          <option value="">全部角色</option>
          <option value="ADMIN">管理员</option>
          <option value="AUTHOR">作者</option>
          <option value="READER">读者</option>
        </select>
        <button type="button" class="btn btn-primary" @click="applySearch">搜索</button>
        <button type="button" class="btn" @click="resetFilters">重置</button>
      </div>
    </div>

    <div class="console-card console-inner-card">
      <div v-if="loading && users.length === 0" style="color: var(--console-muted, var(--muted))">加载中...</div>
      <template v-else-if="!canAccess">
        <div style="color: var(--console-muted, var(--muted)); font-size: 14px">当前账号无权限查看用户列表。</div>
      </template>
      <template v-else>
        <div v-if="users.length === 0" style="color: var(--console-muted, var(--muted)); font-size: 14px">
          暂无匹配的用户
        </div>
        <div v-else class="admin-user-table-wrap">
          <table class="admin-user-table">
            <thead>
              <tr>
                <th>用户</th>
                <th>邮箱</th>
                <th>角色</th>
                <th>状态</th>
                <th>注册时间</th>
                <th v-if="isAdmin">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="u in users" :key="u.userId">
                <td>
                  <router-link :to="`/console/users/${u.userId}`" class="admin-user-link">
                    <img
                      class="admin-user-cell-avatar"
                      :src="u.avatarUrl || defaultAvatar"
                      :alt="u.displayName || u.username || ''"
                    />
                    <span>{{ u.displayName || u.username || '—' }}</span>
                  </router-link>
                </td>
                <td class="admin-user-cell-muted">{{ u.email || '—' }}</td>
                <td>
                  <span class="admin-tag" :class="'tag-role-' + (u.role || '').toLowerCase()">
                    {{ roleLabel(u.role) }}
                  </span>
                </td>
                <td>
                  <span class="admin-tag" :class="'tag-status-' + (u.status || '').toLowerCase()">
                    {{ statusLabel(u.status) }}
                  </span>
                </td>
                <td class="admin-user-cell-muted">{{ formatTime(u.createdAt) }}</td>
                <td v-if="isAdmin">
                  <div class="admin-user-actions">
                    <select
                      class="form-input form-input-sm"
                      :value="u.role"
                      @change="handleRoleChange(u, ($event.target).value)"
                    >
                      <option value="ADMIN">管理员</option>
                      <option value="AUTHOR">作者</option>
                      <option value="READER">读者</option>
                    </select>
                    <template v-if="u.status === 'ACTIVE'">
                      <button type="button" class="btn btn-sm btn-outline-danger" @click="handleBan(u)">封禁</button>
                      <button type="button" class="btn btn-sm btn-outline-warning" @click="handleRestrict(u)">限制互动</button>
                    </template>
                    <template v-else-if="u.status === 'BANNED' || u.status === 'RESTRICTED'">
                      <button type="button" class="btn btn-sm btn-outline-success" @click="handleUnban(u)">解封</button>
                    </template>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
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
import { computed, onMounted, reactive, ref } from 'vue'
import { fetchMe } from '../api/admin'
import { fetchAdminUsers, changeUserStatus, changeUserRole } from '../api/userAdmin'

const PAGE_SIZE = 24

const loading = ref(true)
const loadingMore = ref(false)
const me = ref(null)
const users = ref([])
const total = ref(0)
const page = ref(0)
const error = ref('')

const filters = reactive({
  keyword: '',
  status: '',
  role: ''
})

const defaultAvatar = 'https://via.placeholder.com/80x80.png?text=OB'

const canAccess = computed(() => {
  const r = me.value?.role
  return r === 'ADMIN' || r === 'AUTHOR'
})

const isAdmin = computed(() => me.value?.role === 'ADMIN')

const hasMore = computed(() => users.value.length < total.value)

function formatTime(iso) {
  if (!iso) return ''
  try { return new Date(iso).toLocaleString('zh-CN') } catch { return '' }
}

function roleLabel(role) {
  if (role === 'ADMIN') return '管理员'
  if (role === 'AUTHOR') return '作者'
  if (role === 'READER') return '读者'
  return role || '—'
}

function statusLabel(status) {
  if (status === 'ACTIVE') return '活跃'
  if (status === 'PENDING') return '待审核'
  if (status === 'BANNED') return '已封禁'
  if (status === 'RESTRICTED') return '限制互动'
  return status || '—'
}

async function loadPage(nextPage, append) {
  error.value = ''
  if (!canAccess.value) { users.value = []; total.value = 0; return }
  const resp = await fetchAdminUsers({
    keyword: filters.keyword,
    status: filters.status,
    role: filters.role,
    page: nextPage,
    size: PAGE_SIZE
  })
  const items = resp?.items || []
  total.value = Number(resp?.total) || 0
  page.value = nextPage
  users.value = append ? [...users.value, ...items] : items
}

async function loadMore() {
  if (!hasMore.value || loadingMore.value) return
  loadingMore.value = true
  try { await loadPage(page.value + 1, true) } catch (e) { error.value = e?.message || '加载失败' } finally { loadingMore.value = false }
}

function applySearch() {
  loading.value = true
  users.value = []
  total.value = 0
  loadPage(0, false).finally(() => { loading.value = false })
}

function resetFilters() {
  filters.keyword = ''
  filters.status = ''
  filters.role = ''
  applySearch()
}

async function handleBan(u) {
  if (!confirm(`确定要封禁用户「${u.displayName || u.username}」吗？封禁后该用户将无法登录。`)) return
  try {
    await changeUserStatus(u.userId, 'BANNED')
    u.status = 'BANNED'
  } catch (e) { alert(e?.message || '操作失败') }
}

async function handleRestrict(u) {
  if (!confirm(`确定要限制用户「${u.displayName || u.username}」的互动权限吗？`)) return
  try {
    await changeUserStatus(u.userId, 'RESTRICTED')
    u.status = 'RESTRICTED'
  } catch (e) { alert(e?.message || '操作失败') }
}

async function handleUnban(u) {
  if (!confirm(`确定要解除对「${u.displayName || u.username}」的限制吗？`)) return
  try {
    await changeUserStatus(u.userId, 'ACTIVE')
    u.status = 'ACTIVE'
  } catch (e) { alert(e?.message || '操作失败') }
}

async function handleRoleChange(u, newRole) {
  if (u.role === newRole) return
  if (!confirm(`确定要将「${u.displayName || u.username}」的角色变更为「${roleLabel(newRole)}」吗？`)) return
  try {
    await changeUserRole(u.userId, newRole)
    u.role = newRole
  } catch (e) { alert(e?.message || '操作失败') }
}

onMounted(async () => {
  loading.value = true
  try {
    try { me.value = await fetchMe() } catch { me.value = null }
    await loadPage(0, false)
  } catch (e) { users.value = []; error.value = e?.message || '加载用户列表失败' } finally { loading.value = false }
})
</script>

<template>
  <div class="console-page">
    <header class="console-page-header">
      <nav class="console-breadcrumb">
        <router-link to="/console/users">用户管理</router-link>
        <span class="console-breadcrumb-sep">›</span>
        <span>{{ detail?.username || '用户详情' }}</span>
      </nav>
    </header>

    <div v-if="loading" style="color: var(--console-muted, var(--muted))">加载中...</div>
    <div v-else-if="error" class="error">{{ error }}</div>
    <template v-else-if="detail">
      <div class="user-detail-grid">
        <!-- 左栏：信息卡 -->
        <div class="console-card">
          <div class="user-detail-profile">
            <img
              class="user-detail-avatar"
              :src="detail.avatarUrl || defaultAvatar"
              :alt="detail.username || ''"
            />
            <div class="user-detail-names">
              <h2 class="user-detail-username">{{ detail.username }}</h2>
              <div class="user-detail-tags">
                <span class="admin-tag" :class="'tag-role-' + (detail.role || '').toLowerCase()">
                  {{ roleLabel(detail.role) }}
                </span>
                <span class="admin-tag" :class="'tag-status-' + (detail.status || '').toLowerCase()">
                  {{ statusLabel(detail.status) }}
                </span>
              </div>
            </div>
          </div>
          <dl class="user-detail-fields">
            <dt>邮箱</dt>
            <dd>{{ detail.email || '—' }}</dd>
            <dt>简介</dt>
            <dd>{{ detail.bio || '暂无简介' }}</dd>
          </dl>
        </div>

        <!-- 右栏：统计 -->
        <div class="console-card">
          <h3 class="console-card-title">数据统计</h3>
          <div class="user-detail-stats">
            <div class="stat-item">
              <span class="stat-value">{{ detail.articleCount }}</span>
              <span class="stat-label">文章数</span>
            </div>
            <div class="stat-item">
              <span class="stat-value">{{ detail.commentCount }}</span>
              <span class="stat-label">评论数</span>
            </div>
            <div class="stat-item">
              <span class="stat-value">{{ fmtDate(detail.createdAt) }}</span>
              <span class="stat-label">注册时间</span>
            </div>
            <div class="stat-item">
              <span class="stat-value">{{ fmtDate(detail.updatedAt) }}</span>
              <span class="stat-label">最近更新</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 操作面板（仅 ADMIN） -->
      <div v-if="isAdmin" class="console-card" style="margin-top: 16px">
        <h3 class="console-card-title">管理操作</h3>
        <div class="user-detail-ops">
          <div class="op-group">
            <span class="op-label">角色：</span>
            <select
              class="form-input"
              style="width: 140px"
              :value="detail.role"
              @change="handleRoleChange(($event.target).value)"
            >
              <option value="ADMIN">管理员</option>
              <option value="AUTHOR">作者</option>
              <option value="READER">读者</option>
            </select>
          </div>
          <div class="op-group">
            <span class="op-label">状态：</span>
            <template v-if="detail.status === 'ACTIVE'">
              <button type="button" class="btn btn-outline-danger" @click="handleBan">封禁账号</button>
              <button type="button" class="btn btn-outline-warning" @click="handleRestrict">限制互动</button>
            </template>
            <template v-else-if="detail.status === 'BANNED'">
              <button type="button" class="btn btn-outline-success" @click="handleUnban">解除封禁</button>
            </template>
            <template v-else-if="detail.status === 'RESTRICTED'">
              <button type="button" class="btn btn-outline-success" @click="handleUnban">解除限制</button>
            </template>
            <template v-else-if="detail.status === 'PENDING'">
              <span class="op-hint">待审核用户需在审核页面操作</span>
            </template>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { fetchMe } from '../api/admin'
import { fetchUserDetail, changeUserStatus, changeUserRole } from '../api/userAdmin'

const route = useRoute()

const loading = ref(true)
const me = ref(null)
const detail = ref(null)
const error = ref('')
const defaultAvatar = 'https://via.placeholder.com/80x80.png?text=OB'

const isAdmin = computed(() => me.value?.role === 'ADMIN')

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

function fmtDate(iso) {
  if (!iso) return '—'
  try { return new Date(iso).toLocaleString('zh-CN') } catch { return '—' }
}

async function loadDetail() {
  const userId = route.params.userId
  if (!userId) {
    error.value = '缺少用户 ID'
    return
  }
  try {
    detail.value = await fetchUserDetail(userId)
  } catch (e) {
    error.value = e?.message || '加载用户详情失败'
  }
}

async function handleBan() {
  if (!confirm('确定要封禁该用户吗？')) return
  try {
    await changeUserStatus(detail.value.userId, 'BANNED')
    detail.value.status = 'BANNED'
  } catch (e) { alert(e?.message || '操作失败') }
}

async function handleRestrict() {
  if (!confirm('确定要限制该用户的互动权限吗？')) return
  try {
    await changeUserStatus(detail.value.userId, 'RESTRICTED')
    detail.value.status = 'RESTRICTED'
  } catch (e) { alert(e?.message || '操作失败') }
}

async function handleUnban() {
  if (!confirm('确定要解除该用户的限制吗？')) return
  try {
    await changeUserStatus(detail.value.userId, 'ACTIVE')
    detail.value.status = 'ACTIVE'
  } catch (e) { alert(e?.message || '操作失败') }
}

async function handleRoleChange(newRole) {
  if (detail.value.role === newRole) return
  if (!confirm(`确定要将其角色变更为「${roleLabel(newRole)}」吗？`)) return
  try {
    await changeUserRole(detail.value.userId, newRole)
    detail.value.role = newRole
  } catch (e) { alert(e?.message || '操作失败') }
}

onMounted(async () => {
  try {
    try { me.value = await fetchMe() } catch { me.value = null }
    await loadDetail()
  } finally {
    loading.value = false
  }
})
</script>

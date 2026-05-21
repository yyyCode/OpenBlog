<template>
  <div class="console-page">
    <header class="console-page-header">
      <div class="console-page-title">
        <h1>用户审核</h1>
      </div>
      <p class="console-prose" style="margin-top: 6px">
        此处列出前台注册、状态为「待审核」的读者账号；通过后用户即可登录并使用评论等功能。
      </p>
    </header>

    <div class="console-card console-inner-card">
      <div v-if="loading" style="color: var(--console-muted, var(--muted))">加载中...</div>
      <template v-else-if="!canAccess">
        <div style="color: var(--console-muted, var(--muted)); font-size: 14px">当前账号无权限查看待审核用户。</div>
      </template>
      <template v-else>
        <div v-if="pendingUsers.length === 0" style="color: var(--console-muted, var(--muted)); font-size: 14px">
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
        <div v-if="error" class="error" style="margin-top: 14px">{{ error }}</div>
      </template>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { fetchMe } from '../api/admin'
import { fetchPendingReaders, approveReader } from '../api/userAdmin'
import { showMessage } from '../utils/message'

const loading = ref(true)
const me = ref(null)
const pendingUsers = ref([])
const error = ref('')
const approvingId = ref(null)

const canAccess = computed(() => {
  const r = me.value?.role
  return r === 'ADMIN' || r === 'AUTHOR'
})

function formatTime(iso) {
  if (!iso) return ''
  try {
    return new Date(iso).toLocaleString('zh-CN')
  } catch {
    return ''
  }
}

async function loadList() {
  error.value = ''
  if (!canAccess.value) {
    pendingUsers.value = []
    return
  }
  try {
    pendingUsers.value = await fetchPendingReaders()
  } catch (e) {
    pendingUsers.value = []
    error.value = e?.message || '加载待审核列表失败'
  }
}

async function approve(userId) {
  error.value = ''
  approvingId.value = userId
  try {
    await approveReader(userId)
    showMessage('已通过审核')
    await loadList()
  } catch (e) {
    error.value = e?.message || '操作失败'
  } finally {
    approvingId.value = null
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
    await loadList()
  } finally {
    loading.value = false
  }
})
</script>

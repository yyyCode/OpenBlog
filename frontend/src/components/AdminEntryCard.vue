<template>
  <div class="blog-info-wrap admin-entry-wrap">
    <div class="blog-info-heading">管理员入口</div>
    <div class="card blog-info-card">
      <div class="blog-info-card-body admin-entry-card-body">
        <button
          class="profile-menu-item admin-entry-btn"
          type="button"
          :disabled="loading"
          @click="goAdmin"
        >
          <svg class="admin-entry-icon" viewBox="0 0 24 24" aria-hidden="true">
            <!-- 仪表盘 / 控制台，表示后台 -->
            <path
              fill="currentColor"
              d="M3 13h8V3H3v10zm0 8h8v-6H3v6zm10 0h8V11h-8v10zm0-18v6h8V3h-8z"
            />
          </svg>
          <span>{{ loading ? '验证中…' : '进入后台' }}</span>
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { fetchMe } from '../api/admin'

const router = useRouter()
const loading = ref(false)

async function goAdmin() {
  const token = localStorage.getItem('accessToken')
  if (!token) {
    router.push({ path: '/admin/login', query: { redirect: '/admin' } })
    return
  }
  loading.value = true
  try {
    await fetchMe()
    router.push('/admin')
  } catch {
    localStorage.removeItem('accessToken')
    localStorage.removeItem('refreshToken')
    router.push({ path: '/admin/login', query: { redirect: '/admin' } })
  } finally {
    loading.value = false
  }
}
</script>

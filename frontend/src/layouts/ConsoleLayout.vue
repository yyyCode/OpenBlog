<template>
  <div class="console-root">
    <aside class="console-sidebar">
      <div class="console-sidebar-inner">
        <router-link to="/console" class="console-brand">OpenBlog</router-link>

        <div class="console-search-wrap">
          <span class="console-search-icon" aria-hidden="true">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <circle cx="11" cy="11" r="7" />
              <path d="M21 21l-4.3-4.3" />
            </svg>
          </span>
          <input
            type="search"
            class="console-search-input"
            placeholder="搜索"
            readonly
            title="导航搜索（后续可接入）"
          />
          <span class="console-kbd">{{ searchKbd }}</span>
        </div>

        <nav class="console-nav" aria-label="控制台主导航">
          <router-link v-slot="{ href, navigate }" to="/console" custom>
            <a
              :href="href"
              class="console-nav-item"
              :class="{ active: isDashboardActive }"
              @click="(e) => navigate(e)"
            >
              <span class="console-nav-ico" aria-hidden="true">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z" />
                  <path d="M9 22V12h6v10" />
                </svg>
              </span>
              仪表盘
            </a>
          </router-link>

          <div class="console-nav-group-title">内容</div>
          <router-link to="/console/articles" class="console-nav-item" active-class="active">
            <span class="console-nav-ico" aria-hidden="true">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
                <path d="M14 2v6h6M16 13H8M16 17H8M10 9H8" />
              </svg>
            </span>
            文章
          </router-link>
          <router-link to="/console/changelog" class="console-nav-item" active-class="active">
            <span class="console-nav-ico" aria-hidden="true">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20" />
                <path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z" />
              </svg>
            </span>
            更新日志
          </router-link>
          <router-link to="/console/comments" class="console-nav-item" active-class="active">
            <span class="console-nav-ico" aria-hidden="true">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" />
              </svg>
            </span>
            评论
          </router-link>
          <router-link to="/console/attachments" class="console-nav-item" active-class="active">
            <span class="console-nav-ico" aria-hidden="true">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M21.44 11.05l-9.19 9.19a6 6 0 0 1-8.49-8.49l9.19-9.19A4 4 0 0 1 21 12.44l-9.2 9.19" />
              </svg>
            </span>
            附件
          </router-link>

          <div class="console-nav-group-title">系统</div>
          <router-link to="/console/profile" class="console-nav-item" active-class="active">
            <span class="console-nav-ico" aria-hidden="true">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" />
                <circle cx="12" cy="7" r="4" />
              </svg>
            </span>
            个人资料
          </router-link>
          <router-link to="/console/system" class="console-nav-item" active-class="active">
            <span class="console-nav-ico" aria-hidden="true">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <rect x="3" y="3" width="18" height="18" rx="2" />
                <path d="M3 9h18M9 21V9" />
              </svg>
            </span>
            概览
          </router-link>
        </nav>
      </div>

      <div class="console-sidebar-footer">
        <div class="console-user">
          <img
            class="console-user-avatar"
            :src="me?.avatarUrl || defaultAvatar"
            alt=""
          />
          <div class="console-user-meta">
            <div class="console-user-name">{{ me?.nickname || me?.username || '用户' }}</div>
            <div class="console-user-role">{{ roleLabel }}</div>
          </div>
        </div>
        <div class="console-user-actions">
          <router-link to="/console/profile" class="console-icon-btn" title="设置" aria-label="设置">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <circle cx="12" cy="12" r="3" />
              <path d="M12 1v2M12 21v2M4.22 4.22l1.42 1.42M18.36 18.36l1.42 1.42M1 12h2M21 12h2M4.22 19.78l1.42-1.42M18.36 5.64l1.42-1.42" />
            </svg>
          </router-link>
          <button type="button" class="console-icon-btn" title="退出登录" aria-label="退出登录" @click="logout">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4" />
              <polyline points="16 17 21 12 16 7" />
              <line x1="21" y1="12" x2="9" y2="12" />
            </svg>
          </button>
        </div>
      </div>
    </aside>

    <main class="console-main">
      <router-view />
    </main>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { fetchMe } from '../api/admin'

const router = useRouter()
const route = useRoute()

const isDashboardActive = computed(() => {
  const p = route.path.replace(/\/$/, '') || '/'
  return p === '/console'
})
const me = ref(null)
const defaultAvatar = 'https://via.placeholder.com/80x80.png?text=OB'

const searchKbd = computed(() =>
  typeof navigator !== 'undefined' && /Mac|iPhone|iPad/.test(navigator.platform || navigator.userAgent)
    ? '⌘K'
    : 'Ctrl+K'
)

const roleLabel = computed(() => {
  const r = me.value?.role
  if (r === 'ADMIN') return '超级管理员'
  if (r === 'AUTHOR') return '作者'
  if (r === 'READER') return '读者'
  return '用户'
})

async function loadMe() {
  try {
    me.value = await fetchMe()
  } catch {
    me.value = null
  }
}

function logout() {
  localStorage.removeItem('accessToken')
  localStorage.removeItem('refreshToken')
  router.push('/console/login')
}

onMounted(() => {
  loadMe()
})
</script>

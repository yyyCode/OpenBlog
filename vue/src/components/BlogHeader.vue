<template>
  <header class="site-top-bar" role="banner">
    <div class="site-top-bar-inner">
      <router-link to="/" class="site-brand" aria-label="返回首页">
        <img class="site-brand-logo" src="/site-icon.png" alt="" aria-hidden="true" />
        <span class="site-brand-text"><span class="brand-hl">W</span>e<span class="brand-hl">C</span>ode</span>
      </router-link>

      <div class="site-search-bar" aria-label="搜索">
        <svg class="site-search-bar-icon" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <circle cx="11" cy="11" r="8" />
          <path d="M21 21l-4.35-4.35" />
        </svg>
        <span class="site-search-bar-placeholder">搜索文章...</span>
      </div>

      <nav class="site-nav" aria-label="站点导航">
        <router-link to="/" class="site-nav-link" active-class="active">
          <span class="site-nav-ico" aria-hidden="true">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z" />
              <path d="M9 22V12h6v10" />
            </svg>
          </span>
          首页
        </router-link>

        <router-link to="/all" class="site-nav-link" active-class="active">
          <span class="site-nav-ico" aria-hidden="true">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M4 6h16M4 12h16M4 18h16" />
            </svg>
          </span>
          文章
        </router-link>

        <router-link to="/projects" class="site-nav-link" active-class="active">
          <span class="site-nav-ico" aria-hidden="true">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <rect x="3" y="3" width="18" height="18" rx="2" />
              <path d="M9 3v18" />
              <path d="M3 9h18" />
            </svg>
          </span>
          项目推荐
        </router-link>

        <router-link to="/jobs" class="site-nav-link" active-class="active">
          <span class="site-nav-ico" aria-hidden="true">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M20 7h-4V5a2 2 0 0 0-2-2h-4a2 2 0 0 0-2 2v2H4a2 2 0 0 0-2 2v11a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2V9a2 2 0 0 0-2-2z" />
              <path d="M9 7V5h6v2" />
            </svg>
          </span>
          求职导航
        </router-link>

        <router-link to="/forum" class="site-nav-link" active-class="active">
          <span class="site-nav-ico" aria-hidden="true">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" />
            </svg>
          </span>
          论坛
        </router-link>

        <router-link to="/about-us" class="site-nav-link site-nav-link-sm-hide" active-class="active">
          <span class="site-nav-ico" aria-hidden="true">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <circle cx="12" cy="12" r="9" />
              <path d="M12 8h.01" />
              <path d="M11 12h1v5h1" />
            </svg>
          </span>
          关于我们
        </router-link>

        <div class="site-nav-auth site-nav-auth-trailing" aria-label="账户">
          <button
            type="button"
            class="site-nav-auth-avatar-btn"
            :title="me ? displayName : '登录/注册'"
            @click="goAuth"
          >
            <UserAvatar :url="me ? me.avatarUrl : ''" :size="32" />
          </button>
        </div>
      </nav>
    </div>
  </header>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { fetchMe } from '../api/admin'
import { getStoredAccessToken, isJwtExpired, isLikelyJwt } from '../auth/session'
import UserAvatar from './UserAvatar.vue'

const route = useRoute()
const router = useRouter()
const me = ref(null)

const displayName = computed(() => {
  const m = me.value
  if (!m) return ''
  return (m.username || '').trim() || '用户'
})

function tokenLooksValid() {
  const t = getStoredAccessToken()
  return Boolean(t && isLikelyJwt(t) && !isJwtExpired(t))
}

async function loadMe() {
  if (!tokenLooksValid()) {
    me.value = null
    return
  }
  try {
    me.value = await fetchMe()
  } catch {
    me.value = null
  }
}

watch(
  () => route.fullPath,
  () => {
    loadMe()
  }
)

onMounted(() => {
  loadMe()
})

function goAuth() {
  if (me.value) {
    router.push('/profile')
  } else {
    router.push('/login')
  }
}

</script>

<style scoped>
.site-nav-auth-avatar-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0;
  border: none;
  background: none;
  cursor: pointer;
  border-radius: 999px;
  line-height: 0;
}
.site-nav-auth-avatar-btn:hover {
  opacity: 0.85;
}
</style>

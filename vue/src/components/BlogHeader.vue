<template>
  <header class="site-top-bar" role="banner">
    <div class="site-top-bar-inner">
      <router-link to="/" class="site-brand" aria-label="返回首页">
        <img class="site-brand-logo" src="/site-icon.png" alt="" aria-hidden="true" />
        <span class="site-brand-text">{{ (siteConfig && siteConfig.blog_name) || '微码平台' }}</span>
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

        <a href="#" class="site-nav-link">
          <span class="site-nav-ico" aria-hidden="true">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M20 7h-4V5a2 2 0 0 0-2-2h-4a2 2 0 0 0-2 2v2H4a2 2 0 0 0-2 2v11a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2V9a2 2 0 0 0-2-2z" />
              <path d="M9 7V5h6v2" />
            </svg>
          </span>
          求职导航
        </a>

        <router-link to="/feedback" class="site-nav-link" active-class="active">
          <span class="site-nav-ico" aria-hidden="true">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M4 6h16a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2z" />
              <path d="M22 8l-10 7L2 8" />
            </svg>
          </span>
          反馈
        </router-link>

        <a
          class="site-nav-link site-nav-link-sm-hide"
          :href="(siteConfig && siteConfig.source_code_url) || 'https://github.com/yyyCode/OpenBlog.git'"
          target="_blank"
          rel="noopener noreferrer"
        >
          <span class="site-nav-ico" aria-hidden="true">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
              <path
                d="M12 .5C5.73.5.75 5.67.75 12.09c0 5.15 3.28 9.52 7.83 11.06.57.11.78-.25.78-.55 0-.27-.01-1.17-.02-2.12-3.18.71-3.85-1.39-3.85-1.39-.52-1.36-1.28-1.72-1.28-1.72-1.05-.74.08-.73.08-.73 1.16.08 1.77 1.23 1.77 1.23 1.03 1.8 2.7 1.28 3.36.98.1-.77.4-1.28.73-1.57-2.54-.3-5.21-1.3-5.21-5.79 0-1.28.44-2.33 1.16-3.15-.12-.29-.5-1.48.11-3.08 0 0 .95-.31 3.11 1.2.9-.26 1.86-.39 2.82-.39.96 0 1.92.13 2.82.39 2.16-1.51 3.11-1.2 3.11-1.2.61 1.6.23 2.79.11 3.08.72.82 1.16 1.87 1.16 3.15 0 4.5-2.68 5.49-5.23 5.79.41.36.78 1.07.78 2.17 0 1.57-.02 2.83-.02 3.22 0 .3.21.67.79.55 4.55-1.54 7.83-5.91 7.83-11.06C23.25 5.67 18.27.5 12 .5z"
              />
            </svg>
          </span>
          源码
        </a>

        <div class="site-nav-auth site-nav-auth-trailing" aria-label="账户">
          <template v-if="me">
            <span class="site-nav-user-name" :title="displayName">{{ displayName }}</span>
            <button type="button" class="site-nav-link site-nav-link-btn site-nav-pill-outline" @click="logout">
              退出
            </button>
          </template>
          <router-link v-else to="/login" class="site-nav-link site-nav-pill-solid site-nav-auth-entry">
            登录/注册
          </router-link>
        </div>
      </nav>
    </div>
  </header>
</template>

<script setup>
import { computed, inject, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { fetchMe } from '../api/admin'
import { clearAuth, getStoredAccessToken, isJwtExpired, isLikelyJwt } from '../auth/session'

defineEmits(['toggle-widgets'])

const route = useRoute()
const router = useRouter()
const me = ref(null)
const siteConfig = inject('siteConfig')

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

function logout() {
  clearAuth()
  me.value = null
  if (route.path.startsWith('/console')) {
    router.push('/console/login')
  } else {
    router.push('/')
  }
}

</script>

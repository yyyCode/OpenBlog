<script setup>
import { computed, onMounted, provide, ref } from 'vue'
import { useRoute } from 'vue-router'
import BlogHeader from './components/BlogHeader.vue'
import WelcomeGate from './components/WelcomeGate.vue'
import BackToTop from './components/BackToTop.vue'
import RightDock from './components/RightDock.vue'
import Live2dCharacter from './components/Live2dCharacter.vue'
import { postSiteVisit, fetchSiteConfig } from './api/site'

const route = useRoute()
const isConsole = computed(() => route.path.startsWith('/console'))
const siteConfig = ref({})

// 提供 siteConfig 给所有子组件
provide('siteConfig', siteConfig)

onMounted(() => {
  postSiteVisit().catch(() => {})
})

onMounted(async () => {
  // 加载站点配置
  try {
    siteConfig.value = await fetchSiteConfig()
  } catch {
    siteConfig.value = {}
  }
})
</script>

<template>
  <div class="blog-app">
    <WelcomeGate v-if="!isConsole" />
    <BlogHeader v-if="!isConsole" />
    <RightDock v-if="!isConsole" />
    <BackToTop v-if="!isConsole" />
    <Live2dCharacter v-if="!isConsole" />
    <main class="site-main">
      <router-view />
    </main>
    <footer v-if="!isConsole" class="site-footer">
      <div class="site-footer-inner">
        <span class="site-footer-info">
          <span>{{ (siteConfig && siteConfig.footer_copyright) || '© 2026 OpenBlog' }}</span>
          <span class="site-footer-sep">|</span>
          <a class="site-footer-icp" href="https://beian.miit.gov.cn/" target="_blank" rel="noopener noreferrer">粤ICP备2026033788号</a>
        </span>
        <span class="site-footer-actions">
          <router-link to="/feedback" class="site-footer-action">
            <svg width="19" height="19" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M4 6h16a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2z" />
              <path d="M22 8l-10 7L2 8" />
            </svg>
            反馈
          </router-link>
          <a class="site-footer-action" href="https://github.com/yyyCode/OpenBlog" target="_blank" rel="noopener noreferrer">
            <svg width="19" height="19" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
              <path d="M12 .297c-6.63 0-12 5.373-12 12 0 5.303 3.438 9.8 8.205 11.385.6.113.82-.258.82-.577 0-.285-.01-1.04-.015-2.04-3.338.724-4.042-1.61-4.042-1.61C4.422 18.07 3.633 17.7 3.633 17.7c-1.087-.744.084-.729.084-.729 1.205.084 1.838 1.236 1.838 1.236 1.07 1.835 2.809 1.305 3.495.998.108-.776.417-1.305.76-1.605-2.665-.3-5.466-1.332-5.466-5.93 0-1.31.465-2.38 1.235-3.22-.135-.303-.54-1.523.105-3.176 0 0 1.005-.322 3.3 1.23.96-.267 1.98-.399 3-.405 1.02.006 2.04.138 3 .405 2.28-1.552 3.285-1.23 3.285-1.23.645 1.653.24 2.873.12 3.176.765.84 1.23 1.91 1.23 3.22 0 4.61-2.805 5.625-5.475 5.92.42.36.81 1.096.81 2.22 0 1.606-.015 2.896-.015 3.286 0 .315.21.69.825.57C20.565 22.092 24 17.592 24 12.297c0-6.627-5.373-12-12-12" />
            </svg>
            源码
          </a>
        </span>
      </div>
    </footer>
  </div>
</template>

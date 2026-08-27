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
    <router-view />
    <footer v-if="!isConsole" class="site-footer">
      <div class="site-footer-inner">
        <span>{{ (siteConfig && siteConfig.footer_copyright) || '© 2026 OpenBlog' }}</span>
        <span class="site-footer-sep">|</span>
        <a class="site-footer-icp" href="https://beian.miit.gov.cn/" target="_blank" rel="noopener noreferrer">粤ICP备2026033788号</a>
      </div>
    </footer>
  </div>
</template>

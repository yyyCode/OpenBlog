<script setup>
import { computed, onMounted, provide, ref } from 'vue'
import { useRoute } from 'vue-router'
import BlogHeader from './components/BlogHeader.vue'
import WelcomeGate from './components/WelcomeGate.vue'
import BackToTop from './components/BackToTop.vue'
import WidgetsDrawer from './components/WidgetsDrawer.vue'
import RightDock from './components/RightDock.vue'
import Live2dCharacter from './components/Live2dCharacter.vue'
import { postSiteVisit } from './api/site'
import { fetchSiteConfig } from './api/site'
import { fetchPublicProfile } from './api/profile'

const route = useRoute()
const isConsole = computed(() => route.path.startsWith('/console'))
const widgetsOpen = ref(false)
const profile = ref(null)
const siteConfig = ref({})

// 提供 siteConfig 给所有子组件
provide('siteConfig', siteConfig)

function toggleWidgets() {
  widgetsOpen.value = !widgetsOpen.value
}

onMounted(() => {
  postSiteVisit().catch(() => {})
})

onMounted(async () => {
  if (isConsole.value) return
  try {
    profile.value = await fetchPublicProfile()
  } catch {
    profile.value = null
  }
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
    <BlogHeader v-if="!isConsole" @toggle-widgets="toggleWidgets" />
    <RightDock v-if="!isConsole" @toggle-profile="toggleWidgets" />
    <WidgetsDrawer v-if="!isConsole" :open="widgetsOpen" :profile="profile" @close="widgetsOpen = false" />
    <BackToTop v-if="!isConsole" />
    <Live2dCharacter v-if="!isConsole" />
    <router-view />
    <footer v-if="!isConsole" class="site-footer">
      <div class="site-footer-inner">
        <span>{{ (siteConfig && siteConfig.footer_copyright) || '© 2026 OpenBlog' }}</span>
      </div>
    </footer>
  </div>
</template>

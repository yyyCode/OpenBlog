<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import BlogHeader from './components/BlogHeader.vue'
import WelcomeGate from './components/WelcomeGate.vue'
import BackToTop from './components/BackToTop.vue'
import WidgetsDrawer from './components/WidgetsDrawer.vue'
import RightDock from './components/RightDock.vue'
import { postSiteVisit } from './api/site'
import { fetchPublicProfile } from './api/profile'

const route = useRoute()
const isConsole = computed(() => route.path.startsWith('/console'))
const widgetsOpen = ref(false)
const profile = ref(null)

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
})
</script>

<template>
  <div class="blog-app">
    <WelcomeGate v-if="!isConsole" />
    <BlogHeader v-if="!isConsole" @toggle-widgets="toggleWidgets" />
    <RightDock v-if="!isConsole" @toggle-profile="toggleWidgets" />
    <WidgetsDrawer v-if="!isConsole" :open="widgetsOpen" :profile="profile" @close="widgetsOpen = false" />
    <BackToTop v-if="!isConsole" />
    <router-view />
  </div>
</template>

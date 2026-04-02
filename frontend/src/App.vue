<script setup>
import { computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import BlogHeader from './components/BlogHeader.vue'
import WelcomeGate from './components/WelcomeGate.vue'
import BackToTop from './components/BackToTop.vue'
import { postSiteVisit } from './api/site'

const route = useRoute()
const isConsole = computed(() => route.path.startsWith('/console'))

onMounted(() => {
  postSiteVisit().catch(() => {})
})
</script>

<template>
  <div class="blog-app">
    <WelcomeGate v-if="!isConsole" />
    <BlogHeader v-if="!isConsole" />
    <BackToTop v-if="!isConsole" />
    <router-view />
  </div>
</template>

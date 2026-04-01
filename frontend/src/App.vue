<script setup>
import { onMounted } from 'vue'
import BlogHeader from './components/BlogHeader.vue'
import WelcomeGate from './components/WelcomeGate.vue'
import BackToTop from './components/BackToTop.vue'
import { postSiteVisit } from './api/site'

onMounted(() => {
  postSiteVisit().catch(() => {})
})
</script>

<template>
  <div class="blog-app">
    <WelcomeGate />
    <BlogHeader />
    <BackToTop />
    <router-view />
  </div>
</template>

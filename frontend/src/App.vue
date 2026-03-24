<script setup>
import BlogHeader from './components/BlogHeader.vue'
import WelcomeGate from './components/WelcomeGate.vue'
import BackToTop from './components/BackToTop.vue'
</script>

<template>
  <div class="blog-app">
    <WelcomeGate />
    <BlogHeader />
    <BackToTop />
    <router-view />
  </div>
</template>

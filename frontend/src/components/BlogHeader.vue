<template>
  <header class="site-top-bar" role="banner">
    <div class="site-top-bar-inner">
      <router-link class="site-brand" to="/">思考空间</router-link>

      <nav class="site-nav" aria-label="主导航">
        <button
          type="button"
          class="site-nav-link site-nav-link-btn site-nav-widgets-btn"
          @click="emit('toggle-widgets')"
          aria-label="打开个人信息侧栏"
          title="个人信息"
        >
          <span class="site-nav-widgets-ico" aria-hidden="true">☰</span>
          <span class="site-nav-widgets-text">个人</span>
        </button>
        <router-link class="site-nav-link" to="/" :class="{ active: isActive('/') }">首页</router-link>
        <router-link class="site-nav-link" to="/all" :class="{ active: isActive('/all') }">博客</router-link>
        <router-link class="site-nav-link" to="/about" :class="{ active: isActive('/about') }">关于</router-link>
        <a
          class="site-nav-link"
          href="https://github.com/yyyCode/OpenBlog.git"
          target="_blank"
          rel="noopener noreferrer"
        >
          源码
        </a>
        <button type="button" class="site-nav-link site-nav-link-btn" @click="onToggle">
          {{ isDark ? '浅色' : '深色' }}
        </button>
      </nav>
    </div>
  </header>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { toggleTheme as applyToggle } from '../theme'

const isDark = ref(false)
const route = useRoute()
const emit = defineEmits(['toggle-widgets'])

function sync() {
  isDark.value = document.documentElement.getAttribute('data-theme') === 'dark'
}

onMounted(() => {
  sync()
})

function onToggle() {
  applyToggle()
  sync()
}

function isActive(path) {
  return route.path === path
}
</script>

<template>
  <header class="site-top-bar" role="banner">
    <div class="site-top-bar-inner">
      <router-link class="site-brand" to="/">烧仙草冰室</router-link>

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
        <div ref="dropdownRoot" class="site-nav-dropdown">
          <button
            type="button"
            class="site-nav-link site-nav-link-btn site-nav-dropdown-trigger"
            :aria-expanded="dropdownOpen"
            aria-haspopup="menu"
            @click.stop="toggleDropdown"
          >
            AI实验室 <span class="site-nav-dropdown-caret" aria-hidden="true">▾</span>
          </button>

          <div v-if="dropdownOpen" class="site-nav-dropdown-menu" role="menu">
            <a
              class="site-nav-dropdown-item"
              href="http://ai.wecode.xin/"
              target="_blank"
              rel="noopener noreferrer"
              role="menuitem"
              @click="closeDropdown"
            >
              AI工作平台
            </a>
          </div>
        </div>
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
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { toggleTheme as applyToggle } from '../theme'

const isDark = ref(false)
const route = useRoute()
const emit = defineEmits(['toggle-widgets'])

const dropdownOpen = ref(false)
const dropdownRoot = ref(null)

function sync() {
  isDark.value = document.documentElement.getAttribute('data-theme') === 'dark'
}

onMounted(() => {
  sync()
})

function toggleDropdown() {
  dropdownOpen.value = !dropdownOpen.value
}

function closeDropdown() {
  dropdownOpen.value = false
}

function onDocClick(e) {
  const el = dropdownRoot.value
  if (!el) return
  // 点击下拉触发器或菜单内部：不收起
  const target = e?.target
  if (target && el.contains(target)) return
  closeDropdown()
}

onMounted(() => {
  document.addEventListener('click', onDocClick)
})

onBeforeUnmount(() => {
  document.removeEventListener('click', onDocClick)
})

function onToggle() {
  applyToggle()
  sync()
}

function isActive(path) {
  return route.path === path
}
</script>

<style scoped>
.site-nav-dropdown {
  position: relative;
  display: inline-flex;
}

.site-nav-dropdown-trigger {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  white-space: nowrap;
}

.site-nav-dropdown-caret {
  font-size: 12px;
  opacity: 0.85;
  transform: translateY(-1px);
}

.site-nav-dropdown-menu {
  position: absolute;
  left: 0;
  top: calc(100% + 8px);
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: 10px;
  padding: 6px;
  min-width: 160px;
  box-shadow: var(--shadow);
  z-index: 1000;
}

.site-nav-dropdown-item {
  display: block;
  text-decoration: none;
  color: var(--muted);
  font-weight: 600;
  font-size: 13px;
  padding: 10px 12px;
  border-radius: 8px;
  transition:
    background 0.15s ease,
    color 0.15s ease;
}

.site-nav-dropdown-item:hover {
  color: var(--text);
  background: var(--accent-bg);
}
</style>

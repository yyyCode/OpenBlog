<template>
  <header class="site-top-bar" role="banner">
    <div class="site-top-bar-inner">
      <router-link class="site-brand" to="/" aria-label="烧仙草冰室，返回首页">
        <span class="site-brand-logo" aria-hidden="true">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
            <!-- cup -->
            <path d="M7 6h10l-1.2 14.2a2 2 0 0 1-2 1.8H10.2a2 2 0 0 1-2-1.8L7 6z" />
            <!-- rim -->
            <path d="M6.2 6h11.6" />
            <!-- ice cubes -->
            <path d="M10 9.2h2.6v2.6H10z" />
            <path d="M13.6 10.6h2.6v2.6h-2.6z" />
            <!-- grass jelly layer -->
            <path d="M9.2 15.2c1.1-.8 2.4-.8 3.5 0s2.4.8 3.5 0" />
          </svg>
        </span>
        <span class="site-brand-text">烧仙草冰室</span>
      </router-link>

      <nav class="site-nav" aria-label="主导航">
        <button
          type="button"
          class="site-nav-link site-nav-link-btn site-nav-widgets-btn"
          @click="emit('toggle-widgets')"
          aria-label="打开个人信息侧栏"
          title="个人信息"
        >
          <span class="site-nav-ico" aria-hidden="true"><Menu /></span>
          <span class="site-nav-widgets-text">个人</span>
        </button>
        <router-link class="site-nav-link" to="/" :class="{ active: isActive('/') }">
          <span class="site-nav-ico" aria-hidden="true"><House /></span>
          首页
        </router-link>
        <router-link class="site-nav-link" to="/all" :class="{ active: isActive('/all') }">
          <span class="site-nav-ico" aria-hidden="true"><Notebook /></span>
          博客
        </router-link>
        <router-link class="site-nav-link" to="/about" :class="{ active: isActive('/about') }">
          <span class="site-nav-ico" aria-hidden="true"><InfoFilled /></span>
          关于
        </router-link>
        <div ref="dropdownRoot" class="site-nav-dropdown">
          <button
            type="button"
            class="site-nav-link site-nav-link-btn site-nav-dropdown-trigger"
            :aria-expanded="dropdownOpen"
            aria-haspopup="menu"
            @click.stop="toggleDropdown"
          >
            <span class="site-nav-ico" aria-hidden="true"><MagicStick /></span>
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
          <span class="site-nav-ico" aria-hidden="true"><Link /></span>
          源码
        </a>
        <button type="button" class="site-nav-link site-nav-link-btn" @click="onToggle">
          <span class="site-nav-ico" aria-hidden="true">
            <Sunny v-if="isDark" />
            <Moon v-else />
          </span>
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
import { House, InfoFilled, Link, MagicStick, Menu, Moon, Notebook, Sunny } from '@element-plus/icons-vue'

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

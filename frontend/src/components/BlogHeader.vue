<template>
  <header class="site-top-bar" role="banner">
    <div class="site-top-bar-inner">
      <button
        type="button"
        class="theme-toggle"
        :aria-label="isDark ? '切换到浅色模式' : '切换到深色模式'"
        @click="onToggle"
      >
        <span class="theme-toggle-icon" aria-hidden="true">{{ isDark ? '☀' : '☽' }}</span>
        <span class="theme-toggle-text">{{ isDark ? '浅色' : '深色' }}</span>
      </button>
    </div>
  </header>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { toggleTheme as applyToggle } from '../theme'

const isDark = ref(false)

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
</script>

<template>
  <div class="right-dock" :class="{ open: expanded }" @mouseenter="expanded = true" @mouseleave="onLeave">
    <button type="button" class="right-dock-btn" @click="onToggle" :aria-label="isDark ? '切换到浅色模式' : '切换到深色模式'">
      <span class="right-dock-ico" aria-hidden="true">{{ isDark ? '☀' : '☽' }}</span>
      <span class="right-dock-text">{{ isDark ? '浅色' : '深色' }}</span>
    </button>

    <button type="button" class="right-dock-btn" aria-label="公告">
      <span class="right-dock-ico" aria-hidden="true">
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <path d="M13 3v18" />
          <path d="M7 8h10l-3 5 3 5H7" />
        </svg>
      </span>
      <span class="right-dock-text">公告</span>
    </button>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { toggleTheme as applyToggle } from '../theme'

const expanded = ref(false)
const isDark = ref(false)

function sync() {
  isDark.value = document.documentElement.getAttribute('data-theme') === 'dark'
}

function onToggle() {
  applyToggle()
  sync()
}

function onLeave() {
  expanded.value = false
}

onMounted(() => {
  sync()
})
</script>

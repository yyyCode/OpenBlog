<template>
  <header class="site-top-bar" role="banner">
    <div class="site-top-bar-inner">
      <div class="site-top-actions">
        <button type="button" class="top-action-btn" aria-label="面试内容" @click="onInterviewClick">
          <span class="top-action-ico" aria-hidden="true">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M9 2h6v3H9z" />
              <path d="M7 5h10a2 2 0 0 1 2 2v12a3 3 0 0 1-3 3H8a3 3 0 0 1-3-3V7a2 2 0 0 1 2-2z" />
              <path d="M9 11h6M9 15h6" />
            </svg>
          </span>
          面试内容
        </button>
      </div>
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
import { showMessage } from '../utils/message'

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

function onInterviewClick() {
  showMessage('功能暂未开放')
}
</script>

<template>
  <header class="site-top-bar" role="banner">
    <div class="site-top-bar-inner">
      <div class="site-top-actions">
        <a
          class="top-action-btn"
          href="https://github.com/yyyCode/OpenBlog.git"
          target="_blank"
          rel="noopener noreferrer"
          aria-label="项目源码（GitHub）"
        >
          <span class="top-action-ico" aria-hidden="true">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
              <path
                d="M12 .5C5.73.5.75 5.67.75 12.09c0 5.15 3.28 9.52 7.83 11.06.57.11.78-.25.78-.55 0-.27-.01-1.17-.02-2.12-3.18.71-3.85-1.39-3.85-1.39-.52-1.36-1.28-1.72-1.28-1.72-1.05-.74.08-.73.08-.73 1.16.08 1.77 1.23 1.77 1.23 1.03 1.8 2.7 1.28 3.36.98.1-.77.4-1.28.73-1.57-2.54-.3-5.21-1.3-5.21-5.79 0-1.28.44-2.33 1.16-3.15-.12-.29-.5-1.48.11-3.08 0 0 .95-.31 3.11 1.2.9-.26 1.86-.39 2.82-.39.96 0 1.92.13 2.82.39 2.16-1.51 3.11-1.2 3.11-1.2.61 1.6.23 2.79.11 3.08.72.82 1.16 1.87 1.16 3.15 0 4.5-2.68 5.49-5.23 5.79.41.36.78 1.07.78 2.17 0 1.57-.02 2.83-.02 3.22 0 .3.21.67.79.55 4.55-1.54 7.83-5.91 7.83-11.06C23.25 5.67 18.27.5 12 .5z"
              />
            </svg>
          </span>
          项目源码
        </a>
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

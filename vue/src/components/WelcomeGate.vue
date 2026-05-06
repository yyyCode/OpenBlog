<template>
  <Teleport to="body">
    <div
      v-if="!dismissed"
      class="welcome-overlay"
      role="dialog"
      aria-modal="true"
      aria-labelledby="welcome-title"
    >
      <div class="welcome-dialog card">
        <h2 id="welcome-title" class="welcome-title">欢迎来到我的博客</h2>
        <p class="welcome-desc">
          这里记录学习与项目中的笔记与思考。点击「进入」开始浏览。
        </p>
        <button type="button" class="btn primary welcome-enter" @click="enter">
          进入
        </button>
      </div>
    </div>
  </Teleport>
</template>

<script setup>
import { ref, watch } from 'vue'

const STORAGE_KEY = 'openblog-welcome-dismissed'

function readDismissed() {
  try {
    return sessionStorage.getItem(STORAGE_KEY) === '1'
  } catch {
    return false
  }
}

const dismissed = ref(readDismissed())

watch(
  dismissed,
  (ok) => {
    if (typeof document === 'undefined') return
    document.body.style.overflow = ok ? '' : 'hidden'
  },
  { immediate: true }
)

function enter() {
  try {
    sessionStorage.setItem(STORAGE_KEY, '1')
  } catch {
    /* ignore */
  }
  dismissed.value = true
}
</script>

<style scoped>
.welcome-overlay {
  position: fixed;
  inset: 0;
  z-index: 10050;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: rgba(0, 0, 0, 0.42);
  backdrop-filter: blur(6px);
  -webkit-backdrop-filter: blur(6px);
}

.welcome-dialog {
  width: min(400px, 100%);
  padding: 28px 24px 24px;
  text-align: center;
  box-shadow: 0 16px 48px rgba(0, 0, 0, 0.18);
}

.welcome-title {
  margin: 0;
  font-size: 22px;
  font-weight: 950;
  letter-spacing: -0.3px;
  color: var(--text);
}

.welcome-desc {
  margin: 14px 0 0;
  font-size: 14px;
  line-height: 1.65;
  color: var(--muted);
}

.welcome-enter {
  margin-top: 22px;
  min-width: 120px;
  padding: 10px 22px;
  font-weight: 800;
}
</style>

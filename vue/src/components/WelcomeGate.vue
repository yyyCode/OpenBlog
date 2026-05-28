<template>
  <Teleport to="body">
    <Transition name="welcome">
      <div
        v-if="!dismissed"
        class="welcome-overlay"
        role="dialog"
        aria-modal="true"
        aria-labelledby="welcome-title"
      >
        <div class="welcome-dialog card">
          <div class="welcome-illust" aria-hidden="true">
            <div class="welcome-illust-bg" />
            <svg class="welcome-illust-svg" viewBox="0 0 200 140" fill="none">
              <!-- Desk -->
              <rect x="40" y="90" width="120" height="8" rx="4" fill="currentColor" opacity="0.25" />
              <rect x="54" y="98" width="8" height="28" rx="2" fill="currentColor" opacity="0.2" />
              <rect x="138" y="98" width="8" height="28" rx="2" fill="currentColor" opacity="0.2" />
              <!-- Monitor -->
              <rect x="72" y="44" width="56" height="38" rx="5" fill="currentColor" opacity="0.18" />
              <rect x="76" y="48" width="48" height="28" rx="2" fill="currentColor" opacity="0.1" />
              <rect x="91" y="86" width="18" height="4" rx="2" fill="currentColor" opacity="0.2" />
              <rect x="96" y="86" width="8" height="6" rx="1" fill="currentColor" opacity="0.15" />
              <!-- Coffee cup -->
              <rect x="144" y="72" width="14" height="16" rx="4" fill="currentColor" opacity="0.2" />
              <path d="M158 74c4 0 7 2 7 5s-3 5-7 5" stroke="currentColor" stroke-width="2" opacity="0.2" />
              <rect x="146" y="70" width="10" height="2" rx="1" fill="currentColor" opacity="0.25" />
              <!-- Small plant -->
              <rect x="48" y="78" width="8" height="12" rx="2" fill="currentColor" opacity="0.18" />
              <circle cx="52" cy="74" r="6" fill="currentColor" opacity="0.15" />
              <circle cx="49" cy="71" r="5" fill="currentColor" opacity="0.12" />
              <circle cx="55" cy="72" r="4" fill="currentColor" opacity="0.12" />
              <!-- Stars/sparkles -->
              <path
                d="M36 22l2 6 6 1-5 4 2 6-5-3-5 3 2-6-5-4 6-1 2-6z"
                fill="currentColor"
                opacity="0.25"
              />
              <path
                d="M168 18l1 3 3 0.5-2.5 2 1 3-2.5-1.5-2.5 1.5 1-3-2.5-2 3-0.5 1-3z"
                fill="currentColor"
                opacity="0.2"
              />
              <path
                d="M24 70l1 2 2 0.3-1.5 1.3 0.5 2-1.5-1-1.5 1 0.5-2-1.5-1.3 2-0.3 1-2z"
                fill="currentColor"
                opacity="0.18"
              />
            </svg>
          </div>
          <div class="welcome-body">
            <h2 id="welcome-title" class="welcome-title">欢迎来到我的博客</h2>
            <p class="welcome-desc">
              这里记录学习与项目中的笔记与思考，点击「进入」开始浏览。
            </p>
            <button type="button" class="btn primary welcome-enter" @click="enter">
              进 入
            </button>
          </div>
        </div>
      </div>
    </Transition>
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
  background: rgba(0, 0, 0, 0.45);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
}

.welcome-dialog {
  width: min(420px, 100%);
  padding: 0;
  text-align: center;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.2);
  overflow: hidden;
  border-radius: 18px;
}

.welcome-illust {
  position: relative;
  height: 150px;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}

.welcome-illust-bg {
  position: absolute;
  inset: 0;
  background: linear-gradient(135deg, #f0e6ff 0%, #e6f0ff 40%, #f5e6ff 70%, #e6f5ff 100%);
  opacity: 0.9;
}

[data-theme='dark'] .welcome-illust-bg {
  background: linear-gradient(135deg, #2a2040 0%, #1a2a40 40%, #2a1a40 70%, #1a2a45 100%);
}

.welcome-illust-svg {
  position: relative;
  z-index: 1;
  width: 180px;
  height: 130px;
  color: #7c3aed;
  opacity: 0.6;
}

[data-theme='dark'] .welcome-illust-svg {
  color: #a78bfa;
  opacity: 0.7;
}

.welcome-body {
  padding: 24px 24px 28px;
}

.welcome-title {
  margin: 0;
  font-size: 24px;
  font-weight: 800;
  letter-spacing: -0.02em;
  color: var(--text);
  line-height: 1.3;
}

.welcome-desc {
  margin: 12px 0 0;
  font-size: 15px;
  line-height: 1.7;
  color: var(--muted);
}

.welcome-enter {
  margin-top: 24px;
  min-width: 140px;
  padding: 12px 28px;
  font-weight: 800;
  font-size: 15px;
  letter-spacing: 0.08em;
  transition:
    transform 0.15s ease,
    box-shadow 0.15s ease;
}

.welcome-enter:hover {
  transform: translateY(-1px);
  box-shadow: 0 8px 24px rgba(170, 59, 255, 0.35);
}

/* Transition */
.welcome-enter-active {
  transition: opacity 0.35s ease;
}

.welcome-leave-active {
  transition: opacity 0.25s ease, transform 0.25s ease;
}

.welcome-enter-from {
  opacity: 0;
}

.welcome-leave-to {
  opacity: 0;
  transform: scale(0.96);
}
</style>

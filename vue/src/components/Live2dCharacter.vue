<template>
  <div class="live2d-wrapper" :class="{ 'live2d-wrapper--hidden': dismissed }">
    <div ref="containerRef" class="live2d-container" />
    <button
      type="button"
      class="live2d-toggle"
      :class="{ 'live2d-toggle--loading': loading }"
      :aria-label="dismissed ? '召唤看板娘' : '隐藏看板娘'"
      :title="loading ? '看板娘加载中…' : dismissed ? '召唤看板娘' : '隐藏看板娘'"
      @click="toggle"
    >
      <svg v-if="loading" class="live2d-spinner" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
        <circle cx="12" cy="12" r="10" stroke-dasharray="32" stroke-dashoffset="32" />
      </svg>
      <svg v-else width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2z" />
        <circle cx="8.5" cy="10" r="1.5" />
        <circle cx="15.5" cy="10" r="1.5" />
        <path d="M8 14c0 0 1.5 2 4 2s4-2 4-2" />
      </svg>
    </button>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'

const STORAGE_KEY = 'openblog-live2d-hidden'

function readHidden() {
  try {
    return sessionStorage.getItem(STORAGE_KEY) === '1'
  } catch {
    return false
  }
}

function isDark() {
  if (typeof document === 'undefined') return false
  return document.documentElement.getAttribute('data-theme') === 'dark'
}

const dismissed = ref(readHidden())
const loading = ref(false)
const containerRef = ref(null)
let oml2dInstance = null

function toggle() {
  if (loading.value) return
  dismissed.value = !dismissed.value
  try {
    sessionStorage.setItem(STORAGE_KEY, dismissed.value ? '1' : '0')
  } catch {
    /* ignore */
  }

  if (!oml2dInstance) return
  if (dismissed.value) {
    oml2dInstance.stageSlideOut()
  } else {
    oml2dInstance.stageSlideIn()
  }
}

function makeTipsStyle(dark) {
  return {
    width: 220,
    height: 70,
    fontSize: '13px',
    lineHeight: '1.5',
    borderRadius: '12px',
    background: dark ? 'rgba(34,34,36,0.88)' : 'rgba(255,255,255,0.85)',
    border: dark ? '1px solid rgba(255,255,255,0.12)' : '1px solid rgba(0,0,0,0.08)',
    color: dark ? '#e8e8ea' : '#333',
    backdropFilter: 'saturate(1.1) blur(14px)',
    WebkitBackdropFilter: 'saturate(1.1) blur(14px)',
    boxShadow: dark
      ? '0 4px 20px rgba(0,0,0,0.35)'
      : '0 4px 20px rgba(0,0,0,0.1)',
    marginTop: '-140px',
  }
}

onMounted(async () => {
  if (!containerRef.value) return

  loading.value = true

  const { loadOml2d } = await import('oh-my-live2d')

  oml2dInstance = loadOml2d({
    dockedPosition: 'right',
    mobileDisplay: true,
    primaryColor: '#aa3bff',
    sayHello: false,
    transitionTime: 800,
    models: [
      {
        name: 'Shizuku',
        path: 'https://fastly.jsdelivr.net/npm/live2d-widget-model-shizuku@1.0.5/assets/shizuku.model.json',
        position: [0, 10],
        scale: 0.1,
        stageStyle: { height: 340 },
        mobileScale: 0.08,
        mobilePosition: [0, 5],
      },
      {
        name: 'Wanko',
        path: 'https://fastly.jsdelivr.net/npm/live2d-widget-model-wanko@1.0.5/assets/wanko.model.json',
        position: [0, 10],
        scale: 0.1,
        stageStyle: { height: 340 },
        mobileScale: 0.08,
        mobilePosition: [0, 5],
      },
      {
        name: 'Miku',
        path: 'https://fastly.jsdelivr.net/npm/live2d-widget-model-miku@1.0.5/assets/miku.model.json',
        position: [0, 10],
        scale: 0.1,
        stageStyle: { height: 340 },
        mobileScale: 0.08,
        mobilePosition: [0, 5],
      },
    ],
    tips: {
      messageLine: 3,
      style: makeTipsStyle(isDark()),
      mobileStyle: { width: 180, height: 56, fontSize: '12px' },
      welcomeTips: {
        duration: 5000,
        message: {
          daybreak: '早上好！又是元气满满的一天~',
          morning: '上午好！一起学习新知识吧~',
          noon: '中午好！记得按时吃饭哦~',
          afternoon: '下午好！来杯奶茶提提神~',
          dusk: '傍晚好！今天辛苦了~',
          night: '晚上好！欢迎来到我的博客~',
          lateNight: '夜深了，还在充电呢？注意休息哦~',
          weeHours: '已经凌晨了！答应我别熬夜好吗~',
        },
      },
      idleTips: {
        wordTheDay: true,
        duration: 6000,
        interval: 18000,
      },
    },
    statusBar: { enable: false },
    menus: { disable: true },
    parentElement: containerRef.value,
  })

  oml2dInstance.onLoad(() => {
    loading.value = false
  })

  if (dismissed.value) {
    let firstLoad = true
    oml2dInstance.onLoad(() => {
      if (firstLoad) {
        oml2dInstance.stageSlideOut()
        firstLoad = false
      }
    })
  }
})
</script>

<style scoped>
.live2d-wrapper {
  position: fixed;
  right: 0;
  bottom: 60px;
  z-index: 9995;
  pointer-events: none;
}

.live2d-container {
  pointer-events: auto;
  transition: opacity 0.3s ease;
}

.live2d-wrapper--hidden .live2d-container {
  opacity: 0;
  pointer-events: none;
}

.live2d-toggle {
  position: fixed;
  right: max(18px, env(safe-area-inset-right, 0px));
  bottom: max(80px, calc(env(safe-area-inset-bottom, 0px) + 80px));
  z-index: 9999;
  width: 36px;
  height: 36px;
  border-radius: 50%;
  border: 1px solid var(--border);
  background: var(--card);
  color: var(--muted);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.08);
  backdrop-filter: saturate(1.05) blur(14px);
  -webkit-backdrop-filter: saturate(1.05) blur(14px);
  transition:
    color 0.15s ease,
    border-color 0.15s ease,
    transform 0.15s ease;
  pointer-events: auto;
}

.live2d-toggle:hover {
  color: var(--accent);
  border-color: rgba(170, 59, 255, 0.45);
  transform: scale(1.1);
}

.live2d-toggle--loading {
  cursor: wait;
  color: var(--accent);
}

.live2d-toggle--loading:hover {
  transform: none;
}

.live2d-wrapper--hidden .live2d-toggle {
  animation: live2d-toggle-pulse 2s ease-in-out infinite;
}

@keyframes live2d-toggle-pulse {
  0%,
  100% { box-shadow: 0 2px 10px rgba(0, 0, 0, 0.08); }
  50% { box-shadow: 0 2px 18px rgba(170, 59, 255, 0.3); }
}

@keyframes live2d-spin {
  to { transform: rotate(360deg); }
}

.live2d-spinner {
  animation: live2d-spin 1s linear infinite;
}

@media (max-width: 680px) {
  .live2d-wrapper {
    bottom: 100px;
    right: -10px;
  }

  .live2d-toggle {
    right: max(14px, env(safe-area-inset-right, 0px));
    bottom: max(120px, calc(env(safe-area-inset-bottom, 0px) + 120px));
    width: 32px;
    height: 32px;
  }
}
</style>

<template>
  <div>
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
        <button type="button" class="top-action-btn" aria-label="问题反馈" @click="openFeedback">
          <span class="top-action-ico" aria-hidden="true">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M4 4h16v16H4z" />
              <path d="M22 6l-10 7L2 6" />
            </svg>
          </span>
          问题反馈
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

    <Teleport to="body">
      <div v-if="feedbackOpen" class="ob-dialog-overlay" role="dialog" aria-modal="true" aria-label="问题反馈">
        <div class="ob-dialog card">
          <div class="ob-dialog-title">问题反馈</div>
          <div class="ob-dialog-desc">欢迎提交问题与建议（同一 IP 每天仅可提交一次）。</div>

          <div class="field" style="margin-top: 14px">
            <div class="label">提交者名字</div>
            <input v-model="fbName" class="input" type="text" maxlength="50" placeholder="例如：张三" />
          </div>
          <div class="field">
            <div class="label">内容</div>
            <textarea
              v-model="fbContent"
              class="textarea"
              rows="6"
              maxlength="2000"
              placeholder="请描述你遇到的问题或建议"
            ></textarea>
          </div>

          <div style="display: flex; gap: 10px; justify-content: flex-end; margin-top: 14px">
            <button type="button" class="btn" :disabled="fbLoading" @click="closeFeedback">取消</button>
            <button type="button" class="btn primary" :disabled="fbLoading || !canSubmit" @click="submitFeedback">
              {{ fbLoading ? '提交中…' : '提交' }}
            </button>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { toggleTheme as applyToggle } from '../theme'
import { showMessage } from '../utils/message'
import { createFeedback } from '../api/feedback'

const isDark = ref(false)
const feedbackOpen = ref(false)
const fbName = ref('')
const fbContent = ref('')
const fbLoading = ref(false)

const canSubmit = computed(() => {
  const n = (fbName.value || '').trim()
  const c = (fbContent.value || '').trim()
  return n.length > 0 && c.length > 0
})

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

function openFeedback() {
  feedbackOpen.value = true
}

function closeFeedback() {
  if (fbLoading.value) return
  feedbackOpen.value = false
}

async function submitFeedback() {
  if (!canSubmit.value || fbLoading.value) return
  fbLoading.value = true
  try {
    await createFeedback({
      submitterName: fbName.value.trim(),
      content: fbContent.value.trim()
    })
    showMessage('提交成功，感谢反馈')
    fbName.value = ''
    fbContent.value = ''
    feedbackOpen.value = false
  } catch (e) {
    const traceId = e?.traceId ? `（traceId ${e.traceId}）` : ''
    const prefix = e?.code ? `错误码 ${e.code}` : e?.httpStatus ? `HTTP ${e.httpStatus}` : ''
    const msg = e?.message || '提交失败'
    showMessage(`${prefix ? prefix + '：' : ''}${msg}${traceId}`)
  } finally {
    fbLoading.value = false
  }
}
</script>

<style scoped>
.ob-dialog-overlay {
  position: fixed;
  inset: 0;
  z-index: 10060;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: rgba(0, 0, 0, 0.42);
  backdrop-filter: blur(6px);
  -webkit-backdrop-filter: blur(6px);
}

.ob-dialog {
  width: min(520px, 100%);
  padding: 20px 18px 18px;
  box-shadow: 0 16px 48px rgba(0, 0, 0, 0.18);
}

.ob-dialog-title {
  font-size: 18px;
  font-weight: 950;
  color: var(--text);
}

.ob-dialog-desc {
  margin-top: 8px;
  font-size: 13px;
  line-height: 1.6;
  color: var(--muted);
}
</style>

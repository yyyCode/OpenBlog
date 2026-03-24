<template>
  <div class="blog-container">
    <div class="admin-center">
      <div class="card admin-login-card">
        <div class="card-body" style="padding: 22px">
          <div style="font-weight: 1000; font-size: 22px; margin-bottom: 18px">
            后台登录
          </div>

          <div class="field">
            <div class="label">账号（用户名或邮箱）</div>
            <input v-model="account" class="input" type="text" />
          </div>

          <div class="field">
            <div class="label">密码</div>
            <input v-model="password" class="input" type="password" />
          </div>

          <div v-if="sliderEnabled" class="field">
            <div class="label">滑动验证</div>
            <div
              ref="trackRef"
              class="slider-track"
              @mousedown.prevent="onTrackDown"
              @touchstart.prevent="onTrackDown"
            >
              <div class="slider-fill" :style="{ width: `${sliderPct}%` }" />
              <div
                class="slider-thumb"
                :class="{ done: sliderVerified }"
                :style="{ left: `calc(${sliderPct}% - 22px)` }"
              >
                <span v-if="sliderVerified" class="slider-check">✓</span>
                <span v-else class="slider-hint">→</span>
              </div>
              <div class="slider-label">{{ sliderVerified ? '已通过' : '滑到右端' }}</div>
            </div>
            <button type="button" class="btn" style="margin-top: 8px" @click="resetSlider">
              重置验证
            </button>
          </div>

          <div v-if="error" class="error">{{ error }}</div>

          <button class="btn primary" style="width: 100%; margin-top: 14px" @click="doLogin">
            登录
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { completeSliderChallenge, fetchSliderChallenge, login } from '../api/admin'

const router = useRouter()
const account = ref('')
const password = ref('')
const error = ref('')
const sliderEnabled = ref(false)
const challengeId = ref('')
const sliderPct = ref(0)
const sliderVerified = ref(false)
const trackRef = ref(null)
const dragging = ref(false)

async function loadSliderChallenge() {
  sliderPct.value = 0
  sliderVerified.value = false
  error.value = ''
  try {
    const c = await fetchSliderChallenge()
    sliderEnabled.value = !!c?.enabled
    challengeId.value = c?.challengeId || ''
  } catch {
    sliderEnabled.value = true
    challengeId.value = ''
    error.value = '验证初始化失败，请刷新页面'
  }
}

function resetSlider() {
  sliderPct.value = 0
  sliderVerified.value = false
  loadSliderChallenge()
}

function setPctFromClientX(clientX) {
  const el = trackRef.value
  if (!el) return
  const r = el.getBoundingClientRect()
  const w = r.width
  if (w <= 0) return
  let p = ((clientX - r.left) / w) * 100
  p = Math.max(0, Math.min(100, p))
  sliderPct.value = p
}

async function tryCompleteAtEnd() {
  if (sliderVerified.value || !sliderEnabled.value || !challengeId.value) return
  if (sliderPct.value < 95) {
    sliderPct.value = 0
    return
  }
  try {
    await completeSliderChallenge(challengeId.value)
    sliderVerified.value = true
    sliderPct.value = 100
    error.value = ''
  } catch (e) {
    sliderPct.value = 0
    error.value = e?.message || '验证失败，请重试'
  }
}

function onTrackDown(e) {
  if (sliderVerified.value || !sliderEnabled.value) return
  dragging.value = true
  const clientX = e.touches ? e.touches[0].clientX : e.clientX
  setPctFromClientX(clientX)
}

function onMove(e) {
  if (!dragging.value || sliderVerified.value) return
  const clientX = e.touches ? e.touches[0].clientX : e.clientX
  setPctFromClientX(clientX)
}

function onUp() {
  if (!dragging.value) return
  dragging.value = false
  tryCompleteAtEnd()
}

onMounted(() => {
  loadSliderChallenge()
  window.addEventListener('mousemove', onMove)
  window.addEventListener('mouseup', onUp)
  window.addEventListener('touchmove', onMove, { passive: true })
  window.addEventListener('touchend', onUp)
})

onBeforeUnmount(() => {
  window.removeEventListener('mousemove', onMove)
  window.removeEventListener('mouseup', onUp)
  window.removeEventListener('touchmove', onMove)
  window.removeEventListener('touchend', onUp)
})

async function doLogin() {
  error.value = ''
  try {
    const resp = await login(
      account.value,
      password.value,
      sliderEnabled.value && sliderVerified.value ? challengeId.value : ''
    )
    localStorage.setItem('accessToken', resp.accessToken)
    localStorage.setItem('refreshToken', resp.refreshToken)
    router.push('/admin')
  } catch (e) {
    error.value = e?.message || '登录失败'
    await loadSliderChallenge()
  }
}
</script>

<style scoped>
.slider-track {
  position: relative;
  height: 44px;
  border-radius: 999px;
  background: var(--surface, #f4f4f5);
  border: 1px solid var(--border, #e4e4e7);
  overflow: hidden;
  user-select: none;
  touch-action: none;
  cursor: pointer;
}

.slider-fill {
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  background: linear-gradient(
    90deg,
    rgba(99, 102, 241, 0.2),
    rgba(34, 197, 94, 0.25)
  );
  pointer-events: none;
}

.slider-thumb {
  position: absolute;
  top: 3px;
  width: 38px;
  height: 38px;
  border-radius: 999px;
  background: linear-gradient(135deg, var(--accent, #6366f1), var(--accent2, #22c55e));
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 800;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.12);
  transition: left 0.08s linear;
}

.slider-thumb.done {
  background: linear-gradient(135deg, #22c55e, #16a34a);
}

.slider-check {
  font-size: 18px;
  line-height: 1;
}

.slider-hint {
  font-size: 16px;
  opacity: 0.95;
}

.slider-label {
  position: absolute;
  right: 14px;
  top: 50%;
  transform: translateY(-50%);
  font-size: 13px;
  color: var(--muted, #71717a);
  pointer-events: none;
}
</style>

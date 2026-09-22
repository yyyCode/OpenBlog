<template>
  <div v-if="visible" class="slider-captcha">
    <div class="slider-captcha-head">
      <span class="slider-captcha-title">拖动滑块，把缺口补上</span>
      <button
        type="button"
        class="slider-captcha-refresh"
        :disabled="loading || dragging"
        @click="refresh"
      >{{ loading ? '加载中…' : '换一张' }}</button>
    </div>

    <!--
      stage 用 aspect-ratio 锁定底图比例（320:160），因此无需等图片 onload 就能拿到正确尺寸，
      拖动时的比例换算随时可用。touch-action:none 防移动端拖动时误触发页面滚动。
    -->
    <div
      ref="stageRef"
      class="slider-captcha-stage"
      @pointermove="onPointerMove"
      @pointerup="onPointerUp"
      @pointercancel="onPointerUp"
    >
      <img
        v-if="background"
        class="slider-captcha-bg"
        :src="background"
        alt=""
        draggable="false"
      />
      <img
        v-if="slider"
        class="slider-captcha-piece"
        :src="slider"
        alt=""
        draggable="false"
        :style="pieceStyle"
        @pointerdown="onPointerDown"
      />
      <div v-if="loading" class="slider-captcha-mask">加载中…</div>
    </div>

    <div class="slider-captcha-foot">
      <span class="slider-captcha-hint" :class="{ 'is-error': !!hint }">
        {{ hint || (dragging ? '松手即校验' : '按住滑块向右拖动') }}
      </span>
      <button type="button" class="slider-captcha-cancel" @click="cancel">取消</button>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, ref } from 'vue'
import { fetchSliderChallenge, completeSlider } from '../api/admin'

// 与后端 SliderImageGenerator 的底图/滑块尺寸保持一致，用于 clientX → 图像像素的换算
const IMAGE_WIDTH = 320
const IMAGE_HEIGHT = 160
const PIECE = 48
const MAX_PIECE_X = IMAGE_WIDTH - PIECE

const visible = ref(false)
const loading = ref(false)
const dragging = ref(false)
const background = ref('')
const slider = ref('')
const sliderY = ref(0)
const pieceX = ref(0)
const hint = ref('')

const stageRef = ref(null)

let challengeId = ''
let dragStartClientX = 0
let dragStartPieceX = 0
let dragStartTime = 0
let trail = []
// start() 返回的 promise 的 settle 句柄。同一时刻只会有一个待决的调用。
let pending = null

const pieceStyle = computed(() => ({
  left: `${(pieceX.value / IMAGE_WIDTH) * 100}%`,
  top: `${(sliderY.value / IMAGE_HEIGHT) * 100}%`,
  width: `${(PIECE / IMAGE_WIDTH) * 100}%`
}))

/**
 * 弹出面板并返回 Promise<challengeId | null>：null 表示服务端滑块关闭（直接发码）。
 * 用户取消时 reject；一次校验失败不会 settle，面板留在原地让用户重试。
 */
function start() {
  visible.value = true
  hint.value = ''
  resetPiece()
  return new Promise((resolve, reject) => {
    pending = { resolve, reject }
    loadChallenge()
  })
}

defineExpose({ start })

async function loadChallenge() {
  loading.value = true
  try {
    const data = await fetchSliderChallenge()
    if (!data?.enabled) {
      // 服务端滑块关闭：不发图，直接把"无需凭证"告诉调用方
      settle(null)
      return
    }
    challengeId = data.challengeId
    background.value = data.background
    slider.value = data.slider
    sliderY.value = data.sliderY ?? 0
    resetPiece()
  } catch (e) {
    // 拉不到挑战就不能校验，直接以错误 settle，由调用方展示原因
    fail(e)
  } finally {
    loading.value = false
  }
}

function resetPiece() {
  pieceX.value = 0
  trail = []
}

function refresh() {
  hint.value = ''
  loadChallenge()
}

function onPointerDown(e) {
  if (loading.value || !challengeId) return
  dragging.value = true
  dragStartClientX = e.clientX
  dragStartPieceX = pieceX.value
  dragStartTime = performance.now()
  trail = []
  pushSample(pieceX.value, 0)
  // 指针捕获：手指/光标移出 stage 也能继续收到 move，避免松手位置丢失
  e.currentTarget.setPointerCapture?.(e.pointerId)
  e.preventDefault()
}

function onPointerMove(e) {
  if (!dragging.value) return
  const rect = stageRef.value?.getBoundingClientRect()
  if (!rect?.width) return
  // 底图按容器宽度做了缩放，位移必须换算回图像像素，否则落点会随屏幕宽度漂移
  const next = dragStartPieceX + (e.clientX - dragStartClientX) * (IMAGE_WIDTH / rect.width)
  pieceX.value = Math.min(MAX_PIECE_X, Math.max(0, next))
  pushSample(pieceX.value, performance.now() - dragStartTime)
}

function onPointerUp() {
  if (!dragging.value) return
  dragging.value = false
  submit()
}

/**
 * 记录采样点。跳过 x 未变化的点：那些会形成零速度段，把速度变异系数拉低，
 * 反而让正常用户被判成"匀速机器"。后端要求点数 >= 5，故起步时先补一个原点。
 */
function pushSample(x, t) {
  const rounded = Math.round(x)
  const last = trail[trail.length - 1]
  if (last && last.x === rounded) return
  trail.push({ x: rounded, t: Math.round(t) })
}

async function submit() {
  if (trail.length < 2) {
    hint.value = '滑动距离太短，请拖到缺口处'
    refresh()
    return
  }
  loading.value = true
  try {
    await completeSlider(challengeId, Math.round(pieceX.value), trail)
    settle(challengeId)
  } catch (e) {
    // 失败不 settle：换一张图留在原地，让用户直接重试（多为拖太快或没对准）
    hint.value = e?.message || '验证未通过，请再试一次'
    refresh()
  } finally {
    loading.value = false
  }
}

function cancel() {
  if (pending) {
    const { reject } = pending
    pending = null
    // cancelled 标记让调用方与"真的出错了"区分开：用户主动取消不该弹错误提示
    const err = new Error('已取消滑动验证')
    err.cancelled = true
    reject(err)
  }
  hide()
}

function fail(e) {
  if (pending) {
    const { reject } = pending
    pending = null
    reject(e)
  }
  hide()
}

function settle(proof) {
  if (pending) {
    const { resolve } = pending
    pending = null
    resolve(proof)
  }
  hide()
}

function hide() {
  visible.value = false
  dragging.value = false
  challengeId = ''
  resetPiece()
}
</script>

<style scoped>
.slider-captcha {
  border: 1px solid var(--border);
  border-radius: 12px;
  background: var(--surface);
  padding: 10px;
  /* 与同表单内 .auth-field 的下间距一致；隐藏时不渲染根节点，不会留空档 */
  margin-bottom: 20px;
}

.slider-captcha-head,
.slider-captcha-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.slider-captcha-head {
  margin-bottom: 8px;
}

.slider-captcha-title {
  font-size: 13px;
  color: var(--text);
}

.slider-captcha-refresh,
.slider-captcha-cancel {
  border: none;
  background: none;
  color: var(--accent);
  font-size: 12px;
  cursor: pointer;
  padding: 2px 4px;
}

.slider-captcha-refresh:disabled {
  color: var(--muted);
  cursor: default;
}

.slider-captcha-stage {
  position: relative;
  width: 100%;
  aspect-ratio: 2 / 1;
  border-radius: 8px;
  overflow: hidden;
  background: var(--surface-soft);
  touch-action: none;
  user-select: none;
}

.slider-captcha-bg {
  display: block;
  width: 100%;
  height: 100%;
  pointer-events: none;
}

.slider-captcha-piece {
  position: absolute;
  aspect-ratio: 1;
  cursor: grab;
  touch-action: none;
  filter: drop-shadow(0 1px 4px rgba(0, 0, 0, 0.45));
}

.slider-captcha-mask {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.35);
  color: #fff;
  font-size: 13px;
}

.slider-captcha-foot {
  margin-top: 8px;
}

.slider-captcha-hint {
  font-size: 12px;
  color: var(--muted);
}

.slider-captcha-hint.is-error {
  color: #e5484d;
}
</style>

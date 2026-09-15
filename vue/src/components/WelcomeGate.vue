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
              <!-- 打字光标：屏幕里唯一会动的东西，像在等人敲下第一个字 -->
              <rect class="caret" x="83" y="54" width="3" height="12" rx="1.5" fill="currentColor" opacity="0.5" />
              <rect x="91" y="86" width="18" height="4" rx="2" fill="currentColor" opacity="0.2" />
              <rect x="96" y="86" width="8" height="6" rx="1" fill="currentColor" opacity="0.15" />
              <!-- Coffee cup -->
              <rect x="144" y="72" width="14" height="16" rx="4" fill="currentColor" opacity="0.2" />
              <path d="M158 74c4 0 7 2 7 5s-3 5-7 5" stroke="currentColor" stroke-width="2" opacity="0.2" />
              <rect x="146" y="70" width="10" height="2" rx="1" fill="currentColor" opacity="0.25" />
              <!-- 热气：两缕错开相位，比一缕更像"在冒" -->
              <path
                class="steam steam-1"
                d="M150 68c-4-5 4-8 0-13s4-8 0-13"
                stroke="currentColor"
                stroke-width="2.5"
                stroke-linecap="round"
                opacity="0.3"
              />
              <path
                class="steam steam-2"
                d="M156 67c-3-4 3-6 0-10s3-6 0-10"
                stroke="currentColor"
                stroke-width="2"
                stroke-linecap="round"
                opacity="0.3"
              />
              <!-- Small plant -->
              <rect x="48" y="78" width="8" height="12" rx="2" fill="currentColor" opacity="0.18" />
              <circle cx="52" cy="74" r="6" fill="currentColor" opacity="0.15" />
              <circle cx="49" cy="71" r="5" fill="currentColor" opacity="0.12" />
              <circle cx="55" cy="72" r="4" fill="currentColor" opacity="0.12" />
              <!-- Stars/sparkles -->
              <path
                class="sparkle sparkle-1"
                d="M36 22l2 6 6 1-5 4 2 6-5-3-5 3 2-6-5-4 6-1 2-6z"
                fill="currentColor"
                opacity="0.25"
              />
              <path
                class="sparkle sparkle-2"
                d="M168 18l1 3 3 0.5-2.5 2 1 3-2.5-1.5-2.5 1.5 1-3-2.5-2 3-0.5 1-3z"
                fill="currentColor"
                opacity="0.2"
              />
              <path
                class="sparkle sparkle-3"
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
  background: linear-gradient(135deg, #e6f0ff 0%, #f0f5ff 40%, #e6eeff 70%, #f0f5ff 100%);
  opacity: 0.9;
}

[data-theme='dark'] .welcome-illust-bg {
  background: linear-gradient(135deg, #1a2a3a 0%, #1a2435 40%, #1a2a3a 70%, #1a2435 100%);
}

.welcome-illust-svg {
  position: relative;
  z-index: 1;
  width: 180px;
  height: 130px;
  color: #3370ff;
  /* 注意：这里是整张插图的透明度上限，内部元素的 SVG opacity 属性会与它相乘。
     动效元素靠"峰值 opacity 拉高"来取得对比，而不是靠调大这个值——
     调大它会把桌子/显示器等静态部分一起加深，属于改动设计而非增强动效。 */
  opacity: 0.6;
}

[data-theme='dark'] .welcome-illust-svg {
  color: #4a7fff;
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
  box-shadow: 0 8px 24px rgba(51, 112, 255, 0.35);
}

/* ===== 入场编排 =====
   时间轴：遮罩模糊 0→10px 渐入 → 卡片上浮回弹 → 插画/标题/描述/按钮 每级错开 70ms。
   子元素用一次性 animation 而非 <Transition>：整块只在 !dismissed 时挂载，
   挂载即播放一轮，不需要 Vue 过渡钩子参与。 */
@keyframes welcome-card-in {
  0% {
    opacity: 0;
    transform: translateY(24px) scale(0.9);
  }
  62% {
    opacity: 1;
    /* 明显过冲，落定时才有分量 */
    transform: translateY(-8px) scale(1.02);
  }
  100% {
    opacity: 1;
    transform: none;
  }
}

@keyframes welcome-rise {
  from {
    opacity: 0;
    transform: translateY(20px);
  }
  to {
    opacity: 1;
    transform: none;
  }
}

.welcome-dialog {
  animation: welcome-card-in 0.55s cubic-bezier(0.22, 1, 0.36, 1) 120ms backwards;
}

.welcome-illust {
  animation: welcome-rise 0.5s ease-out 230ms backwards;
}

.welcome-title {
  animation: welcome-rise 0.5s ease-out 310ms backwards;
}

.welcome-desc {
  animation: welcome-rise 0.5s ease-out 390ms backwards;
}

.welcome-enter {
  animation: welcome-rise 0.5s ease-out 470ms backwards;
}

/* Transition（只作用于遮罩本身，子元素的错峰由上面的 animation 负责）
   压到 0.3s：遮罩早点落定，后面的错峰阶梯才看得清，否则整段都糊在渐入里 */
.welcome-enter-active {
  transition:
    opacity 0.3s ease,
    backdrop-filter 0.3s ease,
    -webkit-backdrop-filter 0.3s ease;
}

.welcome-leave-active {
  transition: opacity 0.25s ease, transform 0.25s ease;
}

.welcome-enter-from {
  opacity: 0;
  backdrop-filter: blur(0px);
  -webkit-backdrop-filter: blur(0px);
}

.welcome-leave-to {
  opacity: 0;
  transform: scale(0.96);
}

/* ===== 插画微动效 =====
   周期都压在 1–3s，弹窗被关掉之前正好能看到一轮；长周期动效在这里等于白做。 */
.welcome-illust-svg .steam,
.welcome-illust-svg .sparkle {
  /* SVG 子元素的 transform 默认以用户坐标系原点为基准，不加这行会绕着画布左上角甩飞 */
  transform-box: fill-box;
}

@keyframes welcome-steam {
  0% {
    opacity: 0;
    transform: translateY(5px) scaleY(0.8);
  }
  30% {
    opacity: 0.85;
  }
  100% {
    opacity: 0;
    transform: translateY(-9px) scaleY(1.25);
  }
}

.welcome-illust-svg .steam {
  transform-origin: 50% 100%;
  animation: welcome-steam 2.4s ease-out infinite;
}

/* 第二缕用负延迟：立即从周期中段开始，不用等一整轮才错开 */
.welcome-illust-svg .steam-2 {
  animation-duration: 2.9s;
  animation-delay: -1.3s;
}

@keyframes welcome-twinkle {
  0%,
  100% {
    opacity: 0.1;
    transform: scale(0.6) rotate(0deg);
  }
  50% {
    opacity: 0.9;
    transform: scale(1.5) rotate(25deg);
  }
}

.welcome-illust-svg .sparkle {
  transform-origin: 50% 50%;
  animation: welcome-twinkle 2.6s ease-in-out infinite;
}

/* 三颗星的周期与相位都错开，避免整齐划一显得机械 */
.welcome-illust-svg .sparkle-1 {
  animation-duration: 2.2s;
  animation-delay: 0s;
}

.welcome-illust-svg .sparkle-2 {
  animation-duration: 2.9s;
  animation-delay: 0.7s;
}

.welcome-illust-svg .sparkle-3 {
  animation-duration: 2.5s;
  animation-delay: 1.4s;
}

@keyframes welcome-caret {
  0%,
  49% {
    opacity: 0.9;
  }
  50%,
  100% {
    opacity: 0;
  }
}

.welcome-illust-svg .caret {
  animation: welcome-caret 0.9s linear infinite;
}

/* ===== 按钮光泽 =====
   每 2.6s 斜扫一次、扫完停顿，比连续扫更耐看。
   按钮亮暗主题同为 #3370ff 白字（.btn.primary 无全局暗色覆盖），故一套白色高光通用。 */
.welcome-enter {
  position: relative;
  overflow: hidden;
}

.welcome-enter::after {
  content: '';
  position: absolute;
  top: 0;
  bottom: 0;
  left: 0;
  width: 46%;
  pointer-events: none;
  background: linear-gradient(100deg, transparent, rgba(255, 255, 255, 0.65), transparent);
  transform: translateX(-120%) skewX(-18deg);
  animation: welcome-shine 2.2s ease-in-out 1.1s infinite;
}

@keyframes welcome-shine {
  0% {
    transform: translateX(-120%) skewX(-18deg);
  }
  45% {
    transform: translateX(320%) skewX(-18deg);
  }
  100% {
    transform: translateX(320%) skewX(-18deg);
  }
}

@media (prefers-reduced-motion: reduce) {
  /* 入场与所有循环动效整体关闭（含光标闪烁——闪烁对部分用户是负担）；
     遮罩保留极短淡入，避免"啪"一下出现 */
  .welcome-dialog,
  .welcome-illust,
  .welcome-title,
  .welcome-desc,
  .welcome-enter,
  .welcome-illust-svg .steam,
  .welcome-illust-svg .sparkle,
  .welcome-illust-svg .caret,
  .welcome-enter::after {
    animation: none;
  }

  .welcome-enter-active {
    transition: opacity 0.2s ease;
  }
}
</style>

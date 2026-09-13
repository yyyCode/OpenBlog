<template>
  <div class="about-page">
    <div class="about-card">
      <!-- 左栏：介绍 -->
      <div class="about-intro">
        <h1 class="about-greeting">
          你好<span class="about-wave" aria-hidden="true">👋</span>
          <span class="about-greeting-line">
            我是 <span class="about-greeting-name">{{ name }}</span>
          </span>
        </h1>
        <p v-if="jobIntention" class="about-job">{{ jobIntention }}</p>
        <p class="about-desc">
          这里记录我在开发与学习中的经历、踩坑和思考，欢迎通过下面的方式联系我交流。
        </p>

        <div class="about-actions">
          <a
            class="about-icon-btn"
            :href="githubUrl"
            target="_blank"
            rel="noreferrer"
            title="GitHub 主页"
            aria-label="GitHub 主页"
          >
            <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
              <path d="M12 2C6.48 2 2 6.58 2 12.25c0 4.53 2.87 8.37 6.84 9.73.5.09.68-.22.68-.49l-.01-1.72c-2.78.62-3.37-1.37-3.37-1.37-.45-1.18-1.11-1.5-1.11-1.5-.91-.64.07-.62.07-.62 1 .07 1.53 1.05 1.53 1.05.89 1.57 2.34 1.12 2.91.85.09-.66.35-1.12.63-1.38-2.22-.26-4.56-1.14-4.56-5.07 0-1.12.39-2.03 1.03-2.75-.1-.26-.45-1.3.1-2.7 0 0 .84-.28 2.75 1.05a9.3 9.3 0 0 1 5 0c1.91-1.33 2.75-1.05 2.75-1.05.55 1.4.2 2.44.1 2.7.64.72 1.03 1.63 1.03 2.75 0 3.94-2.34 4.8-4.57 5.06.36.32.68.94.68 1.9l-.01 2.82c0 .27.18.59.69.49A10.02 10.02 0 0 0 22 12.25C22 6.58 17.52 2 12 2z" />
            </svg>
          </a>

          <div ref="contactRef" class="about-contact">
            <button
              type="button"
              class="about-icon-btn"
              :class="{ 'is-open': showEmail }"
              title="邮箱联系方式"
              aria-label="邮箱联系方式"
              :aria-expanded="showEmail"
              @click="toggleEmail"
            >
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                <rect x="3" y="5" width="18" height="14" rx="2" />
                <path d="m3 7.5 9 6 9-6" />
              </svg>
            </button>

            <div v-if="showEmail" class="about-popover" role="dialog" aria-label="邮箱联系方式">
              <a class="about-popover-mail" :href="`mailto:${contactEmail}`">{{ contactEmail }}</a>
              <button
                type="button"
                class="about-copy-btn"
                :class="{ 'is-copied': copied }"
                @click="copyEmail"
              >
                {{ copied ? '已复制' : '复制' }}
              </button>
            </div>
          </div>
        </div>
      </div>

      <!-- 右栏：头像（正方形展示框） -->
      <div class="about-avatar-col">
        <div class="about-avatar-frame">
          <img class="about-avatar-img" :src="avatarUrl" :alt="`${name} 的头像`" />
        </div>
      </div>

      <div class="about-footer">
        <router-link class="about-footer-link" to="/all">全部文章</router-link>
        <span class="about-footer-dot" aria-hidden="true"></span>
        <router-link class="about-footer-link" to="/changelog">更新日志</router-link>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, inject, onBeforeUnmount, onMounted, ref } from 'vue'
import { fetchOwnerProfile } from '../api/profile'

const siteConfig = inject('siteConfig')

const owner = ref(null)
const contactRef = ref(null)
const showEmail = ref(false)
const copied = ref(false)

const avatarUrl = computed(() => {
  const a = owner.value?.avatarUrl
  if (a) return a
  return (
    (siteConfig.value && siteConfig.value.default_avatar_url) ||
    'https://via.placeholder.com/300x300.png?text=OpenBlog'
  )
})
// 问候语里的名字：接口拿不到时回退到站点作者，不用 "—"（在句子里读不通）
const name = computed(() => (owner.value && owner.value.username) || 'yyyCode')

const jobIntention = computed(() => {
  const v = siteConfig.value && siteConfig.value.job_intention
  return v ? String(v).trim() : '后端 / 全栈开发工程师'
})

const contactEmail = computed(() => {
  const v = siteConfig.value && siteConfig.value.contact_email
  return (v && String(v).trim()) || '2678785492@qq.com'
})

const githubUrl = computed(() =>
  (siteConfig.value && siteConfig.value.github_url) || 'https://github.com/yyyCode'
)

function toggleEmail() {
  showEmail.value = !showEmail.value
  if (!showEmail.value) copied.value = false
}

async function copyEmail() {
  const text = contactEmail.value
  try {
    if (navigator.clipboard && window.isSecureContext) {
      await navigator.clipboard.writeText(text)
    } else {
      // 非安全上下文（如 http 内网访问）没有 clipboard API，降级用临时 textarea 选中复制
      const ta = document.createElement('textarea')
      ta.value = text
      ta.style.position = 'fixed'
      ta.style.opacity = '0'
      document.body.appendChild(ta)
      ta.select()
      document.execCommand('copy')
      document.body.removeChild(ta)
    }
  } catch {
    // 复制被拒（权限/浏览器策略）：弹层里的地址本身可手动选中，不打断用户
    return
  }
  copied.value = true
  setTimeout(() => { copied.value = false }, 1800)
}

function onDocClick(e) {
  if (!showEmail.value) return
  if (contactRef.value && !contactRef.value.contains(e.target)) {
    showEmail.value = false
    copied.value = false
  }
}

function onKeydown(e) {
  if (e.key === 'Escape') {
    showEmail.value = false
    copied.value = false
  }
}

onMounted(async () => {
  document.addEventListener('click', onDocClick)
  document.addEventListener('keydown', onKeydown)
  try {
    owner.value = await fetchOwnerProfile()
  } catch {
    owner.value = null
  }
})

onBeforeUnmount(() => {
  document.removeEventListener('click', onDocClick)
  document.removeEventListener('keydown', onKeydown)
})
</script>

<style scoped>
/* 整页铺开、无卡片容器：内容直接坐在页面底色上（无边框、无卡片阴影） */
.about-page {
  width: 100%;
  max-width: var(--container);
  margin: 0 auto;
  padding: 72px var(--page-pad) 110px;
}

.about-card {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 320px;
  grid-template-areas:
    'intro avatar'
    'footer footer';
  gap: 72px 96px;
  align-items: center;
}

/* ---------- 左栏 ---------- */
.about-intro {
  grid-area: intro;
  min-width: 0;
}

.about-greeting {
  margin: 0;
  font-size: 34px;
  font-weight: 900;
  line-height: 1.4;
  letter-spacing: -0.02em;
}

/* 换行成两行：第一行「你好 👋」，第二行「我是 xxx」放大做主视觉 */
.about-greeting-line {
  display: block;
  margin-top: 18px;
  font-size: 68px;
  line-height: 1.2;
}

/* 挥手符号略微收小，避免和标题正文抢大小 */
.about-wave {
  display: inline-block;
  margin-left: 18px;
  font-size: 0.88em;
  line-height: 1;
}

/* 「我是」与名字之间拉开距离（源码里的空格在 68px 下太窄） */
.about-greeting-name {
  margin-left: 16px;
  color: var(--accent);
}

/* 求职意向：朴素一行黑体，不加底色/描边 */
.about-job {
  margin: 44px 0 0;
  color: var(--text);
  font-size: 15px;
  font-weight: 600;
}

.about-desc {
  margin: 22px 0 0;
  max-width: 54ch;
  color: var(--muted);
  font-size: 15px;
  line-height: 2;
}

.about-actions {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-top: 80px;
}

/* 图标按钮：靠浅色块浮起，不使用描边 */
.about-icon-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 48px;
  height: 48px;
  padding: 0;
  border: none;
  border-radius: 15px;
  background: var(--surface);
  color: var(--text);
  cursor: pointer;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);
  transition: color 0.18s ease, background 0.18s ease, transform 0.18s ease,
    box-shadow 0.18s ease;
}

.about-icon-btn:hover,
.about-icon-btn.is-open {
  color: var(--accent);
  background: rgba(51, 112, 255, 0.12);
  transform: translateY(-2px);
  box-shadow: 0 6px 16px rgba(51, 112, 255, 0.18);
}

.about-icon-btn.is-open {
  transform: none;
}

/* 邮箱弹层：图标点击后才暴露联系方式 */
.about-contact {
  position: relative;
}

.about-popover {
  position: absolute;
  top: calc(100% + 14px);
  left: 0;
  z-index: 20;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 12px 10px 18px;
  border: none;
  border-radius: 14px;
  background: var(--card);
  box-shadow: 0 16px 40px rgba(15, 23, 42, 0.18);
  white-space: nowrap;
}

.about-popover-mail {
  color: var(--text);
  font-size: 13.5px;
  font-weight: 600;
  text-decoration: none;
}

.about-popover-mail:hover {
  color: var(--accent);
}

.about-copy-btn {
  padding: 4px 11px;
  border: none;
  border-radius: 9px;
  background: var(--surface-soft);
  color: var(--muted);
  font-size: 12px;
  cursor: pointer;
}

.about-copy-btn:hover,
.about-copy-btn.is-copied {
  color: var(--accent);
  background: rgba(51, 112, 255, 0.12);
}

/* ---------- 右栏：正方形头像 ---------- */
.about-avatar-col {
  grid-area: avatar;
}

.about-avatar-frame {
  width: 100%;
  aspect-ratio: 1 / 1;
  overflow: hidden;
  border: none;
  border-radius: 24px;
  background: var(--surface);
  box-shadow: 0 18px 44px rgba(15, 23, 42, 0.12);
}

.about-avatar-img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

/* ---------- 底部导航（无分隔线，仅留白拉开） ---------- */
.about-footer {
  grid-area: footer;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 18px;
  margin-top: 84px;
}

.about-footer-link {
  color: var(--muted);
  font-size: 13.5px;
  text-decoration: none;
  transition: color 0.18s ease;
}

.about-footer-link:hover {
  color: var(--accent);
}

.about-footer-dot {
  width: 3px;
  height: 3px;
  border-radius: 50%;
  background: var(--muted);
  opacity: 0.5;
}

/* ---------- 窄屏：头像置顶居中，单列 ---------- */
@media (max-width: 860px) {
  .about-page {
    padding: 44px var(--page-pad) 72px;
  }

  .about-card {
    grid-template-columns: minmax(0, 1fr);
    grid-template-areas:
      'avatar'
      'intro'
      'footer';
    gap: 44px;
    text-align: center;
  }

  .about-greeting-line {
    font-size: 46px;
  }

  .about-avatar-col {
    max-width: 200px;
    margin: 0 auto;
  }

  .about-desc {
    margin-left: auto;
    margin-right: auto;
  }

  .about-actions {
    justify-content: center;
  }

  .about-footer {
    margin-top: 52px;
  }

  .about-popover {
    left: 50%;
    transform: translateX(-50%);
  }
}

/* 手机竖屏：68px 的名字会撑破屏宽，再收一档 */
@media (max-width: 480px) {
  .about-greeting-line {
    font-size: 36px;
  }
}
</style>

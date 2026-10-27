<template>
  <div class="about-page">
    <section
      ref="profileSectionRef"
      class="about-screen about-screen-profile"
      aria-label="个人信息"
    >
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

            <a
              class="about-icon-btn"
              :href="xUrl"
              target="_blank"
              rel="noreferrer"
              title="X 主页"
              aria-label="X 主页"
            >
              <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
                <path d="M18.244 2.25h3.308l-7.227 8.26 8.502 11.24H16.17l-5.214-6.817L4.99 21.75H1.68l7.73-8.835L1.254 2.25H8.08l4.713 6.231zm-1.161 17.52h1.833L7.084 4.126H5.117z" />
              </svg>
            </a>

            <a
              class="about-icon-btn"
              :href="xiaohongshuUrl"
              target="_blank"
              rel="noreferrer"
              title="小红书主页"
              aria-label="小红书主页"
            >
              <!-- 小红书：官方字标（Simple Icons，CC0），单色跟随主题 -->
              <svg width="22" height="22" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
                <path d="M22.405 9.879c.002.016.01.02.07.019h.725a.797.797 0 0 0 .78-.972.794.794 0 0 0-.884-.618.795.795 0 0 0-.692.794c0 .101-.002.666.001.777zm-11.509 4.808c-.203.001-1.353.004-1.685.003a2.528 2.528 0 0 1-.766-.126.025.025 0 0 0-.03.014L7.7 16.127a.025.025 0 0 0 .01.032c.111.06.336.124.495.124.66.01 1.32.002 1.981 0 .01 0 .02-.006.023-.015l.712-1.545a.025.025 0 0 0-.024-.036zM.477 9.91c-.071 0-.076.002-.076.01a.834.834 0 0 0-.01.08c-.027.397-.038.495-.234 3.06-.012.24-.034.389-.135.607-.026.057-.033.042.003.112.046.092.681 1.523.787 1.74.008.015.011.02.017.02.008 0 .033-.026.047-.044.147-.187.268-.391.371-.606.306-.635.44-1.325.486-1.706.014-.11.021-.22.03-.33l.204-2.616.022-.293c.003-.029 0-.033-.03-.034zm7.203 3.757a1.427 1.427 0 0 1-.135-.607c-.004-.084-.031-.39-.235-3.06a.443.443 0 0 0-.01-.082c-.004-.011-.052-.008-.076-.008h-1.48c-.03.001-.034.005-.03.034l.021.293c.076.982.153 1.964.233 2.946.05.4.186 1.085.487 1.706.103.215.223.419.37.606.015.018.037.051.048.049.02-.003.742-1.642.804-1.765.036-.07.03-.055.003-.112zm3.861-.913h-.872a.126.126 0 0 1-.116-.178l1.178-2.625a.025.025 0 0 0-.023-.035l-1.318-.003a.148.148 0 0 1-.135-.21l.876-1.954a.025.025 0 0 0-.023-.035h-1.56c-.01 0-.02.006-.024.015l-.926 2.068c-.085.169-.314.634-.399.938a.534.534 0 0 0-.02.191.46.46 0 0 0 .23.378.981.981 0 0 0 .46.119h.59c.041 0-.688 1.482-.834 1.972a.53.53 0 0 0-.023.172.465.465 0 0 0 .23.398c.15.092.342.12.475.12l1.66-.001c.01 0 .02-.006.023-.015l.575-1.28a.025.025 0 0 0-.024-.035zm-6.93-4.937H3.1a.032.032 0 0 0-.034.033c0 1.048-.01 2.795-.01 6.829 0 .288-.269.262-.28.262h-.74c-.04.001-.044.004-.04.047.001.037.465 1.064.555 1.263.01.02.03.033.051.033.157.003.767.009.938-.014.153-.02.3-.06.438-.132.3-.156.49-.419.595-.765.052-.172.075-.353.075-.533.002-2.33 0-4.66-.007-6.991a.032.032 0 0 0-.032-.032zm11.784 6.896c0-.014-.01-.021-.024-.022h-1.465c-.048-.001-.049-.002-.05-.049v-4.66c0-.072-.005-.07.07-.07h.863c.08 0 .075.004.075-.074V8.393c0-.082.006-.076-.08-.076h-3.5c-.064 0-.075-.006-.075.073v1.445c0 .083-.006.077.08.077h.854c.075 0 .07-.004.07.07v4.624c0 .095.008.084-.085.084-.37 0-1.11-.002-1.304 0-.048.001-.06.03-.06.03l-.697 1.519s-.014.025-.008.036c.006.01.013.008.058.008 1.748.003 3.495.002 5.243.002.03-.001.034-.006.035-.033v-1.539zm4.177-3.43c0 .013-.007.023-.02.024-.346.006-.692.004-1.037.004-.014-.002-.022-.01-.022-.024-.005-.434-.007-.869-.01-1.303 0-.072-.006-.071.07-.07l.733-.003c.041 0 .081.002.12.015.093.025.16.107.165.204.006.431.002 1.153.001 1.153zm2.67.244a1.953 1.953 0 0 0-.883-.222h-.18c-.04-.001-.04-.003-.042-.04V10.21c0-.132-.007-.263-.025-.394a1.823 1.823 0 0 0-.153-.53 1.533 1.533 0 0 0-.677-.71 2.167 2.167 0 0 0-1-.258c-.153-.003-.567 0-.72 0-.07 0-.068.004-.068-.065V7.76c0-.031-.01-.041-.046-.039H17.93s-.016 0-.023.007c-.006.006-.008.012-.008.023v.546c-.008.036-.057.015-.082.022h-.95c-.022.002-.028.008-.03.032v1.481c0 .09-.004.082.082.082h.913c.082 0 .072.128.072.128V11.19s.003.117-.06.117h-1.482c-.068 0-.06.082-.06.082v1.445s-.01.068.064.068h1.457c.082 0 .076-.006.076.079v3.225c0 .088-.007.081.082.081h1.43c.09 0 .082.007.082-.08v-3.27c0-.029.006-.035.033-.035l2.323-.003c.098 0 .191.02.28.061a.46.46 0 0 1 .274.407c.008.395.003.79.003 1.185 0 .259-.107.367-.33.367h-1.218c-.023.002-.029.008-.028.033.184.437.374.871.57 1.303a.045.045 0 0 0 .04.026c.17.005.34.002.51.003.15-.002.517.004.666-.01a2.03 2.03 0 0 0 .408-.075c.59-.18.975-.698.976-1.313v-1.981c0-.128-.01-.254-.034-.38 0 .078-.029-.641-.724-.998z" />
              </svg>
            </a>
          </div>
        </div>

        <!-- 右栏：头像（正方形展示框） -->
        <div class="about-avatar-col">
          <div class="about-avatar-frame">
            <img class="about-avatar-img" :src="avatarUrl" :alt="`${name} 的头像`" />
          </div>
        </div>
      </div>

      <!-- 下滑提示：点它平滑滚到第二屏 -->
      <a class="about-scroll-hint" href="#about-opensource" @click="onScrollHintClick">
        下滑查看开源贡献
        <svg width="15" height="15" viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
          <path d="M3.5 6l4.5 4.5L12.5 6" />
        </svg>
      </a>
    </section>

    <!-- 第 2 屏：开源贡献。一行一个项目，整行点进 PR 列表（数据当前为 mock，见 script 顶部） -->
    <section
      id="about-opensource"
      ref="ossSectionRef"
      class="about-screen about-screen-oss"
      aria-labelledby="oss-title"
    >
      <div class="about-screen-inner">
        <h2 id="oss-title" class="about-oss-title">开源贡献</h2>

        <ul class="oss-list">
          <!-- 用下标做 key：列表只读、不做增删动画，且后台允许同名项目（用 name 会撞 key） -->
          <li v-for="(item, i) in openSourceContributions" :key="i">
            <!-- 没填链接时渲染成 div：空的 <a href=""> 会当成跳到当前页刷新 -->
            <component
              :is="item.url ? 'a' : 'div'"
              class="oss-item"
              :class="{ 'is-plain': !item.url }"
              :href="item.url || undefined"
              :target="item.url ? '_blank' : undefined"
              :rel="item.url ? 'noreferrer' : undefined"
            >
              <span class="oss-item-name">{{ item.name }}</span>
              <span
                class="oss-item-role"
                :class="{ 'is-maintainer': item.role === 'Maintainer' }"
              >
                {{ item.role }}
              </span>
              <span v-if="item.url" class="oss-item-jump">
                PR 列表
                <svg width="13" height="13" viewBox="0 0 16 16" fill="currentColor" aria-hidden="true">
                  <path d="M3.75 2h3.5a.75.75 0 0 1 0 1.5h-3.5a.25.25 0 0 0-.25.25v8.5c0 .138.112.25.25.25h8.5a.25.25 0 0 0 .25-.25v-3.5a.75.75 0 0 1 1.5 0v3.5A1.75 1.75 0 0 1 12.25 14h-8.5A1.75 1.75 0 0 1 2 12.25v-8.5C2 2.784 2.784 2 3.75 2Zm6.5 0h4a.75.75 0 0 1 .75.75v4a.75.75 0 0 1-1.5 0V4.56l-4.22 4.22a.75.75 0 0 1-1.06-1.06l4.22-4.22h-2.19a.75.75 0 0 1 0-1.5Z" />
                </svg>
              </span>
            </component>
          </li>
        </ul>

        <!-- 后台把条目清空后的兜底：不留一片空白，也不挡住底部链接 -->
        <p v-if="!openSourceContributions.length" class="oss-empty">
          暂无开源贡献记录
        </p>

        <div class="about-footer">
          <router-link class="about-footer-link" to="/all">全部文章</router-link>
          <span class="about-footer-dot" aria-hidden="true"></span>
          <router-link class="about-footer-link" to="/changelog">更新日志</router-link>
        </div>
      </div>
    </section>
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
// 问候语里的展示名固定为「墨子Mozi」：账号 username 仍是 yyyCode，
// 不能拿接口返回的 username 覆盖，否则页面上又会显示成 yyyCode
const name = '墨子Mozi'

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

// X（推特）/ 小红书主页：站点配置里没有对应字段，直接用固定链接
const xUrl = 'https://x.com/Marx9527'
const xiaohongshuUrl = 'https://www.xiaohongshu.com/user/profile/67c87574000000000a03c42c'

// ---------------------------------------------------------------------------
// 开源贡献
// 数据来自站点配置 site_config 的 about_opensource（JSON 数组字符串），
// 在后台「站点设置 › 开源贡献」维护；url 指向该仓库「我提交的 PR 列表」，点整行即跳转。
// ---------------------------------------------------------------------------

// 配置项还没写入（未跑迁移 / 后台未保存过）时的兜底，同时也是后台首次打开时的初始值
const DEFAULT_CONTRIBUTIONS = [
  { name: 'Apache Dubbo', role: 'Maintainer', url: 'https://github.com/apache/dubbo/pulls?q=author%3AyyyCode' },
  { name: 'Higress', role: 'Contributor', url: 'https://github.com/alibaba/higress/pulls?q=author%3AyyyCode' },
  { name: 'Nacos', role: 'Contributor', url: 'https://github.com/alibaba/nacos/pulls?q=author%3AyyyCode' },
  { name: 'MyBatis-Plus', role: 'Contributor', url: 'https://github.com/baomidou/mybatis-plus/pulls?q=author%3AyyyCode' },
  { name: 'Redisson', role: 'Contributor', url: 'https://github.com/redisson/redisson/pulls?q=author%3AyyyCode' },
  { name: 'Sa-Token', role: 'Contributor', url: 'https://github.com/dromara/Sa-Token/pulls?q=author%3AyyyCode' }
]

/** 逐条挑出合法字段，脏数据（缺 name / 后台手改坏）直接跳过，不整块崩 */
function normalizeContributions(raw) {
  if (!Array.isArray(raw)) return []
  return raw
    .filter((it) => it && typeof it.name === 'string' && it.name.trim())
    .map((it) => ({
      name: it.name.trim(),
      role: (typeof it.role === 'string' && it.role.trim()) || 'Contributor',
      url: typeof it.url === 'string' ? it.url.trim() : ''
    }))
}

const openSourceContributions = computed(() => {
  const raw = siteConfig.value && siteConfig.value.about_opensource
  // 配置项缺失 → 用兜底；配置项存在（含后台清空后存下的 "[]"）→ 以后台为准，允许清空
  if (raw == null || String(raw).trim() === '') return DEFAULT_CONTRIBUTIONS
  try {
    return normalizeContributions(JSON.parse(raw))
  } catch {
    // JSON 手改坏了：退回兜底，页面不至于开天窗
    return DEFAULT_CONTRIBUTIONS
  }
})

// ---------------------------------------------------------------------------
// 两屏滚动吸附：与首页同一套机制 —— 实测吸顶导航高度写进 --header-h，
// 再给 <html> 加类开启 scroll-snap（吸附类型见 blog.css 的 html.about-scroll-snap）
// ---------------------------------------------------------------------------
const SCROLL_SNAP_CLASS = 'about-scroll-snap'
const profileSectionRef = ref(null)
const ossSectionRef = ref(null)

function measureHeaderHeight() {
  const bar = document.querySelector('.site-top-bar')
  return (bar && bar.offsetHeight) || 60
}

// 吸附力度：两屏都塞得进一屏时才用 mandatory（一次下滑即整屏切换）；
// 任一屏内容更高就退回 proximity —— 否则屏底内容会被吸附点顶回来，滚不到
function syncSnapStrictness() {
  const limit = window.innerHeight - measureHeaderHeight() + 1
  const screens = [profileSectionRef.value, ossSectionRef.value].filter(Boolean)
  const fits = screens.length > 0 && screens.every((el) => el.offsetHeight <= limit)
  document.documentElement.style.setProperty(
    '--about-snap-strictness',
    fits ? 'mandatory' : 'proximity'
  )
}

function onViewportResize() {
  document.documentElement.style.setProperty('--header-h', `${measureHeaderHeight()}px`)
  syncSnapStrictness()
}

function enableAboutSnap() {
  document.documentElement.style.setProperty('--header-h', `${measureHeaderHeight()}px`)
  document.documentElement.classList.add(SCROLL_SNAP_CLASS)
  syncSnapStrictness()
  window.addEventListener('resize', onViewportResize)
}

function disableAboutSnap() {
  window.removeEventListener('resize', onViewportResize)
  document.documentElement.classList.remove(SCROLL_SNAP_CLASS)
  document.documentElement.style.removeProperty('--header-h')
  document.documentElement.style.removeProperty('--about-snap-strictness')
}

// 下滑提示：平滑滚到第二屏。href 保留为无 JS 时的兜底
function onScrollHintClick(e) {
  const el = ossSectionRef.value
  if (!el) return
  e.preventDefault()
  const reduced =
    window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches
  el.scrollIntoView({ behavior: reduced ? 'auto' : 'smooth', block: 'start' })
}

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
  enableAboutSnap()
  try {
    owner.value = await fetchOwnerProfile()
  } catch {
    owner.value = null
  }
})

onBeforeUnmount(() => {
  document.removeEventListener('click', onDocClick)
  document.removeEventListener('keydown', onKeydown)
  disableAboutSnap()
})
</script>

<style scoped>
/* 整页铺开、无卡片容器：内容直接坐在页面底色上（无边框、无卡片阴影） */
.about-page {
  width: 100%;
}

/* ---------- 每屏一屏高 + 滚动吸附（吸附类型见 blog.css 的 html.about-scroll-snap） ---------- */
.about-screen {
  position: relative;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  /* 吸顶导航之外正好铺满一屏；内容更高时被 min-height 撑开，不裁切 */
  min-height: calc(100dvh - var(--header-h, 60px));
  /* 底部留白给下滑提示 / 底部链接让位 */
  padding: 56px var(--page-pad) 76px;
  scroll-snap-align: start;
  scroll-snap-stop: always;
}

/* 第 2 屏的内容容器；第 1 屏的 .about-card 自带同款宽度约束 */
.about-screen-inner {
  width: 100%;
  max-width: var(--container);
}

.about-card {
  width: 100%;
  max-width: var(--container);
  display: grid;
  grid-template-columns: minmax(0, 1fr) 320px;
  grid-template-areas: 'intro avatar';
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

/* ---------- 第 2 屏：开源贡献，一行一个项目 ---------- */
.about-oss-title {
  margin: 0 0 10px;
  font-size: 26px;
  font-weight: 900;
  letter-spacing: -0.02em;
}

.oss-list {
  margin: 0;
  padding: 0;
  list-style: none;
}

/* 行间细分割线：加在下一行顶部，首行不留 */
.oss-list > li + li .oss-item {
  border-top: 1px solid var(--border);
}

.oss-item {
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 15px 4px;
  color: inherit;
  text-decoration: none;
  transition: padding-left 0.18s ease;
}

/* hover 时整行向右让开一点，作为「可点」的反馈 */
.oss-item:hover {
  padding-left: 14px;
}

/* 没填链接的行（后台存了项目名但没存 URL）：不是链接，就不给「可点」的反馈 */
.oss-item.is-plain:hover {
  padding-left: 4px;
}

.oss-item.is-plain:hover .oss-item-name {
  color: inherit;
}

.oss-item-name {
  flex: none;
  min-width: 180px;
  font-size: 15px;
  font-weight: 700;
  letter-spacing: -0.01em;
  transition: color 0.18s ease;
}

.oss-item:hover .oss-item-name {
  color: var(--accent);
}

.oss-item-role {
  flex: none;
  color: var(--muted);
  font-size: 13px;
}

/* Maintainer 是更高的角色，给一点强调 */
.oss-item-role.is-maintainer {
  color: var(--accent);
  font-weight: 600;
}

/* 后台把条目清空后的兜底文案 */
.oss-empty {
  margin: 0;
  padding: 15px 4px;
  color: var(--muted);
  font-size: 14px;
}

.oss-item-jump {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  margin-left: auto;
  color: var(--muted);
  font-size: 13px;
  transition: color 0.18s ease, gap 0.18s ease;
}

.oss-item:hover .oss-item-jump {
  gap: 8px;
  color: var(--accent);
}

/* 窄屏：项目名让出 min-width，长名字省略号兜底，避免把角色和跳转挤掉 */
@media (max-width: 640px) {
  .oss-item {
    gap: 12px;
  }

  .oss-item-name {
    flex: 0 1 auto;
    min-width: 0;
    overflow: hidden;
    white-space: nowrap;
    text-overflow: ellipsis;
  }
}

@media (prefers-reduced-motion: reduce) {
  .oss-item,
  .oss-item-name,
  .oss-item-jump {
    transition: none;
  }

  .oss-item:hover {
    padding-left: 4px;
  }
}
/* ---------- 第 1 屏底部的下滑提示 ---------- */
.about-scroll-hint {
  position: absolute;
  left: 50%;
  bottom: 24px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  /* 绝对定位 + 自身位移做水平居中，配合下面的箭头浮动 */
  transform: translateX(-50%);
  color: var(--muted);
  font-size: 12px;
  letter-spacing: 0.14em;
  text-decoration: none;
  transition: color 0.18s ease;
}

.about-scroll-hint:hover {
  color: var(--accent);
}

/* 只让箭头上下浮动，文字不动，避免整块抖动 */
.about-scroll-hint svg {
  animation: about-hint-bob 2.4s ease-in-out infinite;
}

@keyframes about-hint-bob {
  0%,
  100% {
    transform: translateY(0);
    opacity: 0.55;
  }
  50% {
    transform: translateY(6px);
    opacity: 1;
  }
}

/* ---------- 第 2 屏底部：页面出口，贴屏底与下滑提示同一水平线 ---------- */
.about-footer {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 26px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 18px;
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
  .about-screen {
    padding: 44px var(--page-pad) 72px;
  }

  .about-card {
    grid-template-columns: minmax(0, 1fr);
    grid-template-areas:
      'avatar'
      'intro';
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

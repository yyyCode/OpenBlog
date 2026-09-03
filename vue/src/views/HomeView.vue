<template>
  <div class="home-screens">
    <!-- 第 1 屏：口号区 -->
    <section class="home-screen home-screen-slogan" :style="sloganBgStyle">
      <div class="home-slogan-inner">
        <h1 class="home-slogan-title" v-html="sloganTitleHtml"></h1>
        <p v-if="heroSubtitle" class="home-slogan-sub" v-html="heroSubtitleHtml"></p>
      </div>
      <div class="home-slogan-scroll-hint">▼ 下滑</div>
    </section>

    <!-- 第 2 屏：文章区 -->
    <section class="home-screen home-screen-articles">
      <div class="blog-container">
        <div v-if="loading" class="card">
          <div class="card-body" style="padding: 22px; color: var(--muted)">加载中...</div>
        </div>

        <div v-else>
          <div class="card">
            <div v-if="!featuredArticle?.id" style="padding: 22px; color: var(--muted)">暂无文章</div>
            <div v-else class="home-featured" @click="go(featuredArticle.id)">
              <div class="home-featured-grid">
                <div class="home-featured-cover">
                  <img
                    v-if="featuredArticle.coverMediaKey"
                    :src="coverUrl(featuredArticle.coverMediaKey)"
                    alt="cover"
                  />
                </div>
                <div class="home-featured-body">
                  <div class="home-featured-badge">精选文章</div>
                  <div class="home-featured-title">{{ featuredArticle.title }}</div>
                  <div class="home-featured-excerpt">{{ featuredArticle.summary || '' }}</div>
                  <div class="home-featured-meta">
                    <span>{{ formatDate(featuredArticle.publishedAt) }}</span>
                    <span>·</span>
                    <span>{{ featuredArticle.authorNickname || '作者' }}</span>
                  </div>
                  <div class="home-featured-link">阅读更多 →</div>
                </div>
              </div>
            </div>
          </div>

          <section v-if="latestArticles.length" class="home-section home-section-latest">
            <div class="home-section-head">
              <h2 class="home-section-title">最新文章</h2>
              <router-link class="home-section-more" to="/all">查看全部 →</router-link>
            </div>

            <div class="home-latest-grid">
              <div v-for="a in latestArticles" :key="a.id" class="home-post-card" @click="go(a.id)">
                <div class="home-post-cover">
                  <img v-if="a.coverMediaKey" :src="coverUrl(a.coverMediaKey)" alt="cover" />
                </div>
                <div class="home-post-body">
                  <div class="home-post-meta">
                    <span>{{ formatDate(a.publishedAt) }}</span>
                    <span>·</span>
                    <span>{{ a.authorNickname || '作者' }}</span>
                  </div>
                  <div class="home-post-title">{{ a.title }}</div>
                  <div class="home-post-excerpt">{{ a.summary || '' }}</div>
                </div>
              </div>
            </div>
          </section>
        </div>
      </div>
    </section>

    <!-- 第 3 屏：项目推荐 -->
    <section class="home-screen home-screen-projects">
      <div class="home-projects">
        <div class="home-projects-head">
          <span class="home-projects-kicker">HANDMADE · PROJECTS</span>
          <h2 class="home-projects-title">项目推荐</h2>
          <p class="home-projects-sub">把喜欢的东西做出来 —— 亲手写过的项目，欢迎进来看看</p>
        </div>

        <div v-if="latestProjects.length" class="home-projects-grid">
          <div
            v-for="(item, i) in latestProjects"
            :key="item.id"
            class="home-project-card"
            :class="{ 'home-project-card--featured': i === 0 }"
            @click="goProject(item.id)"
          >
            <div class="home-project-cover">
              <img v-if="item.coverMediaKey" :src="coverUrl(item.coverMediaKey)" alt="cover" />
              <span class="home-project-index">{{ String(i + 1).padStart(2, '0') }}</span>
              <span v-if="i === 0" class="home-project-pick">精选</span>
            </div>
            <div class="home-project-body">
              <div class="home-project-title">{{ item.title }}</div>
              <div class="home-project-summary">{{ item.summary || '暂无简介' }}</div>
              <div v-if="item.techStack" class="home-project-tags">
                <span v-for="tag in splitTags(item.techStack)" :key="tag" class="home-project-tag">{{ tag }}</span>
              </div>
              <div class="home-project-links">
                <a
                  v-if="item.projectUrl"
                  :href="item.projectUrl"
                  target="_blank"
                  rel="noopener noreferrer"
                  @click.stop
                >项目链接</a>
                <a
                  v-if="item.githubUrl"
                  :href="item.githubUrl"
                  target="_blank"
                  rel="noopener noreferrer"
                  @click.stop
                >GitHub</a>
              </div>
            </div>
          </div>
        </div>

        <div v-else class="card">
          <div class="card-body" style="padding: 22px; color: var(--muted)">暂无项目推荐，敬请期待</div>
        </div>

        <router-link class="home-projects-more" to="/projects">查看全部项目 →</router-link>
      </div>
    </section>

    <!-- 第 4 屏：小而美公司（编辑式榜单，左宣言 + 右排名，区别于项目推荐居中 bento） -->
    <section class="home-screen home-screen-companies">
      <div class="home-companies">
        <div class="home-companies-copy">
          <span class="home-companies-kicker">CURATED · 小而美</span>
          <h2 class="home-companies-title">
            大厂太卷，<br />不如看看<span class="hl">小而美</span>
          </h2>
          <p class="home-companies-sub">优质小厂同样值得关注 —— 小团队，也能做出好产品</p>
          <button class="home-companies-btn" @click="goCompanies">查看全部小而美公司 →</button>
        </div>

        <ol class="home-companies-list">
          <li
            v-for="(c, i) in previewCompanies"
            :key="c.id"
            class="home-companies-item"
            @click="goCompanyDetail(c)"
          >
            <span class="home-companies-rank">{{ String(i + 1).padStart(2, '0') }}</span>
            <span class="home-companies-avatar" :style="{ backgroundColor: c.color }">
              <img v-if="logoOf(c)" :src="logoOf(c)" :alt="c.name" class="home-companies-avatar-img" />
              <span v-else>{{ initialOf(c) }}</span>
            </span>
            <span class="home-companies-item-body">
              <span class="home-companies-item-name">{{ c.name }}</span>
              <span class="home-companies-item-meta">{{ scaleOf(c) }}<template v-if="scaleOf(c) && c.type"> · </template>{{ c.type }}</span>
            </span>
            <span class="home-companies-arrow">→</span>
          </li>
        </ol>
      </div>
    </section>

    <!-- 第 5 屏：网站时间线 -->
    <section class="home-screen home-screen-timeline">
      <div class="home-timeline">
        <h2 class="home-timeline-title">网站历程</h2>
        <div class="home-timeline-items">
          <div
            v-for="(m, i) in milestones"
            :key="m.date + '-' + i"
            class="home-timeline-item"
          >
            <span class="home-timeline-dot" aria-hidden="true"></span>
            <div class="home-timeline-card">
              <div class="home-timeline-date">{{ m.date }}</div>
              <div class="home-timeline-name">{{ m.name }}</div>
              <div class="home-timeline-desc">{{ m.desc }}</div>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- 第 6 屏：论坛入口（极光渐变 + 毛玻璃，区别于终端/榜单/bento 三种旧入口） -->
    <section class="home-screen home-screen-forum">
      <div class="home-forum-orb home-forum-orb--a" aria-hidden="true"></div>
      <div class="home-forum-orb home-forum-orb--b" aria-hidden="true"></div>

      <div class="home-forum-card">
        <p class="home-forum-kicker">Open Forum · 自由交流</p>
        <h2 class="home-forum-title">有想法，<br /><span class="home-forum-grad">说出来。</span></h2>
        <p class="home-forum-sub">这里欢迎每一种声音 —— 提问、分享、吐槽，都有人回应。</p>

        <div v-if="latestTopics.length" class="home-forum-topics">
          <p class="home-forum-topics-cap"><span class="home-forum-live" aria-hidden="true"></span>正在聊</p>
          <button
            v-for="t in latestTopics"
            :key="t.id"
            type="button"
            class="home-forum-topic"
            @click="goTopic(t.id)"
          >
            <span class="home-forum-topic-title">{{ t.title }}</span>
            <span class="home-forum-topic-meta">{{ t.commentCount || 0 }} 条回复</span>
          </button>
        </div>

        <router-link to="/forum" class="home-forum-cta">去论坛聊聊</router-link>
      </div>
    </section>

    <!-- 第 7 屏：Bug 案例（明确入口标题 + 整块大终端日志） -->
    <section class="home-screen home-screen-bugs">
      <div class="home-bugs">
        <div class="home-bugs-head">
          <p class="home-bugs-command"><span class="t-prompt">$</span> <span class="t-cmd">cat bug-cases/README.md</span></p>
          <h2 class="home-bugs-title">Bug 案例<span class="home-bugs-caret" aria-hidden="true">█</span></h2>
          <p class="home-bugs-sub">开发路上踩过的坑 —— 问题现象、根因定位、解法与复盘</p>
        </div>

        <div
          class="home-bugs-terminal"
          :class="{ 'home-bugs-terminal--ready': latestBug }"
          @click="latestBug && go(latestBug.id)"
        >
          <div class="terminal-bar">
            <span class="terminal-dot terminal-dot--r" aria-hidden="true"></span>
            <span class="terminal-dot terminal-dot--y" aria-hidden="true"></span>
            <span class="terminal-dot terminal-dot--g" aria-hidden="true"></span>
            <span class="terminal-title">debug.log</span>
          </div>

          <div v-if="latestBug" class="terminal-body">
            <p class="t-line"><span class="t-prompt">$</span> <span class="t-cmd">tail -f logs/bug-cases.log</span></p>
            <p class="t-line t-comment"># 问题现象 / 根因定位 / 解法与复盘</p>
            <p class="t-line t-error">[ERROR] {{ formatDate(latestBug.publishedAt) }}</p>
            <p class="t-line t-title">{{ latestBug.title }}</p>
            <p class="t-line t-out">{{ latestBug.summary || '暂无摘要' }}</p>
            <p class="t-line t-link">阅读完整复盘 →<span class="t-caret" aria-hidden="true">▍</span></p>
          </div>

          <div v-else class="terminal-body">
            <p class="t-line"><span class="t-prompt">$</span> <span class="t-cmd">tail -f logs/bug-cases.log</span></p>
            <p class="t-line t-comment"># 等待第一条踩坑记录入库…</p>
            <p class="t-line t-out">未找到踩坑记录，暂无 Bug 案例</p>
            <p class="t-line t-out">如果你有线上问题复盘，欢迎投稿</p>
            <p class="t-line t-link">敬请期待 →</p>
          </div>
        </div>

        <router-link class="home-bugs-more" to="/bugs">查看全部 Bug 案例</router-link>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, inject, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'

import { fetchArticles, fetchArticleDetail, fetchArticlesByType, coverUrl } from '../api/article'
import { fetchProjects } from '../api/project'
import { fetchSmallCompanies, formatScale, logoUrl } from '../api/smallCompany'
import { fetchForumTopics } from '../api/forum'

const router = useRouter()
const siteConfig = inject('siteConfig')

// ---- 第 1 屏：口号 ----
const heroImageUrl = computed(() =>
  (siteConfig && siteConfig.value && siteConfig.value.hero_image_url) || ''
)
// 默认纯白背景；hero_image_url 非空则整屏图片背景（白色垫底兜底）
const sloganBgStyle = computed(() => {
  if (!heroImageUrl.value) return {}
  return { backgroundImage: 'url("' + heroImageUrl.value + '")' }
})

const heroTitle = computed(() =>
  (siteConfig && siteConfig.value && siteConfig.value.hero_title) || '热爱技术 持续生长'
)
// 小字定位：分享计算机与 AI 知识；后台 hero_subtitle 有配置时优先展示后台文案
const heroSubtitle = computed(() =>
  (siteConfig && siteConfig.value && siteConfig.value.hero_subtitle) ||
  '一个专注计算机与 AI 的角落 —— 前沿知识、真实踩坑与独立思考，都在这里。'
)

// 把「热爱」「生长」包上蓝色高亮 span（v-html 渲染；文案变了则不高亮，不报错）
const sloganTitleHtml = computed(() => {
  return heroTitle.value
    .replace(/热爱/g, '<span class="hl">热爱</span>')
    .replace(/生长/g, '<span class="hl">生长</span>')
    .replace(/\n/g, '<br />')
})
const heroSubtitleHtml = computed(() => heroSubtitle.value.replace(/\n/g, '<br />'))

// ---- 第 3 屏：网站时间线（静态内置关键节点） ----
const milestones = [
  { date: '2026-03', name: '网站上线', desc: 'OpenBlog 博客正式上线' },
  { date: '2026-05', name: '基础功能完善', desc: '后台管理、SEO、对象存储、文章导入导出' },
  { date: '2026-07', name: '体验优化', desc: '首页 Hero 图、双 Token 无感刷新' },
  { date: '2026-08', name: 'Docker 自动化部署', desc: '后端迁移 Docker Compose，CI 自动部署' },
  { date: '2026-08', name: '首页改版 · 全屏滚动', desc: '口号区 + 精选文章区全屏滚动' }
]

// ---- 第 2 屏：文章 ----
const loading = ref(true)
const featuredArticle = ref({})
const latestArticles = ref([])

// ---- 第 3 屏：项目推荐（只取前 4 个已发布项目，首个做精选大卡） ----
const latestProjects = ref([])

// ---- 第 4 屏：小而美公司（预览前 6 家，跳转 /jobs/companies 看全部） ----
const previewCompanies = ref([])

async function loadCompanies() {
  try {
    const resp = await fetchSmallCompanies(0, 6)
    previewCompanies.value = (resp?.items || []).slice(0, 6)
  } catch {
    previewCompanies.value = []
  }
}

function goCompanies() {
  router.push('/jobs/companies')
}

function goCompanyDetail(c) {
  router.push(`/jobs/companies/${c.id}`)
}

function initialOf(c) {
  const name = (c && c.name) || '?'
  return name.trim().charAt(0).toUpperCase()
}

function scaleOf(c) {
  return formatScale(c)
}

function logoOf(c) {
  return logoUrl(c && c.logoMediaKey)
}

async function loadProjects() {
  try {
    const resp = await fetchProjects(0, 4)
    latestProjects.value = resp?.items || []
  } catch {
    latestProjects.value = []
  }
}

// ---- 第 5 屏：Bug 案例（只取最新一篇） ----
const latestBug = ref(null)

async function loadLatestBug() {
  try {
    const resp = await fetchArticlesByType('BUG_CASE', { page: 0, size: 1 })
    const items = resp?.items || []
    latestBug.value = items[0] || null
  } catch {
    latestBug.value = null
  }
}

// ---- 第 6 屏：论坛入口（实时取最新 3 条讨论做预览，为空/失败时自动隐藏该块） ----
const latestTopics = ref([])

async function loadLatestForumTopics() {
  try {
    const resp = await fetchForumTopics({ page: 0, size: 3 })
    latestTopics.value = (resp?.items || []).slice(0, 3)
  } catch {
    latestTopics.value = []
  }
}

function goTopic(id) {
  router.push(`/forum/topic/${id}`)
}

onMounted(async () => {
  enableHomepageSnap()
  loading.value = true
  try {
    const list = await fetchArticles({ page: 0, size: 8 })
    const items = list?.items || []
    const featuredFromList =
      items.find(a => a?.featured || a?.isFeatured || a?.pinned || a?.pin) || items[0]
    featuredArticle.value = featuredFromList?.id
      ? await fetchArticleDetail(featuredFromList.id).catch(() => featuredFromList)
      : {}

    latestArticles.value = items
      .filter(a => a?.id && a.id !== featuredFromList?.id)
      .slice(0, 3)
  } finally {
    loading.value = false
  }
  loadLatestBug()
  loadProjects()
  loadCompanies()
  loadLatestForumTopics()
})

onUnmounted(() => {
  disableHomepageSnap()
})

// 只在首页开启滚动吸附：实测吸顶导航高度写入 --header-h，再给 <html> 加类
function measureHeaderHeight() {
  const bar = document.querySelector('.site-top-bar')
  return (bar && bar.offsetHeight) || 60
}
function enableHomepageSnap() {
  document.documentElement.style.setProperty('--header-h', `${measureHeaderHeight()}px`)
  document.documentElement.classList.add('homepage-scroll-snap')
  window.addEventListener('resize', onViewportResize)
}
function disableHomepageSnap() {
  window.removeEventListener('resize', onViewportResize)
  document.documentElement.classList.remove('homepage-scroll-snap')
  document.documentElement.style.removeProperty('--header-h')
}
function onViewportResize() {
  document.documentElement.style.setProperty('--header-h', `${measureHeaderHeight()}px`)
}

function go(id) {
  router.push(`/article/${id}`)
}

function goProject(id) {
  router.push(`/project/${id}`)
}

function splitTags(v) {
  if (!v) return []
  return v.split(',').map(s => s.trim()).filter(Boolean)
}

function formatDate(v) {
  if (!v) return ''
  try {
    return new Date(v).toISOString().slice(0, 10)
  } catch {
    return ''
  }
}
</script>

<style scoped>
/* 第 4 屏：小而美公司 —— 编辑式榜单（左衬线宣言 + 右序号榜单），
   暖色点缀背景区别于项目推荐的蓝色 bento 网格 */
.home-screen-companies {
  background:
    radial-gradient(900px 500px at 92% 108%, rgba(255, 138, 61, 0.09), transparent 60%),
    var(--bg);
  display: flex;
  align-items: center;
  padding: 48px 24px;
}

.home-companies {
  width: 100%;
  max-width: 1080px;
  margin: 0 auto;
  display: grid;
  grid-template-columns: 5fr 7fr;
  gap: 48px;
  align-items: center;
}

/* 左：宣言区（左对齐，衬线大字，呼应口号屏气质） */
.home-companies-copy {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
}

.home-companies-kicker {
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.18em;
  color: var(--accent);
}

.home-companies-title {
  margin: 16px 0 0;
  font-family: 'Songti SC', 'SimSun', 'Noto Serif SC', serif;
  font-size: clamp(32px, 4.5vw, 52px);
  font-weight: 700;
  line-height: 1.25;
  letter-spacing: 0.04em;
  color: var(--text);
}
.home-companies-title .hl {
  color: var(--accent);
}

.home-companies-sub {
  margin: 16px 0 0;
  font-size: 15px;
  line-height: 1.7;
  color: var(--muted);
  max-width: 340px;
}

.home-companies-btn {
  margin-top: 28px;
  padding: 11px 26px;
  border-radius: 999px;
  border: 1px solid var(--accent);
  background: var(--accent);
  color: #fff;
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
  transition: opacity 0.18s ease, transform 0.18s ease;
}
.home-companies-btn:hover {
  opacity: 0.9;
  transform: translateY(-1px);
}

/* 右：序号榜单（细线分隔，悬停整行高亮） */
.home-companies-list {
  margin: 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
}

.home-companies-item {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 12px 8px;
  border-bottom: 1px solid var(--border);
  cursor: pointer;
  transition: background 0.15s ease, padding-left 0.15s ease;
}
.home-companies-item:first-child {
  padding-top: 4px;
}
.home-companies-item:hover {
  background: var(--surface-soft);
  padding-left: 14px;
}

.home-companies-rank {
  flex: none;
  width: 28px;
  font-size: 14px;
  font-weight: 700;
  color: var(--muted);
  font-variant-numeric: tabular-nums;
  letter-spacing: 0.04em;
}

.home-companies-avatar {
  flex: none;
  width: 44px;
  height: 44px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 20px;
  font-weight: 700;
  overflow: hidden;
}
.home-companies-avatar-img {
  width: 100%;
  height: 100%;
  object-fit: contain;
  padding: 4px;
  background: #fff;
}

.home-companies-item-body {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.home-companies-item-name {
  font-size: 15px;
  font-weight: 650;
  color: var(--text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.home-companies-item-meta {
  font-size: 12.5px;
  color: var(--muted);
}

.home-companies-arrow {
  flex: none;
  font-size: 14px;
  color: var(--muted);
  opacity: 0;
  transition: opacity 0.15s ease;
}
.home-companies-item:hover .home-companies-arrow {
  opacity: 1;
  color: var(--accent);
}

/* 窄屏：左右堆叠，宣言区居中 */
@media (max-width: 900px) {
  .home-companies {
    grid-template-columns: 1fr;
    gap: 32px;
  }
  .home-companies-copy {
    align-items: center;
    text-align: center;
  }
  .home-companies-sub {
    margin-left: auto;
    margin-right: auto;
  }
  .home-companies-btn {
    margin-top: 22px;
  }
}
@media (max-width: 480px) {
  .home-screen-companies {
    padding: 40px 18px;
  }
}
</style>

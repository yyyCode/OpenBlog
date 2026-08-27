# 首页两屏全屏滚动实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把首页从普通单页改成「两屏全屏滚动」：第 1 屏口号区（宋体大字「热爱技术 持续生长」、「热爱」「生长」蓝色、纯白底、预留图片背景），第 2 屏文章区（精选大卡片 + 最新文章 3 卡，保留现有样式）。滚动吸附，屏与屏不越界。

**Architecture:** 纯前端改动。数据全部复用现有接口与 siteConfig（`hero_title / hero_subtitle / hero_image_url`）。HomeView.vue 重写为两个 `min-height: calc(100dvh - var(--header-h))` 的区块；进入首页时给 `<html>` 加 `homepage-scroll-snap` 类开启 `scroll-snap-type: y proximity` + `scroll-padding-top`（对齐吸顶导航），离开移除，其它页面不受影响。项目无前端测试基建，验证方式为 `npm run build` + 手动回归。

**Tech Stack:** Vue 3（`<script setup>` + Composition API）、Vue Router 4、Vite。样式在全局 `vue/src/assets/blog.css`（HomeView 无 scoped style）。复用 `--container / --page-pad / --bg / --text / --muted / --border / --surface / --card` CSS 变量与 `.blog-container / .card / .home-featured* / .home-latest-grid / .home-post-card / .home-section*` 全局类。

**关键约定：**
- siteConfig 经 `provide('siteConfig', siteConfig)`（App.vue）注入，**是 ref**，取值用 `siteConfig.value.xxx`。
- 吸顶导航 `.site-top-bar` 是 `position: sticky`、实底、占顶部约 60px。首页每屏高度必须用 `calc(100dvh - var(--header-h))`，`--header-h` 在 HomeView `onMounted` 时用 JS 实测导航 `offsetHeight` 写入 `<html>`，兜底 60px。
- 吸附三件套：区块 `scroll-snap-align: start`；`html.homepage-scroll-snap { scroll-snap-type: y proximity; scroll-padding-top: var(--header-h); }`；HomeView `onMounted` 加类 / `onUnmounted` 移除。
- 口号文案读 `hero_title`（回退「热爱技术 持续生长」），用字符串替换把「热爱」「生长」包上蓝色 `<span class="hl">`（v-html 渲染）。
- 口号区背景：默认纯白；`hero_image_url` 非空则整屏图片背景（白色垫底，图挂了仍白）。**暗色主题下背景用暗色、`.hl` 用暗色主题强调色**（与站点深浅色一致，避免暗色模式下白屏刺眼）。

---

### Task 1: 更新 site-config.sql 默认口号

**Files:**
- Modify: `sql/site-config.sql:18`

- [ ] **Step 1: 修改 hero_title 默认值**

把 `sql/site-config.sql` 第 18 行：

```sql
('hero_title',       '设计，创造，思考未来'),
```

改为：

```sql
('hero_title',       '热爱技术 持续生长'),
```

- [ ] **Step 2: 说明线上库同步方式**

本文件是种子 SQL，不会自动重跑。**上线后需在控制台（站点配置）把 hero_title 改成「热爱技术 持续生长」**，或手动执行：

```sql
UPDATE site_config SET config_value = '热爱技术 持续生长' WHERE config_key = 'hero_title';
```

（此处无需执行，只记录。）

- [ ] **Step 3: 提交**

```bash
git add sql/site-config.sql
git commit -m "feat(web): update default hero title to homepage slogan"
```

---

### Task 2: blog.css 新增全屏区块样式并移除旧 hero 样式

**Files:**
- Modify: `vue/src/assets/blog.css:394-449`

- [ ] **Step 1: 用新样式整块替换旧 `.home-hero*` 样式**

把 `vue/src/assets/blog.css` 中这一段（旧首页 hero 样式，仅 HomeView 使用，Task 3 重写后即废弃，约 394-449 行）：

```css
.home-hero {
  max-width: none;
  margin: 0;
  padding: 52px 0 18px;
}

.home-hero-inner {
  max-width: 720px;
  margin: 0 auto;
  text-align: center;
}

.home-hero-title {
  margin: 0;
  font-size: 42px;
  font-weight: 600;
  letter-spacing: -0.02em;
  line-height: 1.2;
  color: var(--text);
  text-align: center;
}

.home-hero-sub {
  margin: 12px 0 0;
  font-size: 16px;
  line-height: 1.6;
  color: var(--muted);
  text-align: center;
}

@media (max-width: 1024px) {
  .home-hero-title { font-size: 36px; }
  .home-hero-sub { font-size: 15px; }
}
@media (max-width: 680px) {
  .home-hero { padding-top: 34px; }
  .home-hero-title { font-size: 28px; }
  .home-hero-sub { font-size: 14px; }
}

/* 首页 Hero 图片 */
.home-hero-image-wrap {
  width: 100%;
  border-radius: 10px;
  overflow: hidden;
  background: var(--placeholder-tint);
  aspect-ratio: 16 / 9;
  margin-bottom: 32px;
}

.home-hero-image {
  width: 100%;
  height: 100%;
  display: block;
  object-fit: cover;
}
```

整段替换为：

```css
/* ===== 首页：两屏全屏滚动（口号区 + 文章区） ===== */
/* 进入首页时 HomeView 给 <html> 加此 class，开启滚动吸附；离开移除 */
html.homepage-scroll-snap {
  scroll-snap-type: y proximity;
  scroll-padding-top: var(--header-h, 60px);
}

.home-screens .home-screen {
  min-height: calc(100dvh - var(--header-h, 60px));
  scroll-snap-align: start;
  scroll-snap-stop: always;
  box-sizing: border-box;
  position: relative;
}

/* 第 1 屏：口号区 */
.home-screen-slogan {
  background-color: #ffffff;
  background-size: cover;
  background-position: center;
  background-repeat: no-repeat;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  padding: 40px 24px 64px;
}
[data-theme='dark'] .home-screen-slogan {
  background-color: #0f1114;
}
.home-slogan-title {
  margin: 0;
  font-family: 'Songti SC', 'SimSun', 'Noto Serif SC', serif;
  font-weight: 700;
  font-size: clamp(38px, 6vw, 84px);
  letter-spacing: 0.08em;
  line-height: 1.25;
  color: #1f2329;
}
[data-theme='dark'] .home-slogan-title {
  color: #e5e7eb;
}
.home-slogan-title .hl {
  color: #3370ff;
}
[data-theme='dark'] .home-slogan-title .hl {
  color: #4a7fff;
}
.home-slogan-sub {
  margin: 22px 0 0;
  font-size: 15px;
  line-height: 1.7;
  color: var(--muted);
}
.home-slogan-scroll-hint {
  position: absolute;
  bottom: 18px;
  left: 0;
  right: 0;
  text-align: center;
  font-size: 12px;
  letter-spacing: 2px;
  color: #b9bec6;
}

/* 第 2 屏：文章区（精选 + 最新，一屏放下） */
.home-screen-articles {
  background: var(--bg);
  display: flex;
  align-items: center;
}
.home-screen-articles .blog-container {
  padding-top: 28px;
  padding-bottom: 36px;
}
.home-screen-articles .home-section {
  margin-top: 28px;
}
.home-screen-articles .home-section-latest {
  padding-bottom: 0;
}
.home-screen-articles .home-latest-grid {
  gap: 18px;
}

@media (max-width: 680px) {
  .home-slogan-title {
    letter-spacing: 0.04em;
  }
}
```

- [ ] **Step 2: 构建验证**

Run: `cd vue && npm run build`
Expected: 构建成功，无报错（CSS 全局文件，无引用检查；Task 3 会用到新类）。

- [ ] **Step 3: 提交**

```bash
git add vue/src/assets/blog.css
git commit -m "feat(web): add full-screen homepage section styles, drop old hero css"
```

---

### Task 3: HomeView.vue 重写为两屏结构

**Files:**
- Modify: `vue/src/views/HomeView.vue`（整体重写）

- [ ] **Step 1: 重写模板 + 脚本**

把 `vue/src/views/HomeView.vue` 完整替换为：

```vue
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
  </div>
</template>

<script setup>
import { computed, inject, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'

import { fetchArticles, fetchArticleDetail, coverUrl } from '../api/article'

const router = useRouter()
const siteConfig = inject('siteConfig')

// ---- 第 1 屏：口号 ----
const heroImageUrl = computed(() =>
  (siteConfig.value && siteConfig.value.hero_image_url) || ''
)
// 默认纯白背景；hero_image_url 非空则整屏图片背景（白色垫底兜底）
const sloganBgStyle = computed(() => {
  if (!heroImageUrl.value) return {}
  return { backgroundImage: `url(${heroImageUrl.value})` }
})

const heroTitle = computed(() =>
  (siteConfig.value && siteConfig.value.hero_title) || '热爱技术 持续生长'
)
const heroSubtitle = computed(() =>
  (siteConfig.value && siteConfig.value.hero_subtitle) || ''
)

// 把「热爱」「生长」包上蓝色高亮 span（v-html 渲染；文案变了则不高亮，不报错）
const sloganTitleHtml = computed(() => {
  return heroTitle.value
    .replace(/热爱/g, '<span class="hl">热爱</span>')
    .replace(/生长/g, '<span class="hl">生长</span>')
    .replace(/\n/g, '<br />')
})
const heroSubtitleHtml = computed(() => heroSubtitle.value.replace(/\n/g, '<br />'))

// ---- 第 2 屏：文章 ----
const loading = ref(true)
const featuredArticle = ref({})
const latestArticles = ref([])

onMounted(async () => {
  enableHomepageSnap()
  loading.value = true
  try {
    const list = await fetchArticles({ page: 0, size: 8 })
    const items = list?.items || []
    const featuredFromList =
      items.find(a => a?.featured || a?.isFeatured || a?.pinned || a?.pin) || items[0]
    featuredArticle.value = featuredFromList?.id ? await fetchArticleDetail(featuredFromList.id) : {}

    latestArticles.value = items
      .filter(a => a?.id && a.id !== featuredFromList?.id)
      .slice(0, 3)
  } finally {
    loading.value = false
  }
})

onUnmounted(() => {
  disableHomepageSnap()
})

// 只在首页开启滚动吸附：实测吸顶导航高度写入 --header-h，再给 <html> 加类
function enableHomepageSnap() {
  const bar = document.querySelector('.site-top-bar')
  const headerH = (bar && bar.offsetHeight) || 60
  document.documentElement.style.setProperty('--header-h', `${headerH}px`)
  document.documentElement.classList.add('homepage-scroll-snap')
}
function disableHomepageSnap() {
  document.documentElement.classList.remove('homepage-scroll-snap')
}

function go(id) {
  router.push(`/article/${id}`)
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
```

- [ ] **Step 2: 构建验证**

Run: `cd vue && npm run build`
Expected: 构建成功，无报错（`home-hero*` 已从 CSS 移除，模板不再引用）。

- [ ] **Step 3: 提交**

```bash
git add vue/src/views/HomeView.vue
git commit -m "feat(web): rewrite homepage as two full-screen sections (slogan + articles)"
```

---

### Task 4: 全量验证 + 更新 spec 状态

- [ ] **Step 1: 构建**

Run: `cd vue && npm run build`
Expected: 成功，无警告/报错。

- [ ] **Step 2: 确认无残留引用**

Run:
```bash
cd vue && grep -rn "home-hero\|home-hero-image\|home-hero-title\|home-hero-sub" src || echo "no references"
```
Expected: 输出 `no references`（旧 hero 类已从模板和 CSS 移除）。

- [ ] **Step 3: 手动回归**（本地 `npm run dev`，或部署后测线上）

| # | 场景 | 预期 |
|---|---|---|
| 1 | 首页第 1 屏 | 纯白底、宋体大字「热爱技术 持续生长」一行排开、「热爱」「生长」蓝色、副标题、底部「▼ 下滑」 |
| 2 | 下滑 | 恰好落在第 2 屏：精选大卡片 + 最新文章 3 卡，一屏放下；**口号整屏显示时看不到文章区任何内容** |
| 3 | 继续下滑 / 回滚 | 无半屏卡顿、无越界；吸附正常 |
| 4 | 控制台改 hero_title | 首页口号随之变化（命中「热爱/生长」则高亮，否则不高亮） |
| 5 | 控制台填 hero_image_url | 口号区变整屏图片背景，纯白兜底 |
| 6 | 切到 `/all`、`/article/:id` 等其它页 | 滚动恢复正常，无吸附 |
| 7 | 暗色主题 | 口号区暗色底、`.hl` 用暗色主题蓝色 |
| 8 | 手机视口 | 口号区正确占屏（dvh）；极矮屏文章区自动增高、不裁剪 |

- [ ] **Step 4: 更新设计文档状态**

在 `docs/superpowers/specs/2026-08-28-homepage-fullscreen-sections-design.md` 顶部状态行（第 3 行）追加「✅ 已实现（2026-08-28）」。

- [ ] **Step 5: 提交**

```bash
git add docs/superpowers/specs/2026-08-28-homepage-fullscreen-sections-design.md
git commit -m "docs: mark homepage-fullscreen-sections spec as implemented"
```

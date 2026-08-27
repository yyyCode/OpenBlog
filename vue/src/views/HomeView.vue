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

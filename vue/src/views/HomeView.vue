<template>
  <div class="blog-container">
    <section class="home-hero">
      <div class="home-hero-inner">
        <h1 class="home-hero-title">{{ heroTitleLines[0] || '' }}<br />{{ heroTitleLines[1] || '' }}</h1>
        <p class="home-hero-sub" v-html="heroSubtitleHtml"></p>
      </div>
    </section>

    <div class="page-grid home-grid">
      <!-- 左侧：主内容（精选/最新一篇） -->
      <div>
        <div v-if="loading" class="card">
          <div class="card-body" style="padding: 22px; color: var(--muted)">加载中...</div>
        </div>

        <div v-else>
          <div class="card">
            <div class="home-featured">
              <div v-if="!featuredArticle?.id" style="padding: 22px; color: var(--muted)">暂无文章</div>

              <div v-else class="home-featured-grid" @click="go(featuredArticle.id)">
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
                  <div class="home-featured-excerpt">
                    {{ featuredArticle.summary || '' }}
                  </div>
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

          <section class="home-section home-section-latest">
            <div class="home-section-head">
              <h2 class="home-section-title">最新文章</h2>
              <router-link class="home-section-more" to="/all">查看全部 →</router-link>
            </div>

            <div v-if="latestArticles.length === 0" class="card">
              <div class="card-body" style="padding: 22px; color: var(--muted)">暂无文章</div>
            </div>
            <div v-else class="home-latest-grid">
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

          <section class="home-newsletter">
            <div class="home-newsletter-inner">
              <h2 class="home-newsletter-title">订阅更新</h2>
              <p class="home-newsletter-sub">获取最新文章和设计见解，直接发送到您的收件箱。（接口预留）</p>
              <form class="home-newsletter-form" @submit.prevent="onSubscribe">
                <input
                  v-model="email"
                  class="home-newsletter-input"
                  type="email"
                  autocomplete="email"
                  placeholder="输入您的邮箱"
                />
                <button class="home-newsletter-btn" type="submit">订阅</button>
              </form>
            </div>
          </section>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'

import { fetchArticles, fetchArticleDetail, coverUrl } from '../api/article'
import { useRouter } from 'vue-router'
import { showMessage } from '../utils/message'

const router = useRouter()
const siteConfig = inject('siteConfig')

const heroTitle = computed(() => {
  const text = (siteConfig && siteConfig.value && siteConfig.value.hero_title) || '设计，创造，思考未来'
  return text
})

const heroSubtitle = computed(() => {
  const text = (siteConfig && siteConfig.value && siteConfig.value.hero_subtitle) || '探索 AI、设计与技术的交集\n分享关于智能交互、AI 驱动产品与数字创新的实战经验。'
  return text
})

const heroTitleLines = computed(() => {
  const text = heroTitle.value
  const parts = text.split('\n').filter(Boolean)
  if (parts.length >= 2) return parts.slice(0, 2)
  const commaParts = text.split('，')
  if (commaParts.length >= 2) return commaParts.slice(0, 2)
  return [text, '']
})

const heroSubtitleHtml = computed(() => {
  return heroSubtitle.value.replace(/\n/g, '<br />')
})

const loading = ref(true)
const featuredArticle = ref({})
const latestArticles = ref([])
const email = ref('')

onMounted(async () => {
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

function onSubscribe() {
  const v = (email.value || '').trim()
  if (!v) {
    showMessage('请输入邮箱')
    return
  }
  showMessage('订阅接口已预留，暂未接入')
  email.value = ''
}
</script>


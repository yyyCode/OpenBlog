<template>
  <div class="blog-container">
    <section class="home-hero">
      <div class="home-hero-inner">
        <h1 class="home-hero-title">设计，创造，<br />思考未来</h1>
        <p class="home-hero-sub">
          探索设计、技术和创造力的交汇点。<br />
          分享关于用户体验、产品设计和数字创新的见解。
        </p>
      </div>
    </section>

    <div class="page-grid home-grid">
      <!-- 左侧：主内容（精选/最新一篇） -->
      <div>
        <div v-if="loading" class="card">
          <div class="card-body" style="padding: 22px; color: var(--muted)">加载中...</div>
        </div>

        <div v-else class="card">
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
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'

import { fetchArticles, fetchArticleDetail, coverUrl } from '../api/article'
import { useRouter } from 'vue-router'

const router = useRouter()

const loading = ref(true)
const featuredArticle = ref({})

onMounted(async () => {
  loading.value = true
  try {
    const list = await fetchArticles({ page: 0, size: 8 })
    const items = list?.items || []
    const featuredFromList =
      items.find(a => a?.featured || a?.isFeatured || a?.pinned || a?.pin) || items[0]
    featuredArticle.value = featuredFromList?.id ? await fetchArticleDetail(featuredFromList.id) : {}
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
</script>


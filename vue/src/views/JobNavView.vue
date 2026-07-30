<template>
  <div class="blog-container jobs-page">
    <div class="page-header">
      <h1 class="page-title">求职导航</h1>
      <p class="page-subtitle">求职经验、面试技巧、行业洞见</p>
    </div>

    <div v-if="loading" class="article-detail-loading">加载中...</div>

    <div v-else-if="articles.length === 0" class="card">
      <div class="card-body" style="padding: 22px; color: var(--muted)">暂无内容</div>
    </div>

    <div v-else class="article-grid">
      <ArticleCard v-for="a in articles" :key="a.id" :article="a" />
    </div>

    <div v-if="total > size" class="pagination" style="margin-top: 24px; display: flex; justify-content: center; gap: 12px">
      <button class="btn" :disabled="page <= 0" @click="goPage(page - 1)">上一页</button>
      <span style="line-height: 36px; color: var(--muted); font-size: 13px">
        {{ page + 1 }} / {{ Math.ceil(total / size) }}
      </span>
      <button class="btn" :disabled="(page + 1) * size >= total" @click="goPage(page + 1)">下一页</button>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { fetchArticlesByType } from '../api/article'
import ArticleCard from '../components/ArticleCard.vue'

const route = useRoute()
const router = useRouter()
const loading = ref(true)
const articles = ref([])
const page = ref(0)
const size = 20
const total = ref(0)

async function load() {
  loading.value = true
  try {
    const p = Number(route.query.page) || 0
    page.value = p
    const resp = await fetchArticlesByType('JOB_NAV', { page: p, size })
    articles.value = resp?.items || []
    total.value = resp?.total ?? 0
  } finally {
    loading.value = false
  }
}

function goPage(p) {
  router.push({ query: { page: String(p) } })
}

watch(() => route.query.page, () => load())
onMounted(() => load())
</script>

<style scoped>
.jobs-page {
  font-family: -apple-system, BlinkMacSystemFont, 'SF Pro Display', 'SF Pro Text', 'Helvetica Neue', sans-serif;
}

.page-header {
  margin-bottom: 32px;
}
.page-title {
  font-size: 40px;
  font-weight: 700;
  letter-spacing: -0.03em;
  line-height: 1.15;
}
.page-subtitle {
  color: var(--muted);
  font-size: 18px;
  font-weight: 450;
  margin-top: 10px;
}

.article-grid {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

/* ArticleCard content larger on job nav page */
.article-grid :deep(.article-card) {
  padding: 20px;
  border-radius: 14px;
}
.article-grid :deep(.article-title) {
  font-size: 19px;
  font-weight: 700;
  letter-spacing: -0.02em;
}
.article-grid :deep(.article-meta) {
  font-size: 14px;
  margin-top: 6px;
}
</style>

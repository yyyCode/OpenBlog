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
.page-header {
  margin-bottom: 24px;
}
.page-title {
  font-size: 28px;
  font-weight: 800;
  letter-spacing: -0.02em;
}
.page-subtitle {
  color: var(--muted);
  font-size: 14px;
  margin-top: 6px;
}
.article-grid {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
</style>

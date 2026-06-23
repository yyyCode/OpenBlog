<template>
  <div class="search-results-page">
    <div class="page-grid">
      <main class="page-main">
        <div class="search-header">
          <h1 class="search-heading">搜索结果</h1>
          <p v-if="keyword" class="search-keyword">
            关键词：<strong>{{ keyword }}</strong>
            <span v-if="total > 0" class="search-total">，共 {{ total }} 篇</span>
          </p>
        </div>

        <div v-if="loading" class="search-status">搜索中...</div>

        <div v-else-if="error" class="search-status search-status-error">
          {{ error }}
        </div>

        <div v-else-if="items.length === 0" class="search-status">
          <template v-if="keyword">未找到与 "{{ keyword }}" 相关的文章</template>
          <template v-else>请输入搜索关键词</template>
        </div>

        <div v-else class="article-list">
          <ArticleCard v-for="article in items" :key="article.id" :article="article" />
        </div>

        <div v-if="total > size" class="search-pager">
          <button
            class="btn"
            :disabled="page <= 0"
            @click="goPage(page - 1)"
          >
            上一页
          </button>
          <span class="search-pager-info">{{ page + 1 }} / {{ totalPages }}</span>
          <button
            class="btn"
            :disabled="page + 1 >= totalPages"
            @click="goPage(page + 1)"
          >
            下一页
          </button>
        </div>
      </main>

      <aside class="page-side">
        <ProfileCard />
        <BlogInfoCard />
      </aside>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { searchArticles } from '../api/search'
import ArticleCard from '../components/ArticleCard.vue'
import ProfileCard from '../components/ProfileCard.vue'
import BlogInfoCard from '../components/BlogInfoCard.vue'

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const error = ref('')
const items = ref([])
const total = ref(0)
const page = ref(0)
const size = 20

const keyword = computed(() => {
  const q = route.query.q
  return typeof q === 'string' ? q.trim() : ''
})

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / size)))

async function doSearch() {
  const kw = keyword.value
  if (!kw) {
    items.value = []
    total.value = 0
    return
  }
  loading.value = true
  error.value = ''
  try {
    const result = await searchArticles(kw, page.value, size)
    items.value = result.items || []
    total.value = result.total || 0
  } catch (e) {
    error.value = e.message || '搜索失败'
    items.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function goPage(p) {
  page.value = p
  doSearch()
}

watch(keyword, () => {
  page.value = 0
  doSearch()
})

onMounted(() => {
  doSearch()
})
</script>

<style scoped>
.search-results-page {
  padding: 24px 0 48px;
}

.search-header {
  margin-bottom: 28px;
}

.search-heading {
  font-size: 24px;
  font-weight: 700;
  color: var(--text);
  margin: 0 0 8px;
}

.search-keyword {
  font-size: 14px;
  color: var(--muted);
  margin: 0;
}

.search-keyword strong {
  color: var(--accent);
}

.search-total {
  color: var(--muted);
}

.search-status {
  color: var(--muted);
  font-size: 14px;
  padding: 32px 0;
  text-align: center;
}

.search-status-error {
  color: #cf1322;
}

.article-list {
  display: flex;
  flex-direction: column;
  gap: 0;
}

.search-pager {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16px;
  margin-top: 28px;
}

.search-pager-info {
  font-size: 13px;
  color: var(--muted);
  min-width: 60px;
  text-align: center;
}
</style>

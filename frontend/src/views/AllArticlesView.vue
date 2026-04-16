<template>
  <div class="blog-container all-articles-page">
    <div class="all-layout">
      <!-- 右侧：文章正文 -->
      <main class="all-main">
        <div v-if="loadingDetail" class="card">
          <div class="card-body" style="padding: 22px; color: var(--muted)">加载中...</div>
        </div>

        <div v-else-if="!selectedArticle?.id" class="card">
          <div class="card-body" style="padding: 22px; color: var(--muted)">请选择一篇文章</div>
        </div>

        <div v-else class="all-main-inner">
          <div class="card">
            <div class="article-content">
              <div v-if="selectedArticle.coverMediaKey" style="margin-bottom: 14px">
                <img
                  :src="coverUrl(selectedArticle.coverMediaKey)"
                  alt="cover"
                  style="width: 100%; max-height: 340px; object-fit: cover; border-radius: 12px"
                />
              </div>

              <h1 class="article-hero-title">{{ selectedArticle.title }}</h1>
              <div class="article-hero-meta">
                发布于 {{ formatDate(selectedArticle.publishedAt) }} · 作者 {{ selectedArticle.authorNickname || '' }} · 阅读
                {{ selectedArticle.viewCount ?? 0 }}
              </div>

              <div class="markdown-body article-markdown" v-html="html" />
            </div>
          </div>

          <CommentSection v-if="selectedArticle?.id" :article-id="selectedArticle.id" />
        </div>
      </main>
    </div>

    <!-- 外挂式文章列表：左侧抽屉 -->
    <button
      class="articles-fab"
      type="button"
      :aria-expanded="listOpen ? 'true' : 'false'"
      aria-controls="articles-drawer"
      @click="openList"
    >
      目录
    </button>

    <div v-if="listOpen" class="articles-overlay" @click="closeList" />
    <aside
      id="articles-drawer"
      class="articles-drawer"
      :class="{ open: listOpen }"
      role="dialog"
      aria-label="文章目录"
    >
      <div class="articles-drawer-head">
        <div class="articles-drawer-title">目录</div>
        <button class="articles-drawer-close" type="button" aria-label="关闭文章列表" @click="closeList">
          ×
        </button>
      </div>

      <div class="articles-drawer-search">
        <input v-model="keyword" type="text" class="search-input" placeholder="find something..." />
      </div>

      <div class="articles-drawer-body">
        <div v-if="loadingList" class="sidebar-hint" style="color: var(--muted)">加载中...</div>
        <div v-else-if="listItems.length === 0" class="sidebar-hint" style="color: var(--muted)">暂无文章</div>

        <button
          v-for="a in listItems"
          :key="a.id"
          class="sidebar-item articles-drawer-item"
          type="button"
          @click="goArticle(a.id)"
        >
          <div class="sidebar-item-title">{{ a.title }}</div>
          <div class="sidebar-item-date">{{ formatDate(a.publishedAt) }}</div>
        </button>
      </div>
    </aside>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import DOMPurify from 'dompurify'
import { marked } from 'marked'
import { useRouter } from 'vue-router'

import { fetchArticles, fetchArticleDetail, coverUrl } from '../api/article'
import CommentSection from '../components/CommentSection.vue'

const router = useRouter()

const loadingList = ref(true)
const loadingDetail = ref(true)

const keyword = ref('')
const allArticles = ref([])

const selectedId = ref(null)
const selectedArticle = ref({})

onMounted(async () => {
  try {
    loadingList.value = true

    const resp = await fetchArticles({ page: 0, size: 200 })
    allArticles.value = resp?.items || []

    const first = allArticles.value[0]
    if (first?.id) {
      selectedId.value = first.id
      await loadDetail(first.id)
    }
  } finally {
    loadingList.value = false
  }
})

async function loadDetail(id) {
  loadingDetail.value = true
  try {
    selectedArticle.value = await fetchArticleDetail(id)
  } finally {
    loadingDetail.value = false
  }
}

function selectArticle(id) {
  selectedId.value = id
  loadDetail(id)
}

const listOpen = ref(false)

function openList() {
  listOpen.value = true
}

function closeList() {
  listOpen.value = false
}

function goArticle(id) {
  closeList()
  router.push(`/article/${id}`)
}

function onKeyDown(e) {
  if (e.key === 'Escape' && listOpen.value) closeList()
}

onMounted(() => window.addEventListener('keydown', onKeyDown))
onBeforeUnmount(() => window.removeEventListener('keydown', onKeyDown))

const listItems = computed(() => {
  const k = (keyword.value || '').trim().toLowerCase()
  if (!k) return allArticles.value
  return allArticles.value.filter(a => (a.title || '').toLowerCase().includes(k))
})

const html = computed(() => {
  const md = selectedArticle.value?.contentMarkdown || ''
  const raw = marked.parse(md)
  return DOMPurify.sanitize(raw)
})

function formatDate(v) {
  if (!v) return ''
  try {
    return new Date(v).toISOString().slice(0, 10)
  } catch {
    return ''
  }
}
</script>


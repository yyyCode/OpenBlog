<template>
  <div class="blog-container article-detail-page">
    <!-- 桌面端：左侧文章树 -->
    <aside v-if="!isMobile" class="article-tree-rail" aria-label="文章目录">
      <div class="article-tree-card">
        <ArticleTreeSidebar
          :categories="categories"
          :articles="allArticles"
          :current-id="route.params.id"
          :loading="loadingList"
          :show-head="true"
          @select="goArticle"
        />
      </div>
    </aside>

    <!-- 移动端：左侧抽屉 -->
    <button
      v-if="isMobile"
      class="articles-fab"
      type="button"
      :aria-expanded="treeOpen ? 'true' : 'false'"
      aria-controls="article-tree-drawer"
      @click="openTree"
    >
      目录
    </button>

    <div v-if="isMobile && treeOpen" class="articles-overlay" @click="closeTree" />
    <aside
      v-if="isMobile"
      id="article-tree-drawer"
      class="articles-drawer"
      :class="{ open: treeOpen }"
      role="dialog"
      aria-label="文章目录"
    >
      <ArticleTreeSidebar
        :categories="categories"
        :articles="allArticles"
        :current-id="route.params.id"
        :loading="loadingList"
        drawer
        @select="goArticle"
        @close="closeTree"
      />
    </aside>

    <div class="article-detail-wrap">
      <div v-if="loading" class="article-detail-loading">加载中...</div>

      <template v-else>
        <article v-if="article?.id" class="article-reader">
          <p class="article-kicker-meta">
            <span>{{ formatDate(article.publishedAt) }}</span>
            <span class="article-kicker-dot">·</span>
            <span>{{ readMinutes }} 分钟</span>
            <template v-if="categoryLabel">
              <span class="article-kicker-dot">·</span>
              <span>{{ categoryLabel }}</span>
            </template>
          </p>

          <h1 class="article-reader-title">{{ article.title }}</h1>
          <p v-if="article.summary" class="article-reader-lead">{{ article.summary }}</p>

          <figure v-if="article.coverMediaKey" class="article-reader-cover">
            <img :src="coverUrl(article.coverMediaKey)" alt="" />
          </figure>

          <div class="markdown-body article-markdown article-markdown--body" v-html="html" />
        </article>

        <div v-else class="article-detail-empty">文章不存在</div>

        <CommentSection v-if="article?.id" :article-id="article.id" />
      </template>
    </div>

    <!-- 右侧：文章大纲 -->
    <aside v-if="tocItems.length" class="article-toc-rail" aria-label="文章大纲">
      <div class="article-toc-card">
        <div class="article-toc-title">大纲</div>
        <nav class="article-toc-body" aria-label="大纲导航">
          <button
            v-for="it in tocItems"
            :key="it.id"
            class="article-toc-item"
            type="button"
            :style="{ paddingLeft: `${10 + Math.max(0, it.level - 1) * 12}px` }"
            @click="jumpToHeading(it.id)"
          >
            {{ it.text }}
          </button>
        </nav>
      </div>
    </aside>
  </div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref, watch, watchEffect } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import DOMPurify from 'dompurify'
import { marked } from 'marked'

import { fetchArticles, fetchArticleDetail, coverUrl } from '../api/article'
import { fetchCategoryTree } from '../api/category'
import ArticleTreeSidebar from '../components/ArticleTreeSidebar.vue'
import CommentSection from '../components/CommentSection.vue'
import { buildTocAndInjectIds, jumpToHeading } from '../utils/toc'

const route = useRoute()
const router = useRouter()

const loading = ref(true)
const loadingList = ref(true)
const article = ref({})
const allArticles = ref([])
const categories = ref([])
const html = ref('')
const tocItems = ref([])
const treeOpen = ref(false)
const isMobile = ref(false)

let mq = null

onMounted(async () => {
  mq = window.matchMedia('(max-width: 1024px)')
  syncMobile()
  mq.addEventListener('change', syncMobile)

  loadingList.value = true
  try {
    const [catResp, articleResp] = await Promise.all([
      fetchCategoryTree(),
      fetchArticles({ page: 0, size: 200 })
    ])
    categories.value = catResp || []
    allArticles.value = articleResp?.items || []
  } finally {
    loadingList.value = false
  }

  await loadArticle(route.params.id)
  window.addEventListener('keydown', onKeyDown)
})

onBeforeUnmount(() => {
  mq?.removeEventListener('change', syncMobile)
  window.removeEventListener('keydown', onKeyDown)
})

watch(
  () => route.params.id,
  async (id, prev) => {
    if (!id || String(id) === String(prev)) return
    await loadArticle(id)
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }
)

watchEffect(() => {
  const md = article.value?.contentMarkdown || ''
  const rawHtml = marked.parse(md)
  const sanitized = DOMPurify.sanitize(rawHtml)
  const { html: htmlWithIds, toc } = buildTocAndInjectIds(sanitized)
  html.value = htmlWithIds
  tocItems.value = toc
})

const categoryLabel = ref('')
const readMinutes = ref(1)

watchEffect(() => {
  const a = article.value
  categoryLabel.value = formatCategoryLabel(a)
  readMinutes.value = estimateReadMinutes(a?.contentMarkdown)
})

async function loadArticle(id) {
  loading.value = true
  try {
    article.value = await fetchArticleDetail(id)
  } finally {
    loading.value = false
  }
}

function syncMobile() {
  isMobile.value = mq?.matches ?? false
  if (!isMobile.value) treeOpen.value = false
}

function openTree() {
  treeOpen.value = true
}

function closeTree() {
  treeOpen.value = false
}

function goArticle(id) {
  closeTree()
  if (String(id) === String(route.params.id)) return
  router.push(`/article/${id}`)
}

function onKeyDown(e) {
  if (e.key === 'Escape' && treeOpen.value) closeTree()
}

function estimateReadMinutes(md) {
  if (!md || typeof md !== 'string') return 1
  const chars = md.replace(/\s+/g, '').length
  return Math.max(1, Math.round(chars / 450))
}

function formatCategoryLabel(a) {
  const path = a?.categoryPath
  if (Array.isArray(path) && path.length > 0) return path.join(' / ')
  return a?.categoryName || a?.categoryLabel || ''
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

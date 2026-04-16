<template>
  <div class="blog-container article-detail-page">
    <div class="article-detail-wrap">
      <div v-if="loading" class="article-detail-loading">加载中...</div>

      <template v-else>
        <article v-if="article?.id" class="article-reader">
          <router-link class="article-back" to="/all">
            <span class="article-back-chev" aria-hidden="true">‹</span>
            返回博客
          </router-link>

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

    <!-- 外挂式文章列表：左侧抽屉 -->
    <button
      class="articles-fab"
      type="button"
      :aria-expanded="listOpen ? 'true' : 'false'"
      aria-controls="articles-drawer"
      @click="openList"
    >
      博客
    </button>

    <div v-if="listOpen" class="articles-overlay" @click="closeList" />
    <aside
      id="articles-drawer"
      class="articles-drawer"
      :class="{ open: listOpen }"
      role="dialog"
      aria-label="博客文章列表"
    >
      <div class="articles-drawer-head">
        <div class="articles-drawer-title">博客</div>
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
          :class="{ active: String(a.id) === String(route.params.id) }"
          type="button"
          @click="goArticle(a.id)"
        >
          <div class="sidebar-item-title">{{ a.title }}</div>
          <div class="sidebar-item-date">{{ formatDate(a.publishedAt) }}</div>
        </button>
      </div>
    </aside>

    <!-- 外挂式目录：左侧抽屉 -->
    <button
      v-if="tocItems.length"
      class="toc-fab"
      type="button"
      :aria-expanded="tocOpen ? 'true' : 'false'"
      aria-controls="article-toc-drawer"
      @click="tocOpen = true"
    >
      目录
    </button>

    <div v-if="tocOpen" class="toc-overlay" @click="tocOpen = false" />
    <aside
      id="article-toc-drawer"
      class="toc-drawer"
      :class="{ open: tocOpen }"
      role="dialog"
      aria-label="文章目录"
    >
      <div class="toc-drawer-head">
        <div class="toc-drawer-title">目录</div>
        <button class="toc-drawer-close" type="button" aria-label="关闭目录" @click="tocOpen = false">
          ×
        </button>
      </div>
      <nav class="toc-drawer-body" aria-label="目录导航">
        <button
          v-for="it in tocItems"
          :key="it.id"
          class="toc-item"
          type="button"
          :style="{ paddingLeft: `${8 + Math.max(0, it.level - 1) * 12}px` }"
          @click="jumpToHeading(it.id)"
        >
          {{ it.text }}
        </button>
      </nav>
    </aside>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watchEffect } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import DOMPurify from 'dompurify'
import { marked } from 'marked'

import { fetchArticleDetail, fetchArticles, coverUrl } from '../api/article'
import CommentSection from '../components/CommentSection.vue'

const route = useRoute()
const router = useRouter()
const loading = ref(true)
const article = ref({})
const html = ref('')
const tocItems = ref([])
const tocOpen = ref(false)

onMounted(async () => {
  loading.value = true
  try {
    const id = route.params.id
    article.value = await fetchArticleDetail(id)
  } finally {
    loading.value = false
  }
})

const listOpen = ref(false)
const loadingList = ref(true)
const keyword = ref('')
const allArticles = ref([])

onMounted(async () => {
  try {
    loadingList.value = true
    const resp = await fetchArticles({ page: 0, size: 200 })
    allArticles.value = resp?.items || []
  } finally {
    loadingList.value = false
  }
})

const listItems = computed(() => {
  const k = (keyword.value || '').trim().toLowerCase()
  if (!k) return allArticles.value
  return allArticles.value.filter(a => (a.title || '').toLowerCase().includes(k))
})

function openList() {
  listOpen.value = true
}

function closeList() {
  listOpen.value = false
}

function goArticle(id) {
  closeList()
  tocOpen.value = false
  router.push(`/article/${id}`).then(() => {
    window.scrollTo({ top: 0, behavior: 'auto' })
  })
}

watchEffect(() => {
  const md = article.value?.contentMarkdown || ''
  const rawHtml = marked.parse(md)
  const sanitized = DOMPurify.sanitize(rawHtml)
  const { html: htmlWithIds, toc } = buildTocAndInjectIds(sanitized)
  html.value = htmlWithIds
  tocItems.value = toc
  if (!toc.length) tocOpen.value = false
})

/** 后端若将来返回分类名则展示 */
const categoryLabel = ref('')
const readMinutes = ref(1)

watchEffect(() => {
  const a = article.value
  categoryLabel.value = a?.categoryName || a?.categoryLabel || ''
  readMinutes.value = estimateReadMinutes(a?.contentMarkdown)
})

function estimateReadMinutes(md) {
  if (!md || typeof md !== 'string') return 1
  const chars = md.replace(/\s+/g, '').length
  return Math.max(1, Math.round(chars / 450))
}

function formatDate(v) {
  if (!v) return ''
  try {
    return new Date(v).toISOString().slice(0, 10)
  } catch {
    return ''
  }
}

function jumpToHeading(id) {
  tocOpen.value = false
  requestAnimationFrame(() => {
    const el = document.getElementById(id)
    if (!el) return
    el.scrollIntoView({ behavior: 'smooth', block: 'start' })
  })
}

function onKeyDown(e) {
  if (e.key === 'Escape') {
    if (listOpen.value) listOpen.value = false
    if (tocOpen.value) tocOpen.value = false
  }
}

onMounted(() => window.addEventListener('keydown', onKeyDown))
onBeforeUnmount(() => window.removeEventListener('keydown', onKeyDown))

function buildTocAndInjectIds(inputHtml) {
  try {
    const parser = new DOMParser()
    const doc = parser.parseFromString(String(inputHtml || ''), 'text/html')
    const headings = Array.from(doc.querySelectorAll('h1,h2,h3,h4,h5,h6'))
    const used = new Set()

    const toc = headings
      .map((h) => {
        const level = Number(String(h.tagName || '').slice(1)) || 1
        const text = String(h.textContent || '').replace(/\s+/g, ' ').trim()
        if (!text) return null

        let id = String(h.getAttribute('id') || '').trim()
        if (!id) id = slugify(text)
        id = dedupeId(id, used)
        used.add(id)
        h.setAttribute('id', id)

        return { id, level, text }
      })
      .filter(Boolean)

    return { html: doc.body.innerHTML, toc }
  } catch {
    return { html: inputHtml, toc: [] }
  }
}

function slugify(s) {
  const base = String(s || '')
    .toLowerCase()
    .trim()
    .replace(/[\s]+/g, '-')
    .replace(/[^\p{L}\p{N}\-_.~]/gu, '')
    .replace(/-+/g, '-')
    .replace(/^-|-$/g, '')
  return base || `h-${Date.now().toString(36)}`
}

function dedupeId(id, used) {
  let out = id
  let i = 2
  while (used.has(out)) {
    out = `${id}-${i}`
    i += 1
  }
  return out
}
</script>

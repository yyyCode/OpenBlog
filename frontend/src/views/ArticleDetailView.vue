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
import { onBeforeUnmount, onMounted, ref, watchEffect } from 'vue'
import { useRoute } from 'vue-router'
import DOMPurify from 'dompurify'
import { marked } from 'marked'

import { fetchArticleDetail, coverUrl } from '../api/article'
import CommentSection from '../components/CommentSection.vue'

const route = useRoute()
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

watchEffect(() => {
  const md = article.value?.contentMarkdown || ''
  const items = []

  const renderer = new marked.Renderer()
  const slugger = new marked.Slugger()

  renderer.heading = (text, level, raw) => {
    const id = slugger.slug(String(raw || text || ''))
    const plain = String(text || '')
      .replace(/<[^>]+>/g, '')
      .replace(/\s+/g, ' ')
      .trim()
    if (plain) items.push({ id, level, text: plain })
    return `<h${level} id="${id}">${text}</h${level}>`
  }

  const rawHtml = marked.parse(md, { renderer })
  html.value = DOMPurify.sanitize(rawHtml)
  tocItems.value = items

  if (!items.length) tocOpen.value = false
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
  if (e.key === 'Escape' && tocOpen.value) tocOpen.value = false
}

onMounted(() => window.addEventListener('keydown', onKeyDown))
onBeforeUnmount(() => window.removeEventListener('keydown', onKeyDown))
</script>

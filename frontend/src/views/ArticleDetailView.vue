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
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import DOMPurify from 'dompurify'
import { marked } from 'marked'

import { fetchArticleDetail, coverUrl } from '../api/article'
import CommentSection from '../components/CommentSection.vue'

const route = useRoute()
const loading = ref(true)
const article = ref({})

onMounted(async () => {
  loading.value = true
  try {
    const id = route.params.id
    article.value = await fetchArticleDetail(id)
  } finally {
    loading.value = false
  }
})

const html = computed(() => {
  const md = article.value.contentMarkdown || ''
  const raw = marked.parse(md)
  return DOMPurify.sanitize(raw)
})

/** 后端若将来返回分类名则展示 */
const categoryLabel = computed(() => {
  const a = article.value
  return a?.categoryName || a?.categoryLabel || ''
})

const readMinutes = computed(() => estimateReadMinutes(article.value?.contentMarkdown))

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
</script>

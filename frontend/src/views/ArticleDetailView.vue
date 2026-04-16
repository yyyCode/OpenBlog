<template>
  <div class="blog-container article-detail-page">
    <div class="article-detail-grid">
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

      <!-- 右侧：文章大纲（标题目录） -->
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
  </div>
</template>

<script setup>
import { onMounted, ref, watchEffect } from 'vue'
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
  const rawHtml = marked.parse(md)
  const sanitized = DOMPurify.sanitize(rawHtml)
  const { html: htmlWithIds, toc } = buildTocAndInjectIds(sanitized)
  html.value = htmlWithIds
  tocItems.value = toc
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
  requestAnimationFrame(() => {
    const el = document.getElementById(id)
    if (!el) return
    el.scrollIntoView({ behavior: 'smooth', block: 'start' })
  })
}

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

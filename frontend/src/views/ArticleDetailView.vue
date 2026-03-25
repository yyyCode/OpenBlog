<template>
  <div class="blog-container">
    <div class="page-grid">
      <div>
        <div v-if="loading" class="card">
          <div class="card-body" style="padding: 22px">加载中...</div>
        </div>

        <div v-else class="card">
          <div class="article-content">
            <div v-if="!article?.id" style="padding: 20px 0">文章不存在</div>

            <div v-else>
              <div style="margin-bottom: 14px">
                <img
                  v-if="article.coverMediaKey"
                  :src="coverUrl(article.coverMediaKey)"
                  alt="cover"
                  style="width: 100%; max-height: 340px; object-fit: cover; border-radius: 12px"
                />
              </div>

              <h1 class="article-hero-title">{{ article.title }}</h1>
              <div class="article-hero-meta">
                发布于 {{ formatDate(article.publishedAt) }} · 作者 {{ article.authorNickname || '' }} · 阅读
                {{ article.viewCount ?? 0 }}
              </div>

              <div class="markdown-body article-markdown" v-html="html" />
            </div>
          </div>
        </div>
      </div>

      <div class="right-rail">
        <ProfileCard :profile="profile" />
        <BlogInfoCard />
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import DOMPurify from 'dompurify'
import { marked } from 'marked'

import { fetchArticleDetail, coverUrl } from '../api/article'
import ProfileCard from '../components/ProfileCard.vue'
import BlogInfoCard from '../components/BlogInfoCard.vue'
import { fetchPublicProfile } from '../api/profile'

const route = useRoute()
const loading = ref(true)
const article = ref({})
const profile = ref(null)

onMounted(async () => {
  loading.value = true
  try {
    const id = route.params.id
    article.value = await fetchArticleDetail(id)

    profile.value = await fetchPublicProfile()
  } finally {
    loading.value = false
  }
})

const html = computed(() => {
  const md = article.value.contentMarkdown || ''
  const raw = marked.parse(md)
  // MVP：做基础消毒，避免把恶意 HTML 直接注入
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


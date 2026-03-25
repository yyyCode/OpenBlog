<template>
  <div class="blog-container">
    <div class="page-grid">
      <!-- 主内容：一大篇文章 -->
      <div>
        <div v-if="loading" class="card">
          <div class="card-body" style="padding: 22px">加载中...</div>
        </div>

        <div v-else class="card">
          <div class="article-content">
            <div v-if="!article?.id" style="padding: 20px 0">暂无文章</div>

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

      <!-- 右侧：头像/个人信息 -->
      <div class="right-rail">
        <ProfileCard :profile="profile" />
        <BlogInfoCard />
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import DOMPurify from 'dompurify'
import { marked } from 'marked'

import { fetchArticles, fetchArticleDetail } from '../api/article'
import ProfileCard from '../components/ProfileCard.vue'
import BlogInfoCard from '../components/BlogInfoCard.vue'
import { useRouter } from 'vue-router'
import { fetchPublicProfile } from '../api/profile'

const router = useRouter()

const loading = ref(true)
const article = ref({})
const profile = ref(null)

onMounted(async () => {
  loading.value = true
  try {
    const list = await fetchArticles({ page: 0, size: 1 })
    const first = list?.items?.[0]
    article.value = first?.id ? await fetchArticleDetail(first.id) : {}

    profile.value = await fetchPublicProfile()
  } finally {
    loading.value = false
  }
})

const html = computed(() => {
  const md = article.value?.contentMarkdown || ''
  const raw = marked.parse(md)
  return DOMPurify.sanitize(raw)
})

function go(id) {
  router.push(`/article/${id}`)
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


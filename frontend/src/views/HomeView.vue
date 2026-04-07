<template>
  <div class="blog-container">
    <div class="page-grid">
      <!-- 左侧：头像/个人信息 -->
      <div class="right-rail">
        <ProfileCard :profile="profile" />
        <BlogInfoCard />
        <AdminEntryCard />
      </div>

      <!-- 右侧：主内容（文章） -->
      <div>
        <div v-if="loading" class="card">
          <div class="card-body" style="padding: 22px">加载中...</div>
        </div>

        <div v-else>
          <div class="card">
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

          <div v-if="article?.id" class="card" style="margin-top: 18px">
            <div class="card-body">
              <div style="display: flex; align-items: center; justify-content: space-between; gap: 12px">
                <div style="font-weight: 900">最新评论</div>
                <button type="button" class="btn" @click="go(article.id)">进入文章</button>
              </div>

              <div v-if="commentsLoading" style="color: var(--muted); margin-top: 10px">加载中…</div>
              <div v-else-if="commentPreviews.length === 0" style="color: var(--muted); margin-top: 10px">
                暂无评论
              </div>
              <div v-else class="mini-list" style="margin-top: 12px">
                <div v-for="c in commentPreviews" :key="c.id" class="mini-item" style="grid-template-columns: 44px 1fr">
                  <div class="mini-cover" style="width: 44px; height: 44px; border-radius: 999px">
                    <img :src="c.user?.avatarUrl || 'https://www.gravatar.com/avatar/?d=mp&s=96'" alt="avatar" />
                  </div>
                  <div>
                    <div style="display: flex; gap: 10px; align-items: baseline; flex-wrap: wrap">
                      <div style="font-weight: 850; font-size: 13px">{{ c.user?.nickname || '匿名' }}</div>
                      <div style="color: var(--muted); font-size: 12px">{{ formatRelative(c.createdAt) }}</div>
                    </div>
                    <div style="margin-top: 4px; color: var(--text); font-size: 13px; line-height: 1.6">
                      {{ c.content }}
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import DOMPurify from 'dompurify'
import { marked } from 'marked'

import { fetchArticles, fetchArticleDetail, coverUrl } from '../api/article'
import ProfileCard from '../components/ProfileCard.vue'
import BlogInfoCard from '../components/BlogInfoCard.vue'
import AdminEntryCard from '../components/AdminEntryCard.vue'
import { useRouter } from 'vue-router'
import { fetchPublicProfile } from '../api/profile'
import { listComments } from '../api/comment'

const router = useRouter()

const loading = ref(true)
const article = ref({})
const profile = ref(null)
const commentsLoading = ref(false)
const commentPreviews = ref([])

onMounted(async () => {
  loading.value = true
  try {
    const list = await fetchArticles({ page: 0, size: 1 })
    const first = list?.items?.[0]
    article.value = first?.id ? await fetchArticleDetail(first.id) : {}

    profile.value = await fetchPublicProfile()

    if (article.value?.id) {
      commentsLoading.value = true
      try {
        const resp = await listComments(article.value.id, 0, 3)
        const top = resp?.items || []
        // 首页预览只展示顶层评论
        commentPreviews.value = top.slice(0, 3)
      } finally {
        commentsLoading.value = false
      }
    }
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

function formatRelative(v) {
  if (!v) return ''
  const t = new Date(v).getTime()
  if (Number.isNaN(t)) return ''
  let diff = Date.now() - t
  if (diff < 0) diff = 0
  if (diff < 60_000) return '刚刚'
  const m = Math.floor(diff / 60_000)
  if (m < 60) return `${m} 分钟前`
  const h = Math.floor(m / 60)
  if (h < 24) return `${h} 小时前`
  const d = Math.floor(h / 24)
  if (d < 7) return `${d} 天前`
  return new Date(t).toISOString().slice(0, 10)
}
</script>


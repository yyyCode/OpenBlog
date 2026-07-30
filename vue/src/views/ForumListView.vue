<template>
  <div class="page-wrap forum-list-page">
    <div class="page-main">
      <div class="forum-list-header">
        <h1 class="forum-list-title">论坛</h1>
        <router-link to="/forum/new" class="btn primary forum-new-btn">发布话题</router-link>
      </div>

      <div v-if="loading" class="forum-loading">加载中...</div>
      <div v-else-if="error" class="forum-error">{{ error }}</div>
      <div v-else-if="items.length === 0" class="forum-empty">
        <p>暂无话题，来发布第一个吧！</p>
      </div>

      <div v-else class="forum-topic-list">
        <div v-for="t in items" :key="t.id" class="forum-topic-card" @click="goTopic(t.id)">
          <div class="forum-topic-card-main">
            <h2 class="forum-topic-card-title">{{ t.title }}</h2>
            <p class="forum-topic-card-excerpt">{{ excerpt(t.content) }}</p>
            <div class="forum-topic-card-meta">
              <span class="forum-topic-card-author">{{ t.authorName || '匿名' }}</span>
              <span class="forum-topic-card-sep">·</span>
              <span>{{ formatTime(t.createdAt) }}</span>
              <span class="forum-topic-card-sep">·</span>
              <span>{{ t.viewCount }} 浏览</span>
              <span class="forum-topic-card-sep">·</span>
              <span>{{ t.commentCount }} 评论</span>
            </div>
          </div>
        </div>
      </div>

      <div v-if="total > size" class="forum-pager">
        <button class="btn" :disabled="page <= 0" @click="goPage(page - 1)">上一页</button>
        <span class="forum-pager-info">{{ page + 1 }} / {{ totalPages }}</span>
        <button class="btn" :disabled="(page + 1) * size >= total" @click="goPage(page + 1)">下一页</button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { fetchForumTopics } from '../api/forum'

const router = useRouter()

const loading = ref(false)
const items = ref([])
const total = ref(0)
const page = ref(0)
const size = 20
const error = ref('')

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / size)))

function excerpt(content) {
  if (!content) return ''
  return content.replace(/#{1,6}\s|[*_~`>]|!?\[.*?\]\(.*?\)/g, '').slice(0, 150) + (content.length > 150 ? '...' : '')
}

function formatTime(v) {
  if (!v) return ''
  const t = new Date(v).getTime()
  if (Number.isNaN(t)) return ''
  const d = new Date(t)
  const now = new Date()
  const diff = now - d
  if (diff < 3600000) return Math.floor(diff / 60000) + '分钟前'
  if (diff < 86400000) return Math.floor(diff / 3600000) + '小时前'
  return d.toISOString().replace('T', ' ').slice(0, 10)
}

function goTopic(id) {
  router.push('/forum/topic/' + id)
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const result = await fetchForumTopics({ page: page.value, size })
    items.value = result.items || []
    total.value = result.total || 0
  } catch (e) {
    error.value = e?.message || '加载失败'
    items.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

async function goPage(p) {
  page.value = p
  await load()
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

onMounted(() => load())
</script>

<style scoped>
.forum-list-page {
  max-width: 780px;
  margin: 0 auto;
  padding: 32px 16px 60px;
}

.forum-list-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 24px;
}

.forum-list-title {
  font-size: 28px;
  font-weight: 700;
  margin: 0;
  color: var(--text, #1a1a2e);
}

.forum-new-btn {
  padding: 8px 18px;
  font-size: 14px;
}

.forum-loading,
.forum-empty,
.forum-error {
  text-align: center;
  padding: 40px 0;
  color: var(--muted, #888);
}

.forum-error {
  color: #cf1322;
}

.forum-topic-list {
  display: flex;
  flex-direction: column;
  gap: 0;
}

.forum-topic-card {
  padding: 20px 0;
  border-bottom: 1px solid var(--border, #e5e6e8);
  cursor: pointer;
  transition: opacity 0.15s;
}

.forum-topic-card:hover {
  opacity: 0.7;
}

.forum-topic-card-title {
  font-size: 18px;
  font-weight: 600;
  margin: 0 0 8px;
  color: var(--text, #1a1a2e);
  line-height: 1.4;
}

.forum-topic-card-excerpt {
  font-size: 14px;
  color: var(--muted, #666);
  margin: 0 0 10px;
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.forum-topic-card-meta {
  font-size: 13px;
  color: var(--muted, #999);
  display: flex;
  align-items: center;
  gap: 2px;
}

.forum-topic-card-author {
  font-weight: 500;
  color: var(--text-soft, #555);
}

.forum-topic-card-sep {
  margin: 0 4px;
  color: var(--border, #ccc);
}

.forum-pager {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  margin-top: 24px;
}

.forum-pager-info {
  font-size: 13px;
  color: var(--muted, #888);
}
</style>

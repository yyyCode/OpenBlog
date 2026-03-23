<template>
  <div class="card article-card">
    <div class="article-cover">
      <img v-if="article.coverMediaKey" :src="coverUrl(article.coverMediaKey)" alt="cover" />
      <div v-else style="width: 100%; height: 100%; background: #e9e9e9" />
    </div>
    <div style="padding: 14px 12px 12px 0">
      <div class="article-title" @click="goDetail">
        {{ article.title }}
      </div>
      <div class="article-meta">
        {{ formatDate(article.publishedAt) }} · {{ article.authorNickname ? article.authorNickname : '作者' }}
      </div>
      <div style="margin-top: 10px; color: var(--muted); font-size: 14px; line-height: 1.65">
        {{ article.summary || '' }}
      </div>
      <div style="margin-top: 14px; display: flex; gap: 16px; color: var(--muted); font-size: 12px">
        <span>❤ {{ article.likeCount ?? 0 }}</span>
        <span>★ {{ article.favoriteCount ?? 0 }}</span>
        <span>💬 {{ article.commentCount ?? 0 }}</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { coverUrl } from '../api/article'

const props = defineProps({
  article: {
    type: Object,
    required: true
  }
})

const router = useRouter()

function goDetail() {
  router.push(`/article/${props.article.id}`)
}

function formatDate(v) {
  if (!v) return ''
  try {
    const d = new Date(v)
    return d.toISOString().slice(0, 10)
  } catch {
    return ''
  }
}
</script>


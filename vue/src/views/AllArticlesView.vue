<template>
  <div class="blog-container all-articles-page">
    <div v-if="loading" class="article-detail-loading">加载中...</div>
    <div v-else class="card">
      <div class="card-body" style="padding: 22px; color: var(--muted)">暂无文章</div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'

import { fetchArticles } from '../api/article'

const router = useRouter()
const loading = ref(true)

onMounted(async () => {
  try {
    const resp = await fetchArticles({ page: 0, size: 200 })
    const first = resp?.items?.[0]
    if (first?.id) {
      router.replace(`/article/${first.id}`)
      return
    }
  } finally {
    loading.value = false
  }
})
</script>

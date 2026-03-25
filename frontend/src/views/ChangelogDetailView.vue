<template>
  <div class="blog-container">
    <div class="page-grid">
      <div>
        <div v-if="loading" class="card">
          <div class="card-body" style="padding: 22px">加载中...</div>
        </div>

        <div v-else class="card">
          <div class="article-content">
            <div v-if="!entry?.id" style="padding: 20px 0">更新日志不存在</div>

            <div v-else>
              <h1 class="article-hero-title">{{ entry.title }}</h1>
              <div class="article-hero-meta">
                发布于 {{ formatDate(entry.publishedAt) }}
                <span v-if="entry.versionLabel"> · 版本 {{ entry.versionLabel }}</span>
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
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import DOMPurify from 'dompurify'
import { marked } from 'marked'

import { fetchChangelogDetail } from '../api/changelog'
import ProfileCard from '../components/ProfileCard.vue'
import BlogInfoCard from '../components/BlogInfoCard.vue'
import { fetchPublicProfile } from '../api/profile'

const route = useRoute()
const loading = ref(true)
const entry = ref({})
const profile = ref(null)

async function load() {
  loading.value = true
  try {
    const id = route.params.id
    entry.value = await fetchChangelogDetail(id)
    profile.value = await fetchPublicProfile()
  } catch {
    entry.value = {}
  } finally {
    loading.value = false
  }
}

onMounted(() => load())

watch(
  () => route.params.id,
  () => load()
)

const html = computed(() => {
  const md = entry.value?.contentMarkdown || ''
  const raw = marked.parse(md)
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

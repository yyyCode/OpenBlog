<template>
  <div class="blog-container projects-page">
    <div class="page-header">
      <h1 class="page-title">项目推荐</h1>
      <p class="page-subtitle">一些我参与或独立完成的项目</p>
    </div>

    <div v-if="loading" class="article-detail-loading">加载中...</div>

    <div v-else-if="items.length === 0" class="card">
      <div class="card-body" style="padding: 22px; color: var(--muted)">暂无项目</div>
    </div>

    <div v-else class="article-grid">
      <div
        v-for="item in items"
        :key="item.id"
        class="card article-card project-card"
        @click="go(item.id)"
      >
        <div class="article-cover">
          <img v-if="item.coverMediaKey" :src="coverUrl(item.coverMediaKey)" alt="cover" />
          <div v-else style="width: 100%; height: 100%" />
        </div>
        <div class="project-card-body">
          <div class="article-title project-card-title">
            {{ item.title }}
          </div>
          <div class="article-meta">
            {{ formatDate(item.publishedAt) }} · {{ item.status === 'PUBLISHED' ? '已上线' : '开发中' }}
          </div>
          <div v-if="item.techStack" class="project-card-tags">
            <span v-for="tag in splitTags(item.techStack)" :key="tag" class="project-tag">{{ tag }}</span>
          </div>
          <div class="project-card-summary">{{ item.summary || '' }}</div>
          <div class="project-card-links">
            <a
              v-if="item.projectUrl"
              :href="item.projectUrl"
              target="_blank"
              rel="noopener noreferrer"
              @click.stop
            >🔗 项目链接</a>
            <a
              v-if="item.githubUrl"
              :href="item.githubUrl"
              target="_blank"
              rel="noopener noreferrer"
              @click.stop
            >GitHub</a>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { fetchProjects, coverUrl } from '../api/project'

const router = useRouter()
const loading = ref(true)
const items = ref([])

onMounted(async () => {
  loading.value = true
  try {
    const res = await fetchProjects(0, 50)
    items.value = res?.items || []
  } finally {
    loading.value = false
  }
})

function go(id) {
  router.push(`/project/${id}`)
}

function splitTags(v) {
  if (!v) return []
  return v.split(',').map(s => s.trim()).filter(Boolean)
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

<style scoped>
.projects-page {
  font-family: -apple-system, BlinkMacSystemFont, 'SF Pro Display', 'SF Pro Text', 'Helvetica Neue', sans-serif;
}

.page-header {
  margin-bottom: 32px;
}
.page-title {
  font-size: 40px;
  font-weight: 700;
  letter-spacing: -0.03em;
  line-height: 1.15;
}
.page-subtitle {
  color: var(--muted);
  font-size: 18px;
  font-weight: 450;
  margin-top: 10px;
}

.article-grid {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

/* 对齐 Bug 案例页的大卡片风格：横向 grid（200px 封面 + 内容）由全局 .article-card 提供 */
.project-card {
  padding: 20px;
  border-radius: 14px;
  cursor: pointer;
  transition: box-shadow 0.2s ease, border-color 0.2s ease;
}
.project-card:hover {
  border-color: var(--accent);
  box-shadow: 0 6px 20px rgba(51, 112, 255, 0.12);
}

.project-card-title {
  font-size: 19px;
  font-weight: 700;
  letter-spacing: -0.02em;
}
.project-card-title:hover {
  color: var(--accent);
}

.project-card-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 10px;
}
.project-tag {
  display: inline-block;
  padding: 3px 8px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 600;
  color: var(--accent);
  background: rgba(51, 112, 255, 0.08);
}
[data-theme='dark'] .project-tag {
  background: rgba(74, 127, 255, 0.15);
}

.project-card-summary {
  margin-top: 10px;
  color: var(--muted);
  font-size: 14px;
  line-height: 1.65;
}

.project-card-links {
  margin-top: 14px;
  display: flex;
  gap: 16px;
}
.project-card-links a {
  color: var(--accent);
  font-size: 13px;
  text-decoration: none;
}
.project-card-links a:hover {
  text-decoration: underline;
}
</style>

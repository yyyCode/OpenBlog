<template>
  <div class="blog-container">
    <div v-if="loading" class="card">
      <div class="card-body" style="padding: 22px">加载中...</div>
    </div>

    <div v-else>
      <div v-if="!project?.id" class="card">
        <div class="card-body" style="padding: 22px; color: var(--muted)">项目不存在</div>
      </div>

      <div v-else>
        <router-link to="/projects" class="article-back">
          <span class="article-back-chev">←</span>
          返回项目列表
        </router-link>

        <h1 class="project-detail-title">{{ project.title }}</h1>

        <div class="project-detail-meta">
          <span>{{ formatDate(project.publishedAt) }}</span>
          <template v-if="project.techStack">
            <span class="article-kicker-dot">·</span>
            <div class="project-detail-tags">
              <span
                v-for="tag in splitTags(project.techStack)"
                :key="tag"
                class="project-detail-tag"
              >{{ tag }}</span>
            </div>
          </template>
        </div>

        <figure v-if="project.coverMediaKey" class="project-detail-cover">
          <img :src="coverUrl(project.coverMediaKey)" alt="" />
        </figure>

        <div class="markdown-body article-markdown" v-html="html" />

        <div v-if="project.projectUrl || project.githubUrl" class="project-detail-links">
          <a
            v-if="project.projectUrl"
            :href="project.projectUrl"
            target="_blank"
            rel="noopener noreferrer"
            class="project-link-btn"
          >
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6" />
              <polyline points="15 3 21 3 21 9" />
              <line x1="10" y1="14" x2="21" y2="3" />
            </svg>
            查看项目
          </a>
          <a
            v-if="project.githubUrl"
            :href="project.githubUrl"
            target="_blank"
            rel="noopener noreferrer"
            class="project-link-btn project-link-btn--gh"
          >
            <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor">
              <path d="M12 .5C5.73.5.75 5.67.75 12.09c0 5.15 3.28 9.52 7.83 11.06.57.11.78-.25.78-.55 0-.27-.01-1.17-.02-2.12-3.18.71-3.85-1.39-3.85-1.39-.52-1.36-1.28-1.72-1.28-1.72-1.05-.74.08-.73.08-.73 1.16.08 1.77 1.23 1.77 1.23 1.03 1.8 2.7 1.28 3.36.98.1-.77.4-1.28.73-1.57-2.54-.3-5.21-1.3-5.21-5.79 0-1.28.44-2.33 1.16-3.15-.12-.29-.5-1.48.11-3.08 0 0 .95-.31 3.11 1.2.9-.26 1.86-.39 2.82-.39.96 0 1.92.13 2.82.39 2.16-1.51 3.11-1.2 3.11-1.2.61 1.6.23 2.79.11 3.08.72.82 1.16 1.87 1.16 3.15 0 4.5-2.68 5.49-5.23 5.79.41.36.78 1.07.78 2.17 0 1.57-.02 2.83-.02 3.22 0 .3.21.67.79.55 4.55-1.54 7.83-5.91 7.83-11.06C23.25 5.67 18.27.5 12 .5z" />
            </svg>
            GitHub
          </a>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import DOMPurify from 'dompurify'
import { marked } from 'marked'
import { fetchProjectDetail, coverUrl } from '../api/project'

const route = useRoute()
const loading = ref(true)
const project = ref({})

async function load() {
  loading.value = true
  try {
    const id = route.params.id
    project.value = await fetchProjectDetail(id)
  } catch {
    project.value = {}
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
  const md = project.value?.contentMarkdown || ''
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

function splitTags(v) {
  if (!v) return []
  return v.split(',').map(s => s.trim()).filter(Boolean)
}
</script>

<style scoped>
.project-detail-title {
  margin: 0 0 12px;
  font-size: clamp(28px, 4.2vw, 36px);
  font-weight: 700;
  letter-spacing: -0.025em;
  line-height: 1.15;
  color: var(--text);
}

.project-detail-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px;
  margin-bottom: 28px;
  font-size: 13px;
  color: var(--muted);
}

.project-detail-tags {
  display: inline-flex;
  flex-wrap: wrap;
  gap: 6px;
}

.project-detail-tag {
  display: inline-block;
  padding: 2px 7px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 600;
  color: var(--accent);
  background: rgba(51, 112, 255, 0.08);
}

[data-theme='dark'] .project-detail-tag {
  background: rgba(74, 127, 255, 0.15);
}

.project-detail-cover {
  margin: 0 0 36px;
  border-radius: 10px;
  overflow: hidden;
  background: var(--placeholder-tint);
}

.project-detail-cover img {
  width: 100%;
  display: block;
  vertical-align: middle;
  object-fit: cover;
  max-height: min(52vh, 440px);
}

.project-detail-links {
  display: flex;
  gap: 12px;
  margin-top: 40px;
}

.project-link-btn {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 10px 20px;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 600;
  text-decoration: none;
  color: #fff;
  background: var(--accent);
  border: 1px solid transparent;
  transition: background 0.15s ease;
}

.project-link-btn:hover {
  background: var(--accent2);
}

.project-link-btn--gh {
  background: #24292e;
}

.project-link-btn--gh:hover {
  background: #1b1f23;
}

[data-theme='dark'] .project-link-btn--gh {
  background: #3a3a3e;
}

[data-theme='dark'] .project-link-btn--gh:hover {
  background: #4a4a50;
}
</style>

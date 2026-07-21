<template>
  <div class="blog-container">
    <div v-if="loading" class="card">
      <div class="card-body" style="padding: 22px; color: var(--muted)">加载中...</div>
    </div>

    <div v-else>
      <div class="projects-page-head">
        <h1 class="projects-page-title">项目推荐</h1>
        <p class="projects-page-sub">一些我参与或独立完成的项目</p>
      </div>

      <div v-if="items.length === 0" class="card">
        <div class="card-body" style="padding: 22px; color: var(--muted)">暂无项目</div>
      </div>

      <div v-else class="projects-grid">
        <div
          v-for="item in items"
          :key="item.id"
          class="project-card"
          @click="go(item.id)"
        >
          <div class="project-card-cover">
            <img v-if="item.coverMediaKey" :src="coverUrl(item.coverMediaKey)" alt="cover" />
          </div>
          <div class="project-card-body">
            <h2 class="project-card-title">{{ item.title }}</h2>
            <p class="project-card-summary">{{ item.summary || '' }}</p>
            <div v-if="item.techStack" class="project-card-tags">
              <span
                v-for="tag in splitTags(item.techStack)"
                :key="tag"
                class="project-tag"
              >{{ tag }}</span>
            </div>
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
</script>

<style scoped>
.projects-page-head {
  margin-bottom: 32px;
}

.projects-page-title {
  margin: 0;
  font-size: 32px;
  font-weight: 700;
  letter-spacing: -0.02em;
  color: var(--text);
}

.projects-page-sub {
  margin: 8px 0 0;
  font-size: 15px;
  color: var(--muted);
}

.projects-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 24px;
}

@media (max-width: 1024px) {
  .projects-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 680px) {
  .projects-grid {
    grid-template-columns: 1fr;
  }
}

.project-card {
  border-radius: 10px;
  overflow: hidden;
  border: 1px solid var(--border);
  background: var(--card);
  cursor: pointer;
  transition: box-shadow 0.2s ease, border-color 0.2s ease;
}

.project-card:hover {
  border-color: var(--accent);
  box-shadow: 0 4px 16px rgba(51, 112, 255, 0.1);
}

.project-card-cover {
  aspect-ratio: 16 / 9;
  background: var(--placeholder-tint);
}

.project-card-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.project-card-body {
  padding: 14px 14px 16px;
}

.project-card-title {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  letter-spacing: -0.01em;
  color: var(--text);
  line-height: 1.3;
}

.project-card-summary {
  margin-top: 8px;
  font-size: 14px;
  line-height: 1.6;
  color: var(--muted);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
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
</style>

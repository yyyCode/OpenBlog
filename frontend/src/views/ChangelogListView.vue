<template>
  <div class="blog-container">
    <div class="page-grid">
      <div>
        <div v-if="loading" class="card">
          <div class="card-body" style="padding: 22px">加载中...</div>
        </div>

        <div v-else class="card">
          <div class="article-content">
            <h1 class="article-hero-title">更新日志</h1>
            <div class="article-hero-meta">记录本站与博客系统的版本与改动</div>

            <div v-if="items.length === 0" style="margin-top: 18px; color: var(--muted)">
              暂无更新日志
            </div>

            <div v-else class="changelog-list">
              <router-link
                v-for="item in items"
                :key="item.id"
                class="changelog-row"
                :to="`/changelog/${item.id}`"
              >
                <span class="changelog-row-title">{{ item.title }}</span>
                <span v-if="item.versionLabel" class="changelog-row-ver">v{{ item.versionLabel }}</span>
                <span class="changelog-row-date">{{ formatDate(item.publishedAt) }}</span>
              </router-link>
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
import { onMounted, ref } from 'vue'
import ProfileCard from '../components/ProfileCard.vue'
import BlogInfoCard from '../components/BlogInfoCard.vue'
import { fetchChangelogList } from '../api/changelog'
import { fetchPublicProfile } from '../api/profile'

const loading = ref(true)
const items = ref([])
const profile = ref(null)

onMounted(async () => {
  loading.value = true
  try {
    const res = await fetchChangelogList(0, 50)
    items.value = res?.items || []
    profile.value = await fetchPublicProfile()
  } finally {
    loading.value = false
  }
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

<style scoped>
.changelog-list {
  margin-top: 22px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.changelog-row {
  display: grid;
  grid-template-columns: 1fr auto auto;
  gap: 12px;
  align-items: baseline;
  padding: 12px 14px;
  border: 1px solid var(--border);
  border-radius: 12px;
  text-decoration: none;
  color: var(--text);
  background: var(--surface-soft);
  transition: border-color 0.2s, box-shadow 0.2s;
}

.changelog-row:hover {
  border-color: rgba(170, 59, 255, 0.35);
  box-shadow: 0 6px 16px rgba(0, 0, 0, 0.06);
}

.changelog-row-title {
  font-weight: 900;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.changelog-row-ver {
  font-size: 12px;
  font-weight: 800;
  color: var(--muted);
  white-space: nowrap;
}

.changelog-row-date {
  font-size: 12px;
  color: var(--muted);
  white-space: nowrap;
}

@media (max-width: 560px) {
  .changelog-row {
    grid-template-columns: 1fr;
    gap: 6px;
  }
}
</style>

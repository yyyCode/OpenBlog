<template>
  <div class="console-page">
    <header class="console-page-header">
      <div class="console-page-title">
        <h1>文章管理</h1>
      </div>
    </header>

    <div class="console-card console-inner-card">
      <div class="manage-top">
        <div class="manage-top-left">
          <div style="font-weight: 900">我的文章</div>
          <div style="color: var(--console-muted, var(--muted)); font-size: 12px">点击文章进入编辑</div>
        </div>
        <div class="manage-top-right">
          <input v-model="keyword" class="input manage-search" type="search" placeholder="搜索标题..." />
          <button class="btn primary" style="padding: 8px 12px" @click="goNew">新建文章</button>
        </div>
      </div>

      <div v-if="loadingArticles" style="color: var(--console-muted, var(--muted))">加载中...</div>
      <div v-else-if="listItems.length === 0" style="color: var(--console-muted, var(--muted))">暂无文章</div>

      <div v-else class="manage-list">
        <div v-for="a in listItems" :key="a.id" class="manage-item" @click="goEdit(a.id)">
          <div class="manage-item-main">
            <div class="manage-item-title">{{ a.title || '未命名' }}</div>
            <div class="manage-item-meta">
              <span class="manage-status" :class="statusClass(a.status)">{{ statusLabel(a.status) }}</span>
              <span>{{ formatDate(a.publishedAt) }}</span>
            </div>
          </div>
          <div class="manage-item-actions" @click.stop>
            <button class="btn" style="padding: 6px 10px" @click="goEdit(a.id)">编辑</button>
            <button v-if="a.status === 'PUBLISHED'" class="btn" style="padding: 6px 10px" @click="hideArticle(a.id)">隐藏</button>
            <button v-if="a.status === 'HIDDEN'" class="btn" style="padding: 6px 10px" @click="republish(a.id)">发布</button>
            <button class="btn" style="padding: 6px 10px" @click="remove(a.id)">删除</button>
          </div>
        </div>
      </div>

      <div v-if="articleSuccess" class="success" style="margin-top: 10px">{{ articleSuccess }}</div>
      <div v-if="articleError" class="error" style="margin-top: 10px">{{ articleError }}</div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { deleteMyArticle, fetchMyArticles, publishArticle, unpublishArticle } from '../api/admin'

const router = useRouter()

const loadingArticles = ref(false)
const myArticles = ref([])
const keyword = ref('')

const articleError = ref('')
const articleSuccess = ref('')

function formatDate(v) {
  if (!v) return ''
  try {
    return new Date(v).toISOString().slice(0, 10)
  } catch {
    return ''
  }
}

function statusLabel(s) {
  const map = { DRAFT: '草稿', SCHEDULED: '已预约', PUBLISHED: '已发布', HIDDEN: '已隐藏' }
  return map[s] || s
}

function statusClass(s) {
  return `st-${(s || '').toLowerCase()}`
}

async function loadArticles() {
  loadingArticles.value = true
  try {
    const resp = await fetchMyArticles(0, 200)
    myArticles.value = resp?.items || []
  } finally {
    loadingArticles.value = false
  }
}

const listItems = computed(() => {
  const k = (keyword.value || '').trim().toLowerCase()
  if (!k) return myArticles.value
  return myArticles.value.filter((a) => (a.title || '').toLowerCase().includes(k))
})

function goNew() {
  router.push('/console/articles/new')
}

function goEdit(id) {
  router.push({ path: '/console/articles/new', query: { id: String(id) } })
}

async function remove(id) {
  articleError.value = ''
  articleSuccess.value = ''
  if (!window.confirm('确定要永久删除这篇文章吗？此操作不可撤销。')) {
    return
  }
  try {
    await deleteMyArticle(id)
    await loadArticles()
    articleSuccess.value = '删除成功'
  } catch (e) {
    const apiCode = e?.code
    const httpStatus = e?.httpStatus
    const prefix = apiCode ? `错误码 ${apiCode}` : httpStatus ? `HTTP ${httpStatus}` : ''
    articleError.value = prefix ? `${prefix}：${e?.message || '删除失败'}` : e?.message || '删除失败'
    articleSuccess.value = ''
  }
}

async function hideArticle(id) {
  articleError.value = ''
  articleSuccess.value = ''
  try {
    await unpublishArticle(id)
    await loadArticles()
    articleSuccess.value = '已隐藏'
  } catch (e) {
    articleError.value = e?.message || '隐藏失败'
    articleSuccess.value = ''
  }
}

async function republish(id) {
  articleError.value = ''
  articleSuccess.value = ''
  try {
    await publishArticle(id)
    await loadArticles()
    articleSuccess.value = '已发布'
  } catch (e) {
    articleError.value = e?.message || '发布失败'
    articleSuccess.value = ''
  }
}

onMounted(() => {
  loadArticles()
})
</script>

<style scoped>
.manage-top {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.manage-top-right {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.manage-search {
  min-width: 240px;
}

.manage-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.manage-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px;
  border: 1px solid var(--console-border, var(--border));
  background: var(--console-bg, var(--surface-soft));
  border-radius: 10px;
  cursor: pointer;
  transition: background 120ms ease, border-color 120ms ease;
}

.manage-item:hover {
  background: var(--console-accent-soft);
  border-color: rgba(51, 112, 255, 0.4);
}

.manage-item-main {
  min-width: 0;
}

.manage-item-title {
  font-weight: 950;
  line-height: 1.3;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 64vw;
}

.manage-item-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--console-muted, var(--muted));
  font-size: 12px;
  margin-top: 6px;
}

.manage-status {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 700;
  line-height: 1.6;
}

.st-published {
  color: #067a3b;
  background: rgba(6, 122, 59, 0.1);
}

.st-draft {
  color: var(--console-muted, var(--muted));
  background: var(--console-bg, var(--surface-soft));
  border: 1px solid var(--console-border, var(--border));
}

.st-scheduled {
  color: #3370ff;
  background: rgba(51, 112, 255, 0.1);
}

.st-hidden {
  color: #b42318;
  background: rgba(180, 35, 24, 0.1);
}

.manage-item-actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}

@media (max-width: 720px) {
  .manage-top {
    align-items: flex-start;
    flex-direction: column;
  }
  .manage-search {
    min-width: 0;
    width: 100%;
  }
  .manage-item-title {
    max-width: 60vw;
  }
}
</style>


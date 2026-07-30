<template>
  <div class="console-page">
    <header class="console-page-header">
      <div class="console-page-title">
        <h1>论坛管理</h1>
      </div>
      <div style="display: flex; gap: 10px; align-items: center">
        <select v-model="statusFilter" class="cmt-filter" @change="load">
          <option value="">全部状态</option>
          <option value="PUBLISHED">已发布</option>
          <option value="HIDDEN">已隐藏</option>
        </select>
        <button class="btn" style="padding: 8px 12px" @click="load">刷新</button>
      </div>
    </header>

    <div class="console-card console-inner-card">
      <div v-if="loading" style="color: var(--console-muted, var(--muted))">加载中...</div>
      <div v-else-if="error" style="color: #cf1322; padding: 12px 0">{{ error }}</div>
      <div v-else-if="items.length === 0" style="color: var(--console-muted, var(--muted))">暂无话题</div>

      <table v-else class="cmt-table">
        <thead>
          <tr>
            <th style="width: 60px">ID</th>
            <th style="width: 100px">作者</th>
            <th>标题</th>
            <th style="width: 70px">状态</th>
            <th style="width: 70px">评论</th>
            <th style="width: 140px">时间</th>
            <th style="width: 100px">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="t in items" :key="t.id">
            <td>{{ t.id }}</td>
            <td>{{ t.authorName || '匿名' }}</td>
            <td class="cmt-content">
              <a :href="'/forum/topic/' + t.id" target="_blank">{{ t.title }}</a>
            </td>
            <td>
              <span class="cmt-tag" :class="'cmt-tag-' + (t.status || '').toLowerCase()">{{ statusLabel(t.status) }}</span>
            </td>
            <td>{{ t.commentCount }}</td>
            <td>{{ formatTime(t.createdAt) }}</td>
            <td>
              <button
                v-if="t.status === 'PUBLISHED'"
                class="btn"
                style="padding: 4px 10px; font-size: 12px"
                @click="doHide(t.id)"
              >隐藏</button>
              <button
                v-if="t.status === 'HIDDEN'"
                class="btn"
                style="padding: 4px 10px; font-size: 12px"
                @click="doPublish(t.id)"
              >恢复</button>
            </td>
          </tr>
        </tbody>
      </table>

      <div v-if="total > size" class="cmt-pager">
        <button class="btn" :disabled="page <= 0" @click="goPage(page - 1)">上一页</button>
        <span class="cmt-pager-info">{{ page + 1 }} / {{ totalPages }}</span>
        <button class="btn" :disabled="(page + 1) * size >= total" @click="goPage(page + 1)">下一页</button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { fetchAdminForumTopics, hideForumTopic, publishForumTopic } from '../api/forum'

const loading = ref(false)
const items = ref([])
const total = ref(0)
const page = ref(0)
const size = 20
const error = ref('')
const statusFilter = ref('')

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / size)))

function statusLabel(s) {
  if (s === 'PUBLISHED') return '已发布'
  if (s === 'HIDDEN') return '已隐藏'
  return s || ''
}

function formatTime(v) {
  if (!v) return ''
  const t = new Date(v).getTime()
  if (Number.isNaN(t)) return ''
  return new Date(t).toISOString().replace('T', ' ').slice(0, 16)
}

async function load() {
  loading.value = true
  error.value = ''
  page.value = 0
  try {
    const s = statusFilter.value || undefined
    const result = await fetchAdminForumTopics({ page: 0, size, status: s })
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
  loading.value = true
  error.value = ''
  try {
    const s = statusFilter.value || undefined
    const result = await fetchAdminForumTopics({ page: p, size, status: s })
    items.value = result.items || []
    total.value = result.total || 0
  } catch (e) {
    error.value = e?.message || '加载失败'
  } finally {
    loading.value = false
  }
}

async function doHide(id) {
  if (!window.confirm('确定要隐藏这个话题吗？')) return
  try {
    await hideForumTopic(id)
    await load()
  } catch (e) {
    alert(e?.message || '操作失败')
  }
}

async function doPublish(id) {
  try {
    await publishForumTopic(id)
    await load()
  } catch (e) {
    alert(e?.message || '操作失败')
  }
}

onMounted(() => load())
</script>

<style scoped>
.cmt-filter {
  padding: 8px 12px;
  border-radius: 6px;
  border: 1px solid var(--console-border, var(--border));
  background: var(--console-sidebar, #fff);
  color: var(--console-text, var(--text));
  font-size: 13px;
  outline: none;
}

.cmt-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}

.cmt-table th {
  text-align: left;
  padding: 10px 8px;
  border-bottom: 1px solid var(--console-border, #e5e6e8);
  color: var(--console-muted, var(--muted));
  font-weight: 600;
  font-size: 12px;
  white-space: nowrap;
}

.cmt-table td {
  padding: 10px 8px;
  border-bottom: 1px solid var(--console-border, #e5e6e8);
  vertical-align: top;
}

.cmt-content {
  max-width: 300px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cmt-content a {
  color: var(--console-accent, var(--accent));
  text-decoration: none;
}

.cmt-tag {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 6px;
  font-size: 12px;
  font-weight: 500;
}

.cmt-tag-published { background: rgba(52, 199, 89, 0.1); color: #34c759; }
.cmt-tag-hidden { background: rgba(255, 59, 48, 0.1); color: #ff3b30; }

.cmt-pager {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  margin-top: 16px;
}

.cmt-pager-info {
  font-size: 13px;
  color: var(--console-muted, var(--muted));
}
</style>

<template>
  <div class="console-page">
    <header class="console-page-header">
      <div class="console-page-title">
        <h1>评论管理</h1>
      </div>
      <div style="display: flex; gap: 10px; align-items: center">
        <select v-model="statusFilter" class="cmt-filter" @change="load">
          <option value="">全部状态</option>
          <option value="APPROVED">已通过</option>
          <option value="PENDING">待审核</option>
          <option value="DELETED">已删除</option>
        </select>
        <button class="btn" style="padding: 8px 12px" @click="load">刷新</button>
      </div>
    </header>

    <div class="console-card console-inner-card">
      <div v-if="loading" style="color: var(--console-muted, var(--muted))">加载中...</div>
      <div v-else-if="error" style="color: #cf1322; padding: 12px 0">{{ error }}</div>
      <div v-else-if="items.length === 0" style="color: var(--console-muted, var(--muted))">暂无评论</div>

      <table v-else class="cmt-table">
        <thead>
          <tr>
            <th style="width: 60px">ID</th>
            <th style="width: 100px">用户</th>
            <th>内容</th>
            <th style="width: 160px">所属文章</th>
            <th style="width: 70px">状态</th>
            <th style="width: 140px">时间</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="c in items" :key="c.id">
            <td>{{ c.id }}</td>
            <td>{{ c.userName || '匿名' }}</td>
            <td class="cmt-content">{{ c.content }}</td>
            <td class="cmt-article">
              <a v-if="c.articleTitle" :href="'/article/' + c.articleId" target="_blank">{{ c.articleTitle }}</a>
              <span v-else>—</span>
            </td>
            <td>
              <span class="cmt-tag" :class="'cmt-tag-' + (c.status || '').toLowerCase()">{{ statusLabel(c.status) }}</span>
            </td>
            <td>{{ formatTime(c.createdAt) }}</td>
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
import { fetchAllComments } from '../api/comment-admin'

const loading = ref(false)
const items = ref([])
const total = ref(0)
const page = ref(0)
const size = 50
const error = ref('')
const statusFilter = ref('')

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / size)))

function statusLabel(s) {
  if (s === 'APPROVED') return '已通过'
  if (s === 'PENDING') return '待审核'
  if (s === 'DELETED') return '已删除'
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
    const result = await fetchAllComments(0, size, s)
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
    const result = await fetchAllComments(p, size, s)
    items.value = result.items || []
    total.value = result.total || 0
  } catch (e) {
    error.value = e?.message || '加载失败'
  } finally {
    loading.value = false
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

.cmt-article a {
  color: var(--console-accent, var(--accent));
  text-decoration: none;
  max-width: 140px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  display: inline-block;
}

.cmt-tag {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 6px;
  font-size: 12px;
  font-weight: 500;
}

.cmt-tag-approved { background: rgba(52, 199, 89, 0.1); color: #34c759; }
.cmt-tag-pending { background: rgba(255, 149, 0, 0.1); color: #ff9500; }
.cmt-tag-deleted { background: rgba(255, 59, 48, 0.1); color: #ff3b30; }

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

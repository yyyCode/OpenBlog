<template>
  <div class="console-page">
    <header class="console-page-header">
      <div class="console-page-title">
        <h1>问题反馈</h1>
        <div style="color: var(--console-muted, var(--muted)); font-size: 13px; margin-top: 6px">
          展示所有待处理反馈（同一 IP 每天仅可提交一次）。
        </div>
      </div>
      <div>
        <button class="btn" style="padding: 8px 12px" @click="load">刷新</button>
      </div>
    </header>

    <div class="console-card console-inner-card">
      <div v-if="loading" style="color: var(--console-muted, var(--muted))">加载中...</div>
      <div v-else-if="items.length === 0" style="color: var(--console-muted, var(--muted))">暂无待处理反馈</div>

      <div v-else class="fb-list">
        <div v-for="f in items" :key="f.id" class="fb-item">
          <div style="display: flex; align-items: baseline; justify-content: space-between; gap: 12px">
            <div style="font-weight: 950; overflow: hidden; text-overflow: ellipsis; white-space: nowrap">
              {{ f.submitterName || '匿名' }}
            </div>
            <div style="color: var(--console-muted, var(--muted)); font-size: 12px; white-space: nowrap">
              {{ formatDateTime(f.createdAt) }}
            </div>
          </div>
          <div style="margin-top: 10px; white-space: pre-wrap; line-height: 1.7">{{ f.content }}</div>
          <div style="margin-top: 10px; color: var(--console-muted, var(--muted)); font-size: 12px">
            提交日：{{ f.submitDay || '' }} · 状态：{{ f.status || '' }} · ID：{{ f.id }}
          </div>
        </div>
      </div>

      <div v-if="error" class="error" style="margin-top: 10px">{{ error }}</div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { fetchPendingFeedback } from '../api/feedback-admin'

const loading = ref(false)
const items = ref([])
const error = ref('')

function formatDateTime(v) {
  if (!v) return ''
  const t = new Date(v).getTime()
  if (Number.isNaN(t)) return ''
  return new Date(t).toISOString().replace('T', ' ').slice(0, 19)
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const resp = await fetchPendingFeedback(0, 100)
    items.value = resp?.items || []
  } catch (e) {
    const traceId = e?.traceId ? `traceId ${e.traceId}` : ''
    const prefix = e?.code ? `错误码 ${e.code}` : e?.httpStatus ? `HTTP ${e.httpStatus}` : ''
    const head = [prefix, traceId].filter(Boolean).join('，')
    error.value = head ? `${head}：${e?.message || '加载失败'}` : e?.message || '加载失败'
  } finally {
    loading.value = false
  }
}

onMounted(() => load())
</script>

<style scoped>
.fb-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.fb-item {
  padding: 14px 14px;
  border: 1px solid var(--border);
  border-radius: 12px;
  background: var(--surface-soft);
}
</style>


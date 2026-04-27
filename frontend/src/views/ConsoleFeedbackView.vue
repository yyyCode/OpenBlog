<template>
  <div class="console-page">
    <header class="console-page-header">
      <div class="console-page-title">
        <h1>问题反馈</h1>
        <div style="color: var(--console-muted, var(--muted)); font-size: 13px; margin-top: 6px">
          展示全部反馈问题（当前为前端 Mock，后续接入后端接口）。
        </div>
      </div>
      <div>
        <div class="fb-toolbar">
          <input v-model.trim="keyword" class="fb-search" type="search" placeholder="搜索反馈内容/提交者" />
          <button class="btn" style="padding: 8px 12px" @click="load">刷新</button>
        </div>
      </div>
    </header>

    <div class="console-card console-inner-card">
      <div v-if="loading" style="color: var(--console-muted, var(--muted))">加载中...</div>
      <div v-else-if="filteredItems.length === 0" style="color: var(--console-muted, var(--muted))">暂无反馈</div>

      <div v-else>
        <div class="fb-filters">
          <button type="button" class="fb-tab" :class="{ active: statusFilter === 'ALL' }" @click="statusFilter = 'ALL'">
            全部
          </button>
          <button type="button" class="fb-tab" :class="{ active: statusFilter === 'PENDING' }" @click="statusFilter = 'PENDING'">
            待处理
          </button>
          <button type="button" class="fb-tab" :class="{ active: statusFilter === 'DONE' }" @click="statusFilter = 'DONE'">
            已处理
          </button>
          <div class="fb-count" aria-label="数量">{{ filteredItems.length }}</div>
        </div>

        <div class="fb-list">
          <div v-for="f in filteredItems" :key="f.id" class="fb-item">
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
            提交日：{{ f.submitDay || '' }} · 状态：{{ statusLabel(f.status) }} · ID：{{ f.id }}
          </div>
        </div>
      </div>
      </div>

      <div v-if="error" class="error" style="margin-top: 10px">{{ error }}</div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'

const loading = ref(false)
const items = ref([])
const error = ref('')
const keyword = ref('')
const statusFilter = ref('ALL')

function formatDateTime(v) {
  if (!v) return ''
  const t = new Date(v).getTime()
  if (Number.isNaN(t)) return ''
  return new Date(t).toISOString().replace('T', ' ').slice(0, 19)
}

function statusLabel(s) {
  if (s === 'PENDING') return '待处理'
  if (s === 'DONE') return '已处理'
  return s || ''
}

const filteredItems = computed(() => {
  const k = keyword.value.trim().toLowerCase()
  const s = statusFilter.value
  return (items.value || []).filter((it) => {
    if (s !== 'ALL' && it.status !== s) return false
    if (!k) return true
    const a = (it.submitterName || '').toLowerCase()
    const b = (it.content || '').toLowerCase()
    return a.includes(k) || b.includes(k)
  })
})

function buildMock() {
  const now = Date.now()
  const day = (d) => new Date(d).toISOString().slice(0, 10)
  return [
    {
      id: 10001,
      submitterName: '匿名',
      content: '后台附件上传如果没选文件，提示可以更明显一点。',
      createdAt: new Date(now - 1000 * 60 * 18).toISOString(),
      submitDay: day(now - 1000 * 60 * 18),
      status: 'PENDING'
    },
    {
      id: 10002,
      submitterName: '访客',
      content: '文章详情页目录在小屏下希望有折叠入口。',
      createdAt: new Date(now - 1000 * 60 * 60 * 9).toISOString(),
      submitDay: day(now - 1000 * 60 * 60 * 9),
      status: 'DONE'
    },
    {
      id: 10003,
      submitterName: '小明',
      content: '控制台登录页能否支持回车提交。',
      createdAt: new Date(now - 1000 * 60 * 60 * 26).toISOString(),
      submitDay: day(now - 1000 * 60 * 60 * 26),
      status: 'PENDING'
    }
  ]
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    items.value = buildMock()
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
.fb-toolbar {
  display: inline-flex;
  align-items: center;
  gap: 10px;
}

.fb-search {
  width: min(360px, 46vw);
  box-sizing: border-box;
  padding: 9px 12px;
  border-radius: 10px;
  border: 1px solid var(--console-border, var(--border));
  background: var(--console-bg, var(--surface));
  color: var(--console-text, var(--text));
  font-size: 13px;
  outline: none;
}

.fb-search:focus {
  border-color: rgba(0, 0, 0, 0.18);
  box-shadow: 0 0 0 4px rgba(0, 0, 0, 0.06);
}

:global([data-theme='dark']) .fb-search:focus {
  border-color: rgba(255, 255, 255, 0.18);
  box-shadow: 0 0 0 4px rgba(255, 255, 255, 0.06);
}

.fb-filters {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}

.fb-tab {
  border: 1px solid var(--console-border, var(--border));
  background: var(--console-bg, var(--surface));
  color: var(--console-muted, var(--muted));
  border-radius: 10px;
  padding: 8px 12px;
  cursor: pointer;
  font: inherit;
  font-size: 13px;
  font-weight: 650;
}

.fb-tab.active {
  color: var(--console-text, var(--text));
  border-color: rgba(0, 0, 0, 0.12);
  background: rgba(0, 0, 0, 0.04);
}

:global([data-theme='dark']) .fb-tab.active {
  border-color: rgba(255, 255, 255, 0.12);
  background: rgba(255, 255, 255, 0.06);
}

.fb-count {
  margin-left: auto;
  font-size: 12px;
  font-weight: 750;
  color: var(--console-muted, var(--muted));
  padding: 6px 10px;
  border-radius: 999px;
  border: 1px solid var(--console-border, var(--border));
  background: transparent;
}

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

@media (max-width: 560px) {
  .fb-toolbar {
    width: 100%;
    justify-content: flex-end;
  }
  .fb-search {
    width: 52vw;
  }
}
</style>


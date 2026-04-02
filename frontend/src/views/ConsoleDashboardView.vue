<template>
  <div class="console-page">
    <header class="console-page-header">
      <div class="console-page-title">
        <span class="console-page-title-ico" aria-hidden="true">
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="12" cy="12" r="10" />
            <path d="M12 6v6l4 2" />
          </svg>
        </span>
        <h1>仪表盘</h1>
      </div>
      <router-link to="/console/profile" class="console-btn-dark">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <circle cx="12" cy="12" r="3" />
          <path d="M12 1v2M12 21v2M4.22 4.22l1.42 1.42M18.36 18.36l1.42 1.42M1 12h2M21 12h2M4.22 19.78l1.42-1.42M18.36 5.64l1.42-1.42" />
        </svg>
        设置
      </router-link>
    </header>

    <div class="console-stat-grid">
      <div class="console-stat-card">
        <div class="console-stat-ico" aria-hidden="true">
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
            <path d="M14 2v6h6M16 13H8M16 17H8M10 9H8" />
          </svg>
        </div>
        <div class="console-stat-label">文章（已发布）</div>
        <div class="console-stat-value">{{ fmtNum(stats?.articleCount) }}</div>
      </div>
      <div class="console-stat-card">
        <div class="console-stat-ico" aria-hidden="true">
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" />
          </svg>
        </div>
        <div class="console-stat-label">评论</div>
        <div class="console-stat-value">{{ fmtNum(stats?.commentCount) }}</div>
      </div>
      <div class="console-stat-card">
        <div class="console-stat-ico" aria-hidden="true">
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z" />
            <circle cx="12" cy="12" r="3" />
          </svg>
        </div>
        <div class="console-stat-label">浏览量（全站）</div>
        <div class="console-stat-value">{{ fmtNum(stats?.siteVisitCount) }}</div>
      </div>
      <div class="console-stat-card">
        <div class="console-stat-ico" aria-hidden="true">
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
            <path d="M14 2v6h6" />
          </svg>
        </div>
        <div class="console-stat-label">我的文章</div>
        <div class="console-stat-value">{{ fmtNum(myArticleTotal) }}</div>
      </div>
    </div>

    <div class="console-dash-row">
      <section class="console-card console-quick">
        <div class="console-card-head">
          <h2>快捷访问</h2>
        </div>
        <div class="console-quick-grid">
          <router-link to="/console/profile" class="console-quick-tile">
            <span class="console-quick-ico">
              <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" />
                <circle cx="12" cy="7" r="4" />
              </svg>
            </span>
            <span class="console-quick-label">个人中心</span>
            <span class="console-quick-chev" aria-hidden="true">›</span>
          </router-link>
          <a href="/" class="console-quick-tile" target="_blank" rel="noreferrer">
            <span class="console-quick-ico">
              <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <circle cx="12" cy="12" r="10" />
                <path d="M2 12h20M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z" />
              </svg>
            </span>
            <span class="console-quick-label">查看站点</span>
            <span class="console-quick-chev" aria-hidden="true">›</span>
          </a>
          <router-link to="/console/articles?new=1" class="console-quick-tile">
            <span class="console-quick-ico">
              <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M12 20h9M16.5 3.5a2.12 2.12 0 0 1 3 3L7 19l-4 1 1-4L16.5 3.5z" />
              </svg>
            </span>
            <span class="console-quick-label">创建文章</span>
            <span class="console-quick-chev" aria-hidden="true">›</span>
          </router-link>
          <router-link to="/console/changelog" class="console-quick-tile">
            <span class="console-quick-ico">
              <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20" />
                <path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z" />
              </svg>
            </span>
            <span class="console-quick-label">更新日志</span>
            <span class="console-quick-chev" aria-hidden="true">›</span>
          </router-link>
          <router-link to="/console/attachments" class="console-quick-tile">
            <span class="console-quick-ico">
              <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M21.44 11.05l-9.19 9.19a6 6 0 0 1-8.49-8.49l9.19-9.19A4 4 0 0 1 21 12.44l-9.2 9.19" />
              </svg>
            </span>
            <span class="console-quick-label">附件上传</span>
            <span class="console-quick-chev" aria-hidden="true">›</span>
          </router-link>
          <router-link to="/console/system" class="console-quick-tile">
            <span class="console-quick-ico">
              <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <rect x="3" y="3" width="18" height="18" rx="2" />
                <path d="M3 9h18M9 21V9" />
              </svg>
            </span>
            <span class="console-quick-label">站点概览</span>
            <span class="console-quick-chev" aria-hidden="true">›</span>
          </router-link>
        </div>
      </section>

      <section class="console-card console-notify">
        <div class="console-card-head console-card-head-row">
          <h2>通知</h2>
          <span class="console-muted-link">查看全部</span>
        </div>
        <div class="console-empty">
          <div class="console-empty-illus" aria-hidden="true">
            <svg width="72" height="72" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.2">
              <path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z" />
              <path d="M3.27 6.96L12 12.01l8.73-5.05M12 22.08V12" />
            </svg>
          </div>
          <p class="console-empty-text">当前没有未读的消息</p>
          <button type="button" class="console-btn-ghost" @click="refreshNotify">刷新</button>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { fetchSiteStats } from '../api/site'
import { fetchMyArticles } from '../api/admin'

const stats = ref(null)
const myArticleTotal = ref(0)

function fmtNum(v) {
  if (v == null || Number.isNaN(Number(v))) return '—'
  return Number(v).toLocaleString('zh-CN')
}

async function load() {
  try {
    stats.value = await fetchSiteStats()
  } catch {
    stats.value = null
  }
  try {
    const mine = await fetchMyArticles(0, 1)
    const t = mine?.total
    myArticleTotal.value = t != null && !Number.isNaN(Number(t)) ? Number(t) : (mine?.items?.length ?? 0)
  } catch {
    myArticleTotal.value = 0
  }
}

function refreshNotify() {
  load()
}

onMounted(() => {
  load()
})
</script>

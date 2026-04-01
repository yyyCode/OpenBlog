<template>
  <div class="blog-info-wrap">
    <div class="blog-info-heading">博客信息</div>
    <div class="card blog-info-card">
      <div class="blog-info-card-body">
        <div class="blog-info-row">
          <span class="blog-info-left">
            <svg class="blog-info-icon" viewBox="0 0 24 24" aria-hidden="true">
              <path
                fill="currentColor"
                d="M17 3H7a2 2 0 0 0-2 2v16l7-3 7 3V5a2 2 0 0 0-2-2zm0 15l-5-2.18L7 18V5h10v13z"
              />
            </svg>
            <span class="blog-info-label">文章数目</span>
          </span>
          <span class="blog-info-badge">{{ displayArticles }}</span>
        </div>
        <div class="blog-info-row">
          <span class="blog-info-left">
            <svg class="blog-info-icon" viewBox="0 0 24 24" aria-hidden="true">
              <path
                fill="currentColor"
                d="M20 2H4c-1.1 0-2 .9-2 2v18l4-4h14c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2zm0 14H6l-2 2V4h16v12z"
              />
            </svg>
            <span class="blog-info-label">评论数目</span>
          </span>
          <span class="blog-info-badge">{{ displayComments }}</span>
        </div>
        <div class="blog-info-row">
          <span class="blog-info-left">
            <svg class="blog-info-icon" viewBox="0 0 24 24" aria-hidden="true">
              <path
                fill="currentColor"
                d="M12 4.5C7 4.5 2.73 7.61 1 12c1.73 4.39 6 7.5 11 7.5s9.27-3.11 11-7.5c-1.73-4.39-6-7.5-11-7.5zM12 17c-2.76 0-5-2.24-5-5s2.24-5 5-5 5 2.24 5 5-2.24 5-5 5zm0-8c-1.66 0-3 1.34-3 3s1.34 3 3 3 3-1.34 3-3-1.34-3-3-3z"
              />
            </svg>
            <span class="blog-info-label">站点访问</span>
          </span>
          <span class="blog-info-badge">{{ displaySiteVisits }}</span>
        </div>
        <div class="blog-info-row">
          <span class="blog-info-left">
            <svg class="blog-info-icon" viewBox="0 0 24 24" aria-hidden="true">
              <path
                fill="currentColor"
                d="M19 4h-1V2h-2v2H8V2H6v2H5c-1.11 0-1.99.9-1.99 2L3 20c0 1.1.89 2 2 2h14c1.1 0 2-.9 2-2V6c0-1.1-.9-2-2-2zm0 16H5V10h14v10zM9 14H7v-2h2v2zm4 0h-2v-2h2v2zm4 0h-2v-2h2v2zm-8 4H7v-2h2v2zm4 0h-2v-2h2v2zm4 0h-2v-2h2v2z"
              />
            </svg>
            <span class="blog-info-label">运行天数</span>
          </span>
          <span class="blog-info-badge">{{ runningLabel }}</span>
        </div>
        <div class="blog-info-row">
          <span class="blog-info-left">
            <svg class="blog-info-icon blog-info-icon--pulse" viewBox="0 0 24 24" aria-hidden="true">
              <path
                fill="none"
                stroke="currentColor"
                stroke-width="2"
                stroke-linecap="round"
                stroke-linejoin="round"
                d="M2 12h3l2-6 4 12 3-9 2 3h6"
              />
            </svg>
            <span class="blog-info-label">最后活动</span>
          </span>
          <span class="blog-info-badge">{{ lastActivityLabel }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { fetchArticles } from '../api/article'
import { fetchSiteStats } from '../api/site'

/** 站点统计起点：2025-10-01（本地日界线） */
const SITE_START = new Date(2025, 9, 1, 0, 0, 0, 0)

const stats = ref(null)
/** 文章列表页数据（含 total、items），用于展示与回退 */
const articlesPage = ref(null)
/** 来自列表第一条的发布时间（列表按发布时间倒序），在 /site/stats 不可用时作为「最后活动」 */
const lastPublishedFromList = ref(null)
const statsError = ref(false)
const articlesError = ref(false)

onMounted(async () => {
  const [statsResult, articlesResult] = await Promise.allSettled([
    fetchSiteStats(),
    fetchArticles({ page: 0, size: 1 })
  ])
  if (statsResult.status === 'fulfilled') {
    stats.value = statsResult.value
  } else {
    statsError.value = true
  }
  if (articlesResult.status === 'fulfilled') {
    articlesPage.value = articlesResult.value
    const first = articlesResult.value?.items?.[0]
    lastPublishedFromList.value = first?.publishedAt ?? null
  } else {
    articlesError.value = true
  }
})

const displayArticles = computed(() => {
  if (articlesError.value && statsError.value) return '—'
  const page = articlesPage.value
  const items = page?.items ?? []
  const rawTotal = page?.total
  const total = rawTotal == null ? null : Number(rawTotal)
  const statsCount = stats.value?.articleCount

  // total 与 items 不一致（常见于未配置 MP 分页插件时 total=0）：优先用统计接口，否则用列表推断至少篇数
  if (total != null && !Number.isNaN(total)) {
    if (total === 0 && items.length > 0) {
      if (statsCount != null) return String(statsCount)
      return String(items.length)
    }
    return String(total)
  }
  if (!articlesError.value && statsCount != null) return String(statsCount)
  if (articlesError.value && statsError.value) return '—'
  return '…'
})

const displayComments = computed(() => {
  if (statsError.value) return '—'
  const n = stats.value?.commentCount
  return n == null ? '…' : String(n)
})

const displaySiteVisits = computed(() => {
  if (statsError.value) return '—'
  const n = stats.value?.siteVisitCount
  return n == null ? '…' : String(n)
})

const runningLabel = computed(() => formatRunningSince(SITE_START))

const lastActivityLabel = computed(() => {
  const iso = stats.value?.lastActivityAt ?? lastPublishedFromList.value
  if (!iso) {
    if (statsError.value && !lastPublishedFromList.value) return '—'
    return '暂无'
  }
  return formatRelativeZh(iso)
})

function formatRunningSince(start) {
  const now = new Date()
  if (now < start) return '0天'
  const y0 = start.getFullYear()
  const m0 = start.getMonth()
  const d0 = start.getDate()
  let years = 0
  for (;;) {
    const nextYear = new Date(y0 + years + 1, m0, d0)
    if (nextYear > now) break
    years++
  }
  const anchor = new Date(y0 + years, m0, d0)
  const days = Math.floor((now.getTime() - anchor.getTime()) / 86400000)
  if (years === 0) return `${days}天`
  return `${years}年${days}天`
}

function formatRelativeZh(iso) {
  if (!iso) return '暂无'
  const t = new Date(iso).getTime()
  if (Number.isNaN(t)) return '暂无'
  let diffMs = Date.now() - t
  if (diffMs < 0) diffMs = 0
  const sec = Math.floor(diffMs / 1000)
  if (sec < 60) return '刚刚'
  const min = Math.floor(sec / 60)
  if (min < 60) return `${min} 分钟前`
  const hour = Math.floor(min / 60)
  if (hour < 24) return `${hour} 小时前`
  const day = Math.floor(hour / 24)
  if (day < 7) return `${day} 天前`
  const week = Math.floor(day / 7)
  if (week < 5) return `${week} 星期前`
  const month = Math.floor(day / 30)
  if (month < 12) return `${month} 个月前`
  const year = Math.floor(day / 365)
  return `${Math.max(year, 1)} 年前`
}
</script>

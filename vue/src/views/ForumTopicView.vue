<template>
  <div class="page-wrap forum-detail-page">
    <div class="page-main">
      <router-link to="/forum" class="forum-back-link">← 返回论坛</router-link>

      <div v-if="loading" class="forum-loading">加载中...</div>
      <div v-else-if="error" class="forum-error">{{ error }}</div>

      <template v-else-if="topic">
        <article class="forum-topic-detail">
          <h1 class="forum-topic-detail-title">{{ topic.title }}</h1>
          <div class="forum-topic-detail-meta">
            <span class="forum-topic-detail-author">{{ topic.authorName || '匿名' }}</span>
            <span class="forum-topic-card-sep">·</span>
            <span>{{ formatTime(topic.createdAt) }}</span>
            <span class="forum-topic-card-sep">·</span>
            <span>{{ topic.viewCount }} 浏览</span>
          </div>
          <div class="forum-topic-detail-content markdown-body" v-html="renderedContent"></div>
        </article>

        <section class="forum-comments-section">
          <h3 class="forum-comments-title">
            评论 <span class="forum-comments-count">({{ topic.commentCount }})</span>
          </h3>

          <div v-if="me" class="forum-comment-form">
            <textarea
              :value="commentText"
              class="forum-comment-input"
              placeholder="写下你的评论..."
              rows="3"
              maxlength="200"
              @input="onCommentInput"
              @keydown.enter.prevent="onCommentKeydownEnter"
            ></textarea>
            <div class="forum-comment-form-actions">
              <span class="forum-comment-char-count">{{ commentText.length }}/200</span>
              <button class="btn primary" :disabled="!commentText.trim() || submitting" @click="submitComment">
                {{ submitting ? '提交中...' : '发表评论' }}
              </button>
            </div>
            <div v-if="commentError" class="forum-comment-error">{{ commentError }}</div>
          </div>
          <div v-else class="forum-login-hint">
            <router-link to="/login">登录</router-link>后即可评论
          </div>

          <div v-if="commentsLoading" class="forum-loading">加载评论中...</div>
          <div v-else-if="comments.length === 0" class="forum-comments-empty">暂无评论，来说两句吧</div>
          <div v-else class="forum-comment-list">
            <div v-for="c in comments" :key="c.id" class="forum-comment-item">
              <div class="forum-comment-avatar">
                {{ (c.authorName || '?')[0] }}
              </div>
              <div class="forum-comment-body">
                <div class="forum-comment-header">
                  <span class="forum-comment-author">{{ c.authorName || '匿名' }}</span>
                  <span class="forum-comment-time">{{ formatTime(c.createdAt) }}</span>
                </div>
                <p class="forum-comment-content">{{ c.content }}</p>
                <div class="forum-comment-actions">
                  <button
                    v-if="me && (me.id === c.authorId || me.role === 'ADMIN')"
                    class="forum-comment-delete-btn"
                    @click="deleteComment(c.id)"
                  >删除</button>
                </div>
              </div>
            </div>
          </div>

          <div v-if="commentsTotal > commentsSize" class="forum-pager">
            <button class="btn" :disabled="commentsPage <= 0" @click="goCommentsPage(commentsPage - 1)">上一页</button>
            <span class="forum-pager-info">{{ commentsPage + 1 }} / {{ Math.ceil(commentsTotal / commentsSize) }}</span>
            <button class="btn" :disabled="(commentsPage + 1) * commentsSize >= commentsTotal" @click="goCommentsPage(commentsPage + 1)">下一页</button>
          </div>
        </section>
      </template>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import DOMPurify from 'dompurify'
import { marked } from 'marked'
import { fetchForumTopicDetail, fetchForumComments, createForumComment, deleteForumComment } from '../api/forum'
import { fetchMe } from '../api/admin'

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const error = ref('')
const topic = ref(null)

// Comments
const me = ref(null)
const comments = ref([])
const commentsLoading = ref(false)
const commentsPage = ref(0)
const commentsSize = 50
const commentsTotal = ref(0)

const commentText = ref('')
const submitting = ref(false)
const commentError = ref('')

// 评论限制：最多 200 字、最多 10 行（与后端一致）
const MAX_COMMENT_LEN = 200
const MAX_COMMENT_LINES = 10

function onCommentInput(e) {
  commentText.value = sanitizeComment(e.target.value)
}

function sanitizeComment(v) {
  const lines = v.split('\n')
  if (lines.length > MAX_COMMENT_LINES) {
    v = lines.slice(0, MAX_COMMENT_LINES).join('\n')
  }
  if (v.length > MAX_COMMENT_LEN) {
    v = v.slice(0, MAX_COMMENT_LEN)
  }
  return v
}

function onCommentKeydownEnter(e) {
  if (commentText.value.split('\n').length >= MAX_COMMENT_LINES) {
    e.preventDefault()
  }
}

// 论坛主题是任意注册用户可发布的内容，marked 不做 HTML 净化，必须经 DOMPurify 清洗后再 v-html
// （与 ArticleDetailView 的正文渲染一致；否则 <script>/<img onerror> 等原始 HTML 会原样执行 —— 存储型 XSS）
const renderedContent = computed(() => {
  if (!topic.value?.content) return ''
  try {
    return DOMPurify.sanitize(marked.parse(topic.value.content))
  } catch {
    return DOMPurify.sanitize(topic.value.content)
  }
})

function formatTime(v) {
  if (!v) return ''
  const t = new Date(v).getTime()
  if (Number.isNaN(t)) return ''
  const d = new Date(t)
  const now = new Date()
  const diff = now - d
  if (diff < 3600000) return Math.floor(diff / 60000) + '分钟前'
  if (diff < 86400000) return Math.floor(diff / 3600000) + '小时前'
  return d.toISOString().replace('T', ' ').slice(0, 10)
}

async function loadMe() {
  try {
    me.value = await fetchMe()
  } catch {
    me.value = null
  }
}

async function loadTopic() {
  loading.value = true
  error.value = ''
  try {
    const id = route.params.id
    topic.value = await fetchForumTopicDetail(id)
  } catch (e) {
    error.value = e?.message || '加载失败'
  } finally {
    loading.value = false
  }
}

async function loadComments() {
  commentsLoading.value = true
  try {
    const result = await fetchForumComments(route.params.id, { page: commentsPage.value, size: commentsSize })
    comments.value = result.items || []
    commentsTotal.value = result.total || 0
  } catch {
    comments.value = []
  } finally {
    commentsLoading.value = false
  }
}

async function goCommentsPage(p) {
  commentsPage.value = p
  await loadComments()
}

async function submitComment() {
  if (!commentText.value.trim()) return
  submitting.value = true
  commentError.value = ''
  try {
    await createForumComment(route.params.id, { content: commentText.value.trim() })
    commentText.value = ''
    commentsPage.value = 0
    await loadComments()
    // Refresh topic to get updated commentCount
    topic.value = await fetchForumTopicDetail(route.params.id)
  } catch (e) {
    commentError.value = e?.message || '评论失败'
  } finally {
    submitting.value = false
  }
}

async function deleteComment(commentId) {
  if (!window.confirm('确定要删除这条评论吗？')) return
  try {
    await deleteForumComment(commentId)
    await loadComments()
    topic.value = await fetchForumTopicDetail(route.params.id)
  } catch (e) {
    alert(e?.message || '删除失败')
  }
}

onMounted(async () => {
  await Promise.all([loadMe(), loadTopic()])
  if (topic.value) {
    await loadComments()
  }
})
</script>

<style scoped>
.forum-detail-page {
  max-width: 780px;
  margin: 0 auto;
  padding: 32px 16px 60px;
}

.forum-back-link {
  display: inline-block;
  color: var(--muted, #666);
  text-decoration: none;
  font-size: 14px;
  margin-bottom: 20px;
}

.forum-back-link:hover {
  color: var(--accent, #4a90d9);
}

.forum-loading,
.forum-error {
  text-align: center;
  padding: 40px 0;
  color: var(--muted, #888);
}

.forum-error {
  color: #cf1322;
}

.forum-topic-detail {
  margin-bottom: 40px;
}

.forum-topic-detail-title {
  font-size: 28px;
  font-weight: 700;
  margin: 0 0 12px;
  color: var(--text, #1a1a2e);
  line-height: 1.3;
}

.forum-topic-detail-meta {
  font-size: 13px;
  color: var(--muted, #999);
  margin-bottom: 24px;
  display: flex;
  align-items: center;
  gap: 2px;
}

.forum-topic-detail-author {
  font-weight: 500;
  color: var(--text-soft, #555);
}

.forum-topic-card-sep {
  margin: 0 4px;
  color: var(--border, #ccc);
}

.forum-topic-detail-content {
  font-size: 15px;
  line-height: 1.8;
  color: var(--text, #1a1a2e);
}

/* Comments */
.forum-comments-section {
  border-top: 1px solid var(--border, #e5e6e8);
  padding-top: 24px;
}

.forum-comments-title {
  font-size: 18px;
  font-weight: 600;
  margin: 0 0 20px;
  color: var(--text, #1a1a2e);
}

.forum-comments-count {
  font-weight: 400;
  color: var(--muted, #888);
}

.forum-comment-form {
  margin-bottom: 24px;
}

.forum-comment-input {
  width: 100%;
  padding: 12px;
  border: 1px solid var(--border, #d9d9d9);
  border-radius: 8px;
  font-size: 14px;
  font-family: inherit;
  resize: vertical;
  background: var(--bg, #fff);
  color: var(--text, #1a1a2e);
  outline: none;
  box-sizing: border-box;
}

.forum-comment-input:focus {
  border-color: var(--accent, #4a90d9);
}

.forum-comment-form-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 8px;
}

.forum-comment-char-count {
  font-size: 12px;
  color: var(--muted, #aaa);
}

.forum-comment-error {
  color: #cf1322;
  font-size: 13px;
  margin-top: 6px;
}

.forum-login-hint {
  text-align: center;
  padding: 20px;
  color: var(--muted, #888);
  font-size: 14px;
  background: var(--bg-soft, #f8f8f8);
  border-radius: 8px;
  margin-bottom: 24px;
}

.forum-login-hint a {
  color: var(--accent, #4a90d9);
}

.forum-comments-empty {
  text-align: center;
  padding: 30px 0;
  color: var(--muted, #aaa);
  font-size: 14px;
}

.forum-comment-list {
  display: flex;
  flex-direction: column;
  gap: 0;
}

.forum-comment-item {
  display: flex;
  gap: 12px;
  padding: 16px 0;
  border-bottom: 1px solid var(--border-soft, #f0f0f0);
}

.forum-comment-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: var(--accent-soft, #e8f0fe);
  color: var(--accent, #4a90d9);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 15px;
  font-weight: 600;
  flex-shrink: 0;
}

.forum-comment-body {
  flex: 1;
  min-width: 0;
}

.forum-comment-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}

.forum-comment-author {
  font-size: 14px;
  font-weight: 500;
  color: var(--text, #1a1a2e);
}

.forum-comment-time {
  font-size: 12px;
  color: var(--muted, #999);
}

.forum-comment-content {
  font-size: 14px;
  line-height: 1.6;
  color: var(--text, #333);
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
}

.forum-comment-delete-btn {
  background: none;
  border: none;
  font-size: 12px;
  color: var(--muted, #bbb);
  cursor: pointer;
  padding: 0;
}

.forum-comment-delete-btn:hover {
  color: #cf1322;
}

.forum-pager {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  margin-top: 20px;
}

.forum-pager-info {
  font-size: 13px;
  color: var(--muted, #888);
}
</style>

<template>
  <div class="comment-section">
    <div class="comment-section-head">
      <div class="comment-section-title">评论（{{ totalLabel }}）</div>
      <button v-if="hasMore" type="button" class="btn" @click="loadMore" :disabled="loadingMore">
        {{ loadingMore ? '加载中…' : '加载更多' }}
      </button>
    </div>

    <div class="comment-editor card">
      <div class="card-body">
        <div v-if="!isLoggedIn" class="comment-login-hint">
          登录后才能发表评论。
          <button type="button" class="btn primary" @click="goLogin">去登录</button>
        </div>
        <div v-else>
          <textarea v-model="text" class="textarea" rows="4" placeholder="写下你的评论…" />
          <div style="display: flex; justify-content: flex-end; margin-top: 10px">
            <button type="button" class="btn primary" :disabled="!text.trim() || posting" @click="postTopLevel">
              {{ posting ? '发送中…' : '发表评论' }}
            </button>
          </div>
        </div>
      </div>
    </div>

    <div v-if="loading" class="card">
      <div class="card-body" style="padding: 18px; color: var(--muted)">加载评论中…</div>
    </div>
    <div v-else-if="items.length === 0" class="card">
      <div class="card-body" style="padding: 18px; color: var(--muted)">暂无评论，来抢沙发吧。</div>
    </div>

    <div v-else class="comment-list card">
      <div class="card-body">
        <CommentItem
          v-for="c in items"
          :key="c.id"
          :comment="c"
          :me="me"
          @reply="onReplyClick"
          @delete="onDelete"
          @submit-reply="onSubmitReply"
        />

        <div v-for="p in items" :key="'replies-' + p.id">
          <div v-if="p.replies?.length" class="comment-replies">
            <div class="comment-replies-title">回复</div>
            <CommentItem
              v-for="r in p.replies"
              :key="r.id"
              :comment="r"
              :me="me"
              @reply="onReplyClick"
              @delete="onDelete"
              @submit-reply="onSubmitReply"
            />
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getStoredAccessToken, isConsoleSessionValid } from '../auth/session'
import { showMessage } from '../utils/message'
import { fetchMe } from '../api/admin'
import { createComment, deleteComment, listComments, replyComment } from '../api/comment'
import CommentItem from './CommentItem.vue'

const props = defineProps({
  articleId: { type: [String, Number], required: true }
})

const router = useRouter()

const loading = ref(true)
const posting = ref(false)
const loadingMore = ref(false)

const page = ref(0)
const size = ref(10)
const total = ref(0)
const items = ref([])

const text = ref('')

const me = ref(null)
const isLoggedIn = computed(() => {
  const t = getStoredAccessToken()
  return Boolean(t && isConsoleSessionValid())
})

const totalLabel = computed(() => (total.value == null ? '—' : String(total.value)))
const hasMore = computed(() => items.value.length < (total.value || 0))

init()

async function init() {
  await Promise.all([loadMeIfNeeded(), reload()])
}

async function loadMeIfNeeded() {
  if (!isLoggedIn.value) {
    me.value = null
    return
  }
  try {
    me.value = await fetchMe()
  } catch {
    me.value = null
  }
}

async function reload() {
  loading.value = true
  try {
    page.value = 0
    const resp = await listComments(props.articleId, page.value, size.value)
    items.value = resp?.items || []
    total.value = Number(resp?.total ?? 0)
  } finally {
    loading.value = false
  }
}

async function loadMore() {
  if (loadingMore.value || !hasMore.value) return
  loadingMore.value = true
  try {
    const next = page.value + 1
    const resp = await listComments(props.articleId, next, size.value)
    const more = resp?.items || []
    page.value = next
    items.value = items.value.concat(more)
    total.value = Number(resp?.total ?? total.value ?? 0)
  } finally {
    loadingMore.value = false
  }
}

function goLogin() {
  const redirect = window.location.pathname + window.location.search
  router.push({ path: '/login', query: { redirect } })
}

async function postTopLevel() {
  const content = text.value.trim()
  if (!content) return
  posting.value = true
  try {
    await createComment(props.articleId, content)
    text.value = ''
    showMessage('评论已发布')
    await Promise.all([loadMeIfNeeded(), reload()])
  } catch (e) {
    showMessage(e?.message || '评论失败')
  } finally {
    posting.value = false
  }
}

async function onSubmitReply({ parent, content, done }) {
  if (!isLoggedIn.value) {
    showMessage('请先登录')
    return
  }
  try {
    await replyComment(parent.id, content)
    done?.()
    showMessage('回复已发布')
    await Promise.all([loadMeIfNeeded(), reload()])
  } catch (e) {
    showMessage(e?.message || '回复失败')
  }
}

async function onDelete(c) {
  if (!isLoggedIn.value) {
    showMessage('请先登录')
    return
  }
  try {
    await deleteComment(c.id)
    showMessage('已删除')
    await reload()
  } catch (e) {
    showMessage(e?.message || '删除失败')
  }
}

async function onReplyClick(c) {
  // 让 CommentItem 自己展开回复框：当前实现通过 submit-reply 触发即可
  await nextTick()
}
</script>


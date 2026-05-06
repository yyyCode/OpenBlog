<template>
  <div class="comment-item">
    <img class="comment-avatar" :src="avatarSrc" alt="avatar" />
    <div class="comment-main">
      <div class="comment-head">
        <div class="comment-author">{{ authorLabel }}</div>
        <div class="comment-time">{{ timeLabel }}</div>
        <div class="comment-actions">
          <button type="button" class="comment-link" @click="$emit('reply', comment)">回复</button>
          <button
            v-if="canDelete"
            type="button"
            class="comment-link comment-link--danger"
            @click="$emit('delete', comment)"
          >
            删除
          </button>
        </div>
      </div>
      <div class="comment-content">{{ comment.content }}</div>

      <div v-if="replying" class="comment-reply-box">
        <textarea v-model="replyText" class="textarea" rows="3" placeholder="写下你的回复…" />
        <div class="comment-reply-actions">
          <button type="button" class="btn" @click="cancelReply">取消</button>
          <button type="button" class="btn primary" :disabled="!replyText.trim()" @click="submitReply">
            回复
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'

const props = defineProps({
  comment: { type: Object, required: true },
  me: { type: Object, default: null }
})

const emit = defineEmits(['reply', 'delete', 'submit-reply'])

const replying = ref(false)
const replyText = ref('')

watch(
  () => props.comment?.id,
  () => {
    replying.value = false
    replyText.value = ''
  }
)

const authorLabel = computed(() => props.comment?.user?.nickname || '匿名')

const avatarSrc = computed(() => {
  return props.comment?.user?.avatarUrl || 'https://www.gravatar.com/avatar/?d=mp&s=96'
})

const timeLabel = computed(() => formatTime(props.comment?.createdAt))

const canDelete = computed(() => {
  const uid = props.comment?.user?.id
  const meId = props.me?.userId
  const isAdmin = props.me?.role === 'ADMIN'
  return Boolean((meId && uid && meId === uid) || isAdmin)
})

function formatTime(v) {
  if (!v) return ''
  const t = new Date(v).getTime()
  if (Number.isNaN(t)) return ''
  const diff = Date.now() - t
  if (diff < 60_000) return '刚刚'
  const m = Math.floor(diff / 60_000)
  if (m < 60) return `${m} 分钟前`
  const h = Math.floor(m / 60)
  if (h < 24) return `${h} 小时前`
  const d = Math.floor(h / 24)
  if (d < 7) return `${d} 天前`
  return new Date(t).toISOString().slice(0, 10)
}

function cancelReply() {
  replying.value = false
  replyText.value = ''
}

function submitReply() {
  const text = replyText.value.trim()
  if (!text) return
  emit('submit-reply', { parent: props.comment, content: text, done: cancelReply })
}

defineExpose({
  openReply() {
    replying.value = true
  }
})
</script>


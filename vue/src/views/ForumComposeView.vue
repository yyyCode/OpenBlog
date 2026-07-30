<template>
  <div class="page-wrap forum-compose-page">
    <div class="page-main">
      <router-link to="/forum" class="forum-back-link">← 返回论坛</router-link>
      <h1 class="forum-compose-title">发布话题</h1>

      <div v-if="!me" class="forum-login-hint">
        <router-link to="/login">请先登录</router-link>
      </div>

      <form v-else class="forum-compose-form" @submit.prevent="submit">
        <div class="forum-compose-field">
          <label class="forum-compose-label">标题</label>
          <input
            v-model="title"
            class="forum-compose-input"
            type="text"
            placeholder="输入话题标题..."
            maxlength="200"
          />
        </div>

        <div class="forum-compose-field">
          <label class="forum-compose-label">正文 <span class="forum-compose-label-hint">（支持 Markdown）</span></label>
          <textarea
            v-model="content"
            class="forum-compose-textarea"
            placeholder="输入话题内容..."
            rows="12"
            maxlength="20000"
          ></textarea>
        </div>

        <div v-if="error" class="forum-compose-error">{{ error }}</div>

        <div class="forum-compose-actions">
          <button type="submit" class="btn primary" :disabled="!valid || submitting">
            {{ submitting ? '发布中...' : '发布话题' }}
          </button>
        </div>
      </form>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { createForumTopic } from '../api/forum'
import { fetchMe } from '../api/admin'

const router = useRouter()

const me = ref(null)
const title = ref('')
const content = ref('')
const submitting = ref(false)
const error = ref('')

const valid = computed(() => title.value.trim() && content.value.trim())

async function loadMe() {
  try {
    me.value = await fetchMe()
  } catch {
    me.value = null
  }
}

async function submit() {
  if (!valid.value) return
  submitting.value = true
  error.value = ''
  try {
    const result = await createForumTopic({ title: title.value.trim(), content: content.value.trim() })
    router.push('/forum/topic/' + result.id)
  } catch (e) {
    error.value = e?.message || '发布失败'
  } finally {
    submitting.value = false
  }
}

onMounted(() => loadMe())
</script>

<style scoped>
.forum-compose-page {
  max-width: 780px;
  margin: 0 auto;
  padding: 32px 16px 60px;
}

.forum-back-link {
  display: inline-block;
  color: var(--muted, #666);
  text-decoration: none;
  font-size: 14px;
  margin-bottom: 16px;
}

.forum-back-link:hover {
  color: var(--accent, #4a90d9);
}

.forum-compose-title {
  font-size: 24px;
  font-weight: 700;
  margin: 0 0 24px;
  color: var(--text, #1a1a2e);
}

.forum-login-hint {
  text-align: center;
  padding: 40px;
  font-size: 15px;
  color: var(--muted, #888);
}

.forum-login-hint a {
  color: var(--accent, #4a90d9);
}

.forum-compose-form {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.forum-compose-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.forum-compose-label {
  font-size: 14px;
  font-weight: 600;
  color: var(--text, #1a1a2e);
}

.forum-compose-label-hint {
  font-weight: 400;
  color: var(--muted, #999);
  font-size: 12px;
}

.forum-compose-input {
  padding: 10px 14px;
  border: 1px solid var(--border, #d9d9d9);
  border-radius: 8px;
  font-size: 15px;
  font-family: inherit;
  background: var(--bg, #fff);
  color: var(--text, #1a1a2e);
  outline: none;
}

.forum-compose-input:focus {
  border-color: var(--accent, #4a90d9);
}

.forum-compose-textarea {
  padding: 12px 14px;
  border: 1px solid var(--border, #d9d9d9);
  border-radius: 8px;
  font-size: 14px;
  font-family: inherit;
  resize: vertical;
  background: var(--bg, #fff);
  color: var(--text, #1a1a2e);
  outline: none;
  line-height: 1.6;
}

.forum-compose-textarea:focus {
  border-color: var(--accent, #4a90d9);
}

.forum-compose-error {
  color: #cf1322;
  font-size: 13px;
}

.forum-compose-actions {
  display: flex;
  justify-content: flex-end;
}
</style>

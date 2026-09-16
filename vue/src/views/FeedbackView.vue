<template>
  <div class="blog-container">
    <main>
      <div class="card">
        <div class="card-body" style="padding: 22px">
          <h1 style="margin: 0; font-size: 26px; font-weight: 900; letter-spacing: -0.02em">问题反馈</h1>
          <p style="margin: 12px 0 0; color: var(--muted); line-height: 1.8">
            欢迎提交使用中的问题/建议（每个账号每天仅可提交一次）。
          </p>

          <div v-if="!loggedIn" style="margin-top: 18px">
            <p style="margin: 0; color: var(--muted); line-height: 1.8">提交反馈需要先登录账号。</p>
            <div style="display: flex; justify-content: flex-end; margin-top: 14px">
              <router-link
                class="btn primary"
                style="text-decoration: none"
                :to="{ path: '/login', query: { redirect: '/feedback' } }"
              >
                去登录
              </router-link>
            </div>
          </div>

          <div v-else style="margin-top: 18px">
            <div class="field">
              <div class="label">提交人</div>
              <input class="input" type="text" :value="username || '加载中...'" disabled />
            </div>

            <div class="field">
              <div class="label">问题</div>
              <textarea
                v-model.trim="content"
                class="textarea"
                rows="6"
                maxlength="2000"
                placeholder="请尽量描述清楚：发生了什么、期望什么、复现步骤（如有）"
              />
            </div>

            <div style="display: flex; justify-content: flex-end; margin-top: 14px">
              <button class="btn primary" :disabled="submitting" @click="submit">
                {{ submitting ? '提交中...' : '提交反馈' }}
              </button>
            </div>
          </div>

          <div v-if="error" class="error" style="margin-top: 12px">{{ error }}</div>
        </div>
      </div>
    </main>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { createFeedback } from '../api/feedback'
import { fetchMe } from '../api/admin'
import { isConsoleSessionValid } from '../auth/session'
import { showMessage } from '../utils/message'

const content = ref('')
const submitting = ref(false)
const error = ref('')
const username = ref('')
// 本地登录态：与后端一致以有效 JWT 为准（含 READER）；后端还会校验 users.status=ACTIVE。
// 提交时若登录已失效，翻回未登录态并引导重新登录。
const loggedIn = ref(isConsoleSessionValid())

if (loggedIn.value) {
  fetchMe()
    .then((me) => {
      username.value = me?.username || ''
    })
    .catch(() => {
      // 拉取失败不阻塞提交：后端提交人取自 JWT 中的 uid
      username.value = ''
    })
}

async function submit() {
  if (submitting.value) return
  error.value = ''

  if (!content.value || content.value.trim().length < 2) {
    error.value = '请填写问题内容（至少 2 个字符）'
    return
  }

  submitting.value = true
  try {
    await createFeedback({ content: content.value.trim() })
    showMessage('已收到你的反馈，感谢！')
    content.value = ''
  } catch (e) {
    // 未登录：HTTP 401（http.js 已清本地登录态，不带 code）或业务码 4010/4011
    if (e?.httpStatus === 401 || e?.code === 4010 || e?.code === 4011) {
      loggedIn.value = false
    }
    const traceId = e?.traceId ? `traceId ${e.traceId}` : ''
    const prefix = e?.code ? `错误码 ${e.code}` : e?.httpStatus ? `HTTP ${e.httpStatus}` : ''
    const head = [prefix, traceId].filter(Boolean).join('，')
    error.value = head ? `${head}：${e?.message || '提交失败'}` : e?.message || '提交失败'
  } finally {
    submitting.value = false
  }
}
</script>

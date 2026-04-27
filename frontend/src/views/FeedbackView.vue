<template>
  <div class="blog-container">
    <main>
      <div class="card">
        <div class="card-body" style="padding: 22px">
          <h1 style="margin: 0; font-size: 26px; font-weight: 900; letter-spacing: -0.02em">问题反馈</h1>
          <p style="margin: 12px 0 0; color: var(--muted); line-height: 1.8">
            欢迎提交使用中的问题/建议（同一 IP 每天仅可提交一次）。
          </p>

          <div style="margin-top: 18px">
            <div class="field">
              <div class="label">姓名</div>
              <input v-model.trim="name" class="input" type="text" maxlength="50" placeholder="请输入姓名" />
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

            <div v-if="error" class="error" style="margin-top: 12px">{{ error }}</div>
          </div>
        </div>
      </div>
    </main>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { createFeedback } from '../api/feedback'
import { showMessage } from '../utils/message'

const name = ref('')
const content = ref('')
const submitting = ref(false)
const error = ref('')

async function submit() {
  if (submitting.value) return
  error.value = ''

  if (!name.value || name.value.trim().length < 1) {
    error.value = '请填写姓名'
    return
  }

  if (!content.value || content.value.trim().length < 2) {
    error.value = '请填写问题内容（至少 2 个字符）'
    return
  }

  submitting.value = true
  try {
    await createFeedback({
      submitterName: name.value.trim(),
      content: content.value.trim()
    })
    showMessage('已收到你的反馈，感谢！')
    name.value = ''
    content.value = ''
  } catch (e) {
    const traceId = e?.traceId ? `traceId ${e.traceId}` : ''
    const prefix = e?.code ? `错误码 ${e.code}` : e?.httpStatus ? `HTTP ${e.httpStatus}` : ''
    const head = [prefix, traceId].filter(Boolean).join('，')
    error.value = head ? `${head}：${e?.message || '提交失败'}` : e?.message || '提交失败'
  } finally {
    submitting.value = false
  }
}
</script>


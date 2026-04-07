<template>
  <div class="console-page">
    <header class="console-page-header">
      <div class="console-page-title">
        <h1>{{ pageTitle }}</h1>
      </div>
    </header>

    <div class="console-card console-inner-card">
      <div v-if="loadingDetail" style="color: var(--console-muted, var(--muted))">加载中...</div>
      <div v-else class="compose-root">
        <div class="compose-left">
          <div class="grid2" style="gap: 16px">
            <div class="field" style="grid-column: span 2">
              <div class="label">封面图</div>
              <div class="cover-row">
                <div class="cover-preview">
                  <img v-if="form.coverMediaKey" :src="coverUrl(form.coverMediaKey)" alt="cover" />
                  <div v-else class="cover-empty">cover</div>
                </div>
                <div style="flex: 1">
                  <input class="input" type="file" accept="image/*" @change="onPickCover" />
                  <div style="color: var(--console-muted, var(--muted)); font-size: 12px; margin-top: 6px">
                    封面上传后自动设置封面键
                  </div>
                </div>
              </div>
            </div>

            <div class="field">
              <div class="label">标题</div>
              <input v-model="form.title" class="input" type="text" placeholder="请输入标题" />
            </div>
            <div class="field">
              <div class="label">摘要（可选）</div>
              <input v-model="form.summary" class="input" type="text" placeholder="可选：一句话概括内容" />
            </div>
          </div>

          <div class="field" style="margin-top: 12px">
            <div class="label">正文 Markdown</div>
            <textarea v-model="form.contentMarkdown" class="textarea compose-textarea" rows="18" placeholder="开始写作吧..." />
          </div>

          <div class="field" style="margin-top: 12px; max-width: 420px">
            <div class="label">发布时间（可选，留空为当前时间；允许早于当前时间）</div>
            <input v-model="publishAtInput" class="input" type="datetime-local" />
          </div>

          <div style="display: flex; gap: 12px; flex-wrap: wrap; margin-top: 14px">
            <button class="btn primary" @click="saveDraft" :disabled="saving" :style="{ opacity: saving ? 0.7 : 1 }">
              保存草稿/更新
            </button>
            <button
              class="btn"
              :disabled="!selectedId || saving"
              @click="publish"
              :style="{ opacity: selectedId && !saving ? 1 : 0.6 }"
            >
              发布
            </button>
            <button
              class="btn"
              :disabled="!selectedId || saving"
              @click="remove"
              :style="{ opacity: selectedId && !saving ? 1 : 0.6 }"
            >
              删除
            </button>
          </div>

          <div v-if="articleSuccess" class="success" style="margin-top: 10px">{{ articleSuccess }}</div>
          <div v-if="articleError" class="error" style="margin-top: 10px">{{ articleError }}</div>
        </div>

        <div class="compose-right">
          <div class="preview-header">
            <div style="font-weight: 900">预览</div>
            <div class="preview-meta">{{ previewMeta }}</div>
          </div>
          <div class="markdown-body article-markdown compose-preview" v-html="previewHtml" />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import DOMPurify from 'dompurify'
import { marked } from 'marked'

import {
  createDraft,
  deleteMyArticle,
  fetchMyArticleDetail,
  publishArticle,
  publishArticleWithTime,
  updateArticle
} from '../api/admin'
import { uploadMedia } from '../api/media'
import { coverUrl } from '../api/article'

const route = useRoute()
const router = useRouter()

const articleError = ref('')
const articleSuccess = ref('')
const saving = ref(false)

const form = ref({
  title: '',
  summary: '',
  contentMarkdown: '',
  coverMediaKey: null
})

const publishAtInput = ref('')
const loadingDetail = ref(false)
const selectedId = ref(null)

const pageTitle = computed(() => (selectedId.value ? '编辑文章' : '新建文章'))

function resetEditor() {
  selectedId.value = null
  form.value.title = ''
  form.value.summary = ''
  form.value.contentMarkdown = ''
  form.value.coverMediaKey = null
  publishAtInput.value = ''
}

function toLocalDatetimeInput(iso) {
  if (!iso) return ''
  try {
    const d = new Date(iso)
    if (Number.isNaN(d.getTime())) return ''
    const pad = (n) => String(n).padStart(2, '0')
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`
  } catch {
    return ''
  }
}

function toIsoOrEmpty(v) {
  if (!v || !String(v).trim()) return undefined
  const d = new Date(v)
  if (Number.isNaN(d.getTime())) return undefined
  return d.toISOString()
}

async function loadEditor(id) {
  articleError.value = ''
  articleSuccess.value = ''
  loadingDetail.value = true
  try {
    const detail = await fetchMyArticleDetail(id)
    selectedId.value = id
    form.value.title = detail.title || ''
    form.value.summary = detail.summary || ''
    form.value.contentMarkdown = detail.contentMarkdown || ''
    form.value.coverMediaKey = detail.coverMediaKey || null
    publishAtInput.value = toLocalDatetimeInput(detail.publishedAt)
  } finally {
    loadingDetail.value = false
  }
}

const previewHtml = computed(() => {
  const md = form.value.contentMarkdown || ''
  const raw = marked.parse(md)
  return DOMPurify.sanitize(raw)
})

const previewMeta = computed(() => {
  const t = (form.value.title || '').trim()
  const s = selectedId.value ? `#${selectedId.value}` : '未保存'
  return `${t ? `《${t}》` : '未命名'} · ${s}`
})

async function onPickCover(e) {
  const file = e.target.files?.[0]
  if (!file) return
  articleError.value = ''
  const resp = await uploadMedia(file)
  form.value.coverMediaKey = resp.key
}

async function saveDraft() {
  if (saving.value) return
  articleError.value = ''
  articleSuccess.value = ''
  const payload = {
    title: form.value.title,
    summary: form.value.summary,
    contentMarkdown: form.value.contentMarkdown,
    coverMediaKey: form.value.coverMediaKey,
    categoryId: null
  }

  saving.value = true
  try {
    if (selectedId.value) {
      await updateArticle(selectedId.value, payload)
    } else {
      const created = await createDraft(payload)
      selectedId.value = created.id
      // 写作页的 URL 固定下来，方便刷新/分享给自己
      router.replace({ path: '/console/articles/new', query: { id: String(created.id) } })
    }
    articleSuccess.value = '保存成功'
  } catch (e) {
    const apiCode = e?.code
    const httpStatus = e?.httpStatus
    const prefix = apiCode ? `错误码 ${apiCode}` : httpStatus ? `HTTP ${httpStatus}` : ''
    articleError.value = prefix ? `${prefix}：${e?.message || '保存失败'}` : e?.message || '保存失败'
    articleSuccess.value = ''
  } finally {
    saving.value = false
  }
}

async function publish() {
  if (!selectedId.value || saving.value) return
  articleError.value = ''
  articleSuccess.value = ''
  saving.value = true
  try {
    const publishedAt = toIsoOrEmpty(publishAtInput.value)
    if (publishedAt) {
      await publishArticleWithTime(selectedId.value, { publishedAt })
    } else {
      await publishArticle(selectedId.value)
    }
    articleSuccess.value = '发布成功'
  } catch (e) {
    const apiCode = e?.code
    const httpStatus = e?.httpStatus
    const prefix = apiCode ? `错误码 ${apiCode}` : httpStatus ? `HTTP ${httpStatus}` : ''
    articleError.value = prefix ? `${prefix}：${e?.message || '发布失败'}` : e?.message || '发布失败'
    articleSuccess.value = ''
  } finally {
    saving.value = false
  }
}

async function remove() {
  if (!selectedId.value || saving.value) return
  articleError.value = ''
  articleSuccess.value = ''
  saving.value = true
  try {
    await deleteMyArticle(selectedId.value)
    resetEditor()
    router.replace({ path: '/console/articles/new' })
    articleSuccess.value = '删除成功'
  } catch (e) {
    const apiCode = e?.code
    const httpStatus = e?.httpStatus
    const prefix = apiCode ? `错误码 ${apiCode}` : httpStatus ? `HTTP ${httpStatus}` : ''
    articleError.value = prefix ? `${prefix}：${e?.message || '删除失败'}` : e?.message || '删除失败'
    articleSuccess.value = ''
  } finally {
    saving.value = false
  }
}

function normalizeId(raw) {
  if (raw == null) return null
  const s = String(raw).trim()
  if (!s) return null
  return s
}

watch(
  () => route.query.id,
  async (v) => {
    const id = normalizeId(v)
    if (!id) {
      resetEditor()
      return
    }
    await loadEditor(id)
  }
)

onMounted(async () => {
  const id = normalizeId(route.query.id)
  if (id) {
    await loadEditor(id)
  } else {
    resetEditor()
  }
})
</script>

<style scoped>
.compose-root {
  display: grid;
  grid-template-columns: minmax(420px, 1fr) minmax(420px, 1fr);
  gap: 18px;
}

.compose-left {
  min-width: 0;
}

.compose-right {
  min-width: 0;
  border-left: 1px solid rgba(255, 255, 255, 0.08);
  padding-left: 16px;
}

.preview-header {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  margin-bottom: 10px;
}

.preview-meta {
  font-size: 12px;
  color: var(--console-muted, var(--muted));
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 60%;
}

.compose-preview {
  max-height: calc(100vh - 320px);
  overflow: auto;
  padding-right: 8px;
}

.compose-textarea {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', 'Courier New', monospace;
  line-height: 1.6;
}

@media (max-width: 1100px) {
  .compose-root {
    grid-template-columns: 1fr;
  }
  .compose-right {
    border-left: none;
    padding-left: 0;
    border-top: 1px solid rgba(255, 255, 255, 0.08);
    padding-top: 14px;
  }
  .compose-preview {
    max-height: none;
  }
}
</style>


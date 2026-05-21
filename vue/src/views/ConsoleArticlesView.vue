<template>
  <div class="console-page">
    <header class="console-page-header">
      <div class="console-page-title">
        <h1>文章</h1>
      </div>
    </header>

    <div class="console-card console-inner-card">
      <div class="admin-section">
        <div class="admin-articles">
          <div class="article-list">
            <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px">
              <div style="font-weight: 900">我的文章</div>
              <button class="btn" style="padding: 8px 12px" @click="newDraft">新建草稿</button>
            </div>

            <div v-if="loadingArticles" style="color: var(--console-muted, var(--muted))">加载中...</div>
            <div v-else-if="myArticles.length === 0" style="color: var(--console-muted, var(--muted))">暂无文章</div>

            <div
              v-for="a in myArticles"
              :key="a.id"
              class="article-list-item"
              :class="{ active: selectedId === a.id }"
              @click="loadEditor(a.id)"
            >
              <div
                style="
                  font-weight: 950;
                  line-height: 1.3;
                  overflow: hidden;
                  text-overflow: ellipsis;
                  white-space: nowrap;
                "
              >
                {{ a.title }}
              </div>
              <div style="color: var(--console-muted, var(--muted)); font-size: 12px; margin-top: 6px">
                {{ a.status }} · {{ formatDate(a.publishedAt) }}
              </div>
            </div>
          </div>

          <div class="article-editor">
            <div v-if="loadingDetail" style="color: var(--console-muted, var(--muted))">加载中...</div>
            <div v-else>
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
                  <input v-model="form.title" class="input" type="text" />
                </div>
                <div class="field">
                  <div class="label">摘要（可选）</div>
                  <input v-model="form.summary" class="input" type="text" />
                </div>
              </div>

              <div class="field" style="margin-top: 12px">
                <div class="label">正文 Markdown</div>
                <textarea v-model="form.contentMarkdown" class="textarea" rows="10"></textarea>
              </div>

              <div class="field" style="margin-top: 12px; max-width: 420px">
                <div class="label">发布时间（可选，留空为当前时间；允许早于当前时间）</div>
                <input v-model="publishAtInput" class="input" type="datetime-local" />
              </div>

              <div style="display: flex; gap: 12px; flex-wrap: wrap; margin-top: 14px">
                <button class="btn primary" @click="saveDraft">保存草稿/更新</button>
                <button class="btn" :disabled="!selectedId" @click="publish" :style="{ opacity: selectedId ? 1 : 0.6 }">
                  发布
                </button>
                <button class="btn" :disabled="!selectedId" @click="remove" :style="{ opacity: selectedId ? 1 : 0.6 }">
                  删除
                </button>
              </div>

              <div v-if="articleSuccess" class="success" style="margin-top: 10px">{{ articleSuccess }}</div>
              <div v-if="articleError" class="error" style="margin-top: 10px">{{ articleError }}</div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import {
  fetchMyArticles,
  fetchMyArticleDetail,
  createDraft,
  updateArticle,
  publishArticle,
  publishArticleWithTime,
  deleteMyArticle
} from '../api/admin'
import { uploadMedia } from '../api/media'
import { coverUrl } from '../api/article'

const route = useRoute()

const articleError = ref('')
const articleSuccess = ref('')

const form = ref({
  title: '',
  summary: '',
  contentMarkdown: '',
  coverMediaKey: null
})

const publishAtInput = ref('')

const loadingArticles = ref(false)
const loadingDetail = ref(false)
const myArticles = ref([])
const selectedId = ref(null)

function formatDate(v) {
  if (!v) return ''
  try {
    return new Date(v).toISOString().slice(0, 10)
  } catch {
    return ''
  }
}

async function loadArticles() {
  loadingArticles.value = true
  try {
    const resp = await fetchMyArticles(0, 50)
    myArticles.value = resp?.items || []
  } finally {
    loadingArticles.value = false
  }
}

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

async function newDraft() {
  articleError.value = ''
  resetEditor()
}

async function loadEditor(id) {
  articleError.value = ''
  selectedId.value = id
  loadingDetail.value = true
  try {
    const detail = await fetchMyArticleDetail(id)
    form.value.title = detail.title || ''
    form.value.summary = detail.summary || ''
    form.value.contentMarkdown = detail.contentMarkdown || ''
    form.value.coverMediaKey = detail.coverMediaKey || null
    publishAtInput.value = toLocalDatetimeInput(detail.publishedAt)
  } finally {
    loadingDetail.value = false
  }
}

async function onPickCover(e) {
  const file = e.target.files?.[0]
  if (!file) return
  articleError.value = ''
  const resp = await uploadMedia(file)
  form.value.coverMediaKey = resp.key
}

async function saveDraft() {
  articleError.value = ''
  articleSuccess.value = ''
  const payload = {
    title: form.value.title,
    summary: form.value.summary,
    contentMarkdown: form.value.contentMarkdown,
    coverMediaKey: form.value.coverMediaKey,
    categoryId: null
  }
  try {
    if (selectedId.value) {
      await updateArticle(selectedId.value, payload)
    } else {
      const created = await createDraft(payload)
      selectedId.value = created.id
    }
    await loadArticles()
    articleSuccess.value = '保存成功'
  } catch (e) {
    const apiCode = e?.code
    const httpStatus = e?.httpStatus
    const prefix = apiCode ? `错误码 ${apiCode}` : httpStatus ? `HTTP ${httpStatus}` : ''
    articleError.value = prefix ? `${prefix}：${e?.message || '保存失败'}` : e?.message || '保存失败'
    articleSuccess.value = ''
  }
}

async function publish() {
  if (!selectedId.value) return
  articleError.value = ''
  articleSuccess.value = ''
  try {
    const publishedAt = toIsoOrEmpty(publishAtInput.value)
    if (publishedAt) {
      await publishArticleWithTime(selectedId.value, { publishedAt })
    } else {
      await publishArticle(selectedId.value)
    }
    await loadArticles()
    articleSuccess.value = '发布成功'
  } catch (e) {
    const apiCode = e?.code
    const httpStatus = e?.httpStatus
    const prefix = apiCode ? `错误码 ${apiCode}` : httpStatus ? `HTTP ${httpStatus}` : ''
    articleError.value = prefix ? `${prefix}：${e?.message || '发布失败'}` : e?.message || '发布失败'
    articleSuccess.value = ''
  }
}

async function remove() {
  if (!selectedId.value) return
  articleError.value = ''
  articleSuccess.value = ''
  try {
    await deleteMyArticle(selectedId.value)
    resetEditor()
    await loadArticles()
    articleSuccess.value = '删除成功'
  } catch (e) {
    const apiCode = e?.code
    const httpStatus = e?.httpStatus
    const prefix = apiCode ? `错误码 ${apiCode}` : httpStatus ? `HTTP ${httpStatus}` : ''
    articleError.value = prefix ? `${prefix}：${e?.message || '删除失败'}` : e?.message || '删除失败'
    articleSuccess.value = ''
  }
}

watch(
  () => route.query.new,
  (v) => {
    if (v === '1' || v === 'true') {
      newDraft()
    }
  }
)

onMounted(async () => {
  articleError.value = ''
  await loadArticles()
  if (route.query.new === '1' || route.query.new === 'true') {
    newDraft()
  }
})
</script>

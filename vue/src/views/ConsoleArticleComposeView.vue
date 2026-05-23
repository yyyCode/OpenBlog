<template>

  <div class="console-page compose-page">

    <header class="console-page-header">

      <div class="console-page-title">
        <h1>{{ pageTitle }}</h1>
      </div>

      <div class="compose-header-actions">

        <button class="btn" type="button" @click="togglePreview">

          {{ previewOpen ? '关闭预览' : '预览' }}

        </button>

        <button class="btn primary" type="button" @click="saveDraft" :disabled="saving" :style="{ opacity: saving ? 0.7 : 1 }">

          保存草稿/更新

        </button>

        <button

          class="btn"

          type="button"

          :disabled="!selectedId || saving"

          @click="publish"

          :style="{ opacity: selectedId && !saving ? 1 : 0.6 }"

        >

          发布

        </button>

        <button

          class="btn"

          type="button"

          :disabled="!selectedId || saving"

          @click="remove"

          :style="{ opacity: selectedId && !saving ? 1 : 0.6 }"

        >

          删除

        </button>

      </div>

    </header>



    <div class="console-card console-inner-card compose-page-card">

      <div v-if="loadingDetail" style="color: var(--console-muted, var(--muted))">加载中...</div>

      <div v-else class="compose-body">

        <div class="grid2 compose-meta-grid">

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



        <div class="field compose-editor-field">

          <div class="label">正文 Markdown</div>

          <textarea v-model="form.contentMarkdown" class="textarea compose-textarea" placeholder="开始写作吧..." />

        </div>



        <div class="compose-footer-row">

          <div class="field compose-publish-field">

            <div class="label">发布时间（可选，留空为当前时间；允许早于当前时间）</div>

            <input v-model="publishAtInput" class="input" type="datetime-local" />

          </div>

        </div>



        <div v-if="articleSuccess" class="success">{{ articleSuccess }}</div>

        <div v-if="articleError" class="error">{{ articleError }}</div>

      </div>

    </div>



    <Teleport to="body">

      <div v-if="previewOpen" class="compose-preview-overlay" role="dialog" aria-label="文章预览">

        <header class="compose-preview-toolbar">

          <div class="compose-preview-toolbar-title">文章预览</div>

          <button type="button" class="btn" @click="previewOpen = false">关闭预览</button>

        </header>

        <div class="compose-preview-scroll blog-container article-detail-page">

          <div class="article-detail-wrap">

            <article class="article-reader">

              <p class="article-kicker-meta">

                <span>预览</span>

                <span class="article-kicker-dot">·</span>

                <span>{{ previewMeta }}</span>

              </p>



              <h1 class="article-reader-title">{{ previewTitle }}</h1>

              <p v-if="form.summary" class="article-reader-lead">{{ form.summary }}</p>



              <figure v-if="form.coverMediaKey" class="article-reader-cover">

                <img :src="coverUrl(form.coverMediaKey)" alt="" />

              </figure>



              <div class="markdown-body article-markdown article-markdown--body" v-html="previewHtml" />

            </article>

          </div>

        </div>

      </div>

    </Teleport>

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

const previewOpen = ref(false)



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



const previewTitle = computed(() => {

  const t = (form.value.title || '').trim()

  return t || '未命名'

})



function togglePreview() {

  previewOpen.value = !previewOpen.value

}



function resetEditor() {

  selectedId.value = null

  form.value.title = ''

  form.value.summary = ''

  form.value.contentMarkdown = ''

  form.value.coverMediaKey = null

  publishAtInput.value = ''

  previewOpen.value = false

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

.compose-page {

  display: flex;

  flex-direction: column;

  max-width: none;

  min-height: calc(100vh - 48px);

}



.compose-header-actions {

  display: flex;

  align-items: center;

  justify-content: flex-end;

  gap: 10px;

  flex-wrap: wrap;

}



.compose-page-card {

  flex: 1;

  display: flex;

  flex-direction: column;

  min-height: 0;

}



.compose-body {

  flex: 1;

  display: flex;

  flex-direction: column;

  min-height: 0;

  gap: 12px;

}



.compose-meta-grid {

  gap: 16px;

  flex-shrink: 0;

}



.compose-editor-field {

  flex: 1;

  display: flex;

  flex-direction: column;

  min-height: 0;

  margin-top: 0;

}



.compose-editor-field .label {

  flex-shrink: 0;

}



.compose-textarea {

  flex: 1;

  min-height: 320px;

  resize: vertical;

  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', 'Courier New', monospace;

  line-height: 1.6;

}



.compose-footer-row {

  display: flex;

  align-items: flex-end;

  gap: 16px;

  flex-shrink: 0;

}



.compose-publish-field {

  flex: 1;

  max-width: 420px;

  margin: 0;

}



.compose-preview-overlay {

  position: fixed;

  inset: 0;

  z-index: 200;

  display: flex;

  flex-direction: column;

  background: var(--bg, #ffffff);

  color: var(--text, #111827);

}



.compose-preview-toolbar {

  flex-shrink: 0;

  display: flex;

  align-items: center;

  justify-content: space-between;

  gap: 16px;

  padding: 12px 24px;

  border-bottom: 1px solid var(--border, rgba(15, 23, 42, 0.12));

  background: var(--card, #ffffff);

}



.compose-preview-toolbar-title {

  font-size: 14px;

  font-weight: 700;

}



.compose-preview-scroll {

  flex: 1;

  overflow: auto;

  max-width: none;

  margin: 0;

}



@media (max-width: 768px) {

  .compose-page-header {

    flex-direction: column;

    align-items: stretch;

  }



  .compose-header-actions {

    justify-content: flex-start;

  }



  .compose-preview-toolbar {

    padding: 12px 16px;

  }

}

</style>



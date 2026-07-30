<template>

  <div class="console-page compose-page">

    <header class="console-page-header">

      <div class="console-page-title">
        <h1>{{ pageTitle }}</h1>
      </div>

      <div class="compose-header-actions">

        <button class="btn" type="button" @click="triggerInsertImage" :disabled="saving">

          插入图片

        </button>

        <button class="btn" type="button" @click="openMediaBrowser" :disabled="saving">

          媒体库

        </button>

        <button class="btn" type="button" @click="togglePreview">

          {{ previewOpen ? '关闭预览' : '预览' }}

        </button>

        <button class="btn" type="button" @click="triggerImportMd" :disabled="saving">

          导入 MD

        </button>

        <button

          class="btn"

          type="button"

          :disabled="!selectedId || saving"

          @click="exportMd"

          :style="{ opacity: selectedId && !saving ? 1 : 0.6 }"

        >

          导出 MD

        </button>

        <input

          ref="mdFileInput"

          type="file"

          accept=".md,text/markdown"

          style="display: none"

          @change="onMdFilePicked"

        />

        <input

          ref="imageInput"

          type="file"

          accept="image/*"

          style="display: none"

          @change="onPickInsertImage"

        />

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

          <div class="field">

            <div class="label">文章类型</div>

            <select v-model="form.type" class="input">

              <option value="ARTICLE">博客文章</option>

              <option value="JOB_NAV">求职导航</option>

            </select>

          </div>



          <div>

            <div class="label">分类（可选）</div>

            <select v-model="form.categoryId" class="input">

              <option :value="null">未分类</option>

              <option v-for="c in categoryOptions" :key="c.id" :value="c.id">

                {{ categoryLabel(c) }}

              </option>

            </select>

          </div>

        </div>



        <div class="field compose-editor-field">

          <div class="label">正文 Markdown</div>

          <textarea

            ref="editorTextarea"

            v-model="form.contentMarkdown"

            class="textarea compose-textarea"

            placeholder="开始写作吧...支持 Ctrl+V 粘贴图片、拖拽图片到编辑区"

            @paste="onEditorPaste"

            @drop.prevent="onEditorDrop"

            @dragover.prevent

          />

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



    <Teleport to="body">

      <div v-if="mediaBrowserOpen" class="media-browser-overlay" role="dialog" aria-label="媒体库">

        <header class="media-browser-toolbar">

          <div class="media-browser-toolbar-title">媒体库 — 点击图片插入到编辑器</div>

          <select v-model="mediaCategory" class="input" style="width:auto;margin:0 12px" @change="loadMediaPage(0)">

            <option value="">全部分类</option>

            <option v-for="c in mediaCategoryOptions" :key="c.name" :value="c.name">{{ mediaCategoryLabel(c.name) }} ({{ c.count }})</option>

          </select>

          <button type="button" class="btn" @click="mediaBrowserOpen = false">关闭</button>

        </header>

        <div class="media-browser-body">

          <div v-if="mediaLoading" class="media-browser-status">加载中...</div>

          <div v-else-if="mediaError" class="media-browser-status error">{{ mediaError }}</div>

          <template v-else>

            <div v-if="mediaList.length === 0" class="media-browser-status">暂无上传的图片</div>

            <div class="media-browser-grid">

              <div

                v-for="m in mediaList"

                :key="m.key"

                class="media-browser-card"

                @click="selectFromBrowser(m)"

              >

                <img :src="m.thumbUrl" :alt="m.key" loading="lazy" />

                <div class="media-browser-card-info">

                  <span class="media-browser-card-dims">{{ m.width }}x{{ m.height }}</span>

                  <span class="media-browser-card-size">{{ formatSize(m.size) }}</span>

                </div>

              </div>

            </div>

            <div class="media-browser-pager">

              <button class="btn" :disabled="mediaPage <= 0" @click="loadMediaPage(mediaPage - 1)">上一页</button>

              <span class="media-browser-page-num">{{ mediaPage + 1 }}</span>

              <button class="btn" :disabled="mediaList.length < mediaPageSize" @click="loadMediaPage(mediaPage + 1)">下一页</button>

            </div>

          </template>

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

  exportArticleMd,

  fetchMyArticleDetail,

  importArticleMd,

  publishArticle,

  publishArticleWithTime,

  updateArticle

} from '../api/admin'

import { uploadMedia, fetchMediaList, fetchMediaCategories } from '../api/media'

import { coverUrl } from '../api/article'

import { fetchCategories } from '../api/category'



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

  coverMediaKey: null,

  categoryId: null,

  type: 'ARTICLE'

})



const publishAtInput = ref('')

const loadingDetail = ref(false)

const selectedId = ref(null)

const categoryOptions = ref([])

const mdFileInput = ref(null)

const editorTextarea = ref(null)

const imageInput = ref(null)



// media browser state

const mediaBrowserOpen = ref(false)

const mediaList = ref([])

const mediaLoading = ref(false)

const mediaError = ref('')

const mediaPage = ref(0)

const mediaPageSize = 20

const mediaCategory = ref('')

const mediaCategoryOptions = ref([])



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

  form.value.categoryId = null

  publishAtInput.value = ''

  previewOpen.value = false

}



function insertTextAtCursor(text) {

  const el = editorTextarea.value

  if (!el) {

    form.value.contentMarkdown += text

    return

  }

  const start = el.selectionStart

  const end = el.selectionEnd

  const before = form.value.contentMarkdown.slice(0, start)

  const after = form.value.contentMarkdown.slice(end)

  form.value.contentMarkdown = before + text + after

  // restore cursor after inserted text

  requestAnimationFrame(() => {

    const pos = start + text.length

    el.setSelectionRange(pos, pos)

    el.focus()

  })

}



async function uploadAndInsert(file) {

  articleError.value = ''

  const uploadingText = '![上传中...]()'

  insertTextAtCursor(uploadingText)

  const placeholderStart = form.value.contentMarkdown.indexOf(uploadingText)

  try {

    const resp = await uploadMedia(file, { category: 'article-body' })

    const md = `![${file.name || 'image'}](${resp.url})`

    if (placeholderStart >= 0) {

      const before = form.value.contentMarkdown.slice(0, placeholderStart)

      const after = form.value.contentMarkdown.slice(placeholderStart + uploadingText.length)

      form.value.contentMarkdown = before + md + after

    } else {

      form.value.contentMarkdown = form.value.contentMarkdown.replace(uploadingText, md)

    }

  } catch (err) {

    if (placeholderStart >= 0) {

      const before = form.value.contentMarkdown.slice(0, placeholderStart)

      const after = form.value.contentMarkdown.slice(placeholderStart + uploadingText.length)

      form.value.contentMarkdown = before + after

    } else {

      form.value.contentMarkdown = form.value.contentMarkdown.replace(uploadingText, '')

    }

    articleError.value = err?.message || '图片上传失败'

  }

}



function triggerInsertImage() {

  if (saving.value) return

  imageInput.value?.click()

}



async function onPickInsertImage(e) {

  const file = e.target.files?.[0]

  e.target.value = ''

  if (!file) return

  await uploadAndInsert(file)

}



async function onEditorPaste(e) {

  const items = e.clipboardData?.items

  if (!items) return

  for (const item of items) {

    if (item.type.startsWith('image/')) {

      e.preventDefault()

      const file = item.getAsFile()

      if (file) await uploadAndInsert(file)

      return

    }

  }

}



async function onEditorDrop(e) {

  const files = e.dataTransfer?.files

  if (!files || files.length === 0) return

  for (const file of files) {

    if (file.type.startsWith('image/')) {

      await uploadAndInsert(file)

      return

    }

  }

}



async function openMediaBrowser() {

  if (saving.value) return

  mediaBrowserOpen.value = true

  mediaPage.value = 0

  mediaError.value = ''

  mediaCategory.value = ''

  try {

    mediaCategoryOptions.value = await fetchMediaCategories()

  } catch {

    mediaCategoryOptions.value = []

  }

  await loadMediaPage(0)

}



async function loadMediaPage(page) {

  mediaLoading.value = true

  mediaError.value = ''

  try {

    const data = await fetchMediaList(page, mediaPageSize, mediaCategory.value || undefined)

    mediaList.value = data?.records || []

    mediaPage.value = page

  } catch (err) {

    mediaError.value = err?.message || '加载失败'

    mediaList.value = []

  } finally {

    mediaLoading.value = false

  }

}



function selectFromBrowser(m) {

  const md = `![${m.key}](${m.url})`

  insertTextAtCursor(md)

  mediaBrowserOpen.value = false

}



function formatSize(bytes) {

  if (bytes == null) return ''

  if (bytes < 1024) return bytes + ' B'

  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'

  return (bytes / (1024 * 1024)).toFixed(1) + ' MB'

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

    form.value.categoryId = detail.categoryId ?? null

    form.value.type = detail.type || 'ARTICLE'

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

  const resp = await uploadMedia(file, { category: 'article-cover' })

  form.value.coverMediaKey = resp.key

}



function triggerImportMd() {

  if (saving.value) return

  mdFileInput.value?.click()

}



async function onMdFilePicked(e) {

  const file = e.target.files?.[0]

  e.target.value = ''

  if (!file || saving.value) return

  articleError.value = ''

  articleSuccess.value = ''

  saving.value = true

  try {

    if (selectedId.value) {

      await importArticleMd(file, { mode: 'update', articleId: selectedId.value })

      await loadEditor(selectedId.value)

      articleSuccess.value = '已从 Markdown 更新当前文章'

    } else {

      const created = await importArticleMd(file, { mode: 'create' })

      selectedId.value = created.id

      router.replace({ path: '/console/articles/new', query: { id: String(created.id) } })

      await loadEditor(created.id)

      articleSuccess.value = '已从 Markdown 创建草稿'

    }

  } catch (err) {

    const apiCode = err?.code

    const httpStatus = err?.httpStatus

    const prefix = apiCode ? `错误码 ${apiCode}` : httpStatus ? `HTTP ${httpStatus}` : ''

    articleError.value = prefix ? `${prefix}：${err?.message || '导入失败'}` : err?.message || '导入失败'

    articleSuccess.value = ''

  } finally {

    saving.value = false

  }

}



async function exportMd() {

  if (!selectedId.value || saving.value) return

  articleError.value = ''

  articleSuccess.value = ''

  saving.value = true

  try {

    const { blob, filename } = await exportArticleMd(selectedId.value)

    const url = URL.createObjectURL(blob)

    const a = document.createElement('a')

    a.href = url

    a.download = filename || 'article.md'

    a.click()

    URL.revokeObjectURL(url)

    articleSuccess.value = '已导出 Markdown'

  } catch (err) {

    const apiCode = err?.code

    const httpStatus = err?.httpStatus

    const prefix = apiCode ? `错误码 ${apiCode}` : httpStatus ? `HTTP ${httpStatus}` : ''

    articleError.value = prefix ? `${prefix}：${err?.message || '导出失败'}` : err?.message || '导出失败'

    articleSuccess.value = ''

  } finally {

    saving.value = false

  }

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

    categoryId: form.value.categoryId,

    type: form.value.type

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



function categoryLabel(c) {
  const path = c?.path
  if (Array.isArray(path) && path.length > 0) return path.join(' / ')
  return c?.name || ''
}

function mediaCategoryLabel(name) {
  const map = {
    'article-cover': '文章封面',
    'article-body': '正文插图',
    'general': '通用',
    'unknown': '未分类'
  }
  return map[name] || name
}

onMounted(async () => {
  try {
    categoryOptions.value = await fetchCategories()
  } catch {
    categoryOptions.value = []
  }

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

  overflow: hidden;

}



.compose-body {

  flex: 1;

  display: flex;

  flex-direction: column;

  min-height: 0;

  gap: 12px;

  overflow-y: auto;

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



/* media browser */

.media-browser-overlay {

  position: fixed;

  inset: 0;

  z-index: 210;

  display: flex;

  flex-direction: column;

  background: var(--bg, #ffffff);

  color: var(--text, #111827);

}

.media-browser-toolbar {

  flex-shrink: 0;

  display: flex;

  align-items: center;

  justify-content: space-between;

  gap: 16px;

  padding: 12px 24px;

  border-bottom: 1px solid var(--border, rgba(15, 23, 42, 0.12));

  background: var(--card, #ffffff);

}

.media-browser-toolbar-title {

  font-size: 14px;

  font-weight: 700;

}

.media-browser-body {

  flex: 1;

  overflow: auto;

  padding: 24px;

}

.media-browser-status {

  text-align: center;

  padding: 40px 0;

  color: var(--console-muted, var(--muted));

}

.media-browser-grid {

  display: grid;

  grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));

  gap: 12px;

}

.media-browser-card {

  border: 2px solid var(--border, rgba(15, 23, 42, 0.08));

  border-radius: 8px;

  overflow: hidden;

  cursor: pointer;

  transition: border-color 0.15s;

  background: var(--card, #ffffff);

}

.media-browser-card:hover {

  border-color: var(--primary, #2563eb);

}

.media-browser-card img {

  display: block;

  width: 100%;

  height: 120px;

  object-fit: cover;

}

.media-browser-card-info {

  display: flex;

  justify-content: space-between;

  padding: 6px 8px;

  font-size: 11px;

  color: var(--console-muted, var(--muted));

}

.media-browser-pager {

  display: flex;

  align-items: center;

  justify-content: center;

  gap: 12px;

  margin-top: 20px;

}

.media-browser-page-num {

  font-size: 13px;

  color: var(--console-muted, var(--muted));

  min-width: 32px;

  text-align: center;

}

</style>



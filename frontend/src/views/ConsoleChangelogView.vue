<template>
  <div class="console-page">
    <header class="console-page-header">
      <div class="console-page-title">
        <h1>更新日志</h1>
      </div>
    </header>

    <div class="console-card console-inner-card">
      <div class="admin-section">
        <div class="admin-articles">
          <div class="article-list">
            <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px">
              <div style="font-weight: 900">条目列表</div>
              <button class="btn" style="padding: 8px 12px" @click="newChangelog">新建</button>
            </div>

            <div v-if="loadingChangelog" style="color: var(--console-muted, var(--muted))">加载中...</div>
            <div v-else-if="changelogItems.length === 0" style="color: var(--console-muted, var(--muted))">暂无条目</div>

            <div
              v-for="c in changelogItems"
              :key="c.id"
              class="article-list-item"
              :class="{ active: selectedChangelogId === c.id }"
              @click="loadChangelogEditor(c.id)"
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
                {{ c.title }}
              </div>
              <div style="color: var(--console-muted, var(--muted)); font-size: 12px; margin-top: 6px">
                {{ c.versionLabel ? 'v' + c.versionLabel + ' · ' : '' }}{{ formatDate(c.publishedAt) }}
              </div>
            </div>
          </div>

          <div class="article-editor">
            <div v-if="loadingChangelogDetail" style="color: var(--console-muted, var(--muted))">加载中...</div>
            <div v-else>
              <div class="field">
                <div class="label">标题</div>
                <input v-model="changelogForm.title" class="input" type="text" />
              </div>
              <div class="field">
                <div class="label">版本标签（可选，如 1.0.1）</div>
                <input v-model="changelogForm.versionLabel" class="input" type="text" />
              </div>
              <div class="field">
                <div class="label">发布时间（可选，留空为当前时间）</div>
                <input v-model="changelogForm.publishedAt" class="input" type="datetime-local" />
              </div>
              <div class="field">
                <div class="label">正文 Markdown</div>
                <textarea v-model="changelogForm.contentMarkdown" class="textarea" rows="12"></textarea>
              </div>

              <div style="display: flex; gap: 12px; flex-wrap: wrap; margin-top: 14px">
                <button class="btn primary" @click="saveChangelog">保存</button>
                <button
                  class="btn"
                  :disabled="!selectedChangelogId"
                  style="opacity: selectedChangelogId ? 1 : 0.6"
                  @click="removeChangelog"
                >
                  删除
                </button>
              </div>

              <div v-if="changelogSuccess" class="success" style="margin-top: 10px">{{ changelogSuccess }}</div>
              <div v-if="changelogError" class="error" style="margin-top: 10px">{{ changelogError }}</div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import {
  fetchChangelogList,
  fetchChangelogDetail,
  createChangelog,
  updateChangelog,
  deleteChangelog
} from '../api/changelog'

const loadingChangelog = ref(false)
const loadingChangelogDetail = ref(false)
const changelogItems = ref([])
const selectedChangelogId = ref(null)
const changelogForm = ref({
  title: '',
  versionLabel: '',
  contentMarkdown: '',
  publishedAt: ''
})
const changelogError = ref('')
const changelogSuccess = ref('')

function formatDate(v) {
  if (!v) return ''
  try {
    return new Date(v).toISOString().slice(0, 10)
  } catch {
    return ''
  }
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

async function loadChangelogList() {
  loadingChangelog.value = true
  try {
    const resp = await fetchChangelogList(0, 100)
    changelogItems.value = resp?.items || []
  } finally {
    loadingChangelog.value = false
  }
}

function resetChangelogEditor() {
  selectedChangelogId.value = null
  changelogForm.value = {
    title: '',
    versionLabel: '',
    contentMarkdown: '',
    publishedAt: ''
  }
}

function newChangelog() {
  changelogError.value = ''
  changelogSuccess.value = ''
  resetChangelogEditor()
}

async function loadChangelogEditor(id) {
  changelogError.value = ''
  changelogSuccess.value = ''
  selectedChangelogId.value = id
  loadingChangelogDetail.value = true
  try {
    const detail = await fetchChangelogDetail(id)
    changelogForm.value.title = detail.title || ''
    changelogForm.value.versionLabel = detail.versionLabel || ''
    changelogForm.value.contentMarkdown = detail.contentMarkdown || ''
    changelogForm.value.publishedAt = toLocalDatetimeInput(detail.publishedAt)
  } finally {
    loadingChangelogDetail.value = false
  }
}

async function saveChangelog() {
  changelogError.value = ''
  changelogSuccess.value = ''
  const payload = {
    title: changelogForm.value.title,
    versionLabel: changelogForm.value.versionLabel || undefined,
    contentMarkdown: changelogForm.value.contentMarkdown,
    publishedAt: toIsoOrEmpty(changelogForm.value.publishedAt)
  }
  try {
    if (selectedChangelogId.value) {
      await updateChangelog(selectedChangelogId.value, payload)
    } else {
      const created = await createChangelog(payload)
      selectedChangelogId.value = created.id
    }
    await loadChangelogList()
    changelogSuccess.value = '保存成功'
  } catch (e) {
    const apiCode = e?.code
    const httpStatus = e?.httpStatus
    const prefix = apiCode ? `错误码 ${apiCode}` : httpStatus ? `HTTP ${httpStatus}` : ''
    changelogError.value = prefix ? `${prefix}：${e?.message || '保存失败'}` : e?.message || '保存失败'
    changelogSuccess.value = ''
  }
}

async function removeChangelog() {
  if (!selectedChangelogId.value) return
  changelogError.value = ''
  changelogSuccess.value = ''
  try {
    await deleteChangelog(selectedChangelogId.value)
    resetChangelogEditor()
    await loadChangelogList()
    changelogSuccess.value = '删除成功'
  } catch (e) {
    const apiCode = e?.code
    const httpStatus = e?.httpStatus
    const prefix = apiCode ? `错误码 ${apiCode}` : httpStatus ? `HTTP ${httpStatus}` : ''
    changelogError.value = prefix ? `${prefix}：${e?.message || '删除失败'}` : e?.message || '删除失败'
    changelogSuccess.value = ''
  }
}

onMounted(async () => {
  await loadChangelogList()
})
</script>

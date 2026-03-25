<template>
  <div class="blog-container">
    <div class="page-grid" style="grid-template-columns: 1fr 320px">
      <div>
        <div class="card">
          <div class="card-body" style="padding: 18px">
            <div style="display: flex; gap: 12px; margin-bottom: 14px; flex-wrap: wrap">
              <button
                class="tab"
                :class="{ active: tab === 'profile' }"
                @click="tab = 'profile'"
              >
                个人资料
              </button>
              <button
                class="tab"
                :class="{ active: tab === 'articles' }"
                @click="tab = 'articles'"
              >
                文章管理
              </button>
              <button
                class="tab"
                :class="{ active: tab === 'changelog' }"
                @click="onChangelogTab"
              >
                更新日志
              </button>
            </div>

            <!-- Profile tab -->
            <div v-if="tab === 'profile'">
              <div class="admin-section">
                <div style="font-weight: 1000; font-size: 18px; margin-bottom: 12px">
                  编辑资料
                </div>

                <div class="row">
                  <div class="avatar-preview">
                    <img :src="form.avatarUrl || ''" v-if="form.avatarUrl" alt="avatar" />
                    <div v-else class="avatar-placeholder">avatar</div>
                  </div>
                  <div style="flex: 1">
                    <div class="field">
                      <div class="label">头像上传</div>
                      <input class="input" type="file" accept="image/*" @change="onPickAvatar" />
                    </div>
                    <div style="color: var(--muted); font-size: 12px; margin-top: 6px">
                      上传会生成缩略图并更新 `avatarUrl`
                    </div>
                  </div>
                </div>

                <div class="grid2">
                  <div class="field">
                    <div class="label">用户名</div>
                    <input v-model="form.username" class="input" type="text" />
                  </div>
                  <div class="field">
                    <div class="label">昵称</div>
                    <input v-model="form.nickname" class="input" type="text" />
                  </div>
                </div>

                <div class="field">
                  <div class="label">个性签名</div>
                  <textarea v-model="form.bio" class="textarea" rows="4"></textarea>
                </div>

                <div v-if="error" class="error" style="margin-top: 10px">
                  {{ error }}
                </div>
                <div v-else-if="success" class="success" style="margin-top: 10px">
                  {{ success }}
                </div>
                <button class="btn primary" style="margin-top: 14px" @click="saveProfile">
                  保存资料
                </button>
              </div>
            </div>

            <!-- Articles tab -->
            <div v-if="tab === 'articles'">
              <div class="admin-section">
                <div style="font-weight: 1000; font-size: 18px; margin-bottom: 12px">
                  文章管理
                </div>

                <div class="admin-articles">
                  <!-- left list -->
                  <div class="article-list">
                    <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px">
                      <div style="font-weight: 900">我的文章</div>
                      <button class="btn" @click="newDraft" style="padding: 8px 12px">
                        新建草稿
                      </button>
                    </div>

                    <div v-if="loadingArticles" style="color: var(--muted)">加载中...</div>
                    <div v-else-if="myArticles.length === 0" style="color: var(--muted)">暂无文章</div>

                    <div
                      v-for="a in myArticles"
                      :key="a.id"
                      class="article-list-item"
                      :class="{ active: selectedId === a.id }"
                      @click="loadEditor(a.id)"
                    >
                      <div style="font-weight: 950; line-height: 1.3; overflow: hidden; text-overflow: ellipsis; white-space: nowrap">
                        {{ a.title }}
                      </div>
                      <div style="color: var(--muted); font-size: 12px; margin-top: 6px">
                        {{ a.status }} · {{ formatDate(a.publishedAt) }}
                      </div>
                    </div>
                  </div>

                  <!-- editor -->
                  <div class="article-editor">
                    <div v-if="loadingDetail" style="color: var(--muted)">加载中...</div>
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
                              <div style="color: var(--muted); font-size: 12px; margin-top: 6px">
                                封面上传后自动设置 `coverMediaKey`
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

                      <div style="display: flex; gap: 12px; flex-wrap: wrap; margin-top: 14px">
                        <button class="btn primary" @click="saveDraft">
                          保存草稿/更新
                        </button>
                        <button
                          class="btn"
                          :disabled="!selectedId"
                          @click="publish"
                          style="opacity: selectedId ? 1 : 0.6"
                        >
                          发布
                        </button>
                        <button
                          class="btn"
                          :disabled="!selectedId"
                          @click="remove"
                          style="opacity: selectedId ? 1 : 0.6"
                        >
                          删除
                        </button>
                      </div>

                      <div v-if="articleSuccess" class="success" style="margin-top: 10px">
                        {{ articleSuccess }}
                      </div>
                      <div v-if="articleError" class="error" style="margin-top: 10px">
                        {{ articleError }}
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <!-- Changelog tab -->
            <div v-if="tab === 'changelog'">
              <div class="admin-section">
                <div style="font-weight: 1000; font-size: 18px; margin-bottom: 12px">
                  更新日志（站点发版说明）
                </div>

                <div class="admin-articles">
                  <div class="article-list">
                    <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px">
                      <div style="font-weight: 900">条目列表</div>
                      <button class="btn" style="padding: 8px 12px" @click="newChangelog">新建</button>
                    </div>

                    <div v-if="loadingChangelog" style="color: var(--muted)">加载中...</div>
                    <div v-else-if="changelogItems.length === 0" style="color: var(--muted)">暂无条目</div>

                    <div
                      v-for="c in changelogItems"
                      :key="c.id"
                      class="article-list-item"
                      :class="{ active: selectedChangelogId === c.id }"
                      @click="loadChangelogEditor(c.id)"
                    >
                      <div style="font-weight: 950; line-height: 1.3; overflow: hidden; text-overflow: ellipsis; white-space: nowrap">
                        {{ c.title }}
                      </div>
                      <div style="color: var(--muted); font-size: 12px; margin-top: 6px">
                        {{ c.versionLabel ? 'v' + c.versionLabel + ' · ' : '' }}{{ formatDate(c.publishedAt) }}
                      </div>
                    </div>
                  </div>

                  <div class="article-editor">
                    <div v-if="loadingChangelogDetail" style="color: var(--muted)">加载中...</div>
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

                      <div v-if="changelogSuccess" class="success" style="margin-top: 10px">
                        {{ changelogSuccess }}
                      </div>
                      <div v-if="changelogError" class="error" style="margin-top: 10px">
                        {{ changelogError }}
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div>
        <ProfileCard :profile="meProfile" />
        <BlogInfoCard />
        <AdminEntryCard />
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'

import ProfileCard from '../components/ProfileCard.vue'
import BlogInfoCard from '../components/BlogInfoCard.vue'
import AdminEntryCard from '../components/AdminEntryCard.vue'
import { fetchMe, updateMe, fetchMyArticles, fetchMyArticleDetail, createDraft, updateArticle, publishArticle, deleteMyArticle } from '../api/admin'
import { uploadMedia } from '../api/media'
import { coverUrl } from '../api/article'
import {
  fetchChangelogList,
  fetchChangelogDetail,
  createChangelog,
  updateChangelog,
  deleteChangelog
} from '../api/changelog'

const router = useRouter()

const tab = ref('profile')
const error = ref('')
const success = ref('')
const articleError = ref('')
const articleSuccess = ref('')

const meProfile = ref(null)

// profile form
const form = ref({
  username: '',
  nickname: '',
  bio: '',
  avatarUrl: '',
  // article
  title: '',
  summary: '',
  contentMarkdown: '',
  coverMediaKey: null
})

const loadingArticles = ref(false)
const loadingDetail = ref(false)
const myArticles = ref([])
const selectedId = ref(null)

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

function requireAuth() {
  const token = localStorage.getItem('accessToken')
  if (!token) router.push('/admin/login')
}

function formatDate(v) {
  if (!v) return ''
  try {
    return new Date(v).toISOString().slice(0, 10)
  } catch {
    return ''
  }
}

async function loadMe() {
  const me = await fetchMe()
  meProfile.value = me
  form.value.username = me.username || ''
  form.value.nickname = me.nickname || ''
  form.value.bio = me.bio || ''
  form.value.avatarUrl = me.avatarUrl || ''
}

async function saveProfile() {
  error.value = ''
  success.value = ''
  try {
    await updateMe({
      username: form.value.username,
      nickname: form.value.nickname,
      bio: form.value.bio,
      avatarUrl: form.value.avatarUrl
    })
    await loadMe()
    success.value = '保存成功'
  } catch (e) {
    const apiCode = e?.code
    const httpStatus = e?.httpStatus
    const prefix = apiCode ? `错误码 ${apiCode}` : httpStatus ? `HTTP ${httpStatus}` : ''
    error.value = prefix ? `${prefix}：${e?.message || '保存失败'}` : e?.message || '保存失败'
    success.value = ''
  }
}

async function onPickAvatar(e) {
  const file = e.target.files?.[0]
  if (!file) return
  error.value = ''
  const resp = await uploadMedia(file)
  // 上传响应：{url, thumbUrl, key...}
  form.value.avatarUrl = resp.url
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
    await publishArticle(selectedId.value)
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

async function onChangelogTab() {
  tab.value = 'changelog'
  await loadChangelogList()
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
  requireAuth()
  error.value = ''
  await loadMe()
  await loadArticles()
})
</script>


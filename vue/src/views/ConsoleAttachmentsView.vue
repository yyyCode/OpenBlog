<template>
  <div class="console-page">
    <header class="console-page-header">
      <div class="console-page-title">
        <svg class="att-title-icon" width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
          <rect x="3" y="3" width="18" height="18" rx="3"/>
          <circle cx="8.5" cy="8.5" r="1.5"/>
          <path d="m21 15-5-5L5 21"/>
        </svg>
        <h1>附件管理</h1>
      </div>
    </header>

    <div class="console-card console-inner-card attachments-panel">
      <!-- left: folder tree -->
      <aside class="attachments-sidebar">
        <MediaTreeSidebar
          :folders="folders"
          :selected-folder-id="selectedFolderId"
          :loading="foldersLoading"
          :load-error="foldersError"
          :uploading="uploading"
          @select="switchFolder"
          @create-folder="createFolder"
          @rename-folder="renameFolder"
          @delete-folder="deleteFolder"
          @upload="triggerUpload"
        />
        <input
          ref="fileInput"
          type="file"
          accept="image/*"
          style="display: none"
          @change="onUploadFile"
        />
      </aside>

      <!-- right: file grid -->
      <main class="attachments-main">
        <Transition name="toast">
          <div v-if="error" class="att-toast att-toast--error">{{ error }}</div>
        </Transition>
        <Transition name="toast">
          <div v-if="success" class="att-toast att-toast--success">{{ success }}</div>
        </Transition>

        <div v-if="loading" class="att-status">加载中...</div>
        <div v-else-if="loadError" class="att-toast att-toast--error">{{ loadError }}</div>
        <template v-else>
          <div v-if="list.length === 0" class="att-status att-status--empty">暂无图片</div>

          <div class="att-grid">
            <article v-for="m in list" :key="m.key" class="att-card">
              <div class="att-card-media" @click="viewOriginal = m">
                <img :src="m.thumbUrl" :alt="m.key" loading="lazy" />
                <div class="att-card-media-overlay">
                  <span class="att-card-media-hint">点击预览</span>
                </div>
              </div>
              <div class="att-card-body">
                <div class="att-card-dims">{{ m.width }} &times; {{ m.height }}</div>
                <div class="att-card-meta">
                  <span>{{ formatSize(m.size) }}</span>
                  <span class="att-card-meta-sep">&middot;</span>
                  <span>{{ formatDate(m.createdAt) }}</span>
                </div>
                <div class="att-card-actions">
                  <button class="att-btn" @click="copyUrl(m.url)">复制URL</button>
                  <button class="att-btn att-btn--delete" @click="confirmDelete(m)">删除</button>
                </div>
              </div>
            </article>
          </div>

          <nav class="att-pager">
            <button class="att-pager-btn" :disabled="page <= 0" @click="loadPage(page - 1)">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="m15 18-6-6 6-6"/></svg>
              上一页
            </button>
            <span class="att-pager-num">{{ page + 1 }} / {{ totalPages || 1 }}</span>
            <button class="att-pager-btn" :disabled="page + 1 >= totalPages" @click="loadPage(page + 1)">
              下一页
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="m9 18 6-6-6-6"/></svg>
            </button>
          </nav>
        </template>
      </main>
    </div>

    <!-- full-size preview -->
    <Teleport to="body">
      <Transition name="preview">
        <div v-if="viewOriginal" class="att-preview" @click.self="viewOriginal = null">
          <button class="att-preview-close" @click="viewOriginal = null" aria-label="关闭预览">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M18 6 6 18"/><path d="m6 6 12 12"/></svg>
          </button>
          <img :src="viewOriginal.url" :alt="viewOriginal.key" class="att-preview-img" />
          <div class="att-preview-info">
            <span>{{ viewOriginal.key }}</span>
            <span class="att-preview-info-sep">&middot;</span>
            <span>{{ viewOriginal.width }} &times; {{ viewOriginal.height }}</span>
            <span class="att-preview-info-sep">&middot;</span>
            <span>{{ formatSize(viewOriginal.size) }}</span>
          </div>
        </div>
      </Transition>
    </Teleport>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { uploadMedia, fetchMediaList, deleteMedia } from '../api/media'
import { fetchFolderTree, createFolder as apiCreateFolder, updateFolder, deleteFolder as apiDeleteFolder } from '../api/mediaFolder'
import MediaTreeSidebar from '../components/MediaTreeSidebar.vue'

const fileInput = ref(null)
const uploading = ref(false)
const error = ref('')
const success = ref('')

const list = ref([])
const loading = ref(false)
const loadError = ref('')
const page = ref(0)
const pageSize = 20
const totalPages = ref(1)

const folders = ref([])
const foldersLoading = ref(false)
const foldersError = ref('')
const selectedFolderId = ref(null)

const viewOriginal = ref(null)

function switchFolder(folderId) {
  selectedFolderId.value = folderId
  loadPage(0)
}

function triggerUpload() {
  fileInput.value?.click()
}

async function onUploadFile(e) {
  const file = e.target.files?.[0]
  e.target.value = ''
  if (!file) return
  uploading.value = true
  error.value = ''
  success.value = ''
  try {
    await uploadMedia(file, selectedFolderId.value)
    success.value = '上传成功'
    await Promise.all([loadFolders(), loadPage(0)])
  } catch (err) {
    error.value = err?.message || '上传失败'
  } finally {
    uploading.value = false
  }
}

async function loadFolders() {
  foldersLoading.value = true
  foldersError.value = ''
  try {
    folders.value = await fetchFolderTree()
  } catch (err) {
    folders.value = []
    foldersError.value = err?.message || '文件夹加载失败'
  } finally {
    foldersLoading.value = false
  }
}

async function loadPage(p) {
  loading.value = true
  loadError.value = ''
  try {
    const folderId = selectedFolderId.value ?? undefined
    const data = await fetchMediaList(p, pageSize, folderId)
    list.value = data?.records || []
    page.value = data?.current != null ? data.current - 1 : p
    totalPages.value = data?.pages || 1
  } catch (err) {
    loadError.value = err?.message || '加载失败'
    list.value = []
  } finally {
    loading.value = false
  }
}

async function createFolder({ name, parentId }) {
  error.value = ''
  try {
    await apiCreateFolder({ name, parentId, sortOrder: 0 })
    success.value = '文件夹已创建'
    await loadFolders()
  } catch (err) {
    error.value = err?.message || '创建失败'
  }
}

async function renameFolder({ id, name }) {
  error.value = ''
  try {
    await updateFolder(id, { name })
    success.value = '文件夹已重命名'
    await loadFolders()
  } catch (err) {
    error.value = err?.message || '重命名失败'
  }
}

async function deleteFolder(id) {
  error.value = ''
  try {
    await apiDeleteFolder(id)
    success.value = '文件夹已删除'
    if (selectedFolderId.value === id) {
      selectedFolderId.value = null
      loadPage(0)
    }
    await loadFolders()
  } catch (err) {
    error.value = err?.message || '删除失败'
  }
}

async function confirmDelete(m) {
  if (!confirm(`确定删除 ${m.key} 吗？此操作不可恢复。`)) return
  error.value = ''
  success.value = ''
  try {
    await deleteMedia(m.key)
    success.value = '删除成功'
    if (viewOriginal.value?.key === m.key) viewOriginal.value = null
    await Promise.all([
      loadFolders(),
      loadPage(list.value.length <= 1 && page.value > 0 ? page.value - 1 : page.value)
    ])
  } catch (err) {
    error.value = err?.message || '删除失败'
  }
}

async function copyUrl(url) {
  try {
    await navigator.clipboard.writeText(url)
    success.value = 'URL 已复制到剪贴板'
    setTimeout(() => { if (success.value === 'URL 已复制到剪贴板') success.value = '' }, 2000)
  } catch {
    error.value = '复制失败，请手动复制'
  }
}

function formatSize(bytes) {
  if (bytes == null) return ''
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
}

function formatDate(iso) {
  if (!iso) return ''
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return ''
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

onMounted(() => {
  Promise.all([loadFolders(), loadPage(0)])
})
</script>

<style scoped>
/* ---- Header ---- */
.att-title-icon {
  color: var(--console-accent);
  flex-shrink: 0;
}

/* ---- Layout ---- */
.attachments-panel {
  display: flex;
  gap: 0;
  padding: 0;
  overflow: hidden;
}

.attachments-sidebar {
  width: 236px;
  flex-shrink: 0;
  border-right: 1px solid var(--console-border);
  display: flex;
  flex-direction: column;
  background: var(--console-bg);
}

.attachments-main {
  flex: 1;
  padding: 24px 28px;
  min-width: 0;
}

/* ---- Status ---- */
.att-status {
  text-align: center;
  padding: 56px 0;
  color: var(--console-muted);
  font-size: 14px;
  font-weight: 500;
}

.att-status--empty::before {
  content: '';
  display: block;
  width: 48px;
  height: 48px;
  margin: 0 auto 16px;
  border-radius: 12px;
  background: var(--console-bg);
  border: 1px solid var(--console-border);
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='22' height='22' viewBox='0 0 24 24' fill='none' stroke='%239ca3af' stroke-width='1.5'%3E%3Crect x='3' y='3' width='18' height='18' rx='3'/%3E%3Ccircle cx='8.5' cy='8.5' r='1.5'/%3E%3Cpath d='m21 15-5-5L5 21'/%3E%3C/svg%3E");
  background-repeat: no-repeat;
  background-position: center;
}

/* ---- Toast messages ---- */
.att-toast {
  padding: 10px 16px;
  border-radius: 10px;
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 16px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.att-toast--error {
  color: #991b1b;
  background: #fef2f2;
  border: 1px solid #fecaca;
}

.att-toast--success {
  color: #065f46;
  background: #ecfdf5;
  border: 1px solid #a7f3d0;
}

[data-theme='dark'] .att-toast--error {
  color: #fca5a5;
  background: rgba(153, 27, 27, 0.12);
  border-color: rgba(153, 27, 27, 0.25);
}

[data-theme='dark'] .att-toast--success {
  color: #6ee7b7;
  background: rgba(6, 95, 70, 0.12);
  border-color: rgba(6, 95, 70, 0.25);
}

.toast-enter-active,
.toast-leave-active {
  transition: all 0.25s ease;
}
.toast-enter-from,
.toast-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}

/* ---- Grid ---- */
.att-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(228px, 1fr));
  gap: 18px;
}

/* ---- Card ---- */
.att-card {
  border: 1px solid var(--console-border);
  border-radius: 12px;
  overflow: hidden;
  background: #fff;
  transition: transform 0.2s ease, box-shadow 0.2s ease, border-color 0.2s ease;
}

[data-theme='dark'] .att-card {
  background: #181a1f;
}

.att-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 28px rgba(0, 0, 0, 0.07);
  border-color: rgba(13, 148, 136, 0.28);
}

[data-theme='dark'] .att-card:hover {
  box-shadow: 0 8px 28px rgba(0, 0, 0, 0.4);
  border-color: rgba(45, 212, 191, 0.22);
}

/* ---- Card media ---- */
.att-card-media {
  position: relative;
  aspect-ratio: 4 / 3;
  overflow: hidden;
  cursor: pointer;
  background: var(--console-bg);
}

.att-card-media img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.4s cubic-bezier(0.2, 0, 0, 1);
}

.att-card:hover .att-card-media img {
  transform: scale(1.06);
}

.att-card-media-overlay {
  position: absolute;
  inset: 0;
  background: linear-gradient(to top, rgba(0, 0, 0, 0.45) 0%, transparent 55%);
  opacity: 0;
  transition: opacity 0.25s ease;
  display: flex;
  align-items: flex-end;
  justify-content: center;
  padding-bottom: 14px;
  pointer-events: none;
}

.att-card:hover .att-card-media-overlay {
  opacity: 1;
}

.att-card-media-hint {
  color: #fff;
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.02em;
  text-shadow: 0 1px 3px rgba(0, 0, 0, 0.5);
}

/* ---- Card body ---- */
.att-card-body {
  padding: 12px 14px 14px;
}

.att-card-dims {
  font-size: 13px;
  font-weight: 700;
  color: var(--console-text);
  letter-spacing: -0.01em;
}

.att-card-meta {
  display: flex;
  align-items: center;
  gap: 5px;
  margin-top: 3px;
  font-size: 11px;
  color: var(--console-muted);
  font-weight: 500;
  white-space: nowrap;
  overflow: hidden;
}

.att-card-meta-sep {
  opacity: 0.35;
  font-weight: 400;
}

/* ---- Card actions ---- */
.att-card-actions {
  display: flex;
  gap: 8px;
  margin-top: 10px;
}

.att-btn {
  flex: 1;
  padding: 6px 0;
  border-radius: 8px;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  border: 1px solid var(--console-border);
  background: var(--console-bg);
  color: var(--console-text);
  transition: all 0.15s ease;
  font-family: inherit;
  text-align: center;
  letter-spacing: 0.01em;
}

.att-btn:hover {
  border-color: var(--console-accent);
  color: var(--console-accent);
  background: rgba(13, 148, 136, 0.04);
}

.att-btn--delete {
  color: #dc2626;
  border-color: rgba(220, 38, 38, 0.18);
  background: rgba(220, 38, 38, 0.03);
}

.att-btn--delete:hover {
  background: rgba(220, 38, 38, 0.08);
  border-color: rgba(220, 38, 38, 0.45);
  color: #dc2626;
}

/* ---- Pagination ---- */
.att-pager {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16px;
  margin-top: 32px;
}

.att-pager-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 18px;
  border: 1px solid var(--console-border);
  border-radius: 10px;
  background: #fff;
  color: var(--console-text);
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.15s ease;
  font-family: inherit;
}

[data-theme='dark'] .att-pager-btn {
  background: #181a1f;
}

.att-pager-btn:hover:not(:disabled) {
  border-color: var(--console-accent);
  color: var(--console-accent);
}

.att-pager-btn:disabled {
  opacity: 0.3;
  cursor: not-allowed;
}

.att-pager-num {
  font-size: 13px;
  font-weight: 600;
  color: var(--console-muted);
  min-width: 60px;
  text-align: center;
  font-variant-numeric: tabular-nums;
}

/* ---- Preview overlay ---- */
.att-preview {
  position: fixed;
  inset: 0;
  z-index: 220;
  background: rgba(0, 0, 0, 0.92);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 28px;
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
}

.att-preview-close {
  position: absolute;
  top: 22px;
  right: 22px;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 42px;
  height: 42px;
  border-radius: 50%;
  border: 1px solid rgba(255, 255, 255, 0.18);
  background: rgba(255, 255, 255, 0.06);
  color: #fff;
  cursor: pointer;
  transition: all 0.2s ease;
}

.att-preview-close:hover {
  background: rgba(255, 255, 255, 0.14);
  border-color: rgba(255, 255, 255, 0.35);
  transform: scale(1.05);
}

.att-preview-img {
  max-width: 90vw;
  max-height: 76vh;
  object-fit: contain;
  border-radius: 8px;
  box-shadow: 0 24px 70px rgba(0, 0, 0, 0.55);
}

.att-preview-info {
  margin-top: 18px;
  display: flex;
  align-items: center;
  gap: 10px;
  color: rgba(255, 255, 255, 0.55);
  font-size: 13px;
  font-weight: 500;
  letter-spacing: 0.01em;
}

.att-preview-info-sep {
  opacity: 0.3;
}

/* Preview transitions */
.preview-enter-active {
  transition: opacity 0.22s ease;
}
.preview-enter-active .att-preview-img {
  transition: transform 0.28s cubic-bezier(0.2, 0, 0, 1);
}
.preview-leave-active {
  transition: opacity 0.18s ease;
}
.preview-enter-from {
  opacity: 0;
}
.preview-enter-from .att-preview-img {
  transform: scale(0.94);
}
.preview-leave-to {
  opacity: 0;
}

/* ---- Responsive ---- */
@media (max-width: 768px) {
  .attachments-panel {
    flex-direction: column;
  }

  .attachments-sidebar {
    width: 100%;
    border-right: none;
    border-bottom: 1px solid var(--console-border);
    max-height: 42vh;
  }

  .attachments-main {
    padding: 16px;
  }

  .att-grid {
    grid-template-columns: repeat(auto-fill, minmax(152px, 1fr));
    gap: 12px;
  }

  .att-preview-close {
    top: 12px;
    right: 12px;
    width: 36px;
    height: 36px;
  }
}
</style>

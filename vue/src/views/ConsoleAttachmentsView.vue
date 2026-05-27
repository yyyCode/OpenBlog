<template>
  <div class="console-page">
    <header class="console-page-header">
      <div class="console-page-title">
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
        <div v-if="error" class="error" style="margin-bottom: 12px">{{ error }}</div>
        <div v-if="success" class="success" style="margin-bottom: 12px">{{ success }}</div>

        <div v-if="loading" class="attachments-status">加载中...</div>
        <div v-else-if="loadError" class="error" style="margin-bottom: 12px">{{ loadError }}</div>
        <template v-else>
          <div v-if="list.length === 0" class="attachments-status">暂无图片</div>

          <div class="attachments-grid">
            <div v-for="m in list" :key="m.key" class="attachments-card">
              <div class="attachments-card-img" @click="viewOriginal = m">
                <img :src="m.thumbUrl" :alt="m.key" loading="lazy" />
              </div>
              <div class="attachments-card-body">
                <div class="attachments-card-dims">{{ m.width }}x{{ m.height }}</div>
                <div class="attachments-card-size">{{ formatSize(m.size) }}</div>
                <div class="attachments-card-date">{{ formatDate(m.createdAt) }}</div>
                <div class="attachments-card-actions">
                  <button class="btn btn-sm" @click="copyUrl(m.url)">复制URL</button>
                  <button class="btn btn-sm btn-danger" @click="confirmDelete(m)">删除</button>
                </div>
              </div>
            </div>
          </div>

          <div class="attachments-pager">
            <button class="btn" :disabled="page <= 0" @click="loadPage(page - 1)">上一页</button>
            <span class="attachments-page-num">{{ page + 1 }} / {{ totalPages || 1 }}</span>
            <button class="btn" :disabled="page + 1 >= totalPages" @click="loadPage(page + 1)">下一页</button>
          </div>
        </template>
      </main>
    </div>

    <!-- full-size preview -->
    <Teleport to="body">
      <div v-if="viewOriginal" class="attachments-preview-overlay" @click="viewOriginal = null">
        <button class="attachments-preview-close btn" @click="viewOriginal = null">关闭</button>
        <img :src="viewOriginal.url" :alt="viewOriginal.key" />
        <div class="attachments-preview-info">
          {{ viewOriginal.width }}x{{ viewOriginal.height }} · {{ formatSize(viewOriginal.size) }}
        </div>
      </div>
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
  try {
    folders.value = await fetchFolderTree()
  } catch {
    folders.value = []
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
.attachments-panel {
  display: flex;
  gap: 0;
  padding: 0;
  overflow: hidden;
}

/* sidebar */
.attachments-sidebar {
  width: 220px;
  flex-shrink: 0;
  border-right: 1px solid var(--border, rgba(15, 23, 42, 0.08));
  display: flex;
  flex-direction: column;
}

/* main */
.attachments-main {
  flex: 1;
  padding: 20px 24px;
  min-width: 0;
}

.attachments-status {
  text-align: center;
  padding: 40px 0;
  color: var(--console-muted, var(--muted));
}

.attachments-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 16px;
}

.attachments-card {
  border: 1px solid var(--border, rgba(15, 23, 42, 0.08));
  border-radius: 8px;
  overflow: hidden;
  background: var(--card, #ffffff);
}

.attachments-card-img {
  cursor: pointer;
  overflow: hidden;
  background: #f0f0f0;
}

.attachments-card-img img {
  display: block;
  width: 100%;
  height: 140px;
  object-fit: cover;
  transition: transform 0.2s;
}

.attachments-card-img:hover img {
  transform: scale(1.05);
}

.attachments-card-body {
  padding: 10px 12px;
}

.attachments-card-dims {
  font-size: 12px;
  color: var(--text, #111827);
  font-weight: 600;
}

.attachments-card-size,
.attachments-card-date {
  font-size: 11px;
  color: var(--console-muted, var(--muted));
  margin-top: 2px;
}

.attachments-card-actions {
  display: flex;
  gap: 6px;
  margin-top: 8px;
}

.btn-sm {
  padding: 3px 10px;
  font-size: 12px;
}

.btn-danger {
  color: #dc2626;
  border-color: #dc2626;
}

.attachments-pager {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  margin-top: 24px;
}

.attachments-page-num {
  font-size: 13px;
  color: var(--console-muted, var(--muted));
  min-width: 64px;
  text-align: center;
}

/* preview overlay */
.attachments-preview-overlay {
  position: fixed;
  inset: 0;
  z-index: 220;
  background: rgba(0, 0, 0, 0.85);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 24px;
}

.attachments-preview-overlay img {
  max-width: 90vw;
  max-height: 80vh;
  object-fit: contain;
}

.attachments-preview-close {
  position: absolute;
  top: 16px;
  right: 16px;
  color: #fff;
  border-color: rgba(255, 255, 255, 0.4);
}

.attachments-preview-close:hover {
  border-color: #fff;
}

.attachments-preview-info {
  margin-top: 12px;
  color: rgba(255, 255, 255, 0.7);
  font-size: 13px;
}

@media (max-width: 768px) {
  .attachments-panel {
    flex-direction: column;
  }

  .attachments-sidebar {
    width: 100%;
    border-right: none;
    border-bottom: 1px solid var(--border, rgba(15, 23, 42, 0.08));
  }

  .attachments-main {
    padding: 12px 16px;
  }

  .attachments-grid {
    grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
    gap: 10px;
  }

  .attachments-card-img img {
    height: 100px;
  }
}
</style>

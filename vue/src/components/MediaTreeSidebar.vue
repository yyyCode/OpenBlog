<template>
  <div class="media-tree-sidebar">
    <div class="media-tree-head">
      <svg class="media-tree-head-icon" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
        <path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"/>
      </svg>
      <span class="media-tree-title">文件夹</span>
      <button class="media-tree-add-btn" type="button" title="新建文件夹" @click="startCreate">+</button>
    </div>

    <div class="media-tree-body">
      <div v-if="loading" class="media-tree-hint">加载中...</div>
      <div v-else-if="loadError" class="media-tree-hint error">{{ loadError }}</div>

      <template v-else>
        <button
          class="media-tree-root-btn"
          :class="{ active: selectedFolderId == null }"
          type="button"
          @click="$emit('select', null)"
        >
          <span class="media-tree-group-label">全部文件</span>
          <span class="media-tree-group-count">{{ totalFileCount }}</span>
        </button>

        <div v-if="tree.length === 0" class="media-tree-hint">暂无文件夹，点击右上角 + 创建</div>

        <MediaTreeBranch
          v-for="node in tree"
          :key="node.key"
          :folder="node"
          :selected-folder-id="selectedFolderId"
          :expanded-map="expanded"
          @toggle="toggleGroup"
          @select="onSelect"
          @contextmenu="onContextMenu"
        />
      </template>
    </div>

    <!-- context menu -->
    <Teleport to="body">
      <div
        v-if="contextMenu.show"
        class="media-tree-context-overlay"
        @click="closeContextMenu"
        @contextmenu.prevent="closeContextMenu"
      >
        <div
          class="media-tree-context-menu"
          :style="{ left: contextMenu.x + 'px', top: contextMenu.y + 'px' }"
        >
          <button type="button" @click="startRename">重命名</button>
          <button type="button" @click="startCreateChild">新建子文件夹</button>
          <button type="button" class="danger" @click="confirmDelete">删除</button>
        </div>
      </div>
    </Teleport>

    <!-- inline rename / create input -->
    <div v-if="editing" class="media-tree-edit-row">
      <input
        ref="editInput"
        v-model="editName"
        class="media-tree-edit-input"
        maxlength="80"
        @keyup.enter="submitEdit"
        @keyup.escape="cancelEdit"
        @blur="onEditBlur"
      />
    </div>

    <div class="media-tree-footer">
      <button class="media-tree-upload-btn" :disabled="uploading" @click="$emit('upload')">
        <svg v-if="!uploading" class="media-tree-upload-icon" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
          <polyline points="17 8 12 3 7 8"/>
          <line x1="12" y1="3" x2="12" y2="15"/>
        </svg>
        <span v-if="uploading" class="media-tree-upload-spinner"></span>
        {{ uploading ? '上传中...' : '上传到当前文件夹' }}
      </button>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, ref, watch } from 'vue'

import MediaTreeBranch from './MediaTreeBranch.vue'
import { buildFolderTree, collectExpandedKeysForFolder } from '../utils/mediaTree'

const props = defineProps({
  folders: { type: Array, default: () => [] },
  selectedFolderId: { type: [String, Number], default: null },
  loading: { type: Boolean, default: false },
  loadError: { type: String, default: '' },
  uploading: { type: Boolean, default: false }
})

const emit = defineEmits(['select', 'create-folder', 'rename-folder', 'delete-folder', 'upload'])

const expanded = ref({})

const tree = computed(() => buildFolderTree(props.folders))

const totalFileCount = computed(() =>
  tree.value.reduce((sum, n) => sum + (n.fileCount || 0), 0)
)

const contextMenu = ref({ show: false, x: 0, y: 0, folder: null })

const editing = ref(false)
const editName = ref('')
const editTarget = ref(null) // null=create root, {id}=rename, {parentId}=create child
const editInput = ref(null)

function toggleGroup(key) {
  expanded.value = {
    ...expanded.value,
    [key]: expanded.value[key] === false
  }
}

function onSelect(folderId) {
  emit('select', folderId)
}

function onContextMenu({ event, folder }) {
  contextMenu.value = {
    show: true,
    x: event.clientX,
    y: event.clientY,
    folder
  }
}

function closeContextMenu() {
  contextMenu.value = { show: false, x: 0, y: 0, folder: null }
}

function startCreate() {
  editing.value = true
  editName.value = ''
  editTarget.value = null
  closeContextMenu()
  nextTick(() => editInput.value?.focus())
}

function startCreateChild() {
  const f = contextMenu.value.folder
  editing.value = true
  editName.value = ''
  editTarget.value = { parentId: f.folderId }
  closeContextMenu()
  nextTick(() => editInput.value?.focus())
}

function startRename() {
  const f = contextMenu.value.folder
  editing.value = true
  editName.value = f.label
  editTarget.value = { id: f.folderId }
  closeContextMenu()
  nextTick(() => editInput.value?.focus())
}

function cancelEdit() {
  editing.value = false
  editName.value = ''
  editTarget.value = null
}

function onEditBlur() {
  if (!editing.value) return
  const name = editName.value.trim()
  if (name) submitEdit()
  else cancelEdit()
}

async function submitEdit() {
  const name = editName.value.trim()
  if (!name) {
    cancelEdit()
    return
  }
  if (editTarget.value === null) {
    emit('create-folder', { name, parentId: null })
  } else if (editTarget.value.parentId != null) {
    emit('create-folder', { name, parentId: editTarget.value.parentId })
  } else if (editTarget.value.id != null) {
    emit('rename-folder', { id: editTarget.value.id, name })
  }
  cancelEdit()
}

function confirmDelete() {
  const f = contextMenu.value.folder
  closeContextMenu()
  if (confirm(`确定删除文件夹「${f.label}」吗？`)) {
    emit('delete-folder', f.folderId)
  }
}

function syncExpanded() {
  const next = { ...expanded.value }
  for (const node of tree.value) {
    initNode(node, next)
  }
  for (const key of collectExpandedKeysForFolder(tree.value, props.selectedFolderId)) {
    next[key] = true
  }
  expanded.value = next
}

function initNode(node, map) {
  if (map[node.key] === undefined) map[node.key] = true
  for (const child of node.children || []) initNode(child, map)
}

watch([() => props.folders, () => props.selectedFolderId], syncExpanded, { immediate: true })
</script>

<style scoped>
/* ---- Sidebar ---- */
.media-tree-sidebar {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
}

/* ---- Header ---- */
.media-tree-head {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 18px 16px 10px;
}

.media-tree-head-icon {
  color: var(--console-muted);
  flex-shrink: 0;
  opacity: 0.65;
}

.media-tree-title {
  font-size: 11px;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.07em;
  color: var(--console-muted);
  flex: 1;
}

.media-tree-add-btn {
  border: 1px solid var(--console-border);
  background: transparent;
  color: var(--console-muted);
  cursor: pointer;
  width: 26px;
  height: 26px;
  border-radius: 7px;
  font-size: 15px;
  line-height: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0;
  transition: all 0.15s ease;
  flex-shrink: 0;
}

.media-tree-add-btn:hover {
  border-color: var(--console-accent);
  color: var(--console-accent);
  background: rgba(13, 148, 136, 0.06);
}

/* ---- Body ---- */
.media-tree-body {
  flex: 1 1 auto;
  min-height: 0;
  overflow: auto;
  padding: 4px 10px 14px;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.media-tree-hint {
  padding: 20px 10px;
  font-size: 12px;
  color: var(--console-muted);
  text-align: center;
}

/* ---- Root button ---- */
.media-tree-root-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  border: none;
  background: transparent;
  color: var(--console-text);
  cursor: pointer;
  padding: 9px 12px;
  border-radius: 8px;
  font-size: 13px;
  font-weight: 600;
  text-align: left;
  font-family: inherit;
  transition: background 0.12s ease, color 0.12s ease;
}

.media-tree-root-btn:hover {
  background: rgba(13, 148, 136, 0.06);
}

.media-tree-root-btn.active {
  background: var(--console-accent);
  color: #fff;
}

.media-tree-root-btn.active .media-tree-group-count {
  color: rgba(255, 255, 255, 0.7);
}

/* ---- Footer ---- */
.media-tree-footer {
  padding: 14px 12px;
  border-top: 1px solid var(--console-border);
}

.media-tree-upload-btn {
  width: 100%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 10px 14px;
  border: 1px solid var(--console-border);
  border-radius: 10px;
  background: var(--console-accent);
  color: #fff;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  font-family: inherit;
  transition: all 0.15s ease;
}

.media-tree-upload-btn:hover:not(:disabled) {
  filter: brightness(1.08);
  box-shadow: 0 2px 10px rgba(13, 148, 136, 0.25);
}

.media-tree-upload-btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.media-tree-upload-icon {
  flex-shrink: 0;
}

.media-tree-upload-spinner {
  width: 14px;
  height: 14px;
  border: 2px solid rgba(255, 255, 255, 0.3);
  border-top-color: #fff;
  border-radius: 50%;
  animation: spin 0.6s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

/* ---- Context menu ---- */
.media-tree-context-overlay {
  position: fixed;
  inset: 0;
  z-index: 250;
}

.media-tree-context-menu {
  position: absolute;
  background: #fff;
  border: 1px solid var(--console-border);
  border-radius: 10px;
  box-shadow: 0 8px 30px rgba(0, 0, 0, 0.12);
  padding: 6px;
  min-width: 148px;
  display: flex;
  flex-direction: column;
  gap: 2px;
  backdrop-filter: blur(14px);
  -webkit-backdrop-filter: blur(14px);
}

[data-theme='dark'] .media-tree-context-menu {
  background: #1e2028;
  box-shadow: 0 8px 30px rgba(0, 0, 0, 0.45);
}

.media-tree-context-menu button {
  display: block;
  width: 100%;
  border: none;
  background: transparent;
  color: var(--console-text);
  cursor: pointer;
  padding: 8px 12px;
  border-radius: 6px;
  font-size: 13px;
  text-align: left;
  font-family: inherit;
  font-weight: 500;
  transition: background 0.1s ease;
}

.media-tree-context-menu button:hover {
  background: rgba(13, 148, 136, 0.08);
}

.media-tree-context-menu button.danger {
  color: #dc2626;
}

.media-tree-context-menu button.danger:hover {
  background: rgba(220, 38, 38, 0.07);
}

/* ---- Edit row ---- */
.media-tree-edit-row {
  padding: 8px 12px 12px;
}

.media-tree-edit-input {
  width: 100%;
  box-sizing: border-box;
  padding: 7px 10px;
  font-size: 13px;
  border: 2px solid var(--console-accent);
  border-radius: 8px;
  outline: none;
  background: #fff;
  color: var(--console-text);
  font-family: inherit;
  box-shadow: 0 0 0 4px rgba(13, 148, 136, 0.1);
}

[data-theme='dark'] .media-tree-edit-input {
  background: #181a1f;
}
</style>

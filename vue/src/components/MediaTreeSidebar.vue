<template>
  <div class="media-tree-sidebar">
    <div class="media-tree-head">
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
        @blur="cancelEdit"
      />
    </div>

    <div class="media-tree-footer">
      <button class="media-tree-upload-btn" :disabled="uploading" @click="$emit('upload')">
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

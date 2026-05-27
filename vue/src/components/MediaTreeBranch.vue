<template>
  <div class="media-tree-group">
    <button
      class="media-tree-group-head"
      :class="{ active: folder.folderId === selectedFolderId }"
      type="button"
      :aria-expanded="isExpanded ? 'true' : 'false'"
      @click="$emit('select', folder.folderId)"
      @contextmenu.prevent="$emit('contextmenu', { event: $event, folder })"
    >
      <span
        v-if="folder.children?.length > 0"
        class="media-tree-chev"
        aria-hidden="true"
        @click.stop="$emit('toggle', folder.key)"
      >{{ isExpanded ? '▾' : '▸' }}</span>
      <svg v-else class="media-tree-chev media-tree-chev--empty" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
        <path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"/>
      </svg>
      <span class="media-tree-group-label">{{ folder.label }}</span>
      <span class="media-tree-group-count">{{ folder.fileCount }}</span>
    </button>

    <div v-show="isExpanded" class="media-tree-group-body">
      <MediaTreeBranch
        v-for="child in folder.children"
        :key="child.key"
        :folder="child"
        :selected-folder-id="selectedFolderId"
        :expanded-map="expandedMap"
        @toggle="$emit('toggle', $event)"
        @select="$emit('select', $event)"
        @contextmenu="$emit('contextmenu', $event)"
      />
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

defineOptions({ name: 'MediaTreeBranch' })

const props = defineProps({
  folder: { type: Object, required: true },
  selectedFolderId: { type: [String, Number], default: null },
  expandedMap: { type: Object, required: true }
})

defineEmits(['toggle', 'select', 'contextmenu'])

const isExpanded = computed(() => {
  return props.expandedMap[props.folder.key] !== false
})
</script>

<style scoped>
/* ---- Group ---- */
.media-tree-group {
  display: flex;
  flex-direction: column;
}

/* ---- Group head ---- */
.media-tree-group-head {
  display: flex;
  align-items: center;
  gap: 6px;
  width: 100%;
  border: none;
  background: transparent;
  color: var(--console-text);
  cursor: pointer;
  padding: 8px 10px;
  border-radius: 8px;
  font-size: 13px;
  font-weight: 500;
  line-height: 1.45;
  text-align: left;
  font-family: inherit;
  transition: background 0.12s ease, color 0.12s ease;
}

.media-tree-group-head:hover {
  background: rgba(13, 148, 136, 0.06);
}

.media-tree-group-head.active {
  background: var(--console-accent);
  color: #fff;
}

.media-tree-group-head.active .media-tree-group-count,
.media-tree-group-head.active .media-tree-chev {
  color: rgba(255, 255, 255, 0.7);
}

/* ---- Chevron ---- */
.media-tree-chev {
  flex: 0 0 auto;
  width: 14px;
  font-size: 11px;
  color: var(--console-muted);
  line-height: 1;
  text-align: center;
}

.media-tree-chev--empty {
  color: var(--console-muted);
  opacity: 0.4;
}

/* ---- Label & Count ---- */
.media-tree-group-label {
  flex: 1 1 auto;
  min-width: 0;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.media-tree-group-count {
  flex: 0 0 auto;
  font-size: 10px;
  font-weight: 600;
  color: var(--console-muted);
  min-width: 18px;
  text-align: center;
  padding: 1px 5px;
  border-radius: 4px;
  background: rgba(0, 0, 0, 0.04);
}

[data-theme='dark'] .media-tree-group-count {
  background: rgba(255, 255, 255, 0.06);
}

/* ---- Group body (children) ---- */
.media-tree-group-body {
  display: flex;
  flex-direction: column;
  padding-left: 16px;
}

.media-tree-group-body .media-tree-group-body {
  padding-left: 14px;
}
</style>

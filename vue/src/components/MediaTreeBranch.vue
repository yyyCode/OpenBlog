<template>
  <div class="media-tree-group">
    <button
      class="media-tree-group-head"
      :class="{ active: group.folderId === selectedFolderId }"
      type="button"
      :aria-expanded="isExpanded ? 'true' : 'false'"
      @click="$emit('select', group.folderId)"
      @contextmenu.prevent="$emit('contextmenu', { event: $event, folder: group })"
    >
      <span
        v-if="group.children.length > 0"
        class="media-tree-chev"
        aria-hidden="true"
        @click.stop="$emit('toggle', group.key)"
      >{{ isExpanded ? '▾' : '▸' }}</span>
      <span v-else class="media-tree-chev media-tree-chev--empty" aria-hidden="true"></span>
      <span class="media-tree-group-label">{{ group.label }}</span>
      <span class="media-tree-group-count">{{ group.fileCount }}</span>
    </button>

    <div v-show="isExpanded" class="media-tree-group-body">
      <MediaTreeBranch
        v-for="child in group.children"
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

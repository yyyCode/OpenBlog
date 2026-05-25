<template>
  <div class="article-tree-group">
    <button
      class="article-tree-group-head"
      type="button"
      :aria-expanded="isExpanded ? 'true' : 'false'"
      @click="$emit('toggle', group.key)"
    >
      <span class="article-tree-chev" aria-hidden="true">{{ isExpanded ? '▾' : '▸' }}</span>
      <span class="article-tree-group-label">{{ group.label }}</span>
    </button>

    <div v-show="isExpanded" class="article-tree-group-body">
      <ArticleTreeBranch
        v-for="child in group.children"
        :key="child.key"
        :group="child"
        :current-id="currentId"
        :expanded-map="expandedMap"
        @toggle="$emit('toggle', $event)"
        @select="$emit('select', $event)"
      />

      <button
        v-for="a in group.articles"
        :key="a.id"
        class="article-tree-leaf"
        :class="{ active: String(a.id) === String(currentId) }"
        type="button"
        @click="$emit('select', a.id)"
      >
        {{ a.title }}
      </button>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

defineOptions({ name: 'ArticleTreeBranch' })

const props = defineProps({
  group: { type: Object, required: true },
  currentId: { type: [String, Number], default: null },
  expandedMap: { type: Object, required: true }
})

defineEmits(['toggle', 'select'])

const isExpanded = computed(() => {
  return props.expandedMap[props.group.key] !== false
})
</script>

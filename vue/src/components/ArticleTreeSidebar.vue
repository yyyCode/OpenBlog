<template>
  <div class="article-tree-sidebar" :class="{ 'article-tree-sidebar--drawer': drawer }">
    <div v-if="showHead" class="article-tree-head">
      <div class="article-tree-head-title">{{ title }}</div>
      <button
        v-if="drawer"
        class="article-tree-head-close"
        type="button"
        aria-label="关闭目录"
        @click="$emit('close')"
      >
        ×
      </button>
    </div>

    <div class="article-tree-search">
      <input v-model="keyword" type="text" class="search-input" placeholder="搜索文章..." />
    </div>

    <div class="article-tree-body">
      <div v-if="loading" class="article-tree-hint">加载中...</div>
      <div v-else-if="filteredTree.length === 0" class="article-tree-hint">暂无文章</div>

      <ArticleTreeBranch
        v-for="group in filteredTree"
        v-else
        :key="group.key"
        :group="group"
        :current-id="currentId"
        :expanded-map="expanded"
        :force-expand="!!keyword.trim()"
        @toggle="toggleGroup"
        @select="onSelect"
      />
    </div>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'

import ArticleTreeBranch from './ArticleTreeBranch.vue'
import {
  buildCategoryArticleTree,
  collectExpandedKeysForArticle,
  filterCategoryArticleTree
} from '../utils/articleTree'

const props = defineProps({
  categories: { type: Array, default: () => [] },
  articles: { type: Array, default: () => [] },
  currentId: { type: [String, Number], default: null },
  loading: { type: Boolean, default: false },
  title: { type: String, default: '目录' },
  drawer: { type: Boolean, default: false },
  showHead: { type: Boolean, default: true }
})

const emit = defineEmits(['select', 'close'])

const keyword = ref('')
const expanded = ref({})

const tree = computed(() => buildCategoryArticleTree(props.categories, props.articles))
const filteredTree = computed(() => filterCategoryArticleTree(tree.value, keyword.value))

function toggleGroup(key) {
  expanded.value = {
    ...expanded.value,
    [key]: expanded.value[key] === false
  }
}

function onSelect(id) {
  emit('select', id)
}

function syncExpanded() {
  const next = { ...expanded.value }
  for (const node of tree.value) {
    walkInit(node, next)
  }
  for (const key of collectExpandedKeysForArticle(tree.value, props.currentId)) {
    next[key] = true
  }
  expanded.value = next
}

function walkInit(node, map) {
  if (map[node.key] === undefined) map[node.key] = true
  for (const child of node.children || []) walkInit(child, map)
}

watch([() => props.categories, () => props.articles, () => props.currentId], syncExpanded, { immediate: true })
</script>

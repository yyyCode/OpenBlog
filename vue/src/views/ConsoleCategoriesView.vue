<template>
  <div class="console-page">
    <header class="console-page-header">
      <div class="console-page-title">
        <h1>分类管理</h1>
      </div>
    </header>

    <div class="console-card console-inner-card">
      <div class="admin-section">
        <div class="admin-articles">
          <div class="article-list">
            <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px">
              <div style="font-weight: 900">分类列表</div>
              <button class="btn" style="padding: 8px 12px" type="button" @click="newCategory">新建</button>
            </div>

            <div v-if="loading" style="color: var(--console-muted, var(--muted))">加载中...</div>
            <div v-else-if="items.length === 0" style="color: var(--console-muted, var(--muted))">暂无分类</div>

            <div
              v-for="c in items"
              :key="c.id"
              class="article-list-item"
              :class="{ active: selectedId === c.id }"
              @click="loadEditor(c.id)"
            >
              <div style="font-weight: 950; line-height: 1.3">{{ categoryDisplay(c) }}</div>
              <div style="color: var(--console-muted, var(--muted)); font-size: 12px; margin-top: 6px">
                排序 {{ c.sortOrder ?? 0 }}
              </div>
            </div>
          </div>

          <div class="article-editor">
            <div v-if="loadingDetail" style="color: var(--console-muted, var(--muted))">加载中...</div>
            <div v-else>
              <div class="field">
                <div class="label">分类名称</div>
                <input v-model="form.name" class="input" type="text" />
              </div>
              <div class="field">
                <div class="label">父分类</div>
                <select v-model="form.parentId" class="input">
                  <option :value="null">无（顶级分类）</option>
                  <option v-for="c in parentOptions" :key="c.id" :value="c.id">
                    {{ categoryDisplay(c) }}
                  </option>
                </select>
              </div>
              <div class="field">
                <div class="label">排序（越小越靠前）</div>
                <input v-model.number="form.sortOrder" class="input" type="number" />
              </div>

              <div style="display: flex; gap: 12px; flex-wrap: wrap; margin-top: 14px">
                <button class="btn primary" type="button" @click="saveCategory">保存</button>
                <button
                  class="btn"
                  type="button"
                  :disabled="!selectedId"
                  :style="{ opacity: selectedId ? 1 : 0.6 }"
                  @click="removeCategory"
                >
                  删除
                </button>
              </div>

              <div v-if="success" class="success" style="margin-top: 10px">{{ success }}</div>
              <div v-if="error" class="error" style="margin-top: 10px">{{ error }}</div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import {
  createCategory,
  deleteCategory,
  fetchCategories,
  updateCategory
} from '../api/category'

const loading = ref(false)
const loadingDetail = ref(false)
const items = ref([])
const selectedId = ref(null)
const form = ref({
  name: '',
  parentId: null,
  sortOrder: 0
})
const error = ref('')
const success = ref('')

const parentOptions = computed(() => {
  if (!selectedId.value) return items.value
  return items.value.filter((c) => c.id !== selectedId.value)
})

function categoryDisplay(c) {
  const path = c?.path
  if (Array.isArray(path) && path.length > 0) return path.join(' / ')
  return c?.name || ''
}

function resetForm() {
  selectedId.value = null
  form.value = { name: '', parentId: null, sortOrder: 0 }
}

function newCategory() {
  error.value = ''
  success.value = ''
  resetForm()
}

function loadEditor(id) {
  error.value = ''
  success.value = ''
  loadingDetail.value = true
  try {
    const c = items.value.find((x) => x.id === id)
    if (!c) return
    selectedId.value = id
    form.value = {
      name: c.name || '',
      parentId: c.parentId ?? null,
      sortOrder: c.sortOrder ?? 0
    }
  } finally {
    loadingDetail.value = false
  }
}

async function loadList() {
  loading.value = true
  try {
    items.value = await fetchCategories()
  } finally {
    loading.value = false
  }
}

async function saveCategory() {
  error.value = ''
  success.value = ''
  const payload = {
    name: form.value.name,
    parentId: form.value.parentId,
    sortOrder: form.value.sortOrder ?? 0
  }
  try {
    if (selectedId.value) {
      await updateCategory(selectedId.value, payload)
    } else {
      const created = await createCategory(payload)
      selectedId.value = created.id
    }
    success.value = '保存成功'
    await loadList()
    if (selectedId.value) loadEditor(selectedId.value)
  } catch (e) {
    error.value = e?.message || '保存失败'
  }
}

async function removeCategory() {
  if (!selectedId.value) return
  error.value = ''
  success.value = ''
  try {
    await deleteCategory(selectedId.value)
    success.value = '已删除'
    resetForm()
    await loadList()
  } catch (e) {
    error.value = e?.message || '删除失败'
  }
}

onMounted(loadList)
</script>

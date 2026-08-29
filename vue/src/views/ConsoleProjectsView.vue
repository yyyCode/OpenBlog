<template>
  <div class="console-page">
    <div class="console-page-header">
      <div class="console-page-title">
        <span class="console-page-title-ico">
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <rect x="3" y="3" width="18" height="18" rx="2" />
            <path d="M9 3v18" />
            <path d="M3 9h18" />
          </svg>
        </span>
        <h1>项目管理</h1>
      </div>
      <button type="button" class="console-btn-dark" @click="showEditor = true; editing = null">
        ＋ 新建项目
      </button>
    </div>

    <!-- 编辑器弹出层 -->
    <div v-if="showEditor" class="console-editor-overlay" @click.self="closeEditor">
      <div class="console-editor-panel">
        <h2 class="console-editor-title">{{ editing ? '编辑项目' : '新建项目' }}</h2>

        <div class="console-editor-form">
          <div class="field">
            <div class="label">项目名称 *</div>
            <input v-model="form.title" class="input" placeholder="项目名称" maxlength="120" />
          </div>
          <div class="field">
            <div class="label">简介</div>
            <input v-model="form.summary" class="input" placeholder="一句话简介" maxlength="255" />
          </div>
          <div class="field">
            <div class="label">封面图 Media Key</div>
            <input v-model="form.coverMediaKey" class="input" placeholder="留空则不显示封面" />
          </div>
          <div class="field">
            <div class="label">技术栈</div>
            <input v-model="form.techStack" class="input" placeholder="Vue, Spring Boot, MySQL（逗号分隔）" />
          </div>
          <div class="field">
            <div class="label">项目链接</div>
            <input v-model="form.projectUrl" class="input" placeholder="https://..." />
          </div>
          <div class="field">
            <div class="label">GitHub 链接</div>
            <input v-model="form.githubUrl" class="input" placeholder="https://github.com/..." />
          </div>
          <div class="field">
            <div class="label">排序值</div>
            <input v-model.number="form.sortOrder" class="input" type="number" placeholder="0" />
          </div>
          <div class="field">
            <div class="label">状态</div>
            <select v-model="form.status" class="input">
              <option value="DRAFT">草稿</option>
              <option value="PUBLISHED">已发布</option>
            </select>
          </div>
          <div class="field">
            <div class="label">正文 (Markdown) *</div>
            <textarea v-model="form.contentMarkdown" class="textarea" rows="12" placeholder="支持 Markdown 语法"></textarea>
          </div>
        </div>

        <div class="console-editor-actions">
          <button type="button" class="btn" @click="closeEditor">取消</button>
          <button type="button" class="btn primary" :disabled="saving" @click="save">
            {{ saving ? '保存中...' : '保存' }}
          </button>
        </div>
      </div>
    </div>

    <!-- 列表 -->
    <div v-if="loading" class="card">
      <div class="card-body" style="padding: 22px">加载中...</div>
    </div>

    <div v-else>
      <div v-if="items.length === 0" class="card">
        <div class="card-body" style="padding: 22px; color: var(--muted)">暂无项目</div>
      </div>

      <div v-else class="card">
        <div class="admin-article-table">
          <div
            v-for="item in items"
            :key="item.id"
            class="admin-article-row"
          >
            <div class="admin-article-info">
              <div class="admin-article-title">{{ item.title }}</div>
              <div class="admin-article-meta">
                <span :class="statusClass(item.status)">{{ item.status }}</span>
                <span>排序: {{ item.sortOrder ?? 0 }}</span>
                <span v-if="item.publishedAt">{{ formatDate(item.publishedAt) }}</span>
              </div>
            </div>
            <div class="admin-article-actions">
              <button type="button" class="btn btn-sm" @click="editItem(item)">编辑</button>
              <button type="button" class="btn btn-sm btn-outline-danger" @click="remove(item.id)">删除</button>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { fetchAdminProjects, fetchAdminProjectDetail, createProject, updateProject, deleteProject } from '../api/project'
import { showMessage } from '../utils/message'

const loading = ref(true)
const items = ref([])
const showEditor = ref(false)
const editing = ref(null)
const saving = ref(false)
const form = ref(emptyForm())

function emptyForm() {
  return {
    title: '',
    summary: '',
    contentMarkdown: '',
    coverMediaKey: '',
    techStack: '',
    projectUrl: '',
    githubUrl: '',
    sortOrder: 0,
    status: 'DRAFT'
  }
}

async function load() {
  loading.value = true
  try {
    // 控制台用管理接口，草稿/已发布都可见可管理（公开列表只返回已发布）
    const res = await fetchAdminProjects(0, 100)
    items.value = res?.items || []
  } finally {
    loading.value = false
  }
}

onMounted(() => load())

function editItem(item) {
  editing.value = item
  form.value = {
    title: item.title || '',
    summary: item.summary || '',
    contentMarkdown: '',
    coverMediaKey: item.coverMediaKey || '',
    techStack: item.techStack || '',
    projectUrl: item.projectUrl || '',
    githubUrl: item.githubUrl || '',
    sortOrder: item.sortOrder ?? 0,
    status: item.status || 'DRAFT'
  }
  showEditor.value = true
  // 加载完整详情获取 contentMarkdown（用带鉴权的 admin 详情，草稿也能取到）
  import('../api/project').then(m => m.fetchAdminProjectDetail(item.id)).then(d => {
    if (d?.contentMarkdown) form.value.contentMarkdown = d.contentMarkdown
  }).catch(() => {})
}

function closeEditor() {
  showEditor.value = false
  editing.value = null
  form.value = emptyForm()
}

async function save() {
  if (!form.value.title.trim()) {
    showMessage('请输入项目名称')
    return
  }
  if (!form.value.contentMarkdown.trim()) {
    showMessage('请输入正文内容')
    return
  }
  saving.value = true
  try {
    if (editing.value) {
      await updateProject(editing.value.id, form.value)
      showMessage('更新成功')
    } else {
      await createProject(form.value)
      showMessage('创建成功')
    }
    closeEditor()
    await load()
  } catch {
    showMessage('保存失败')
  } finally {
    saving.value = false
  }
}

async function remove(id) {
  if (!confirm('确定删除此项目？')) return
  try {
    await deleteProject(id)
    showMessage('已删除')
    await load()
  } catch {
    showMessage('删除失败')
  }
}

function statusClass(s) {
  return s === 'PUBLISHED' ? 'tag-status-active' : 'tag-status-pending'
}

function formatDate(v) {
  if (!v) return ''
  try {
    return new Date(v).toISOString().slice(0, 10)
  } catch {
    return ''
  }
}
</script>

<style scoped>
.admin-article-table {
  display: flex;
  flex-direction: column;
}

.admin-article-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 16px;
  border-bottom: 1px solid var(--console-border);
}

.admin-article-row:last-child {
  border-bottom: none;
}

.admin-article-info {
  flex: 1;
  min-width: 0;
}

.admin-article-title {
  font-weight: 600;
  font-size: 14px;
  color: var(--console-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.admin-article-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 6px;
  font-size: 12px;
  color: var(--console-muted);
}

.admin-article-actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}

/* Editor overlay */
.console-editor-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.4);
  z-index: 20000;
  display: flex;
  align-items: flex-start;
  justify-content: center;
  padding: 40px 16px;
  overflow-y: auto;
}

.console-editor-panel {
  background: var(--card);
  border-radius: 12px;
  width: 100%;
  max-width: 720px;
  padding: 28px;
  box-shadow: 0 16px 48px rgba(0, 0, 0, 0.2);
}

.console-editor-title {
  margin: 0 0 20px;
  font-size: 18px;
  font-weight: 700;
  color: var(--text);
}

.console-editor-form {
  max-height: 60vh;
  overflow-y: auto;
}

.console-editor-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 20px;
}
</style>

<template>
  <div class="console-page">
    <div class="console-page-header">
      <div class="console-page-title">
        <span class="console-page-title-ico">
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <rect x="3" y="3" width="18" height="18" rx="2" />
            <circle cx="12" cy="12" r="4" />
            <path d="M3 12h4" />
            <path d="M17 12h4" />
          </svg>
        </span>
        <h1>小而美公司管理</h1>
      </div>
      <button type="button" class="console-btn-dark" @click="showEditor = true; editing = null">
        ＋ 新建公司
      </button>
    </div>

    <!-- 编辑器弹出层 -->
    <div v-if="showEditor" class="console-editor-overlay" @click.self="closeEditor">
      <div class="console-editor-panel">
        <h2 class="console-editor-title">{{ editing ? '编辑公司' : '新建公司' }}</h2>

        <div class="console-editor-form">
          <div class="field">
            <div class="label">公司名称 *</div>
            <input v-model="form.name" class="input" placeholder="公司名称" maxlength="120" />
          </div>
          <div class="field">
            <div class="label">公司类型</div>
            <input v-model="form.type" class="input" placeholder="效率工具 / 企业服务 / 云服务…" maxlength="32" />
          </div>
          <div class="field-grid">
            <div class="field">
              <div class="label">规模下限（人）</div>
              <input v-model.number="form.scaleMin" class="input" type="number" placeholder="如 100" />
            </div>
            <div class="field">
              <div class="label">规模上限（人，留空表示 N+）</div>
              <input v-model.number="form.scaleMax" class="input" type="number" placeholder="如 499" />
            </div>
          </div>
          <div class="field">
            <div class="label">头像底色（无 logo 时占位用）</div>
            <div class="color-row">
              <input v-model="form.color" class="input color-input" placeholder="如 #4f7cff" maxlength="16" />
              <input v-model="form.color" type="color" class="color-picker" />
            </div>
          </div>
          <div class="field">
            <div class="label">Logo（媒体库 Key 或完整链接，留空用首字占位）</div>
            <input v-model="form.logoMediaKey" class="input" placeholder="媒体库 key（如 general/xxx.png）或完整图片链接" />
            <img v-if="previewLogo" :src="previewLogo" alt="logo 预览" class="cover-preview" />
          </div>
          <div class="field-grid">
            <div class="field">
              <div class="label">所在城市</div>
              <input v-model="form.city" class="input" placeholder="如 北京" maxlength="64" />
            </div>
            <div class="field">
              <div class="label">成立年份</div>
              <input v-model.number="form.founded" class="input" type="number" placeholder="如 2014" />
            </div>
          </div>
          <div class="field">
            <div class="label">办公地址</div>
            <input v-model="form.address" class="input" placeholder="办公地址" maxlength="255" />
          </div>
          <div class="field">
            <div class="label">主营业务</div>
            <input v-model="form.business" class="input" placeholder="一句话说明主营方向" maxlength="255" />
          </div>
          <div class="field">
            <div class="label">公司简介</div>
            <textarea v-model="form.description" class="textarea" rows="5" placeholder="公司简介（纯文本）"></textarea>
          </div>
          <div class="field">
            <div class="label">官网域名（不带协议）</div>
            <input v-model="form.website" class="input" placeholder="如 mockingbot.com" maxlength="128" />
          </div>
          <div class="field-grid">
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
        <div class="card-body" style="padding: 22px; color: var(--muted)">暂无公司数据，点击右上角新建</div>
      </div>

      <div v-else class="card">
        <div class="admin-article-table">
          <div
            v-for="item in items"
            :key="item.id"
            class="admin-article-row"
          >
            <div class="admin-article-info">
              <div class="admin-article-title">
                <img v-if="logoUrl(item.logoMediaKey)" :src="logoUrl(item.logoMediaKey)" alt="" class="row-logo" />
                {{ item.name }}
              </div>
              <div class="admin-article-meta">
                <span v-if="item.type">{{ item.type }}</span>
                <span v-if="scaleText(item)">{{ scaleText(item) }}人</span>
                <span v-if="item.city">{{ item.city }}</span>
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
import { computed, onMounted, ref } from 'vue'
import {
  fetchAdminSmallCompanies,
  fetchAdminSmallCompanyDetail,
  createSmallCompany,
  updateSmallCompany,
  deleteSmallCompany,
  logoUrl,
  formatScale
} from '../api/smallCompany'
import { showMessage } from '../utils/message'

const loading = ref(true)
const items = ref([])
const showEditor = ref(false)
const editing = ref(null)
const saving = ref(false)
const form = ref(emptyForm())

function emptyForm() {
  return {
    name: '',
    type: '',
    scaleMin: null,
    scaleMax: null,
    color: '#4f7cff',
    logoMediaKey: '',
    city: '',
    founded: null,
    address: '',
    business: '',
    description: '',
    website: '',
    sortOrder: 0,
    status: 'DRAFT'
  }
}

async function load() {
  loading.value = true
  try {
    // 控制台用管理接口，草稿/已发布都可见可管理（公开列表只返回已发布）
    const res = await fetchAdminSmallCompanies(0, 100)
    items.value = res?.items || []
  } finally {
    loading.value = false
  }
}

onMounted(() => load())

async function editItem(item) {
  editing.value = item
  showEditor.value = true
  // 列表接口只返回 ListItem（无 address/business/description/website），
  // 必须用带鉴权的详情接口补拉，否则保存会把这些字段清空
  form.value = {
    name: item.name || '',
    type: item.type || '',
    scaleMin: item.scaleMin ?? null,
    scaleMax: item.scaleMax ?? null,
    color: item.color || '#4f7cff',
    logoMediaKey: item.logoMediaKey || '',
    city: item.city || '',
    founded: item.founded ?? null,
    address: '',
    business: '',
    description: '',
    website: '',
    sortOrder: item.sortOrder ?? 0,
    status: item.status || 'DRAFT'
  }
  try {
    const d = await fetchAdminSmallCompanyDetail(item.id)
    if (d) {
      form.value.address = d.address || ''
      form.value.business = d.business || ''
      form.value.description = d.description || ''
      form.value.website = d.website || ''
    }
  } catch {
    // 详情拉取失败不阻塞编辑，保存时以上述默认值提交
  }
}

function closeEditor() {
  showEditor.value = false
  editing.value = null
  form.value = emptyForm()
}

async function save() {
  if (!form.value.name.trim()) {
    showMessage('请输入公司名称')
    return
  }
  if (form.value.scaleMin != null && form.value.scaleMax != null && form.value.scaleMax < form.value.scaleMin) {
    showMessage('规模上限不能小于下限')
    return
  }
  const payload = { ...form.value }
  // v-model.number 清空时回退为 ''，后端 Integer 反序列化空串会 500；统一归一为 null
  for (const k of ['scaleMin', 'scaleMax', 'founded', 'sortOrder']) {
    if (payload[k] === '' || payload[k] === null) payload[k] = null
  }
  saving.value = true
  try {
    if (editing.value) {
      await updateSmallCompany(editing.value.id, payload)
      showMessage('更新成功')
    } else {
      await createSmallCompany(payload)
      showMessage('创建成功')
    }
    closeEditor()
    await load()
  } catch (e) {
    showMessage(e?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function remove(id) {
  if (!confirm('确定删除此公司？')) return
  try {
    await deleteSmallCompany(id)
    showMessage('已删除')
    await load()
  } catch {
    showMessage('删除失败')
  }
}

function statusClass(s) {
  return s === 'PUBLISHED' ? 'tag-status-active' : 'tag-status-pending'
}

function scaleText(c) {
  return formatScale(c)
}

// logo 预览兼容两种输入：裸 key 或完整媒体链接（完整链接先提取 key 再拼 URL，避免破链）
const previewLogo = computed(() => {
  const raw = (form.value.logoMediaKey || '').trim()
  if (!raw) return ''
  const marker = '/api/v1/media/files/'
  const idx = raw.indexOf(marker)
  const key = idx >= 0 ? raw.substring(idx + marker.length) : raw
  return logoUrl(key)
})

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
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  font-size: 14px;
  color: var(--console-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.row-logo {
  width: 22px;
  height: 22px;
  border-radius: 6px;
  object-fit: contain;
  background: #fff;
  flex: none;
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

.field-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.color-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.color-input {
  flex: 1;
}

.color-picker {
  width: 42px;
  height: 34px;
  padding: 0;
  border: 1px solid var(--console-border);
  border-radius: 6px;
  background: none;
  cursor: pointer;
}

.cover-preview {
  display: block;
  margin-top: 8px;
  max-width: 120px;
  max-height: 120px;
  border-radius: 12px;
  object-fit: contain;
  border: 1px solid var(--console-border);
  background: #fff;
}

.console-editor-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 20px;
}

@media (max-width: 560px) {
  .field-grid {
    grid-template-columns: 1fr;
  }
}
</style>

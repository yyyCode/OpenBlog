<template>
  <div class="console-page">
    <header class="console-page-header">
      <div class="console-page-title">
        <h1>敏感词管理</h1>
        <div class="sw-sub">词库版本化发布，运行时快照热切换，三引擎可切换对比</div>
      </div>
      <div class="sw-toolbar">
        <button class="btn" :disabled="loading" @click="refreshAll">刷新</button>
      </div>
    </header>

    <div class="console-card console-inner-card">
      <div v-if="status" class="sw-status-grid">
        <div class="sw-stat">
          <span class="sw-stat-label">状态</span>
          <strong :class="['sw-badge', status.state === 'READY' ? 'ok' : 'warn']">{{ status.state }}</strong>
        </div>
        <div class="sw-stat">
          <span class="sw-stat-label">引擎</span>
          <strong>{{ status.engine }}</strong>
        </div>
        <div class="sw-stat">
          <span class="sw-stat-label">线上版本</span>
          <strong>{{ status.publishedVersion ?? '未发布' }}</strong>
        </div>
        <div class="sw-stat">
          <span class="sw-stat-label">本机版本</span>
          <strong>{{ status.localVersion }}</strong>
        </div>
        <div class="sw-stat">
          <span class="sw-stat-label">词条数</span>
          <strong>{{ status.wordCount }}</strong>
        </div>
        <div class="sw-stat">
          <span class="sw-stat-label">平均匹配耗时</span>
          <strong>{{ status.avgMatchUs > 0 ? formatDuration(status.avgMatchUs) : '-' }}</strong>
        </div>
        <div class="sw-stat">
          <span class="sw-stat-label">待发布</span>
          <strong>{{ pendingTotal }}</strong>
        </div>
        <div class="sw-stat">
          <span class="sw-stat-label">节点</span>
          <strong>{{ status.nodes?.upToDate ?? 0 }}/{{ status.nodes?.total ?? 0 }}</strong>
        </div>
        <div class="sw-stat" v-if="status.shadow?.engine">
          <span class="sw-stat-label">影子 {{ status.shadow.engine }}</span>
          <strong>{{ status.shadow.mismatches }}/{{ status.shadow.samples }} 不一致</strong>
        </div>
      </div>
      <div v-else class="sw-muted">状态加载中...</div>
      <div v-if="status" class="sw-publish-row">
        <span class="sw-stat-label">运行引擎</span>
        <select v-model="engineChoice" class="sw-input sw-select" :disabled="switchingEngine">
          <option value="default">跟随配置（{{ status.configuredEngine }}）</option>
          <option value="houbb">houbb（DFA）</option>
          <option value="trie">trie（字典树）</option>
          <option value="ac">ac（AC 自动机）</option>
        </select>
        <button
          class="btn primary"
          :disabled="switchingEngine || engineChoice === (status.engineOverride || 'default')"
          @click="switchEngine"
        >
          {{ switchingEngine ? '切换中...' : '热切换' }}
        </button>
        <span v-if="engineSwitchMsg" class="sw-muted">{{ engineSwitchMsg }}</span>
        <span v-if="engineSwitchError" class="sw-error">{{ engineSwitchError }}</span>
      </div>
    </div>

    <div class="console-card console-inner-card">
      <div class="sw-toolbar sw-toolbar-wrap">
        <input
          v-model.trim="keyword"
          class="sw-input"
          type="search"
          placeholder="搜索词条"
          @keyup.enter="applyFilters"
        />
        <select v-model="category" class="sw-input sw-select" @change="applyFilters">
          <option value="">全部分类</option>
          <option v-for="c in categories" :key="c" :value="c">{{ c }}</option>
        </select>
        <select v-model="statusFilter" class="sw-input sw-select" @change="applyFilters">
          <option value="">全部状态</option>
          <option :value="1">启用</option>
          <option :value="0">停用</option>
        </select>
        <button class="btn" @click="applyFilters">查询</button>
        <span class="sw-spacer"></span>
        <button class="btn primary" @click="openCreate">新增词条</button>
        <button class="btn" @click="openImport">导入</button>
        <button class="btn" :disabled="exporting" @click="doExport">{{ exporting ? '导出中...' : '导出' }}</button>
        <button class="btn" :disabled="selectedIds.length === 0" @click="removeSelected">
          批量删除{{ selectedIds.length ? ` (${selectedIds.length})` : '' }}
        </button>
      </div>

      <div v-if="error" class="sw-error">{{ error }}</div>

      <div v-if="loading" class="sw-muted" style="padding: 16px 0">加载中...</div>
      <div v-else-if="items.length === 0" class="sw-muted" style="padding: 16px 0">暂无词条</div>
      <div v-else class="sw-table-wrap">
        <table class="sw-table">
          <thead>
            <tr>
              <th class="sw-check">
                <input type="checkbox" :checked="allSelected" @change="toggleAll" />
              </th>
              <th>词条</th>
              <th>分类</th>
              <th>状态</th>
              <th>备注</th>
              <th>更新时间</th>
              <th class="sw-actions">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in items" :key="item.id">
              <td class="sw-check">
                <input v-model="selectedIds" type="checkbox" :value="item.id" />
              </td>
              <td><span class="sw-word">{{ item.word }}</span></td>
              <td>{{ item.category || 'default' }}</td>
              <td>
                <span :class="['sw-badge', item.status === 1 ? 'ok' : 'off']">
                  {{ item.status === 1 ? '启用' : '停用' }}
                </span>
              </td>
              <td class="sw-ellipsis" :title="item.remark || ''">{{ item.remark || '-' }}</td>
              <td class="sw-muted">{{ formatTime(item.updatedAt) }}</td>
              <td class="sw-actions">
                <button class="sw-link" @click="openEdit(item)">编辑</button>
                <button class="sw-link danger" @click="removeOne(item)">删除</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <div class="sw-pager">
        <span class="sw-muted">共 {{ total }} 条 · 第 {{ page + 1 }} / {{ totalPages }} 页</span>
        <div class="sw-pager-btns">
          <button class="btn" :disabled="page <= 0" @click="goPage(page - 1)">上一页</button>
          <button class="btn" :disabled="page + 1 >= totalPages" @click="goPage(page + 1)">下一页</button>
        </div>
      </div>
    </div>

    <div class="sw-grid">
      <div class="console-card console-inner-card">
        <div class="console-card-head">
          <h2>待发布变更</h2>
          <span class="sw-muted">新增 {{ pending.added }} · 删除 {{ pending.removed }} · 修改 {{ pending.updated }}</span>
        </div>
        <div v-if="pending.items.length === 0" class="sw-muted">暂无未发布变更</div>
        <ul v-else class="sw-change-list">
          <li v-for="(it, idx) in pending.items.slice(0, 60)" :key="idx">
            <span :class="['sw-badge', actionClass(it.action)]">{{ actionLabel(it.action) }}</span>
            <span class="sw-word">{{ it.word }}</span>
          </li>
          <li v-if="pending.items.length > 60" class="sw-muted">还有 {{ pending.items.length - 60 }} 条...</li>
        </ul>
        <div class="sw-publish-row">
          <input v-model.trim="publishRemark" class="sw-input" placeholder="发布备注（可选）" />
          <label class="sw-check" title="工作集为空时仍允许发布">
            <input v-model="allowEmpty" type="checkbox" /> 允许空词表
          </label>
          <button class="btn primary" :disabled="publishing" @click="publishNow">
            {{ publishing ? '发布中...' : '发布新版本' }}
          </button>
        </div>
      </div>

      <div class="console-card console-inner-card">
        <div class="console-card-head"><h2>在线检测</h2></div>
        <textarea v-model="checkText" class="sw-textarea" rows="5" placeholder="输入文本，检测是否命中当前线上词库"></textarea>
        <div class="sw-publish-row">
          <button class="btn primary" :disabled="checking || !checkText.trim()" @click="doCheck">
            {{ checking ? '检测中...' : '检测' }}
          </button>
          <span v-if="checkResult" class="sw-muted">
            {{ checkResult.engine }} · v{{ checkResult.version }} · 匹配耗时 {{ formatDuration(checkResult.costUs) }}
          </span>
        </div>
        <div v-if="checkResult" class="sw-check-result">
          <div>
            <span :class="['sw-badge', checkResult.hit ? 'warn' : 'ok']">
              {{ checkResult.hit ? '命中' : '通过' }}
            </span>
            <span v-if="checkResult.hitWords?.length" class="sw-muted">
              命中词：{{ checkResult.hitWords.join('、') }}
            </span>
          </div>
          <div class="sw-filtered">{{ checkResult.filteredText }}</div>
        </div>
      </div>
    </div>

    <div class="console-card console-inner-card">
      <div class="console-card-head">
        <h2>版本历史</h2>
        <button class="btn" @click="loadVersions">刷新版本</button>
      </div>
      <div v-if="versions.length === 0" class="sw-muted">暂无版本</div>
      <div v-else class="sw-table-wrap">
        <table class="sw-table">
          <thead>
            <tr>
              <th>版本</th>
              <th>类型</th>
              <th>状态</th>
              <th>词条数</th>
              <th>变更摘要</th>
              <th>发布时间</th>
              <th class="sw-actions">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="v in versions" :key="v.version">
              <td>v{{ v.version }}</td>
              <td>{{ kindLabel(v.kind) }}</td>
              <td><span :class="['sw-badge', v.status === 'PUBLISHED' ? 'ok' : 'off']">{{ v.status }}</span></td>
              <td>{{ v.wordCount }}</td>
              <td class="sw-ellipsis" :title="v.changeSummary || ''">{{ v.changeSummary || v.remark || '-' }}</td>
              <td class="sw-muted">{{ formatTime(v.publishedAt) }}</td>
              <td class="sw-actions">
                <button class="sw-link" @click="showDiff(v.version)">差异</button>
                <button class="sw-link" @click="syncVersion(v.version)">同步</button>
                <button
                  v-if="v.status === 'PUBLISHED' && v.version !== status?.publishedVersion"
                  class="sw-link danger"
                  @click="rollbackVersion(v.version)"
                >
                  回滚
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <div v-if="editorOpen" class="sw-modal-mask" @click.self="closeEditor">
      <div class="sw-modal">
        <h3>{{ editingId ? '编辑词条' : '新增词条' }}</h3>
        <label class="field">
          <span>词条</span>
          <input v-model.trim="editor.word" class="input" maxlength="128" placeholder="敏感词内容" />
        </label>
        <label class="field">
          <span>分类</span>
          <input v-model.trim="editor.category" class="input" maxlength="64" placeholder="default" />
        </label>
        <label class="field">
          <span>状态</span>
          <select v-model.number="editor.status" class="input">
            <option :value="1">启用</option>
            <option :value="0">停用</option>
          </select>
        </label>
        <label class="field">
          <span>备注</span>
          <input v-model.trim="editor.remark" class="input" maxlength="255" placeholder="可选" />
        </label>
        <div v-if="editorError" class="sw-error">{{ editorError }}</div>
        <div class="sw-modal-actions">
          <button class="btn" @click="closeEditor">取消</button>
          <button class="btn primary" :disabled="saving" @click="saveEditor">
            {{ saving ? '保存中...' : '保存' }}
          </button>
        </div>
      </div>
    </div>

    <div v-if="importOpen" class="sw-modal-mask" @click.self="closeImport">
      <div class="sw-modal sw-modal-wide">
        <h3>批量导入</h3>
        <div class="sw-toolbar">
          <input type="file" accept=".txt,.csv" @change="onFileChange" />
          <button class="btn" :disabled="!importFile || previewing" @click="doPreview">
            {{ previewing ? '解析中...' : '预览' }}
          </button>
        </div>
        <div v-if="preview" class="sw-import-summary">
          共 {{ preview.total }} 行 · 新增 {{ preview.newCount }} · 已存在 {{ preview.exist }} ·
          非法 {{ preview.invalid }} · 忽略 {{ preview.ignored }}
        </div>
        <div v-if="preview?.rows?.length" class="sw-table-wrap sw-import-rows">
          <table class="sw-table">
            <thead>
              <tr><th>行号</th><th>词条</th><th>结果</th><th>原因</th></tr>
            </thead>
            <tbody>
              <tr v-for="row in preview.rows.slice(0, 100)" :key="row.line">
                <td>{{ row.line }}</td>
                <td>{{ row.word }}</td>
                <td>{{ row.status }}</td>
                <td class="sw-muted">{{ row.reason || '-' }}</td>
              </tr>
            </tbody>
          </table>
        </div>
        <div class="sw-toolbar" style="margin-top: 12px">
          <select v-model="importMode" class="input" style="max-width: 220px">
            <option value="APPEND">追加（推荐）</option>
            <option value="REPLACE">全量替换</option>
          </select>
          <label v-if="importMode === 'REPLACE'" class="sw-inline-check">
            <input v-model="confirmReplace" type="checkbox" />
            确认清空并替换工作集
          </label>
        </div>
        <div v-if="importError" class="sw-error">{{ importError }}</div>
        <div class="sw-modal-actions">
          <button class="btn" @click="closeImport">取消</button>
          <button
            class="btn primary"
            :disabled="!importFile || importing || (importMode === 'REPLACE' && !confirmReplace)"
            @click="doImport"
          >
            {{ importing ? '导入中...' : '开始导入' }}
          </button>
        </div>
      </div>
    </div>

    <div v-if="diffOpen" class="sw-modal-mask" @click.self="diffOpen = false">
      <div class="sw-modal sw-modal-wide">
        <h3>版本 v{{ diff?.version }} 与当前工作集差异</h3>
        <div class="sw-muted" style="margin-bottom: 10px">
          新增 {{ diff?.added ?? 0 }} · 删除 {{ diff?.removed ?? 0 }}
        </div>
        <div class="sw-diff-grid">
          <div>
            <div class="sw-diff-title">新增</div>
            <ul class="sw-change-list">
              <li v-for="w in (diff?.addedWords || []).slice(0, 200)" :key="'a-' + w">
                <span class="sw-badge ok">+</span><span class="sw-word">{{ w }}</span>
              </li>
              <li v-if="(diff?.added ?? 0) > (diff?.addedWords || []).length" class="sw-muted">
                还有 {{ diff.added - (diff.addedWords || []).length }} 条...
              </li>
            </ul>
          </div>
          <div>
            <div class="sw-diff-title">删除</div>
            <ul class="sw-change-list">
              <li v-for="w in (diff?.removedWords || []).slice(0, 200)" :key="'r-' + w">
                <span class="sw-badge off">-</span><span class="sw-word">{{ w }}</span>
              </li>
              <li v-if="(diff?.removed ?? 0) > (diff?.removedWords || []).length" class="sw-muted">
                还有 {{ diff.removed - (diff.removedWords || []).length }} 条...
              </li>
            </ul>
          </div>
        </div>
        <div class="sw-modal-actions">
          <button class="btn" @click="diffOpen = false">关闭</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import {
  batchDeleteSensitiveWords,
  checkSensitiveText,
  createSensitiveWord,
  deleteSensitiveWord,
  exportSensitiveWords,
  fetchSensitiveWordCategories,
  fetchSensitiveWordStatus,
  fetchSensitiveWordVersionDiff,
  fetchSensitiveWordVersions,
  fetchSensitiveWords,
  importSensitiveWords,
  previewSensitiveWordImport,
  publishSensitiveWords,
  rollbackSensitiveWordVersion,
  switchSensitiveWordEngine,
  syncSensitiveWordVersion,
  updateSensitiveWord
} from '../api/sensitiveWord'

const PAGE_SIZE = 20

const status = ref(null)
const items = ref([])
const total = ref(0)
const page = ref(0)
const loading = ref(false)
const error = ref('')
const keyword = ref('')
const category = ref('')
const statusFilter = ref('')
const categories = ref([])
const selectedIds = ref([])

const pending = reactive({ added: 0, removed: 0, updated: 0, items: [] })
const publishRemark = ref('')
const allowEmpty = ref(false)
const publishing = ref(false)

const checkText = ref('')
const checking = ref(false)
const checkResult = ref(null)

const engineChoice = ref('default')
const switchingEngine = ref(false)
const engineSwitchMsg = ref('')
const engineSwitchError = ref('')

const versions = ref([])
const diffOpen = ref(false)
const diff = ref(null)
const exporting = ref(false)

const editorOpen = ref(false)
const editingId = ref(null)
const saving = ref(false)
const editorError = ref('')
const editor = reactive({ word: '', category: 'default', status: 1, remark: '' })

const importOpen = ref(false)
const importFile = ref(null)
const previewing = ref(false)
const preview = ref(null)
const importMode = ref('APPEND')
const confirmReplace = ref(false)
const importing = ref(false)
const importError = ref('')

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / PAGE_SIZE)))
const allSelected = computed(() => items.value.length > 0 && items.value.every((it) => selectedIds.value.includes(it.id)))
const pendingTotal = computed(() => pending.added + pending.removed + pending.updated)

// 匹配通常只有几十微秒，用 ms 显示会全是 0ms，这里按量级选单位
function formatDuration(us) {
  const n = Number(us)
  if (!Number.isFinite(n)) return '-'
  if (n >= 1000) return `${(n / 1000).toFixed(2)}ms`
  return `${n < 10 ? n.toFixed(1) : n.toFixed(0)}µs`
}

function formatTime(v) {
  if (!v) return ''
  const t = new Date(v).getTime()
  if (Number.isNaN(t)) return ''
  return new Date(t).toISOString().replace('T', ' ').slice(0, 19).replace(' ', ' ')
}

function errorText(e) {
  const prefix = e?.code ? `错误码 ${e.code}` : e?.httpStatus ? `HTTP ${e.httpStatus}` : ''
  const head = [prefix, e?.traceId ? `traceId ${e.traceId}` : ''].filter(Boolean).join('，')
  return head ? `${head}：${e?.message || '操作失败'}` : e?.message || '操作失败'
}

function actionLabel(action) {
  return { ADD: '新增', DELETE: '删除', UPDATE: '修改' }[action] || action
}

function actionClass(action) {
  return { ADD: 'ok', DELETE: 'off', UPDATE: 'warn' }[action] || 'off'
}

function kindLabel(kind) {
  return { PUBLISH: '发布', ROLLBACK: '回滚' }[kind] || kind || '-'
}

async function loadStatus() {
  try {
    status.value = await fetchSensitiveWordStatus()
    // 切换请求进行中不要回填下拉框，避免用户刚选的引擎被上一次状态覆盖
    if (!switchingEngine.value) {
      engineChoice.value = status.value?.engineOverride || 'default'
    }
    // /status 已携带待发布差异，避免再请求 /pending 重复加载全量词表
    const data = status.value?.pending
    pending.added = data?.added || 0
    pending.removed = data?.removed || 0
    pending.updated = data?.updated || 0
    pending.items = data?.items || []
  } catch (e) {
    error.value = errorText(e)
  }
}

async function switchEngine() {
  switchingEngine.value = true
  engineSwitchMsg.value = ''
  engineSwitchError.value = ''
  try {
    const result = await switchSensitiveWordEngine(
      engineChoice.value === 'default' ? null : engineChoice.value
    )
    engineSwitchMsg.value = result.applied
      ? `已切到 ${result.engine}（${result.wordCount} 词，构建 ${result.buildMs}ms），其他节点 ${result.propagateSeconds}s 内收敛`
      : `覆盖已记录为 ${result.engine}，词库就绪后生效`
    checkResult.value = null
    await loadStatus()
  } catch (e) {
    engineSwitchError.value = errorText(e)
  } finally {
    switchingEngine.value = false
  }
}

async function loadList() {
  loading.value = true
  error.value = ''
  try {
    const data = await fetchSensitiveWords({
      keyword: keyword.value,
      category: category.value,
      status: statusFilter.value === '' ? '' : statusFilter.value,
      page: page.value,
      size: PAGE_SIZE
    })
    items.value = data?.items || []
    total.value = data?.total || 0
    selectedIds.value = []
  } catch (e) {
    error.value = errorText(e)
  } finally {
    loading.value = false
  }
}

async function loadCategories() {
  try {
    categories.value = (await fetchSensitiveWordCategories()) || []
  } catch {
    categories.value = []
  }
}

async function loadVersions() {
  try {
    const data = await fetchSensitiveWordVersions(0, 30)
    versions.value = data?.items || []
  } catch (e) {
    error.value = errorText(e)
  }
}

async function refreshAll() {
  await Promise.all([loadStatus(), loadList(), loadCategories(), loadVersions()])
}

function applyFilters() {
  page.value = 0
  loadList()
}

function goPage(next) {
  page.value = Math.max(0, Math.min(next, totalPages.value - 1))
  loadList()
}

function toggleAll(e) {
  selectedIds.value = e.target.checked ? items.value.map((it) => it.id) : []
}

function openCreate() {
  editingId.value = null
  Object.assign(editor, { word: '', category: 'default', status: 1, remark: '' })
  editorError.value = ''
  editorOpen.value = true
}

function openEdit(item) {
  editingId.value = item.id
  Object.assign(editor, {
    word: item.word,
    category: item.category || 'default',
    status: item.status ?? 1,
    remark: item.remark || ''
  })
  editorError.value = ''
  editorOpen.value = true
}

function closeEditor() {
  editorOpen.value = false
}

async function saveEditor() {
  if (!editor.word) {
    editorError.value = '词条不能为空'
    return
  }
  saving.value = true
  editorError.value = ''
  try {
    const payload = {
      word: editor.word,
      category: editor.category || 'default',
      status: editor.status,
      remark: editor.remark
    }
    if (editingId.value) {
      await updateSensitiveWord(editingId.value, payload)
    } else {
      await createSensitiveWord(payload)
    }
    editorOpen.value = false
    await Promise.all([loadList(), loadStatus()])
  } catch (e) {
    editorError.value = errorText(e)
  } finally {
    saving.value = false
  }
}

async function removeOne(item) {
  if (!window.confirm(`确认删除「${item.word}」？`)) return
  try {
    await deleteSensitiveWord(item.id)
    await Promise.all([loadList(), loadStatus()])
  } catch (e) {
    error.value = errorText(e)
  }
}

async function removeSelected() {
  if (selectedIds.value.length === 0) return
  if (!window.confirm(`确认删除选中的 ${selectedIds.value.length} 个词条？`)) return
  try {
    await batchDeleteSensitiveWords(selectedIds.value)
    await Promise.all([loadList(), loadStatus()])
  } catch (e) {
    error.value = errorText(e)
  }
}

async function publishNow() {
  const confirmText = allowEmpty.value
    ? '确认发布新版本（允许空词表，发布后线上将不再拦截任何词）？'
    : '确认发布新版本？发布后各节点将在数秒内热切换。'
  if (!window.confirm(confirmText)) return
  publishing.value = true
  try {
    const result = await publishSensitiveWords(publishRemark.value, allowEmpty.value)
    publishRemark.value = ''
    // 等本节点热切换完成，避免刷新后状态区短暂显示旧版本
    for (let i = 0; i < 15; i++) {
      await loadStatus()
      if ((status.value?.localVersion ?? 0) >= result.version) break
      await new Promise(resolve => setTimeout(resolve, 2000))
    }
    window.alert(`已发布 v${result.version}，共 ${result.wordCount} 条`)
    await refreshAll()
  } catch (e) {
    error.value = errorText(e)
  } finally {
    publishing.value = false
  }
}

function openImport() {
  importFile.value = null
  preview.value = null
  importMode.value = 'APPEND'
  confirmReplace.value = false
  importError.value = ''
  importOpen.value = true
}

function closeImport() {
  importOpen.value = false
}

function onFileChange(e) {
  importFile.value = e.target.files?.[0] || null
  preview.value = null
}

async function doPreview() {
  if (!importFile.value) return
  previewing.value = true
  importError.value = ''
  try {
    preview.value = await previewSensitiveWordImport(importFile.value)
  } catch (e) {
    importError.value = errorText(e)
  } finally {
    previewing.value = false
  }
}

async function doImport() {
  if (!importFile.value) return
  importing.value = true
  importError.value = ''
  try {
    const result = await importSensitiveWords(importFile.value, importMode.value, confirmReplace.value)
    window.alert(`导入完成：新增 ${result.inserted}，跳过 ${result.skipped}，非法 ${result.invalid}`)
    importOpen.value = false
    await Promise.all([loadList(), loadStatus()])
  } catch (e) {
    importError.value = errorText(e)
  } finally {
    importing.value = false
  }
}

async function doExport() {
  exporting.value = true
  try {
    const { blob } = await exportSensitiveWords()
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = 'sensitive-words.csv'
    document.body.appendChild(a)
    a.click()
    a.remove()
    URL.revokeObjectURL(url)
  } catch (e) {
    error.value = errorText(e)
  } finally {
    exporting.value = false
  }
}

async function doCheck() {
  if (!checkText.value.trim()) return
  checking.value = true
  try {
    checkResult.value = await checkSensitiveText(checkText.value)
  } catch (e) {
    error.value = errorText(e)
  } finally {
    checking.value = false
  }
}

async function showDiff(version) {
  try {
    diff.value = await fetchSensitiveWordVersionDiff(version)
    diffOpen.value = true
  } catch (e) {
    error.value = errorText(e)
  }
}

async function syncVersion(version) {
  try {
    await syncSensitiveWordVersion(version)
    window.alert(`v${version} 已重新分发到 Redis`)
    await loadStatus()
  } catch (e) {
    error.value = errorText(e)
  }
}

async function rollbackVersion(version) {
  if (!window.confirm(`确认回滚到 v${version}？会以该版本词表创建一个新版本。`)) return
  try {
    const result = await rollbackSensitiveWordVersion(version, `回滚自 v${status.value?.publishedVersion ?? ''}`)
    window.alert(`已回滚，新版本 v${result.newVersion}，共 ${result.wordCount} 条`)
    await refreshAll()
  } catch (e) {
    error.value = errorText(e)
  }
}

onMounted(() => refreshAll())
</script>

<style scoped>
.sw-sub {
  color: var(--console-muted, var(--muted));
  font-size: 13px;
  margin-top: 6px;
}

.sw-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
}

.sw-toolbar-wrap {
  flex-wrap: wrap;
  margin-bottom: 14px;
}

.sw-spacer {
  flex: 1;
}

.sw-input {
  box-sizing: border-box;
  padding: 9px 12px;
  border-radius: 10px;
  border: 1px solid var(--console-border, var(--border));
  background: var(--console-bg, var(--surface));
  color: var(--console-text, var(--text));
  font: inherit;
  font-size: 13px;
  outline: none;
}

.sw-input:focus {
  border-color: rgba(0, 0, 0, 0.18);
  box-shadow: 0 0 0 4px rgba(0, 0, 0, 0.06);
}

.sw-select {
  min-width: 130px;
}

.sw-status-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
  gap: 12px;
}

.sw-stat {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 12px 14px;
  border-radius: 12px;
  border: 1px solid var(--console-border, var(--border));
  background: color-mix(in srgb, var(--console-bg, var(--surface)) 88%, transparent);
}

.sw-stat-label {
  font-size: 12px;
  color: var(--console-muted, var(--muted));
}

.sw-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 9px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
  border: 1px solid transparent;
}

.sw-badge.ok {
  color: #1a7f37;
  background: rgba(26, 127, 55, 0.1);
  border-color: rgba(26, 127, 55, 0.25);
}

.sw-badge.warn {
  color: #9a6700;
  background: rgba(154, 103, 0, 0.12);
  border-color: rgba(154, 103, 0, 0.28);
}

.sw-badge.off {
  color: var(--console-muted, var(--muted));
  background: rgba(128, 128, 128, 0.12);
  border-color: rgba(128, 128, 128, 0.22);
}

.sw-muted {
  color: var(--console-muted, var(--muted));
  font-size: 13px;
}

.sw-error {
  color: #cf1322;
  font-size: 13px;
  margin: 8px 0;
  white-space: pre-wrap;
}

.sw-table-wrap {
  overflow-x: auto;
}

.sw-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}

.sw-table th,
.sw-table td {
  text-align: left;
  padding: 10px 12px;
  border-bottom: 1px solid var(--console-border, var(--border));
  vertical-align: middle;
}

.sw-table th {
  color: var(--console-muted, var(--muted));
  font-weight: 700;
  white-space: nowrap;
}

.sw-table tbody tr:hover {
  background: rgba(128, 128, 128, 0.05);
}

.sw-check {
  width: 34px;
}

.sw-actions {
  text-align: right;
  white-space: nowrap;
}

.sw-word {
  font-weight: 700;
  word-break: break-all;
}

.sw-ellipsis {
  max-width: 260px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sw-link {
  border: 0;
  background: transparent;
  color: var(--console-text, var(--text));
  cursor: pointer;
  font: inherit;
  font-size: 13px;
  padding: 4px 6px;
  border-radius: 6px;
}

.sw-link:hover {
  background: rgba(128, 128, 128, 0.12);
}

.sw-link.danger {
  color: #cf1322;
}

.sw-pager {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-top: 14px;
}

.sw-pager-btns {
  display: flex;
  gap: 8px;
}

.sw-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(360px, 1fr));
  gap: 16px;
  margin-bottom: 16px;
}

.sw-change-list {
  list-style: none;
  margin: 10px 0 0;
  padding: 0;
  max-height: 240px;
  overflow: auto;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.sw-change-list li {
  display: flex;
  align-items: center;
  gap: 8px;
}

.sw-publish-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 14px;
  flex-wrap: wrap;
}

.sw-publish-row .sw-input {
  flex: 1;
  min-width: 160px;
}

.sw-check {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--console-muted, var(--muted));
  white-space: nowrap;
  cursor: pointer;
}

.sw-textarea {
  width: 100%;
  box-sizing: border-box;
  resize: vertical;
  padding: 10px 12px;
  border-radius: 10px;
  border: 1px solid var(--console-border, var(--border));
  background: var(--console-bg, var(--surface));
  color: var(--console-text, var(--text));
  font: inherit;
  font-size: 13px;
  outline: none;
}

.sw-check-result {
  margin-top: 12px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.sw-filtered {
  padding: 10px 12px;
  border-radius: 10px;
  background: rgba(128, 128, 128, 0.08);
  white-space: pre-wrap;
  word-break: break-all;
  font-size: 13px;
}

.sw-modal-mask {
  position: fixed;
  inset: 0;
  z-index: 1000;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: rgba(0, 0, 0, 0.45);
}

.sw-modal {
  width: min(480px, 94vw);
  max-height: 88vh;
  overflow: auto;
  padding: 22px;
  border-radius: 16px;
  background: var(--console-bg, var(--surface));
  color: var(--console-text, var(--text));
  box-shadow: 0 24px 60px rgba(0, 0, 0, 0.28);
}

.sw-modal-wide {
  width: min(760px, 94vw);
}

.sw-modal h3 {
  margin: 0 0 16px;
}

.sw-modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 18px;
}

.sw-import-summary {
  margin: 12px 0;
  font-size: 13px;
  color: var(--console-muted, var(--muted));
}

.sw-import-rows {
  max-height: 320px;
  overflow: auto;
  border: 1px solid var(--console-border, var(--border));
  border-radius: 10px;
}

.sw-inline-check {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
}

.sw-diff-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: 16px;
}

.sw-diff-title {
  font-weight: 700;
  font-size: 13px;
}

:global([data-theme='dark']) .sw-input:focus {
  border-color: rgba(255, 255, 255, 0.18);
  box-shadow: 0 0 0 4px rgba(255, 255, 255, 0.06);
}
</style>

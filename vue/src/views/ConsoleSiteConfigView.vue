<template>
  <div class="console-page">
    <header class="console-page-header">
      <div class="console-page-title">
        <h1>站点设置</h1>
      </div>
    </header>

    <div class="console-card console-inner-card">
      <div v-if="loading" style="color: var(--console-muted, var(--muted))">加载中...</div>
      <template v-else>
        <!-- 社交链接 -->
        <div class="admin-section">
          <h2 class="admin-section-title">社交链接</h2>
          <div class="field">
            <div class="label">GitHub 主页</div>
            <input v-model="form.github_url" class="input" type="url" placeholder="https://github.com/..." />
          </div>
          <div class="field">
            <div class="label">CSDN 博客</div>
            <input v-model="form.csdn_url" class="input" type="url" placeholder="https://blog.csdn.net/..." />
          </div>
          <div class="field">
            <div class="label">牛客主页</div>
            <input v-model="form.nowcoder_url" class="input" type="url" placeholder="https://www.nowcoder.com/..." />
          </div>
          <div class="field">
            <div class="label">源码仓库</div>
            <input v-model="form.source_code_url" class="input" type="url" placeholder="https://github.com/..." />
          </div>
          <div class="field">
            <div class="label">AI 工作平台</div>
            <input v-model="form.ai_platform_url" class="input" type="url" placeholder="http://..." />
          </div>
        </div>

        <!-- 站点展示 -->
        <div class="admin-section">
          <h2 class="admin-section-title">站点展示</h2>
          <div class="field">
            <div class="label">博客名称</div>
            <input v-model="form.blog_name" class="input" type="text" placeholder="博客名称" />
          </div>
          <div class="field">
            <div class="label">首页 Hero 标题</div>
            <input v-model="form.hero_title" class="input" type="text" placeholder="标题" />
          </div>
          <div class="field">
            <div class="label">首页 Hero 副标题</div>
            <textarea v-model="form.hero_subtitle" class="textarea" rows="3" placeholder="副标题（支持 \n 换行）"></textarea>
          </div>
          <div class="field">
            <div class="label">关于页面介绍</div>
            <textarea v-model="form.about_text" class="textarea" rows="4" placeholder="关于页面介绍文字"></textarea>
          </div>
          <div class="field">
            <div class="label">默认头像 URL</div>
            <input v-model="form.default_avatar_url" class="input" type="url" placeholder="https://..." />
          </div>
          <div class="field">
            <div class="label">站点起始日期</div>
            <input v-model="form.site_start_date" class="input" type="date" />
          </div>
          <div class="field">
            <div class="label">首页 Hero 图片（16:9）</div>
            <div class="hero-image-upload-row">
              <div class="hero-image-preview" v-if="form.hero_image_url">
                <img :src="form.hero_image_url" alt="hero preview" />
              </div>
              <div v-else class="hero-image-preview hero-image-preview--empty">
                <span class="hero-image-placeholder">未设置首页图片</span>
              </div>
              <div class="hero-image-upload-actions">
                <input
                  ref="heroFileInput"
                  class="input"
                  type="file"
                  accept="image/*"
                  @change="onPickHeroImage"
                />
                <button
                  v-if="form.hero_image_url"
                  type="button"
                  class="btn"
                  style="margin-top: 6px"
                  @click="form.hero_image_url = ''"
                >
                  移除图片
                </button>
              </div>
            </div>
          </div>
        </div>

        <!-- 关于页面 -->
        <div class="admin-section">
          <h2 class="admin-section-title">关于页面</h2>
          <div class="field">
            <div class="label">求职意向</div>
            <input v-model="form.job_intention" class="input" type="text" placeholder="后端 / 全栈开发工程师" />
          </div>
          <div class="field">
            <div class="label">联系邮箱</div>
            <input v-model="form.contact_email" class="input" type="email" placeholder="you@example.com" />
          </div>
        </div>

        <!-- 开源贡献：一行一个项目，顺序即前台展示顺序 -->
        <div class="admin-section">
          <h2 class="admin-section-title">开源贡献</h2>
          <p class="oss-hint">
            前台「关于」页第二屏按此顺序逐行展示；每行的链接指向该仓库的 PR 列表。项目名为空的行保存时会被丢弃。
          </p>

          <div v-if="ossWarn" class="oss-warn">{{ ossWarn }}</div>
          <div v-if="!contributions.length" class="oss-empty">还没有条目，点下面「新增一条」开始。</div>

          <div v-for="(row, i) in contributions" :key="i" class="oss-row">
            <input v-model="row.name" class="input" type="text" placeholder="项目名，如 Apache Dubbo" />
            <select v-model="row.role" class="input">
              <option value="Contributor">Contributor</option>
              <option value="Maintainer">Maintainer</option>
            </select>
            <input
              v-model="row.url"
              class="input"
              type="url"
              placeholder="https://github.com/owner/repo/pulls?q=author%3A..."
            />
            <div class="oss-row-actions">
              <button type="button" class="btn oss-btn" :disabled="i === 0" title="上移" @click="moveRow(i, -1)">↑</button>
              <button
                type="button"
                class="btn oss-btn"
                :disabled="i === contributions.length - 1"
                title="下移"
                @click="moveRow(i, 1)"
              >
                ↓
              </button>
              <button type="button" class="btn oss-btn" title="删除" @click="removeRow(i)">✕</button>
            </div>
          </div>

          <button type="button" class="btn" style="margin-top: 10px" @click="addRow">+ 新增一条</button>
        </div>

        <!-- 页脚 -->
        <div class="admin-section">
          <h2 class="admin-section-title">页脚</h2>
          <div class="field">
            <div class="label">版权信息</div>
            <input v-model="form.footer_copyright" class="input" type="text" placeholder="© 2026 OpenBlog" />
          </div>
        </div>

        <div v-if="error" class="error" style="margin-top: 10px">{{ error }}</div>
        <div v-else-if="success" class="success" style="margin-top: 10px">{{ success }}</div>
        <button class="btn primary" style="margin-top: 14px" :disabled="saving" @click="save">
          {{ saving ? '保存中...' : '保存配置' }}
        </button>
      </template>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { fetchSiteConfig, updateSiteConfig } from '../api/site'
import { uploadMedia } from '../api/media'

const loading = ref(true)
const saving = ref(false)
const error = ref('')
const success = ref('')

const form = ref({
  github_url: '',
  csdn_url: '',
  nowcoder_url: '',
  source_code_url: '',
  ai_platform_url: '',
  blog_name: '',
  hero_title: '',
  hero_subtitle: '',
  about_text: '',
  default_avatar_url: '',
  site_start_date: '',
  hero_image_url: '',
  job_intention: '',
  contact_email: '',
  footer_copyright: '',
  // 存的是 JSON 数组字符串；编辑用下面的 contributions 行编辑器，保存前序列化回来
  about_opensource: ''
})

const heroFileInput = ref(null)

// ---------------------------------------------------------------------------
// 开源贡献行编辑器
// 表单里放结构化数组、保存时 stringify 进 form.about_opensource，
// 这样既复用上面那套「form 里有什么就存什么」的逻辑，又不用手写 JSON。
// ---------------------------------------------------------------------------
const contributions = ref([])
const ossWarn = ref('')

function addRow() {
  contributions.value.push({ name: '', role: 'Contributor', url: '' })
}

function removeRow(i) {
  contributions.value.splice(i, 1)
}

function moveRow(i, delta) {
  const j = i + delta
  if (j < 0 || j >= contributions.value.length) return
  const [row] = contributions.value.splice(i, 1)
  contributions.value.splice(j, 0, row)
}

/**
 * 读取配置里的 JSON。
 * 返回 null 表示「有值但解析不了」（调用方要提示，别让坏值被静默覆盖成空）；
 * 返回 [] 表示合法地没有条目（含未配置、后台清空过）。
 */
function parseContributions(raw) {
  if (raw == null || String(raw).trim() === '') return []
  try {
    const arr = JSON.parse(raw)
    if (!Array.isArray(arr)) return null
    return arr
      .filter((it) => it && typeof it.name === 'string')
      .map((it) => ({
        name: it.name,
        role: it.role === 'Maintainer' ? 'Maintainer' : 'Contributor',
        url: typeof it.url === 'string' ? it.url : ''
      }))
  } catch {
    return null
  }
}

async function onPickHeroImage(e) {
  const file = e.target.files?.[0]
  if (!file) return
  error.value = ''
  try {
    const resp = await uploadMedia(file)
    form.value.hero_image_url = resp.url
    if (heroFileInput.value) heroFileInput.value.value = ''
  } catch (e) {
    error.value = e.message || '上传失败'
  }
}

onMounted(async () => {
  loading.value = true
  try {
    const config = await fetchSiteConfig()
    if (config) {
      Object.keys(form.value).forEach((key) => {
        if (config[key] != null) {
          form.value[key] = String(config[key])
        }
      })
      const parsed = parseContributions(config.about_opensource)
      if (parsed === null) {
        ossWarn.value =
          '库里的开源贡献不是合法 JSON，编辑器已按空列表打开（保存会用编辑器内容覆盖原值）'
      } else {
        contributions.value = parsed
      }
    }
  } catch {
    error.value = '加载配置失败'
  }
  loading.value = false
})

async function save() {
  error.value = ''
  success.value = ''
  saving.value = true
  try {
    // 项目名为空的行是「加了一条没填」，不算有效数据，静默丢掉落库
    const kept = contributions.value
      .map((r) => ({
        name: (r.name || '').trim(),
        role: r.role === 'Maintainer' ? 'Maintainer' : 'Contributor',
        url: (r.url || '').trim()
      }))
      .filter((r) => r.name)
    contributions.value = kept
    const payload = {}
    Object.entries(form.value).forEach(([k, v]) => {
      payload[k] = (v || '').trim()
    })
    payload.about_opensource = JSON.stringify(kept)
    await updateSiteConfig(payload)
    success.value = '配置已保存'
    setTimeout(() => { success.value = '' }, 3000)
  } catch (e) {
    error.value = e.message || '保存失败'
  }
  saving.value = false
}
</script>

<style scoped>
/* ---------- 开源贡献行编辑器 ---------- */
.oss-hint {
  margin: 0 0 12px;
  font-size: 12.5px;
  line-height: 1.7;
  color: var(--console-muted, var(--muted));
}

.oss-empty {
  padding: 14px 0;
  font-size: 13px;
  color: var(--console-muted, var(--muted));
}

/* 库里 JSON 坏掉时的提示：不是错误拦截，只是让保存会覆盖原值这件事有交代 */
.oss-warn {
  margin-bottom: 12px;
  padding: 8px 12px;
  border-left: 3px solid #e0a400;
  background: rgba(224, 164, 0, 0.08);
  font-size: 12.5px;
  line-height: 1.7;
}

.oss-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 140px minmax(0, 1.6fr) auto;
  gap: 8px;
  align-items: center;
  margin-bottom: 8px;
}

.oss-row-actions {
  display: flex;
  gap: 4px;
}

/* 上/下/删按钮：比主按钮小一圈，三个挤在一格里不抢视觉 */
.oss-btn {
  min-width: 32px;
  padding: 6px 8px;
  font-size: 13px;
  line-height: 1;
}

/* 窄屏：四个元素一行排不下，项目名独占一行，其余三个挤第二行 */
@media (max-width: 720px) {
  .oss-row {
    grid-template-columns: minmax(0, 1fr) auto;
  }

  .oss-row > input:first-child {
    grid-column: 1 / -1;
  }
}
</style>

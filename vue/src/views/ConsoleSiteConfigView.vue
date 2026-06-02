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
  footer_copyright: ''
})

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
    const payload = {}
    Object.entries(form.value).forEach(([k, v]) => {
      payload[k] = (v || '').trim()
    })
    await updateSiteConfig(payload)
    success.value = '配置已保存'
    setTimeout(() => { success.value = '' }, 3000)
  } catch (e) {
    error.value = e.message || '保存失败'
  }
  saving.value = false
}
</script>

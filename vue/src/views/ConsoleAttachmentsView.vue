<template>
  <div class="console-page">
    <header class="console-page-header">
      <div class="console-page-title">
        <h1>附件</h1>
      </div>
    </header>

    <div class="console-card console-inner-card">
      <p class="console-prose">
        后端提供上传接口，文件将保存到站点媒体目录。上传成功后可获得 <code>key</code> 与访问 URL，用于文章封面与头像等引用。
      </p>

      <div class="field" style="margin-top: 16px">
        <div class="label">选择文件</div>
        <input class="input" type="file" @change="onFile" />
      </div>

      <div v-if="uploading" style="margin-top: 12px; color: var(--console-muted, var(--muted))">上传中...</div>
      <div v-if="error" class="error" style="margin-top: 12px">{{ error }}</div>
      <div v-if="result" class="success" style="margin-top: 12px">
        <div style="font-weight: 700; margin-bottom: 8px">上传成功</div>
        <div class="console-kv"><span class="console-k">key</span><code class="console-v">{{ result.key }}</code></div>
        <div v-if="result.url" class="console-kv" style="margin-top: 6px">
          <span class="console-k">url</span>
          <a :href="result.url" class="console-link" target="_blank" rel="noreferrer">{{ result.url }}</a>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { uploadMedia } from '../api/media'

const uploading = ref(false)
const error = ref('')
const result = ref(null)

async function onFile(e) {
  const file = e.target.files?.[0]
  if (!file) return
  uploading.value = true
  error.value = ''
  result.value = null
  try {
    result.value = await uploadMedia(file)
  } catch (err) {
    error.value = err?.message || '上传失败'
  } finally {
    uploading.value = false
  }
}
</script>

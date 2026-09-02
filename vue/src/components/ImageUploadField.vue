<template>
  <div class="image-field">
    <div class="image-field-main">
      <div class="image-field-preview">
        <img v-if="previewSrc && !previewBroken" :src="previewSrc" alt="图片预览" @error="previewBroken = true" />
        <span v-else class="image-field-placeholder">
          {{ previewSrc ? '图片无法加载' : '暂无图片' }}
        </span>
      </div>

      <div class="image-field-actions">
        <button type="button" class="btn btn-sm" :disabled="uploading" @click="pick">
          {{ uploading ? '上传中...' : '上传图片' }}
        </button>
        <button
          v-if="modelValue"
          type="button"
          class="btn btn-sm btn-outline-danger"
          :disabled="uploading"
          @click="emitValue('')"
        >
          移除
        </button>
      </div>
    </div>

    <p v-if="error" class="image-field-error">{{ error }}</p>

    <input
      v-if="!compact"
      :value="modelValue"
      class="input image-field-text"
      :placeholder="manualPlaceholder"
      @input="onManualInput"
    />
    <p v-if="hint" class="image-field-hint">{{ hint }}</p>

    <input
      ref="fileInput"
      type="file"
      accept="image/*"
      style="display: none"
      @change="onFile"
    />
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { uploadMedia, API_BASE } from '../api/media'

const props = defineProps({
  modelValue: { type: String, default: '' },
  /** 上传后写入媒体库的分类，如 company-logo / project-cover / project-body */
  category: { type: String, default: '' },
  hint: { type: String, default: '' },
  /** 隐藏手动粘贴输入框，仅保留上传按钮（适合封面等只想要按钮的场景） */
  compact: { type: Boolean, default: false },
  manualPlaceholder: { type: String, default: '或手动粘贴媒体 key / 完整图片链接' }
})

const emit = defineEmits(['update:modelValue'])

const fileInput = ref(null)
const uploading = ref(false)
const error = ref('')
const previewBroken = ref(false)

function emitValue(v) {
  emit('update:modelValue', v)
}

function pick() {
  fileInput.value?.click()
}

/** 兼容两种已存值：媒体 key 或完整 URL */
const previewSrc = computed(() => {
  const raw = (props.modelValue || '').trim()
  if (!raw) return ''
  if (/^https?:\/\//i.test(raw) || raw.includes('/api/v1/media/files/')) return raw
  return `${API_BASE}/api/v1/media/files/${raw}`
})

watch(previewSrc, () => {
  previewBroken.value = false
})

function onManualInput(e) {
  emitValue(e.target.value)
}

async function onFile(e) {
  const file = e.target.files?.[0]
  e.target.value = ''
  if (!file) return
  error.value = ''
  uploading.value = true
  try {
    const resp = await uploadMedia(file, props.category ? { category: props.category } : undefined)
    // 统一存媒体 key（公开侧渲染都基于 key 拼 /media/files/ 前缀）
    emitValue(resp.key)
  } catch (err) {
    error.value = err?.message || '上传失败'
  } finally {
    uploading.value = false
  }
}
</script>

<style scoped>
.image-field-main {
  display: flex;
  gap: 12px;
  align-items: flex-start;
}

.image-field-preview {
  width: 150px;
  height: 96px;
  border-radius: 10px;
  overflow: hidden;
  border: 1px solid var(--border);
  background: var(--surface);
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}

.image-field-preview img {
  width: 100%;
  height: 100%;
  object-fit: contain;
  background: #fff;
}

[data-theme='dark'] .image-field-preview img {
  background: #181a1f;
}

.image-field-placeholder {
  color: var(--muted);
  font-size: 12px;
  font-weight: 600;
}

.image-field-actions {
  display: flex;
  gap: 8px;
  padding-top: 2px;
}

.image-field-error {
  color: #b42318;
  font-size: 12px;
  margin: 8px 0 0;
}

.image-field-text {
  margin-top: 10px;
  font-size: 13px;
}

.image-field-hint {
  color: var(--muted);
  font-size: 12px;
  margin: 6px 0 0;
}
</style>

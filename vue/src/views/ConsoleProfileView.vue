<template>
  <div class="console-page">
    <header class="console-page-header">
      <div class="console-page-title">
        <h1>个人资料</h1>
      </div>
    </header>

    <div class="console-card console-inner-card">
      <div class="admin-section">
        <div class="row">
          <div class="avatar-preview">
            <img v-if="form.avatarUrl" :src="form.avatarUrl" alt="avatar" />
            <div v-else class="avatar-placeholder">avatar</div>
          </div>
          <div style="flex: 1">
            <div class="field">
              <div class="label">头像上传</div>
              <input class="input" type="file" accept="image/*" @change="onPickAvatar" />
            </div>
            <div style="color: var(--console-muted, var(--muted)); font-size: 12px; margin-top: 6px">
              上传会生成缩略图并更新头像地址
            </div>
          </div>
        </div>

        <div class="grid2">
          <div class="field">
            <div class="label">用户名</div>
            <input v-model="form.username" class="input" type="text" />
          </div>
          <div class="field">
            <div class="label">昵称</div>
            <input v-model="form.nickname" class="input" type="text" />
          </div>
        </div>

        <div class="field">
          <div class="label">个性签名</div>
          <textarea v-model="form.bio" class="textarea" rows="4"></textarea>
        </div>

        <div v-if="error" class="error" style="margin-top: 10px">{{ error }}</div>
        <div v-else-if="success" class="success" style="margin-top: 10px">{{ success }}</div>
        <button class="btn primary" style="margin-top: 14px" @click="saveProfile">保存资料</button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { fetchMe, updateMe } from '../api/admin'
import { uploadMedia } from '../api/media'

const error = ref('')
const success = ref('')

const form = ref({
  username: '',
  nickname: '',
  bio: '',
  avatarUrl: ''
})

async function loadMe() {
  const me = await fetchMe()
  form.value.username = me.username || ''
  form.value.nickname = me.nickname || ''
  form.value.bio = me.bio || ''
  form.value.avatarUrl = me.avatarUrl || ''
}

async function saveProfile() {
  error.value = ''
  success.value = ''
  try {
    await updateMe({
      username: form.value.username,
      nickname: form.value.nickname,
      bio: form.value.bio,
      avatarUrl: form.value.avatarUrl
    })
    await loadMe()
    success.value = '保存成功'
  } catch (e) {
    const apiCode = e?.code
    const httpStatus = e?.httpStatus
    const prefix = apiCode ? `错误码 ${apiCode}` : httpStatus ? `HTTP ${httpStatus}` : ''
    error.value = prefix ? `${prefix}：${e?.message || '保存失败'}` : e?.message || '保存失败'
    success.value = ''
  }
}

async function onPickAvatar(e) {
  const file = e.target.files?.[0]
  if (!file) return
  error.value = ''
  const resp = await uploadMedia(file)
  form.value.avatarUrl = resp.url
}

onMounted(async () => {
  error.value = ''
  await loadMe()
})
</script>

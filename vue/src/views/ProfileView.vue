<template>
  <div class="page-wrap">
    <div class="card profile-view-card">
      <h1 class="profile-view-title">个人中心</h1>

      <div class="profile-view-body">
        <div class="profile-view-avatar">
          <UserAvatar :url="form.avatarUrl" :size="112" />
          <label class="profile-view-upload-btn">
            更换头像
            <input type="file" accept="image/*" @change="onPickAvatar" />
          </label>
          <p v-if="uploading" class="profile-view-hint">上传中...</p>
        </div>

        <div class="profile-view-fields">
          <label class="profile-view-field">
            <span class="profile-view-label">用户名</span>
            <input class="input" v-model.trim="form.username" maxlength="32" placeholder="用户名" />
          </label>

          <label class="profile-view-field">
            <span class="profile-view-label">签名</span>
            <textarea
              class="textarea"
              v-model.trim="form.bio"
              maxlength="512"
              rows="3"
              placeholder="一句话介绍自己"
            ></textarea>
          </label>

          <p v-if="error" class="profile-view-msg profile-view-error">{{ error }}</p>
          <p v-if="saved" class="profile-view-msg profile-view-success">已保存</p>

          <div class="profile-view-actions">
            <button type="button" class="btn primary" :disabled="saving" @click="save">
              {{ saving ? '保存中...' : '保存' }}
            </button>
            <button type="button" class="btn" @click="logout">退出登录</button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import UserAvatar from '../components/UserAvatar.vue'
import { fetchMe, updateMe } from '../api/admin'
import { uploadMedia } from '../api/media'
import { clearAuth } from '../auth/session'

const router = useRouter()

const form = ref({ username: '', avatarUrl: '', bio: '' })
const error = ref('')
const saved = ref(false)
const saving = ref(false)
const uploading = ref(false)

async function loadMe() {
  try {
    const me = await fetchMe()
    form.value = {
      username: me.username || '',
      avatarUrl: me.avatarUrl || '',
      bio: me.bio || ''
    }
  } catch (e) {
    if (e.httpStatus === 401) {
      clearAuth()
      router.replace({ path: '/login', query: { redirect: '/profile' } })
    } else {
      error.value = e.message || '加载个人信息失败'
    }
  }
}

async function onPickAvatar(e) {
  const file = e.target.files?.[0]
  if (!file) return
  error.value = ''
  uploading.value = true
  try {
    const resp = await uploadMedia(file)
    form.value.avatarUrl = resp.url
  } catch (err) {
    error.value = err.message || '头像上传失败'
  } finally {
    uploading.value = false
    e.target.value = ''
  }
}

async function save() {
  const name = form.value.username.trim()
  if (!name) {
    error.value = '用户名不能为空'
    return
  }
  if (name.length < 3) {
    error.value = '用户名至少 3 个字符'
    return
  }
  error.value = ''
  saved.value = false
  saving.value = true
  try {
    await updateMe({
      username: name,
      bio: form.value.bio,
      avatarUrl: form.value.avatarUrl
    })
    saved.value = true
    window.setTimeout(() => {
      saved.value = false
    }, 2000)
  } catch (err) {
    error.value = err.message || '保存失败'
  } finally {
    saving.value = false
  }
}

function logout() {
  clearAuth()
  router.replace('/login')
}

onMounted(loadMe)
</script>

<style scoped>
.page-wrap {
  display: flex;
  justify-content: center;
  padding: 28px 16px;
}
.profile-view-card {
  width: 100%;
  max-width: 560px;
  padding: 28px;
}
.profile-view-title {
  font-size: 20px;
  font-weight: 800;
  margin: 0 0 20px;
}
.profile-view-body {
  display: flex;
  gap: 28px;
  align-items: flex-start;
}
.profile-view-avatar {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
}
.profile-view-upload-btn {
  cursor: pointer;
  font-size: 13px;
  color: var(--muted);
  border: 1px solid var(--border);
  border-radius: 6px;
  padding: 5px 12px;
}
.profile-view-upload-btn:hover {
  color: var(--text);
}
.profile-view-upload-btn input {
  display: none;
}
.profile-view-hint {
  font-size: 12px;
  color: var(--muted);
  margin: 0;
}
.profile-view-fields {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.profile-view-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.profile-view-label {
  font-size: 13px;
  font-weight: 650;
  color: var(--muted);
}
.profile-view-msg {
  margin: 0;
  font-size: 13px;
}
.profile-view-error {
  color: #d33;
}
.profile-view-success {
  color: #2a7;
}
.profile-view-actions {
  display: flex;
  gap: 12px;
  margin-top: 4px;
}
</style>

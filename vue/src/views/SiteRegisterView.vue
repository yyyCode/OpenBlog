<template>
  <div class="blog-container">
    <div class="admin-center">
      <div class="card admin-login-card site-auth-card">
        <div class="card-body" style="padding: 22px">
          <div class="site-auth-title">注册会员</div>
          <p class="site-auth-lead">注册为读者账号，用于评论与互动。发文与站点管理仍由作者或管理员在控制台操作。</p>

          <div class="field">
            <div class="label">用户名（3–32 位）</div>
            <input v-model="username" class="input" type="text" autocomplete="username" />
          </div>

          <div class="field">
            <div class="label">邮箱</div>
            <input v-model="email" class="input" type="email" autocomplete="email" />
          </div>

          <div class="field">
            <div class="label">密码（至少 6 位）</div>
            <input v-model="password" class="input" type="password" autocomplete="new-password" />
          </div>

          <div class="field">
            <div class="label">昵称（选填）</div>
            <input v-model="nickname" class="input" type="text" autocomplete="nickname" />
          </div>

          <div v-if="error" class="error">{{ error }}</div>

          <button class="btn primary" style="width: 100%; margin-top: 14px" type="button" :disabled="busy" @click="doRegister">
            {{ busy ? '提交中…' : '注册' }}
          </button>

          <div class="site-auth-footer">
            <router-link class="site-auth-link" to="/login">已有账号？去登录</router-link>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { register } from '../api/admin'
import { showMessage } from '../utils/message'

const router = useRouter()
const username = ref('')
const email = ref('')
const password = ref('')
const nickname = ref('')
const error = ref('')
const busy = ref(false)

async function doRegister() {
  error.value = ''
  const u = (username.value || '').trim()
  const em = (email.value || '').trim()
  const pw = password.value || ''
  if (u.length < 3 || u.length > 32) {
    error.value = '用户名长度需在 3–32 位之间'
    return
  }
  if (!em) {
    error.value = '请填写邮箱'
    return
  }
  if (pw.length < 6) {
    error.value = '密码至少 6 位'
    return
  }
  busy.value = true
  try {
    await register({ username: u, email: em, password: pw, nickname: nickname.value })
    showMessage('注册成功，请登录')
    router.push('/login')
  } catch (e) {
    error.value = e?.message || '注册失败'
  } finally {
    busy.value = false
  }
}
</script>

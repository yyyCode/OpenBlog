<template>
  <div class="blog-container">
    <div class="admin-center">
      <div class="card admin-login-card">
        <div class="card-body" style="padding: 22px">
          <div style="font-weight: 1000; font-size: 22px; margin-bottom: 18px">
            后台登录
          </div>

          <div class="field">
            <div class="label">账号（用户名或邮箱）</div>
            <input v-model="account" class="input" type="text" />
          </div>

          <div class="field">
            <div class="label">密码</div>
            <input v-model="password" class="input" type="password" />
          </div>

          <div v-if="error" class="error">{{ error }}</div>

          <button class="btn primary" style="width: 100%; margin-top: 14px" @click="doLogin">
            登录
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { login } from '../api/admin'

const router = useRouter()
const route = useRoute()
const account = ref('')
const password = ref('')
const error = ref('')

async function doLogin() {
  error.value = ''
  try {
    const resp = await login(account.value, password.value)
    localStorage.setItem('accessToken', resp.accessToken)
    localStorage.setItem('refreshToken', resp.refreshToken)
    const r = route.query.redirect
    const target =
      typeof r === 'string' && r.startsWith('/') && !r.startsWith('//') ? r : '/admin'
    router.push(target)
  } catch (e) {
    error.value = e?.message || '登录失败'
  }
}
</script>

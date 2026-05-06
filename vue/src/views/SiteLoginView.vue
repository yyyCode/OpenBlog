<template>
  <div class="blog-container">
    <div class="admin-center">
      <div class="card admin-login-card site-auth-card">
        <div class="card-body" style="padding: 22px">
          <div class="site-auth-title">会员登录</div>
          <p class="site-auth-lead">登录后可发表评论、点赞与收藏。站点管理员请使用控制台入口。</p>

          <div class="field">
            <div class="label">账号（用户名或邮箱）</div>
            <input v-model="account" class="input" type="text" autocomplete="username" />
          </div>

          <div class="field">
            <div class="label">密码</div>
            <input v-model="password" class="input" type="password" autocomplete="current-password" />
          </div>

          <div v-if="error" class="error">{{ error }}</div>

          <button class="btn primary" style="width: 100%; margin-top: 14px" type="button" @click="doLogin">
            登录
          </button>

          <div class="site-auth-footer">
            <router-link class="site-auth-link" to="/register">没有账号？去注册</router-link>
            <span class="site-auth-dot">·</span>
            <router-link class="site-auth-link" to="/console/login">管理员控制台登录</router-link>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { login } from '../api/admin'
import { getAccessTokenRole } from '../auth/session'

const router = useRouter()
const route = useRoute()
const account = ref('')
const password = ref('')
const error = ref('')

function safeSiteRedirect(raw) {
  if (typeof raw !== 'string') return '/'
  const t = raw.trim()
  if (!t.startsWith('/') || t.startsWith('//')) return '/'
  if (t.startsWith('/console')) {
    const role = getAccessTokenRole()
    if (role !== 'ADMIN' && role !== 'AUTHOR') return '/'
  }
  return t
}

async function doLogin() {
  error.value = ''
  try {
    const resp = await login(account.value, password.value)
    localStorage.setItem('accessToken', resp.accessToken)
    localStorage.setItem('refreshToken', resp.refreshToken)
    const r = route.query.redirect
    const target = safeSiteRedirect(typeof r === 'string' ? r : '')
    router.push(target)
  } catch (e) {
    error.value = e?.message || '登录失败'
  }
}
</script>

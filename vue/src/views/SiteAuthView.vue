<template>
  <div class="blog-container">
    <div class="admin-center">
      <div class="card admin-login-card site-auth-card">
        <div class="card-body" style="padding: 22px">
          <div class="site-auth-tabs" role="tablist" aria-label="登录或注册">
            <button
              type="button"
              role="tab"
              class="tab"
              :class="{ active: tab === 'login' }"
              :aria-selected="tab === 'login'"
              @click="setTab('login')"
            >
              登录
            </button>
            <button
              type="button"
              role="tab"
              class="tab"
              :class="{ active: tab === 'register' }"
              :aria-selected="tab === 'register'"
              @click="setTab('register')"
            >
              注册
            </button>
          </div>

          <div v-show="tab === 'login'" role="tabpanel">
            <div class="site-auth-title">账号登录</div>
            <p class="site-auth-lead">登录后可发表评论、点赞与收藏。站点管理员请使用控制台入口。</p>

            <div class="field">
              <div class="label">账号（用户名或邮箱）</div>
              <input v-model="account" class="input" type="text" autocomplete="username" />
            </div>

            <div class="field">
              <div class="label">密码</div>
              <input v-model="password" class="input" type="password" autocomplete="current-password" />
            </div>

            <div v-if="loginError" class="error">{{ loginError }}</div>

            <button class="btn primary" style="width: 100%; margin-top: 14px" type="button" @click="doLogin">
              登录
            </button>

            <div class="site-auth-footer">
              <router-link class="site-auth-link" to="/console/login">管理员控制台登录</router-link>
            </div>
          </div>

          <div v-show="tab === 'register'" role="tabpanel">
            <div class="site-auth-title">账号注册</div>
            <p class="site-auth-lead">为避免恶意注册账号，注册的账号需要管理员审核通过。</p>

            <div class="field">
              <div class="label">用户名（3–32 位）</div>
              <input v-model="username" class="input" type="text" autocomplete="username" />
            </div>

            <div class="field">
              <div class="label">邮箱</div>
              <input v-model="email" class="input" type="email" autocomplete="email" inputmode="email" />
              <p class="site-auth-email-hint">{{ ALLOWED_EMAIL_MESSAGE }}</p>
            </div>

            <div class="field">
              <div class="label">密码（至少 6 位）</div>
              <input v-model="regPassword" class="input" type="password" autocomplete="new-password" />
            </div>

            <div v-if="registerError" class="error">{{ registerError }}</div>

            <button
              class="btn primary"
              style="width: 100%; margin-top: 14px"
              type="button"
              :disabled="busy"
              @click="doRegister"
            >
              {{ busy ? '提交中…' : '注册' }}
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { login, register } from '../api/admin'
import { getAccessTokenRole } from '../auth/session'
import { showMessage } from '../utils/message'
import { ALLOWED_EMAIL_MESSAGE, isAllowedMailboxEmail } from '../utils/allowedEmail'

const router = useRouter()
const route = useRoute()

const tab = ref('login')

const account = ref('')
const password = ref('')
const loginError = ref('')

const username = ref('')
const email = ref('')
const regPassword = ref('')
const registerError = ref('')
const busy = ref(false)

function syncTabFromRoute() {
  tab.value = route.query.tab === 'register' ? 'register' : 'login'
}

function setTab(next) {
  tab.value = next
  const q = { ...route.query }
  if (next === 'register') {
    q.tab = 'register'
  } else {
    delete q.tab
  }
  router.replace({ path: '/login', query: q })
}

watch(() => route.fullPath, syncTabFromRoute)

onMounted(() => {
  syncTabFromRoute()
})

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
  loginError.value = ''
  try {
    const resp = await login(account.value, password.value)
    localStorage.setItem('accessToken', resp.accessToken)
    localStorage.setItem('refreshToken', resp.refreshToken)
    const r = route.query.redirect
    const target = safeSiteRedirect(typeof r === 'string' ? r : '')
    router.push(target)
  } catch (e) {
    loginError.value = e?.message || '登录失败'
  }
}

async function doRegister() {
  registerError.value = ''
  const u = (username.value || '').trim()
  const em = (email.value || '').trim()
  const pw = regPassword.value || ''
  if (u.length < 3 || u.length > 32) {
    registerError.value = '用户名长度需在 3–32 位之间'
    return
  }
  if (!em) {
    registerError.value = '请填写邮箱'
    return
  }
  if (!isAllowedMailboxEmail(em)) {
    registerError.value = ALLOWED_EMAIL_MESSAGE
    return
  }
  if (pw.length < 6) {
    registerError.value = '密码至少 6 位'
    return
  }
  busy.value = true
  try {
    await register({ username: u, email: em, password: pw })
    showMessage('注册成功，请等待管理员审核通过后再登录')
    regPassword.value = ''
    setTab('login')
  } catch (e) {
    registerError.value = e?.message || '注册失败'
  } finally {
    busy.value = false
  }
}
</script>

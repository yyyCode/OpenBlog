<template>
  <div class="blog-container">
    <div class="admin-center">
      <div class="card admin-login-card site-auth-card">
        <div class="card-body" style="padding: 22px">
          <div class="site-auth-tabs" role="tablist" aria-label="登录、注册或修改密码">
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
            <button
              type="button"
              role="tab"
              class="tab"
              :class="{ active: tab === 'change-password' }"
              :aria-selected="tab === 'change-password'"
              @click="setTab('change-password')"
            >
              修改密码
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
              <button type="button" class="site-auth-link site-auth-link-btn" @click="setTab('change-password')">
                忘记密码？修改密码
              </button>
              <span class="site-auth-dot">·</span>
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

          <div v-show="tab === 'change-password'" role="tabpanel">
            <div class="site-auth-title">修改密码</div>
            <p class="site-auth-lead">输入注册时使用的邮箱，验证通过后即可设置新密码。</p>

            <div class="field">
              <div class="label">注册邮箱</div>
              <input v-model="changeEmail" class="input" type="email" autocomplete="email" inputmode="email" />
              <p class="site-auth-email-hint">{{ ALLOWED_EMAIL_MESSAGE }}</p>
            </div>

            <div class="field">
              <div class="label">新密码（至少 6 位）</div>
              <input v-model="newPassword" class="input" type="password" autocomplete="new-password" />
            </div>

            <div class="field">
              <div class="label">确认新密码</div>
              <input v-model="confirmPassword" class="input" type="password" autocomplete="new-password" />
            </div>

            <div v-if="changePasswordError" class="error">{{ changePasswordError }}</div>

            <button
              class="btn primary"
              style="width: 100%; margin-top: 14px"
              type="button"
              :disabled="busy"
              @click="doChangePassword"
            >
              {{ busy ? '提交中…' : '确认修改' }}
            </button>

            <div class="site-auth-footer">
              <button type="button" class="site-auth-link site-auth-link-btn" @click="setTab('login')">
                返回登录
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { login, register, changePassword } from '../api/admin'
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
const changeEmail = ref('')
const newPassword = ref('')
const confirmPassword = ref('')
const changePasswordError = ref('')
const busy = ref(false)

function syncTabFromRoute() {
  const qTab = route.query.tab
  if (qTab === 'register') {
    tab.value = 'register'
  } else if (qTab === 'change-password') {
    tab.value = 'change-password'
  } else {
    tab.value = 'login'
  }
}

function setTab(next) {
  tab.value = next
  const q = { ...route.query }
  if (next === 'register') {
    q.tab = 'register'
  } else if (next === 'change-password') {
    q.tab = 'change-password'
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

async function doChangePassword() {
  changePasswordError.value = ''
  const em = (changeEmail.value || '').trim()
  const pw = newPassword.value || ''
  const confirm = confirmPassword.value || ''
  if (!em) {
    changePasswordError.value = '请填写注册邮箱'
    return
  }
  if (!isAllowedMailboxEmail(em)) {
    changePasswordError.value = ALLOWED_EMAIL_MESSAGE
    return
  }
  if (pw.length < 6) {
    changePasswordError.value = '新密码至少 6 位'
    return
  }
  if (pw !== confirm) {
    changePasswordError.value = '两次输入的新密码不一致'
    return
  }
  busy.value = true
  try {
    await changePassword(em, pw)
    showMessage('密码修改成功，请使用新密码登录')
    newPassword.value = ''
    confirmPassword.value = ''
    setTab('login')
  } catch (e) {
    changePasswordError.value = e?.message || '修改密码失败'
  } finally {
    busy.value = false
  }
}
</script>

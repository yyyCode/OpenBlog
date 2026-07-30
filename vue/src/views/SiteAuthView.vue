<template>
  <div class="auth-split-bg">
    <div class="auth-split-card">
      <div class="auth-form">
        <!-- Underline Tabs -->
        <div class="auth-tabs" role="tablist" aria-label="登录、注册或修改密码">
          <button
            type="button"
            role="tab"
            class="auth-tab"
            :class="{ active: tab === 'login' }"
            :aria-selected="tab === 'login'"
            @click="setTab('login')"
          >登录</button>
          <button
            type="button"
            role="tab"
            class="auth-tab"
            :class="{ active: tab === 'register' }"
            :aria-selected="tab === 'register'"
            @click="setTab('register')"
          >注册</button>
          <button
            type="button"
            role="tab"
            class="auth-tab"
            :class="{ active: tab === 'change-password' }"
            :aria-selected="tab === 'change-password'"
            @click="setTab('change-password')"
          >修改密码</button>
        </div>

        <!-- Login Tab -->
        <div v-show="tab === 'login'" role="tabpanel">
          <div class="auth-field">
            <div class="auth-label">账号</div>
            <input
              v-model="account"
              class="auth-input"
              type="text"
              autocomplete="username"
              placeholder="用户名或邮箱"
              @keyup.enter="doLogin"
            />
          </div>
          <div class="auth-field">
            <div class="auth-label">密码</div>
            <input
              v-model="password"
              class="auth-input"
              type="password"
              autocomplete="current-password"
              placeholder="输入密码"
              @keyup.enter="doLogin"
            />
          </div>
          <div v-if="loginError" class="auth-alert auth-alert--error">
            <span>⚠️</span> {{ loginError }}
          </div>
          <button class="auth-submit" type="button" @click="doLogin">登 录</button>
          <div class="auth-footer">
            <button type="button" class="auth-link" @click="setTab('change-password')">忘记密码？</button>
            <span class="auth-muted">管理员入口 &rarr; <router-link class="auth-link" to="/console/login">控制台登录</router-link></span>
          </div>
        </div>

        <!-- Register Tab -->
        <div v-show="tab === 'register'" role="tabpanel">
          <div class="auth-field">
            <div class="auth-label">用户名</div>
            <input
              v-model="username"
              class="auth-input"
              type="text"
              autocomplete="username"
              placeholder="3&ndash;32 位"
            />
          </div>
          <div class="auth-field">
            <div class="auth-label">邮箱</div>
            <input
              v-model="email"
              class="auth-input"
              type="email"
              autocomplete="email"
              inputmode="email"
              placeholder="支持 QQ / 163 / Gmail 等"
            />
            <div class="auth-hint">{{ ALLOWED_EMAIL_MESSAGE }}</div>
          </div>
          <div class="auth-field">
            <div class="auth-label">密码</div>
            <input
              v-model="regPassword"
              class="auth-input"
              type="password"
              autocomplete="new-password"
              placeholder="至少 6 位"
              @keyup.enter="doRegister"
            />
          </div>
          <div v-if="registerError" class="auth-alert auth-alert--error">
            <span>⚠️</span> {{ registerError }}
          </div>
          <button
            class="auth-submit"
            type="button"
            :disabled="busy"
            @click="doRegister"
          >{{ busy ? '提交中…' : '注 册' }}</button>
          <div class="auth-terms">注册即表示同意服务条款</div>
        </div>

        <!-- Change Password Tab -->
        <div v-show="tab === 'change-password'" role="tabpanel">
          <div class="auth-field">
            <div class="auth-label">注册邮箱</div>
            <input
              v-model="changeEmail"
              class="auth-input"
              type="email"
              autocomplete="email"
              inputmode="email"
              placeholder="输入注册时使用的邮箱"
            />
            <div class="auth-hint">{{ ALLOWED_EMAIL_MESSAGE }}</div>
          </div>
          <div class="auth-field">
            <div class="auth-label">新密码</div>
            <input
              v-model="newPassword"
              class="auth-input"
              type="password"
              autocomplete="new-password"
              placeholder="至少 6 位"
            />
          </div>
          <div class="auth-field">
            <div class="auth-label">确认新密码</div>
            <input
              v-model="confirmPassword"
              class="auth-input"
              type="password"
              autocomplete="new-password"
              placeholder="再输入一次"
              @keyup.enter="doChangePassword"
            />
          </div>
          <div
            v-if="changePasswordError"
            class="auth-alert"
            :class="changePasswordOk ? 'auth-alert--success' : 'auth-alert--error'"
          >
            <span>{{ changePasswordOk ? '✓' : '⚠️' }}</span> {{ changePasswordError }}
          </div>
          <button
            class="auth-submit"
            type="button"
            :disabled="busy"
            @click="doChangePassword"
          >{{ busy ? '提交中…' : '确认修改' }}</button>
          <div class="auth-footer" style="justify-content: center">
            <button type="button" class="auth-link" @click="setTab('login')">返回登录</button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { login, register, changePassword } from '../api/admin'
import { getAccessTokenRole } from '../auth/session'
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
const changePasswordOk = ref(false)

const busy = ref(false)

const brandSubtitle = computed(() => {
  switch (tab.value) {
    case 'register':
      return '加入 OpenBlog\n创建账号即可发表评论、点赞与收藏'
    case 'change-password':
      return '重置密码\n输入注册邮箱验证后设置新密码'
    default:
      return '一个简洁的写作与分享空间\n记录技术笔记与生活思考'
  }
})

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

function storeSession(accessToken, refreshToken) {
  localStorage.setItem('accessToken', accessToken)
  localStorage.setItem('refreshToken', refreshToken)
}

async function doLogin() {
  loginError.value = ''
  try {
    const resp = await login(account.value, password.value)
    storeSession(resp.accessToken, resp.refreshToken)
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
    registerError.value = '用户名长度需在 3-32 位之间'
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
    const resp = await register({ username: u, email: em, password: pw })
    storeSession(resp.accessToken, resp.refreshToken)
    regPassword.value = ''
    const r = route.query.redirect
    const target = safeSiteRedirect(typeof r === 'string' ? r : '')
    router.push(target)
  } catch (e) {
    registerError.value = e?.message || '注册失败'
  } finally {
    busy.value = false
  }
}

async function doChangePassword() {
  changePasswordError.value = ''
  changePasswordOk.value = false
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
    changePasswordOk.value = true
    changePasswordError.value = '密码修改成功，请使用新密码登录'
    newPassword.value = ''
    confirmPassword.value = ''
    setTimeout(() => setTab('login'), 1500)
  } catch (e) {
    changePasswordOk.value = false
    changePasswordError.value = e?.message || '修改密码失败'
  } finally {
    busy.value = false
  }
}
</script>

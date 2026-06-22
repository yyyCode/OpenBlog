# Site Auth Feishu-Style Redesign — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Redesign the login/register page (`SiteAuthView.vue`) from centered card + glassmorphism to a Feishu-style left-right split layout with clean blue theme and auto-login on registration.

**Architecture:** Modify two files — `blog.css` for new style classes and `SiteAuthView.vue` for template + logic. The page keeps its existing route (`/login?tab=`) and API calls (`admin.js`), only visual presentation and registration flow behavior change. Dark mode support via `[data-theme='dark']` selectors.

**Tech Stack:** Vue 3 Composition API, Vue Router, plain CSS (no UI framework)

---

## File Structure

| File | Action | Responsibility |
|------|--------|---------------|
| `vue/src/assets/blog.css` | Modify | New `.auth-split-*` CSS classes for Feishu layout, updated `.auth-feishu-*` form controls, dark mode variants |
| `vue/src/views/SiteAuthView.vue` | Rewrite | Complete template (split layout + 3 tabs) and script (register auto-login, error states) |

---

### Task 1: Add Feishu-style CSS classes to blog.css

**Files:**
- Modify: `vue/src/assets/blog.css` (append new classes at end of file)

- [ ] **Step 1: Add auth split layout CSS**

Append the following CSS block to the end of `vue/src/assets/blog.css`:

```css
/* ================
   Auth split layout (Feishu style)
   ================ */
.auth-split-bg {
  min-height: 100svh;
  background: #f0f2f5;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
}

[data-theme='dark'] .auth-split-bg {
  background: #0f1114;
}

.auth-split-card {
  display: flex;
  width: 780px;
  max-width: 100%;
  background: #fff;
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 2px 20px rgba(0, 0, 0, 0.06);
}

[data-theme='dark'] .auth-split-card {
  background: #1a1a1a;
  box-shadow: 0 2px 20px rgba(0, 0, 0, 0.35);
}

/* Brand panel (left) */
.auth-brand {
  width: 340px;
  flex-shrink: 0;
  background: linear-gradient(160deg, #3370ff, #5b47b0);
  color: #fff;
  padding: 56px 40px;
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.auth-brand-logo {
  width: 48px;
  height: 48px;
  background: rgba(255, 255, 255, 0.18);
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  margin-bottom: 24px;
}

.auth-brand-title {
  font-size: 26px;
  font-weight: 700;
  letter-spacing: -0.02em;
  margin-bottom: 12px;
}

.auth-brand-sub {
  font-size: 14px;
  line-height: 1.8;
  opacity: 0.82;
  white-space: pre-line;
}

.auth-brand-footer {
  font-size: 12px;
  opacity: 0.5;
  margin-top: 40px;
}

/* Form panel (right) */
.auth-form {
  flex: 1;
  min-width: 0;
  padding: 48px 44px;
}

/* Underline tabs */
.auth-tabs {
  display: flex;
  gap: 32px;
  border-bottom: 1px solid #ebecef;
  margin-bottom: 28px;
}

[data-theme='dark'] .auth-tabs {
  border-bottom-color: #2a2a2e;
}

.auth-tab {
  padding: 0 0 12px;
  font-size: 15px;
  cursor: pointer;
  border: none;
  background: none;
  font-weight: 400;
  color: #8f959e;
  margin-bottom: -1px;
  border-bottom: 2px solid transparent;
  transition: color 0.2s;
  font-family: inherit;
}

.auth-tab:hover {
  color: #646a73;
}

[data-theme='dark'] .auth-tab:hover {
  color: #bbb;
}

.auth-tab.active {
  font-weight: 600;
  color: #1f2329;
  border-bottom-color: #3370ff;
}

[data-theme='dark'] .auth-tab.active {
  color: #e5e7eb;
}

/* Form fields */
.auth-field {
  margin-bottom: 20px;
}

.auth-label {
  font-size: 13px;
  color: #646a73;
  font-weight: 500;
  margin-bottom: 6px;
}

[data-theme='dark'] .auth-label {
  color: #9ca3af;
}

.auth-input {
  width: 100%;
  padding: 10px 14px;
  border: 1px solid #dee0e3;
  border-radius: 8px;
  font-size: 14px;
  outline: none;
  box-sizing: border-box;
  background: #f5f6f7;
  color: #1f2329;
  transition: border-color 0.2s, background 0.2s;
  font-family: inherit;
}

.auth-input::placeholder {
  color: #bbb;
}

.auth-input:focus {
  border-color: #3370ff;
  background: #fff;
}

[data-theme='dark'] .auth-input {
  background: #2a2a2e;
  border-color: #3a3a3e;
  color: #e5e7eb;
}

[data-theme='dark'] .auth-input:focus {
  background: #1f1f22;
  border-color: #3370ff;
}

/* Submit button */
.auth-submit {
  width: 100%;
  padding: 10px 0;
  border-radius: 8px;
  background: #3370ff;
  color: #fff;
  font-weight: 600;
  font-size: 15px;
  border: none;
  cursor: pointer;
  letter-spacing: 0.5px;
  transition: background 0.2s;
  font-family: inherit;
}

.auth-submit:hover {
  background: #2860e0;
}

.auth-submit:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

[data-theme='dark'] .auth-submit:hover {
  background: #4a7fff;
}

/* Footer links */
.auth-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 18px;
  font-size: 13px;
}

.auth-link {
  color: #3370ff;
  cursor: pointer;
  border: none;
  background: none;
  padding: 0;
  font: inherit;
  text-decoration: none;
}

.auth-link:hover {
  text-decoration: underline;
}

.auth-muted {
  color: #8f959e;
}

[data-theme='dark'] .auth-muted {
  color: #6b7280;
}

/* Error / Success alerts */
.auth-alert {
  padding: 10px 14px;
  border-radius: 8px;
  font-size: 13px;
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 16px;
}

.auth-alert--error {
  background: #fff2f0;
  border: 1px solid #ffccc7;
  color: #cf1322;
}

[data-theme='dark'] .auth-alert--error {
  background: #2c1618;
  border-color: #5c2022;
  color: #f5c6cb;
}

.auth-alert--success {
  background: #f6ffed;
  border: 1px solid #b7eb8f;
  color: #389e0d;
}

[data-theme='dark'] .auth-alert--success {
  background: #162c18;
  border-color: #2c5c20;
  color: #c6f5ca;
}

/* Email hint */
.auth-hint {
  margin-top: 6px;
  font-size: 12px;
  color: #bbb;
}

[data-theme='dark'] .auth-hint {
  color: #666;
}

/* Auth terms */
.auth-terms {
  text-align: center;
  font-size: 13px;
  color: #8f959e;
  margin-top: 16px;
}

[data-theme='dark'] .auth-terms {
  color: #6b7280;
}

/* Responsive: mobile */
@media (max-width: 640px) {
  .auth-brand {
    display: none;
  }

  .auth-split-card {
    width: 100%;
  }

  .auth-form {
    padding: 32px 24px;
  }

  .auth-tabs {
    gap: 20px;
  }
}
```

- [ ] **Step 2: Commit CSS changes**

```bash
git add vue/src/assets/blog.css
git commit -m "style: add Feishu-style auth split layout CSS classes"
```

---

### Task 2: Rewrite SiteAuthView.vue

**Files:**
- Modify: `vue/src/views/SiteAuthView.vue` (replace entire file)

- [ ] **Step 1: Write the complete new SiteAuthView.vue**

```vue
<template>
  <div class="auth-split-bg">
    <div class="auth-split-card">
      <!-- Left: Brand Panel -->
      <div class="auth-brand">
        <div class="auth-brand-logo">📝</div>
        <div class="auth-brand-title">OpenBlog</div>
        <div class="auth-brand-sub">{{ brandSubtitle }}</div>
        <div class="auth-brand-footer">&copy; 2026 OpenBlog</div>
      </div>

      <!-- Right: Form Area -->
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
          >{{ busy ? '提交中&hellip;' : '注 册' }}</button>
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
          >{{ busy ? '提交中&hellip;' : '确认修改' }}</button>
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
```

- [ ] **Step 2: Commit Vue component**

```bash
git add vue/src/views/SiteAuthView.vue
git commit -m "feat: redesign SiteAuthView with Feishu-style split layout and auto-login"
```

---

### Task 3: Final verification

- [ ] **Step 1: Verify Vue build succeeds**

```bash
cd vue && npm run build 2>&1 | tail -10
```
Expected: Build completes without errors.

- [ ] **Step 2: Commit any build fixups**

```bash
git add -A && git commit -m "chore: verify build after auth page redesign"
```

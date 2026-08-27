# 个人信息功能（Header 头像按钮 + 个人中心页）实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 Header 登录/注册位放一个头像缩略图按钮（未登录显示人形图标），点击进入 `/profile` 个人中心页查看并编辑自己的用户名 / 头像 / 签名。

**Architecture:** 纯前端改动，复用现有后端接口（`GET/PUT /api/v1/users/me`、`POST /api/v1/media/upload`）。新增一个可复用 `UserAvatar` 组件（有头像显示图片、无头像显示人形 SVG），Header 与个人页共用；新增 `/profile` 路由 + 未登录守卫。项目无前端测试基建，验证方式为 `npm run build` + 手动回归。

**Tech Stack:** Vue 3（`<script setup>` + Composition API）、Vue Router 4、Vite。样式复用 `vue/src/assets/blog.css` 的 `--text/--muted/--border/--surface/--card` CSS 变量与 `.card/.btn/.input/.textarea` 类。

**关键约定：**
- `request()`（`api/http.js`）直接返回后端 `ApiResponse.data`，所以 `fetchMe()`/`updateMe()` 返回 MeResponse 对象，`uploadMedia()` 返回 `{ url, ... }`。
- MeResponse 字段：`userId, username, avatarUrl, bio, role`（**无 email**，个人页不展示邮箱）。
- 登录页 `SiteAuthView` 已支持 `?redirect=` 登录成功后回跳。

---

### Task 1: 新建 UserAvatar 组件

**Files:**
- Create: `vue/src/components/UserAvatar.vue`

- [ ] **Step 1: 创建组件文件**

`vue/src/components/UserAvatar.vue` 完整内容：

```vue
<template>
  <span class="user-avatar" :style="{ width: size + 'px', height: size + 'px' }">
    <img v-if="url" class="user-avatar-img" :src="url" alt="avatar" />
    <svg v-else class="user-avatar-fallback" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
      <path d="M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z" />
    </svg>
  </span>
</template>

<script setup>
defineProps({
  url: { type: String, default: '' },
  size: { type: Number, default: 32 }
})
</script>

<style scoped>
.user-avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 999px;
  overflow: hidden;
  flex-shrink: 0;
  background: var(--surface);
  border: 1px solid var(--border);
}
.user-avatar-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.user-avatar-fallback {
  width: 62%;
  height: 62%;
  color: var(--muted);
}
</style>
```

- [ ] **Step 2: 提交**

```bash
git add vue/src/components/UserAvatar.vue
git commit -m "feat(web): add reusable UserAvatar component (img or person-icon fallback)"
```

---

### Task 2: Header 登录/注册位改为头像按钮

**Files:**
- Modify: `vue/src/components/BlogHeader.vue`

- [ ] **Step 1: 模板里替换 `.site-nav-auth` 块**

把 `vue/src/components/BlogHeader.vue` 模板中这段（约 93-103 行）：

```html
        <div class="site-nav-auth site-nav-auth-trailing" aria-label="账户">
          <template v-if="me">
            <span class="site-nav-user-name" :title="displayName">{{ displayName }}</span>
            <button type="button" class="site-nav-link site-nav-link-btn site-nav-pill-outline" @click="logout">
              退出
            </button>
          </template>
          <router-link v-else to="/login" class="site-nav-link site-nav-pill-solid site-nav-auth-entry">
            登录/注册
          </router-link>
        </div>
```

替换为：

```html
        <div class="site-nav-auth site-nav-auth-trailing" aria-label="账户">
          <button
            type="button"
            class="site-nav-auth-avatar-btn"
            :title="me ? displayName : '登录/注册'"
            @click="goAuth"
          >
            <UserAvatar :url="me ? me.avatarUrl : ''" :size="32" />
          </button>
        </div>
```

- [ ] **Step 2: `<script setup>` 调整**

在 `vue/src/components/BlogHeader.vue` 的 `<script setup>` 中：

1. 顶部 import 加一行：
```js
import UserAvatar from './UserAvatar.vue'
```
2. `import { clearAuth, getStoredAccessToken, isJwtExpired, isLikelyJwt } from '../auth/session'` 改为
   `import { getStoredAccessToken, isJwtExpired, isLikelyJwt } from '../auth/session'`（移除 `clearAuth`，删除 logout 后不再使用）。
3. 把 `displayName` computed 保留（仍用于按钮 `:title`）。
4. 删除整个 `logout()` 函数（不再使用）。
5. 新增：
```js
function goAuth() {
  if (me.value) {
    router.push('/profile')
  } else {
    router.push('/login')
  }
}
```

- [ ] **Step 3: 加按钮样式（scoped style）**

在 `vue/src/components/BlogHeader.vue` 文件末尾（`</script>` 之后）追加：

```vue
<style scoped>
.site-nav-auth-avatar-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0;
  border: none;
  background: none;
  cursor: pointer;
  border-radius: 999px;
  line-height: 0;
}
.site-nav-auth-avatar-btn:hover {
  opacity: 0.85;
}
</style>
```

- [ ] **Step 4: 构建验证**

Run: `cd vue && npm run build`
Expected: 构建成功，无报错。

- [ ] **Step 5: 提交**

```bash
git add vue/src/components/BlogHeader.vue
git commit -m "feat(web): replace header auth entry with avatar button (→ /profile or /login)"
```

---

### Task 3: 个人中心页 ProfileView

**Files:**
- Create: `vue/src/views/ProfileView.vue`

- [ ] **Step 1: 创建页面组件**

`vue/src/views/ProfileView.vue` 完整内容：

```vue
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
```

- [ ] **Step 2: 构建验证**

Run: `cd vue && npm run build`
Expected: 构建成功（ProfileView 尚未接入路由，不影响构建）。

- [ ] **Step 3: 提交**

```bash
git add vue/src/views/ProfileView.vue
git commit -m "feat(web): add profile page (view/edit username, avatar, bio)"
```

---

### Task 4: 注册 /profile 路由 + 未登录守卫

**Files:**
- Modify: `vue/src/router/index.js`

- [ ] **Step 1: 导入 ProfileView**

在 `vue/src/router/index.js` 的 import 区（`ForumListView` 之后）加一行：

```js
import ProfileView from '../views/ProfileView.vue'
```

- [ ] **Step 2: 注册路由**

在 routes 数组里，`/login` 路由之后加：

```js
  {
    path: '/profile',
    name: 'profile',
    component: ProfileView
  },
```

- [ ] **Step 3: 加守卫**

把 `router.beforeEach` 回调开头（`if (!to.path.startsWith('/console')) return true` 之前）改为：

```js
router.beforeEach((to) => {
  // 个人中心：需登录，未登录跳登录页（登录成功后经 redirect 回跳）
  if (to.path === '/profile') {
    if (!isConsoleSessionValid()) {
      clearAuth()
      return { path: '/login', query: { redirect: '/profile' } }
    }
    return true
  }

  if (!to.path.startsWith('/console')) return true
  ...
```

- [ ] **Step 4: 构建验证**

Run: `cd vue && npm run build`
Expected: 构建成功，无报错。

- [ ] **Step 5: 提交**

```bash
git add vue/src/router/index.js
git commit -m "feat(web): add /profile route with auth guard"
```

---

### Task 5: 全量验证

- [ ] **Step 1: 构建**

Run: `cd vue && npm run build`
Expected: 成功，无警告/报错。

- [ ] **Step 2: 手动回归**（本地 `npm run dev`，或部署后测线上）

| # | 场景 | 预期 |
|---|---|---|
| 1 | 未登录访问首页 | Header 右侧显示圆形人形图标 |
| 2 | 点击人形图标 | 跳 `/login` |
| 3 | 登录成功 | 回跳 `/profile`（若从图标进入）或原页面；Header 变为头像缩略图 |
| 4 | `/profile` 修改用户名 / 签名 → 保存 | 提示「已保存」；Header 与侧边栏 ProfileCard 名字同步 |
| 5 | 点「更换头像」选图片 | 头像预览即时更新；保存后全站头像更新 |
| 6 | 用户名改成已存在名字（如与另一用户相同） | 内联提示「用户名已存在」，输入保留 |
| 7 | 直接访问 `/profile`（未登录） | 自动跳 `/login?redirect=/profile` |
| 8 | 点「退出登录」 | 回 `/login`，Header 恢复人形图标 |

- [ ] **Step 3: 更新设计文档状态**

在 `docs/superpowers/specs/2026-08-28-user-profile-page-design.md` 顶部状态行追加「✅ 已实现（2026-08-28）」。

- [ ] **Step 4: 提交**

```bash
git add docs/superpowers/specs/2026-08-28-user-profile-page-design.md
git commit -m "docs: mark user-profile-page spec as implemented"
```

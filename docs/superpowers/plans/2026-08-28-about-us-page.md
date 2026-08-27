# 关于我们页面实现计划（顶部导航调整 + 独立 `/about-us` 页面 + 抽屉移除）

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 顶部导航「源码」外链改为「关于我们」跳新独立页 `/about-us`（展示站点作者 yyycode 的头像/昵称/签名/简介/社交链接），头像按钮与它交换位置；右侧悬浮按钮「个人信息」与挂件抽屉整体移除。

**Architecture:** 纯前端改动。数据复用现有后端接口（匿名 `GET /api/v1/profile` 返回站点作者）与 siteConfig（github_url/csdn_url/nowcoder_url/default_avatar_url）。新增 `fetchOwnerProfile()`（始终匿名，确保显示作者而非当前登录用户）、`AboutUsView.vue`、`/about-us` 路由；删除 `WidgetsDrawer.vue`，清理 App.vue / RightDock 的抽屉接线。项目无前端测试基建，验证方式为 `npm run build` + 手动回归。

**Tech Stack:** Vue 3（`<script setup>` + Composition API）、Vue Router 4、Vite。样式复用 `vue/src/assets/blog.css` 的 `--text/--muted/--border/--surface/--card` CSS 变量与 `.card/.profile-avatar-wrap/.profile-name/.profile-signature/.profile-menu/.profile-menu-item/.links-row/.icon-link` 全局类。

**关键约定：**
- `request()`（`api/http.js`）不带 `withAuth` 时即匿名请求，返回 `ApiResponse.data`。`fetchOwnerProfile()` 就是匿名 `request('/api/v1/profile')`，返回 `PublicProfileResponse`：`userId, username, avatarUrl, bio`。
- siteConfig 经 `provide('siteConfig', siteConfig)`（App.vue）注入，**是 ref**，取值用 `siteConfig.value.xxx`。
- `UserAvatar` 组件 props：`url`（String，默认 `''`）、`size`（Number，默认 32）。有 url 显示圆形图片，无则人形 SVG。本文档不直接用 UserAvatar（用全局 `.profile-avatar` 大图），AboutUsView 需要**自己处理头像回退**。
- 全局类 `.profile-avatar-wrap`（112px 圆形容器，`justify-self: center`）在 flex 列布局里靠 `align-items: center` 居中；`.profile-name`/`.profile-signature`/`.profile-menu`/`.profile-menu-item`/`.links-row`/`.icon-link` 样式见 blog.css（ProfileCard 同款）。

---

### Task 1: api/profile.js 新增 fetchOwnerProfile()

**Files:**
- Modify: `vue/src/api/profile.js`

- [ ] **Step 1: 在文件末尾追加函数**

在 `vue/src/api/profile.js` 末尾追加：

```js

export function fetchOwnerProfile() {
  // 关于我们页：始终匿名请求，确保返回站点作者（而非当前登录用户）
  return request(`/api/v1/profile`)
}
```

- [ ] **Step 2: 构建验证**

Run: `cd vue && npm run build`
Expected: 构建成功，无报错。

- [ ] **Step 3: 提交**

```bash
git add vue/src/api/profile.js
git commit -m "feat(web): add fetchOwnerProfile (anonymous /api/v1/profile) for about-us page"
```

---

### Task 2: 新建 AboutUsView 页面

**Files:**
- Create: `vue/src/views/AboutUsView.vue`

- [ ] **Step 1: 创建页面组件**

`vue/src/views/AboutUsView.vue` 完整内容：

```vue
<template>
  <div class="page-wrap">
    <div class="card about-us-card">
      <div class="profile-avatar-wrap">
        <img class="profile-avatar" :src="avatarUrl" alt="avatar" />
      </div>

      <div class="profile-name">{{ name }}</div>
      <div class="profile-signature">{{ signature }}</div>

      <div class="profile-menu">
        <router-link class="profile-menu-item" to="/all">
          全部文章
        </router-link>
        <router-link class="profile-menu-item" to="/changelog">
          更新日志
        </router-link>
      </div>

      <div class="about-us-intro">
        <p class="about-us-intro-text">
          你好，我是 yyycode，这个站点的作者。这里记录我在开发与学习中的经历、踩坑和思考，欢迎通过下面的方式联系我交流。
        </p>
      </div>

      <div class="links-row">
        <a class="icon-link" :href="githubUrl" target="_blank" rel="noreferrer">
          GitHub
        </a>
        <a class="icon-link" :href="csdnUrl" target="_blank" rel="noreferrer">
          CSDN
        </a>
        <a class="icon-link" :href="nowcoderUrl" target="_blank" rel="noreferrer">
          牛客
        </a>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, inject, onMounted, ref } from 'vue'
import { fetchOwnerProfile } from '../api/profile'

const siteConfig = inject('siteConfig')

const owner = ref(null)

const avatarUrl = computed(() => {
  const a = owner.value?.avatarUrl
  if (a) return a
  return (
    (siteConfig.value && siteConfig.value.default_avatar_url) ||
    'https://via.placeholder.com/120x120.png?text=OpenBlog'
  )
})
const name = computed(() => (owner.value && owner.value.username) || '—')
const signature = computed(() => (owner.value && owner.value.bio) || '平凡的一枚程序员')

const githubUrl = computed(() =>
  (siteConfig.value && siteConfig.value.github_url) || 'https://github.com/yyyCode'
)
const csdnUrl = computed(() =>
  (siteConfig.value && siteConfig.value.csdn_url) || 'https://blog.csdn.net/2301_80044822'
)
const nowcoderUrl = computed(() =>
  (siteConfig.value && siteConfig.value.nowcoder_url) || 'https://www.nowcoder.com/users/597303882'
)

onMounted(async () => {
  try {
    owner.value = await fetchOwnerProfile()
  } catch {
    owner.value = null
  }
})
</script>

<style scoped>
.page-wrap {
  display: flex;
  justify-content: center;
  padding: 28px 16px;
}
.about-us-card {
  width: 100%;
  max-width: 520px;
  padding: 28px 18px 20px;
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
}
.about-us-intro {
  margin-top: 26px;
}
.about-us-intro-text {
  margin: 0;
  color: var(--muted);
  font-size: 14px;
  line-height: 1.9;
}
.about-us-card .links-row {
  margin-top: 22px;
}
</style>
```

- [ ] **Step 2: 构建验证**

Run: `cd vue && npm run build`
Expected: 构建成功（AboutUsView 尚未接入路由，不影响构建）。

- [ ] **Step 3: 提交**

```bash
git add vue/src/views/AboutUsView.vue
git commit -m "feat(web): add about-us page (site owner avatar/name/bio/intro/social links)"
```

---

### Task 3: 注册 /about-us 路由

**Files:**
- Modify: `vue/src/router/index.js`

- [ ] **Step 1: 导入 AboutUsView**

在 `vue/src/router/index.js` 的 import 区，`import AboutView from '../views/AboutView.vue'`（第 11 行）之后加一行：

```js
import AboutUsView from '../views/AboutUsView.vue'
```

- [ ] **Step 2: 注册路由**

在 routes 数组里 `/about` 路由（第 52-55 行）之后加：

```js
  {
    path: '/about-us',
    name: 'aboutUs',
    component: AboutUsView
  },
```

- [ ] **Step 3: 构建验证**

Run: `cd vue && npm run build`
Expected: 构建成功，无报错。

- [ ] **Step 4: 提交**

```bash
git add vue/src/router/index.js
git commit -m "feat(web): add /about-us route"
```

---

### Task 4: BlogHeader 导航重排（源码→关于我们 + 头像按钮换位）

**Files:**
- Modify: `vue/src/components/BlogHeader.vue`

- [ ] **Step 1: 替换「源码」外链 + 头像按钮块**

把 `vue/src/components/BlogHeader.vue` 模板中这段（当前约 77-102 行，即「反馈」`router-link` 之后的两块：`<a ...>源码</a>` 与 `<div class="site-nav-auth ...">…</div>`）：

```html
        <a
          class="site-nav-link site-nav-link-sm-hide"
          :href="(siteConfig && siteConfig.source_code_url) || 'https://github.com/yyyCode/OpenBlog.git'"
          target="_blank"
          rel="noopener noreferrer"
        >
          <span class="site-nav-ico" aria-hidden="true">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
              <path
                d="M12 .5C5.73.5.75 5.67.75 12.09c0 5.15 3.28 9.52 7.83 11.06.57.11.78-.25.78-.55 0-.27-.01-1.17-.02-2.12-3.18.71-3.85-1.39-3.85-1.39-.52-1.36-1.28-1.72-1.28-1.72-1.05-.74.08-.73.08-.73 1.16.08 1.77 1.23 1.77 1.23 1.03 1.8 2.7 1.28 3.36.98.1-.77.4-1.28.73-1.57-2.54-.3-5.21-1.3-5.21-5.79 0-1.28.44-2.33 1.16-3.15-.12-.29-.5-1.48.11-3.08 0 0 .95-.31 3.11 1.2.9-.26 1.86-.39 2.82-.39.96 0 1.92.13 2.82.39 2.16-1.51 3.11-1.2 3.11-1.2.61 1.6.23 2.79.11 3.08.72.82 1.16 1.87 1.16 3.15 0 4.5-2.68 5.49-5.23 5.79.41.36.78 1.07.78 2.17 0 1.57-.02 2.83-.02 3.22 0 .3.21.67.79.55 4.55-1.54 7.83-5.91 7.83-11.06C23.25 5.67 18.27.5 12 .5z"
              />
            </svg>
          </span>
          源码
        </a>

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

替换为（头像按钮在前、「关于我们」`router-link` 在后）：

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

        <router-link to="/about-us" class="site-nav-link site-nav-link-sm-hide" active-class="active">
          <span class="site-nav-ico" aria-hidden="true">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <circle cx="12" cy="12" r="9" />
              <path d="M12 8h.01" />
              <path d="M11 12h1v5h1" />
            </svg>
          </span>
          关于我们
        </router-link>
```

- [ ] **Step 2: 移除失效的 emit 声明**

`vue/src/components/BlogHeader.vue` 的 `<script setup>` 中删除这一行（`toggle-widgets` 从未被 emit，抽屉移除后 App.vue 也不监听它）：

```js
defineEmits(['toggle-widgets'])
```

- [ ] **Step 3: 构建验证**

Run: `cd vue && npm run build`
Expected: 构建成功，无报错。

- [ ] **Step 4: 提交**

```bash
git add vue/src/components/BlogHeader.vue
git commit -m "feat(web): swap header source-code link to about-us nav link"
```

---

### Task 5: RightDock 移除「个人信息」按钮

**Files:**
- Modify: `vue/src/components/RightDock.vue`

- [ ] **Step 1: 删除第一个按钮与 emit**

删除 `vue/src/components/RightDock.vue` 模板中第一个按钮（当前约 3-11 行，「个人信息」那个，带 `@click="emit('toggle-profile')"`）：

```html
    <button type="button" class="right-dock-btn" @click="emit('toggle-profile')" :aria-label="'打开个人信息'">
      <span class="right-dock-ico" aria-hidden="true">
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" />
          <circle cx="12" cy="7" r="4" />
        </svg>
      </span>
      <span class="right-dock-text">个人信息</span>
    </button>

```

同时在 `<script setup>` 中把这一行（当前第 34 行）：

```js
const emit = defineEmits(['toggle-profile'])
```

改为删除整行（`emit` 不再被使用）。

- [ ] **Step 2: 构建验证**

Run: `cd vue && npm run build`
Expected: 构建成功，无报错（App.vue 的 `@toggle-profile` 监听在 Task 6 移除，此时仍存在但无对应 emit，不报错）。

- [ ] **Step 3: 提交**

```bash
git add vue/src/components/RightDock.vue
git commit -m "refactor(web): remove profile drawer button from right dock"
```

---

### Task 6: 移除挂件抽屉 + 清理 App.vue

**Files:**
- Delete: `vue/src/components/WidgetsDrawer.vue`
- Modify: `vue/src/App.vue`

- [ ] **Step 1: 删除 WidgetsDrawer**

```bash
git rm vue/src/components/WidgetsDrawer.vue
```

- [ ] **Step 2: 重写 App.vue**

把 `vue/src/App.vue` 完整替换为：

```vue
<script setup>
import { computed, onMounted, provide, ref } from 'vue'
import { useRoute } from 'vue-router'
import BlogHeader from './components/BlogHeader.vue'
import WelcomeGate from './components/WelcomeGate.vue'
import BackToTop from './components/BackToTop.vue'
import RightDock from './components/RightDock.vue'
import Live2dCharacter from './components/Live2dCharacter.vue'
import { postSiteVisit, fetchSiteConfig } from './api/site'

const route = useRoute()
const isConsole = computed(() => route.path.startsWith('/console'))
const siteConfig = ref({})

// 提供 siteConfig 给所有子组件
provide('siteConfig', siteConfig)

onMounted(() => {
  postSiteVisit().catch(() => {})
})

onMounted(async () => {
  // 加载站点配置
  try {
    siteConfig.value = await fetchSiteConfig()
  } catch {
    siteConfig.value = {}
  }
})
</script>

<template>
  <div class="blog-app">
    <WelcomeGate v-if="!isConsole" />
    <BlogHeader v-if="!isConsole" />
    <RightDock v-if="!isConsole" />
    <BackToTop v-if="!isConsole" />
    <Live2dCharacter v-if="!isConsole" />
    <router-view />
    <footer v-if="!isConsole" class="site-footer">
      <div class="site-footer-inner">
        <span>{{ (siteConfig && siteConfig.footer_copyright) || '© 2026 OpenBlog' }}</span>
        <span class="site-footer-sep">|</span>
        <a class="site-footer-icp" href="https://beian.miit.gov.cn/" target="_blank" rel="noopener noreferrer">粤ICP备2026033788号</a>
      </div>
    </footer>
  </div>
</template>
```

（相比原文件：移除 `WidgetsDrawer` import 与渲染节点、`@toggle-profile="toggleWidgets"`、`@toggle-widgets="toggleWidgets"`、`fetchPublicProfile` import、`watch` import、`widgetsOpen`/`toggleWidgets`/`profile`/`loadProfile` 及 `watch` 监听、onMounted 中的 `loadProfile()` 调用。）

- [ ] **Step 3: 构建验证**

Run: `cd vue && npm run build`
Expected: 构建成功，无报错（关键：`WidgetsDrawer` 删除后无残留引用）。

- [ ] **Step 4: 提交**

```bash
git add -A vue/src/App.vue vue/src/components/WidgetsDrawer.vue
git commit -m "refactor(web): remove widgets drawer and its App wiring entirely"
```

---

### Task 7: 全量验证 + 更新 spec 状态

- [ ] **Step 1: 构建**

Run: `cd vue && npm run build`
Expected: 成功，无警告/报错。

- [ ] **Step 2: 确认无残留引用**

Run:
```bash
cd vue && grep -rn "WidgetsDrawer" src || echo "no references"
cd vue && grep -rn "toggle-widgets\|toggle-profile\|toggleWidgets\|loadProfile\|widgetsOpen" src || echo "no references"
```
Expected: 两处都输出 `no references`（`fetchPublicProfile` 仅剩 `api/profile.js` 定义本身，无消费方属正常，可保留）。

- [ ] **Step 3: 手动回归**（本地 `npm run dev`，或部署后测线上）

| # | 场景 | 预期 |
|---|---|---|
| 1 | 顶部导航 | 顺序：首页/文章/项目推荐/求职导航/论坛/反馈/头像按钮/关于我们；无「源码」外链 |
| 2 | 未登录点「关于我们」 | 进入 `/about-us`，显示作者头像/昵称/签名/简介/GitHub·CSDN·牛客 链接 |
| 3 | 登录普通读者账号访问 `/about-us` | 仍显示站点作者 yyycode（而非读者自己） |
| 4 | 点头像按钮 | 未登录跳 `/login`；登录后进 `/profile` |
| 5 | 右侧悬浮按钮 | 只剩「深浅色」「公告」，无「个人信息」；抽屉不再出现 |
| 6 | 搜索页（`/search`）侧栏 | ProfileCard / BlogInfoCard 仍正常显示 |
| 7 | 直接访问 `/about-us` 页面内刷新 | 正常渲染（路由已注册，无 404） |

- [ ] **Step 4: 更新设计文档状态**

在 `docs/superpowers/specs/2026-08-28-about-us-page-design.md` 顶部状态行（第 3 行）追加「✅ 已实现（2026-08-28）」。

- [ ] **Step 5: 提交**

```bash
git add docs/superpowers/specs/2026-08-28-about-us-page-design.md
git commit -m "docs: mark about-us-page spec as implemented"
```

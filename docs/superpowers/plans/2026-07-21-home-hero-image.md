# 首页 Hero 图片功能 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在首页顶部增加 16:9 独立图片展示区存放网站主旨视觉图，并在后台站点设置页提供上传更换入口

**Architecture:** 复用现有 `site_config` key-value 机制存储图片 URL，`uploadMedia` 接口上传，`siteConfig` provide/inject 传递至首页渲染。零新增后端接口。

**Tech Stack:** Vue 3 (Composition API), Spring Boot, MySQL

---

### Task 1: 数据库添加初始配置行

**Files:**
- Modify: `OpenBlog-business/src/main/resources/sql/site-config.sql`

- [ ] **Step 1: 在 site-config.sql 中新增 `hero_image_url` 初始数据**

在 `INSERT IGNORE INTO site_config` 的 values 列表末尾追加一行：

```sql
('hero_image_url', ''),
```

- [ ] **Step 2: Commit**

```bash
git add OpenBlog-business/src/main/resources/sql/site-config.sql
git commit -m "feat: add hero_image_url site config key"
```

---

### Task 2: 后台站点设置页增加 Hero 图片上传控件

**Files:**
- Modify: `vue/src/views/ConsoleSiteConfigView.vue`

- [ ] **Step 1: 在 form 对象中添加 `hero_image_url` 字段**

找到第 94-107 行的 `form` 对象，在 `site_start_date` 后面追加：

```js
hero_image_url: '',
```

- [ ] **Step 2: 在模板「站点展示」区块添加上传控件**

在 `site_start_date` 字段后面（约第 66 行附近）、`保存配置` 按钮上方，插入以下内容：

```html
<div class="field">
  <div class="label">首页 Hero 图片（16:9）</div>
  <div class="hero-image-upload-row">
    <div class="hero-image-preview" v-if="form.hero_image_url">
      <img :src="form.hero_image_url" alt="hero preview" />
    </div>
    <div v-else class="hero-image-preview hero-image-preview--empty">
      <span class="hero-image-placeholder">未设置首页图片</span>
    </div>
    <div class="hero-image-upload-actions">
      <input
        ref="heroFileInput"
        class="input"
        type="file"
        accept="image/*"
        @change="onPickHeroImage"
      />
      <button
        v-if="form.hero_image_url"
        type="button"
        class="btn"
        style="margin-top: 6px"
        @click="form.hero_image_url = ''"
      >
        移除图片
      </button>
    </div>
  </div>
</div>
```

- [ ] **Step 3: 添加上传处理函数和 import**

在 `<script setup>` 中找到 `onPickAvatar` 函数（约第 86 行），在其附近添加 `onPickHeroImage` 和 `uploadMedia` import：

先确认 `uploadMedia` 已在文件顶部导入，如果没有，添加：

```js
import { uploadMedia } from '../api/media'
```

然后在 `onPickAvatar` 之后添加：

```js
const heroFileInput = ref(null)

async function onPickHeroImage(e) {
  const file = e.target.files?.[0]
  if (!file) return
  error.value = ''
  try {
    const resp = await uploadMedia(file)
    form.value.hero_image_url = resp.url
    // 清除 file input 以便重复选择同一文件
    if (heroFileInput.value) heroFileInput.value.value = ''
  } catch (e) {
    error.value = e.message || '上传失败'
  }
}
```

- [ ] **Step 4: Commit**

```bash
git add vue/src/views/ConsoleSiteConfigView.vue
git commit -m "feat: add hero image upload control in site settings"
```

---

### Task 3: 首页新增 Hero 图片展示区

**Files:**
- Modify: `vue/src/views/HomeView.vue`

- [ ] **Step 1: 在 Hero 文字区域上方插入图片区块**

找到 `HomeView.vue` 第 3-8 行，在 `<section class="home-hero">` 的 `<div class="home-hero-inner">` 之前插入：

```html
<div v-if="heroImageUrl" class="home-hero-image-wrap">
  <img class="home-hero-image" :src="heroImageUrl" alt="网站主旨图" />
</div>
```

修改后的模板结构应如下：

```html
<section class="home-hero">
  <!-- 新增：Hero 图片 -->
  <div v-if="heroImageUrl" class="home-hero-image-wrap">
    <img class="home-hero-image" :src="heroImageUrl" alt="网站主旨图" />
  </div>
  <div class="home-hero-inner">
    <h1 class="home-hero-title">{{ heroTitleLines[0] || '' }}<br />{{ heroTitleLines[1] || '' }}</h1>
    <p class="home-hero-sub" v-html="heroSubtitleHtml"></p>
  </div>
</section>
```

- [ ] **Step 2: 添加 `heroImageUrl` 计算属性**

在 `<script setup>` 中，在 `heroTitle` 计算属性之前（约第 106 行），添加：

```js
const heroImageUrl = computed(() => {
  return (siteConfig && siteConfig.value && siteConfig.value.hero_image_url) || ''
})
```

- [ ] **Step 3: Commit**

```bash
git add vue/src/views/HomeView.vue
git commit -m "feat: display hero image on homepage"
```

---

### Task 4: 添加 Hero 图片样式

**Files:**
- Modify: `vue/src/assets/blog.css`

- [ ] **Step 1: 在 `.home-hero` 相关样式区域添加图片样式**

在现有 `.home-hero` 样式块后面（约第 426 行，`@media` 之后），追加：

```css
/* 首页 Hero 图片 */
.home-hero-image-wrap {
  width: 100%;
  max-width: 720px;
  border-radius: 10px;
  overflow: hidden;
  background: var(--placeholder-tint);
  aspect-ratio: 16 / 9;
  margin-bottom: 32px;
}

.home-hero-image {
  width: 100%;
  height: 100%;
  display: block;
  object-fit: cover;
}
```

- [ ] **Step 2: 确认自适应样式**

检查 `@media (max-width: 680px)` 中是否需要微调：
- `.home-hero-image-wrap` 不需要额外调整，因为 `width: 100%` 已自适应
- 圆角和间距在移动端表现良好

- [ ] **Step 3: 提交**

```bash
git add vue/src/assets/blog.css
git commit -m "style: add hero image styles"
```

---

### Task 5: Checkstyle / 验证与冒烟测试

**Files:**
- Modify: none（验证性操作）

- [ ] **Step 1: 验证前端构建**

```bash
cd vue && npx vite build 2>&1 | tail -20
```

预期结果：无报错，构建成功，输出 `dist/` 目录

- [ ] **Step 2: 验证 SQL 语法**

确认 SQL 插入语句语法正确：`('hero_image_url', '')` 末尾无遗漏逗号。

- [ ] **Step 3: 功能验证 checklist**

| 检查项 | 预期 |
|--------|------|
| 无图时首页不显示图片区域 | `.home-hero-image-wrap` 不渲染 |
| 有图时显示 16:9 图片 | `aspect-ratio: 16/9` 生效 |
| 后台设置页显示预览 | 上传后预览图可见 |
| 移除图片功能可清空 | 点击"移除图片"后 `hero_image_url` 清空 |
| 保存配置后刷新首页 | 图片展示与后台设置一致 |

- [ ] **Step 4: 提交最终验证

```bash
git add -A
git commit -m "feat: add homepage hero image with admin upload control"
```

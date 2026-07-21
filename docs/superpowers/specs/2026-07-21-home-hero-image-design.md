# 首页 Hero 图片功能设计

## 概述
在首页顶部（导航栏下方）增加一个 16:9 比例的独立图片展示区，用于展示网站主旨/理念的视觉图。后台提供上传更换入口。

## 涉及页面
- **前台首页** — `vue/src/views/HomeView.vue`
- **后台站点设置** — `vue/src/views/ConsoleSiteConfigView.vue`
- **样式** — `vue/src/assets/blog.css`

## 数据方案
利用现有 `site_config` 表，新增配置键：
| key | value 示例 | 说明 |
|-----|-----------|------|
| `hero_image_url` | `https://.../hero.png` | 首页 Hero 图片 URL，空串表示无图 |

后台通过现有 `PUT /api/v1/site/config` 保存，前台通过 `siteConfig.hero_image_url` 读取。  
图片上传复用现有 `POST /api/v1/media/upload` 接口（`src/api/media.js` 的 `uploadMedia`）。

## 首页布局变化

```
[BlogHeader 导航栏]
┌─────────────────────────────────────┐
│     16:9 Hero Banner 图片           │  ← 新增
│   (网站主旨/理念视觉图)              │
│   有图时显示图片，无图时不占位        │
└─────────────────────────────────────┘
[Home Hero 标题 + 副标题]              ← 保留现有
[精选文章]
[最新文章]
[订阅区]
```

图片区样式要求：
- 16:9 宽高比（`aspect-ratio: 16/9`）
- 全宽显示（与内容区同宽，max-width: 720px 居中对齐，或与 Hero 文字对齐）
- 圆角 10px
- 图片 `object-fit: cover`
- 无图时不渲染该区域

## 后台配置页面
在 ConsoleSiteConfigView.vue 的「站点展示」区块增加「首页 Hero 图片」行：
- 当前图片预览（16:9 缩略图，圆角）
- 文件选择 `<input type="file" accept="image/*">`
- 上传后自动填充 URL 并刷新预览
- 有图时显示预览 + 更换按钮，无图时显示占位文字

## 实现步骤
1. 在 `site-config.sql` 中新增 `hero_image_url` 初始数据行
2. `ConsoleSiteConfigView.vue` — 添加表单字段 `hero_image_url` + 文件上传控件
3. `HomeView.vue` — 在 Hero 文字上方插入图片区，条件渲染
4. `blog.css` — 新增 `.home-hero-image` 样式类

## 涉及文件清单
| 文件 | 改动 |
|------|------|
| `vue/src/views/HomeView.vue` | 新增 `heroImageUrl` 计算属性 + 图片模板 |
| `vue/src/views/ConsoleSiteConfigView.vue` | 新增 `hero_image_url` 字段 + 上传控件 |
| `vue/src/assets/blog.css` | 新增 `.home-hero-image` 相关样式 |
| `OpenBlog-business/src/main/resources/sql/site-config.sql` | 新增初始数据行 |

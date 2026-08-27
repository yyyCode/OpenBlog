# 关于我们页面：顶部导航调整 + 独立 `/about-us` 页面

日期：2026-08-28
状态：已确认（用户拍板：源码链接改为「关于我们」并与头像按钮交换位置；新增独立 `/about-us` 页而非小页面；页面内容含作者卡基础项 + 个人简介段落 + 联系方式/社交链接；右侧抽屉整体移除）✅ 已实现（2026-08-28）

## 背景与目标

站点顶部导航目前有：`首页 | 文章 | 项目推荐 | 求职导航 | 论坛 | 反馈 | 源码(外链) | 头像按钮`。其中「源码」是一个跳 GitHub 仓库的外部链接；「个人信息」入口位于右侧悬浮按钮 → 抽屉（ProfileCard + BlogInfoCard）。

目标：
1. 顶部导航把「源码」外链改为**「关于我们」导航项**（跳站内新页面），并与头像按钮**交换位置**。
2. 新增独立整页 `/about-us`，内容为站点作者 yyycode 的自我介绍（即原抽屉 ProfileCard 的「关于我」内容 + 个人简介段落 + 联系方式/社交链接）。
3. 右侧悬浮按钮「个人信息」与挂件抽屉（WidgetsDrawer）**整体移除**。

**范围：纯前端。** 后端零改动；数据全部复用现有接口与 siteConfig。

## 后端现状（已具备，直接复用）

| 能力 | 接口 | 说明 |
|---|---|---|
| 公开资料 | `GET /api/v1/profile` | **匿名调用时返回站点作者**（最近更新的 ACTIVE AUTHOR，回退第一个 AUTHOR，再回退第一个用户）；带 JWT 时返回当前登录用户。返回 `PublicProfileResponse`：`userId, username, avatarUrl, bio`。关于我们页需要**始终匿名**请求，确保访客/登录读者看到的都是作者本人 |
| 站点配置 | siteConfig（`inject('siteConfig')`，App.vue 统一加载） | 含 `github_url, csdn_url, nowcoder_url, default_avatar_url, blog_name, source_code_url` 等 |

`request()`（`api/http.js`）不带 `withAuth` 时即匿名请求，直接返回 `ApiResponse.data`。

## 改动清单

| 文件 | 改动 |
|---|---|
| `vue/src/components/BlogHeader.vue` | 导航重排：「源码」外链 `<a>` → 改为「关于我们」`router-link`（跳 `/about-us`），移到最右端；头像按钮移到「关于我们」之前（原「源码」位置） |
| `vue/src/views/AboutUsView.vue` | **新建**。关于我们整页 |
| `vue/src/api/profile.js` | 新增 `fetchOwnerProfile()`：始终匿名 `request('/api/v1/profile')` |
| `vue/src/router/index.js` | 新增 `/about-us` 路由（`/about` 保留不动） |
| `vue/src/components/WidgetsDrawer.vue` | **删除**（整抽屉移除） |
| `vue/src/components/RightDock.vue` | 移除「个人信息」按钮及 `toggle-profile` emit，保留「深浅色」「公告」 |
| `vue/src/App.vue` | 清理抽屉相关：移除 `WidgetsDrawer` import/渲染、`@toggle-profile` 监听、BlogHeader 的 `@toggle-widgets` 监听、`widgetsOpen`/`toggleWidgets`、`profile`/`loadProfile`（全部只为抽屉服务） |

**保留不动**：`ProfileCard.vue`（搜索页 SearchResultsView 仍用）、`BlogInfoCard.vue`（同上）、`PublicLayout.vue`（死代码，无引用）、`ConsoleSiteConfigView.vue`（`source_code_url` 配置表单保留，导航不再使用该字段）。

## 交互与数据流

### 顶部导航（BlogHeader.vue）

调整后顺序：`首页 | 文章 | 项目推荐 | 求职导航 | 论坛 | 反馈 | [头像按钮] [关于我们]`

- **关于我们**：`router-link to="/about-us"`，替换原「源码」外链 `<a>`（`site-nav-link site-nav-link-sm-hide` 类保留，小屏隐藏）；图标从 GitHub 换成 info 圆形图标。
- **头像按钮**：从最右端移到「关于我们」之前（原「源码」位置）。点击行为不变：登录 → `/profile`；未登录 → `/login`。
- 视觉上头像按钮 + 关于我们位于导航右侧一组；`site-nav-auth-trailing` 的右对齐行为由实现者按现有 CSS 调整（最终效果：两个入口靠右、其余导航项靠左）。

### 关于我们页（AboutUsView.vue）

独立整页（`page-wrap` 居中 + `.card`，复用 `vue/src/assets/blog.css` 的 CSS 变量）。结构从上到下：

1. **大头像**：`UserAvatar`（`size` 约 112），`owner.avatarUrl`，回退 `siteConfig.default_avatar_url`，再回退占位图。
2. **昵称**：`owner.username`（回退「—」）。
3. **签名**：`owner.bio`（回退「平凡的一枚程序员」）。
4. **菜单**：全部文章（`/all`）、更新日志（`/changelog`）——沿用原 ProfileCard 的卡片基础项。
5. **个人简介段落**：静态文案（可编辑）。默认草稿：
   > 你好，我是 yyycode，这个站点的作者。这里记录我在开发与学习中的经历、踩坑和思考，欢迎通过下面的方式联系我交流。
6. **联系方式 + 社交链接**：GitHub / CSDN / 牛客，`siteConfig.github_url` / `csdn_url` / `nowcoder_url`，回退值与 ProfileCard 一致（`https://github.com/yyyCode`、`https://blog.csdn.net/2301_80044822`、`https://www.nowcoder.com/users/597303882`）。

**数据源**：`onMounted` 调 `fetchOwnerProfile()`（`request('/api/v1/profile')` 匿名）回填 `owner`；siteConfig 走 `inject('siteConfig')`。页面不依赖登录态。

### 抽屉与悬浮钮移除

- `WidgetsDrawer.vue` 文件删除；App.vue 不再渲染它。
- RightDock 删除第一个按钮（个人信息）与 `defineEmits(['toggle-profile'])`。
- App.vue 删除：`widgetsOpen` ref、`toggleWidgets()`、`profile` ref、`loadProfile()`、`watch(route.fullPath, loadProfile)`、onMounted 中的 `loadProfile()` 调用、`WidgetsDrawer` import 与模板节点、`@toggle-profile="toggleWidgets"`（RightDock）、`@toggle-widgets="toggleWidgets"`（BlogHeader）。保留 `postSiteVisit()` 与 `fetchSiteConfig()` 逻辑。

## 错误处理

| 场景 | 表现 |
|---|---|
| `fetchOwnerProfile()` 网络/接口失败 | 页面正常渲染，作者区用回退值（占位头像、「—」昵称、回退签名）；社交链接仍正常显示。不报错、不跳转 |
| 头像为空 | `UserAvatar` 显示人形兜底图标 |

## 验证

1. `cd vue && npm run build` 通过（无编译/引用错误——特别是删文件后无残留引用）。
2. 手动回归：
   - 顶部导航顺序：反馈之后依次是头像按钮、「关于我们」，无「源码」外链。
   - 未登录点「关于我们」→ 进入 `/about-us`，显示作者 yyycode 的头像/昵称/签名/简介/社交链接。
   - 登录普通读者账号访问 `/about-us` → 仍显示作者 yyycode（而非读者自己）。
   - 点头像按钮 → 未登录跳 `/login`，登录后进 `/profile`。
   - 右侧悬浮按钮：只剩深浅色 + 公告；「个人信息」抽屉不再出现。
   - 搜索页侧栏 ProfileCard / BlogInfoCard 仍正常显示。

## 不做（YAGNI）

- 不加邮箱等联系方式字段（siteConfig 无 email 字段，需后端改动，本期纯前端）。
- 不改后端、不加接口、不加表。
- 不动 `/about` 页、不动 `PublicLayout.vue`（死代码）、不改 `SearchResultsView`。
- 不给「公告」悬浮按钮接新功能（本期只是随抽屉改动顺带保留现状）。

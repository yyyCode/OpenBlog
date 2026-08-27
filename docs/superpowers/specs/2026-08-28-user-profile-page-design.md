# 个人信息功能：Header 头像按钮 + 个人中心页

日期：2026-08-28
状态：已确认（用户拍板：用 username 作展示名；点击头像直接进个人页；不加「我的文章」列表）

## 背景与目标

站点目前缺「个人信息」入口：Header 登录/注册位只有文字按钮，登录后只显示用户名 + 退出，无法查看/编辑自己的资料。
目标：在 Header 的登录/注册位置改为**头像缩略图按钮**（未登录显示默认人形图标），点击进入个人中心页查看并编辑个人信息（用户名 / 头像 / 签名）。

**范围：纯前端。** 后端已完整支持，零后端改动。

## 后端现状（已具备，直接复用）

| 能力 | 接口 | 说明 |
|---|---|---|
| 获取当前用户 | `GET /api/v1/users/me` | 返回 MeResponse（userId, username, avatarUrl, bio, role） |
| 更新当前用户 | `PUT /api/v1/users/me` | updateMe(UserUpdateRequest)：username / bio / avatarUrl / email；校验用户名唯一、邮箱白名单 |
| 公开资料 | `GET /api/v1/profile` | 带 JWT 时返回当前用户公开信息，供侧边栏 ProfileCard 同步 |
| 图片上传 | `POST /api/v1/media/upload` | 任何登录用户（含 READER）可用，≤10MB 图片，返回 url |

前端已存在：`fetchMe()` / `updateMe(payload)`（api/admin.js）、`uploadMedia(file)`（api/media.js，返回 `{ url }`）。

## 改动清单

| 文件 | 改动 |
|---|---|
| `vue/src/components/UserAvatar.vue` | **新建**。props：`url`（可空）、`size`。有 url → 圆形 `img`；无 → 圆形人形 SVG 图标 |
| `vue/src/components/BlogHeader.vue` | 把 `.site-nav-auth` 块整体替换为 `<UserAvatar>` 按钮 |
| `vue/src/views/ProfileView.vue` | **新建**。个人中心页：查看 + 编辑 |
| `vue/src/router/index.js` | 新增 `/profile` 路由 + 未登录守卫（跳 `/login?redirect=/profile`） |

CSS 沿用站点现有卡片风格（`vue/src/assets/blog.css` 与 `ConsoleProfileView` 的样式习惯），组件内用 scoped style。

## 交互与数据流

### Header 头像按钮（BlogHeader.vue）

- 复用现有 `loadMe()` 逻辑（route 变化时重载）。
- 登录态：显示 `me.avatarUrl` 缩略图；未登录/无头像：显示人形图标（`UserAvatar` 兜底）。
- 点击：登录 → `router.push('/profile')`；未登录 → `router.push('/login')`。
- 移除原「用户名文字 + 退出按钮」；退出登录入口移到个人页。

### 个人中心页（ProfileView.vue）

- `onMounted` 调 `fetchMe()` 回填表单。
- 字段：
  - **头像**：大图预览（`UserAvatar`）+「更换头像」`<input type=file accept=image/*>` → `uploadMedia(file)` → 拿 `resp.url` 暂存到表单（此时未落库）。
  - **用户名**：可编辑（3–32 字符，后端 `@Size` 已校验）。
  - **签名 bio**：可编辑（≤512）。
- **保存**：一次性 `PUT /api/v1/users/me`（payload：username / bio / avatarUrl）→ 用返回的 MeResponse 刷新本地 → 成功提示。Header 在路由变化时自动重载、侧边栏 `GET /profile`（auth-aware）自动同步新头像/名字。
- **不展示邮箱**：MeResponse 不含 email 字段，展示需后端改动，本期不做。
- **退出登录**：`clearAuth()` → `router.push('/login')`。

## 路由守卫

`/profile` 未登录时：`clearAuth()` 后跳 `/login?redirect=/profile`，登录成功回跳。复用 `auth/session.js` 的 `isConsoleSessionValid()` 思路（JWT 存在且未过期）。注意 `/console` 有独立守卫，本守卫只处理 `/profile`。

## 错误处理

| 场景 | 表现 |
|---|---|
| 用户名已存在（4090） | 表单内联提示「用户名已存在」，不清空输入 |
| 上传非图片 / 超 10MB / 解析失败 | 内联提示，不改变当前头像 |
| `fetchMe` 失败（401 等） | 视为未登录，跳登录页 |
| 保存网络错误 | 内联错误提示，保留表单输入 |

## 验证

1. `cd vue && npm run build` 通过（无 TS/编译错误）。
2. 手动回归：
   - 未登录：Header 显示人形图标 → 点击跳 `/login`。
   - 登录后：Header 显示头像缩略图 → 点击进 `/profile`。
   - `/profile` 改用户名/签名 → 保存成功 → Header、侧边栏名字同步。
   - 换头像 → 上传 → 保存 → 各处头像更新。
   - 用户名改成已存在的 → 内联报错。
   - 未登录直接访问 `/profile` → 自动跳登录页。
   - 退出登录 → Header 回到人形图标。

## 不做（YAGNI）

- 不建「我的文章」列表（用户自己发文章，注册用户主要是增加用户量）。
- 不做邮箱可编辑（无验证码重验流程）。
- 不新后端接口/字段/表。
- 不做头像裁剪/压缩（MediaService 已有缩略图机制）。

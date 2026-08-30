# 安全加固记录（低危·纵深防御）

> 与 `dev-experiences.md`（踩坑排障）分开：本文件沉淀**安全审查发现的中低危问题的修复**与纵深防御经验。
> 新条目按日期追加在末尾。高危问题（存储型 XSS）修复见 `dev-experiences.md` 的 `2026-08-30` 条目。

---

## 2026-08-30：媒体 Content-Type 伪造 / 用户名无字符集约束 / 头像地址无 scheme 校验

承接同日安全审查（见 `dev-experiences.md` 2026-08-30 条目），本次修复 3 个低危项。

### 1. 媒体上传：客户端伪造 Content-Type + 服务端无 nosniff

**问题**：`MediaService.upload` 仅校验 `contentType.startsWith("image/")`（客户端可控）就把类型**原样入库**；`MediaController.serveFile` 把库里的类型**原样回写**且无 `X-Content-Type-Options: nosniff`。攻击者可上传一个 PNG 但声言 `image/svg+xml` 等，服务端照单全收。

**为什么是低危**：字节经 `ImageIO.read` 验证可解码，且栅格格式（PNG/JPEG/GIF/BMP）本身不执行脚本，真实 XSS 面很窄。但"客户端声明什么就回写什么"是明确的纵深防御缺口：历史脏数据 + polyglot 文件 + 旧浏览器 MIME 嗅探的叠加风险。

**修复**（两层收口）：
- **上传**：`ImageIO.getImageReaders` 按**文件头**识别真实格式，只放行 `png/jpeg/gif/bmp/webp` 白名单，`Content-Type` 与后缀都由识别结果推导，彻底不信任客户端文件名/Content-Type（`MediaService.detectImageContentType`）。
- **回写**：`serveFile` 前 `sanitizeContentType` 归一化——白名单直通，非法类型降级 `application/octet-stream`（触发下载而非内联解析），覆盖历史脏数据；并加 `X-Content-Type-Options: nosniff`。

**代码位置**：`MediaService.java`（upload / detectImageContentType / sanitizeContentType）、`MediaController.java`（serveFile）。

### 2. 用户名：无字符集约束 + 注册不 trim

**问题**：`RegisterRequest` / `UserUpdateRequest` 的用户名仅 `@Size(3,32)`，无字符集白名单；注册/登录不 trim。控制字符、前后空格、零宽字符、视觉混淆字符都可入库。

**为什么是低危**：不构成 XSS——Vue 文本绑定自动转义，Thymeleaf 3.1.3 对 `th:text` HTML 转义、对 `th:inline="javascript"` 转义 `</script>`。真实影响是 UI 错乱、冒名（同形字符）与注册/登录不对称。

**修复**：
- 新增 `AllowedUsername` 白名单：`^[\p{L}\p{N}_.-]{3,32}$`（中英文、数字、下划线、点、短横线），注册与资料更新共用。
- 注册入口 `.trim()` 归一化（防御绕过校验的内部调用）。
- **登录不 trim，有意为之**：库里历史带空格用户名（修复前可注册出 `"foo "`）需保留登录路径；`@Pattern` 已保证新账号不可能再带空白。

**代码位置**：`user/validation/AllowedUsername.java`、`user/dto/RegisterRequest.java`、`user/dto/UserUpdateRequest.java`、`user/service/AuthService.java`（register）。

### 3. 头像地址：无 scheme 校验

**问题**：`UserUpdateRequest.avatarUrl` 仅 `@Size(512)`，可填任意 scheme。

**为什么是低危**：头像只在 `<img :src>` 渲染，`javascript:`/`data:` 在 img 里不执行。

**修复**：`@Pattern` 限定空串（清除头像）、`http(s)://` 或本站 `/api/v1/media/` 相对路径。

**代码位置**：`user/dto/UserUpdateRequest.java`。

---

### 经验价值

1. **纵深防御按层收口，别只在入口堵一次**。媒体类型：入口（`image/*` 预检）→ 权威判定（解码器识别文件头）→ 出口（回写白名单 + nosniff）。每一层都独立可防，历史脏数据由出口层兜住。
2. **"客户端声明的元数据"一律不可信**：Content-Type、原始文件名、扩展名都只是提示。真实格式以**内容本身**（解码器识别）为准——这是文件上传的通用铁律。
3. **安全审查时把"为什么是低危"写清楚**：区分"理论风险"与"可验证风险"。三处都先论证不可利用（img 不执行、Thymeleaf 转义、ImageIO 可解码栅格），再决定修多深——避免为修而修引入回归。
4. **校验白名单优先于黑名单**：用户名 `\p{L}\p{N}_.-` 白名单天然排除控制符/零宽/emoji；比"禁止特殊字符"的黑名单思路可靠。
5. **修复要考虑存量兼容**：登录不 trim 是为了不让历史带空格账号失联——安全修复不能以破坏已有用户为前提，必要时"入口收紧 + 出口兜底"而非一刀切。
6. **无状态纯函数便于测试**：`detectImageContentType` / `sanitizeContentType` 做成 `static`，单测直接断言；配合真实 PNG / 伪造 SVG 两个用例锁死行为。

---

## 2026-08-31：改密接口可匿名接管账号 —— 邮箱验证码强制 + 用户邮箱仅 ADMIN 可见

安全审查（重点：能否爬取用户邮箱、密码是否加密存储）。结论：**密码为 BCrypt 单向哈希（`users.passwordHash`），任何接口不返回哈希**；但发现一处**可账号接管的高危漏洞**与一处**邮箱数据暴露过宽**。

### 1. 高危：POST /api/v1/auth/change-password 仅凭邮箱即可匿名改密

**问题**：`AuthService.changePassword()` 仅凭 `email` 查出用户就直接 `setPasswordHash(encode(newPassword))`，**不校验旧密码 / 验证码 / 滑块**；`SecurityConfig` 对 `/api/v1/auth/**` 整体 `permitAll`，网关对无 token 请求放行交由 business 判定 → **完全匿名可达**。前端改密页也只需"邮箱 + 新密码"（API 注释写着"通过注册邮箱验证后修改密码"，但验证实际不存在）。

**攻击链**：知道用户邮箱 → POST 改密设新密码 → 用该邮箱 + 新密码登录 → 完全接管账号（含作者/管理员）。该接口还不在网关限流规则内。

**修复**（复用现有验证码机制，不新造轮子）：
- 后端改密强制邮箱验证码：`ChangePasswordRequest` 加 `code`（`^\d{6}$` 校验），`AuthService.changePassword` 改密前 `emailCodeService.verifyAndConsume()`（一次性消费 + 错次超限自动作废，防 6 位码撞库）。
- `/api/v1/auth/email-code` 新增 `purpose` 参数：`register`（默认，邮箱需未注册）| `reset`（邮箱需已注册）；`EmailCodeService.sendCode` 按用途校验注册态、区分主题与模板。message 模块新增 `reset-verification-code` 模板（"OpenBlog 找回密码验证码"）。
- 前端改密页增加验证码输入 + 获取按钮（复用注册的冷却倒计时）；邮箱变更自动作废旧码。

### 2. 中危：AUTHOR 可拉取全量用户邮箱

**问题**：`GET /admin/users`、`GET /admin/users/pending` 为 `hasAnyRole('ADMIN','AUTHOR')`，AUTHOR（内容角色，信任级别低于 ADMIN）能拉全量用户列表含邮箱 + 待审核读者邮箱。

**修复**：list / pending / approve 三个接口收紧为 `hasRole('ADMIN')`；前端侧栏"用户列表 / 用户审核"导航仅 ADMIN 显示。AUTHOR 保留内容管理能力（文章/分类/项目/公司/评论），不再接触用户邮箱。

### 3. 审查结论（直接回答问题）

| 问 | 答 |
|----|----|
| 密码是加密存储吗？ | ✅ BCrypt 单向哈希存 `users.passwordHash`，任何接口不返回哈希（无控制器直接返回 `User` 实体，全走 DTO） |
| 匿名 / 普通读者能爬邮箱吗？ | ❌ 不能。`/admin/**` 需登录 + 角色门槛；公开 `/profile` 仅回 userId/username/avatarUrl/bio |

### 经验价值

1. **"permitAll 路径里的写接口"也必须验证身份**：`/auth/**` 整体 `permitAll` + 改密无独立验证 = 把密码修改暴露给匿名者。放行路径要逐个确认写操作是否带身份校验。
2. **"验证码前置"模式可复用**：注册已有的 `verifyAndConsume`（一次性消费 + 错次作废）原样复用到改密，成本低且测试齐备；验证码是"持有邮箱"的最强匿名凭证。
3. **授权面按数据敏感度分层**：用户邮箱等 PII 的可见性应收紧到最高信任角色（ADMIN）；AUTHOR 需要的是内容管理而非用户数据。
4. **审计"能否爬取"要拆角色粒度**：匿名 / READER / AUTHOR 三档分别验证，答案差异很大，合并成一档会掩盖中间层的暴露面。
5. **部署约束**：message 新增模板后，business 与 message 需**同时部署**（business 发送 `reset-verification-code`，message 旧版本会报"未知模板"）；改密契约变化随前端一起发布。

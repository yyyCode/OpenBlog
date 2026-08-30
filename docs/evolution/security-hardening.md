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

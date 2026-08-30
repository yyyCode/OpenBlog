# 开发经验记录

> 沉淀踩过的坑与解法。新经验追加在末尾，按日期 + 标题格式。

---

## 2026-08-27：Dubbo 默认重试导致邮件重复发送 —— 非幂等 RPC 的幂等设计

### 现象

注册"获取验证码"一次点击，收到 **3 封完全相同的验证码邮件**（验证码和正文一样）。

### 根因

- Dubbo 消费端**默认 `retries=2`**（首次调用超时/失败后自动重试 2 次，共 3 次调用）、**默认 `timeout=1000ms`**。
- 阿里云真实发信耗时经常 >1s → 消费端超时 → Dubbo 在网络层**把同一个请求重新发给 provider** → email 服务的 `EmailService.send()` 被执行 3 遍 → 3 封邮件。
- 发邮件是**非幂等副作用**：重复执行会重复投递。

### 关键排查线索

**3 封邮件验证码完全相同** → 排除"点了 3 次获取"（每次点击会生成不同的随机验证码）。同码 + 同内容 = **同一次业务逻辑被重复执行**。由此定位到是 Dubbo 重试，而不是前端重复请求。

### 修复：三层防线

| 层 | 方案 | 代码位置 |
|----|------|---------|
| 1 | 消费端关闭默认重试：`@DubboReference(retries=0, timeout=5000)` | `EmailCodeService.java` |
| 2 | **provider 幂等去重**：按 `idempotencyKey` 查 `email_records`，命中直接返回已有记录、不重发 | `EmailService.send()` |
| 3 | `email_records.idempotency_key` **唯一索引**硬兜底：并发双插 / 进程崩溃 / 时间重放都不重发 | `sql/migrate-email-records-idempotency-key.sql` |

- 调用方（business）：一次 `sendCode` 生成一个 **UUID 幂等键**随 RPC 传入；复用 Redis 里未过期的验证码，避免重发作废旧邮件。
- 部署顺序：先执行迁移 SQL 加唯一索引 → 再部署 email 服务 → 最后部署 business。

### 认知教训：为什么"分布式锁"解决不了这个问题

踩坑时想过用分布式锁，**它是错误工具**：

- **Dubbo 重试发生在消费端代理层**：`sendCode()` 业务代码只执行一次，重试是网络层把同一请求重新投递给 provider。业务侧加锁根本拦不住 provider 被重复执行。
- 锁解决的是**并发互斥**，这里是**同一请求被重复投递、副作用重复执行**，需要的是**幂等**（让重复投递无害），两者不是一回事。
- 锁有 TTL 过期、进程崩溃丢锁、"几小时后重放"时锁早已不存在的缺陷。
- **DB 唯一索引本身就是一种内置的、race-safe 的分布式互斥**：它同时做到"并发只有一个成功"和"重放返回已有结果"，跨进程崩溃、跨时间均有效，是这类问题的最强解。

### 通用经验

1. **Dubbo/微服务调用任何非幂等副作用**（发邮件、发短信、扣款、生成订单）都必须：要么显式 `retries=0`，要么做幂等。二者都做最稳。
2. **幂等设计三件套**：
   - 调用方生成**幂等键**（UUID 或 `recipient+业务标识` 派生），一次逻辑操作一个键；
   - 接收方按幂等键**去重**（查已有记录 → 复用返回）；
   - 存储层**唯一索引**兜底并发竞态。
3. **排查"重复副作用"**：先看重复对象里是否有业务标识（验证码/订单号/流水号）。标识相同 = 同一逻辑被重放；标识不同 = 被触发多次。这能快速二分定位是"重试/重放"还是"重复请求"。
4. 阿里云等真实外部调用耗时长，Dubbo 默认 `timeout=1000ms` 过紧，需要按实际放大（本项目 5000ms）。

---

## 2026-08-30：两处存储型 XSS —— markdown 渲染器的"净化责任"必须落在每一处渲染边界

### 现象

安全审查发现两处存储型 XSS，一处高危、一处中危：

1. **论坛主题帖**：任意注册用户发帖，内容里写 `<img src=x onerror=...>`，所有访客打开主题详情页即执行脚本（可读 `localStorage` 里的 JWT 冒充管理员）。
2. **SEO 文章页**：作者在 markdown 里写 `<script>`，所有访客/爬虫打开 `/seo/article/{id}` 即执行。

### 根因

两处同源：**markdown 渲染产物是含原始 HTML 的，而净化只做在了前端的一部分渲染路径上**。

- **`marked.js`（前端）默认不净化 HTML**：`ForumTopicView.vue` 用 `marked.parse(topic.content)` + `v-html` 直接输出，**漏掉了 DOMPurify**——同一仓库里 `ArticleDetailView` / `ProjectDetailView` / `ChangelogDetailView` / 编辑器预览全都先 `DOMPurify.sanitize()`，唯独论坛这条用户可控路径漏了。
- **服务端 `MarkdownRenderer` 显式 `HtmlRenderer.ESCAPE_HTML=false`**：flexmark 允许 markdown 里的原始 HTML 原样进入 `content_html` 入库，这是**有意的能力**（作者可写 HTML）。
- **SEO 模板用 `th:utext` 非转义输出** `content_html`（`templates/seo/article.html:73`）：Vue 前端有 DOMPurify 兜底，但服务端渲染的 SEO 页是**唯一没有净化的公开渲染面**。
- 后端论坛帖内容**原样存储**（`ForumService` 仅 `checkSensitive()` 关键词过滤，无 HTML 净化）。

### 排查关键

逐条核对"**用户可控内容 → 在哪里渲染 → 渲染前是否净化**"：

| 用户内容 | 渲染面 | 净化？ |
|---------|--------|--------|
| 文章正文 | Vue `v-html` | ✅ DOMPurify |
| 文章正文 | SEO 模板 `th:utext` | ❌ 无 |
| 论坛主题 | Vue `v-html` | ❌ 无（漏了 DOMPurify） |
| 用户名/摘要 | Vue `{{ }}`、Thymeleaf `th:text`/`th:inline` | ✅ 框架自动转义 |

漏网的两处恰好是"手写 v-html / 手写 th:utext"的边界——**框架自动转义管不到显式输出 HTML 的 API**。

### 修复

1. **论坛**（`ForumTopicView.vue`）：`renderedContent` 改为 `DOMPurify.sanitize(marked.parse(...))`，与全站其他 markdown 渲染对齐。改后 fallback 分支同样过 DOMPurify。
2. **SEO 文章页**（新增 `seo/HtmlSanitizer.java` + `SeoPageController`）：用 **jsoup `Safelist.relaxed()`** 在渲染边界净化 `contentHtml` 后再进模板。

   关键取舍：**净化放在 SEO 渲染边界，而不是写库时**。原因：
   - 不改存储/API 返回 → Vue 前端行为零变化，任务列表、SVG 等 `DOMPurify` 放行的内容不受影响；
   - **覆盖已入库的旧脏数据**（历史文章若含原始 HTML，渲染时也被清洗）；
   - 修复点=漏洞点，一处闭合，不扩散风险。
3. 新增 `HtmlSanitizerTest` 回归测试（script/onerror/javascript: 协议剥离，正常排版保留）。

### 认知教训

1. **净化责任在"渲染边界"，不在"存储层"**。只要 markdown 允许原始 HTML（`ESCAPE_HTML=false` 是有意能力），内容在库里就是"脏"的；安全的关键是**每一处把它当 HTML 输出的地方**都必须净化。
2. **"其他地方都净化了"不是安全**。安全审查不是看"主流路径安全"，而是枚举**所有** `v-html` / `th:utext` / `innerHTML`，逐处核对。这次漏的论坛恰是"用户可控程度最高"的路径。
3. **`marked.parse()` 不净化**，`th:utext` 不转义，`th:inline="javascript"`（Thymeleaf 3.1）会转义 `</script>`——框架默认行为各不相同，别凭印象，看文档/源码。
4. **前端净化 ≠ 后端安全**。前端 DOMPurify 保护的是自家 Vue 应用；API 返回的原始 `content_html` 一旦被第三方/服务端模板/爬虫渲染，保护即失效。服务端渲染面（Thymeleaf/爬虫）必须有自己的净化。

### 通用经验

1. 新增任何渲染用户内容的页面，先过一遍"**内容来源 → 渲染 API（v-html / th:utext / innerHTML / dangerouslySetInnerHTML）→ 是否有净化**"检查表。
2. 前后端各留一份 HTML 净化（前端 DOMPurify、后端 jsoup/Owasp sanitizer），白名单策略保持一致；**净化放在每个渲染边界**，宁可重复净化（幂等）也不漏一处。
3. 安全审查用 `grep -rn "v-html\|th:utext\|innerHTML"` 一票枚举全部 XSS 输出面，再对每个面追数据流。
4. 高危的判定标准是"**用户可控程度 × 渲染面公开度**"：论坛（任意注册用户可控 + 全站公开）> SEO 文章页（作者可控 + 公开）> 管理端。

---

## 2026-08-30：媒体上传 5001/DirectoryNotEmptyException —— 缩略图临时文件名去扩展名的连锁故障

### 现象

生产上传图片接口报错，网关层 `5001`，message 是临时目录路径：

```
code: 5001, message: "/tmp/openblog-media-7453218818868257170"
```

服务端日志根因：

```
java.nio.file.DirectoryNotEmptyException: /tmp/openblog-media-...
    at Files.deleteIfExists(...)
    at MediaService.upload(MediaService.java:173)
```

### 根因

`MediaService.upload` 的临时缩略图文件从**带扩展名**改成了**无扩展名**：

- 改前：`tempDir.resolve("thumb-" + key.replace('/', '-'))` —— `key` 含 `.png`/`.jpg` 等后缀
- 改后：`tempDir.resolve("thumb")` —— 只剩裸文件名

**Thumbnailator 0.4.20 的 `toFile(File)` 靠扩展名推断输出格式**。无扩展名时它在**同目录生成 `thumb.JPEG`**（scratch 文件），而目标文件 `thumb` 从不落盘。连锁反应：

1. `ImageIO.read(thumbPath)` 读不到 `thumb` → 上传失败（本地/某些平台抛 `IIOException: Can't read input file!`，另一些返回 null 后 `getWidth()` NPE）；
2. `thumb.JPEG` 残留在临时目录 → `finally` 里 `deleteIfExists(tempDir)` 抛 `DirectoryNotEmptyException`；
3. 若 try 块先抛异常（NPE/IIO），finally 里的 `DirectoryNotEmptyException` 会**覆盖主异常**成为最终日志，且 message 就是临时目录路径。

### 排查关键

不要停留在"目录为什么非空"，用**最小复现**找出谁在目录里留了文件：

1. 写一个复现测试，跑 `Files.createTempDirectory → 写图 → ImageIO.read → Thumbnails.toFile → 列目录`，逐步骤打印目录内容。
2. 证据（Windows 本地与生产 Linux 行为一致）：
   ```
   >>> AFTER toFile leftover: original (size=8227)
   >>> AFTER toFile leftover: thumb.JPEG (size=1827)   ← 多出来的文件
   >>> CLEANUP FAILED: DirectoryNotEmptyException
   ```
3. 结论：**无扩展名 + 靠扩展名推断格式的库**组合是直接原因。

### 修复

1. **缩略图临时文件带扩展名**：`ext` 由权威格式判定（`detectImageContentType`）得出后，`thumbPath = tempDir.resolve("thumb." + ext)`。`original` 无扩展名无碍——ImageIO 按文件内容识别格式，与文件名无关。
2. **清理改为递归删除整个临时目录**（`deleteRecursively(tempDir)`）：任何库留下的残留都不再触发 `DirectoryNotEmptyException`，属纵深防御。
3. 新增 `MediaTempDirCleanupTest` 回归：带扩展名缩略图可写可读 + 递归清理无残留 + 缺失路径静默。

### 认知教训

1. **给"靠文件名推断行为"的库传文件时，永远带正确扩展名**。Thumbnailator 推断输出格式、ImageIO 的部分实现、很多工具库都依赖扩展名；无扩展名时它们的行为是"写一个你能猜到的 scratch 名 + 目标不落盘"，比直接报错更隐蔽。
2. **`finally` 清理不要只删"已知的 N 个文件"**。临时目录里的文件集合由多库共同决定（Thumbnailator、ImageIO 缓存、未来新增步骤），一次只删 2 个已知文件必然漏。**递归删除整个专属临时目录**才是稳的。
3. **`deleteIfExists(目录)` 对非空目录抛 `DirectoryNotEmptyException`，不是静默失败**。它不会帮你"尽量删"，必须自己保证目录为空或递归删。
4. **finally 里的异常会覆盖 try 里的主异常**。若 try 抛 NPE/IIO、finally 又抛 `DirectoryNotEmptyException`，线上日志只有后者，message 还可能是无意义的路径——排查时先想"有没有被 finally 覆盖的异常"。
5. **安全加固改动的隐性回归**：这轮把临时文件名 `thumb-<key>` 简化成 `thumb` 属"顺手简化"，却踩了格式推断的坑。**重构/加固时保持与原实现等价的外部契约**，文件名扩展名这类细节也要留意。
6. **复现测试的价值**：一次本地复现（Windows 上同一套 Thumbnailator 行为）就锁定了跨平台的根因，不需要在生产 Linux 上反复试。文件系统/图像库的这类行为跨平台稳定时可放心用本地最小复现替代线上排查。

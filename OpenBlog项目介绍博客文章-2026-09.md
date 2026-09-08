# OpenBlog：从零自研的 Spring Boot 3 + Vue 3 全栈博客系统

> OpenBlog 是一个面向个人博客场景的开源全栈项目，后端 Spring Boot 3，前端 Vue 3，线上运行于 [wecode.xin](https://www.wecode.xin/)。这篇文章介绍它的技术架构、功能设计，以及几个值得参考的实现细节。

## 这个项目是做什么的

OpenBlog 是一套完整的博客系统，文章、评论、论坛、媒体、SEO、管理后台都有，前后端分离，全部自研。项目在 GitHub 开源，线上站点 wecode.xin 在运行。

功能覆盖一条完整的博客链路：

| 模块 | 功能 |
|------|------|
| 账号 | 注册、登录、JWT 双 Token 鉴权（Access + Refresh）、用户资料 |
| 文章 | Markdown 编辑、草稿/发布/定时发布/隐藏、全文搜索、导入导出 |
| 评论 | 文章评论、回复嵌套、幂等防重复提交 |
| 论坛 | 话题发布、评论、敏感词过滤（DFA）、管理员隐藏 |
| 互动 | 点赞、收藏、关注 |
| 媒体 | MinIO/本地存储、缩略图、粘贴/拖拽上传，支持 WebP |
| SEO | 爬虫动态 SSR、Open Graph、JSON-LD、sitemap.xml、百度推送 |
| 站点配置 | 后台可视化编辑站点名称/描述/版权 |
| 管理后台 | 文章、用户、评论、反馈、附件管理，更新日志 |
| 个性化 | Live2D 看板娘、飞书风格主题、亮暗模式 |

## 技术选型

| 层 | 技术 |
|----|------|
| 后端 | Java 17、Spring Boot 3.5、MyBatis-Plus、Spring Security |
| 中间件 | MySQL 8、Redis（Redisson）、MinIO、RocketMQ |
| RPC/注册 | Dubbo 3.4 + Nacos |
| 前端 | Vue 3、Vue Router 4、Vite 8、marked、DOMPurify |
| 部署 | Docker、GitHub Actions + 自托管 Runner |

## 架构：三服务拆分 + 自研框架模块

Maven 多模块工程，按职责拆成几块：

| 模块 | 端口 | 职责 |
|------|------|------|
| OpenBlog-business | 8082 | 主业务：文章、评论、论坛、用户、SEO、媒体 |
| OpenBlog-message | 8083 | 统一通知服务：邮件（阿里云 DirectMail）+ Dubbo + Nacos |
| OpenBlog-gateway | 8090 | API 网关：JWT 粗校验、限流、traceId、统一错误兜底、CORS |
| OpenBlog-framework | - | 自研框架：redis / elasticsearch / audit / idempotent 四个子模块 |
| OpenBlog-api | - | 跨服务共享的 RPC 接口与 DTO |
| vue | - | Vue 3 前端 |

三个服务之间的耦合约束写进了部署文档：business 通过 Dubbo 调 message，两边共用同一套 RPC 接口，升级必须同步部署；business 和 gateway 共用 JWT 密钥，换密钥也要两端同步。

自研框架模块是另一个重点：

- **framework-redis**：封装 `RedisOps`，统一 Key 管理，内置容错，Redis 不可用时自动回退数据库，不影响业务。
- **framework-audit**：`@AuditLog` 注解 + AOP，自动记录操作快照和耗时。
- **framework-idempotent**：通用幂等组件，下文单独讲。
- **framework-elasticsearch**：预留模块，为搜索能力升级做准备。

## 几个值得看的实现细节

### 1. 文章表拆成两张

`articles` 表存标题、摘要、状态、分类等元数据，`article_bodies` 表单独存 Markdown 和渲染后的 HTML。列表查询不拖正文大字段，正文可以独立做 FULLTEXT 全文索引。

### 2. 两级缓存 + 版本号失效

已发布文章走 Redis 缓存。正文缓存 30 分钟，列表缓存 5 分钟。列表缓存的 Key 里带版本号，发布或删除文章时只做一次 INCR，旧缓存自然过期，不用逐个清理。

`RedisOps` 所有方法内置 try-catch，Redis 挂了就回退数据库，不会让业务跟着挂。

### 3. 轻量动态 SSR

SPA 对搜索引擎不友好是老问题。OpenBlog 没有引入 Nuxt，而是用 Nginx 按 User-Agent 分流：爬虫请求转发到后端，用 Thymeleaf 渲染完整 HTML；普通用户直接拿 Vue SPA。再配上 Open Graph、JSON-LD 结构化数据、sitemap.xml 自动生成和百度站长推送，凑成一套完整的 SEO 闭环。

### 4. 网关收口 + 设备指纹限流

gateway 统一做 JWT 粗校验、限流、traceId 透传、统一错误兜底和 CORS。限流支持设备指纹维度：前端用 FingerprintJS 算指纹，网关按指纹 + IP 复合维度限流，登录、反馈等未登录接口也能防刷。

### 5. 幂等框架：一个注解防重复执行

双击"发表评论"会发两条，RPC 超时重试也可能重复执行。OpenBlog 抽了一个通用幂等组件：`@RepeatExecuteLimit` 注解 + AOP 切面，在方法外层套本地锁（Caffeine）+ Redisson 分布式锁 + Redis 幂等标识三层防护，配合双重检测。

支持两种失败策略：

- **CACHE_REJECT**：直接拒绝，提示重复操作。
- **RETURN_SAME_RESULT**：返回上一次的结果。评论场景用这个，双击会返回同一条评论，而不是报错。

幂等 Key 由前端 requestId + 用户 + 业务字段拼出来，不会误伤"同一内容想再发一次"的正常操作。

### 6. 通知服务：同步 + 异步两条路径

消息服务拆出来之后，通知编排层也整体迁了过去，business 只留一个 Dubbo RPC 客户端。同步路径直发邮件；异步路径走 outbox 模式：先在同一事务里写 `notification_outbox` 表，定时任务扫 PENDING 记录发到 RocketMQ，消费后再投递，保证至少一次，配合幂等兜底防重复。邮件发送本身也做了幂等，用 `email_records` 表去重。

## 运维与部署

三个服务各自 Docker 化，CI 是三条独立流水线，push master 自动构建部署，互不影响。business 和 message 因为 Dubbo 接口强耦合，部署必须同步。

踩过的坑都有记录。比如 Dubbo 重试导致邮件重复发送，最后用幂等三件套解决：调用方幂等键 + 接收方去重 + DB 唯一索引。这些经验沉淀在仓库的 docs 目录里。另外各模块日志都持久化到文件，business 的 5xx 错误单独留痕，方便排障。

## 线上体验与开源

线上地址 [wecode.xin](https://www.wecode.xin/)，可以实际体验文章渲染、亮暗主题切换、SEO 效果（右键查看源码能看到服务端渲染的结构化数据）和 Live2D 看板娘。

GitHub 仓库：[yyyCode/OpenBlog](https://github.com/yyyCode/OpenBlog)

本地跑起来只需要 MySQL 8 + Redis：

```bash
# 后端（默认端口 8082）
mvn spring-boot:run -pl OpenBlog-business

# 前端
cd vue && npm install && npm run dev
```

## 一点个人感受

这个项目最大的价值不在功能多，而在它是一个完整可运行的参考实现：微服务拆分、缓存、幂等、消息、SEO、CI/CD 都有真实落地，每个设计都踩过坑、写成了文档。

如果你在搭个人博客，或者想系统看一个 Spring Boot 3 + Vue 3 全栈项目实际长什么样，可以拿来参考。欢迎 Star、提 Issue 交流。

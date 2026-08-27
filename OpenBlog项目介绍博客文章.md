# OpenBlog —— 从零搭建的个人博客全栈实践

> 一个面向个人博客场景的现代化全栈项目，基于 Spring Boot 3 + Vue 3 构建，已上线运行于 [wecode.xin](https://www.wecode.xin/)。

---

## 写在前面

作为一名开发者，我一直想拥有一个属于自己的博客空间——不只是为了写文章，更是为了亲手打造一个完整的、现代化的 Web 应用。于是，**OpenBlog** 诞生了。

这是一个从后端到前端、从数据库到缓存、从 SEO 到运维部署，全链路自研的个人博客系统。目前项目已开源在 GitHub（[yyyCode/OpenBlog](https://github.com/yyyCode/OpenBlog.git)），线上站点 [wecode.xin](https://www.wecode.xin/) 稳定运行中。

本文将全面介绍 OpenBlog 的技术架构、功能设计与实现细节，希望能给同样想搭建个人博客的开发者一些参考。

---

## 项目概览

OpenBlog 定位为**面向个人博客场景的全栈项目**，覆盖了博客系统的完整功能链路：

| 模块 | 功能说明 |
|------|----------|
| 账号系统 | 注册、登录、JWT 双 Token 鉴权（Access + Refresh）、用户资料管理 |
| 文章系统 | Markdown 撰写、草稿/发布/定时发布、导入/导出、全文搜索 |
| 评论系统 | 文章评论、回复嵌套、删除管理 |
| 互动系统 | 点赞、收藏、关注，完整的用户互动链路 |
| 媒体管理 | 图片上传（支持粘贴/拖拽）、缩略图生成、媒体库浏览、一键插入 |
| SEO 优化 | 爬虫动态 SSR、Open Graph / JSON-LD 结构化数据、sitemap.xml 自动生成、百度站长推送 |
| 站点配置 | 后台可视化配置站点标题、描述、版权等全局信息 |
| 管理后台 | 文章管理、用户管理、评论审核、反馈处理、附件管理、更新日志等 |
| 个性化 | Live2D 看板娘、飞书风格主题、亮暗主题切换 |

---

## 技术栈一览

### 后端

| 技术 | 版本 | 用途 |
|------|------|------|
| Java | **17** | 运行环境 |
| Spring Boot | **3.5.x** | 核心框架（Web、Security、Validation、Data JPA） |
| MyBatis-Plus | **3.5.x** | ORM 增强（与 JPA 并存，按场景选用） |
| MySQL | **8.0** | 关系型数据库，含 FULLTEXT 全文索引 |
| Redis | — | 两级缓存、计数器去重、限流器 |
| JWT (jjwt) | **0.12.x** | 无状态鉴权 |
| MinIO | — | 对象存储（图片等二进制资源，可选本地文件系统） |
| flexmark | **0.64.x** | 服务端 Markdown → HTML 预渲染 |
| Thymeleaf | — | SEO 爬虫页面服务端渲染模板引擎 |
| Thumbnailator | — | 图片缩略图生成 |
| Caffeine | — | 本地缓存 |

### 前端

| 技术 | 版本 | 用途 |
|------|------|------|
| Vue | **3.5.x** | 前端框架 |
| Vue Router | **4.x** | 前端路由 |
| Vite | **8.x** | 构建工具 |
| marked | **17.x** | Markdown 渲染（前端回退） |
| DOMPurify | **3.x** | HTML 消毒，防止 XSS |
| oh-my-live2d | **0.19.x** | Live2D 看板娘组件 |

### 自定义框架模块

项目拆分为多模块 Maven 工程，框架封装统一收拢在 `OpenBlog-framework` 父聚合下，其中三个子模块值得单独说明：

- **framework-redis**（`com.yqz.openblog.redis`）：对 Redis 操作的统一封装，提供 `RedisOps` 接口（内置容错）、`RedisKeys` 统一 Key 管理、滑动窗口限流器等基础设施。业务模块不直接依赖 `StringRedisTemplate`。
- **framework-elasticsearch**（`com.yqz.openblog.search`）：预留的 Elasticsearch 集成模块，为后续搜索能力升级做准备。
- **framework-audit**（`com.yqz.openblog.audit`）：可插拔审计框架，`@AuditLog` 注解 + AOP 切面 + SPI 扩展点。

---

## 架构设计亮点

### 1. 文章存储：元数据与正文分离

传统博客系统往往将文章标题、摘要、正文全部塞在一张表里。OpenBlog 采用了**分表存储**策略：

```
┌─────────────────┐         ┌──────────────────────┐
│    articles     │ 1:1     │   article_bodies     │
│─────────────────│         │──────────────────────│
│ id              │────────→│ article_id           │
│ title           │         │ content_markdown     │ (MEDIUMTEXT)
│ summary         │         │ content_html         │ (MEDIUMTEXT)
│ status          │         └──────────────────────┘
│ category_id     │
│ author_id       │
│ view_count      │
│ like_count      │
│ ...             │
└─────────────────┘
```

**设计收益：**
- 列表查询只检索元数据表，不拖出正文大字段，查询更轻量
- 正文独立做 FULLTEXT 全文索引，搜索更高效
- 服务端发布时用 flexmark **预渲染** Markdown 为 HTML，前端直接展示，避免每次客户端解析

### 2. 缓存架构：两级缓存 + 版本号失效

已发布文章的读取链路使用 Redis 做两级缓存：

| 缓存层级 | TTL | 策略 |
|----------|-----|------|
| 文章正文缓存 | 30 分钟 | Cache-Aside：查无则回源 MySQL 并回写 |
| 文章列表缓存 | 5 分钟 | **版本号机制**：写操作递增全局版本号，旧缓存自然过期 |

```
读取流程：Redis → 命中返回 → 未命中查 MySQL → 回写 Redis
写入流程：更新 MySQL → 删除/失效 Redis → 下次读取自动重建
```

**版本号机制的巧妙之处**：文章列表缓存 Key 中携带 `v{version}`，发布/删除文章时只需 `INCR version`，旧版本缓存无需逐个清理，自然淘汰。

**故障降级**：`RedisOps` 所有方法内置 try-catch，Redis 不可用时自动回退到数据库，不影响正常业务。

### 3. SEO 架构：动态 SSR 方案

SPA（单页应用）对搜索引擎不友好是个老问题。OpenBlog 没有采用 Nuxt.js 等重量级 SSR 框架，而是实现了一套**轻量级动态 SSR**：

```
爬虫请求 → Nginx（User-Agent 检测）
              ├── 爬虫：转发到后端 SEO 控制器 → Thymeleaf 渲染 → 完整 HTML
              └── 普通用户：直接返回 Vue SPA
```

配合以下组件形成完整 SEO 闭环：

- **Open Graph / Twitter Card**：文章详情页注入社交分享元标签
- **JSON-LD 结构化数据**：帮助搜索引擎理解页面内容
- **sitemap.xml**：自动生成，包含所有已发布文章 URL
- **百度站长推送**：文章发布时异步调用百度 API，加速收录

### 4. 媒体管理：粘贴即上传

文章编辑器做了细致的产品体验打磨：

- 在编辑区 **Ctrl+V 粘贴截图**或**拖拽图片**，自动上传并插入 Markdown 图片语法
- 工具栏「插入图片」按钮支持直接选文件
- 「媒体库」弹窗浏览已上传图片，点击缩略图一键插入
- 上传自动生成缩略图，全屏预览、一键复制 URL

---

## 项目结构一览

```
OpenBlog/
├── OpenBlog-framework/               # 框架封装（父聚合）
│   ├── framework-redis/              # Redis 框架封装（自研模块）
│   ├── framework-elasticsearch/      # ES 集成预留（自研模块）
│   └── framework-audit/              # 审计框架（自研模块）
├── OpenBlog-business/                # 业务主模块
│   └── src/main/
│       ├── java/com/yqz/openblog/
│       │   ├── article/              # 文章：实体、服务、缓存、导入导出
│       │   ├── category/             # 文章分类
│       │   ├── changelog/            # 更新日志
│       │   ├── comment/              # 评论系统
│       │   ├── interaction/          # 互动（点赞/收藏/关注）
│       │   ├── media/                # 媒体管理（MinIO/本地+缩略图）
│       │   ├── user/                 # 用户认证与 JWT
│       │   ├── seo/                  # SEO：SSR渲染、sitemap、百度推送
│       │   ├── site/                 # 站点全局配置
│       │   ├── controller/           # REST 控制器
│       │   └── config/               # Spring Security 等配置
│       └── resources/
│           ├── templates/seo/        # Thymeleaf SEO 模板
│           └── sql/                  # 数据库脚本
├── vue/                              # Vue 3 前端
│   └── src/
│       ├── api/                      # HTTP 请求封装
│       ├── views/                    # 页面组件（含管理后台全套页面）
│       ├── components/               # 通用组件（Live2D、评论、文章卡片等）
│       └── layouts/                  # 布局组件（公开页+控制台双布局）
├── docs/                             # 设计文档与规划
└── pom.xml                           # 根 POM
```

---

## 线上体验

OpenBlog 已部署上线，欢迎访问体验：

**线上地址：[https://www.wecode.xin/](https://www.wecode.xin/)**

你可以在这里：
- 浏览已发布的文章，体验 Markdown 渲染效果
- 感受飞书风格主题与亮暗模式切换
- 查看 SEO 优化效果（右键查看源码，体验服务端渲染的结构化数据）
- 与右下角 Live2D 看板娘互动

---

## 开源与贡献

项目完全开源，采用 Maven 多模块 + Vue 3 单页应用的经典前后端分离架构，适合作为个人博客搭建的参考实现，也适合 Java/Vue 开发者学习全栈项目实践。

**GitHub 仓库：[https://github.com/yyyCode/OpenBlog](https://github.com/yyyCode/OpenBlog.git)**

### 本地运行

```bash
# 1. 准备 MySQL 8 数据库与 Redis

# 2. 启动后端（默认端口 8082）
./mvnw spring-boot:run

# 3. 启动前端
cd vue && npm install && npm run dev
```

详细配置说明请参考仓库 [README](https://github.com/yyyCode/OpenBlog)。

---

## 写在最后

OpenBlog 是我个人在博客系统领域的一次完整实践，从最初的文章发布，到后续逐步加入缓存优化、SEO 支持、媒体管理、管理后台、站点配置等能力，迭代过程中踩了不少坑，也学到了很多。

技术选型上，没有盲目追新，而是选用了 Spring Boot + Vue 这套成熟稳定的组合，把精力集中在**架构设计的合理性**和**产品体验的打磨**上——比如文章分表存储降低查询开销、版本号机制优雅处理缓存失效、粘贴即上传降低写作摩擦、动态 SSR 兼顾 SEO 与开发体验。

如果你也在搭建个人博客，或者想学习 Spring Boot 3 + Vue 3 的全栈项目实践，希望 OpenBlog 能给你带来一些启发。欢迎 Star、提 Issue、交流讨论！

---

*2026 年 7 月 · Spring Boot / Vue 3 / 全栈 / 个人博客 / 开源*

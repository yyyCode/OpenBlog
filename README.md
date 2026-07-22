# OpenBlog

面向个人博客场景的全栈项目：后端为 **Spring Boot 3** REST API，前端为 **Vue 3 + Vite** 单页应用。支持文章发布与 Markdown 展示、评论与回复、点赞/收藏/关注、媒体上传与缩略图等能力。

---

## 功能概览

| 模块 | 说明 |
|------|------|
| 账号 | 注册、登录、JWT 刷新、当前用户资料查询与更新 |
| 文章 | 草稿创建/编辑、发布（支持定时发布）、下架、删除、Markdown 导入/导出、公开列表与详情、作者侧「我的文章」与详情；正文独立存储并在发布时预渲染 HTML；编辑器支持粘贴/拖拽插图、媒体库一键插入 |
| 搜索 | MySQL FULLTEXT 索引支持文章正文搜索（`article_bodies.content_markdown`） |
| 互动 | 文章点赞/取消、收藏/取消、关注/取消关注 |
| 评论 | 文章下评论列表、发表评论、回复评论、删除评论 |
| 媒体 | 上传、列表浏览（分页）、删除、原图/缩略图访问、缩略图元数据查询、全屏预览、一键复制 URL |
| 首页 | 聚合数据接口（如 `/api/v1/home`） |
| 公开资料 | `/api/v1/profile`：未登录展示站点作者信息，已登录可优先展示当前用户公开资料 |
| SEO | 为爬虫提供 Thymeleaf 服务端渲染页面（含 Open Graph / JSON-LD 结构化数据），自动生成 `sitemap.xml` 与 `robots.txt`，文章发布时异步推送 URL 到百度站长平台 |
| 站点配置 | 后台管理页面读写站点元信息（站点标题、描述、版权等），前端全局加载并注入各组件，避免硬编码 |

更完整的产品与接口设计可参考仓库内文档：`docs/后端功能与架构设计.md`。

### 文章存储架构

文章元数据（标题、摘要、状态、计数器等）与正文**分表存储**：

| 表 | 存储内容 | 说明 |
|----|---------|------|
| `articles` | 元数据 | 列表查询轻量，不拖出正文大字段 |
| `article_bodies` | `content_markdown`(MEDIUMTEXT) + `content_html`(MEDIUMTEXT) | 正文与渲染结果独立存储 |

- **预渲染**：保存/发布时由 flexmark 将 Markdown 渲染为 HTML，前端展示直接使用，避免每次客户端解析。
- **全文索引**：`article_bodies.content_markdown` 上建有 MySQL FULLTEXT 索引，支持正文搜索。
- **惰性回填**：迁移后的旧文章 `content_html` 为空，首次读取时自动渲染并写回。
- **图片等二进制资源**：通过 MinIO（或本地文件系统）独立存储，不在数据库中。

### 缓存架构

Redis 操作统一封装在 `OpenBlog-framework-redis` 模块中，提供 `RedisOps` 接口（内置容错）、`RedisKeys` 统一 Key 管理、滑动窗口限流器等基础能力。业务模块通过该模块间接使用 Redis，不直接依赖 `StringRedisTemplate`。

已发布文章的读取链路使用 Redis 做两级缓存，减少数据库压力：

| 缓存层级 | Redis Key | TTL | 说明 |
|---------|-----------|-----|------|
| 文章正文 | `openblog:content:article:body:{id}` | 30 min | 缓存标题、摘要、正文（Markdown + HTML）、作者、分类等相对稳定字段。计数类字段（阅读量、点赞数等）每次从数据库合并，避免缓存与数据库不一致。 |
| 文章列表 | `openblog:content:article:list:v{version}:{categoryId}:{page}:{size}` | 5 min | 缓存已发布文章分页列表。写操作通过递增全局版本号使旧版本缓存自然过期，无需全量扫描删除。 |

**Redis Key 命名规范**：`openblog:{领域}:{实体}:{用途}:{参数...}`

| 领域 | 示例 Key | 用途 |
|------|---------|------|
| `content` | `openblog:content:article:body:{id}` | 文章正文与列表缓存 |
| `counter` | `openblog:counter:article:view:{id}:{ip}` | 阅读量/访问量去重 |
| `security` | `openblog:security:login:fail:{ip}` | 登录锁定、滑块验证 |
| `ratelimit` | `openblog:ratelimit:feedback:{ip}:{date}` | 反馈提交限流 |

**缓存一致性策略（Cache-Aside）**：

```
读取：先查 Redis → 命中返回 → 未命中查 MySQL 并回写 Redis
写入：更新 MySQL → 删除 Redis 缓存 → 下次读取时自动重建
```

| 写操作 | 正文缓存 | 列表缓存 |
|--------|---------|---------|
| 发布文章 | 删除 | 递增版本号（全局失效） |
| 更新已发布文章 | 删除 | 递增版本号 |
| 删除/下架文章 | 删除 | 递增版本号 |
| 定时发布 | 逐条删除 | 批量完成后递增版本号 |
| 创建草稿 | 不处理 | 不处理 |

**故障降级**：`RedisOps` 所有方法内置 try-catch，Redis 不可用时读侧返回空（走数据库），写侧忽略并记录日志，不影响正常业务响应。

### SEO 架构

针对 SPA 对搜索引擎不友好的问题，采用 **动态服务端渲染（动态 SSR）** 方案：Nginx 根据 User-Agent 将爬虫请求转发到后端 SEO 控制器，由 Thymeleaf 模板渲染完整 HTML 页面返回。

```
爬虫请求 → Nginx（User-Agent 检测）→ 后端 SEO 控制器 → Thymeleaf 模板渲染 → 完整 HTML
普通用户 → Nginx → Vue SPA（正常前端体验）
```

| 组件 | 说明 |
|------|------|
| `SeoPageController` | 渲染文章详情页与首页的 SEO HTML，注入 Open Graph / Twitter Card / JSON-LD 结构化数据 |
| `SitemapController` | 动态生成 `sitemap.xml`，列出已发布文章 URL，含 `lastmod` 与 `priority` |
| `RobotsController` | 返回 `robots.txt`，指向 sitemap 并配置爬取规则 |
| `BaiduPushService` | 文章发布时异步调用百度站长平台 URL 推送 API，加速收录 |
| `SeoProperties` | 配置项：站点 URL、名称、描述、百度推送 token 与开关（`openblog.seo.*`） |
| `vue/nginx.conf` | 参考 Nginx 配置，包含爬虫 User-Agent 路由规则 |

---

## 技术栈

**后端**

- Java **17**
- Spring Boot **3.5.x**（Web、Validation、Security、Data JPA）
- MyBatis-Plus **3.5.x**（与 JPA 并存，按模块使用）
- MySQL **8**（Hibernate `ddl-auto: update` 便于开发迭代）
- Redis（通过 `OpenBlog-framework-redis` 模块统一封装：`RedisOps` 操作接口、`RedisKeys` Key 管理、滑动窗口限流器）
- JWT（jjwt **0.12.x**）
- MinIO 对象存储（图片上传，可选本地文件系统回退）
- flexmark（服务端 Markdown → HTML 预渲染）
- Thymeleaf（SEO 爬虫页面服务端渲染）
- 缩略图（Thumbnailator）、Caffeine 本地缓存

**前端**

- Vue **3**、Vue Router **4**
- Vite **8**
- Markdown 渲染：服务端 `flexmark` 预渲染 HTML，前端 `marked` 作为回退；HTML 消毒：`dompurify`

---

## 前端界面说明

- **首页布局**：两栏布局，左侧为个人信息栏（吸顶），右侧为文章内容。
- **顶栏导航**：提供主题切换、项目源码入口等快捷操作。
  - **GitHub（项目源码）**：`https://github.com/yyyCode/OpenBlog.git`
- **文章编辑器**：
  - Ctrl+V 粘贴截图 / 拖拽图片到编辑区，自动上传并插入 Markdown 图片语法
  - 工具栏「插入图片」按钮可直接选文件上传
  - 「媒体库」弹窗浏览已上传图片，点击缩略图一键插入
- **附件管理**：缩略图网格展示、分页浏览、全屏预览、复制 URL、删除确认
- **Live2D 看板娘**：页面右下角展示 Live2D 角色，支持交互，首次访问显示欢迎引导弹窗
- **站点设置管理**：后台 `/console/site-config` 页面可编辑站点名称、描述、版权信息等全局配置，修改后各组件自动从配置读取，无需修改代码
- **页脚**：自动展示站点版权信息（从站点配置读取）

---

## 仓库结构

```
OpenBlog/
├── OpenBlog-framework-redis/        # Redis 框架封装模块
│   └── src/main/java/com/yqz/openblog/redis/
│       ├── core/                    # RedisOps 接口、DefaultRedisOps 实现、RedisKeys
│       ├── config/                  # RedisProperties、自动装配
│       └── limiter/                 # SlidingWindowLimiter（ZSET 滑动窗口）
├── OpenBlog-business/               # 业务模块（Spring Boot）
│   └── src/main/
│       ├── java/com/yqz/openblog/
│       │   ├── article/            # 文章：实体、DTO、服务、导入/导出、定时发布
│       │   │   ├── entity/         # Article, ArticleBody（正文独立存储）
│       │   │   ├── service/        # 文章服务、缓存（正文+列表）、阅读量、导入导出
│       │   │   └── repo/           # MyBatis-Plus Mapper + JPA Repository
│       │   ├── category/           # 分类
│       │   ├── changelog/          # 更新日志
│       │   ├── comment/            # 评论
│       │   ├── interaction/        # 互动（点赞、收藏、关注）
│       │   ├── media/              # 媒体上传（MinIO / 本地存储 + 缩略图）
│       │   ├── user/               # 用户、认证、JWT
│       │   ├── seo/                # SEO：爬虫页面渲染、sitemap、robots、百度推送
│       │   ├── site/               # 站点配置（实体、Mapper、Service）
│       │   ├── controller/         # REST 控制器（含站点配置读写端点）
│       │   └── config/             # Spring Security、MyBatis-Plus 等配置
│       └── resources/
│           ├── application.yaml    # 服务端口、数据源、Redis、JWT、MinIO、SEO 等
│           ├── templates/seo/      # Thymeleaf SEO 模板（article.html、home.html）
│           └── sql/                # 手动 SQL 脚本（迁移、参考数据、site_config 建表）
├── vue/                            # Vue 3 前端
│   ├── src/
│   │   ├── api/                    # HTTP 封装（含站点配置 API）
│   │   ├── views/                  # 页面组件（含 ConsoleSiteConfigView 站点设置）
│   │   └── components/             # 通用组件（含 Live2dCharacter 看板娘、WelcomeGate 欢迎引导）
│   ├── package.json
│   └── vite.config.js
├── docs/                           # 架构与需求说明
├── pom.xml                         # 根 POM（聚合模块）
└── mvnw / mvnw.cmd                 # Maven Wrapper（可选）
```

---

## 环境要求

- **JDK 17**（与 `pom.xml` 中 `java.version` 一致）
- **Maven 3.6+**（或使用项目自带的 `./mvnw` / `mvnw.cmd`）
- **Node.js 18+**（建议 LTS），用于前端依赖安装与构建
- **MySQL 8**：先创建空库（例如 `openblog`），字符集建议 `utf8mb4`
- **Redis**：与 `application.yaml` 中配置一致

---

## 配置说明

主要配置位于 `src/main/resources/application.yaml`。

| 配置项 | 含义 |
|--------|------|
| `server.port` | HTTP 端口（默认 **8082**） |
| `spring.datasource.*` | MySQL 连接 URL、用户名、密码 |
| `spring.data.redis.*` | Redis 主机、端口、超时等 |
| `openblog.jwt.*` | JWT 密钥、签发方、Access/Refresh 过期时间（秒） |
| `openblog.storage.*` | 本地上传根目录、`public-base-url`（对外访问文件与拼 URL 用）、缩略图长边像素 |
| `openblog.cache.*` | 文章正文缓存 TTL（`article-published-ttl-minutes`，默认 30）、列表缓存 TTL（`article-list-ttl-minutes`，默认 5） |
| `openblog.seo.*` | 站点 URL（`site-url`）、名称（`site-name`）、描述（`site-description`）；百度推送 token（`baidu-push-token`）与开关（`baidu-push-enabled`） |

**务必在部署环境中：**

1. 将 **`openblog.jwt.secret`** 改为足够长且随机的密钥，不要使用示例占位串。
2. 使用环境变量或外部化配置管理数据库与 Redis 密码，避免将生产凭据提交到版本库。
3. 将 **`openblog.storage.root-path`** 设为服务器上可写目录，并保证 **`public-base-url`** 与反向代理/域名一致，否则前端拿到的媒体 URL 可能错误。

**前端 API 基址**

前端通过环境变量 **`VITE_API_BASE`** 指定后端根地址（不带末尾 `/`）。未设置时，代码内存在默认基址，本地开发建议新建 `frontend/.env.development`：

```properties
VITE_API_BASE=http://localhost:8082
```

生产构建可使用 `frontend/.env.production` 或 CI 注入同名变量。

---

## 本地运行

### 1. 准备数据库与 Redis

- 在 MySQL 中创建数据库（名称与 JDBC URL 一致）。
- 启动 Redis 服务。

### 2. 启动后端

在项目根目录执行：

```bash
# Windows
mvnw.cmd spring-boot:run

# Linux / macOS
./mvnw spring-boot:run
```

或使用已安装的 Maven：

```bash
mvn spring-boot:run
```

应用默认监听 **http://localhost:8082**。首次启动时 JPA 会根据实体自动维护表结构（`ddl-auto: update`）。

> **若从旧版本升级**（此前文章正文字段在 `articles` 表中），需在启动后执行 `sql/migrate-article-body.sql` 将正文迁移到 `article_bodies` 表。旧版请求/响应格式完全兼容，迁移不会影响已发布文章的正常访问。

### 3. 启动前端

```bash
cd frontend
npm install
npm run dev
```

按 Vite 终端提示访问本地开发地址（通常为 `http://localhost:5173`）。请确保 **`VITE_API_BASE`** 指向正在运行的后端。

### 4. 生产构建（前端）

```bash
cd frontend
npm run build
```

静态资源输出在 `frontend/dist/`，可由 Nginx 等托管；API 请求仍指向配置的后端地址。

### 5. 打包后端

```bash
mvn -DskipTests package
```

可执行 Jar 位于 `target/` 目录（名称以构建产物为准）。

---

## API 与鉴权简述

- **统一前缀**：`/api/v1`
- **响应封装**：业务接口多使用统一包装（如 `ApiResponse`），具体字段以后端 DTO 为准。
- **公开访问（无需登录）**：例如 `GET /api/v1/home`、`GET /api/v1/articles`、`GET /api/v1/articles/{id}`、`GET /api/v1/articles/{articleId}/comments`、`GET /api/v1/media/**`、`GET /api/v1/profile`、`GET /api/v1/site/config`、`GET /robots.txt`、`GET /sitemap.xml`、`GET /seo/**`，以及 `POST /api/v1/auth/register`、`POST /api/v1/auth/login`、`POST /api/v1/auth/refresh`。
- **需登录**：文章创建/修改/发布/删除、互动类 POST/DELETE、评论发表与删除、`GET/PUT /api/v1/users/me`、`PUT /api/v1/site/config`（站点配置写入）等。请求头携带 **Bearer Token**（实现以 `JwtAuthenticationFilter` 为准）。

详细放行规则见 `SecurityConfig`。

---

## 测试

当前仓库 `src/test` 下暂无单元测试用例。添加测试后可使用：

```bash
mvn test
```

---

## 提交规范

本仓库遵循 [Conventional Commits](https://www.conventionalcommits.org/) 约定，提交信息格式如下：

```
<type>(<scope>): <subject>

[可选正文]

[可选脚注]
```

- **type**（必填）：本次提交的类别，取值见下表。
- **scope**（可选）：影响范围，如 `article`、`comment`、`header`、`theme`、`redis` 等模块或功能名。
- **subject**（必填）：简明扼要的一句话描述，使用祈使句、首字母小写、结尾不加句号，建议不超过 70 字符。

### type 取值

| type | 说明 |
|------|------|
| `feat` | 新增功能 |
| `fix` | 修复缺陷 |
| `refactor` | 重构（既不新增功能也不修复缺陷的代码改动） |
| `style` | 不影响逻辑的样式/格式调整（CSS、代码格式、空白等） |
| `perf` | 性能优化 |
| `docs` | 文档改动 |
| `test` | 新增或修改测试 |
| `build` | 构建系统或依赖变更（Maven、npm、Vite 等） |
| `ci` | CI 配置与脚本变更 |
| `chore` | 其他杂项（不影响源码与测试的维护性改动） |
| `revert` | 回滚此前的提交 |

### 示例

```
feat(project): add project detail view
fix(article): widen content area from 720px to 880px
style(header): rework top bar layout to reduce crowding
refactor(theme): move dark mode toggle to RightDock alongside profile button
docs: add commit convention to README
```

正文可用于补充改动动机与实现要点，多条要点建议使用 `-` 列表。破坏性变更需在脚注中以 `BREAKING CHANGE:` 开头说明。

---

## 相关文档

- [后端功能与架构设计](docs/后端功能与架构设计.md) — 业务模块、接口约定与扩展方向的参考说明。

---

## 许可证

若未在仓库中另行声明许可证文件，使用前请与项目维护者确认授权范围。

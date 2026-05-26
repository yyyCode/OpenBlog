# OpenBlog

面向个人博客场景的全栈项目：后端为 **Spring Boot 3** REST API，前端为 **Vue 3 + Vite** 单页应用。支持文章发布与 Markdown 展示、评论与回复、点赞/收藏/关注、媒体上传与缩略图等能力。

---

## 功能概览

| 模块 | 说明 |
|------|------|
| 账号 | 注册、登录、JWT 刷新、当前用户资料查询与更新 |
| 文章 | 草稿创建/编辑、发布（支持定时发布）、下架、删除、Markdown 导入/导出、公开列表与详情、作者侧「我的文章」与详情；正文独立存储并在发布时预渲染 HTML |
| 搜索 | MySQL FULLTEXT 索引支持文章正文搜索（`article_bodies.content_markdown`） |
| 互动 | 文章点赞/取消、收藏/取消、关注/取消关注 |
| 评论 | 文章下评论列表、发表评论、回复评论、删除评论 |
| 媒体 | 上传、原图/缩略图访问、缩略图元数据查询 |
| 首页 | 聚合数据接口（如 `/api/v1/home`） |
| 公开资料 | `/api/v1/profile`：未登录展示站点作者信息，已登录可优先展示当前用户公开资料 |

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

---

## 技术栈

**后端**

- Java **17**
- Spring Boot **3.5.x**（Web、Validation、Security、Data JPA、Data Redis）
- MyBatis-Plus **3.5.x**（与 JPA 并存，按模块使用）
- MySQL **8**（Hibernate `ddl-auto: update` 便于开发迭代）
- Redis（文章正文缓存、阅读量滑动窗口等）
- JWT（jjwt **0.12.x**）
- MinIO 对象存储（图片上传，可选本地文件系统回退）
- flexmark（服务端 Markdown → HTML 预渲染）
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

---

## 仓库结构

```
OpenBlog/
├── OpenBlog-business/              # 业务模块（Spring Boot）
│   └── src/main/
│       ├── java/com/yqz/openblog/
│       │   ├── article/           # 文章：实体、DTO、服务、导入/导出、定时发布
│       │   │   ├── entity/        # Article, ArticleBody（正文独立存储）
│       │   │   ├── service/       # 文章服务、缓存、阅读量、MarkdownRenderer
│       │   │   └── repo/          # MyBatis-Plus Mapper + JPA Repository
│       │   ├── category/          # 分类
│       │   ├── changelog/         # 更新日志
│       │   ├── comment/           # 评论
│       │   ├── interaction/       # 互动（点赞、收藏、关注）
│       │   ├── media/             # 媒体上传（MinIO / 本地存储 + 缩略图）
│       │   ├── user/              # 用户、认证、JWT
│       │   └── config/            # Spring Security、MyBatis-Plus、缓存配置
│       └── resources/
│           ├── application.yaml   # 服务端口、数据源、Redis、JWT、MinIO 等
│           └── sql/               # 手动 SQL 脚本（迁移、参考数据）
├── vue/                           # Vue 3 前端
│   ├── src/
│   │   ├── api/                   # HTTP 封装
│   │   ├── views/                 # 页面组件
│   │   └── components/            # 通用组件
│   ├── package.json
│   └── vite.config.js
├── docs/                          # 架构与需求说明
├── pom.xml                        # 根 POM（聚合模块）
└── mvnw / mvnw.cmd                # Maven Wrapper（可选）
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
- **公开访问（无需登录）**：例如 `GET /api/v1/home`、`GET /api/v1/articles`、`GET /api/v1/articles/{id}`、`GET /api/v1/articles/{articleId}/comments`、`GET /api/v1/media/**`、`GET /api/v1/profile`，以及 `POST /api/v1/auth/register`、`POST /api/v1/auth/login`、`POST /api/v1/auth/refresh`。
- **需登录**：文章创建/修改/发布/删除、互动类 POST/DELETE、评论发表与删除、`GET/PUT /api/v1/users/me` 等。请求头携带 **Bearer Token**（实现以 `JwtAuthenticationFilter` 为准）。

详细放行规则见 `SecurityConfig`。

---

## 测试

当前仓库 `src/test` 下暂无单元测试用例。添加测试后可使用：

```bash
mvn test
```

---

## 相关文档

- [后端功能与架构设计](docs/后端功能与架构设计.md) — 业务模块、接口约定与扩展方向的参考说明。

---

## 许可证

若未在仓库中另行声明许可证文件，使用前请与项目维护者确认授权范围。

<div align="center">

<img src="vue/public/site-icon.png" width="104" alt="OpenBlog logo" />

<h1>OpenBlog</h1>

<p><strong>一个可以真正跑起来的全栈个人博客系统</strong><br />
Spring Boot 3 微服务后端 · Vue 3 前端 · business / message / gateway 三服务独立部署</p>

<p>
  <a href="https://github.com/yyyCode/OpenBlog/actions/workflows/ci.yml"><img src="https://github.com/yyyCode/OpenBlog/actions/workflows/ci.yml/badge.svg?branch=master" alt="CI" /></a>
  <img src="https://img.shields.io/badge/Java-17-orange?logo=openjdk&logoColor=white" alt="Java 17" />
  <img src="https://img.shields.io/badge/Spring%20Boot-3.5.12-6DB33F?logo=springboot&logoColor=white" alt="Spring Boot" />
  <img src="https://img.shields.io/badge/Spring%20Cloud-2025.0.3-6DB33F?logo=spring&logoColor=white" alt="Spring Cloud" />
  <img src="https://img.shields.io/badge/Vue-3.5-42B883?logo=vuedotjs&logoColor=white" alt="Vue 3" />
  <img src="https://img.shields.io/badge/Vite-8-646CFF?logo=vite&logoColor=white" alt="Vite 8" />
  <img src="https://img.shields.io/badge/Docker-Compose-2496ED?logo=docker&logoColor=white" alt="Docker Compose" />
</p>

<p>
  <a href="https://www.wecode.xin"><strong>🌐 在线体验</strong></a> ·
  <a href="https://github.com/yyyCode/OpenBlog/issues">问题反馈</a> ·
  <a href="docs/ROADMAP.md">路线图</a>
</p>

</div>

---

## 📖 项目简介

OpenBlog 是一个用于学习与实践的个人博客系统，覆盖「写作 → 发布 → 互动 → 运营」的完整链路：文章、评论、论坛、媒体库、站内搜索、管理后台、SEO 和邮件通知都是真实落地的功能，不是占位 demo。

项目刻意采用了**微服务**形态（business / message / gateway + Dubbo + Nacos），把消息异步化、网关鉴权、幂等去重、审计这些通常只在更大体量下才遇到的问题，在个人项目的规模里走了一遍。所以它更适合被当作一个可读、可改、可拆的工程范本，而不是开箱即用的商业成品。

## ✨ 功能特性

| 模块 | 能力 |
| --- | --- |
| 📝 内容 | 文章（草稿 / 发布 / 定时 / 隐藏）、Markdown 渲染（marked + DOMPurify）、分类标签、站内搜索、更新日志、Bug 案例、项目与「小而美公司」推荐 |
| 🖼️ 媒体 | MinIO / 本地磁盘双实现，拖拽上传、缩略图、WebP 转换，删除前校验引用，避免封面 / Logo / 正文破链 |
| 💬 互动 | 评论回复（级联软删、双击幂等）、论坛（话题 + 评论、DFA 敏感词过滤、管理员隐藏）、点赞 / 收藏 / 关注、求职专区 |
| 🔐 账号 | JWT + `ADMIN / AUTHOR / READER` 三级角色、邮箱验证码（阿里云 DirectMail 经 Dubbo 投递）、设备指纹三层防护 |
| 🛠️ 治理 | 管理后台、`@AuditLog` 审计、`@RepeatExecuteLimit` 幂等框架（fail-open）、通知抽象层（EMAIL 已落地，SMS / 飞书 / MQ 预留）、SEO（Thymeleaf SSR + sitemap） |

## 🧩 技术栈

| 层 | 选型 |
| --- | --- |
| 后端 | Java 17 · Spring Boot 3.5.12 · Spring Cloud 2025.0.3 · MyBatis-Plus + JPA · Dubbo + Nacos · JWT · flexmark · Thymeleaf |
| 数据 / 中间件 | MySQL 8 · Redis（Redisson）· MinIO · Elasticsearch（可选，`openblog.search.enabled`） |
| 前端 | Vue 3.5 · Vue Router 4 · Vite 8 · marked + DOMPurify · FingerprintJS · oh-my-live2d |
| 构建 / 部署 | Maven 多模块 · Docker Compose · Nginx · GitHub Actions（自托管 Runner，三链独立） |

## 🏗️ 系统架构

```text
浏览器
  │  HTTP
  ▼
Nginx ── 托管 vue/dist 静态资源，/api 反向代理到网关
  │
  ▼
gateway :8090 ── JWT 粗校验 · 设备指纹 · 限流 · traceId · CORS
  │  HTTP
  ▼
business :8082 ── 文章 / 评论 / 论坛 / 用户 / 认证 / SEO / 媒体 / 审计
  │                    │
  │ Dubbo RPC          └──▶ MySQL 8 · Redis · MinIO
  ▼
message :8083 ── 邮件（阿里云 DirectMail）/ 通知抽象层
```

business 与 message 通过 **Nacos** 注册发现、以 **Dubbo** 互通；邮件、验证码等耗时链路都从主业务链路里摘了出去。

## 📦 模块结构

```text
OpenBlog
├── OpenBlog-common      通用：ApiResponse / PageResult / BizException / 全局异常处理
├── OpenBlog-api         RPC 契约聚合：OpenBlog-message-api
├── OpenBlog-business    主业务服务（:8082）
├── OpenBlog-message     消息服务（:8083，Dubbo :20883）
├── OpenBlog-gateway     API 网关（:8090）
├── OpenBlog-framework   框架封装：framework-redis / -elasticsearch / -audit / -idempotent
├── vue                  前端（Vite 8）：npm run dev / build / preview
├── sql                  建表、迁移与 Mock 数据
├── deploy               三服务 Docker 化部署产物与说明
└── docs                 设计文档、spec 与经验沉淀
```

| 服务 | 端口 |
| --- | --- |
| business | 8082 |
| message | 8083（Dubbo 20883） |
| gateway | 8090 |
| Redis | 6739 |
| 前端 dev server | 5173 |

## 🚀 快速开始

### 环境要求

| 依赖 | 版本 | 说明 |
| --- | --- | --- |
| JDK | 17+ | 必需 |
| Maven | 3.9+ | 必需 |
| Node.js | 18+ | 前端构建 |
| MySQL | 8 | 必需 |
| Redis | 6+ | 必需（默认端口 6739） |
| Nacos | 2.x | business / message 的服务注册与配置 |
| MinIO | — | 可选，`openblog.storage.type=local` 时不需要 |
| 阿里云 DirectMail | — | 可选，不启用邮箱验证码可跳过 |

### 本地启动

```bash
# 1. 建库并导入 sql/ 下的建表脚本（带 migrate- 前缀的是增量迁移，已有库按顺序执行）
mysql -uroot -p -e "CREATE DATABASE openblog DEFAULT CHARSET utf8mb4"

# 2. 可选：复制本地配置模板并按需填入连接信息（该文件已被 git 忽略）
cp OpenBlog-business/src/main/resources/application-local.example.yaml \
   OpenBlog-business/src/main/resources/application-local.yaml

# 3. 启动消息服务（承载邮件 / 验证码；consumer check=false，不起也能跑非邮箱功能）
mvn spring-boot:run -pl OpenBlog-message

# 4. 启动主业务服务（使用本地配置时追加 -Dspring-boot.run.profiles=local）
mvn spring-boot:run -pl OpenBlog-business

# 5. 启动前端：dev 把 /api 代理到本机 8082，与生产 Nginx 反代到网关的行为一致
cd vue && npm install && npm run dev
```

启动后访问：前端 **http://localhost:5173** · 后端 **http://localhost:8082**。

> 中间件地址默认指向内网开发机（见各服务 `application.yaml`），本地运行请通过 `application-local.yaml` 或环境变量覆盖。

### 关键环境变量

| 变量 | 生效服务 | 说明 |
| --- | --- | --- |
| `OPENBLOG_JWT_SECRET` | business · gateway | JWT 签名密钥，**两端必须一致**，否则网关校验全 401 |
| `OPENBLOG_DEVICE_TOKEN_SECRET` | gateway | 设备令牌签名密钥，网关独有，≥32 字符，缺失或过短会 fail-fast |
| `ALIYUN_AK` / `ALIYUN_SK` / `ALIYUN_FROM` | message | 阿里云邮件推送凭据与发件人 |
| `OPENBLOG_SLIDER_ENABLED` | business | 发码滑动验证码开关，默认 `false` |

## 🐳 部署

> [!IMPORTANT]
> **三服务强耦合**
> - business ↔ message 通过 Dubbo 调用：改接口签名必须两端同部署
> - business ↔ gateway 共用 JWT 密钥：改密钥必须两端同部署
>
> 否则 Nacos 里的 provider / consumer 会对不上，business 直接抛 `No provider`。

```bash
mvn -pl OpenBlog-business -am package -DskipTests   # :8082
mvn -pl OpenBlog-message  -am package -DskipTests   # :8083
mvn -pl OpenBlog-gateway  -am package -DskipTests   # :8090
```

- 各服务 Docker 化产物与说明：`deploy/business/README.md`、`deploy/message/`、`deploy/gateway/README.md`
- 前端 `npm run build` 产出 `dist/`，交由 Nginx 托管，`/api` 反代到 gateway `:8090`
- 推送 master 后由 GitHub Actions 按变更链路自动构建部署（`.github/workflows/ci.yml`）

## 📚 文档

| 文档 | 内容 |
| --- | --- |
| [CLAUDE.md](CLAUDE.md) | 开发规范、常用命令、提交 / PR 规则、已知坑位 |
| [docs/ROADMAP.md](docs/ROADMAP.md) | 规划与进度 |
| [踩坑记录](docs/evolution/dev-experiences.md) | Dubbo 重试导致邮件重复 → 幂等三件套的由来 |
| [安全加固](docs/evolution/security-hardening.md) | 鉴权、上传、越权等加固记录 |
| [设备指纹](docs/evolution/设备指纹合法性-网关侧校验与防构造.md) | 网关侧校验与防构造的三层设计 |
| [网关安全与高可用](docs/designs/api-gateway-security-ha.md) | 网关职责边界与限流方案 |
| [幂等框架设计](docs/superpowers/specs/2026-08-29-idempotent-framework-design.md) | `@RepeatExecuteLimit` 完整设计 |
| [通知 MQ 异步](docs/designs/notification-mq-async.md) | 通知抽象层与异步化演进 |

## 🤝 参与贡献

1. 从 `master` 切分支，命名 `<type>/<短横线 slug>`，例如 `feat/home-forum-entry`
2. 提交信息用中文 conventional commit（`feat | fix | harden | style | docs | refactor | build`）
3. 提交前跑通对应模块构建：后端 `mvn -pl <module> -am package`，前端 `cd vue && npm run build`
4. 一律走 PR，正文写明「变更内容 / 涉及文件 / 验证」，合并使用 merge commit

细节见 [CLAUDE.md](CLAUDE.md)。

## 📄 许可证

仓库暂未附带 `LICENSE` 文件，默认保留所有权利。使用、分发或二次开发前，请先联系维护者确认授权范围。

---

<div align="center">

<sub>如果这个项目对你有帮助，欢迎点一个 ⭐ Star</sub>

</div>

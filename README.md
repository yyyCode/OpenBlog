# OpenBlog

全栈个人博客系统：**Spring Boot 3 微服务后端** + **Vue 3 前端**，支撑 [www.wecode.xin](https://www.wecode.xin)。

生产架构为 **business / message / gateway 三服务**（Dubbo + Nacos 互通），每条 CI 链独立 Docker 部署。

## 核心功能

- **内容**：文章（草稿 / 发布 / 定时 / 隐藏，Markdown 经 `marked + dompurify` 渲染）、更新日志、Bug 案例、项目与「小而美公司」推荐、站内搜索
- **媒体**：MinIO / 本地存储，拖拽上传、缩略图、WebP；删除前校验引用，避免封面 / Logo / 正文破链
- **互动**：评论回复（级联软删、双击幂等）、论坛（话题 + 评论、DFA 敏感词、管理员隐藏）、点赞收藏关注、求职专区
- **账号**：JWT + `ADMIN / AUTHOR / READER` 三级角色，邮箱验证码（阿里云 DirectMail，经 Dubbo），设备指纹三层防护
- **治理**：管理后台、`@AuditLog` 审计、`@RepeatExecuteLimit` 幂等框架（fail-open）、通知抽象层（EMAIL 已落地，SMS / 飞书 / MQ 预留）、SEO（Thymeleaf SSR、sitemap）

## 技术栈

| 端 | 技术 |
|----|------|
| 后端 | Java 17 · Spring Boot 3.5.12 · Spring Cloud 2025.0.3 · MyBatis-Plus + JPA · Dubbo + Nacos · MySQL 8 · Redis（Redisson） · JWT · MinIO · flexmark · Thymeleaf |
| 前端 | Vue 3.5 · Vue Router 4 · Vite 8 · marked + dompurify · FingerprintJS · oh-my-live2d |
| 部署 / CI | Docker Compose · GitHub Actions（自托管 Runner，三链独立） · Nginx 反代 |

## 模块结构

```
OpenBlog-common      通用：ApiResponse、PageResult、BizException、统一异常处理
OpenBlog-api         API 聚合：OpenBlog-message-api（Message RPC 契约与 DTO）
OpenBlog-business     主业务服务（8082）：文章/评论/论坛/公司/用户/认证/SEO/媒体/审计
OpenBlog-message      消息服务（8083，dubbo 20883）：邮件（阿里云 DirectMail）+ 通知抽象层
OpenBlog-gateway      API 网关（8090）：JWT 粗校验 + 设备指纹三层防护 / 限流 / traceId / CORS
OpenBlog-framework    框架封装：framework-redis / -elasticsearch / -audit / -idempotent
vue                    Vue 3 前端（Vite 8），npm run dev / build / preview
sql                    数据库脚本：建表、迁移、Mock 数据
deploy                 各服务 Docker 化部署产物与说明（business / message / gateway）
docs                   设计文档、spec、经验沉淀
```

服务端口：**business 8082** · **message 8083**（dubbo 20883）· **gateway 8090** · Redis 6739。

## 快速开始

依赖：JDK 17、Maven 3.9、Node 18+、MySQL 8、Redis、Nacos；邮件验证码链路还需 message 服务与阿里云 DirectMail 配置。中间件地址默认指向内网开发机（见各服务 `application.yaml`），本地跑请按需覆盖。

```bash
# 1. 建库 openblog 并执行 sql/ 下建表脚本；Redis 端口见配置（默认 6739）

# 2. 启动消息服务（承载邮件/验证码，consumer check=false，不启也能跑非邮箱功能）
mvn spring-boot:run -pl OpenBlog-message

# 3. 启动主业务服务
mvn spring-boot:run -pl OpenBlog-business

# 4. 启动前端（dev 把 /api 代理到本机 8082，与生产 Nginx 反代到网关行为一致）
cd vue && npm install && npm run dev
```

后端 **http://localhost:8082**，前端 **http://localhost:5173**。

## 部署

**三服务强耦合**：business ↔ message 经 Dubbo（改接口签名需两端同部署）；business ↔ gateway 共用 JWT / 设备令牌密钥（改密钥需两端同部署）。**涉及任一耦合的变更，三服务必须同步部署**，否则 Nacos 中 provider / consumer 对不上，business 抛 "No provider"。

```bash
mvn -pl OpenBlog-business -am package -DskipTests   # 8082，push master 自动部署
mvn -pl OpenBlog-message  -am package -DskipTests   # 8083
mvn -pl OpenBlog-gateway  -am package -DskipTests   # 8090，部署需注入 OPENBLOG_DEVICE_TOKEN_SECRET
```

Docker 化部署见 `deploy/business/README.md`、`deploy/message/`、`deploy/gateway/README.md`；前端 `npm run build` 产出 `dist/` 交由 Nginx 托管，`/api` 反代到 gateway 8090。

## 更多

- 开发规范、构建命令、提交/PR 规则、已知坑位：[`CLAUDE.md`](CLAUDE.md)
- 规划与进度：[`docs/ROADMAP.md`](docs/ROADMAP.md)
- 踩坑记录：[`docs/evolution/dev-experiences.md`](docs/evolution/dev-experiences.md)
- 许可证：使用前请与项目维护者确认授权范围

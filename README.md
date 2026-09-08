# OpenBlog

全栈个人博客系统：**Spring Boot 3 微服务后端** + **Vue 3 前端**，支撑 [www.wecode.xin](https://www.wecode.xin)。围绕「内容发布 + 社区互动」展开：文章、评论、论坛、小而美公司推荐、项目与更新日志，并内置设备指纹防护、幂等框架、审计追踪等工程化治理。

生产架构为 **business / message / gateway 三服务**（Dubbo + Nacos 互通），每条 CI 链独立 Docker 部署。

---

## 核心功能

**内容与发布**
- **文章**：草稿 / 发布 / 定时发布 / 隐藏；Markdown 编辑，前端 `marked + dompurify` 渲染（XSS 过滤）；16:9 封面、正文插图；站内搜索
- **内容型页面**：更新日志、Bug 案例、项目推荐、「小而美公司」推荐（数据来源标注）
- **媒体**：MinIO / 本地存储，拖拽上传、缩略图、**WebP 支持**；删除前先校验引用，避免封面 / Logo / 正文破链

**互动与社区**
- **评论 / 回复**：级联软删整棵回复子树，按实删条数递减计数；双击去重（幂等）
- **论坛**：话题 + 评论、DFA 敏感词过滤、管理员隐藏；首页论坛入口实时话题预览
- **求职专区**：小而美公司推荐列表 / 详情 + 管理端增删改查（公开端 permitAll，管理端 ADMIN）
- **互动**：点赞、收藏、关注

**账号与安全**
- **JWT** 认证 + `ADMIN / AUTHOR / READER` 三级角色 + 后台注册/作者审核
- **邮箱验证码**：注册与改密均强制真实发送邮件验证码（阿里云 DirectMail，经 Dubbo + Nacos）
- **设备指纹防护（3 层）**：网关签名设备令牌签发与续签 + 指纹轮换 / 洪泛检测，限流桶以签名 `deviceId` 为身份；business 记录「账号 × 设备」绑定并对设备级失败封禁

**管理与治理**
- **管理后台（console）**：仪表盘站点统计、文章、分类、评论、用户审核、公司 / 项目 / 论坛 / 更新日志 / 意见反馈、附件、站点配置
- **审计**：`@AuditLog` 注解自动记录操作快照与耗时（可插拔 SPI）
- **幂等框架**：`@RepeatExecuteLimit` + AOP，本地锁 + Redisson 分布式锁 + Redis 标识双重检测，fail-open（Redis 故障降级放行、绝不 500）
- **通知抽象层**：`NotificationChannel` 策略 + 模板渲染 + ChannelRegistry 路由，当前落地 EMAIL（经 Dubbo 直发、带幂等键 + `email_records` 唯一索引防重放），SMS / 飞书 / MQ 为预留扩展位

**站点体验**
- SEO：Thymeleaf SSR、Open Graph、sitemap.xml（百度推送默认关闭待配置）
- 站点配置后台动态编辑；Live2D 看板娘

---

## 技术栈

| 端 | 技术 |
|----|------|
| 后端 | Java 17 · Spring Boot 3.5.12 · Spring Cloud 2025.0.3 · MyBatis-Plus 3.5 + Spring Data JPA · Dubbo + Nacos · MySQL 8 · Redis（Redisson） · JWT（jjwt） · MinIO · flexmark · Thymeleaf · sensitive-word |
| 前端 | Vue 3.5 · Vue Router 4 · Vite 8 · marked + dompurify · FingerprintJS · oh-my-live2d |
| 部署 / CI | Docker Compose · GitHub Actions（自托管 Runner，三链独立） · Nginx 反代 |

---

## 模块结构

```
OpenBlog-common      通用：ApiResponse、PageResult、BizException、统一异常处理
OpenBlog-api          API 聚合：OpenBlog-message-api（Message RPC 契约与 DTO）
OpenBlog-business     主业务服务（8082）：文章/评论/论坛/公司/用户/认证/SEO/媒体/审计
OpenBlog-message      消息服务（8083，dubbo 20883）：邮件（阿里云 DirectMail）+ 通知抽象层
OpenBlog-gateway      API 网关（8090）：JWT 粗校验 + 设备指纹三层防护 / 限流 / traceId / 统一错误兜底 / CORS
OpenBlog-framework    框架封装（父聚合）
├ framework-redis       RedisOps、Key 管理、Lua 滑动窗口限流
├ framework-elasticsearch  Elasticsearch 索引与搜索封装
├ framework-audit       @AuditLog 注解 + AOP + SPI 审计扩展点
└ framework-idempotent  @RepeatExecuteLimit 幂等注解 + AOP + 分布式锁 + Redis 标识
vue                    Vue 3 前端（Vite 8），npm run dev / build / preview
sql                    数据库脚本：建表、迁移、Mock 数据
deploy                 各服务 Docker 化部署产物与说明（business / message / gateway）
docs                   设计文档、spec、经验沉淀
```

服务端口：**business 8082** · **message 8083**（dubbo 20883）· **gateway 8090** · Redis 6739。

---

## 快速开始（本地开发）

依赖：JDK 17、Maven 3.9、Node 18+、MySQL 8、Redis、Nacos；邮件验证码链路还需 message 服务与阿里云 DirectMail 配置（`openblog.message.aliyun`）。中间件地址/账号默认指向内网开发机（见各服务 `application.yaml`），本地跑请按需覆盖 host / 账号。

```bash
# 1. 建库并初始化
#    建 MySQL 库 openblog，执行 sql/ 下建表脚本；Redis 端口见配置（默认 6739）

# 2. 启动消息服务（先于 business，承载邮件/验证码；consumer check=false，不启也能跑非邮箱功能）
mvn spring-boot:run -pl OpenBlog-message

# 3. 启动主业务服务
mvn spring-boot:run -pl OpenBlog-business

# 4. 启动前端（dev 把 /api 代理到本机 8082，与生产 Nginx 反代到网关行为一致）
cd vue && npm install && npm run dev
```

本地默认地址：后端 **http://localhost:8082**，前端 **http://localhost:5173**。构建验证 `npm run build`（生成 `dist/` 即通过）。

---

## 部署

**三服务强耦合**：business ↔ message 经 Dubbo（改接口签名需两端同部署）；business ↔ gateway 共用 JWT / 设备令牌密钥（改密钥需两端同部署）。**涉及任一耦合的变更，三服务必须同步部署**；只升其一会在 Nacos 中出现 provider / consumer 对不上，business 抛 "No provider"。

```bash
# business（Web 服务，8082）—— CI 已接入：push master 自动 Docker 部署
mvn -pl OpenBlog-business -am package -DskipTests
# Docker 化部署见 deploy/business/README.md

# message（消息服务，8083）—— CI 已接入
mvn -pl OpenBlog-message -am package -DskipTests
# Docker 化部署见 deploy/message/

# gateway（API 网关，8090）—— CI 已接入；部署需注入网关设备令牌密钥（OPENBLOG_DEVICE_TOKEN_SECRET）
mvn -pl OpenBlog-gateway -am package -DskipTests
# Docker 化部署见 deploy/gateway/README.md

# 前端：npm run build 产出 dist/，交由 Nginx 托管，/api 反代到 gateway 8090
```

CI/CD：push master 自动构建并 Docker 部署 business / message / gateway（GitHub Actions + 自托管 Runner，三条链独立互不影响）。

---

## 路线图与待办

当前规划与进度详见 [`docs/ROADMAP.md`](docs/ROADMAP.md)。已知未完结项：

- [ ] SEO 收录完善：`openblog.seo.baidu-push-enabled` 默认关闭、推送 token 为空，待配 AK/Token 与百度 / Google 收录验证
- [ ] 审计记录管理后台页面（记录已落库，缺可视化页面）
- [ ] 单元测试覆盖（当前聚焦幂等框架与网关验签等核心场景）

---

## 开发与协作

详细开发规范见 **`CLAUDE.md`**（模块构建命令、改前必跑验证、提交/分支/PR 规则、已知坑位与经验沉淀）。约定摘要：

- 提交遵循 Conventional Commits，中文标题，type 取仓库实际使用集合：`feat | fix | harden | style | docs | refactor | build`，scope 取模块/端（`frontend / project / small-company / comment / media / security / gateway / ...`）
- 功能 / 修复分支从 `master` 切出（`<type>/<slug>`），走 PR 合回 master（merge commit，保留原子提交历史）
- 触及 business ↔ message（Dubbo）或 business ↔ gateway（JWT）耦合的改动，PR 需注明**三服务同步部署**

---

## 许可证

使用前请与项目维护者确认授权范围。

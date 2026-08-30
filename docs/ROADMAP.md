# OpenBlog 项目规划

> 最近更新:2026-08-18
> 目的:梳理近期开发任务优先级与范围,作为后续 spec/plan 的输入。
> 每个任务开工前,建议用 brainstorming 细化设计 → writing-plans 出实现计划 → subagent-driven 执行(与 OpenBlog-common 抽取同一流程)。

---

## 任务一览(按优先级)

| # | 任务 | 状态 | 目标 |
|---|------|------|------|
| 1 | 完善 email 服务,真正实现邮箱注册验证 | ✅ 已完成 | 注册流程真实发送验证邮件;通知抽象层(统一渠道,当前 email 直发) |
| 2 | 首页前端动态化 | 🔲 待排期 | 设计动态首页 |
| 3 | 部署 Docker 化 + 启动加速 | 🔲 待排期 | 后端 docker 启动,加快启动 |

---

## 任务 1:邮箱验证注册(最高优先级)

### 现状(已核实)

- **注册流程不发邮件**:`AuthService.register()`(`OpenBlog-business/.../user/service/AuthService.java:69`)流程为
  滑块验证 → 邮箱格式白名单(`AllowedMailbox`:QQ/网易/谷歌系)→ 用户名/邮箱唯一性 → **直接创建 ACTIVE READER → 返回 token**(注册即登录)。
- **email 基础设施已就绪,只差接线**:
  - RPC 契约 `EmailRpcService.send(EmailSendRequest)`(`OpenBlog-api/OpenBlog-message-api/`)
  - Provider:`EmailRpcServiceImpl` `@DubboService`(message 模块),委托 `EmailService.send()`,经阿里云 DirectMail 真实发送,落 `email_record` 表
  - **business 侧目前没有 `@DubboReference` 消费调用**(全库检索确认)——链路已通但未接入
- Redis 封装可用(`OpenBlog-framework-redis`,`RedisOps`),适合存验证码。

### 建议范围

1. **business 接入 Dubbo 消费**:新增 `@DubboReference` 注入 `EmailRpcService`(consumer 配置 `application.yaml` 已就绪)。
2. **验证码发送接口**:`POST /api/v1/auth/email-code`(邮箱 + 滑块 challenge)
   - 生成 6 位数字验证码;Redis 存储(`email:code:{email}`),TTL 5 分钟;发送冷却 60 秒。
   - 调 `EmailRpcService.send()` 发"OpenBlog 注册验证码"模板邮件。
3. **注册接口校验验证码**:`RegisterRequest` 增加 `code` 字段;校验通过才创建用户。
4. **email 模块**:新增注册验证码邮件模板(HTML),可配置站点名/验证码。
5. **前端注册页**(`vue/src/views/`,注册组件):邮箱输入 → "获取验证码"按钮(60s 倒计时)→ 提交注册。

### 推荐决策:验证码前置

先验证邮箱(发码 → 校验)再建号,而非"建号后激活"。理由:避免邮箱占用、符合"注册即登录"现状,改动集中在 auth 流程。

### 验收标准

- [ ] 注册流程真实发出验证邮件(本地可观察 email_record 记录)
- [ ] 验证码错误 / 过期 / 重发冷却均有明确提示
- [ ] business → email 的 Dubbo 调用打通(日志可见)

### 开放问题(开工前确认)

- 验证码**前置** vs **建号后激活**(现有 `PendingUser` 后台审核是管理员审核概念,是否要与此结合?)
- email 模块现有 HTTP 管理接口(`/api/v1/email/*`)是否保留?
- 验证码邮件是否需要图形验证码之外的防刷(滑块已有,是否足够)?

### 完成情况(2026-08-28)

- 注册验证码链路已闭环:business `EmailCodeService`(验证码生成 / Redis 存储 / 冷却 / 校验)→ 统一通知抽象层 → email 模块 Dubbo 直发(幂等:retries=0 + 幂等键 + `email_records.idempotency_key` 唯一索引)。
- **通知抽象层**(`com.yqz.openblog.notification`):`NotificationChannel` 策略 + `AbstractNotificationChannel` 模板方法 + `NotificationTemplateService` 占位符渲染 + `ChannelRegistry` 路由 + `NotificationService` 门面。当前只实现 EMAIL 渠道(经 Dubbo),SMS / 飞书 / MQ 为预留扩展位,接入时主链路零改动。
- 重放问题与幂等设计详见 `docs/evolution/dev-experiences.md`(2026-08-27)。

---

## 任务 2:首页前端动态化

### 现状(已核实)

- 首页组件:`vue/src/views/HomeView.vue`。
- 此前相关设计:`2026-07-21-home-hero-image`(Hero 图)、`2026-07-22-project-recommendation`(项目推荐)、`2026-06-23` 系列飞书主题重构。
- business 已提供文章/论坛/项目/互动/站点配置等 API,首页可接真实数据。

### 建议范围

1. **信息架构**:Hero(动态化)→ 最新文章流 → 推荐项目墙 → 站点数据(文章数/评论数/访问) → 最新动态/评论。
2. **动态效果**:滚动渐入、鼠标视差、动态渐变背景、骨架屏;Live2D 看板娘已有,保留。
3. **真实数据接入**:文章列表、项目推荐、站点配置(`/api/v1/...`)替代静态占位。
4. **响应式 + 性能**:懒加载、图片优化、首屏指标。

### 验收标准

- [ ] 首页为动态渲染(数据来自接口,非写死)
- [ ] 动效流畅,无首屏卡顿
- [ ] 桌面/移动端响应式可用

### 开放问题(开工前确认)

- "动态前端页面"侧重**视觉动效**还是**内容动态加载**,还是两者?
- 在现有飞书主题上迭代,还是重新设计风格?

---

## 任务 3:部署 Docker 化 + 启动加速

### 现状(已核实)

- **前端**:已 Docker 化 —— Nginx 容器(`vue/Dockerfile`、`vue/docker.sh`),宿主端口 `18088 → 80`,CI 在 Runner 构建镜像 → 上传 → `docker.sh` 加载更新。
- **后端**:宿主机 **systemd** `java -jar`(`OpenBlog-business-1.0.0-SNAPSHOT.jar`,`-Xmx1024M -Xms256M`),由 self-hosted Runner(`openblog-backend`)部署(`.github/workflows/ci.yml`)。
- **基础设施**:MySQL / Redis / MinIO / Nacos 位于 `10.21.76.221`(局域网,宿主或独立机器)。

### 建议范围

1. **后端 Dockerfile**(`OpenBlog-business`):基于 JRE 17 镜像;分层构建(依赖层缓存);非 root 用户;healthcheck。
2. **docker-compose 编排**:business + email 服务;MySQL/Redis/MinIO/Nacos 作为外部依赖通过环境变量注入,保持容器外。
3. **启动加速**(Spring Boot + JVM 层面):
   - `spring.main.lazy-initialization=true`(或按 bean 选择性懒加载)
   - JVM 调优:`-XX:+UseG1GC`(或 ZGC)、合理 `-Xms/-Xmx`、显式 dump 参数
   - 可选:AppCDS / 镜像分层缓存加速部署
   - 依赖瘦身:排查未用 starter
4. **CI 改造**:构建后端镜像 → 上传服务器 → `docker compose up -d`(替换 systemd);保留回滚(镜像 tag)。

### 验收标准

- [ ] `docker compose up -d` 一键启动后端服务
- [ ] 记录优化前后启动耗时对比
- [ ] CI 部署走 Docker,且失败可回滚

### 开放问题(开工前确认)

- 是否也容器化 **email** 服务(建议一起,同机编排)?
- MySQL/Redis/MinIO/Nacos 留在容器外还是纳入编排?
- "加快启动速度"目标值(如:冷启动 < 15s / 部署总耗时减半)?

---

## 通用待办(非当前优先级)

- 单元测试补充(README 待办项)。
- 通知服务化(未来):本地消息表 + MQ 异步消费 + 延时队列重试,替换 `NotificationService.submit` 的同步发送实现;短信 / 飞书渠道按同一 Channel 抽象扩展。完整设计见 `docs/designs/notification-mq-async.md`。
- 版本号收敛到根 pom `dependencyManagement`。
- `MybatisPlusMetaObjectHandler` 迁移至 framework 模块(common 抽取时遗留)。

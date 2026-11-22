# 文档索引

本目录按**文档性质**分层，先看本页再进子目录，避免「现行设计 / 外部参考 / 流程产物」混读。

> 最近整理：2026-11-23（补全 spec/plan 导航、核对 ROADMAP 状态）

## 目录约定

| 目录 | 放什么 | 不放什么 |
|---|---|---|
| `designs/` | OpenBlog 自身的设计与评估 | 外部项目原文、实现步骤 |
| `reference/` | 外部项目或通用方案参考（可移植） | 本项目已拍板的决策 |
| `superpowers/specs/` | 按日期命名的项目设计 spec | 外部参考原文 |
| `superpowers/plans/` | 与 spec 对应的实现计划 | 新的设计决策 |
| `evolution/` | 已发生的经验沉淀（踩坑、安全加固、演进） | 未来规划 |
| `bugs/` | 具体线上/开发 bug 的现象、根因、修复 | 设计讨论 |
| `architecture/` | 架构图、时序图、出图 prompt | 文字设计说明 |
| `samples/` | 可导入的演示数据 | 生产数据 |
| `articles/` | 对外发布的文章存档 | 内部设计 |

## 规划与架构

- [ROADMAP.md](ROADMAP.md) — 任务优先级与进度（任务 1–2 已完成，任务 3 仅完成容器化，2026-11-23 核对）
- [architecture/](architecture/README.md) — 系统架构图与出图 prompt

## 现行设计（docs/designs/）

| 文档 | 主题 |
|---|---|
| [api-gateway-security-ha.md](designs/api-gateway-security-ha.md) | 是否引入 API 网关的评估、选型与不依赖网关的加固清单 |
| [load-testing-design.md](designs/load-testing-design.md) | 压测方案：接口上限、鉴权负担、瓶颈定位 |
| [notification-mq-async.md](designs/notification-mq-async.md) | 通知服务化规划（本地消息表 + MQ + 延时重试，已实施并迁入 message 服务） |
| [service-extraction-analysis.md](designs/service-extraction-analysis.md) | 各业务域拆分独立服务的评估与顺序 |

## 项目设计 spec 与实现计划（docs/superpowers/）

spec 多数按 `YYYY-MM-DD-主题-design.md` 命名（个别省略 `-design` 后缀）；实现计划在 `plans/` 下，一般同名（早期个别带 `-plan` 后缀）。
**「计划」列为 `—`** 表示实现期直接开工、未回写计划，不代表未实现。

### 基础设施与架构

| 日期 | 设计 | 实现计划 | 说明 |
|---|---|---|---|
| 2026-08-09 | [openblog-common](superpowers/specs/2026-08-09-openblog-common-design.md) | [plan](superpowers/plans/2026-08-09-openblog-common.md) | 公共模块抽取 |
| 2026-08-29 | [gateway-module](superpowers/specs/2026-08-29-gateway-module-design.md) | [plan](superpowers/plans/2026-08-29-gateway-module.md) | Spring Cloud Gateway 网关模块 |
| 2026-08-29 | [notification-service-split](superpowers/specs/2026-08-29-notification-service-split-design.md) | [plan](superpowers/plans/2026-08-29-notification-service-split.md) | 通知服务拆分进 message |
| 2026-08-31 | [logging-file-persistence](superpowers/specs/2026-08-31-logging-file-persistence-design.md) | — | 日志文件持久化 + 错误日志缺口 |

### 安全与风控

| 日期 | 设计 | 实现计划 | 说明 |
|---|---|---|---|
| 2026-08-29 | [idempotent-framework](superpowers/specs/2026-08-29-idempotent-framework-design.md) | [plan](superpowers/plans/2026-08-29-idempotent-framework.md) | 通用幂等组件 `@RepeatExecuteLimit` |
| 2026-08-31 | [device-fingerprint-ratelimit](superpowers/specs/2026-08-31-device-fingerprint-ratelimit-design.md) | [plan](superpowers/plans/2026-08-31-device-fingerprint-ratelimit.md) | 设备指纹 + IP 复合限流 |
| 2026-09-22 | [slider-captcha](superpowers/specs/2026-09-22-slider-captcha-design.md) | — | 发码接口滑动验证码 |
| 2026-10-20 | [sensitive-word-filter](superpowers/specs/2026-10-20-sensitive-word-filter-design.md) | — | 敏感词过滤（AC + Trie 双引擎） |
| 2026-10-20 | [sensitive-word-engine-hot-switch](superpowers/specs/2026-10-20-sensitive-word-engine-hot-switch.md) | — | 敏感词引擎热切换 |

### 内容与业务

| 日期 | 设计 | 实现计划 | 说明 |
|---|---|---|---|
| 2026-07-22 | [project-recommendation](superpowers/specs/2026-07-22-project-recommendation-design.md) | [plan](superpowers/plans/2026-07-22-project-recommendation-plan.md) | 项目推荐 |
| 2026-07-30 | [article-hidden-delete](superpowers/specs/2026-07-30-article-hidden-delete-design.md) | [plan](superpowers/plans/2026-07-30-article-hidden-delete.md) | 文章隐藏状态与物理删除 |
| 2026-07-31 | [article-type-job-nav](superpowers/specs/2026-07-31-article-type-job-nav-design.md) | — | 文章类型字段与招聘导航 |
| 2026-08-31 | [small-company-backend](superpowers/specs/2026-08-31-small-company-backend-design.md) | — | 小而美公司后端（表结构 + CRUD + 管理端） |

### 管理后台

| 日期 | 设计 | 实现计划 | 说明 |
|---|---|---|---|
| 2026-06-02 | [admin-user-management](superpowers/specs/2026-06-02-admin-user-management-design.md) | [plan](superpowers/plans/2026-06-02-admin-user-management.md) | 后台用户管理 |
| 2026-06-02 | [site-config](superpowers/specs/2026-06-02-site-config-design.md) | [plan](superpowers/plans/2026-06-02-site-config.md) | 站点配置动态化 |

### 前端页面与主题

| 日期 | 设计 | 实现计划 | 说明 |
|---|---|---|---|
| 2026-06-23 | [blog-feishu-theme-overhaul](superpowers/specs/2026-06-23-blog-feishu-theme-overhaul.md) | [plan](superpowers/plans/2026-06-23-blog-feishu-theme-overhaul.md) | 博客前台飞书蓝白主题 |
| 2026-06-23 | [console-feishu-redesign](superpowers/specs/2026-06-23-console-feishu-redesign.md) | [plan](superpowers/plans/2026-06-23-console-feishu-redesign.md) | 管理台飞书风格改版 |
| 2026-06-23 | [site-auth-feishu-redesign](superpowers/specs/2026-06-23-site-auth-feishu-redesign.md) | [plan](superpowers/plans/2026-06-23-site-auth-feishu-redesign.md) | 登录/注册页改版 |
| 2026-07-21 | [home-hero-image](superpowers/specs/2026-07-21-home-hero-image-design.md) | [plan](superpowers/plans/2026-07-21-home-hero-image.md) | 首页 Hero 图片 |
| 2026-08-28 | [homepage-fullscreen-sections](superpowers/specs/2026-08-28-homepage-fullscreen-sections-design.md) | [plan](superpowers/plans/2026-08-28-homepage-fullscreen-sections.md) | 首页两屏全屏滚动 |
| 2026-08-28 | [about-us-page](superpowers/specs/2026-08-28-about-us-page-design.md) | [plan](superpowers/plans/2026-08-28-about-us-page.md) | 关于我们页面 |
| 2026-08-28 | [user-profile-page](superpowers/specs/2026-08-28-user-profile-page-design.md) | [plan](superpowers/plans/2026-08-28-user-profile-page.md) | 个人中心页 |

## 外部参考（docs/reference/）

| 文档 | 来源与用途 |
|---|---|
| [gateway-module-overview.md](reference/gateway-module-overview.md) | 大麦购票网关模块整体设计，本项目网关取其子集 |
| [gateway-param-security.md](reference/gateway-param-security.md) | 大麦网关参数非对称加密 + 签名验证 |
| [idempotent-design.md](reference/idempotent-design.md) | 大麦幂等组件（本地锁 + 分布式锁 + 缓存标识符） |
| [sensitive-word-universal.md](reference/sensitive-word-universal.md) | 通用敏感词方案（版本化快照 + 不可变匹配器 + 原子替换） |

## 经验与排障

- [evolution/dev-experiences.md](evolution/dev-experiences.md) — 踩坑与解法（按日期追加）
- [evolution/security-hardening.md](evolution/security-hardening.md) — 中低危安全修复与纵深防御
- [evolution/设备指纹限流优化.md](evolution/设备指纹限流优化.md) — 网关限流升级为设备指纹 + IP
- [evolution/设备指纹合法性-网关侧校验与防构造.md](evolution/设备指纹合法性-网关侧校验与防构造.md) — 指纹合法性校验与防构造（三层）
- [bugs/debug-log.md](bugs/debug-log.md) — 典型 bug 的现象、根因、修复与验证

## 其他

- [samples/sensitive-word-import-10000.txt](samples/sensitive-word-import-10000.txt) — 1 万条敏感词导入样例（每行一词）
- [articles/OpenBlog项目介绍博客文章.md](articles/OpenBlog项目介绍博客文章.md) — 对外发布的项目介绍文章存档

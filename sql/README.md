# SQL 脚本

脚本均为**手动执行**（项目未引入 Flyway/Liquibase）。按用途分三层，执行前先备份数据库。

| 目录 | 用途 | 可重复执行 |
|---|---|---|
| `schema/` | 建表 + 首次部署所需的初始数据 | 是（`CREATE TABLE IF NOT EXISTS` / `INSERT IGNORE`） |
| `migrations/` | 已上线库的一次性结构变更 | 否（重跑通常报列/索引已存在） |
| `seeds/` | 演示与案例数据 | 否（显式主键或自增插入，重跑会产生重复数据） |

> 多数表也可由 JPA `ddl-auto` 自动创建；`schema/` 脚本用于手动初始化、对齐字段与补充初始数据，二者字段应保持一致。

## schema/ — 建表与初始数据

| 文件 | 表 | 备注 |
|---|---|---|
| [audit-records.sql](schema/audit-records.sql) | `audit_records` | 审计记录 |
| [category.sql](schema/category.sql) | `article_categories` | 文章分类 |
| [email-records.sql](schema/email-records.sql) | `email_records` | message 服务独占 |
| [feedback.sql](schema/feedback.sql) | `feedback_entries` | 按「用户 + 天」限额的现行结构 |
| [forum.sql](schema/forum.sql) | `forum_topics`、`forum_comments` | 论坛 |
| [media_folder.sql](schema/media_folder.sql) | `media_folders` | 媒体文件夹 |
| [notification-outbox.sql](schema/notification-outbox.sql) | `notification_outbox` | 通知 Outbox，与 business 同库 |
| [projects.sql](schema/projects.sql) | `projects` | 项目推荐 |
| [rbac-init.sql](schema/rbac-init.sql) | `rbac_*` 5 张表 + `users` | 建表并给 `id=1` 用户授全权限、提为 ADMIN |
| [sensitive-word.sql](schema/sensitive-word.sql) | `sensitive_word`、`sensitive_word_version`、`sensitive_word_change_log` | 词条 + 版本快照 + 变更日志 |
| [site-config.sql](schema/site-config.sql) | `site_config` | 含默认配置项 |
| [small_companies.sql](schema/small_companies.sql) | `small_companies` | 小公司推荐 |

首次部署按上表顺序执行即可，除 `feedback.sql` 与 `email-records.sql` 外无相互依赖。

## migrations/ — 一次性迁移

按依赖顺序执行；同一行内箭头表示必须先后执行。

| 顺序 | 文件 | 变更 | 前置 / 注意 |
|---|---|---|---|
| 1 | [migrate-article-type.sql](migrations/migrate-article-type.sql) | `articles` 增加 `type` | 无 |
| 2 | [migrate-article-body.sql](migrations/migrate-article-body.sql) | 新建 `article_bodies` 并迁移正文 | **先部署新版代码**再执行 |
| 3 | [migrate-article-body-drop-legacy-columns.sql](migrations/migrate-article-body-drop-legacy-columns.sql) | 删除 `articles` 旧正文列 | 确认第 2 步数据迁移完成并备份 |
| 4 | [migrate-article-deleted-to-hidden.sql](migrations/migrate-article-deleted-to-hidden.sql) | `DELETED` → `HIDDEN` | 无 |
| 5 | [migrate-feedback-add-status.sql](migrations/migrate-feedback-add-status.sql) | `feedback_entries` 增加 `status` | 需已执行 `schema/feedback.sql` |
| 6 | [migrate-email-records-idempotency-key.sql](migrations/migrate-email-records-idempotency-key.sql) | `email_records` 增加幂等键 + 唯一索引 | 部署顺序：本脚本 → message → business |
| 7 | [migrate-feedback-user-scope.sql](migrations/migrate-feedback-user-scope.sql) | 反馈限额从 IP 改为登录用户 | 需已执行第 5 步；重跑的 1060/1061 报错可忽略 |

## seeds/ — 演示数据

| 文件 | 内容 | 注意 |
|---|---|---|
| [forum-mock-data.sql](seeds/forum-mock-data.sql) | 论坛话题与评论 | 假设 `users.id=1` 为 admin；显式主键，重跑会主键冲突 |
| [bug-case-dubbo-replay.sql](seeds/bug-case-dubbo-replay.sql) | 一篇「Dubbo 重试致邮件重复」案例文章 | 假设 `users.id=1`；自增主键，重跑会产生重复文章 |

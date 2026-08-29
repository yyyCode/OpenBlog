-- Bug 案例种子数据：Dubbo 默认重试导致验证码邮件重复发送
-- 假设已有用户 id=1 为 admin（与 forum-mock-data.sql 一致）
-- 执行一次即可；幂等：articles 用自增主键，重复执行会产生第二条相同文章（可先 DELETE 后重插）。

INSERT INTO articles (title, summary, status, type, published_at, author_id, view_count, like_count, favorite_count, comment_count, created_at, updated_at) VALUES
('Dubbo 默认重试导致验证码邮件重复发送', '注册页点击一次「获取验证码」却收到 3 封完全相同的邮件。根因：Dubbo 消费端默认 retries=2，阿里云真实发信耗时超 1s 触发超时，网络层把同一请求重发了 2 次，非幂等的发信副作用被重复执行。', 'PUBLISHED', 'BUG_CASE', '2026-08-30 10:00:00', 1, 0, 0, 0, 0, '2026-08-30 10:00:00', '2026-08-30 10:00:00');

SET @bug_article_id = LAST_INSERT_ID();

INSERT INTO article_bodies (article_id, content_markdown, content_html, word_count) VALUES
(@bug_article_id, '## 现象\n\n注册页点击一次「获取验证码」，收到了 **3 封完全相同的验证码邮件**——验证码和正文一模一样。\n\n## 根因\n\n- Dubbo 消费端**默认 `retries=2`**：首次调用超时/失败后自动重试 2 次，一共 3 次调用；默认 `timeout=1000ms`。\n- 阿里云真实发信耗时经常超过 1s → 消费端超时 → Dubbo 在网络层**把同一个请求重新发给 provider** → 邮件服务的 `EmailService.send()` 被完整执行 3 遍 → 3 封邮件。\n- 发邮件是**非幂等副作用**：重复执行 = 重复投递。\n\n## 排查关键\n\n3 封邮件**验证码完全相同** → 排除「点了 3 次获取」（每次点击会生成不同的随机验证码）。同码 + 同内容 = **同一次业务逻辑被重复执行**，由此定位到是 Dubbo 重试，而不是前端重复请求。\n\n## 修复：三层防线\n\n| 层 | 方案 |\n|----|------|\n| 1 | 消费端关闭默认重试：`@DubboReference(retries=0, timeout=5000)` |\n| 2 | provider 幂等去重：按 `idempotencyKey` 查 `email_records`，命中直接返回已有记录、不重发 |\n| 3 | `email_records.idempotency_key` **唯一索引**硬兜底：并发双插 / 进程崩溃 / 时间重放都不重发 |\n\n调用方（business）一次 `sendCode` 生成一个 **UUID 幂等键**随 RPC 传入；复用 Redis 里未过期的验证码，避免重发作废旧邮件。\n\n部署顺序：先执行迁移 SQL 加唯一索引 → 再部署 email 服务 → 最后部署 business。\n\n## 认知教训：为什么分布式锁解决不了这个问题\n\n- **Dubbo 重试发生在消费端代理层**：业务代码只执行一次，重试是网络层把同一请求重新投递给 provider，业务侧加锁根本拦不住 provider 被重复执行。\n- 锁解决的是**并发互斥**，这里是**同一请求被重复投递、副作用重复执行**，需要的是**幂等**，两者不是一回事。\n- 锁有 TTL 过期、进程崩溃丢锁、「几小时后重放」时锁早已不存在的缺陷。\n- **DB 唯一索引本身就是内置的 race-safe 分布式互斥**：同时做到「并发只有一个成功」和「重放返回已有结果」，跨进程崩溃、跨时间均有效，是这类问题的最强解。\n\n## 通用经验\n\n1. Dubbo/微服务调用任何**非幂等副作用**（发邮件、发短信、扣款、生成订单）都必须：要么显式 `retries=0`，要么做幂等，二者都做最稳。\n2. 幂等设计三件套：调用方生成**幂等键**（一次逻辑操作一个键）→ 接收方按幂等键**去重** → 存储层**唯一索引**兜底并发竞态。\n3. 排查「重复副作用」：先看重复对象里是否有业务标识（验证码/订单号/流水号）。标识相同 = 同一逻辑被重放；标识不同 = 被触发多次，能快速二分定位是「重试/重放」还是「重复请求」。', NULL, 0);

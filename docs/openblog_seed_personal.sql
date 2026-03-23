-- OpenBlog 个人博客模式：种子数据（头像 + 用户 + 文章 + 评论）
-- 使用方式：
-- 1) 先确保已执行 docs/openblog_mysql.sql 建表
-- 2) 再执行本文件
--
-- 注意：
-- - 当前项目前端 `ProfileCard` 先用占位信息展示；后续如果你希望从数据库读取头像/签名，我再帮你加“公开个人资料接口”并对接。
-- - 文章封面 cover_media_key 设为 NULL，这样无需额外上传媒体文件即可正常显示正文。

USE openblog;

-- ==============
-- 1) 用户（作者）
-- ==============
-- password=123456 的 BCrypt hash（仅用于登录校验不直接报错）
-- hash: $2b$10$bSlok5nag8XkP0hXYktlk.WdOfaBShV0SQnlPaT6mFDRz29QkZabi
INSERT INTO users (
  id, username, email, password_hash, nickname, avatar_url, bio, role, status, created_at, updated_at
) VALUES (
  1, 'FUNKY', 'funky@example.com',
  '$2b$10$bSlok5nag8XkP0hXYktlk.WdOfaBShV0SQnlPaT6mFDRz29QkZabi',
  'FUNKY', 'https://via.placeholder.com/256x256.png?text=FUNKY',
  '平凡的一枚程序员', 'AUTHOR', 'ACTIVE',
  NOW(6), NOW(6)
);

-- ==============
-- 2) 文章（PUBLISHED）
-- ==============
INSERT INTO articles (
  id, title, summary, content_markdown, cover_media_key, category_id, status,
  published_at, submitted_at, reviewed_at, rejected_reason,
  author_id, like_count, favorite_count, comment_count, created_at, updated_at
) VALUES
(
  1, 'Seata 分布式事务 - Raft 模式完整解析',
  '围绕“Seata Raft 模式”的核心组件与运行流程，从整体架构到关键点逐步拆解。',
  '## Seata 也能玩 Raft？\n\nSeata 是一套面向分布式事务的解决方案。相比传统的一阶段/二阶段协议，Raft 模式更像是把关键状态交由一致性协议来维护。\n\n### 一、核心目标\n\n- 降低事务状态丢失或并发冲突带来的不可控风险\n- 让全局事务的关键决策拥有一致性保障\n\n### 二、整体流程\n\n1. 客户端发起全局事务（Global Transaction）\n2. 事务协调相关组件进入一致性状态机\n3. 提交/回滚决策在 Raft 协调下达成\n4. 参与者执行本地操作并反馈结果\n\n### 三、你需要重点理解的点\n\n- 事务状态的“变更”不是简单写库，而是要经由一致性机制\n- 网络延迟与成员变更如何影响决策传播\n- 超时与重试的边界条件（MVP 阶段建议先保证链路与数据模型正确）\n\n```text\nMVP 建议：\n- 先把页面和接口联通\n- 再把业务数据跑通\n- 最后再优化性能与容错\n```\n\n### 四、结论\n\nRaft 模式让事务决策拥有更强的一致性语义。对于个人博客项目而言，理解“状态如何在分布式环境达成一致”同样能帮助你做更可靠的后端设计。\n',
  NULL, NULL, 'PUBLISHED',
  '2024-12-19 10:00:00', NULL, NULL, NULL,
  1, 55, 33, 5, NOW(6), NOW(6)
),
(
  2, '我的第一篇博客',
  '记录一次从 0 到 1 搭建个人博客的实践：后端、数据库、接口到前端页面。',
  '## Hello World\n\n欢迎来到我的个人博客。\n\n这是一篇演示用的正文（Markdown）。你可以在这里看到：\n\n- 标题\n- 分段\n- 代码块\n- 列表\n\n> 小技巧：后端返回 `markdown`，前端用 `marked` 渲染。\n\n```js\nfunction hello(){\n  return \"openblog\";\n}\n```\n\n感谢阅读！\n',
  NULL, NULL, 'PUBLISHED',
  '2024-12-09 09:30:00', NULL, NULL, NULL,
  1, 12, 8, 2, NOW(6), NOW(6)
),
(
  3, '如何把博客做成“个人可用”的系统',
  '不是社区，而是一个稳定展示自己内容的平台：权限、接口、页面与工程化取舍。',
  '## 个人博客的 MVP\n\n做个人博客时，通常你不需要复杂的社区功能。\n\n### 建议 MVP 范围\n\n- 文章列表/详情\n- JWT 登录（作者发布）\n- 本地文件存储（封面/图片）\n- 评论（可选，但这里支持多级回复）\n\n### 取舍\n\n- 不引入 ES：先用 MySQL LIKE/索引解决搜索\n- 不做审核：作者自己发布\n- 不做版本回滚：先确保发布与展示稳定\n\n最后：愿你写作顺滑、部署省心。\n',
  NULL, NULL, 'PUBLISHED',
  '2024-11-28 18:00:00', NULL, NULL, NULL,
  1, 6, 3, 0, NOW(6), NOW(6)
);

-- ==============
-- 3) 评论（APPROVED）
-- ==============
-- 文章 1：构造 5 级多级回复链（depth=5）
INSERT INTO comments (
  id, article_id, user_id, parent_id, content, status, created_at, updated_at
) VALUES
  (1, 1, 1, NULL, '写得太好了，状态流转讲清楚了！', 'APPROVED', NOW(6), NOW(6)),
  (2, 1, 1, 1, '继续看了下一段，Raft 状态机那块很关键。', 'APPROVED', NOW(6), NOW(6)),
  (3, 1, 1, 2, '对超时和重试边界的总结很实用。', 'APPROVED', NOW(6), NOW(6)),
  (4, 1, 1, 3, '多级回复的深度限制也不错，防滥用。', 'APPROVED', NOW(6), NOW(6)),
  (5, 1, 1, 4, '收藏了，等你后续写补充文章。', 'APPROVED', NOW(6), NOW(6));

-- 文章 2：2 条顶级评论
INSERT INTO comments (
  id, article_id, user_id, parent_id, content, status, created_at, updated_at
) VALUES
  (10, 2, 1, NULL, '很喜欢你的风格，继续更！', 'APPROVED', NOW(6), NOW(6)),
  (11, 2, 1, NULL, 'Markdown 渲染效果很不错。', 'APPROVED', NOW(6), NOW(6));


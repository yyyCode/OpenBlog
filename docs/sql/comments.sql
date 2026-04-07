-- OpenBlog comments table (MySQL 8 / utf8mb4)
-- 对应实体：src/main/java/com/yqz/openblog/comment/entity/Comment.java

CREATE TABLE IF NOT EXISTS `comments` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `article_id` BIGINT NOT NULL,
  `user_id` BIGINT NOT NULL,
  `parent_id` BIGINT NULL,
  `content` TEXT NOT NULL,
  `status` VARCHAR(16) NOT NULL DEFAULT 'APPROVED',
  `created_at` TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_comments_article_id` (`article_id`),
  KEY `idx_comments_parent_id` (`parent_id`),
  KEY `idx_comments_article_parent_created` (`article_id`, `parent_id`, `created_at`)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci;

-- 说明：
-- - parent_id 为 NULL 表示顶层评论；否则表示回复某条评论。
-- - status 目前与枚举 CommentStatus 对齐：APPROVED / DELETED（软删）。

-- =========================
-- 演示数据（可选）
-- =========================
-- 注意：请先确保对应的 article_id 与 user_id 在你的库里真实存在。
-- 你可以先用下面两条查询确认：
--   SELECT id, title FROM articles ORDER BY id DESC LIMIT 5;
--   SELECT id, username, nickname FROM users ORDER BY id DESC LIMIT 5;

-- 将下方的 1、2 替换为你真实的文章/用户 ID
SET @demo_article_id := 1;
SET @demo_user_a := 1;
SET @demo_user_b := 2;

-- 顶层评论
INSERT INTO comments(article_id, user_id, parent_id, content, status)
VALUES
  (@demo_article_id, @demo_user_a, NULL, '第一条评论：这篇文章写得很清晰，学到了！', 'APPROVED'),
  (@demo_article_id, @demo_user_b, NULL, '第二条评论：能否补充一下实际部署的细节？', 'APPROVED');

-- 回复（挂到上一条顶层评论）
SET @top1 := LAST_INSERT_ID() - 1;
INSERT INTO comments(article_id, user_id, parent_id, content, status)
VALUES
  (@demo_article_id, @demo_user_b, @top1, '我也想看部署部分，尤其是反代和静态资源配置。', 'APPROVED');

-- 楼中楼（回复上面的回复）
SET @reply1 := LAST_INSERT_ID();
INSERT INTO comments(article_id, user_id, parent_id, content, status)
VALUES
  (@demo_article_id, @demo_user_a, @reply1, '好的，我后面补一篇部署踩坑记录。', 'APPROVED');


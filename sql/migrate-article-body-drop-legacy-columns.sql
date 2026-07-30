-- 删除 articles 表上已迁移至 article_bodies 的正文列
-- 执行前请确认 migrate-article-body.sql 已完成（article_bodies 表存在且数据已迁移）
-- 执行前请备份数据库

ALTER TABLE articles DROP COLUMN content_markdown;

-- 若旧表仍有 content_html 列，取消下行注释后执行
-- ALTER TABLE articles DROP COLUMN content_html;

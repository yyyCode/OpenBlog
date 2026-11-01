-- 为 articles 表添加 type 字段，默认值为 ARTICLE
ALTER TABLE articles ADD COLUMN type VARCHAR(16) NOT NULL DEFAULT 'ARTICLE' AFTER status;

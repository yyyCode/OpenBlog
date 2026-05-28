-- ===============================================================
-- 文章正文表分离迁移
-- 执行顺序：先部署新版代码 → 再执行此脚本
-- 执行前请备份数据库
-- ===============================================================

-- 1. 创建 article_bodies 表
CREATE TABLE IF NOT EXISTS article_bodies (
    article_id       BIGINT NOT NULL PRIMARY KEY,
    content_markdown MEDIUMTEXT NOT NULL,
    content_html     MEDIUMTEXT NULL,
    word_count       INT DEFAULT 0,
    created_at       TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at       TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_article_bodies_article FOREIGN KEY (article_id) REFERENCES articles(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. 迁移现有文章正文
-- 旧文章的 content_html 留空，新版代码首次读取时会惰性渲染并回填
INSERT INTO article_bodies (article_id, content_markdown, content_html, word_count, created_at, updated_at)
SELECT
    a.id,
    a.content_markdown,
    NULL,
    0,
    a.created_at,
    a.updated_at
FROM articles a
WHERE a.content_markdown IS NOT NULL
  AND a.content_markdown != ''
  AND NOT EXISTS (
      SELECT 1 FROM article_bodies b WHERE b.article_id = a.id
  );

-- 3. 添加全文索引（用于搜索）
-- 注意：MySQL FULLTEXT 索引仅在 InnoDB + utf8mb4 下支持
ALTER TABLE article_bodies ADD FULLTEXT INDEX ft_article_bodies_markdown (content_markdown);

-- 4. 确认迁移成功后，删除旧列（新建文章 INSERT 不再写入这些列，保留 NOT NULL 列会导致保存失败）
-- 也可单独执行 migrate-article-body-drop-legacy-columns.sql
ALTER TABLE articles DROP COLUMN content_markdown;

-- 文章分类表（JPA ddl-auto 也会自动建表；此脚本供手动初始化或参考）
CREATE TABLE IF NOT EXISTS article_categories (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(80) NOT NULL,
    parent_id BIGINT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    INDEX idx_article_categories_parent_id (parent_id)
);

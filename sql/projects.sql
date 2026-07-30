-- projects.sql
CREATE TABLE IF NOT EXISTS projects (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    title           VARCHAR(120) NOT NULL COMMENT '项目名称',
    summary         VARCHAR(255)  COMMENT '项目简介',
    content_markdown TEXT         COMMENT 'Markdown 正文',
    cover_media_key VARCHAR(64)   COMMENT '封面图 media key',
    tech_stack      VARCHAR(255)  COMMENT '技术栈（逗号分隔）',
    project_url     VARCHAR(512)  COMMENT '项目链接',
    github_url      VARCHAR(512)  COMMENT '源码链接',
    sort_order      INT DEFAULT 0 COMMENT '排序值（越小越靠前）',
    status          VARCHAR(16) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT / PUBLISHED',
    published_at    DATETIME      COMMENT '发布时间',
    created_at      DATETIME NOT NULL,
    updated_at      DATETIME NOT NULL,
    INDEX idx_projects_status_sort (status, sort_order, published_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 站点配置表（key-value 结构，支持后台编辑）
CREATE TABLE IF NOT EXISTS site_config (
    id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    config_key  VARCHAR(64)  NOT NULL COMMENT '配置键',
    config_value TEXT        NULL     COMMENT '配置值',
    updated_at  DATETIME     NULL     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_config_key (config_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='站点配置表';

-- 初始默认数据
INSERT IGNORE INTO site_config (config_key, config_value) VALUES
('github_url',       'https://github.com/yyyCode'),
('csdn_url',         'https://blog.csdn.net/2301_80044822'),
('nowcoder_url',     'https://www.nowcoder.com/users/597303882'),
('source_code_url',  'https://github.com/yyyCode/OpenBlog.git'),
('ai_platform_url',  'http://ai.wecode.xin/#/chat/default'),
('blog_name',        '烧仙草冰室'),
('hero_title',       '设计，创造，思考未来'),
('hero_subtitle',    '探索 AI、设计与技术的交集\n分享关于智能交互、AI 驱动产品与数字创新的实战经验。'),
('about_text',       '这里是个人博客，用来记录设计、技术与思考。'),
('default_avatar_url','https://via.placeholder.com/120x120.png?text=OpenBlog'),
('site_start_date',  '2026-03-20'),
('footer_copyright', '© 2026 OpenBlog');

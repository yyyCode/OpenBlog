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
('hero_title',       '热爱技术 持续生长'),
('hero_subtitle',    '探索 AI、设计与技术的交集\n分享关于智能交互、AI 驱动产品与数字创新的实战经验。'),
('about_text',       '这里是个人博客，用来记录设计、技术与思考。'),
('default_avatar_url','https://via.placeholder.com/120x120.png?text=OpenBlog'),
('site_start_date',  '2026-03-20'),
('footer_copyright', '© 2026 OpenBlog'),
('hero_image_url', ''),
('job_intention',    '后端 / 全栈开发工程师'),
('contact_email',    '2678785492@qq.com'),
-- 关于页第二屏的开源贡献列表，JSON 数组字符串；在后台「站点设置 › 开源贡献」编辑
-- （后台按行编辑、保存时序列化，这里只是首次部署的初始值；JSON 内没有单引号，无需转义）
('about_opensource', '[{"name":"Apache Dubbo","role":"Maintainer","url":"https://github.com/apache/dubbo/pulls?q=author%3AyyyCode"},{"name":"Higress","role":"Contributor","url":"https://github.com/alibaba/higress/pulls?q=author%3AyyyCode"},{"name":"Nacos","role":"Contributor","url":"https://github.com/alibaba/nacos/pulls?q=author%3AyyyCode"},{"name":"MyBatis-Plus","role":"Contributor","url":"https://github.com/baomidou/mybatis-plus/pulls?q=author%3AyyyCode"},{"name":"Redisson","role":"Contributor","url":"https://github.com/redisson/redisson/pulls?q=author%3AyyyCode"},{"name":"Sa-Token","role":"Contributor","url":"https://github.com/dromara/Sa-Token/pulls?q=author%3AyyyCode"}]');

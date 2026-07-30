-- 论坛 Mock 数据（假设已有用户 id=1 为 admin）

-- 话题 1
INSERT INTO forum_topics (id, title, content, status, author_id, view_count, comment_count, created_at, updated_at) VALUES
(1, '欢迎来到微码平台论坛！', '大家好！这是微码平台的论坛板块。\n\n欢迎大家在这里：\n- 分享技术心得\n- 讨论行业动态\n- 提问求助\n- 闲聊吹水\n\n希望大家在这里玩得开心！🎉', 'PUBLISHED', 1, 128, 3, '2026-07-20 09:00:00', '2026-07-20 09:00:00');

-- 话题 2
INSERT INTO forum_topics (id, title, content, status, author_id, view_count, comment_count, created_at, updated_at) VALUES
(2, 'Spring Boot 3.5 新特性讨论', 'Spring Boot 3.5 发布了一段时间了，大家有没有在生产环境用上的？\n\n个人感觉这几个特性很不错：\n1. **虚拟线程支持** - 性能提升明显\n2. **AOT 编译优化** - 启动速度更快\n3. **Observability 改进** - 可观测性更好了\n\n有没有踩过坑的朋友分享下经验？', 'PUBLISHED', 1, 256, 2, '2026-07-22 14:30:00', '2026-07-22 14:30:00');

-- 话题 3
INSERT INTO forum_topics (id, title, content, status, author_id, view_count, comment_count, created_at, updated_at) VALUES
(3, 'Vue 3 Composition API 最佳实践', '最近在重构项目，全面迁移到 Composition API。\n\n总结了一些个人觉得好的实践：\n\n1. **用 `useXxx` 命名组合函数**\n2. **避免在 setup 里写太多逻辑，抽成 composables**\n3. **ref vs reactive 的选择**\n   - 基本类型用 ref\n   - 对象用 reactive（但要注意解构问题）\n\n大家有什么补充的吗？', 'PUBLISHED', 1, 189, 1, '2026-07-25 16:45:00', '2026-07-25 16:45:00');

-- 话题 4
INSERT INTO forum_topics (id, title, content, status, author_id, view_count, comment_count, created_at, updated_at) VALUES
(4, '远程办公 VS 坐班，你怎么选？', '最近公司在推行混合办公模式，想听听大家的看法。\n\n**远程办公优点：**\n- 省通勤时间\n- 更自由的工作节奏\n- 可以边旅行边工作\n\n**坐班优点：**\n- 沟通效率高\n- 更容易建立团队氛围\n- 工作生活有明确边界\n\n目前我个人倾向于混合模式（一周 2-3 天远程）。你们呢？', 'PUBLISHED', 1, 312, 0, '2026-07-28 10:15:00', '2026-07-28 10:15:00');

-- 话题 5（被隐藏/下架的示例）
INSERT INTO forum_topics (id, title, content, status, author_id, view_count, comment_count, created_at, updated_at) VALUES
(5, '测试违规内容', '这里曾经是一条违规内容，已被管理员隐藏。', 'HIDDEN', 1, 15, 0, '2026-07-29 08:00:00', '2026-07-30 09:00:00');

-- 话题 6
INSERT INTO forum_topics (id, title, content, status, author_id, view_count, comment_count, created_at, updated_at) VALUES
(6, '求推荐好用的 Markdown 编辑器', '除了 VS Code 和 Typora，大家还有没有什么推荐的 Markdown 编辑器？\n\n需求：\n- 支持实时预览\n- 最好有文件管理器\n- 支持自定义主题\n- 免费或买断制（不要订阅制）\n\n目前试过：\n- Obsidian（功能强大但略复杂）\n- Notion（太重了）\n- MarkText（功能偏少）\n\n求推荐！🙏', 'PUBLISHED', 1, 95, 0, '2026-07-30 11:20:00', '2026-07-30 11:20:00');

-- Mock 评论数据
INSERT INTO forum_comments (id, topic_id, content, author_id, status, created_at) VALUES
(1, 1, '来了来了！第一个报道 🎉', 1, 'APPROVED', '2026-07-20 10:00:00'),
(2, 1, '支持！希望论坛越来越热闹', 1, 'APPROVED', '2026-07-20 11:30:00'),
(3, 1, '有没有技术交流板块？想讨论后端架构', 1, 'APPROVED', '2026-07-21 08:15:00'),

(4, 2, '我们在生产环境跑了两个月了，虚拟线程确实香！QPS 提升了 40%', 1, 'APPROVED', '2026-07-22 18:00:00'),
(5, 2, 'AOT 编译的坑：有些动态代理的场景不支持，要注意排查', 1, 'APPROVED', '2026-07-23 09:30:00'),

(6, 3, '补充一点：composable 尽量保持单一职责，不要一个 useXxx 里做太多事', 1, 'APPROVED', '2026-07-26 10:00:00');

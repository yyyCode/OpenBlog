-- 将 status='DELETED' 的文章迁移为 'HIDDEN'
-- 执行前请确认备份数据
UPDATE articles SET status = 'HIDDEN' WHERE status = 'DELETED';

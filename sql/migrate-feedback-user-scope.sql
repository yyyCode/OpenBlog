-- 迁移：反馈限额从「按 IP 每天一次」改为「按登录用户每天一次」
-- 前置：库中已有旧结构 feedback_entries（执行过旧版 sql/feedback.sql）
--
-- 说明：
-- 1) MySQL 8 不支持 ADD COLUMN IF NOT EXISTS / DROP INDEX IF EXISTS，本脚本为一次性迁移，
--    重复执行会因「列已存在 / 索引不存在」报错，可忽略这类错误。
-- 2) 脚本不删任何数据；历史行的 user_id 为 NULL（无归属用户可回填）。
-- 3) Hibernate ddl-auto=update 只会补建新唯一键、不会删除旧唯一键，故本脚本为必跑项。

-- 1) 新增 user_id：历史数据留 NULL（MySQL 唯一索引不对 NULL 去重，历史行之间不会互撞）
ALTER TABLE feedback_entries
  ADD COLUMN user_id BIGINT NULL COMMENT '提交用户 ID（新数据必填；迁移前历史数据为 NULL）' AFTER id;

-- 2) 新唯一键：同一用户每天一次（并发双击的 DB 兜底）
ALTER TABLE feedback_entries
  ADD UNIQUE KEY uk_feedback_user_day (user_id, submit_day);

-- 3) 删旧唯一键：不删则同一 NAT 出口下的多个用户仍互相占用名额，正是本次要修的问题
ALTER TABLE feedback_entries
  DROP INDEX uk_feedback_ip_day;

-- 4) ip_key 语义降级为审计/风控字段，注释同步
ALTER TABLE feedback_entries
  MODIFY COLUMN ip_key VARCHAR(80) NOT NULL COMMENT '提交来源 IP 摘要，仅用于审计/风控，不参与唯一性';

-- 回滚（如需）：
-- ALTER TABLE feedback_entries ADD UNIQUE KEY uk_feedback_ip_day (ip_key, submit_day);
-- ALTER TABLE feedback_entries DROP INDEX uk_feedback_user_day;
-- ALTER TABLE feedback_entries DROP COLUMN user_id;

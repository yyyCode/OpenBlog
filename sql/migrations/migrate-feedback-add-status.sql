-- 若你之前已执行过 feedback.sql（没有 status 字段），请再执行本迁移。
ALTER TABLE feedback_entries
  ADD COLUMN IF NOT EXISTS status VARCHAR(16) NOT NULL DEFAULT 'PENDING' AFTER submit_day;


-- 问题反馈表（MySQL 8+）
-- 限额维度：同一登录用户每天一次（uk_feedback_user_day），ip_key 仅作审计/风控字段
CREATE TABLE IF NOT EXISTS feedback_entries (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NULL COMMENT '提交用户 ID（新数据必填；迁移前历史数据为 NULL）',
  ip_key VARCHAR(80) NOT NULL COMMENT '提交来源 IP 摘要，仅用于审计/风控，不参与唯一性',
  submitter_name VARCHAR(50) NOT NULL,
  content TEXT NOT NULL,
  submit_day DATE NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_feedback_user_day (user_id, submit_day),
  KEY idx_feedback_created_at (created_at),
  KEY idx_feedback_submit_day (submit_day)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

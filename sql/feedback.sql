-- 问题反馈表（MySQL 8+）
CREATE TABLE IF NOT EXISTS feedback_entries (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  ip_key VARCHAR(80) NOT NULL,
  submitter_name VARCHAR(50) NOT NULL,
  content TEXT NOT NULL,
  submit_day DATE NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_feedback_ip_day (ip_key, submit_day),
  KEY idx_feedback_created_at (created_at),
  KEY idx_feedback_submit_day (submit_day)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


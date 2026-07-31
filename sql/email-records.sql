-- 邮件发送记录表（OpenBlog-email 服务独占）
CREATE TABLE IF NOT EXISTS email_records (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    recipient VARCHAR(128) NOT NULL COMMENT '收件人',
    subject VARCHAR(256) NOT NULL COMMENT '主题',
    body TEXT NOT NULL COMMENT '正文',
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING / SENT / FAILED',
    error_msg VARCHAR(512) COMMENT '失败原因',
    request_id VARCHAR(64) COMMENT '阿里云 DirectMail 请求 ID',
    sent_at DATETIME COMMENT '发送时间',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_status_created (status, created_at DESC),
    INDEX idx_recipient (recipient)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

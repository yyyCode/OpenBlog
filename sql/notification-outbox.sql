-- 通知本地消息表（Transactional Outbox）
-- 业务库（business 同一库）。submitAsync 与业务动作同事务写入，Relay 扫 PENDING 发布到 MQ，
-- 保证「业务提交」与「通知入队」原子，消息不丢失。消费端按 message_id 幂等，不重复。
-- 说明：与 OpenBlog-email 的 email_records 职责不同 —— 那是发送结果记录，这是待投递任务。
CREATE TABLE IF NOT EXISTS notification_outbox (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    message_id VARCHAR(64) NOT NULL COMMENT '全局幂等键（UUID），MQ 消息与消费去重共用',
    channel VARCHAR(16) NOT NULL COMMENT 'EMAIL / SMS / FEISHU',
    recipient VARCHAR(128) NOT NULL COMMENT '接收方（邮箱/手机号/webhook）',
    subject VARCHAR(256) COMMENT '主题',
    template_code VARCHAR(64) NOT NULL COMMENT '模板 code，经 NotificationTemplateService 渲染',
    params_json JSON COMMENT '模板参数',
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING'
        COMMENT 'PENDING待发布 / PUBLISHED已发布 / SENT已送达 / FAILED失败重试中 / DEAD死信',
    retry_count INT NOT NULL DEFAULT 0,
    next_retry_at DATETIME COMMENT '下次重试时间',
    last_error VARCHAR(512) COMMENT '最近一次失败原因',
    sent_at DATETIME COMMENT '送达时间',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_message_id (message_id),
    INDEX idx_status_retry (status, next_retry_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

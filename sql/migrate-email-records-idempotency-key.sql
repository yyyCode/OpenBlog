-- 迁移：email_records 增加幂等键列 + 唯一索引（防 Dubbo 重试/重放导致重复发信）
-- 生产库执行一次即可；幂等键为业务方生成的 UUID，历史记录留空不影响查询/展示。
-- 注意：MySQL 中 UNIQUE 索引对 NULL 值不去重（NULL 可重复），历史无幂等键的行不受影响。

ALTER TABLE email_records
    ADD COLUMN idempotency_key VARCHAR(64) NULL COMMENT '幂等键（业务方生成，如 UUID）。provider 据此去重，防止同一逻辑请求被重试/重放时重复发送' AFTER request_id;

ALTER TABLE email_records
    ADD UNIQUE INDEX uk_idempotency_key (idempotency_key);

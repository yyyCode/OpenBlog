-- 敏感词过滤：工作集 + 不可变版本快照 + 变更日志
-- 版本号只增不减；回滚也生成新版本。
-- word 唯一键保持 utf8mb4_0900_ai_ci：运行时匹配忽略大小写，abc/ABC 视为同一词条；
-- 仅重音不同的词（café/cafe）也会被视为重复，运营侧按同一词处理。

CREATE TABLE IF NOT EXISTS `sensitive_word` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `word`       VARCHAR(128)    NOT NULL COMMENT '词条，trim 后存储，禁止换行/控制字符',
  `category`   VARCHAR(64)     NOT NULL DEFAULT 'default' COMMENT '分类，供运营筛选',
  `status`     TINYINT         NOT NULL DEFAULT 1 COMMENT '1=启用（参与过滤） 0=停用/豁免（原 allow-words 语义）',
  `remark`     VARCHAR(255)    DEFAULT NULL,
  `created_by` VARCHAR(64)     NOT NULL COMMENT '操作人用户名，解析失败时存 userId',
  `updated_by` VARCHAR(64)     NOT NULL,
  `created_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sensitive_word` (`word`),
  KEY `idx_sensitive_word_category_status` (`category`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='敏感词工作集（草稿 + 当前内容）';

CREATE TABLE IF NOT EXISTS `sensitive_word_version` (
  `id`             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增 ID 即版本号',
  `kind`           VARCHAR(16)     NOT NULL DEFAULT 'PUBLISH' COMMENT 'PUBLISH / ROLLBACK',
  `status`         VARCHAR(16)     NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING / PUBLISHED / FAILED',
  `source_version` BIGINT UNSIGNED DEFAULT NULL COMMENT '回滚来源版本',
  `word_count`     INT             NOT NULL,
  `checksum`       CHAR(64)        NOT NULL COMMENT '快照明文 SHA-256',
  `snapshot_blob`  LONGBLOB        NOT NULL COMMENT 'gzip 压缩的换行分隔词表',
  `change_summary` VARCHAR(512)    DEFAULT NULL COMMENT '{"added":3,"removed":1,"updated":0}',
  `remark`         VARCHAR(255)    DEFAULT NULL COMMENT '发布备注',
  `created_by`     VARCHAR(64)     NOT NULL,
  `published_at`   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_sensitive_word_version_published_at` (`published_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COMMENT='不可变版本快照（版本号只增不减）';

CREATE TABLE IF NOT EXISTS `sensitive_word_change_log` (
  `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `word`        VARCHAR(128)    NOT NULL COMMENT '批量/回滚动作用 * 作为占位',
  `action`      VARCHAR(16)     NOT NULL COMMENT 'ADD / UPDATE / DELETE / IMPORT / ROLLBACK',
  `before_json` JSON            DEFAULT NULL,
  `after_json`  JSON            DEFAULT NULL,
  `version_id`  BIGINT UNSIGNED DEFAULT NULL COMMENT '发布后回填',
  `operator`    VARCHAR(64)     NOT NULL,
  `operated_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_sensitive_word_log_word_time` (`word`, `operated_at`),
  KEY `idx_sensitive_word_log_version` (`version_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='词库操作审计，只增不改';

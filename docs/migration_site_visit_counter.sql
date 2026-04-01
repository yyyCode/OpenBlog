-- 已有库升级：全站访问计数表（执行一次即可）
USE openblog;

CREATE TABLE IF NOT EXISTS site_visit_counter (
  id TINYINT NOT NULL PRIMARY KEY,
  visit_count BIGINT NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO site_visit_counter (id, visit_count) VALUES (1, 0)
  ON DUPLICATE KEY UPDATE id = id;

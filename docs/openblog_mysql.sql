-- OpenBlog MySQL 初始化脚本（基于当前代码中的 JPA 实体）
-- 使用方式：
--   1) 执行前先确认 MySQL 版本为 8.x
--   2) 用你的客户端（Navicat / mysql 命令行）运行本文件

CREATE DATABASE IF NOT EXISTS openblog
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_0900_ai_ci;

USE openblog;

-- ----------------------------
-- users
-- ----------------------------
CREATE TABLE IF NOT EXISTS users (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  username VARCHAR(32) NOT NULL,
  email VARCHAR(64) NOT NULL,
  password_hash VARCHAR(120) NOT NULL,
  nickname VARCHAR(32) NULL,
  avatar_url VARCHAR(255) NULL,
  bio VARCHAR(512) NULL,
  role VARCHAR(16) NOT NULL,
  status VARCHAR(16) NOT NULL,
  created_at TIMESTAMP(6) NOT NULL,
  updated_at TIMESTAMP(6) NOT NULL,
  UNIQUE KEY uk_users_username (username),
  UNIQUE KEY uk_users_email (email),
  KEY idx_users_username (username),
  KEY idx_users_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------
-- refresh_tokens
-- ----------------------------
CREATE TABLE IF NOT EXISTS refresh_tokens (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  token_hash VARCHAR(128) NOT NULL,
  expires_at TIMESTAMP(6) NOT NULL,
  revoked_at TIMESTAMP(6) NULL,
  created_at TIMESTAMP(6) NOT NULL,
  UNIQUE KEY uk_refresh_tokens_token_hash (token_hash),
  KEY idx_refresh_tokens_user (user_id),
  CONSTRAINT fk_refresh_tokens_user
    FOREIGN KEY (user_id) REFERENCES users(id)
    ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------
-- articles
-- ----------------------------
CREATE TABLE IF NOT EXISTS articles (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  title VARCHAR(120) NOT NULL,
  summary VARCHAR(255) NULL,
  content_markdown TEXT NOT NULL,
  cover_media_key VARCHAR(64) NULL,
  category_id BIGINT NULL,
  status VARCHAR(16) NOT NULL,
  published_at TIMESTAMP(6) NULL,
  submitted_at TIMESTAMP(6) NULL,
  reviewed_at TIMESTAMP(6) NULL,
  rejected_reason VARCHAR(512) NULL,
  author_id BIGINT NOT NULL,
  like_count BIGINT NOT NULL,
  view_count BIGINT NOT NULL DEFAULT 0,
  favorite_count BIGINT NOT NULL,
  comment_count BIGINT NOT NULL,
  created_at TIMESTAMP(6) NOT NULL,
  updated_at TIMESTAMP(6) NOT NULL,
  KEY idx_articles_author_id (author_id),
  KEY idx_articles_status_published_at (status, published_at),
  KEY idx_articles_title (title),
  CONSTRAINT fk_articles_author
    FOREIGN KEY (author_id) REFERENCES users(id)
    ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------
-- comments
-- ----------------------------
CREATE TABLE IF NOT EXISTS comments (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  article_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  parent_id BIGINT NULL,
  content TEXT NOT NULL,
  status VARCHAR(16) NOT NULL,
  created_at TIMESTAMP(6) NOT NULL,
  updated_at TIMESTAMP(6) NOT NULL,
  KEY idx_comments_article_id (article_id),
  KEY idx_comments_parent_id (parent_id),
  CONSTRAINT fk_comments_article
    FOREIGN KEY (article_id) REFERENCES articles(id)
    ON DELETE CASCADE,
  CONSTRAINT fk_comments_user
    FOREIGN KEY (user_id) REFERENCES users(id)
    ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------
-- article_likes
-- ----------------------------
CREATE TABLE IF NOT EXISTS article_likes (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  article_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  created_at TIMESTAMP(6) NOT NULL,
  UNIQUE KEY uk_article_likes_article_user (article_id, user_id),
  CONSTRAINT fk_article_likes_article
    FOREIGN KEY (article_id) REFERENCES articles(id)
    ON DELETE CASCADE,
  CONSTRAINT fk_article_likes_user
    FOREIGN KEY (user_id) REFERENCES users(id)
    ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------
-- article_favorites
-- ----------------------------
CREATE TABLE IF NOT EXISTS article_favorites (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  article_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  created_at TIMESTAMP(6) NOT NULL,
  UNIQUE KEY uk_article_favorites_article_user (article_id, user_id),
  CONSTRAINT fk_article_favorites_article
    FOREIGN KEY (article_id) REFERENCES articles(id)
    ON DELETE CASCADE,
  CONSTRAINT fk_article_favorites_user
    FOREIGN KEY (user_id) REFERENCES users(id)
    ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------
-- user_follows
-- ----------------------------
CREATE TABLE IF NOT EXISTS user_follows (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  follower_id BIGINT NOT NULL,
  followee_id BIGINT NOT NULL,
  created_at TIMESTAMP(6) NOT NULL,
  UNIQUE KEY uk_user_follows_follower_followee (follower_id, followee_id),
  CONSTRAINT fk_user_follows_follower
    FOREIGN KEY (follower_id) REFERENCES users(id)
    ON DELETE CASCADE,
  CONSTRAINT fk_user_follows_followee
    FOREIGN KEY (followee_id) REFERENCES users(id)
    ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------
-- media（本地文件元信息）
-- ----------------------------
CREATE TABLE IF NOT EXISTS media (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  storage_type VARCHAR(16) NOT NULL,
  storage_key VARCHAR(64) NOT NULL,
  url VARCHAR(500) NOT NULL,
  thumb_url VARCHAR(500) NOT NULL,
  size BIGINT NULL,
  content_type VARCHAR(128) NULL,
  width INT NULL,
  height INT NULL,
  thumb_width INT NULL,
  thumb_height INT NULL,
  uploaded_by BIGINT NULL,
  created_at TIMESTAMP(6) NOT NULL,
  UNIQUE KEY uk_media_storage_key (storage_key),
  KEY idx_media_storage_key (storage_key),
  CONSTRAINT fk_media_uploaded_by
    FOREIGN KEY (uploaded_by) REFERENCES users(id)
    ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- small_companies.sql
CREATE TABLE IF NOT EXISTS small_companies (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    name            VARCHAR(120) NOT NULL COMMENT '公司名称',
    type            VARCHAR(32)   COMMENT '公司类型（效率工具/企业服务/云服务…）',
    scale_min       INT           COMMENT '员工规模下限（如 100）',
    scale_max       INT           COMMENT '员工规模上限（如 499，null 表示 N+）',
    color           VARCHAR(16)   COMMENT '占位头像底色',
    logo_media_key  VARCHAR(64)   COMMENT '头像 media key（有则渲染真图）',
    city            VARCHAR(64)   COMMENT '所在城市',
    founded         INT           COMMENT '成立年份',
    address         VARCHAR(255)  COMMENT '办公地址',
    business        VARCHAR(255)  COMMENT '主营业务',
    description     TEXT          COMMENT '公司简介（纯文本）',
    website         VARCHAR(128)  COMMENT '官网域名（不带协议）',
    sort_order      INT NOT NULL DEFAULT 0 COMMENT '排序值（越小越靠前）',
    status          VARCHAR(16) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT / PUBLISHED',
    published_at    DATETIME      COMMENT '发布时间',
    created_at      DATETIME NOT NULL,
    updated_at      DATETIME NOT NULL,
    INDEX idx_small_companies_status_sort (status, sort_order, published_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

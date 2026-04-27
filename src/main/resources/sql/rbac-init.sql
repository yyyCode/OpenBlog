-- RBAC 初始化（MySQL 8+）
-- 说明：
-- 1) 当前项目鉴权主要用 users.role（枚举：ADMIN/AUTHOR/READER）+ @PreAuthorize(hasRole('ADMIN'))
-- 2) 本 SQL 先把 RBAC 相关表建好，并把 id=1 用户授予全部权限（并提升为 ADMIN 角色）
--
-- 执行方式：在你的数据库中手动执行本文件（例如用 Navicat / DBeaver / mysql 客户端）。

-- 角色表（可选：即使现在用 users.role，也保留用于未来扩展）
CREATE TABLE IF NOT EXISTS rbac_roles (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  code VARCHAR(64) NOT NULL UNIQUE,
  name VARCHAR(128) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 权限表
CREATE TABLE IF NOT EXISTS rbac_permissions (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  code VARCHAR(128) NOT NULL UNIQUE,
  name VARCHAR(255) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 用户-角色关联表
CREATE TABLE IF NOT EXISTS rbac_user_roles (
  user_id BIGINT NOT NULL,
  role_id BIGINT NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (user_id, role_id),
  CONSTRAINT fk_rbac_user_roles_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_rbac_user_roles_role FOREIGN KEY (role_id) REFERENCES rbac_roles(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 角色-权限关联表
CREATE TABLE IF NOT EXISTS rbac_role_permissions (
  role_id BIGINT NOT NULL,
  permission_id BIGINT NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (role_id, permission_id),
  CONSTRAINT fk_rbac_role_permissions_role FOREIGN KEY (role_id) REFERENCES rbac_roles(id) ON DELETE CASCADE,
  CONSTRAINT fk_rbac_role_permissions_perm FOREIGN KEY (permission_id) REFERENCES rbac_permissions(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- （可选）用户-权限直授表：用于临时开权限或绕过角色
CREATE TABLE IF NOT EXISTS rbac_user_permissions (
  user_id BIGINT NOT NULL,
  permission_id BIGINT NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (user_id, permission_id),
  CONSTRAINT fk_rbac_user_permissions_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_rbac_user_permissions_perm FOREIGN KEY (permission_id) REFERENCES rbac_permissions(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 预置角色
INSERT INTO rbac_roles(code, name) VALUES
('ADMIN', '管理员'),
('AUTHOR', '作者'),
('READER', '读者')
ON DUPLICATE KEY UPDATE name = VALUES(name);

-- 预置权限（覆盖当前代码里所有“需要管理员”的能力；后续有新接口再补充）
INSERT INTO rbac_permissions(code, name) VALUES
('changelog:create', '创建更新日志'),
('changelog:update', '更新更新日志'),
('changelog:delete', '删除更新日志')
ON DUPLICATE KEY UPDATE name = VALUES(name);

-- 把 id=1 用户提升为 ADMIN（这是让 @PreAuthorize(hasRole('ADMIN')) 立刻生效的关键）
UPDATE users SET role = 'ADMIN' WHERE id = 1;

-- 给 id=1 用户挂上 ADMIN 角色（RBAC 侧）
INSERT INTO rbac_user_roles(user_id, role_id)
SELECT 1, r.id FROM rbac_roles r WHERE r.code = 'ADMIN'
ON DUPLICATE KEY UPDATE role_id = role_id;

-- 给 ADMIN 角色授予全部权限
INSERT INTO rbac_role_permissions(role_id, permission_id)
SELECT r.id, p.id
FROM rbac_roles r
CROSS JOIN rbac_permissions p
WHERE r.code = 'ADMIN'
ON DUPLICATE KEY UPDATE permission_id = permission_id;

-- （可选冗余）同时把全部权限直授给 id=1 用户
INSERT INTO rbac_user_permissions(user_id, permission_id)
SELECT 1, p.id FROM rbac_permissions p
ON DUPLICATE KEY UPDATE permission_id = permission_id;


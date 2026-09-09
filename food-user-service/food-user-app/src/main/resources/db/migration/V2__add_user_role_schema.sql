CREATE TABLE IF NOT EXISTS user_role (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'user role id',
  user_id BIGINT NOT NULL COMMENT 'user id',
  role_code VARCHAR(32) NOT NULL COMMENT 'USER/ADMIN/OPERATOR',
  role_name VARCHAR(64) NOT NULL COMMENT 'role name',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '1 enabled, 0 disabled',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time',
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_role (user_id, role_code),
  KEY idx_user_status (user_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='user role';

INSERT INTO user_role (user_id, role_code, role_name, status)
SELECT 1, 'ADMIN', 'local admin', 1
WHERE NOT EXISTS (
  SELECT 1 FROM user_role WHERE user_id = 1 AND role_code = 'ADMIN'
);


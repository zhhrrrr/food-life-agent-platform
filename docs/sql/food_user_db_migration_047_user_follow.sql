CREATE DATABASE IF NOT EXISTS food_user_db
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_general_ci;

USE food_user_db;

CREATE TABLE IF NOT EXISTS user_follow (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'follow relation id',
  user_id BIGINT NOT NULL COMMENT 'follower user id',
  follow_user_id BIGINT NOT NULL COMMENT 'followed user id',
  follow_status TINYINT NOT NULL DEFAULT 1 COMMENT '1 following, 0 canceled',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time',
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_follow (user_id, follow_user_id),
  KEY idx_user_status_id (user_id, follow_status, id),
  KEY idx_follow_user_status_id (follow_user_id, follow_status, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='user follow relation';

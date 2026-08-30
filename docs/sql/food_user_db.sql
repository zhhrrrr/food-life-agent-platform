CREATE DATABASE IF NOT EXISTS food_user_db
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_general_ci;

USE food_user_db;

CREATE TABLE IF NOT EXISTS user (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'user id',
  phone VARCHAR(20) NOT NULL COMMENT 'phone',
  password VARCHAR(128) DEFAULT NULL COMMENT 'password, nullable for sms login',
  nick_name VARCHAR(64) NOT NULL COMMENT 'nickname',
  icon VARCHAR(255) DEFAULT '' COMMENT 'avatar',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '1 normal, 0 disabled',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time',
  PRIMARY KEY (id),
  UNIQUE KEY uk_phone (phone)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='user table';

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

CREATE TABLE IF NOT EXISTS user_profile (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'profile id',
  user_id BIGINT NOT NULL COMMENT 'user id',
  city VARCHAR(64) DEFAULT '' COMMENT 'city',
  bio VARCHAR(200) DEFAULT '' COMMENT 'personal bio',
  food_preference VARCHAR(200) DEFAULT '' COMMENT 'food preference tags or text',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time',
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='user profile extension';

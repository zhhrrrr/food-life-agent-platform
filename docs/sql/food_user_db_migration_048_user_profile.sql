CREATE DATABASE IF NOT EXISTS food_user_db
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_general_ci;

USE food_user_db;

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

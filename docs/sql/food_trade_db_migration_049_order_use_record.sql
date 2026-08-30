CREATE DATABASE IF NOT EXISTS food_trade_db
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_general_ci;

USE food_trade_db;

CREATE TABLE IF NOT EXISTS order_use_record (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'order use record id',
  use_record_no VARCHAR(64) NOT NULL COMMENT 'order use record no',
  order_id BIGINT NOT NULL COMMENT 'dining order id',
  order_no VARCHAR(64) NOT NULL COMMENT 'dining order no',
  user_id BIGINT NOT NULL COMMENT 'user id',
  shop_id BIGINT NOT NULL COMMENT 'shop id',
  package_id BIGINT NOT NULL COMMENT 'package id',
  trade_type VARCHAR(32) NOT NULL COMMENT 'NORMAL/GROUP_BUY/SECKILL',
  use_source VARCHAR(32) NOT NULL COMMENT 'use source',
  use_status VARCHAR(32) NOT NULL COMMENT 'SUCCESS/FAILED',
  use_time DATETIME NOT NULL COMMENT 'use time',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time',
  PRIMARY KEY (id),
  UNIQUE KEY uk_use_record_no (use_record_no),
  UNIQUE KEY uk_order_id (order_id),
  KEY idx_user_time (user_id, use_time),
  KEY idx_shop_time (shop_id, use_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='order use record';

CREATE DATABASE IF NOT EXISTS food_trade_db
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_general_ci;

USE food_trade_db;

CREATE TABLE IF NOT EXISTS dining_order (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'order id',
  order_no VARCHAR(64) NOT NULL COMMENT 'order no',
  user_id BIGINT NOT NULL COMMENT 'user id',
  shop_id BIGINT NOT NULL COMMENT 'shop id',
  package_id BIGINT NOT NULL COMMENT 'package id',
  quantity INT NOT NULL DEFAULT 1 COMMENT 'quantity',
  total_amount BIGINT NOT NULL COMMENT 'total amount in cents',
  pay_amount BIGINT NOT NULL COMMENT 'pay amount in cents',
  trade_type VARCHAR(32) NOT NULL COMMENT 'NORMAL/GROUP_BUY/SECKILL',
  order_status VARCHAR(32) NOT NULL COMMENT 'order status: WAIT_PAY/PAID/CANCELED',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time',
  PRIMARY KEY (id),
  UNIQUE KEY uk_order_no (order_no),
  KEY idx_user_id (user_id),
  KEY idx_shop_id (shop_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='dining order';

CREATE TABLE IF NOT EXISTS dining_order_item (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'order item id',
  order_id BIGINT NOT NULL COMMENT 'order id',
  shop_id BIGINT NOT NULL COMMENT 'shop id',
  shop_name_snapshot VARCHAR(128) NOT NULL COMMENT 'shop name snapshot',
  package_id BIGINT NOT NULL COMMENT 'package id',
  package_name_snapshot VARCHAR(128) NOT NULL COMMENT 'package name snapshot',
  package_description_snapshot VARCHAR(512) DEFAULT '' COMMENT 'package description snapshot',
  cover_image_snapshot VARCHAR(255) DEFAULT '' COMMENT 'cover image snapshot',
  package_price_snapshot BIGINT NOT NULL COMMENT 'package price snapshot in cents',
  actual_price BIGINT NOT NULL COMMENT 'actual price in cents',
  quantity INT NOT NULL DEFAULT 1 COMMENT 'quantity',
  use_rule_snapshot VARCHAR(512) DEFAULT '' COMMENT 'use rule snapshot',
  PRIMARY KEY (id),
  KEY idx_order_id (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='dining order item';

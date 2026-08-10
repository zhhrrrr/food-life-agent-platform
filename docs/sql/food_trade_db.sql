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
  order_status VARCHAR(32) NOT NULL COMMENT 'order status: WAIT_PAY/PAID/USED/CANCELED/REFUNDED',
  use_time DATETIME DEFAULT NULL COMMENT 'package use time',
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

CREATE TABLE IF NOT EXISTS group_buy_activity (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'activity id',
  package_id BIGINT NOT NULL COMMENT 'meal package id',
  activity_name VARCHAR(128) NOT NULL COMMENT 'activity name',
  target_count INT NOT NULL COMMENT 'team target count',
  user_take_limit INT NOT NULL DEFAULT 1 COMMENT 'user take limit in this activity',
  group_price BIGINT NOT NULL COMMENT 'group buy price in cents',
  activity_status TINYINT NOT NULL DEFAULT 1 COMMENT 'activity status: 1 enabled, 0 disabled',
  valid_start_time DATETIME NOT NULL COMMENT 'activity start time',
  valid_end_time DATETIME NOT NULL COMMENT 'activity end time',
  stock INT NOT NULL DEFAULT 0 COMMENT 'activity stock',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time',
  PRIMARY KEY (id),
  UNIQUE KEY uk_package_id (package_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='group buy activity';

CREATE TABLE IF NOT EXISTS group_buy_order (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'team id',
  team_id VARCHAR(64) NOT NULL COMMENT 'team biz id',
  activity_id BIGINT NOT NULL COMMENT 'activity id',
  package_id BIGINT NOT NULL COMMENT 'meal package id',
  target_count INT NOT NULL COMMENT 'team target count',
  complete_count INT NOT NULL DEFAULT 0 COMMENT 'paid participant count',
  lock_count INT NOT NULL DEFAULT 0 COMMENT 'locked participant count',
  team_status VARCHAR(32) NOT NULL COMMENT 'team status: IN_PROGRESS/SUCCESS/FAILED',
  valid_start_time DATETIME NOT NULL COMMENT 'team start time',
  valid_end_time DATETIME NOT NULL COMMENT 'team end time',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time',
  PRIMARY KEY (id),
  UNIQUE KEY uk_team_id (team_id),
  KEY idx_activity_id (activity_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='group buy team order';

CREATE TABLE IF NOT EXISTS group_buy_order_list (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'group buy order list id',
  user_id BIGINT NOT NULL COMMENT 'user id',
  team_id VARCHAR(64) NOT NULL COMMENT 'team biz id',
  order_id BIGINT NOT NULL COMMENT 'dining order id',
  order_no VARCHAR(64) NOT NULL COMMENT 'dining order no',
  activity_id BIGINT NOT NULL COMMENT 'activity id',
  package_id BIGINT NOT NULL COMMENT 'meal package id',
  order_status VARCHAR(32) NOT NULL COMMENT 'group buy order status: LOCKED/PAID/CANCELED/REFUNDED',
  out_trade_time DATETIME DEFAULT NULL COMMENT 'external pay success time',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time',
  PRIMARY KEY (id),
  UNIQUE KEY uk_order_id (order_id),
  KEY idx_user_activity (user_id, activity_id),
  KEY idx_team_id (team_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='group buy participant order list';

INSERT INTO group_buy_activity (
  package_id, activity_name, target_count, user_take_limit, group_price,
  activity_status, valid_start_time, valid_end_time, stock
) VALUES (
  1, '双人火锅套餐拼团', 2, 1, 12800,
  1, '2026-01-01 00:00:00', '2026-12-31 23:59:59', 100
) ON DUPLICATE KEY UPDATE
  activity_name = VALUES(activity_name),
  target_count = VALUES(target_count),
  user_take_limit = VALUES(user_take_limit),
  group_price = VALUES(group_price),
  activity_status = VALUES(activity_status),
  valid_start_time = VALUES(valid_start_time),
  valid_end_time = VALUES(valid_end_time),
  stock = IF(stock < 1, VALUES(stock), stock);

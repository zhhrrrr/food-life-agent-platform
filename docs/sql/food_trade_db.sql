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
  discount_amount BIGINT NOT NULL DEFAULT 0 COMMENT 'discount amount in cents',
  pay_amount BIGINT NOT NULL COMMENT 'pay amount in cents',
  user_coupon_id BIGINT DEFAULT NULL COMMENT 'used user coupon id',
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
  team_status VARCHAR(32) NOT NULL COMMENT 'team status: IN_PROGRESS/SUCCESS/FAILED/COMPLETE_FAIL',
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

CREATE TABLE IF NOT EXISTS seckill_activity (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'seckill activity id',
  package_id BIGINT NOT NULL COMMENT 'meal package id',
  activity_name VARCHAR(128) NOT NULL COMMENT 'activity name',
  seckill_price BIGINT NOT NULL COMMENT 'seckill price in cents',
  activity_status TINYINT NOT NULL DEFAULT 1 COMMENT 'activity status: 1 enabled, 0 disabled',
  valid_start_time DATETIME NOT NULL COMMENT 'activity start time',
  valid_end_time DATETIME NOT NULL COMMENT 'activity end time',
  stock INT NOT NULL DEFAULT 0 COMMENT 'activity stock',
  user_take_limit INT NOT NULL DEFAULT 1 COMMENT 'user take limit in this activity',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time',
  PRIMARY KEY (id),
  UNIQUE KEY uk_package_id (package_id),
  KEY idx_valid_time (valid_start_time, valid_end_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='seckill activity';

CREATE TABLE IF NOT EXISTS seckill_order (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'seckill order id',
  user_id BIGINT NOT NULL COMMENT 'user id',
  activity_id BIGINT NOT NULL COMMENT 'seckill activity id',
  package_id BIGINT NOT NULL COMMENT 'meal package id',
  order_id BIGINT NOT NULL COMMENT 'dining order id',
  order_no VARCHAR(64) NOT NULL COMMENT 'dining order no',
  order_status VARCHAR(32) NOT NULL COMMENT 'seckill order status: WAIT_PAY/PAID/CANCELED/REFUNDED',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time',
  PRIMARY KEY (id),
  UNIQUE KEY uk_order_id (order_id),
  UNIQUE KEY uk_user_activity (user_id, activity_id),
  KEY idx_activity_id (activity_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='seckill participant order';

CREATE TABLE IF NOT EXISTS seckill_order_request (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'seckill order request id',
  request_no VARCHAR(64) NOT NULL COMMENT 'seckill request no',
  user_id BIGINT NOT NULL COMMENT 'user id',
  activity_id BIGINT NOT NULL COMMENT 'seckill activity id',
  package_id BIGINT NOT NULL COMMENT 'meal package id',
  quantity INT NOT NULL DEFAULT 1 COMMENT 'quantity',
  order_id BIGINT DEFAULT NULL COMMENT 'created dining order id',
  order_no VARCHAR(64) DEFAULT NULL COMMENT 'created dining order no',
  request_status VARCHAR(32) NOT NULL COMMENT 'request status: INIT/PROCESSING/SUCCESS/FAILED',
  fail_reason VARCHAR(512) DEFAULT NULL COMMENT 'fail reason',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time',
  PRIMARY KEY (id),
  UNIQUE KEY uk_request_no (request_no),
  KEY idx_user_activity (user_id, activity_id),
  KEY idx_status_update_time (request_status, update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='seckill async order request';

CREATE TABLE IF NOT EXISTS trade_local_message (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'local message id',
  message_id VARCHAR(64) NOT NULL COMMENT 'message id',
  message_type VARCHAR(64) NOT NULL COMMENT 'message type',
  biz_type VARCHAR(64) NOT NULL COMMENT 'biz type',
  biz_id VARCHAR(64) NOT NULL COMMENT 'biz id',
  message_status VARCHAR(32) NOT NULL COMMENT 'message status: INIT/PROCESSING/SUCCESS/FAILED',
  retry_count INT NOT NULL DEFAULT 0 COMMENT 'retry count',
  max_retry_count INT NOT NULL DEFAULT 3 COMMENT 'max retry count',
  next_retry_time DATETIME NOT NULL COMMENT 'next retry time',
  content VARCHAR(1024) DEFAULT NULL COMMENT 'message content',
  fail_reason VARCHAR(512) DEFAULT NULL COMMENT 'fail reason',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time',
  PRIMARY KEY (id),
  UNIQUE KEY uk_message_id (message_id),
  KEY idx_status_retry_time (message_status, next_retry_time),
  KEY idx_biz_id (biz_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='trade local reliable message';

CREATE TABLE IF NOT EXISTS payment_order (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'payment order id',
  pay_order_no VARCHAR(64) NOT NULL COMMENT 'platform payment order no',
  order_id BIGINT NOT NULL COMMENT 'dining order id',
  order_no VARCHAR(64) NOT NULL COMMENT 'dining order no',
  user_id BIGINT NOT NULL COMMENT 'user id',
  source VARCHAR(32) NOT NULL COMMENT 'payment source',
  channel VARCHAR(32) NOT NULL COMMENT 'payment channel',
  pay_amount BIGINT NOT NULL COMMENT 'pay amount in cents',
  pay_status VARCHAR(32) NOT NULL COMMENT 'payment status: PREPARED/SUCCESS/CLOSED',
  out_trade_no VARCHAR(128) DEFAULT NULL COMMENT 'external payment trade no',
  pay_time DATETIME DEFAULT NULL COMMENT 'external payment success time',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time',
  PRIMARY KEY (id),
  UNIQUE KEY uk_pay_order_no (pay_order_no),
  UNIQUE KEY uk_order_user (order_id, user_id),
  UNIQUE KEY uk_out_trade_no (out_trade_no),
  KEY idx_order_id (order_id),
  KEY idx_status_update_time (pay_status, update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='payment order';

CREATE TABLE IF NOT EXISTS coupon_template (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'coupon template id',
  coupon_name VARCHAR(128) NOT NULL COMMENT 'coupon name',
  coupon_type VARCHAR(32) NOT NULL COMMENT 'FULL_REDUCTION',
  threshold_amount BIGINT NOT NULL DEFAULT 0 COMMENT 'minimum order amount in cents',
  discount_amount BIGINT NOT NULL COMMENT 'discount amount in cents',
  scope_type VARCHAR(32) NOT NULL DEFAULT 'ALL' COMMENT 'ALL/SHOP/PACKAGE',
  scope_shop_id BIGINT DEFAULT NULL COMMENT 'available shop id when scope_type=SHOP',
  scope_package_id BIGINT DEFAULT NULL COMMENT 'available package id when scope_type=PACKAGE',
  user_receive_limit INT NOT NULL DEFAULT 1 COMMENT 'receive limit per user, 0 means unlimited',
  valid_start_time DATETIME NOT NULL COMMENT 'valid start time',
  valid_end_time DATETIME NOT NULL COMMENT 'valid end time',
  total_stock INT NOT NULL DEFAULT 0 COMMENT 'total coupon stock',
  received_count INT NOT NULL DEFAULT 0 COMMENT 'received count',
  template_status TINYINT NOT NULL DEFAULT 1 COMMENT '1 enabled, 0 disabled',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time',
  PRIMARY KEY (id),
  KEY idx_status_time (template_status, valid_start_time, valid_end_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='coupon template';

CREATE TABLE IF NOT EXISTS user_coupon (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'user coupon id',
  template_id BIGINT NOT NULL COMMENT 'coupon template id',
  user_id BIGINT NOT NULL COMMENT 'user id',
  coupon_name VARCHAR(128) NOT NULL COMMENT 'coupon name snapshot',
  coupon_type VARCHAR(32) NOT NULL COMMENT 'coupon type snapshot',
  threshold_amount BIGINT NOT NULL DEFAULT 0 COMMENT 'minimum order amount in cents',
  discount_amount BIGINT NOT NULL COMMENT 'discount amount in cents',
  scope_type VARCHAR(32) NOT NULL DEFAULT 'ALL' COMMENT 'ALL/SHOP/PACKAGE snapshot',
  scope_shop_id BIGINT DEFAULT NULL COMMENT 'available shop id snapshot',
  scope_package_id BIGINT DEFAULT NULL COMMENT 'available package id snapshot',
  coupon_status VARCHAR(32) NOT NULL COMMENT 'UNUSED/USED/EXPIRED',
  used_order_id BIGINT DEFAULT NULL COMMENT 'used order id',
  valid_start_time DATETIME NOT NULL COMMENT 'valid start time',
  valid_end_time DATETIME NOT NULL COMMENT 'valid end time',
  receive_time DATETIME NOT NULL COMMENT 'receive time',
  use_time DATETIME DEFAULT NULL COMMENT 'use time',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time',
  PRIMARY KEY (id),
  KEY idx_user_status (user_id, coupon_status),
  KEY idx_template_user (template_id, user_id),
  KEY idx_used_order (used_order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='user coupon';

INSERT INTO coupon_template (
  id, coupon_name, coupon_type, threshold_amount, discount_amount,
  scope_type, scope_shop_id, scope_package_id, user_receive_limit,
  valid_start_time, valid_end_time, total_stock, received_count, template_status
) VALUES (
  1, 'local normal order 20 off 100', 'FULL_REDUCTION', 10000, 2000,
  'PACKAGE', NULL, 1, 1,
  '2026-01-01 00:00:00', '2026-12-31 23:59:59', 1000, 0, 1
) ON DUPLICATE KEY UPDATE
  coupon_name = VALUES(coupon_name),
  coupon_type = VALUES(coupon_type),
  threshold_amount = VALUES(threshold_amount),
  discount_amount = VALUES(discount_amount),
  scope_type = VALUES(scope_type),
  scope_shop_id = VALUES(scope_shop_id),
  scope_package_id = VALUES(scope_package_id),
  user_receive_limit = VALUES(user_receive_limit),
  valid_start_time = VALUES(valid_start_time),
  valid_end_time = VALUES(valid_end_time),
  total_stock = VALUES(total_stock),
  template_status = VALUES(template_status);

INSERT INTO group_buy_activity (
  package_id, activity_name, target_count, user_take_limit, group_price,
  activity_status, valid_start_time, valid_end_time, stock
) VALUES (
  1, 'local group buy meal package', 2, 1, 12800,
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

INSERT INTO seckill_activity (
  package_id, activity_name, seckill_price, activity_status,
  valid_start_time, valid_end_time, stock, user_take_limit
) VALUES (
  1, 'local seckill meal package', 9800, 1,
  '2026-01-01 00:00:00', '2026-12-31 23:59:59', 20, 1
) ON DUPLICATE KEY UPDATE
  activity_name = VALUES(activity_name),
  seckill_price = VALUES(seckill_price),
  activity_status = VALUES(activity_status),
  valid_start_time = VALUES(valid_start_time),
  valid_end_time = VALUES(valid_end_time),
  stock = IF(stock < 1, VALUES(stock), stock),
  user_take_limit = VALUES(user_take_limit);

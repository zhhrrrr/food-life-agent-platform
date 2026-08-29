USE food_business_db;

CREATE TABLE IF NOT EXISTS shop_review (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'review id',
  review_no VARCHAR(64) NOT NULL COMMENT 'review no',
  user_id BIGINT NOT NULL COMMENT 'user id',
  shop_id BIGINT NOT NULL COMMENT 'shop id',
  package_id BIGINT NOT NULL COMMENT 'meal package id',
  order_id BIGINT NOT NULL COMMENT 'trade order id',
  order_no VARCHAR(64) NOT NULL COMMENT 'trade order no',
  score INT NOT NULL COMMENT 'score 1-5',
  content VARCHAR(500) NOT NULL COMMENT 'review content',
  images VARCHAR(1024) DEFAULT '' COMMENT 'review images',
  review_status TINYINT NOT NULL DEFAULT 1 COMMENT '1 normal, 0 hidden',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time',
  PRIMARY KEY (id),
  UNIQUE KEY uk_review_no (review_no),
  UNIQUE KEY uk_order_user (order_id, user_id),
  KEY idx_shop_time (shop_id, id),
  KEY idx_package_time (package_id, id),
  KEY idx_user_time (user_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='shop review';

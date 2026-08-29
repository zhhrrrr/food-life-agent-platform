CREATE DATABASE IF NOT EXISTS food_business_db
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_general_ci;

USE food_business_db;

CREATE TABLE IF NOT EXISTS shop_category (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'category id',
  name VARCHAR(64) NOT NULL COMMENT 'category name',
  icon VARCHAR(255) DEFAULT '' COMMENT 'icon',
  sort INT NOT NULL DEFAULT 0 COMMENT 'sort',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='shop category';

CREATE TABLE IF NOT EXISTS shop (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'shop id',
  name VARCHAR(128) NOT NULL COMMENT 'shop name',
  category_id BIGINT NOT NULL COMMENT 'category id',
  images VARCHAR(1024) DEFAULT '' COMMENT 'images',
  area VARCHAR(64) DEFAULT '' COMMENT 'business area',
  address VARCHAR(255) DEFAULT '' COMMENT 'address',
  longitude DOUBLE DEFAULT NULL COMMENT 'longitude',
  latitude DOUBLE DEFAULT NULL COMMENT 'latitude',
  avg_price BIGINT DEFAULT 0 COMMENT 'average price in cents',
  sold INT DEFAULT 0 COMMENT 'sold count',
  comments INT DEFAULT 0 COMMENT 'comment count',
  score INT DEFAULT 0 COMMENT 'score multiplied by 10',
  open_hours VARCHAR(64) DEFAULT '' COMMENT 'open hours',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '1 online, 0 offline',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time',
  PRIMARY KEY (id),
  KEY idx_category_id (category_id),
  KEY idx_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='shop';

CREATE TABLE IF NOT EXISTS meal_package (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'package id',
  shop_id BIGINT NOT NULL COMMENT 'shop id',
  name VARCHAR(128) NOT NULL COMMENT 'package name',
  description VARCHAR(512) DEFAULT '' COMMENT 'description',
  cover_image VARCHAR(255) DEFAULT '' COMMENT 'cover image',
  price BIGINT NOT NULL COMMENT 'price in cents',
  original_price BIGINT DEFAULT NULL COMMENT 'original price in cents',
  stock INT NOT NULL DEFAULT 0 COMMENT 'stock',
  sold INT NOT NULL DEFAULT 0 COMMENT 'sold count',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '1 online, 0 offline',
  use_rule VARCHAR(512) DEFAULT '' COMMENT 'use rule',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time',
  PRIMARY KEY (id),
  KEY idx_shop_id (shop_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='meal package';

CREATE TABLE IF NOT EXISTS package_stock_change_record (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'stock change record id',
  operation_id VARCHAR(128) NOT NULL COMMENT 'idempotent operation id',
  package_id BIGINT NOT NULL COMMENT 'meal package id',
  quantity INT NOT NULL COMMENT 'quantity',
  change_type VARCHAR(32) NOT NULL COMMENT 'OCCUPY/RELEASE/CONFIRM_SOLD/ROLLBACK_SOLD',
  change_status VARCHAR(32) NOT NULL COMMENT 'SUCCESS',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time',
  PRIMARY KEY (id),
  UNIQUE KEY uk_operation_id (operation_id),
  KEY idx_package_id (package_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='package stock change idempotent record';

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
  shop_comments_before INT DEFAULT NULL COMMENT 'shop comments before review',
  shop_score_before INT DEFAULT NULL COMMENT 'shop score before review',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time',
  PRIMARY KEY (id),
  UNIQUE KEY uk_review_no (review_no),
  UNIQUE KEY uk_order_user (order_id, user_id),
  KEY idx_shop_time (shop_id, id),
  KEY idx_package_time (package_id, id),
  KEY idx_user_time (user_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='shop review';

INSERT INTO shop_category (id, name, icon, sort)
VALUES
  (1, 'Hot Pot', 'hotpot', 1),
  (2, 'BBQ', 'bbq', 2),
  (3, 'Dessert', 'dessert', 3)
ON DUPLICATE KEY UPDATE name = VALUES(name), icon = VALUES(icon), sort = VALUES(sort);

INSERT INTO shop (id, name, category_id, images, area, address, longitude, latitude, avg_price, sold, comments, score, open_hours, status)
VALUES
  (1, 'Chengdu Spicy Hot Pot', 1, '', 'Downtown', 'No. 18 Food Street', 120.12345, 30.12345, 12800, 318, 82, 46, '10:00-22:00', 1),
  (2, 'Late Night BBQ House', 2, '', 'Riverside', 'No. 6 Riverside Road', 120.22345, 30.22345, 8800, 256, 61, 45, '17:00-02:00', 1),
  (3, 'Sweet Time Dessert', 3, '', 'Mall Area', '3F Joy Mall', 120.32345, 30.32345, 4200, 189, 49, 47, '11:00-21:30', 1)
ON DUPLICATE KEY UPDATE name = VALUES(name), category_id = VALUES(category_id), avg_price = VALUES(avg_price), sold = VALUES(sold), comments = VALUES(comments), score = VALUES(score);

INSERT INTO meal_package (id, shop_id, name, description, cover_image, price, original_price, stock, sold, status, use_rule)
VALUES
  (1, 1, 'Two-person Hot Pot Set', 'Beef, vegetables, snacks and drinks for two.', '', 16800, 23800, 100, 52, 1, 'Valid for dine-in only. Not available on public holidays.'),
  (2, 1, 'Four-person Hot Pot Set', 'Family hot pot set with mixed meat and vegetable platter.', '', 29800, 39800, 80, 31, 1, 'Reservation recommended.'),
  (3, 2, 'BBQ Combo for Two', 'Skewers, grilled fish tofu and drinks.', '', 12800, 18800, 120, 44, 1, 'Valid after 17:00.'),
  (4, 3, 'Afternoon Dessert Set', 'Cake, coffee and seasonal dessert.', '', 5800, 8800, 60, 27, 1, 'Valid from 14:00 to 17:30.')
ON DUPLICATE KEY UPDATE name = VALUES(name), price = VALUES(price), original_price = VALUES(original_price), stock = VALUES(stock), sold = VALUES(sold), status = VALUES(status);

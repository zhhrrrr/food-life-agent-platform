USE food_business_db;

CREATE TABLE IF NOT EXISTS shop_favorite (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'favorite id',
  user_id BIGINT NOT NULL COMMENT 'user id',
  shop_id BIGINT NOT NULL COMMENT 'shop id',
  favorite_status TINYINT NOT NULL DEFAULT 1 COMMENT '1 favorite, 0 canceled',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time',
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_shop (user_id, shop_id),
  KEY idx_user_status_id (user_id, favorite_status, id),
  KEY idx_shop_id (shop_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='shop favorite';

USE food_trade_db;

ALTER TABLE coupon_template
  ADD COLUMN scope_type VARCHAR(32) NOT NULL DEFAULT 'ALL' COMMENT 'ALL/SHOP/PACKAGE' AFTER discount_amount,
  ADD COLUMN scope_shop_id BIGINT DEFAULT NULL COMMENT 'available shop id when scope_type=SHOP' AFTER scope_type,
  ADD COLUMN scope_package_id BIGINT DEFAULT NULL COMMENT 'available package id when scope_type=PACKAGE' AFTER scope_shop_id,
  ADD COLUMN user_receive_limit INT NOT NULL DEFAULT 1 COMMENT 'receive limit per user, 0 means unlimited' AFTER scope_package_id;

ALTER TABLE user_coupon
  ADD COLUMN scope_type VARCHAR(32) NOT NULL DEFAULT 'ALL' COMMENT 'ALL/SHOP/PACKAGE snapshot' AFTER discount_amount,
  ADD COLUMN scope_shop_id BIGINT DEFAULT NULL COMMENT 'available shop id snapshot' AFTER scope_type,
  ADD COLUMN scope_package_id BIGINT DEFAULT NULL COMMENT 'available package id snapshot' AFTER scope_shop_id;

UPDATE coupon_template
SET scope_type = 'PACKAGE',
    scope_shop_id = NULL,
    scope_package_id = 1,
    user_receive_limit = 1
WHERE id = 1;

UPDATE user_coupon
SET scope_type = 'PACKAGE',
    scope_shop_id = NULL,
    scope_package_id = 1
WHERE template_id = 1;

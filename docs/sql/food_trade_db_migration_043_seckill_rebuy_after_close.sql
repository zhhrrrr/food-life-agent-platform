USE food_trade_db;

ALTER TABLE seckill_order
  DROP INDEX uk_user_activity,
  ADD INDEX idx_user_activity_status (user_id, activity_id, order_status);

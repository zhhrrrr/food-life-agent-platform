USE food_trade_db;

ALTER TABLE seckill_order
  MODIFY COLUMN order_status VARCHAR(32) NOT NULL COMMENT 'seckill order status: WAIT_PAY/PAID/CANCELED/REFUNDED';

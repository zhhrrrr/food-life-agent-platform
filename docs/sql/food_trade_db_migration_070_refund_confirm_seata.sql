USE food_trade_db;

ALTER TABLE payment_order
  MODIFY COLUMN pay_status VARCHAR(32) NOT NULL COMMENT 'payment status: PREPARED/SUCCESS/CLOSED/REFUNDED';

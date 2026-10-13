ALTER TABLE dining_order ADD COLUMN request_fingerprint CHAR(64) DEFAULT NULL COMMENT 'confirmed creation parameters';
ALTER TABLE seckill_order_request
  ADD COLUMN client_request_id VARCHAR(64) DEFAULT NULL COMMENT 'client idempotency key',
  ADD COLUMN request_fingerprint CHAR(64) DEFAULT NULL COMMENT 'confirmed creation parameters',
  ADD COLUMN expected_unit_price BIGINT DEFAULT NULL COMMENT 'confirmed activity unit price';
CREATE UNIQUE INDEX uk_seckill_user_request ON seckill_order_request(user_id, client_request_id);

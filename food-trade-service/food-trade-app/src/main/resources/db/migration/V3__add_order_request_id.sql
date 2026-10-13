ALTER TABLE dining_order ADD COLUMN request_id VARCHAR(64) DEFAULT NULL COMMENT 'client idempotency key';
CREATE UNIQUE INDEX uk_order_user_request ON dining_order (user_id, request_id);

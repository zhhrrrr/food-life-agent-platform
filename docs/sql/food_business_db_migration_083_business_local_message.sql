USE food_business_db;

CREATE TABLE IF NOT EXISTS business_local_message (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'local message id',
  message_id VARCHAR(128) NOT NULL COMMENT 'message id',
  message_type VARCHAR(64) NOT NULL COMMENT 'message type',
  biz_type VARCHAR(64) NOT NULL COMMENT 'business type',
  biz_id VARCHAR(128) NOT NULL COMMENT 'business id',
  message_status VARCHAR(32) NOT NULL COMMENT 'INIT/PROCESSING/SUCCESS/FAILED',
  retry_count INT NOT NULL DEFAULT 0 COMMENT 'retry count',
  max_retry_count INT NOT NULL DEFAULT 5 COMMENT 'max retry count',
  next_retry_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'next retry time',
  content TEXT NOT NULL COMMENT 'message content',
  fail_reason VARCHAR(512) DEFAULT NULL COMMENT 'fail reason',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time',
  PRIMARY KEY (id),
  UNIQUE KEY uk_message_id (message_id),
  KEY idx_status_retry_time (message_status, next_retry_time),
  KEY idx_biz_type_id (biz_type, biz_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='business local reliable message';

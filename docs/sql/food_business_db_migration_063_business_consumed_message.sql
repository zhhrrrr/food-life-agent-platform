USE food_business_db;

CREATE TABLE IF NOT EXISTS business_consumed_message (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'consumed message id',
  message_id VARCHAR(128) NOT NULL COMMENT 'idempotent message id',
  topic VARCHAR(64) NOT NULL COMMENT 'RabbitMQ exchange',
  tag VARCHAR(64) NOT NULL COMMENT 'RabbitMQ routing key',
  biz_key VARCHAR(128) NOT NULL COMMENT 'business key',
  consume_status VARCHAR(32) NOT NULL COMMENT 'PROCESSING/SUCCESS/FAILED',
  fail_reason VARCHAR(512) DEFAULT NULL COMMENT 'fail reason',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time',
  PRIMARY KEY (id),
  UNIQUE KEY uk_message_id (message_id),
  KEY idx_topic_tag_biz (topic, tag, biz_key),
  KEY idx_status_update_time (consume_status, update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='business consumed message idempotent record';


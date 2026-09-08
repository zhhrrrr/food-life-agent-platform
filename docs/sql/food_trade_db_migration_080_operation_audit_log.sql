USE food_trade_db;

CREATE TABLE IF NOT EXISTS trade_operation_audit_log (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'operation audit id',
  trace_id VARCHAR(64) DEFAULT NULL COMMENT 'trace id',
  operator_id BIGINT DEFAULT NULL COMMENT 'operator user id',
  operator_role VARCHAR(32) DEFAULT NULL COMMENT 'operator role',
  operation_type VARCHAR(64) NOT NULL COMMENT 'operation type',
  biz_type VARCHAR(64) NOT NULL COMMENT 'business type',
  biz_id VARCHAR(128) NOT NULL COMMENT 'business id',
  operation_status VARCHAR(32) NOT NULL COMMENT 'SUCCESS/FAILED',
  request_content TEXT DEFAULT NULL COMMENT 'request json',
  response_content TEXT DEFAULT NULL COMMENT 'response json',
  remark VARCHAR(255) DEFAULT NULL COMMENT 'operation remark',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
  PRIMARY KEY (id),
  KEY idx_trace_id (trace_id),
  KEY idx_operator_time (operator_id, create_time),
  KEY idx_biz (biz_type, biz_id),
  KEY idx_operation_time (operation_type, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='trade operation audit log';

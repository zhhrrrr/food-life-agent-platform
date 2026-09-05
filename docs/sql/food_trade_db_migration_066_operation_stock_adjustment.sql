USE food_trade_db;

CREATE TABLE IF NOT EXISTS trade_operation_stock_adjust_log (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'operation stock adjustment log id',
  operation_id VARCHAR(128) NOT NULL COMMENT 'idempotent operation id',
  operator_id BIGINT NOT NULL COMMENT 'operator user id',
  package_id BIGINT NOT NULL COMMENT 'meal package id',
  adjust_quantity INT NOT NULL COMMENT 'stock adjust quantity',
  reason VARCHAR(255) DEFAULT '' COMMENT 'adjust reason',
  stock INT DEFAULT NULL COMMENT 'stock after adjust',
  sold INT DEFAULT NULL COMMENT 'sold after adjust',
  tx_status VARCHAR(32) NOT NULL COMMENT 'PROCESSING/SUCCESS',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time',
  PRIMARY KEY (id),
  UNIQUE KEY uk_operation_id (operation_id),
  KEY idx_package_id (package_id),
  KEY idx_operator_time (operator_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='trade operation stock adjustment log';

CREATE TABLE IF NOT EXISTS undo_log (
  branch_id BIGINT NOT NULL COMMENT 'branch transaction id',
  xid VARCHAR(128) NOT NULL COMMENT 'global transaction id',
  context VARCHAR(128) NOT NULL COMMENT 'undo_log context',
  rollback_info LONGBLOB NOT NULL COMMENT 'rollback info',
  log_status INT NOT NULL COMMENT '0 normal, 1 defense',
  log_created DATETIME(6) NOT NULL COMMENT 'create datetime',
  log_modified DATETIME(6) NOT NULL COMMENT 'modify datetime',
  UNIQUE KEY ux_undo_log (xid, branch_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Seata AT undo log';

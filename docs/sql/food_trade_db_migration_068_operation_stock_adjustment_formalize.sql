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

DROP PROCEDURE IF EXISTS migrate_trade_operation_stock_adjust_log;

DELIMITER $$
CREATE PROCEDURE migrate_trade_operation_stock_adjust_log()
BEGIN
  IF EXISTS (
    SELECT 1
    FROM information_schema.tables
    WHERE table_schema = DATABASE()
      AND table_name = 'trade_distributed_tx_demo_log'
  ) THEN
    INSERT IGNORE INTO trade_operation_stock_adjust_log (
      id,
      operation_id,
      operator_id,
      package_id,
      adjust_quantity,
      reason,
      stock,
      sold,
      tx_status,
      create_time,
      update_time
    )
    SELECT
      id,
      operation_id,
      operator_id,
      package_id,
      adjust_quantity,
      reason,
      stock,
      sold,
      tx_status,
      create_time,
      update_time
    FROM trade_distributed_tx_demo_log;
  END IF;
END$$
DELIMITER ;

CALL migrate_trade_operation_stock_adjust_log();

DROP PROCEDURE IF EXISTS migrate_trade_operation_stock_adjust_log;

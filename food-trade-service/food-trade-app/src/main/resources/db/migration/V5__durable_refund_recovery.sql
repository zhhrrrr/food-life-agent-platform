ALTER TABLE refund_order
  ADD COLUMN business_completed TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'money and business compensation completed',
  ADD COLUMN recovery_count INT NOT NULL DEFAULT 0 COMMENT 'automatic recovery attempts; manual retry remains available',
  ADD COLUMN next_retry_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'next automatic recovery time',
  ADD KEY idx_refund_recovery (business_completed, next_retry_time);

-- Do not replay historical stock releases: the old code did not store per-order Redis markers.
UPDATE refund_order r
JOIN payment_order p ON p.pay_order_no = r.pay_order_no
JOIN dining_order d ON d.id = r.order_id AND d.user_id = r.user_id
SET r.business_completed = 1
WHERE r.refund_status = 'SUCCESS' AND p.pay_status = 'REFUNDED'
  AND d.order_status IN ('REFUNDED', 'CANCELED');

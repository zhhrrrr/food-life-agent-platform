-- These events refreshed search documents or logged changes; business mutations
-- and idempotency records are already committed independently of their delivery.
-- Keep the audit rows and terminate retries for the retired package exchange.
UPDATE business_local_message
SET message_status = 'FAILED',
    fail_reason = 'retired: package trading facts are queried through business API',
    update_time = CURRENT_TIMESTAMP
WHERE biz_type = 'BUSINESS_EVENT'
  AND message_status IN ('INIT', 'PROCESSING')
  AND message_type IN ('stock.occupied', 'stock.released', 'stock.sold.confirmed', 'stock.rollback', 'package.changed')
  AND JSON_VALID(content)
  AND JSON_UNQUOTE(JSON_EXTRACT(content, '$.topic')) = 'package_stock_topic';

param(
  [string]$ManagementBaseUrl = "http://127.0.0.1:15672",
  [string]$Username = "guest",
  [string]$Password = "guest",
  [string]$VirtualHost = "/"
)

$ErrorActionPreference = "Stop"

$pair = "$Username`:$Password"
$token = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes($pair))
$headers = @{ Authorization = "Basic $token" }
$vhost = [Uri]::EscapeDataString($VirtualHost)

$queues = @(
  "food.trade.order.event.queue",
  "food.trade.payment.event.queue",
  "food.trade.order.timeout.close.queue",
  "food.trade.order.timeout.delay.queue",
  "food.trade.order.event.dlq",
  "food.trade.payment.event.dlq",
  "food.trade.order.timeout.close.dlq",
  "food.business.review.created.queue",
  "food.business.package.stock.event.queue",
  "food.business.review.created.dlq",
  "food.business.package.stock.event.dlq"
)

$exchanges = @(
  "trade_order_topic",
  "payment_topic",
  "food.trade.dlx",
  "package_stock_topic",
  "shop_review_topic",
  "food.business.dlx"
)

foreach ($queue in $queues) {
  $encoded = [Uri]::EscapeDataString($queue)
  try {
    Invoke-RestMethod -Method Delete -Uri "$ManagementBaseUrl/api/queues/$vhost/$encoded" -Headers $headers -TimeoutSec 10 | Out-Null
    Write-Host "Deleted queue $queue"
  } catch {
    if ($_.Exception.Response.StatusCode.value__ -ne 404) {
      throw
    }
  }
}

foreach ($exchange in $exchanges) {
  $encoded = [Uri]::EscapeDataString($exchange)
  try {
    Invoke-RestMethod -Method Delete -Uri "$ManagementBaseUrl/api/exchanges/$vhost/$encoded" -Headers $headers -TimeoutSec 10 | Out-Null
    Write-Host "Deleted exchange $exchange"
  } catch {
    if ($_.Exception.Response.StatusCode.value__ -ne 404) {
      throw
    }
  }
}

Write-Host "RabbitMQ food topology reset completed. Restart business-service and trade-service to declare DLQ topology."

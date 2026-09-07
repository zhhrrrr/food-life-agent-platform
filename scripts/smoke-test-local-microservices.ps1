param(
    [string]$GatewayBaseUrl = "http://127.0.0.1:8080",
    [string]$Phone = "13800000003"
)

$ErrorActionPreference = "Stop"

& (Join-Path $PSScriptRoot "check-microservice-infra.ps1")

function Assert-SuccessResponse {
    param(
        [string]$Name,
        [object]$Response
    )

    if ($Response.code -ne "0000") {
        throw "$Name failed: $($Response | ConvertTo-Json -Compress -Depth 8)"
    }
    Write-Host "OK $Name"
}

$health = Invoke-RestMethod "$GatewayBaseUrl/health"
Assert-SuccessResponse -Name "gateway health" -Response $health

$categoryList = Invoke-RestMethod "$GatewayBaseUrl/api/shop-category/list"
Assert-SuccessResponse -Name "shop category list" -Response $categoryList

$snapshot = Invoke-RestMethod "$GatewayBaseUrl/api/package/trade-snapshot/1"
Assert-SuccessResponse -Name "package trade snapshot" -Response $snapshot

Invoke-RestMethod -Method Post -Uri "$GatewayBaseUrl/api/user/code?phone=$Phone" | Out-Null
$code = (& redis-cli GET "food:login:code:$Phone")
if ([string]::IsNullOrWhiteSpace($code)) {
    throw "login code not found in Redis for phone $Phone"
}

$loginBody = @{ phone = $Phone; code = $code } | ConvertTo-Json
$login = Invoke-RestMethod -Method Post -Uri "$GatewayBaseUrl/api/user/login" -ContentType "application/json" -Body $loginBody
Assert-SuccessResponse -Name "user login" -Response $login

$headers = @{ authorization = $login.data.token }
$me = Invoke-RestMethod -Method Get -Uri "$GatewayBaseUrl/api/user/me" -Headers $headers
Assert-SuccessResponse -Name "current user" -Response $me

$orderBody = @{ packageId = 1; quantity = 1 } | ConvertTo-Json
$order = Invoke-RestMethod -Method Post -Uri "$GatewayBaseUrl/api/trade/orders/normal" -ContentType "application/json" -Headers $headers -Body $orderBody
Assert-SuccessResponse -Name "normal order create" -Response $order

$seckillActivities = Invoke-RestMethod -Method Get -Uri "$GatewayBaseUrl/api/trade/seckill/activities" -Headers $headers
Assert-SuccessResponse -Name "seckill activity list" -Response $seckillActivities

$forbiddenBody = @{ orderNo = "smoke-public-block"; quantity = 1 } | ConvertTo-Json
try {
    Invoke-RestMethod -Method Post -Uri "$GatewayBaseUrl/api/package/1/stock/occupy" -ContentType "application/json" -Body $forbiddenBody | Out-Null
    throw "public package stock occupy should be forbidden"
} catch {
    $response = $_.Exception.Response
    if ($null -eq $response -or [int]$response.StatusCode -ne 403) {
        throw
    }
    Write-Host "OK public stock write forbidden"
}

$rabbitAuth = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes("guest:guest"))
$overview = Invoke-RestMethod "http://127.0.0.1:15672/api/overview" -Headers @{ Authorization = "Basic $rabbitAuth" }
if ($overview.rabbitmq_version -ne "4.3.5") {
    throw "RabbitMQ version mismatch: $($overview.rabbitmq_version)"
}
Write-Host "OK RabbitMQ management API"

Write-Host "Smoke test completed. Created orderId=$($order.data.orderId), orderNo=$($order.data.orderNo)"

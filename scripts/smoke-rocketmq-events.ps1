param(
    [string]$GatewayBaseUrl = "http://localhost:8080",
    [string]$Phone = "13800138063",
    [string]$Mysql = "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"
)

$ErrorActionPreference = "Stop"

function Assert-Code {
    param(
        [string]$Name,
        [object]$Response,
        [string]$ExpectedCode
    )
    if ($Response.code -ne $ExpectedCode) {
        throw "$Name failed. expected code=$ExpectedCode, actual code=$($Response.code), message=$($Response.message)"
    }
    Write-Host "OK $Name -> code=$($Response.code)"
    return $Response
}

function Invoke-JsonPost {
    param(
        [string]$Name,
        [string]$Uri,
        [object]$Body,
        [string]$ExpectedCode,
        [hashtable]$Headers = @{}
    )
    $json = $Body | ConvertTo-Json -Compress
    $response = Invoke-RestMethod -Method Post -Uri $Uri -ContentType "application/json" -Headers $Headers -Body $json -TimeoutSec 10
    return Assert-Code -Name $Name -Response $response -ExpectedCode $ExpectedCode
}

function Invoke-FormPost {
    param(
        [string]$Name,
        [string]$Uri,
        [string]$ExpectedCode,
        [hashtable]$Headers = @{}
    )
    $response = Invoke-RestMethod -Method Post -Uri $Uri -Headers $Headers -TimeoutSec 10
    return Assert-Code -Name $Name -Response $response -ExpectedCode $ExpectedCode
}

function Invoke-MysqlScalar {
    param([string]$Sql)
    $result = & $Mysql -uroot -proot -N -B -e $Sql
    if ($LASTEXITCODE -ne 0) {
        throw "mysql query failed: $Sql"
    }
    return (($result | Select-Object -Last 1) -as [string]).Trim()
}

Invoke-FormPost `
    -Name "send login code" `
    -Uri "$GatewayBaseUrl/api/user/code?phone=$Phone" `
    -ExpectedCode "0000" | Out-Null

$code = (& redis-cli -h localhost -p 6379 -n 0 get "food:login:code:$Phone") -join ""
if ([string]::IsNullOrWhiteSpace($code)) {
    throw "login code missing in redis"
}

$login = Invoke-JsonPost `
    -Name "login" `
    -Uri "$GatewayBaseUrl/api/user/login" `
    -Body @{ phone = $Phone; code = $code } `
    -ExpectedCode "0000"

$headers = @{ authorization = $login.data.token }

$order = Invoke-JsonPost `
    -Name "create normal order" `
    -Uri "$GatewayBaseUrl/api/trade/orders/normal" `
    -Headers $headers `
    -Body @{ packageId = 1; quantity = 1 } `
    -ExpectedCode "0000"

$orderId = $order.data.orderId
if ($null -eq $orderId) {
    throw "orderId missing"
}

$payment = Invoke-JsonPost `
    -Name "prepare payment" `
    -Uri "$GatewayBaseUrl/api/trade/pay/orders/$orderId/prepare" `
    -Headers $headers `
    -Body @{ source = "FOOD_LIFE"; channel = "MOCK_PAY" } `
    -ExpectedCode "0000"

$payOrderNo = $payment.data.payOrderNo
$payAmount = $payment.data.payAmount

Invoke-JsonPost `
    -Name "mock payment callback" `
    -Uri "$GatewayBaseUrl/api/trade/pay/callback/mock" `
    -Body @{ payOrderNo = $payOrderNo; outTradeNo = "OUT$([DateTimeOffset]::Now.ToUnixTimeMilliseconds())"; payAmount = $payAmount } `
    -ExpectedCode "0000" | Out-Null

Invoke-FormPost `
    -Name "use order" `
    -Uri "$GatewayBaseUrl/api/trade/orders/$orderId/use/mock" `
    -Headers $headers `
    -ExpectedCode "0000" | Out-Null

$review = Invoke-JsonPost `
    -Name "create review" `
    -Uri "$GatewayBaseUrl/api/reviews" `
    -Headers $headers `
    -Body @{ orderId = $orderId; score = 5; content = "rocketmq event smoke review"; images = "" } `
    -ExpectedCode "0000"

$reviewNo = $review.data.reviewNo
if ([string]::IsNullOrWhiteSpace($reviewNo)) {
    throw "reviewNo missing"
}

$tradeEventCount = Invoke-MysqlScalar -Sql "SELECT COUNT(*) FROM food_trade_db.trade_local_message WHERE biz_type='TRADE_EVENT' AND biz_id IN ('$orderId', '$payOrderNo') AND message_status='SUCCESS';"
if ([int]$tradeEventCount -lt 5) {
    throw "trade event message count invalid, actual=$tradeEventCount"
}
Write-Host "OK trade local event messages -> count=$tradeEventCount"

$reviewConsumeCount = Invoke-MysqlScalar -Sql "SELECT COUNT(*) FROM food_business_db.business_consumed_message WHERE biz_key='$reviewNo' AND consume_status='SUCCESS';"
if ([int]$reviewConsumeCount -lt 1) {
    throw "review consumed message missing, reviewNo=$reviewNo"
}
Write-Host "OK review async stats idempotent message -> count=$reviewConsumeCount"

Write-Host "RocketMQ event smoke verification completed. orderId=$orderId, payOrderNo=$payOrderNo, reviewNo=$reviewNo"


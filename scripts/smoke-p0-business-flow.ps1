param(
    [string]$GatewayBaseUrl = "http://localhost:8080",
    [string]$Phone = "13800138100",
    [long]$PackageId = 1,
    [string]$LocalPaymentSecret = "local-payment-secret",
    [string]$Mysql = "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"
)

$ErrorActionPreference = "Stop"

function Assert-Code {
    param(
        [string]$Name,
        [object]$Response,
        [string]$ExpectedCode = "0000"
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
        [hashtable]$Headers = @{},
        [string]$ExpectedCode = "0000"
    )
    $json = $Body | ConvertTo-Json -Compress -Depth 8
    $response = Invoke-RestMethod -Method Post -Uri $Uri -ContentType "application/json" -Headers $Headers -Body $json -TimeoutSec 20
    return Assert-Code -Name $Name -Response $response -ExpectedCode $ExpectedCode
}

function Invoke-Get {
    param(
        [string]$Name,
        [string]$Uri,
        [hashtable]$Headers = @{},
        [string]$ExpectedCode = "0000"
    )
    $response = Invoke-RestMethod -Method Get -Uri $Uri -Headers $Headers -TimeoutSec 20
    return Assert-Code -Name $Name -Response $response -ExpectedCode $ExpectedCode
}

function Build-LocalPaymentSignature {
    param(
        [string]$PayOrderNo,
        [string]$OutTradeNo,
        [long]$PayAmount
    )
    $plainText = "$PayOrderNo|$OutTradeNo|$PayAmount|LOCAL_PAY|$LocalPaymentSecret"
    $sha256 = [System.Security.Cryptography.SHA256]::Create()
    try {
        $bytes = [System.Text.Encoding]::UTF8.GetBytes($plainText)
        $hash = $sha256.ComputeHash($bytes)
        return (($hash | ForEach-Object { $_.ToString("x2") }) -join "")
    } finally {
        $sha256.Dispose()
    }
}

function Grant-LocalAdminRole {
    param([long]$UserId)

    $sql = "INSERT INTO food_user_db.user_role (user_id, role_code, role_name, status) VALUES ($UserId, 'ADMIN', 'local smoke admin', 1) ON DUPLICATE KEY UPDATE status=1, role_name='local smoke admin';"
    & $Mysql -uroot -proot -e $sql | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "grant local admin role failed for userId=$UserId"
    }
}

function Login-And-ReturnHeaders {
    Invoke-RestMethod -Method Post -Uri "$GatewayBaseUrl/api/user/code?phone=$Phone" -TimeoutSec 10 | Out-Null
    $code = (& redis-cli -h localhost -p 6379 -n 0 get "food:login:code:$Phone") -join ""
    if ([string]::IsNullOrWhiteSpace($code)) {
        throw "login code missing in redis"
    }

    $login = Invoke-JsonPost `
        -Name "login" `
        -Uri "$GatewayBaseUrl/api/user/login" `
        -Body @{ phone = $Phone; code = $code }
    return @{ authorization = $login.data.token }
}

function New-PaidOrder {
    param([string]$Name)

    $order = Invoke-JsonPost `
        -Name "$Name create normal order" `
        -Uri "$GatewayBaseUrl/api/trade/orders/normal" `
        -Headers $headers `
        -Body @{ packageId = $PackageId; quantity = 1 }
    $orderId = $order.data.orderId

    $payment = Invoke-JsonPost `
        -Name "$Name prepare local payment" `
        -Uri "$GatewayBaseUrl/api/trade/pay/orders/$orderId/prepare" `
        -Headers $headers `
        -Body @{ source = "FOOD_LIFE_WEB"; channel = "LOCAL_PAY" }

    $payOrderNo = $payment.data.payOrderNo
    $payAmount = [long]$payment.data.payAmount
    $outTradeNo = "LOCAL_OUT_$([DateTimeOffset]::Now.ToUnixTimeMilliseconds())"
    $signature = Build-LocalPaymentSignature -PayOrderNo $payOrderNo -OutTradeNo $outTradeNo -PayAmount $payAmount

    Invoke-JsonPost `
        -Name "$Name signed local payment callback" `
        -Uri "$GatewayBaseUrl/api/trade/pay/callback/local" `
        -Body @{
            payOrderNo = $payOrderNo
            outTradeNo = $outTradeNo
            payAmount = $payAmount
            payTime = (Get-Date).ToString("yyyy-MM-ddTHH:mm:ss")
            signType = "SHA256"
            signature = $signature
        } | Out-Null

    return @{
        orderId = $orderId
        orderNo = $order.data.orderNo
        payOrderNo = $payOrderNo
        payAmount = $payAmount
    }
}

Invoke-Get -Name "gateway health" -Uri "$GatewayBaseUrl/health" | Out-Null
Invoke-Get -Name "package trade snapshot" -Uri "$GatewayBaseUrl/api/package/trade-snapshot/$PackageId" | Out-Null

$headers = Login-And-ReturnHeaders
$me = Invoke-Get -Name "current user" -Uri "$GatewayBaseUrl/api/user/me" -Headers $headers
Grant-LocalAdminRole -UserId ([long]$me.data.id)
$headers = Login-And-ReturnHeaders
$me = Invoke-Get -Name "current admin user from user_role" -Uri "$GatewayBaseUrl/api/user/me" -Headers $headers
if ($me.data.role -ne "ADMIN") {
    throw "current user role should be ADMIN, actual=$($me.data.role)"
}

$useFlow = New-PaidOrder -Name "use flow"
$orderId = $useFlow.orderId

Invoke-JsonPost `
    -Name "use order" `
    -Uri "$GatewayBaseUrl/api/trade/orders/$orderId/use/local" `
    -Headers $headers `
    -Body @{} | Out-Null

Invoke-JsonPost `
    -Name "create review" `
    -Uri "$GatewayBaseUrl/api/reviews" `
    -Headers $headers `
    -Body @{ orderId = $orderId; score = 5; content = "p0 business flow smoke review"; images = "" } | Out-Null

$refundFlow = New-PaidOrder -Name "refund flow"
$refundOrderId = $refundFlow.orderId

$refund = Invoke-JsonPost `
    -Name "apply refund with refund order" `
    -Uri "$GatewayBaseUrl/api/trade/orders/$refundOrderId/refund/apply" `
    -Headers $headers `
    -Body @{ source = "FOOD_LIFE_WEB"; channel = "LOCAL_PAY"; refundReason = "p0 smoke refund" }

if ([string]::IsNullOrWhiteSpace($refund.data.refundOrderNo)) {
    throw "refundOrderNo missing"
}

$operatorHeaders = $headers
$reconcile = Invoke-JsonPost `
    -Name "operation payment reconcile" `
    -Uri "$GatewayBaseUrl/api/trade/operations/payments/reconcile?limit=100" `
    -Headers $operatorHeaders `
    -Body @{}

if ($null -eq $reconcile.data.scannedPaymentCount) {
    throw "payment reconcile data missing"
}

$audit = Invoke-Get `
    -Name "operation audit query" `
    -Uri "$GatewayBaseUrl/api/trade/operations/audit-logs?bizId=$orderId&limit=20" `
    -Headers $operatorHeaders

if ($null -eq $audit.data.logs) {
    throw "audit logs missing"
}

Write-Host "P0 business flow smoke completed."
Write-Host "userId=$($me.data.id), role=$($me.data.role), usedOrderId=$orderId, refundOrderId=$refundOrderId, refundOrderNo=$($refund.data.refundOrderNo)"

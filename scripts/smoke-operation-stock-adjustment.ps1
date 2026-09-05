param(
    [string]$GatewayBaseUrl = "http://localhost:8080",
    [string]$Phone = "13800138066",
    [long]$PackageId = 1
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
    $response = Invoke-RestMethod -Method Post -Uri $Uri -ContentType "application/json" -Headers $Headers -Body $json -TimeoutSec 15
    return Assert-Code -Name $Name -Response $response -ExpectedCode $ExpectedCode
}

Invoke-RestMethod -Method Post -Uri "$GatewayBaseUrl/api/user/code?phone=$Phone" -TimeoutSec 10 | Out-Null
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

$plusOperationId = "OPERATION_STOCK_PLUS_$([DateTimeOffset]::Now.ToUnixTimeMilliseconds())"
$plus = Invoke-JsonPost `
    -Name "operation stock adjustment plus" `
    -Uri "$GatewayBaseUrl/api/trade/operations/package-stock-adjustments" `
    -Headers $headers `
    -Body @{
        packageId = $PackageId
        adjustQuantity = 1
        reason = "operation stock smoke plus"
        operationId = $plusOperationId
    } `
    -ExpectedCode "0000"

$minusOperationId = "OPERATION_STOCK_MINUS_$([DateTimeOffset]::Now.ToUnixTimeMilliseconds())"
$minus = Invoke-JsonPost `
    -Name "operation stock adjustment minus" `
    -Uri "$GatewayBaseUrl/api/trade/operations/package-stock-adjustments" `
    -Headers $headers `
    -Body @{
        packageId = $PackageId
        adjustQuantity = -1
        reason = "operation stock smoke minus"
        operationId = $minusOperationId
    } `
    -ExpectedCode "0000"

Write-Host "Operation stock adjustment smoke verification completed."
Write-Host "Plus operationId=$($plus.data.operationId), stock=$($plus.data.stock)"
Write-Host "Minus operationId=$($minus.data.operationId), stock=$($minus.data.stock)"

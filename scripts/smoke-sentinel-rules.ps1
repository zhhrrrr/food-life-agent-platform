param(
    [string]$GatewayBaseUrl = "http://localhost:8080",
    [string]$Phone = "13800138000"
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

$token = $login.data.token
if ([string]::IsNullOrWhiteSpace($token)) {
    throw "login token missing"
}

$headers = @{ authorization = $token }
$invalidOrderBody = @{ packageId = $null; quantity = 1 }
for ($i = 1; $i -le 5; $i++) {
    Invoke-JsonPost `
        -Name "trade user order sentinel warmup $i" `
        -Uri "$GatewayBaseUrl/api/trade/orders/normal" `
        -Headers $headers `
        -Body $invalidOrderBody `
        -ExpectedCode "400" | Out-Null
}

Invoke-JsonPost `
    -Name "trade user order sentinel limited" `
    -Uri "$GatewayBaseUrl/api/trade/orders/normal" `
    -Headers $headers `
    -Body $invalidOrderBody `
    -ExpectedCode "429" | Out-Null

for ($i = 1; $i -le 20; $i++) {
    Invoke-FormPost `
        -Name "package stock hotspot warmup $i" `
        -Uri "$GatewayBaseUrl/api/package/1/stock/occupy?quantity=0&operationId=sentinel-smoke-$i" `
        -ExpectedCode "400" | Out-Null
}

Invoke-FormPost `
    -Name "package stock hotspot limited" `
    -Uri "$GatewayBaseUrl/api/package/1/stock/occupy?quantity=0&operationId=sentinel-smoke-limited" `
    -ExpectedCode "429" | Out-Null

Write-Host "Sentinel rule smoke verification completed."

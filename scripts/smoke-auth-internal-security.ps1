param(
    [string]$GatewayBaseUrl = "http://localhost:8080",
    [string]$BusinessBaseUrl = "http://localhost:8201",
    [string]$Phone = "13800138066",
    [long]$PackageId = 1
)

$ErrorActionPreference = "Stop"

function Invoke-Http {
    param(
        [string]$Name,
        [string]$Method,
        [string]$Uri,
        [hashtable]$Headers = @{},
        [object]$Body = $null,
        [int]$ExpectedStatus,
        [string]$ExpectedCode = $null
    )

    $args = @("-s", "-w", "`n%{http_code}", "--connect-timeout", "10", "--max-time", "20", "-X", $Method)
    foreach ($key in $Headers.Keys) {
        $args += @("-H", "$key`: $($Headers[$key])")
    }
    $bodyFile = $null
    if ($null -ne $Body) {
        $json = $Body | ConvertTo-Json -Compress
        $bodyFile = Join-Path $env:TEMP ("food-auth-smoke-" + [guid]::NewGuid().ToString() + ".json")
        [System.IO.File]::WriteAllText($bodyFile, $json, [System.Text.UTF8Encoding]::new($false))
        $args += @("-H", "Content-Type: application/json", "--data-binary", "@$bodyFile")
    }
    $args += $Uri

    $raw = (& curl.exe @args) -join "`n"
    if ($bodyFile -and (Test-Path -LiteralPath $bodyFile)) {
        Remove-Item -LiteralPath $bodyFile -Force
    }
    $splitIndex = $raw.LastIndexOf("`n")
    if ($splitIndex -lt 0) {
        throw "$Name failed. curl output is invalid."
    }
    $bodyText = $raw.Substring(0, $splitIndex)
    $status = [int]$raw.Substring($splitIndex + 1)
    $body = if ([string]::IsNullOrWhiteSpace($bodyText)) { @{ code = ""; message = "" } } else { $bodyText | ConvertFrom-Json }

    if ($status -ne $ExpectedStatus) {
        throw "$Name failed. expected status=$ExpectedStatus, actual status=$status, body=$bodyText"
    }
    if ($ExpectedCode -and $body.code -ne $ExpectedCode) {
        throw "$Name failed. expected code=$ExpectedCode, actual code=$($body.code), message=$($body.message)"
    }

    Write-Host "OK $Name -> status=$status, code=$($body.code)"
    return $body
}

Invoke-Http `
    -Name "gateway protected api without token" `
    -Method "GET" `
    -Uri "$GatewayBaseUrl/api/user/me" `
    -ExpectedStatus 401 `
    -ExpectedCode "401" | Out-Null

Invoke-Http `
    -Name "gateway blocks internal api" `
    -Method "POST" `
    -Uri "$GatewayBaseUrl/api/internal/package/$PackageId/stock/adjust" `
    -Body @{ packageId = $PackageId; adjustQuantity = 1; reason = "blocked by gateway"; operationId = "AUTH_BLOCK_$([DateTimeOffset]::Now.ToUnixTimeMilliseconds())" } `
    -ExpectedStatus 403 `
    -ExpectedCode "403" | Out-Null

Invoke-Http `
    -Name "gateway blocks public package stock mutation" `
    -Method "POST" `
    -Uri "$GatewayBaseUrl/api/package/$PackageId/stock/occupy?quantity=0&operationId=AUTH_PUBLIC_BLOCK_$([DateTimeOffset]::Now.ToUnixTimeMilliseconds())" `
    -ExpectedStatus 403 `
    -ExpectedCode "403" | Out-Null

Invoke-Http `
    -Name "direct business internal api without secret" `
    -Method "POST" `
    -Uri "$BusinessBaseUrl/api/internal/package/$PackageId/stock/adjust" `
    -Body @{ packageId = $PackageId; adjustQuantity = 1; reason = "missing internal secret"; operationId = "AUTH_DIRECT_BLOCK_$([DateTimeOffset]::Now.ToUnixTimeMilliseconds())" } `
    -ExpectedStatus 403 `
    -ExpectedCode "403" | Out-Null

Invoke-Http `
    -Name "send login code" `
    -Method "POST" `
    -Uri "$GatewayBaseUrl/api/user/code?phone=$Phone" `
    -ExpectedStatus 200 `
    -ExpectedCode "0000" | Out-Null

$code = (& redis-cli -h localhost -p 6379 -n 0 get "food:login:code:$Phone") -join ""
if ([string]::IsNullOrWhiteSpace($code)) {
    throw "login code missing in redis"
}

$login = Invoke-Http `
    -Name "login" `
    -Method "POST" `
    -Uri "$GatewayBaseUrl/api/user/login" `
    -Body @{ phone = $Phone; code = $code } `
    -ExpectedStatus 200 `
    -ExpectedCode "0000"

$headers = @{ authorization = $login.data.token; "x-user-id" = "999999"; "x-internal-call" = "spoofed"; "x-internal-secret" = "spoofed" }

Invoke-Http `
    -Name "gateway auth passes and strips spoofed headers" `
    -Method "GET" `
    -Uri "$GatewayBaseUrl/api/user/me" `
    -Headers $headers `
    -ExpectedStatus 200 `
    -ExpectedCode "0000" | Out-Null

$plusOperationId = "AUTH_INTERNAL_PLUS_$([DateTimeOffset]::Now.ToUnixTimeMilliseconds())"
Invoke-Http `
    -Name "feign internal secret allows operation stock adjustment plus" `
    -Method "POST" `
    -Uri "$GatewayBaseUrl/api/trade/operations/package-stock-adjustments" `
    -Headers @{ authorization = $login.data.token } `
    -Body @{ packageId = $PackageId; adjustQuantity = 1; reason = "auth smoke plus"; operationId = $plusOperationId } `
    -ExpectedStatus 200 `
    -ExpectedCode "0000" | Out-Null

$minusOperationId = "AUTH_INTERNAL_MINUS_$([DateTimeOffset]::Now.ToUnixTimeMilliseconds())"
Invoke-Http `
    -Name "feign internal secret allows operation stock adjustment minus" `
    -Method "POST" `
    -Uri "$GatewayBaseUrl/api/trade/operations/package-stock-adjustments" `
    -Headers @{ authorization = $login.data.token } `
    -Body @{ packageId = $PackageId; adjustQuantity = -1; reason = "auth smoke minus"; operationId = $minusOperationId } `
    -ExpectedStatus 200 `
    -ExpectedCode "0000" | Out-Null

Write-Host "Auth and internal service security smoke verification completed."

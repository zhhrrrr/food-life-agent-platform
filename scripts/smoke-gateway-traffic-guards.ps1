param(
    [string]$GatewayBaseUrl = "http://localhost:8080",
    [string]$RedisCli = "redis-cli",
    [string]$RedisHost = "localhost",
    [int]$RedisPort = 6379,
    [int]$RedisDatabase = 0,
    [int]$IpCapacity = 120,
    [int]$UserCapacity = 60,
    [int]$WindowSeconds = 60
)

$ErrorActionPreference = "Stop"

function Get-Sha256Hex {
    param([string]$Value)

    $sha256 = [System.Security.Cryptography.SHA256]::Create()
    try {
        $bytes = [System.Text.Encoding]::UTF8.GetBytes($Value)
        return (($sha256.ComputeHash($bytes) | ForEach-Object { $_.ToString("x2") }) -join "")
    } finally {
        $sha256.Dispose()
    }
}

function Invoke-Redis {
    param([string[]]$RedisArgs)

    $result = & $RedisCli -h $RedisHost -p $RedisPort -n $RedisDatabase @RedisArgs
    if ($LASTEXITCODE -ne 0) {
        throw "redis-cli failed: $($RedisArgs -join ' ')"
    }
    return $result
}

function Invoke-SmokeGet {
    param(
        [string]$Name,
        [string]$Uri,
        [int]$ExpectedStatus,
        [string]$ExpectedCode,
        [hashtable]$Headers = @{}
    )

    $curlArgs = @("-s", "-w", "`n%{http_code}", "--connect-timeout", "10", "--max-time", "10")
    foreach ($header in $Headers.GetEnumerator()) {
        $curlArgs += @("-H", "$($header.Key): $($header.Value)")
    }
    $curlArgs += $Uri

    $raw = (& curl.exe @curlArgs) -join "`n"
    $splitIndex = $raw.LastIndexOf("`n")
    if ($splitIndex -lt 0) {
        throw "$Name failed. curl output is invalid."
    }
    $body = $raw.Substring(0, $splitIndex)
    $status = [int]$raw.Substring($splitIndex + 1)

    if (-not [string]::IsNullOrWhiteSpace($body)) {
        $response = $body | ConvertFrom-Json
    } else {
        $response = @{ code = ""; message = "" }
    }

    if ($status -ne $ExpectedStatus) {
        throw "$Name failed. expected status=$ExpectedStatus, actual status=$status, body=$body"
    }
    if ($ExpectedCode -and $response.code -ne $ExpectedCode) {
        throw "$Name failed. expected code=$ExpectedCode, actual code=$($response.code), message=$($response.message)"
    }

    Write-Host "OK $Name -> status=$status, code=$($response.code)"
}

Invoke-SmokeGet `
    -Name "gateway blacklist" `
    -Uri "$GatewayBaseUrl/internal/ping" `
    -ExpectedStatus 403 `
    -ExpectedCode "403"

$bucket = [math]::Floor([DateTimeOffset]::UtcNow.ToUnixTimeSeconds() / $WindowSeconds)
$ip = "10.99.0.61"
$token = "traffic-guard-smoke-token"
$ipKey = "food:gateway:rate-limit:ip:$(Get-Sha256Hex $ip):$bucket"
$userKey = "food:gateway:rate-limit:user:$(Get-Sha256Hex $token):$bucket"

try {
    Invoke-Redis -RedisArgs @("setex", $ipKey, "$WindowSeconds", "$IpCapacity") | Out-Null
    Invoke-SmokeGet `
        -Name "gateway ip rate limit" `
        -Uri "$GatewayBaseUrl/api/shop-category/list" `
        -ExpectedStatus 429 `
        -ExpectedCode "429" `
        -Headers @{ "X-Forwarded-For" = $ip }

    Invoke-Redis -RedisArgs @("setex", $userKey, "$WindowSeconds", "$UserCapacity") | Out-Null
    Invoke-SmokeGet `
        -Name "gateway user rate limit" `
        -Uri "$GatewayBaseUrl/api/user/me" `
        -ExpectedStatus 429 `
        -ExpectedCode "429" `
        -Headers @{
            "X-Forwarded-For" = "10.99.0.62"
            "Authorization" = "Bearer $token"
        }
} finally {
    Invoke-Redis -RedisArgs @("del", $ipKey, $userKey) | Out-Null
}

Write-Host "Gateway traffic guard smoke verification completed."

param(
    [string]$GatewayBaseUrl = "http://localhost:8080"
)

$ErrorActionPreference = "Stop"

function Assert-SmokeResponse {
    param(
        [string]$Name,
        [string]$Raw,
        [int]$ExpectedStatus,
        [string]$ExpectedCode
    )

    $splitIndex = $Raw.LastIndexOf("`n")
    if ($splitIndex -lt 0) {
        throw "$Name failed. curl output is invalid."
    }
    $body = $Raw.Substring(0, $splitIndex)
    $status = [int]$Raw.Substring($splitIndex + 1)

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
    Assert-SmokeResponse -Name $Name -Raw $raw -ExpectedStatus $ExpectedStatus -ExpectedCode $ExpectedCode
}

Invoke-SmokeGet `
    -Name "gateway blacklist" `
    -Uri "$GatewayBaseUrl/internal/ping" `
    -ExpectedStatus 403 `
    -ExpectedCode "403"

$smokeHeaderValue = "gateway-sentinel-$(Get-Random)"
Invoke-SmokeGet `
    -Name "gateway sentinel first request" `
    -Uri "$GatewayBaseUrl/api/shop-category/list" `
    -ExpectedStatus 200 `
    -ExpectedCode "0000" `
    -Headers @{ "X-Sentinel-Smoke" = $smokeHeaderValue }

Invoke-SmokeGet `
    -Name "gateway sentinel limited request" `
    -Uri "$GatewayBaseUrl/api/shop-category/list" `
    -ExpectedStatus 429 `
    -ExpectedCode "429" `
    -Headers @{ "X-Sentinel-Smoke" = $smokeHeaderValue }

Write-Host "Gateway traffic guard smoke verification completed."

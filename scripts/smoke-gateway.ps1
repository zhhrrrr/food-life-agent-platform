param(
    [string]$GatewayBaseUrl = "http://localhost:8080"
)

$ErrorActionPreference = "Stop"

function Invoke-SmokeGet {
    param(
        [string]$Name,
        [string]$Uri,
        [int]$ExpectedStatus = 200,
        [string]$ExpectedCode = "0000"
    )

    $raw = (& curl.exe -s -w "`n%{http_code}" --connect-timeout 10 --max-time 10 $Uri) -join "`n"
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
        throw "$Name failed. expected status=$ExpectedStatus, actual status=$status"
    }

    if ($ExpectedCode -and $response.code -ne $ExpectedCode) {
        throw "$Name failed. expected code=$ExpectedCode, actual code=$($response.code), message=$($response.message)"
    }
    Write-Host "OK $Name -> status=$status, code=$($response.code)"
    return $response
}

Invoke-SmokeGet -Name "gateway health" -Uri "$GatewayBaseUrl/health" | Out-Null
Invoke-SmokeGet -Name "user route unauthenticated" -Uri "$GatewayBaseUrl/api/user/me" -ExpectedStatus 401 -ExpectedCode "401" | Out-Null
Invoke-SmokeGet -Name "trade route unauthenticated" -Uri "$GatewayBaseUrl/api/trade/orders" -ExpectedStatus 401 -ExpectedCode "401" | Out-Null
Invoke-SmokeGet -Name "shop category route" -Uri "$GatewayBaseUrl/api/shop-category/list" | Out-Null
Invoke-SmokeGet -Name "shop route" -Uri "$GatewayBaseUrl/api/shop/1" | Out-Null
Invoke-SmokeGet -Name "package snapshot route" -Uri "$GatewayBaseUrl/api/package/trade-snapshot/1" | Out-Null

Write-Host "Gateway smoke verification completed."

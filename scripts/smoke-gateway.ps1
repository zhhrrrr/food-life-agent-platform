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

    try {
        $webResponse = Invoke-WebRequest -Method Get -Uri $Uri -TimeoutSec 10 -UseBasicParsing
        $status = [int]$webResponse.StatusCode
        $response = $webResponse.Content | ConvertFrom-Json
    } catch {
        if ($null -eq $_.Exception.Response) {
            throw
        }
        $status = [int]$_.Exception.Response.StatusCode
        $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
        $body = $reader.ReadToEnd()
        if (-not [string]::IsNullOrWhiteSpace($body)) {
            $response = $body | ConvertFrom-Json
        } else {
            $response = @{ code = ""; message = "" }
        }
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
Invoke-SmokeGet -Name "user route unauthenticated" -Uri "$GatewayBaseUrl/api/user/me" -ExpectedStatus 401 -ExpectedCode "" | Out-Null
Invoke-SmokeGet -Name "shop category route" -Uri "$GatewayBaseUrl/api/shop-category/list" | Out-Null
Invoke-SmokeGet -Name "shop route" -Uri "$GatewayBaseUrl/api/shop/1" | Out-Null
Invoke-SmokeGet -Name "package snapshot route" -Uri "$GatewayBaseUrl/api/package/trade-snapshot/1" | Out-Null

Write-Host "Gateway smoke verification completed."

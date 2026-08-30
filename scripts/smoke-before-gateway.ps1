param(
    [string]$Token
)

$ErrorActionPreference = "Stop"

function Assert-Success {
    param(
        [string]$Name,
        [string]$Uri,
        [hashtable]$Headers = @{}
    )
    $response = Invoke-RestMethod -Method Get -Uri $Uri -Headers $Headers -TimeoutSec 5
    if ($response.code -ne "0000") {
        throw "$Name failed, code=$($response.code), message=$($response.message)"
    }
    Write-Host "$Name ok"
    return $response
}

function Assert-Code {
    param(
        [string]$Name,
        [string]$Uri,
        [string]$ExpectedCode
    )
    $response = curl.exe -s $Uri | ConvertFrom-Json
    if ($response.code -ne $ExpectedCode) {
        throw "$Name failed, expected=$ExpectedCode, actual=$($response.code)"
    }
    Write-Host "$Name ok"
}

Assert-Success -Name "user health" -Uri "http://localhost:8101/health" | Out-Null
Assert-Success -Name "business health" -Uri "http://localhost:8201/health" | Out-Null
Assert-Success -Name "trade health" -Uri "http://localhost:8301/health" | Out-Null
Assert-Success -Name "shop categories" -Uri "http://localhost:8201/api/shop-category/list" | Out-Null
Assert-Success -Name "shop detail" -Uri "http://localhost:8201/api/shop/1" | Out-Null
Assert-Success -Name "package snapshot" -Uri "http://localhost:8201/api/package/trade-snapshot/1" | Out-Null
Assert-Code -Name "protected business endpoint without token" -Uri "http://localhost:8201/api/shop-homepage/1" -ExpectedCode "401"
Assert-Code -Name "protected trade endpoint without token" -Uri "http://localhost:8301/api/trade/orders" -ExpectedCode "401"

if ($Token -and $Token.Trim().Length -gt 0) {
    $headers = @{ authorization = $Token }
    Assert-Success -Name "current user" -Uri "http://localhost:8101/api/user/me" -Headers $headers | Out-Null
    Assert-Success -Name "shop homepage" -Uri "http://localhost:8201/api/shop-homepage/1" -Headers $headers | Out-Null
    Assert-Success -Name "order list" -Uri "http://localhost:8301/api/trade/orders?pageSize=5" -Headers $headers | Out-Null
}

Write-Host "Pre-gateway smoke check completed."

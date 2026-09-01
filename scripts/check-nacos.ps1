param(
    [int]$Port = 8848
)

$ErrorActionPreference = "Stop"

$connection = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
if ($null -eq $connection) {
    throw "Nacos port $Port is not listening."
}

$homeUrl = "http://127.0.0.1:$Port/nacos"
$response = Invoke-WebRequest -Method Get -Uri $homeUrl -UseBasicParsing -TimeoutSec 5

Write-Host "Nacos port $Port is listening, pid=$($connection.OwningProcess)"
Write-Host "Nacos console: $homeUrl"
Write-Host "HTTP status: $($response.StatusCode)"

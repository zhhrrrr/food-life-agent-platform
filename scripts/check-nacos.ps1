param(
    [int]$Port = 8848
)

$ErrorActionPreference = "Stop"

$grpcPort = $Port + 1000
for ($i = 1; $i -le 60; $i++) {
    $connection = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
    $grpcConnection = Get-NetTCPConnection -LocalPort $grpcPort -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($null -ne $connection -and $null -ne $grpcConnection) {
        break
    }
    Start-Sleep -Seconds 1
}
if ($null -eq $connection) {
    throw "Nacos port $Port is not listening."
}
if ($null -eq $grpcConnection) {
    throw "Nacos gRPC port $grpcPort is not listening."
}

$homeUrl = "http://127.0.0.1:$Port/nacos"
$status = & curl.exe -s -m 5 -o NUL -w "%{http_code}" $homeUrl
if ($LASTEXITCODE -ne 0 -or ($status -ne "200" -and $status -ne "302")) {
    throw "Nacos console check failed. status=$status"
}

Write-Host "Nacos port $Port is listening, pid=$($connection.OwningProcess)"
Write-Host "Nacos gRPC port $grpcPort is listening, pid=$($grpcConnection.OwningProcess)"
Write-Host "Nacos console: $homeUrl"
Write-Host "HTTP status: $status"

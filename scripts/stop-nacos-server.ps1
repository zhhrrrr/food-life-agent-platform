param(
    [string]$NacosHome = "tools/nacos-server/nacos",
    [int]$Port = 8848
)

$ErrorActionPreference = "Stop"

$Root = Split-Path -Parent $PSScriptRoot
$NacosHomePath = Join-Path $Root $NacosHome
$Shutdown = Join-Path $NacosHomePath "bin/shutdown.cmd"

if (Test-Path $Shutdown) {
    Write-Host "Stopping Nacos with shutdown.cmd"
    Start-Process -FilePath "cmd.exe" `
        -ArgumentList "/c", "shutdown.cmd" `
        -WorkingDirectory (Split-Path -Parent $Shutdown) `
        -Wait `
        -WindowStyle Hidden
}

$connection = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
if ($null -ne $connection) {
    Write-Host "Nacos still listens on port $Port, stopping pid=$($connection.OwningProcess)"
    Stop-Process -Id $connection.OwningProcess -Force
}

Write-Host "Nacos stop command completed."

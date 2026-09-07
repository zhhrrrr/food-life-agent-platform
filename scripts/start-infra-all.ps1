param(
    [switch]$RestartSentinel,
    [switch]$SkipInstall
)

$ErrorActionPreference = "Stop"

if (-not $SkipInstall) {
    & (Join-Path $PSScriptRoot "install-sentinel-dashboard.ps1")
    & (Join-Path $PSScriptRoot "install-seata-server.ps1")
    & (Join-Path $PSScriptRoot "install-local-rabbitmq.ps1")
}

& (Join-Path $PSScriptRoot "start-nacos-server.ps1")

if ($RestartSentinel) {
    & (Join-Path $PSScriptRoot "start-sentinel-dashboard.ps1") -Restart
} else {
    & (Join-Path $PSScriptRoot "start-sentinel-dashboard.ps1")
}

& (Join-Path $PSScriptRoot "start-seata-server.ps1")
& (Join-Path $PSScriptRoot "start-rabbitmq-server.ps1")
& (Join-Path $PSScriptRoot "publish-nacos-configs.ps1")
& (Join-Path $PSScriptRoot "check-microservice-infra.ps1")

Write-Host "All local infrastructure is ready."

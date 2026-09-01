param(
    [switch]$Rebuild,
    [switch]$Restart,
    [switch]$WithoutGateway,
    [string]$NacosServerAddr = "127.0.0.1:8848",
    [string]$NacosUsername = "nacos",
    [string]$NacosPassword = "nacos",
    [string]$NacosNamespace = "",
    [string]$NacosDiscoveryGroup = "FOOD_LIFE_AGENT",
    [string]$NacosConfigGroup = "FOOD_LIFE_AGENT"
)

$ErrorActionPreference = "Stop"

$env:NACOS_SERVER_ADDR = $NacosServerAddr
$env:NACOS_USERNAME = $NacosUsername
$env:NACOS_PASSWORD = $NacosPassword
$env:NACOS_NAMESPACE = $NacosNamespace
$env:NACOS_DISCOVERY_GROUP = $NacosDiscoveryGroup
$env:NACOS_CONFIG_GROUP = $NacosConfigGroup
$env:NACOS_DISCOVERY_ENABLED = "true"
$env:NACOS_CONFIG_ENABLED = "true"

$startScript = Join-Path $PSScriptRoot "start-local-services.ps1"

Write-Host "Starting local services with Nacos."
Write-Host "NACOS_SERVER_ADDR=$env:NACOS_SERVER_ADDR"
Write-Host "NACOS_DISCOVERY_GROUP=$env:NACOS_DISCOVERY_GROUP"
Write-Host "NACOS_CONFIG_GROUP=$env:NACOS_CONFIG_GROUP"

& (Join-Path $PSScriptRoot "check-nacos.ps1") -Port (($NacosServerAddr -split ":")[-1])

$startArgs = @{}
if ($Rebuild) {
    $startArgs.Rebuild = $true
}
if ($Restart) {
    $startArgs.Restart = $true
}
if (-not $WithoutGateway) {
    $startArgs.IncludeGateway = $true
}

& $startScript @startArgs

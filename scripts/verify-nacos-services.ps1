param(
    [string]$NacosServerAddr = "127.0.0.1:8848",
    [string]$Group = "FOOD_LIFE_AGENT",
    [string[]]$Services = @("food-user-service", "food-business-service", "food-trade-service")
)

$ErrorActionPreference = "Stop"

& (Join-Path $PSScriptRoot "check-nacos.ps1") -Port (($NacosServerAddr -split ":")[-1])

$serviceListUrl = "http://$NacosServerAddr/nacos/v1/ns/service/list?pageNo=1&pageSize=100&groupName=$([uri]::EscapeDataString($Group))"
$serviceList = Invoke-RestMethod -Method Get -Uri $serviceListUrl

foreach ($service in $Services) {
    if ($serviceList.doms -notcontains $service) {
        throw "Nacos service not found: $service"
    }

    $instanceUrl = "http://$NacosServerAddr/nacos/v1/ns/instance/list?serviceName=$([uri]::EscapeDataString($service))&groupName=$([uri]::EscapeDataString($Group))"
    $instances = Invoke-RestMethod -Method Get -Uri $instanceUrl
    $healthyHosts = @($instances.hosts | Where-Object { $_.healthy -eq $true -and $_.enabled -eq $true })
    if ($healthyHosts.Count -lt 1) {
        throw "No healthy Nacos instance found: $service"
    }

    foreach ($hostItem in $healthyHosts) {
        Write-Host "Verified Nacos service: $service -> $($hostItem.ip):$($hostItem.port), healthy=$($hostItem.healthy)"
    }
}

Write-Host "Nacos service verification completed."

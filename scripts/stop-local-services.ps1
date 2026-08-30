$ErrorActionPreference = "Stop"

$Services = @(
    @{ Name = "food-user-service"; Port = 8101 },
    @{ Name = "food-business-service"; Port = 8201 },
    @{ Name = "food-trade-service"; Port = 8301 }
)

function Get-PortProcessId {
    param([int]$Port)
    $connection = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($null -eq $connection) {
        return $null
    }
    return $connection.OwningProcess
}

foreach ($service in $Services) {
    $processId = Get-PortProcessId -Port $service.Port
    if ($null -eq $processId) {
        Write-Host "$($service.Name) is not running on port $($service.Port)"
        continue
    }
    Write-Host "Stopping $($service.Name) on port $($service.Port), pid=$processId"
    Stop-Process -Id $processId -Force
}

Write-Host "Local service stop command completed."

param(
    [switch]$IncludeInfrastructure
)

$ErrorActionPreference = "Stop"

$servicePorts = @(8080, 8081, 8101, 8102, 8201, 8202, 8301, 8302, 8730, 8731, 8732, 8733)
$infraPorts = @(8848, 8858, 8799, 8091, 5672, 15672)

$ports = @($servicePorts)
if ($IncludeInfrastructure) {
    $ports += $infraPorts
}

$processIds = @()
foreach ($port in $ports) {
    $connection = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($null -ne $connection) {
        $processIds += $connection.OwningProcess
        Write-Host "Found listener port=$port pid=$($connection.OwningProcess)"
    }
}

$processIds = @($processIds | Sort-Object -Unique)
foreach ($processId in $processIds) {
    Write-Host "Stopping pid=$processId"
    Stop-Process -Id $processId -Force
}

Write-Host "Local service stop completed."

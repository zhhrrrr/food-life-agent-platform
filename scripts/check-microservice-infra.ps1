param(
    [int]$MysqlPort = 3306,
    [int]$RedisPort = 6379,
    [int]$NacosPort = 8848,
    [int]$RabbitMqPort = 5672,
    [int]$RabbitMqManagementPort = 15672,
    [int]$SeataPort = 8091,
    [int]$SentinelDashboardPort = 8858
)

$ErrorActionPreference = "Stop"

function Assert-PortListening {
    param(
        [string]$Name,
        [int]$Port
    )

    $connection = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($null -eq $connection) {
        return "$Name is required but port $Port is not listening."
    }

    Write-Host "OK $Name port=$Port pid=$($connection.OwningProcess)"
    return $null
}

$missing = @()
$missing += Assert-PortListening -Name "MySQL" -Port $MysqlPort
$missing += Assert-PortListening -Name "Redis" -Port $RedisPort
$missing += Assert-PortListening -Name "Nacos" -Port $NacosPort
$missing += Assert-PortListening -Name "RabbitMQ" -Port $RabbitMqPort
$missing += Assert-PortListening -Name "RabbitMQ Management" -Port $RabbitMqManagementPort
$missing += Assert-PortListening -Name "Seata" -Port $SeataPort
$missing += Assert-PortListening -Name "Sentinel Dashboard" -Port $SentinelDashboardPort
$missing = @($missing | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })

if ($missing.Count -gt 0) {
    $message = $missing -join [Environment]::NewLine
    throw $message
}

Write-Host "Microservice infrastructure check completed."

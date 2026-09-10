param(
    [switch]$Rebuild,
    [switch]$Restart,
    [switch]$IncludeGateway,
    [string]$SpringProfilesActive = "nacos",
    [string]$RabbitMqHost = "127.0.0.1",
    [string]$RabbitMqPort = "5672",
    [string]$RabbitMqManagementPort = "15672",
    [string]$RabbitMqUsername = "guest",
    [string]$RabbitMqPassword = "guest",
    [string]$RabbitMqVirtualHost = "/",
    [string]$SeataServerAddr = "127.0.0.1:8091",
    [string]$SentinelDashboardAddr = "localhost:8858"
)

$ErrorActionPreference = "Stop"

$Root = Split-Path -Parent $PSScriptRoot
$Logs = Join-Path $Root "logs"
New-Item -ItemType Directory -Force -Path $Logs | Out-Null

& (Join-Path $PSScriptRoot "use-java17-plus.ps1")

$env:SPRING_PROFILES_ACTIVE = $SpringProfilesActive
$env:NACOS_DISCOVERY_ENABLED = "true"
$env:NACOS_CONFIG_ENABLED = "true"
$env:RABBITMQ_HOST = $RabbitMqHost
$env:RABBITMQ_PORT = $RabbitMqPort
$env:RABBITMQ_USERNAME = $RabbitMqUsername
$env:RABBITMQ_PASSWORD = $RabbitMqPassword
$env:RABBITMQ_VIRTUAL_HOST = $RabbitMqVirtualHost
$env:FOOD_RABBIT_HEALTH_ENABLED = "true"
$env:FOOD_MQ_ENABLED = "true"
$env:SEATA_ENABLED = "true"
$env:SEATA_SERVER_ADDR = $SeataServerAddr
$env:SENTINEL_DASHBOARD_ADDR = $SentinelDashboardAddr
$env:FOOD_OBSERVABILITY_LOG_NORMAL_REQUEST = "true"
Write-Host "SPRING_PROFILES_ACTIVE=$env:SPRING_PROFILES_ACTIVE"
Write-Host "NACOS_DISCOVERY_ENABLED=$env:NACOS_DISCOVERY_ENABLED"
Write-Host "NACOS_CONFIG_ENABLED=$env:NACOS_CONFIG_ENABLED"
Write-Host "FOOD_MQ_ENABLED=$env:FOOD_MQ_ENABLED"
Write-Host "SEATA_ENABLED=$env:SEATA_ENABLED"

& (Join-Path $PSScriptRoot "check-microservice-infra.ps1") `
    -RabbitMqPort ([int]$RabbitMqPort) `
    -RabbitMqManagementPort ([int]$RabbitMqManagementPort) `
    -SeataPort (($SeataServerAddr -split ":")[-1]) `
    -SentinelDashboardPort (($SentinelDashboardAddr -split ":")[-1])

$CoreServices = @(
    @{
        Name = "food-user-service"
        Port = 8101
        SentinelTransportPort = 8733
        MavenModule = "food-user-service/food-user-app"
        Jar = "food-user-service/food-user-app/target/food-user-app-1.0-SNAPSHOT.jar"
        OutLog = "logs/user-service-8101.out.log"
        ErrLog = "logs/user-service-8101.err.log"
        Health = "http://localhost:8101/health"
    },
    @{
        Name = "food-business-service"
        Port = 8201
        SentinelTransportPort = 8731
        MavenModule = "food-business-service/food-business-app"
        Jar = "food-business-service/food-business-app/target/food-business-app-1.0-SNAPSHOT.jar"
        OutLog = "logs/business-service-8201.out.log"
        ErrLog = "logs/business-service-8201.err.log"
        Health = "http://localhost:8201/health"
    },
    @{
        Name = "food-trade-service"
        Port = 8301
        SentinelTransportPort = 8732
        MavenModule = "food-trade-service/food-trade-app"
        Jar = "food-trade-service/food-trade-app/target/food-trade-app-1.0-SNAPSHOT.jar"
        OutLog = "logs/trade-service-8301.out.log"
        ErrLog = "logs/trade-service-8301.err.log"
        Health = "http://localhost:8301/health"
    }
)

$GatewayService = @{
    Name = "food-gateway-service"
    Port = 8080
    SentinelTransportPort = 8730
    MavenModule = "food-gateway-service"
    Jar = "food-gateway-service/target/food-gateway-service-1.0-SNAPSHOT.jar"
    OutLog = "logs/gateway-service-8080.out.log"
    ErrLog = "logs/gateway-service-8080.err.log"
    Health = "http://localhost:8080/health"
}

$Services = @($CoreServices)
if ($IncludeGateway) {
    $Services += $GatewayService
}

function Get-PortProcessId {
    param([int]$Port)
    $connection = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($null -eq $connection) {
        return $null
    }
    return $connection.OwningProcess
}

function Stop-ServicePort {
    param([hashtable]$Service)
    $processId = Get-PortProcessId -Port $Service.Port
    if ($null -ne $processId) {
        Write-Host "Stopping $($Service.Name) on port $($Service.Port), pid=$processId"
        Stop-Process -Id $processId -Force
        Start-Sleep -Seconds 2
    }
}

function Wait-Health {
    param([hashtable]$Service)
    for ($i = 1; $i -le 30; $i++) {
        try {
            $response = Invoke-RestMethod -Method Get -Uri $Service.Health -TimeoutSec 2
            if ($response.code -eq "0000") {
                Start-Sleep -Seconds 3
                $processId = Get-PortProcessId -Port $Service.Port
                if ($null -eq $processId) {
                    throw "$($Service.Name) health was transient but process exited."
                }
                $confirm = Invoke-RestMethod -Method Get -Uri $Service.Health -TimeoutSec 2
                if ($confirm.code -eq "0000") {
                    Write-Host "$($Service.Name) healthy on port $($Service.Port)"
                    return
                }
            }
        } catch {
            Start-Sleep -Seconds 2
        }
    }
    throw "$($Service.Name) did not become healthy on port $($Service.Port)"
}

Set-Location $Root

if ($Restart -or $Rebuild) {
    foreach ($service in $Services) {
        Stop-ServicePort -Service $service
    }
}

if ($Rebuild) {
    $mavenModules = ($Services | ForEach-Object { $_.MavenModule }) -join ","
    mvn -pl $mavenModules -am package -DskipTests
}

foreach ($service in $Services) {
    $processId = Get-PortProcessId -Port $service.Port
    if ($null -ne $processId) {
        Write-Host "$($service.Name) already running on port $($service.Port), pid=$processId"
        Wait-Health -Service $service
        continue
    }

    $jarPath = Join-Path $Root $service.Jar
    if (-not (Test-Path $jarPath)) {
        Write-Host "Jar missing for $($service.Name), packaging $($service.MavenModule)"
        mvn -pl $service.MavenModule -am package -DskipTests
    }

    Write-Host "Starting $($service.Name) on port $($service.Port)"
    $javaExe = Join-Path $env:JAVA_HOME "bin\java.exe"
    $javaArgs = @(
        "-Dproject.name=$($service.Name)",
        "-Dcsp.sentinel.dashboard.server=$env:SENTINEL_DASHBOARD_ADDR",
        "-Dcsp.sentinel.api.port=$($service.SentinelTransportPort)",
        "-jar",
        $jarPath
    )
    Start-Process -FilePath $javaExe `
        -ArgumentList $javaArgs `
        -WorkingDirectory $Root `
        -RedirectStandardOutput (Join-Path $Root $service.OutLog) `
        -RedirectStandardError (Join-Path $Root $service.ErrLog) `
        -WindowStyle Hidden

    Wait-Health -Service $service
}

Write-Host "All local services are ready."

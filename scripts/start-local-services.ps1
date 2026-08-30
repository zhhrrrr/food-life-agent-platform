param(
    [switch]$Rebuild,
    [switch]$Restart
)

$ErrorActionPreference = "Stop"

$Root = Split-Path -Parent $PSScriptRoot
$Logs = Join-Path $Root "logs"
New-Item -ItemType Directory -Force -Path $Logs | Out-Null

$Services = @(
    @{
        Name = "food-user-service"
        Port = 8101
        MavenModule = "food-user-service/food-user-app"
        Jar = "food-user-service/food-user-app/target/food-user-app-1.0-SNAPSHOT.jar"
        OutLog = "logs/user-service-8101.out.log"
        ErrLog = "logs/user-service-8101.err.log"
        Health = "http://localhost:8101/health"
    },
    @{
        Name = "food-business-service"
        Port = 8201
        MavenModule = "food-business-service/food-business-app"
        Jar = "food-business-service/food-business-app/target/food-business-app-1.0-SNAPSHOT.jar"
        OutLog = "logs/business-service-8201.out.log"
        ErrLog = "logs/business-service-8201.err.log"
        Health = "http://localhost:8201/health"
    },
    @{
        Name = "food-trade-service"
        Port = 8301
        MavenModule = "food-trade-service/food-trade-app"
        Jar = "food-trade-service/food-trade-app/target/food-trade-app-1.0-SNAPSHOT.jar"
        OutLog = "logs/trade-service-8301.out.log"
        ErrLog = "logs/trade-service-8301.err.log"
        Health = "http://localhost:8301/health"
    }
)

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
                Write-Host "$($Service.Name) healthy on port $($Service.Port)"
                return
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
    mvn -pl "food-user-service/food-user-app,food-business-service/food-business-app,food-trade-service/food-trade-app" -am package -DskipTests
}

foreach ($service in $Services) {
    $processId = Get-PortProcessId -Port $service.Port
    if ($null -ne $processId) {
        Write-Host "$($service.Name) already running on port $($service.Port), pid=$processId"
        continue
    }

    $jarPath = Join-Path $Root $service.Jar
    if (-not (Test-Path $jarPath)) {
        Write-Host "Jar missing for $($service.Name), packaging $($service.MavenModule)"
        mvn -pl $service.MavenModule -am package -DskipTests
    }

    Write-Host "Starting $($service.Name) on port $($service.Port)"
    Start-Process -FilePath "java" `
        -ArgumentList "-jar", $jarPath `
        -WorkingDirectory $Root `
        -RedirectStandardOutput (Join-Path $Root $service.OutLog) `
        -RedirectStandardError (Join-Path $Root $service.ErrLog) `
        -WindowStyle Hidden
}

foreach ($service in $Services) {
    Wait-Health -Service $service
}

Write-Host "All local services are ready."

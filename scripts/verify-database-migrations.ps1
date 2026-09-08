param()

$ErrorActionPreference = "Stop"

$Root = Split-Path -Parent $PSScriptRoot

$services = @(
    @{
        Name = "food-user-service"
        AppPom = "food-user-service/food-user-app/pom.xml"
        Migration = "food-user-service/food-user-app/src/main/resources/db/migration/V1__init_user_schema.sql"
        LocalConfig = "food-user-service/food-user-app/src/main/resources/application-local.yml"
        NacosConfig = "deploy/nacos/configs/food-user-service.yaml"
        Database = "food_user_db"
    },
    @{
        Name = "food-business-service"
        AppPom = "food-business-service/food-business-app/pom.xml"
        Migration = "food-business-service/food-business-app/src/main/resources/db/migration/V1__init_business_schema.sql"
        LocalConfig = "food-business-service/food-business-app/src/main/resources/application-local.yml"
        NacosConfig = "deploy/nacos/configs/food-business-service.yaml"
        Database = "food_business_db"
    },
    @{
        Name = "food-trade-service"
        AppPom = "food-trade-service/food-trade-app/pom.xml"
        Migration = "food-trade-service/food-trade-app/src/main/resources/db/migration/V1__init_trade_schema.sql"
        LocalConfig = "food-trade-service/food-trade-app/src/main/resources/application-local.yml"
        NacosConfig = "deploy/nacos/configs/food-trade-service.yaml"
        Database = "food_trade_db"
    }
)

function Read-ProjectFile {
    param([string]$Path)

    $fullPath = Join-Path $Root $Path
    if (-not (Test-Path $fullPath)) {
        throw "Missing required file: $Path"
    }

    return Get-Content -Raw -Path $fullPath
}

foreach ($service in $services) {
    $pom = Read-ProjectFile $service.AppPom
    if ($pom -notmatch "<artifactId>flyway-core</artifactId>") {
        throw "$($service.Name) app module must depend on flyway-core."
    }

    $migration = Read-ProjectFile $service.Migration
    if ($migration -notmatch "CREATE TABLE IF NOT EXISTS") {
        throw "$($service.Name) migration must create at least one table."
    }
    if ($migration -match "CREATE DATABASE" -or $migration -match "USE\s+food_.*_db;") {
        throw "$($service.Name) Flyway migration must not contain CREATE DATABASE or USE statements."
    }

    foreach ($configPath in @($service.LocalConfig, $service.NacosConfig)) {
        $config = Read-ProjectFile $configPath
        if ($config -notmatch "flyway:") {
            throw "$($service.Name) config $configPath must enable Flyway configuration."
        }
        if ($config -notmatch "enabled:\s+\$\{FLYWAY_ENABLED:true\}") {
            throw "$($service.Name) config $configPath must default Flyway to enabled."
        }
        if ($config -notmatch "baseline-on-migrate:\s+true") {
            throw "$($service.Name) config $configPath must set baseline-on-migrate=true."
        }
        if ($config -notmatch "validate-on-migrate:\s+true") {
            throw "$($service.Name) config $configPath must set validate-on-migrate=true."
        }
        if ($config -notmatch "createDatabaseIfNotExist=true") {
            throw "$($service.Name) config $configPath must allow local database auto creation."
        }
    }
}

Write-Host "Database migration governance verified."

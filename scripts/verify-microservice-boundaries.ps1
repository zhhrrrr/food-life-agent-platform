param(
    [string]$Root = (Resolve-Path "$PSScriptRoot\..").Path
)

$ErrorActionPreference = "Stop"

function Assert-FileExists {
    param(
        [string]$Path,
        [string]$Message
    )

    if (-not (Test-Path $Path)) {
        throw $Message
    }
}

function Assert-FileMissing {
    param(
        [string]$Path,
        [string]$Message
    )

    if (Test-Path $Path) {
        throw $Message
    }
}

function Assert-NotContains {
    param(
        [string]$Path,
        [string[]]$Patterns,
        [string]$Message
    )

    $content = Get-Content -Raw -Path $Path
    foreach ($pattern in $Patterns) {
        if ($content.Contains($pattern)) {
            throw "$Message pattern=$pattern file=$Path"
        }
    }
}

function Assert-Contains {
    param(
        [string]$Path,
        [string[]]$Patterns,
        [string]$Message
    )

    $content = Get-Content -Raw -Path $Path
    foreach ($pattern in $Patterns) {
        if (-not $content.Contains($pattern)) {
            throw "$Message pattern=$pattern file=$Path"
        }
    }
}

$publicPackageController = Join-Path $Root "food-business-service\food-business-trigger\src\main\java\com\foodlife\business\trigger\http\PackageController.java"
$internalPackageController = Join-Path $Root "food-business-service\food-business-trigger\src\main\java\com\foodlife\business\trigger\http\PackageInternalController.java"
$gatewayAuthProperties = Join-Path $Root "food-gateway-service\src\main\java\com\foodlife\gateway\properties\GatewayAuthProperties.java"
$gatewayLocalConfig = Join-Path $Root "food-gateway-service\src\main\resources\application-local.yml"
$gatewayNacosConfig = Join-Path $Root "deploy\nacos\configs\food-gateway-service.yaml"
$gatewayOldLogFilter = Join-Path $Root "food-gateway-service\src\main\java\com\foodlife\gateway\filter\GatewayRequestLogFilter.java"
$observabilityStarter = Join-Path $Root "food-observability-starter\src\main\resources\META-INF\spring\org.springframework.boot.autoconfigure.AutoConfiguration.imports"

Assert-FileExists $publicPackageController "PackageController missing"
Assert-FileExists $internalPackageController "PackageInternalController missing"
Assert-FileExists $gatewayAuthProperties "GatewayAuthProperties missing"
Assert-FileExists $gatewayLocalConfig "gateway application-local.yml missing"
Assert-FileExists $gatewayNacosConfig "gateway Nacos config missing"
Assert-FileExists $observabilityStarter "observability auto configuration imports missing"

Assert-FileMissing $gatewayOldLogFilter "GatewayRequestLogFilter should be removed; use food-observability-starter instead"

$stockWritePatterns = @(
    'stock/occupy',
    'stock/release',
    'sold/confirm',
    'sold/rollback',
    'stock-change-records'
)

Assert-NotContains `
    -Path $publicPackageController `
    -Patterns $stockWritePatterns `
    -Message "public PackageController must not expose stock mutation or stock audit APIs"

Assert-Contains `
    -Path $internalPackageController `
    -Patterns $stockWritePatterns `
    -Message "internal PackageInternalController must expose stock mutation and stock audit APIs"

$precisePackageAllowList = @(
    '/api/package/*',
    '/api/package/of/shop',
    '/api/package/trade-snapshot/*'
)

Assert-Contains `
    -Path $gatewayAuthProperties `
    -Patterns $precisePackageAllowList `
    -Message "Gateway default auth allow-list must keep package public read APIs precise"

Assert-Contains `
    -Path $gatewayLocalConfig `
    -Patterns $precisePackageAllowList `
    -Message "Gateway local auth allow-list must keep package public read APIs precise"

Assert-Contains `
    -Path $gatewayNacosConfig `
    -Patterns $precisePackageAllowList `
    -Message "Gateway Nacos auth allow-list must keep package public read APIs precise"

Assert-Contains `
    -Path $observabilityStarter `
    -Patterns @(
        'com.foodlife.observability.config.ObservabilityAutoConfiguration',
        'com.foodlife.observability.config.ServletObservabilityAutoConfiguration',
        'com.foodlife.observability.config.ReactiveObservabilityAutoConfiguration',
        'com.foodlife.observability.config.FeignObservabilityAutoConfiguration'
    ) `
    -Message "observability starter must keep servlet, reactive and feign auto configuration"

Write-Host "OK microservice boundaries verified."

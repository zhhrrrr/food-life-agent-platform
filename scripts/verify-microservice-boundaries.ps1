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
$commonNacosConfig = Join-Path $Root "deploy\nacos\configs\food-common.yaml"
$gatewayOldLogFilter = Join-Path $Root "food-gateway-service\src\main\java\com\foodlife\gateway\filter\GatewayRequestLogFilter.java"
$observabilityStarter = Join-Path $Root "food-observability-starter\src\main\resources\META-INF\spring\org.springframework.boot.autoconfigure.AutoConfiguration.imports"
$observabilityProperties = Join-Path $Root "food-observability-starter\src\main\java\com\foodlife\observability\properties\ObservabilityProperties.java"
$businessMqProperties = Join-Path $Root "food-business-service\food-business-infrastructure\src\main\java\com\foodlife\business\infrastructure\mq\BusinessRabbitMqProperties.java"
$tradeMqProperties = Join-Path $Root "food-trade-service\food-trade-infrastructure\src\main\java\com\foodlife\trade\infrastructure\mq\TradeRabbitMqProperties.java"
$startLocalServicesScript = Join-Path $Root "scripts\start-local-services.ps1"
$serviceApplicationConfigs = @(
    (Join-Path $Root "food-user-service\food-user-app\src\main\resources\application.yml"),
    (Join-Path $Root "food-business-service\food-business-app\src\main\resources\application.yml"),
    (Join-Path $Root "food-trade-service\food-trade-app\src\main\resources\application.yml"),
    (Join-Path $Root "food-gateway-service\src\main\resources\application.yml")
)

Assert-FileExists $publicPackageController "PackageController missing"
Assert-FileExists $internalPackageController "PackageInternalController missing"
Assert-FileExists $gatewayAuthProperties "GatewayAuthProperties missing"
Assert-FileExists $gatewayLocalConfig "gateway application-local.yml missing"
Assert-FileExists $gatewayNacosConfig "gateway Nacos config missing"
Assert-FileExists $commonNacosConfig "food-common Nacos config missing"
Assert-FileExists $observabilityStarter "observability auto configuration imports missing"
Assert-FileExists $observabilityProperties "observability properties missing"
Assert-FileExists $businessMqProperties "business MQ properties missing"
Assert-FileExists $tradeMqProperties "trade MQ properties missing"
Assert-FileExists $startLocalServicesScript "start local services script missing"
foreach ($config in $serviceApplicationConfigs) {
    Assert-FileExists $config "service application.yml missing"
}

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

foreach ($config in $serviceApplicationConfigs) {
    Assert-Contains `
        -Path $config `
        -Patterns @(
            'active: ${SPRING_PROFILES_ACTIVE:nacos}',
            'enabled: true',
            'nacos:food-common.yaml'
        ) `
        -Message "service application.yml must default to real Nacos config"

    Assert-NotContains `
        -Path $config `
        -Patterns @(
            'optional:nacos:',
            'active: ${SPRING_PROFILES_ACTIVE:local}',
            'enabled: false'
        ) `
        -Message "service application.yml must not disable Nacos by default"
}

Assert-Contains `
    -Path $commonNacosConfig `
    -Patterns @(
        'enabled: ${FOOD_RABBIT_HEALTH_ENABLED:true}',
        'enabled: ${SEATA_ENABLED:true}',
        'enabled: ${FOOD_MQ_ENABLED:true}',
        'log-normal-request: ${FOOD_OBSERVABILITY_LOG_NORMAL_REQUEST:true}'
    ) `
    -Message "common Nacos config must enable microservice components by default"

Assert-NotContains `
    -Path $commonNacosConfig `
    -Patterns @(
        'enabled: ${FOOD_RABBIT_HEALTH_ENABLED:false}',
        'enabled: ${SEATA_ENABLED:false}',
        'enabled: ${FOOD_MQ_ENABLED:false}',
        'log-normal-request: ${FOOD_OBSERVABILITY_LOG_NORMAL_REQUEST:false}',
        'enabled: false'
    ) `
    -Message "common Nacos config must not disable microservice components"

foreach ($config in @($gatewayLocalConfig, $gatewayNacosConfig)) {
    Assert-Contains `
        -Path $config `
        -Patterns @(
            'rate-limit:',
            'enabled: true'
        ) `
        -Message "Gateway rate limit must be enabled"
}

Assert-Contains `
    -Path $observabilityProperties `
    -Patterns @(
        'private boolean enabled = true;',
        'private boolean logNormalRequest = true;'
    ) `
    -Message "observability defaults must be enabled"

Assert-Contains `
    -Path $businessMqProperties `
    -Patterns @('private Boolean enabled = true;') `
    -Message "business MQ defaults must be enabled"

Assert-Contains `
    -Path $tradeMqProperties `
    -Patterns @('private Boolean enabled = true;') `
    -Message "trade MQ defaults must be enabled"

Assert-Contains `
    -Path $startLocalServicesScript `
    -Patterns @(
        '$env:NACOS_DISCOVERY_ENABLED = "true"',
        '$env:NACOS_CONFIG_ENABLED = "true"',
        '$env:FOOD_RABBIT_HEALTH_ENABLED = "true"',
        '$env:FOOD_MQ_ENABLED = "true"',
        '$env:SEATA_ENABLED = "true"',
        '$env:FOOD_OBSERVABILITY_LOG_NORMAL_REQUEST = "true"'
    ) `
    -Message "local start script must enable real microservice components"

Write-Host "OK microservice boundaries verified."

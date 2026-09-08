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
$businessEventPublisher = Join-Path $Root "food-business-service\food-business-infrastructure\src\main\java\com\foodlife\business\infrastructure\mq\BusinessRabbitMqEventPublisher.java"
$businessLocalMessagePO = Join-Path $Root "food-business-service\food-business-infrastructure\src\main\java\com\foodlife\business\infrastructure\dao\po\BusinessLocalMessagePO.java"
$businessLocalMessageMapper = Join-Path $Root "food-business-service\food-business-infrastructure\src\main\java\com\foodlife\business\infrastructure\dao\IBusinessLocalMessageMapper.java"
$businessEventRetryJob = Join-Path $Root "food-business-service\food-business-trigger\src\main\java\com\foodlife\business\trigger\job\BusinessRabbitMqEventRetryJob.java"
$businessSchema = Join-Path $Root "docs\sql\food_business_db.sql"
$tradeMqProperties = Join-Path $Root "food-trade-service\food-trade-infrastructure\src\main\java\com\foodlife\trade\infrastructure\mq\TradeRabbitMqProperties.java"
$tradeEventPublisher = Join-Path $Root "food-trade-service\food-trade-infrastructure\src\main\java\com\foodlife\trade\infrastructure\mq\TradeRabbitMqEventPublisher.java"
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
Assert-FileExists $businessEventPublisher "business event publisher missing"
Assert-FileExists $businessLocalMessagePO "business local message PO missing"
Assert-FileExists $businessLocalMessageMapper "business local message mapper missing"
Assert-FileExists $businessEventRetryJob "business event retry job missing"
Assert-FileExists $businessSchema "business schema missing"
Assert-FileExists $tradeMqProperties "trade MQ properties missing"
Assert-FileExists $tradeEventPublisher "trade event publisher missing"
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
            'sentinel:',
            'trade-route-qps:',
            'user-header-qps:',
            'enabled: true'
        ) `
        -Message "Gateway traffic limit must be owned by Sentinel"
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
    -Patterns @(
        'private Boolean enabled = true;',
        'private Integer retryDelaySeconds = 30;',
        'private Integer retryLimit = 50;',
        'private Integer processingTimeoutSeconds = 120;'
    ) `
    -Message "business MQ defaults must be enabled"

Assert-Contains `
    -Path $businessEventPublisher `
    -Patterns @(
        'TransactionSynchronizationManager.registerSynchronization',
        'retryPendingEvents',
        'recoverProcessingMessages'
    ) `
    -Message "business event publisher must use reliable local message table"

Assert-NotContains `
    -Path $businessEventPublisher `
    -Patterns @(
        'mock publish',
        'applyReviewCreatedStats(key',
        'fallbackIfNeeded'
    ) `
    -Message "business event publisher must not use local mock fallback"

Assert-Contains `
    -Path $businessLocalMessagePO `
    -Patterns @(
        '@TableName("business_local_message")',
        'private String messageStatus;',
        'private LocalDateTime nextRetryTime;'
    ) `
    -Message "business local message PO must map reliable message table"

Assert-Contains `
    -Path $businessEventRetryJob `
    -Patterns @(
        'food.jobs.business-event-retry.enabled',
        'food.jobs.business-event-retry.fixed-delay-ms',
        'retryPendingEvents'
    ) `
    -Message "business event retry job must scan local messages"

Assert-Contains `
    -Path $businessSchema `
    -Patterns @(
        'CREATE TABLE IF NOT EXISTS business_local_message',
        'UNIQUE KEY uk_message_id (message_id)',
        'KEY idx_status_retry_time (message_status, next_retry_time)'
    ) `
    -Message "business schema must include local reliable message table"

Assert-Contains `
    -Path $tradeMqProperties `
    -Patterns @(
        'private Boolean enabled = true;',
        'private Integer processingTimeoutSeconds = 120;'
    ) `
    -Message "trade MQ defaults must be enabled"

Assert-Contains `
    -Path $tradeEventPublisher `
    -Patterns @(
        'TransactionSynchronizationManager.registerSynchronization',
        'recoverProcessingMessages',
        'trade RabbitMQ publisher disabled'
    ) `
    -Message "trade event publisher must use real RabbitMQ publish and stuck message recovery"

Assert-NotContains `
    -Path $tradeEventPublisher `
    -Patterns @(
        'mock publish',
        'markSuccess(message.getId());`r`n                return true;'
    ) `
    -Message "trade event publisher must not mark disabled RabbitMQ publish as success"

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

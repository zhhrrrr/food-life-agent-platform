param()

$ErrorActionPreference = "Stop"

$Root = Split-Path -Parent $PSScriptRoot

function Read-ProjectFile {
    param([string]$Path)

    $fullPath = Join-Path $Root $Path
    if (-not (Test-Path $fullPath)) {
        throw "Missing required file: $Path"
    }

    return Get-Content -Raw -Encoding UTF8 -Path $fullPath
}

$tradeApi = Read-ProjectFile "food-life-agent-web/src/api/trade.ts"
if ($tradeApi -notmatch "queryOperationAuditLogs") {
    throw "frontend trade api must expose queryOperationAuditLogs."
}
if ($tradeApi -notmatch "/trade-api/operations/audit-logs") {
    throw "frontend audit api must call gateway trade-api audit route."
}

$orderTypes = Read-ProjectFile "food-life-agent-web/src/types/order.ts"
foreach ($typeName in @("OperationAuditLog", "OperationAuditLogListResponse", "OperationAuditQuery")) {
    if ($orderTypes -notmatch $typeName) {
        throw "frontend order types must define $typeName."
    }
}

$router = Read-ProjectFile "food-life-agent-web/src/router/index.ts"
if ($router -notmatch "operations/audit") {
    throw "router must expose operations/audit route."
}
if ($router -notmatch "requiresOperator") {
    throw "operation audit route must require operator role."
}
if ($router -notmatch "auth.fetchMe") {
    throw "router guard must fetch current user before checking operator role."
}

$layout = Read-ProjectFile "food-life-agent-web/src/layouts/AppLayout.vue"
if ($layout -notmatch "canViewOperations") {
    throw "layout must hide operation nav for non-operator users."
}
if ($layout -notmatch "/operations/audit") {
    throw "layout must link to operation audit page."
}

$view = Read-ProjectFile "food-life-agent-web/src/views/OperationAuditView.vue"
foreach ($keyword in @("traceId", "operatorId", "operationType", "bizType", "bizId", "limit")) {
    if ($view -notmatch $keyword) {
        throw "operation audit view must support $keyword query/display."
    }
}

foreach ($configPath in @(
    "food-user-service/food-user-app/src/main/resources/application-local.yml",
    "deploy/nacos/configs/food-user-service.yaml"
)) {
    $config = Read-ProjectFile $configPath
    if ($config -notmatch "local-user-roles:") {
        throw "user-service config $configPath must expose local admin role to /api/user/me."
    }
    if ($config -notmatch "1:\s+ADMIN") {
        throw "user-service config $configPath must map local user 1 to ADMIN."
    }
}

Write-Host "Operation audit UI integration verified."

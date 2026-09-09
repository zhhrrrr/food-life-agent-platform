param()

$ErrorActionPreference = "Stop"

$Root = Split-Path -Parent $PSScriptRoot
Set-Location $Root

function Assert-FileExists {
    param([string]$Path)
    if (-not (Test-Path $Path)) {
        throw "required file missing: $Path"
    }
    Write-Host "OK file exists -> $Path"
}

function Assert-FileContains {
    param(
        [string]$Path,
        [string]$Pattern
    )
    $match = Select-String -Path $Path -Pattern $Pattern -SimpleMatch -Quiet
    if (-not $match) {
        throw "required pattern missing: $Pattern in $Path"
    }
    Write-Host "OK pattern -> $Path :: $Pattern"
}

$requiredFiles = @(
    "food-trade-service/food-trade-app/src/main/resources/db/migration/V2__add_refund_order_schema.sql",
    "food-user-service/food-user-app/src/main/resources/db/migration/V2__add_user_role_schema.sql",
    "food-trade-service/food-trade-domain/src/main/java/com/foodlife/trade/domain/order/payment/service/PaymentRefundService.java",
    "food-trade-service/food-trade-trigger/src/main/java/com/foodlife/trade/trigger/http/OperationPaymentController.java",
    "food-user-service/food-user-infrastructure/src/main/java/com/foodlife/user/infrastructure/repository/UserRoleRepository.java",
    "food-life-agent-web/src/utils/localPayment.ts",
    "scripts/smoke-p0-business-flow.ps1",
    "docs/p0-production-readiness-contract.md"
)

foreach ($file in $requiredFiles) {
    Assert-FileExists $file
}

Assert-FileContains "food-trade-service/food-trade-infrastructure/src/main/java/com/foodlife/trade/infrastructure/payment/LocalPaymentProvider.java" "verifySignature(command)"
Assert-FileContains "food-trade-service/food-trade-infrastructure/src/main/java/com/foodlife/trade/infrastructure/payment/LocalPaymentProvider.java" "public PaymentRefundResult refund"
Assert-FileContains "food-trade-service/food-trade-app/src/main/resources/application-local.yml" "callback-secret"
Assert-FileContains "deploy/nacos/configs/food-common.yaml" "callback-secret"
Assert-FileContains "food-life-agent-web/src/views/OrdersView.vue" "buildLocalPaymentSignature"
Assert-FileContains "scripts/smoke-rabbitmq-events.ps1" "signType = `"SHA256`""
Assert-FileContains "scripts/smoke-p0-business-flow.ps1" "operation payment reconcile"
Assert-FileContains "food-user-service/food-user-trigger/src/main/java/com/foodlife/user/trigger/http/UserAuthController.java" "userRoleRepository.findHighestRoleCodeByUserId"
Assert-FileContains "food-trade-service/food-trade-types/src/main/java/com/foodlife/trade/types/response/ErrorCode.java" "PAYMENT_CALLBACK_SIGNATURE_INVALID"
Assert-FileContains "food-trade-service/food-trade-trigger/src/main/java/com/foodlife/trade/trigger/exception/GlobalExceptionHandler.java" "ORDER_STATUS_INVALID"

Write-Host "P0 readiness static verification completed."

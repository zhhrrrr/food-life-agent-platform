param(
    [switch]$SkipMaven,
    [switch]$SkipFrontend,
    [switch]$SkipRuntimeSmoke
)

$ErrorActionPreference = "Stop"

$Root = Split-Path -Parent $PSScriptRoot
Set-Location $Root

& (Join-Path $PSScriptRoot "use-java17-plus.ps1")
& (Join-Path $PSScriptRoot "verify-microservice-boundaries.ps1")
& (Join-Path $PSScriptRoot "verify-ddd-boundaries.ps1")
& (Join-Path $PSScriptRoot "verify-database-migrations.ps1")

if (-not $SkipMaven) {
    mvn -B test
}

if (-not $SkipFrontend) {
    Push-Location (Join-Path $Root "food-life-agent-web")
    try {
        npm run build
    } finally {
        Pop-Location
    }
}

if (-not $SkipRuntimeSmoke) {
    $gateway = Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($null -eq $gateway) {
        Write-Host "Gateway is not running, skip runtime smoke test. Start services with scripts/start-local-services.ps1 -IncludeGateway -Restart."
    } else {
        & (Join-Path $PSScriptRoot "check-microservice-infra.ps1")
        & (Join-Path $PSScriptRoot "verify-nacos-configs.ps1")
        & (Join-Path $PSScriptRoot "smoke-test-local-microservices.ps1")
    }
}

Write-Host "Company readiness verification completed."

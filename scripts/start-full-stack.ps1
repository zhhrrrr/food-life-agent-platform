param(
    [switch]$Rebuild,
    [switch]$Restart,
    [switch]$SkipInstall,
    [switch]$SkipFrontend,
    [switch]$SkipObservability,
    [switch]$SkipSmoke
)

$ErrorActionPreference = "Stop"

Write-Host "Starting Food Life Agent full local stack."
Write-Host "Step 1/5: infrastructure"
& (Join-Path $PSScriptRoot "start-infra-all.ps1") -SkipInstall:$SkipInstall

Write-Host "Step 2/5: backend services"
& (Join-Path $PSScriptRoot "start-local-services.ps1") -IncludeGateway -Rebuild:$Rebuild -Restart:$Restart

if (-not $SkipFrontend) {
    Write-Host "Step 3/5: frontend"
    & (Join-Path $PSScriptRoot "start-frontend-web.ps1") -Restart:$Restart
} else {
    Write-Host "Step 3/5: frontend skipped"
}

if (-not $SkipObservability) {
    Write-Host "Step 4/5: observability"
    & (Join-Path $PSScriptRoot "start-observability-stack.ps1") -SkipInstall:$SkipInstall -Restart:$Restart
} else {
    Write-Host "Step 4/5: observability skipped"
}

if (-not $SkipSmoke) {
    Write-Host "Step 5/5: smoke checks"
    & (Join-Path $PSScriptRoot "smoke-observability.ps1")
} else {
    Write-Host "Step 5/5: smoke checks skipped"
}

& (Join-Path $PSScriptRoot "show-local-endpoints.ps1")

Write-Host "Food Life Agent full local stack is ready."

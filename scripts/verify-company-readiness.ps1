param(
    [switch]$SkipMaven,
    [switch]$SkipFrontend,
    [switch]$SkipPython,
    [switch]$SkipRuntimeSmoke
)

$ErrorActionPreference = "Stop"

$Root = Split-Path -Parent $PSScriptRoot
Set-Location $Root

function Invoke-CheckedNative {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Command,
        [Parameter(ValueFromRemainingArguments = $true)]
        [string[]]$Arguments
    )

    Get-Command $Command -ErrorAction Stop | Out-Null
    $previousErrorAction = $ErrorActionPreference
    try {
        # Windows PowerShell treats native stderr as ErrorRecord when output is redirected.
        # npm notices and compiler warnings must not override the actual process exit code.
        $ErrorActionPreference = "Continue"
        & $Command @Arguments 2>&1 | ForEach-Object { $_.ToString() }
        $nativeExitCode = $LASTEXITCODE
    } finally { $ErrorActionPreference = $previousErrorAction }
    if ($nativeExitCode -ne 0) {
        throw "Command failed with exit code ${nativeExitCode}: $Command $($Arguments -join ' ')"
    }
}

& (Join-Path $PSScriptRoot "use-java17-plus.ps1")
& (Join-Path $PSScriptRoot "verify-microservice-boundaries.ps1")
& (Join-Path $PSScriptRoot "verify-ddd-boundaries.ps1")
& (Join-Path $PSScriptRoot "verify-database-migrations.ps1")
& (Join-Path $PSScriptRoot "verify-operation-audit-ui.ps1")
& (Join-Path $PSScriptRoot "verify-p0-readiness.ps1")

if (-not $SkipMaven) {
    Invoke-CheckedNative mvn -B clean test
}

if (-not $SkipFrontend) {
    Push-Location (Join-Path $Root "food-life-agent-web")
    try {
        Invoke-CheckedNative npm run build
        Invoke-CheckedNative npm test
    } finally {
        Pop-Location
    }
}

if (-not $SkipPython) {
    Push-Location (Join-Path $Root "ai/food-agent-api")
    try {
        $agentPython = if (Test-Path ".venv/Scripts/python.exe") { ".venv/Scripts/python.exe" } else { "python" }
        Invoke-CheckedNative $agentPython -m pytest -q -rs
    } finally {
        Pop-Location
    }
}

if (-not $SkipRuntimeSmoke) {
    $gateway = Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($null -eq $gateway) {
        throw "Runtime smoke was requested but Gateway is not listening on port 8080. Start services with scripts/start-local-services.ps1 -IncludeGateway -Restart, or use -SkipRuntimeSmoke for offline verification."
    } else {
        & (Join-Path $PSScriptRoot "check-microservice-infra.ps1")
        & (Join-Path $PSScriptRoot "verify-nacos-configs.ps1")
        & (Join-Path $PSScriptRoot "smoke-test-local-microservices.ps1")
    }
}

Write-Host "Company readiness verification completed."

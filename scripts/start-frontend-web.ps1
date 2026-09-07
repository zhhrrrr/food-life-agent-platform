param(
    [switch]$Restart,
    [int]$Port = 5173,
    [string]$HostAddress = "0.0.0.0",
    [switch]$Install
)

$ErrorActionPreference = "Stop"

$Root = Split-Path -Parent $PSScriptRoot
$WebRoot = Join-Path $Root "food-life-agent-web"
$Logs = Join-Path $Root "logs"
New-Item -ItemType Directory -Force -Path $Logs | Out-Null

if (-not (Test-Path $WebRoot)) {
    throw "Frontend directory not found: $WebRoot"
}

function Get-PortProcessId {
    param([int]$Port)
    $connection = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($null -eq $connection) {
        return $null
    }
    return $connection.OwningProcess
}

if ($Restart) {
    $processId = Get-PortProcessId -Port $Port
    if ($null -ne $processId) {
        Write-Host "Stopping frontend on port $Port, pid=$processId"
        Stop-Process -Id $processId -Force
        Start-Sleep -Seconds 2
    }
}

if ($Install -or -not (Test-Path (Join-Path $WebRoot "node_modules"))) {
    Write-Host "Installing frontend dependencies"
    Push-Location $WebRoot
    try {
        npm install
    } finally {
        Pop-Location
    }
}

$existing = Get-PortProcessId -Port $Port
if ($null -ne $existing) {
    Write-Host "Frontend already running: http://127.0.0.1:$Port pid=$existing"
    exit 0
}

Write-Host "Starting frontend on port $Port"
Start-Process -FilePath "cmd.exe" `
    -ArgumentList "/c", "npm run dev -- --host $HostAddress --port $Port" `
    -WorkingDirectory $WebRoot `
    -RedirectStandardOutput (Join-Path $Logs "frontend-$Port.out.log") `
    -RedirectStandardError (Join-Path $Logs "frontend-$Port.err.log") `
    -WindowStyle Hidden

for ($i = 1; $i -le 45; $i++) {
    $processId = Get-PortProcessId -Port $Port
    if ($null -ne $processId) {
        try {
            $response = Invoke-WebRequest -Uri "http://127.0.0.1:$Port" -UseBasicParsing -TimeoutSec 3
            if ($response.StatusCode -eq 200) {
                Write-Host "Frontend ready: http://127.0.0.1:$Port pid=$processId"
                exit 0
            }
        } catch {
            Start-Sleep -Seconds 1
        }
    } else {
        Start-Sleep -Seconds 1
    }
}

throw "Frontend did not become ready. Check logs\frontend-$Port.err.log"

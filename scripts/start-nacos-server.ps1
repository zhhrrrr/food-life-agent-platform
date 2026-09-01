param(
    [string]$NacosHome = "tools/nacos-server/nacos",
    [int]$Port = 8848,
    [int]$WaitSeconds = 90
)

$ErrorActionPreference = "Stop"

$Root = Split-Path -Parent $PSScriptRoot
$NacosHomePath = Join-Path $Root $NacosHome
$BinPath = Join-Path $NacosHomePath "bin"
$Startup = Join-Path $BinPath "startup.cmd"
$Logs = Join-Path $Root "logs"

New-Item -ItemType Directory -Force -Path $Logs | Out-Null

function Test-PortListening {
    param([int]$ListenPort)
    $connection = Get-NetTCPConnection -LocalPort $ListenPort -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
    return $null -ne $connection
}

function Test-NacosReady {
    param([int]$ServerPort)
    try {
        $response = Invoke-WebRequest -Method Get -Uri "http://127.0.0.1:$ServerPort/nacos" -UseBasicParsing -TimeoutSec 3
        return $response.StatusCode -ge 200 -and $response.StatusCode -lt 500
    } catch {
        return $false
    }
}

if (-not (Test-Path $Startup)) {
    throw "Nacos is not installed. Run .\scripts\install-nacos-server.ps1 first."
}

if (Test-PortListening -ListenPort $Port) {
    Write-Host "Nacos port $Port is already listening."
    & (Join-Path $PSScriptRoot "check-nacos.ps1") -Port $Port
    exit 0
}

Write-Host "Starting Nacos standalone from $NacosHomePath"
Start-Process -FilePath "cmd.exe" `
    -ArgumentList "/c", "startup.cmd -m standalone" `
    -WorkingDirectory $BinPath `
    -RedirectStandardOutput (Join-Path $Logs "nacos-standalone.out.log") `
    -RedirectStandardError (Join-Path $Logs "nacos-standalone.err.log") `
    -WindowStyle Hidden

for ($i = 1; $i -le $WaitSeconds; $i++) {
    if (Test-NacosReady -ServerPort $Port) {
        Write-Host "Nacos is ready: http://127.0.0.1:$Port/nacos"
        exit 0
    }
    Start-Sleep -Seconds 1
}

throw "Nacos did not become ready in $WaitSeconds seconds. Check logs/nacos-standalone.out.log and logs/nacos-standalone.err.log."

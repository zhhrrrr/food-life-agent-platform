param(
    [string]$SeataHome = "tools/seata-server/seata",
    [int]$Port = 8091,
    [int]$WaitSeconds = 60
)

$ErrorActionPreference = "Stop"

$Root = Split-Path -Parent $PSScriptRoot
$SeataHomePath = Join-Path $Root $SeataHome
$SeataBin = Join-Path $SeataHomePath "bin"
$Startup = Join-Path $SeataBin "seata-server.bat"
$Logs = Join-Path $Root "logs"
New-Item -ItemType Directory -Force -Path $Logs | Out-Null

& (Join-Path $PSScriptRoot "use-java17-plus.ps1")

function Test-PortListening {
    param([int]$ListenPort)
    $connection = Get-NetTCPConnection -LocalPort $ListenPort -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
    return $null -ne $connection
}

if (-not (Test-Path $Startup)) {
    throw "Seata startup script not found: $Startup"
}

if (Test-PortListening -ListenPort $Port) {
    Write-Host "Seata Server port $Port is already listening."
    exit 0
}

Write-Host "Starting Seata Server on port $Port"
Start-Process -FilePath "cmd.exe" `
    -ArgumentList "/c", "seata-server.bat -p $Port -m file" `
    -WorkingDirectory $SeataBin `
    -RedirectStandardOutput (Join-Path $Logs "seata-server-$Port.out.log") `
    -RedirectStandardError (Join-Path $Logs "seata-server-$Port.err.log") `
    -WindowStyle Hidden

for ($i = 1; $i -le $WaitSeconds; $i++) {
    if (Test-PortListening -ListenPort $Port) {
        Write-Host "Seata Server is ready on port $Port"
        exit 0
    }
    Start-Sleep -Seconds 1
}

throw "Seata Server did not become ready in $WaitSeconds seconds. Check logs/seata-server-$Port.out.log and logs/seata-server-$Port.err.log."

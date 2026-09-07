param(
    [string]$JarPath = "tools/sentinel-dashboard/sentinel-dashboard-1.8.6.jar",
    [int]$Port = 8858,
    [int]$CommandPort = 8799,
    [switch]$Restart,
    [int]$WaitSeconds = 60
)

$ErrorActionPreference = "Stop"

$Root = Split-Path -Parent $PSScriptRoot
$Jar = Join-Path $Root $JarPath
$Logs = Join-Path $Root "logs"
New-Item -ItemType Directory -Force -Path $Logs | Out-Null

& (Join-Path $PSScriptRoot "use-java17-plus.ps1")

function Test-PortListening {
    param([int]$ListenPort)
    $connection = Get-NetTCPConnection -LocalPort $ListenPort -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
    return $null -ne $connection
}

if (-not (Test-Path $Jar)) {
    throw "Sentinel Dashboard jar not found: $Jar"
}

if ($Restart) {
    $existing = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($null -ne $existing) {
        Write-Host "Stopping Sentinel Dashboard on port $Port, pid=$($existing.OwningProcess)"
        Stop-Process -Id $existing.OwningProcess -Force
        Start-Sleep -Seconds 2
    }
}

if (Test-PortListening -ListenPort $Port) {
    Write-Host "Sentinel Dashboard port $Port is already listening."
    exit 0
}

$javaExe = Join-Path $env:JAVA_HOME "bin\java.exe"
Write-Host "Starting Sentinel Dashboard on port $Port"
Start-Process -FilePath $javaExe `
    -ArgumentList "-Dserver.port=$Port", "-Dcsp.sentinel.api.port=$CommandPort", "-Dcsp.sentinel.dashboard.server=127.0.0.1:$Port", "-Dproject.name=sentinel-dashboard", "-jar", $Jar `
    -WorkingDirectory $Root `
    -RedirectStandardOutput (Join-Path $Logs "sentinel-dashboard-$Port.out.log") `
    -RedirectStandardError (Join-Path $Logs "sentinel-dashboard-$Port.err.log") `
    -WindowStyle Hidden

for ($i = 1; $i -le $WaitSeconds; $i++) {
    if (Test-PortListening -ListenPort $Port) {
        Write-Host "Sentinel Dashboard is ready: http://127.0.0.1:$Port"
        exit 0
    }
    Start-Sleep -Seconds 1
}

throw "Sentinel Dashboard did not become ready in $WaitSeconds seconds. Check logs/sentinel-dashboard-$Port.out.log and logs/sentinel-dashboard-$Port.err.log."

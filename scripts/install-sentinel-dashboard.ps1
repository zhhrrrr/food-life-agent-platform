param(
    [string]$SentinelVersion = "1.8.6"
)

$ErrorActionPreference = "Stop"

$Root = Split-Path -Parent $PSScriptRoot
$TargetDir = Join-Path $Root "tools\sentinel-dashboard"
$Jar = Join-Path $TargetDir "sentinel-dashboard-$SentinelVersion.jar"
$Url = "https://github.com/alibaba/Sentinel/releases/download/$SentinelVersion/sentinel-dashboard-$SentinelVersion.jar"

New-Item -ItemType Directory -Force -Path $TargetDir | Out-Null

if (-not (Test-Path $Jar)) {
    Write-Host "Downloading Sentinel Dashboard $SentinelVersion"
    Invoke-WebRequest -Uri $Url -OutFile $Jar -UseBasicParsing
}

Write-Host "Sentinel Dashboard jar: $Jar"

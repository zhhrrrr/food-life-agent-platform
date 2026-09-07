param(
    [string]$SeataVersion = "1.7.1"
)

$ErrorActionPreference = "Stop"

$Root = Split-Path -Parent $PSScriptRoot
$Cache = Join-Path $Root ".cache\seata"
$TargetDir = Join-Path $Root "tools\seata-server"
$Zip = Join-Path $Cache "seata-server-$SeataVersion.zip"
$SeataHome = Join-Path $TargetDir "seata"
$Url = "https://github.com/seata/seata/releases/download/v$SeataVersion/seata-server-$SeataVersion.zip"

New-Item -ItemType Directory -Force -Path $Cache | Out-Null
New-Item -ItemType Directory -Force -Path $TargetDir | Out-Null

if (-not (Test-Path $Zip)) {
    Write-Host "Downloading Seata Server $SeataVersion"
    Invoke-WebRequest -Uri $Url -OutFile $Zip -UseBasicParsing
}

if (-not (Test-Path (Join-Path $SeataHome "bin\seata-server.bat"))) {
    Write-Host "Extracting Seata Server to tools\seata-server"
    Expand-Archive -Path $Zip -DestinationPath $TargetDir -Force
}

Write-Host "Seata home: $SeataHome"

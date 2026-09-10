param(
  [string]$DownloadUrl = "https://dl.k6.io/msi/k6-latest-amd64.msi"
)

$ErrorActionPreference = "Stop"

$root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$toolsDir = Join-Path $root "tools\k6"
$workDir = Join-Path $env:TEMP ("food-life-agent-k6-" + [Guid]::NewGuid().ToString("N"))
$msi = Join-Path $workDir "k6-latest-amd64.msi"
$installDir = Join-Path $toolsDir "msi-install"

New-Item -ItemType Directory -Force -Path $workDir | Out-Null
New-Item -ItemType Directory -Force -Path $toolsDir | Out-Null
New-Item -ItemType Directory -Force -Path $installDir | Out-Null

Write-Host "Downloading k6 from $DownloadUrl"
curl.exe -L --fail --retry 3 --retry-delay 2 --connect-timeout 30 --max-time 180 -o $msi $DownloadUrl

Write-Host "Installing k6 to $installDir"
& msiexec.exe /i $msi /qn ALLUSERS=2 MSIINSTALLPERUSER=1 INSTALLDIR=$installDir
if ($LASTEXITCODE -ne 0) {
  throw "msiexec install failed with code $LASTEXITCODE"
}

$exe = Get-ChildItem -Path $installDir -Recurse -Filter k6.exe -ErrorAction SilentlyContinue | Select-Object -First 1
if (-not $exe) {
  $exe = Get-ChildItem -Path $env:LOCALAPPDATA -Recurse -Filter k6.exe -ErrorAction SilentlyContinue | Select-Object -First 1
}
if (-not $exe) {
  throw "k6.exe not found after install"
}

Copy-Item -LiteralPath $exe.FullName -Destination (Join-Path $toolsDir "k6.exe") -Force
& (Join-Path $toolsDir "k6.exe") version

param(
    [string]$Version = "2.2.3",
    [string]$InstallRoot = "tools/nacos-server",
    [string]$DownloadUrl = "",
    [string]$LocalZip = "",
    [switch]$Force
)

$ErrorActionPreference = "Stop"

$Root = Split-Path -Parent $PSScriptRoot
$InstallRootPath = Join-Path $Root $InstallRoot
$NacosHome = Join-Path $InstallRootPath "nacos"
$CacheDir = Join-Path $Root ".cache/nacos"
$ZipPath = Join-Path $CacheDir "nacos-server-$Version.zip"
if ([string]::IsNullOrWhiteSpace($DownloadUrl)) {
    $DownloadUrl = "https://github.com/alibaba/nacos/releases/download/$Version/nacos-server-$Version.zip"
}

function Test-ZipArchive {
    param([string]$Path)
    try {
        Add-Type -AssemblyName System.IO.Compression.FileSystem
        $archive = [System.IO.Compression.ZipFile]::OpenRead($Path)
        $archive.Dispose()
        return $true
    } catch {
        return $false
    }
}

if ((Test-Path $NacosHome) -and -not $Force) {
    Write-Host "Nacos already installed: $NacosHome"
    Write-Host "Use -Force to reinstall."
    exit 0
}

if ((Test-Path $InstallRootPath) -and $Force) {
    Remove-Item -LiteralPath $InstallRootPath -Recurse -Force
}

New-Item -ItemType Directory -Force -Path $InstallRootPath | Out-Null
New-Item -ItemType Directory -Force -Path $CacheDir | Out-Null

if (-not [string]::IsNullOrWhiteSpace($LocalZip)) {
    $LocalZipPath = Resolve-Path -LiteralPath $LocalZip
    Write-Host "Using local Nacos zip: $LocalZipPath"
    Copy-Item -LiteralPath $LocalZipPath -Destination $ZipPath -Force
} elseif ((-not (Test-Path $ZipPath)) -or $Force -or (-not (Test-ZipArchive -Path $ZipPath))) {
    if ((Test-Path $ZipPath) -and (-not (Test-ZipArchive -Path $ZipPath))) {
        Write-Host "Existing Nacos zip is incomplete, redownloading: $ZipPath"
        Remove-Item -LiteralPath $ZipPath -Force
    }
    Write-Host "Downloading Nacos $Version from $DownloadUrl"
    $curl = Get-Command curl.exe -ErrorAction SilentlyContinue
    if ($null -ne $curl) {
        & curl.exe --ssl-no-revoke -L --retry 3 --retry-delay 3 -o $ZipPath $DownloadUrl
        if ($LASTEXITCODE -ne 0) {
            throw "curl download failed, exitCode=$LASTEXITCODE"
        }
    } else {
        Invoke-WebRequest -Uri $DownloadUrl -OutFile $ZipPath
    }
}

if (-not (Test-ZipArchive -Path $ZipPath)) {
    throw "Downloaded Nacos zip is invalid: $ZipPath"
}

Write-Host "Extracting $ZipPath to $InstallRootPath"
Expand-Archive -LiteralPath $ZipPath -DestinationPath $InstallRootPath -Force

$Startup = Join-Path $NacosHome "bin/startup.cmd"
if (-not (Test-Path $Startup)) {
    throw "Nacos startup.cmd not found after extraction: $Startup"
}

Write-Host "Nacos installed successfully: $NacosHome"
Write-Host "Start with: .\scripts\start-nacos-server.ps1"

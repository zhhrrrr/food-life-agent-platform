param(
    [string]$GrafanaVersion = "11.1.4",
    [string]$PrometheusVersion = "2.54.1",
    [switch]$Force
)

$ErrorActionPreference = "Stop"

$Root = Split-Path -Parent $PSScriptRoot
$Tools = Join-Path $Root "tools"
$Cache = Join-Path $Root ".cache"
New-Item -ItemType Directory -Force -Path $Tools, $Cache | Out-Null

$GrafanaZip = Join-Path $Cache "grafana-$GrafanaVersion.windows-amd64.zip"
$PrometheusZip = Join-Path $Cache "prometheus-$PrometheusVersion.windows-amd64.zip"
$GrafanaUrl = "https://dl.grafana.com/oss/release/grafana-$GrafanaVersion.windows-amd64.zip"
$PrometheusUrl = "https://github.com/prometheus/prometheus/releases/download/v$PrometheusVersion/prometheus-$PrometheusVersion.windows-amd64.zip"

function Save-File {
    param(
        [string]$Url,
        [string]$Destination
    )

    if ((Test-Path $Destination) -and -not $Force) {
        Write-Host "Archive exists: $Destination"
        return
    }

    if (Test-Path $Destination) {
        Remove-Item -LiteralPath $Destination -Force
    }

    Write-Host "Downloading $Url"
    try {
        curl.exe -L --fail --retry 3 --connect-timeout 20 -o $Destination $Url
    } catch {
        Write-Host "curl download failed, trying Start-BitsTransfer."
        Start-BitsTransfer -Source $Url -Destination $Destination
    }

    if (-not (Test-Path $Destination)) {
        throw "Download failed: $Destination"
    }
}

function Expand-ToolArchive {
    param(
        [string]$Archive,
        [string]$ExpectedMarker
    )

    $existing = Get-ChildItem $Tools -Directory -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -like $ExpectedMarker } |
        Select-Object -First 1

    if ($null -ne $existing -and -not $Force) {
        Write-Host "Tool exists: $($existing.FullName)"
        return $existing.FullName
    }

    if ($Force -and $null -ne $existing) {
        Write-Host "Removing existing tool directory: $($existing.FullName)"
        Remove-Item -LiteralPath $existing.FullName -Recurse -Force
    }

    Write-Host "Extracting $Archive to $Tools"
    Expand-Archive -Path $Archive -DestinationPath $Tools -Force

    $installed = Get-ChildItem $Tools -Directory -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -like $ExpectedMarker } |
        Select-Object -First 1

    if ($null -eq $installed) {
        throw "Tool directory not found after extraction, marker=$ExpectedMarker"
    }

    return $installed.FullName
}

Save-File -Url $GrafanaUrl -Destination $GrafanaZip
Save-File -Url $PrometheusUrl -Destination $PrometheusZip

$GrafanaHome = Expand-ToolArchive -Archive $GrafanaZip -ExpectedMarker "grafana*$GrafanaVersion*"
$PrometheusHome = Expand-ToolArchive -Archive $PrometheusZip -ExpectedMarker "prometheus-$PrometheusVersion.windows-amd64"

Write-Host "Grafana home: $GrafanaHome"
Write-Host "Prometheus home: $PrometheusHome"

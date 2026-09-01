param(
    [string]$ConfigDir = "deploy/nacos/configs",
    [string]$NacosServerAddr = "127.0.0.1:8848",
    [string]$Group = "FOOD_LIFE_AGENT",
    [string]$Namespace = ""
)

$ErrorActionPreference = "Stop"

$Root = Split-Path -Parent $PSScriptRoot
$ConfigPath = Join-Path $Root $ConfigDir

if (-not (Test-Path $ConfigPath)) {
    throw "Nacos config directory not found: $ConfigPath"
}

& (Join-Path $PSScriptRoot "check-nacos.ps1") -Port (($NacosServerAddr -split ":")[-1])

$Files = Get-ChildItem -LiteralPath $ConfigPath -File -Filter "*.yaml" | Sort-Object Name

foreach ($file in $Files) {
    $query = "dataId=$([uri]::EscapeDataString($file.Name))&group=$([uri]::EscapeDataString($Group))"
    if (-not [string]::IsNullOrWhiteSpace($Namespace)) {
        $query = $query + "&tenant=$([uri]::EscapeDataString($Namespace))"
    }

    $url = "http://$NacosServerAddr/nacos/v1/cs/configs?$query"
    $content = Invoke-RestMethod -Method Get -Uri $url
    if ([string]::IsNullOrWhiteSpace($content)) {
        throw "Nacos config is empty or missing: dataId=$($file.Name), group=$Group"
    }
    Write-Host "Verified Nacos config: dataId=$($file.Name), group=$Group, length=$($content.Length)"
}

Write-Host "Nacos config verification completed."

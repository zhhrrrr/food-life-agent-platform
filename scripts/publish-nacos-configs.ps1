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

$PublishUrl = "http://$NacosServerAddr/nacos/v1/cs/configs"
$Files = Get-ChildItem -LiteralPath $ConfigPath -File -Filter "*.yaml" | Sort-Object Name

foreach ($file in $Files) {
    $body = @{
        dataId = $file.Name
        group = $Group
        content = Get-Content -LiteralPath $file.FullName -Raw
        type = "yaml"
    }
    if (-not [string]::IsNullOrWhiteSpace($Namespace)) {
        $body.tenant = $Namespace
    }

    $result = Invoke-RestMethod -Method Post -Uri $PublishUrl -Body $body -ContentType "application/x-www-form-urlencoded"
    if ($result -ne $true -and "$result" -ne "true") {
        throw "Publish Nacos config failed: dataId=$($file.Name), result=$result"
    }
    Write-Host "Published Nacos config: dataId=$($file.Name), group=$Group"
}

Write-Host "Nacos config publish completed."

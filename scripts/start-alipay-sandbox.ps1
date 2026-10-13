param([switch]$Rebuild, [switch]$Restart, [string]$ArtifactSourceRoot)
$ErrorActionPreference = "Stop"
& (Join-Path $PSScriptRoot "start-local-services.ps1") -IncludeGateway `
    -SpringProfilesActive "nacos,alipay-sandbox" -Rebuild:$Rebuild -Restart:$Restart -ArtifactSourceRoot $ArtifactSourceRoot

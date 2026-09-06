param(
    [string]$GatewayManagementBaseUrl = "http://localhost:8081",
    [string]$UserManagementBaseUrl = "http://localhost:8102",
    [string]$BusinessManagementBaseUrl = "http://localhost:8202",
    [string]$TradeManagementBaseUrl = "http://localhost:8302"
)

$ErrorActionPreference = "Stop"

function Assert-Actuator {
    param(
        [string]$Name,
        [string]$BaseUrl
    )

    $health = Invoke-RestMethod -Method Get -Uri "$BaseUrl/actuator/health" -TimeoutSec 5
    if ($health.status -ne "UP") {
        throw "$Name actuator health failed, status=$($health.status)"
    }
    Write-Host "OK $Name actuator health -> status=$($health.status)"

    $metrics = Invoke-RestMethod -Method Get -Uri "$BaseUrl/actuator/prometheus" -TimeoutSec 5
    if ([string]::IsNullOrWhiteSpace($metrics) -or -not $metrics.Contains("jvm_memory_used_bytes")) {
        throw "$Name prometheus endpoint did not expose JVM metrics"
    }
    Write-Host "OK $Name prometheus metrics"
}

Assert-Actuator -Name "gateway" -BaseUrl $GatewayManagementBaseUrl
Assert-Actuator -Name "user" -BaseUrl $UserManagementBaseUrl
Assert-Actuator -Name "business" -BaseUrl $BusinessManagementBaseUrl
Assert-Actuator -Name "trade" -BaseUrl $TradeManagementBaseUrl

Write-Host "Observability smoke verification completed."

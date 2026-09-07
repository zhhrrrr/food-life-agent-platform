param(
    [switch]$SkipInstall,
    [switch]$Restart,
    [int]$PrometheusPort = 9090,
    [int]$GrafanaPort = 4000,
    [string]$GrafanaUser = "admin",
    [string]$GrafanaPassword = "admin"
)

$ErrorActionPreference = "Stop"

$Root = Split-Path -Parent $PSScriptRoot
$Tools = Join-Path $Root "tools"
$Runtime = Join-Path $Root ".runtime\observability"
$Logs = Join-Path $Root "logs"
$PrometheusData = Join-Path $Runtime "prometheus-data"
$GrafanaRuntime = Join-Path $Runtime "grafana"
$GrafanaData = Join-Path $GrafanaRuntime "data"
$GrafanaLogs = Join-Path $GrafanaRuntime "logs"
$GrafanaProvisioning = Join-Path $GrafanaRuntime "provisioning"
$GrafanaDashboardDir = Join-Path $GrafanaRuntime "dashboards"

New-Item -ItemType Directory -Force -Path `
    $Runtime, $Logs, $PrometheusData, $GrafanaData, $GrafanaLogs, `
    $GrafanaProvisioning, $GrafanaDashboardDir, `
    (Join-Path $GrafanaProvisioning "datasources"), `
    (Join-Path $GrafanaProvisioning "dashboards") | Out-Null

if (-not $SkipInstall) {
    & (Join-Path $PSScriptRoot "install-observability-stack.ps1")
}

$PrometheusHome = Get-ChildItem $Tools -Directory -ErrorAction SilentlyContinue |
    Where-Object { $_.Name -like "prometheus-*.windows-amd64" } |
    Sort-Object Name -Descending |
    Select-Object -First 1
$GrafanaHome = Get-ChildItem $Tools -Directory -ErrorAction SilentlyContinue |
    Where-Object { $_.Name -like "grafana*" } |
    Sort-Object Name -Descending |
    Select-Object -First 1

if ($null -eq $PrometheusHome) {
    throw "Prometheus is not installed. Run scripts\install-observability-stack.ps1 first."
}
if ($null -eq $GrafanaHome) {
    throw "Grafana is not installed. Run scripts\install-observability-stack.ps1 first."
}

$PrometheusExe = Join-Path $PrometheusHome.FullName "prometheus.exe"
$GrafanaExe = Join-Path $GrafanaHome.FullName "bin\grafana-server.exe"
if (-not (Test-Path $PrometheusExe)) {
    throw "Prometheus executable not found: $PrometheusExe"
}
if (-not (Test-Path $GrafanaExe)) {
    throw "Grafana executable not found: $GrafanaExe"
}

function Get-PortProcessId {
    param([int]$Port)
    $connection = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($null -eq $connection) {
        return $null
    }
    return $connection.OwningProcess
}

function Stop-Port {
    param(
        [string]$Name,
        [int]$Port
    )
    $processId = Get-PortProcessId -Port $Port
    if ($null -ne $processId) {
        Write-Host "Stopping $Name on port $Port, pid=$processId"
        Stop-Process -Id $processId -Force
        Start-Sleep -Seconds 2
    }
}

if ($Restart) {
    Stop-Port -Name "Prometheus" -Port $PrometheusPort
    Stop-Port -Name "Grafana" -Port $GrafanaPort
}

$PrometheusConfig = @"
global:
  scrape_interval: 15s
  evaluation_interval: 15s

scrape_configs:
  - job_name: 'food-gateway-service'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['127.0.0.1:8081']
        labels:
          application: 'food-gateway-service'
  - job_name: 'food-user-service'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['127.0.0.1:8102']
        labels:
          application: 'food-user-service'
  - job_name: 'food-business-service'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['127.0.0.1:8202']
        labels:
          application: 'food-business-service'
  - job_name: 'food-trade-service'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['127.0.0.1:8302']
        labels:
          application: 'food-trade-service'
"@
Set-Content -Path (Join-Path $Runtime "prometheus-local.yml") -Value $PrometheusConfig -Encoding UTF8

$DatasourceConfig = @"
apiVersion: 1

datasources:
  - name: Food Prometheus
    type: prometheus
    access: proxy
    url: http://127.0.0.1:$PrometheusPort
    isDefault: true
    editable: true
"@
Set-Content -Path (Join-Path $GrafanaProvisioning "datasources\prometheus.yml") -Value $DatasourceConfig -Encoding UTF8

$DashboardProvider = @"
apiVersion: 1

providers:
  - name: Food Life Agent
    orgId: 1
    folder: Food Life Agent
    type: file
    disableDeletion: false
    updateIntervalSeconds: 10
    allowUiUpdates: true
    options:
      path: $($GrafanaDashboardDir -replace '\\','/')
"@
Set-Content -Path (Join-Path $GrafanaProvisioning "dashboards\food-life-agent.yml") -Value $DashboardProvider -Encoding UTF8
Copy-Item -Path (Join-Path $Root "deploy\observability\grafana\dashboards\*.json") -Destination $GrafanaDashboardDir -Force

if ($null -eq (Get-PortProcessId -Port $PrometheusPort)) {
    Write-Host "Starting Prometheus on port $PrometheusPort"
    Start-Process -FilePath $PrometheusExe `
        -ArgumentList "--config.file=`"$(Join-Path $Runtime 'prometheus-local.yml')`"", "--storage.tsdb.path=`"$PrometheusData`"", "--web.listen-address=127.0.0.1:$PrometheusPort" `
        -WorkingDirectory $PrometheusHome.FullName `
        -RedirectStandardOutput (Join-Path $Logs "prometheus-$PrometheusPort.out.log") `
        -RedirectStandardError (Join-Path $Logs "prometheus-$PrometheusPort.err.log") `
        -WindowStyle Hidden
} else {
    Write-Host "Prometheus already listening on port $PrometheusPort"
}

if ($null -eq (Get-PortProcessId -Port $GrafanaPort)) {
    Write-Host "Starting Grafana on port $GrafanaPort"
    $GrafanaArgs = @(
        "--homepath=`"$($GrafanaHome.FullName)`"",
        "--config=`"$(Join-Path $GrafanaHome.FullName 'conf\defaults.ini')`"",
        "cfg:default.paths.data=`"$GrafanaData`"",
        "cfg:default.paths.logs=`"$GrafanaLogs`"",
        "cfg:default.paths.provisioning=`"$GrafanaProvisioning`"",
        "cfg:server.http_addr=127.0.0.1",
        "cfg:server.http_port=$GrafanaPort",
        "cfg:security.admin_user=$GrafanaUser",
        "cfg:security.admin_password=$GrafanaPassword",
        "cfg:users.default_theme=light"
    )
    Start-Process -FilePath $GrafanaExe `
        -ArgumentList $GrafanaArgs `
        -WorkingDirectory $GrafanaHome.FullName `
        -RedirectStandardOutput (Join-Path $Logs "grafana-$GrafanaPort.out.log") `
        -RedirectStandardError (Join-Path $Logs "grafana-$GrafanaPort.err.log") `
        -WindowStyle Hidden
} else {
    Write-Host "Grafana already listening on port $GrafanaPort"
}

for ($i = 1; $i -le 60; $i++) {
    try {
        $ready = Invoke-RestMethod -Uri "http://127.0.0.1:$PrometheusPort/-/ready" -TimeoutSec 3
        $health = Invoke-RestMethod -Uri "http://127.0.0.1:$GrafanaPort/api/health" -TimeoutSec 3
        if ($ready -like "*Ready*" -and $health.database -eq "ok") {
            break
        }
    } catch {
        Start-Sleep -Seconds 1
    }
}

$PrometheusPid = Get-PortProcessId -Port $PrometheusPort
$GrafanaPid = Get-PortProcessId -Port $GrafanaPort
if ($null -eq $PrometheusPid) {
    throw "Prometheus did not start. Check logs\prometheus-$PrometheusPort.err.log"
}
if ($null -eq $GrafanaPid) {
    throw "Grafana did not start. Check logs\grafana-$GrafanaPort.err.log"
}

$AuthHeader = @{
    Authorization = "Basic " + [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes("${GrafanaUser}:${GrafanaPassword}"))
}

$DataSources = Invoke-RestMethod -Uri "http://127.0.0.1:$GrafanaPort/api/datasources" -Headers $AuthHeader
$FoodPrometheus = @($DataSources | Where-Object { $_.name -eq "Food Prometheus" } | Select-Object -First 1)
if ($FoodPrometheus.Count -eq 0) {
    $DataSourceBody = @{
        name = "Food Prometheus"
        type = "prometheus"
        access = "proxy"
        url = "http://127.0.0.1:$PrometheusPort"
        isDefault = $true
        editable = $true
    } | ConvertTo-Json -Depth 10
    Invoke-RestMethod -Method Post -Uri "http://127.0.0.1:$GrafanaPort/api/datasources" -Headers $AuthHeader -ContentType "application/json" -Body $DataSourceBody | Out-Null
    Write-Host "Grafana datasource created: Food Prometheus"
} else {
    $UpdateBody = @{
        name = "Food Prometheus"
        type = "prometheus"
        access = "proxy"
        url = "http://127.0.0.1:$PrometheusPort"
        isDefault = $true
        editable = $true
    } | ConvertTo-Json -Depth 10
    Invoke-RestMethod -Method Put -Uri "http://127.0.0.1:$GrafanaPort/api/datasources/$($FoodPrometheus.id)" -Headers $AuthHeader -ContentType "application/json" -Body $UpdateBody | Out-Null
    Write-Host "Grafana datasource updated: Food Prometheus"
}

$DashboardPath = Join-Path $Root "deploy\observability\grafana\dashboards\food-life-agent-overview.json"
$Dashboard = Get-Content $DashboardPath -Raw | ConvertFrom-Json
$DashboardPayload = @{
    dashboard = $Dashboard
    overwrite = $true
} | ConvertTo-Json -Depth 100
$DashboardResult = Invoke-RestMethod -Method Post -Uri "http://127.0.0.1:$GrafanaPort/api/dashboards/db" -Headers $AuthHeader -ContentType "application/json" -Body $DashboardPayload

$Targets = Invoke-RestMethod -Uri "http://127.0.0.1:$PrometheusPort/api/v1/targets" -TimeoutSec 5
$TargetStates = @($Targets.data.activeTargets | ForEach-Object { "$($_.labels.application)=$($_.health)" })

Write-Host "Prometheus ready: http://127.0.0.1:$PrometheusPort pid=$PrometheusPid"
Write-Host "Grafana ready: http://127.0.0.1:$GrafanaPort pid=$GrafanaPid"
Write-Host "Grafana dashboard: http://127.0.0.1:$GrafanaPort$($DashboardResult.url)"
Write-Host "Prometheus targets: $($TargetStates -join '; ')"

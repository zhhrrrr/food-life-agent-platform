param(
    [string]$RabbitMqVersion = "4.3.5",
    [string]$ErlangHome = "C:\Users\zhr\.food-life-tools\erlang-27.3.4.13",
    [int]$AmqpPort = 5672,
    [int]$ManagementPort = 15672,
    [int]$WaitSeconds = 90
)

$ErrorActionPreference = "Stop"

$Root = Split-Path -Parent $PSScriptRoot
$RabbitMqHome = Join-Path $Root "tools\rabbitmq-server\rabbitmq_server-$RabbitMqVersion"
$RabbitMqBin = Join-Path $RabbitMqHome "sbin"
$RabbitMqServer = Join-Path $RabbitMqBin "rabbitmq-server.bat"
$RabbitMqPlugins = Join-Path $RabbitMqBin "rabbitmq-plugins.bat"
$Runtime = Join-Path $Root ".runtime\rabbitmq"
$Logs = Join-Path $Root "logs"

New-Item -ItemType Directory -Force -Path $Runtime | Out-Null
New-Item -ItemType Directory -Force -Path $Logs | Out-Null

function Test-PortListening {
    param([int]$ListenPort)
    $connection = Get-NetTCPConnection -LocalPort $ListenPort -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
    return $null -ne $connection
}

if (-not (Test-Path (Join-Path $ErlangHome "bin\erl.exe"))) {
    throw "Erlang is not installed at $ErlangHome. Run scripts\install-local-rabbitmq.ps1 first."
}

if (-not (Test-Path $RabbitMqServer)) {
    throw "RabbitMQ is not installed at $RabbitMqHome. Run scripts\install-local-rabbitmq.ps1 first."
}

$env:ERLANG_HOME = $ErlangHome
$env:Path = "$ErlangHome\bin;$env:Path"
$env:RABBITMQ_BASE = $Runtime
$env:RABBITMQ_NODENAME = "rabbit@localhost"

if (-not (Test-PortListening -ListenPort $AmqpPort)) {
    Write-Host "Starting RabbitMQ AMQP on port $AmqpPort"
    Start-Process -FilePath "cmd.exe" `
        -ArgumentList "/c", "rabbitmq-server.bat" `
        -WorkingDirectory $RabbitMqBin `
        -RedirectStandardOutput (Join-Path $Logs "rabbitmq-server.out.log") `
        -RedirectStandardError (Join-Path $Logs "rabbitmq-server.err.log") `
        -WindowStyle Hidden
}

for ($i = 1; $i -le $WaitSeconds; $i++) {
    if (Test-PortListening -ListenPort $AmqpPort) {
        Write-Host "RabbitMQ AMQP is ready on port $AmqpPort"
        break
    }
    Start-Sleep -Seconds 1
}

if (-not (Test-PortListening -ListenPort $AmqpPort)) {
    throw "RabbitMQ AMQP did not become ready in $WaitSeconds seconds. Check logs\rabbitmq-server.out.log and logs\rabbitmq-server.err.log."
}

& $RabbitMqPlugins enable rabbitmq_management

for ($i = 1; $i -le 60; $i++) {
    if (Test-PortListening -ListenPort $ManagementPort) {
        Write-Host "RabbitMQ Management is ready: http://127.0.0.1:$ManagementPort"
        exit 0
    }
    Start-Sleep -Seconds 1
}

throw "RabbitMQ Management did not become ready on port $ManagementPort."

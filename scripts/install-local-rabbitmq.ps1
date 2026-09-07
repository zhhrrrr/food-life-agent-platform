param(
    [string]$RabbitMqVersion = "4.3.5",
    [string]$ErlangVersion = "27.3.4.13",
    [string]$ErlangHome = "C:\Users\zhr\.food-life-tools\erlang-27.3.4.13"
)

$ErrorActionPreference = "Stop"

$Root = Split-Path -Parent $PSScriptRoot
$Cache = Join-Path $Root ".cache\rabbitmq"
$Tools = Join-Path $Root "tools"
$RabbitMqZip = Join-Path $Cache "rabbitmq-server-windows-$RabbitMqVersion.zip"
$ErlangInstaller = Join-Path $Cache "otp_win64_$ErlangVersion.exe"
$RabbitMqHome = Join-Path $Tools "rabbitmq-server\rabbitmq_server-$RabbitMqVersion"

New-Item -ItemType Directory -Force -Path $Cache | Out-Null
New-Item -ItemType Directory -Force -Path $Tools | Out-Null

$RabbitMqUrl = "https://github.com/rabbitmq/rabbitmq-server/releases/download/v$RabbitMqVersion/rabbitmq-server-windows-$RabbitMqVersion.zip"
$ErlangUrl = "https://github.com/erlang/otp/releases/download/OTP-$ErlangVersion/otp_win64_$ErlangVersion.exe"

if (-not (Test-Path $ErlangInstaller)) {
    Write-Host "Downloading Erlang $ErlangVersion"
    Invoke-WebRequest -Uri $ErlangUrl -OutFile $ErlangInstaller -UseBasicParsing
}

if (-not (Test-Path (Join-Path $ErlangHome "bin\erl.exe"))) {
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $ErlangHome) | Out-Null
    Write-Host "Installing Erlang to $ErlangHome"
    $process = Start-Process -FilePath $ErlangInstaller -ArgumentList "/S /D=$ErlangHome" -Wait -PassThru
    if ($process.ExitCode -ne 0) {
        throw "Erlang installer failed with exit code $($process.ExitCode)"
    }
}

if (-not (Test-Path $RabbitMqZip)) {
    Write-Host "Downloading RabbitMQ $RabbitMqVersion"
    Invoke-WebRequest -Uri $RabbitMqUrl -OutFile $RabbitMqZip -UseBasicParsing
}

if (-not (Test-Path (Join-Path $RabbitMqHome "sbin\rabbitmq-server.bat"))) {
    Write-Host "Extracting RabbitMQ to tools\rabbitmq-server"
    Expand-Archive -Path $RabbitMqZip -DestinationPath (Join-Path $Tools "rabbitmq-server") -Force
}

Write-Host "Erlang home: $ErlangHome"
Write-Host "RabbitMQ home: $RabbitMqHome"

param(
  [string]$BaseUrl = "http://127.0.0.1:8080",
  [string]$AuthToken,
  [string]$Phone = "13800138992",
  [int]$PackageId = 1,
  [int]$ActivityId = 1,
  [ValidateSet("smoke", "load")]
  [string]$Profile = "smoke"
)

$ErrorActionPreference = "Stop"

function Resolve-K6Exe {
  $systemK6 = Get-Command k6 -ErrorAction SilentlyContinue
  if ($systemK6) {
    return $systemK6.Source
  }

  $localK6 = Join-Path (Split-Path $PSScriptRoot -Parent) "tools\k6\k6.exe"
  if (Test-Path $localK6) {
    return $localK6
  }

  throw "k6 is not installed. Expected k6 in PATH or tools\k6\k6.exe."
}

function Resolve-AuthToken {
  if (-not [string]::IsNullOrWhiteSpace($AuthToken)) {
    return $AuthToken
  }

  Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/user/code?phone=$Phone" -TimeoutSec 30 | Out-Null
  $code = (& redis-cli -h localhost -p 6379 -n 0 get "food:login:code:$Phone") -join ""
  if ([string]::IsNullOrWhiteSpace($code)) {
    throw "login code missing in redis, phone=$Phone"
  }

  $body = @{ phone = $Phone; code = $code } | ConvertTo-Json -Compress
  $login = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/user/login" -ContentType "application/json" -Body $body -TimeoutSec 30
  if ($login.code -ne "0000") {
    throw "login failed, code=$($login.code), message=$($login.message)"
  }
  return $login.data.token
}

$k6Exe = Resolve-K6Exe
$token = Resolve-AuthToken

$env:BASE_URL = $BaseUrl
$env:AUTH_TOKEN = $token
$env:PACKAGE_ID = "$PackageId"
$env:ACTIVITY_ID = "$ActivityId"
$env:K6_PROFILE = $Profile

$summaryDir = Join-Path (Split-Path $PSScriptRoot -Parent) "logs\k6"
New-Item -ItemType Directory -Force -Path $summaryDir | Out-Null

Write-Host "k6 executable: $k6Exe"
Write-Host "k6 profile: $Profile"
Write-Host "gateway: $BaseUrl"
Write-Host "packageId: $PackageId, activityId: $ActivityId"

& $k6Exe run --summary-export (Join-Path $summaryDir "normal-order-summary.json") "$PSScriptRoot/k6/normal-order.js"
& $k6Exe run --summary-export (Join-Path $summaryDir "group-buy-order-summary.json") "$PSScriptRoot/k6/group-buy-order.js"
& $k6Exe run --summary-export (Join-Path $summaryDir "seckill-order-summary.json") "$PSScriptRoot/k6/seckill-order.js"

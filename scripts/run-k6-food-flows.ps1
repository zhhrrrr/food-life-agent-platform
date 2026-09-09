param(
  [string]$BaseUrl = "http://127.0.0.1:8080",
  [string]$AuthToken,
  [int]$PackageId = 1,
  [int]$ActivityId = 1
)

$ErrorActionPreference = "Stop"

if (-not (Get-Command k6 -ErrorAction SilentlyContinue)) {
  throw "k6 is not installed. Install k6 first, then rerun this script."
}

$env:BASE_URL = $BaseUrl
$env:AUTH_TOKEN = $AuthToken
$env:PACKAGE_ID = "$PackageId"
$env:ACTIVITY_ID = "$ActivityId"

k6 run "$PSScriptRoot/k6/normal-order.js"
k6 run "$PSScriptRoot/k6/group-buy-order.js"
k6 run "$PSScriptRoot/k6/seckill-order.js"

param(
    [string]$PreferredJavaHome = $env:FOOD_JAVA_HOME
)

$ErrorActionPreference = "Stop"

function Get-JavaVersionOutput {
    param([string]$JavaExe)
    $info = New-Object System.Diagnostics.ProcessStartInfo
    $info.FileName = $JavaExe
    $info.Arguments = "-version"
    $info.UseShellExecute = $false
    $info.CreateNoWindow = $true
    $info.RedirectStandardError = $true
    $process = [System.Diagnostics.Process]::Start($info)
    try {
        $output = $process.StandardError.ReadToEnd()
        $process.WaitForExit()
        if ($process.ExitCode -ne 0) { throw "Java version probe failed: $JavaExe" }
        return $output
    } finally { $process.Dispose() }
}

function Test-JavaHome17Plus {
    param([string]$JavaHome)
    if ([string]::IsNullOrWhiteSpace($JavaHome)) {
        return $false
    }
    $javaExe = Join-Path $JavaHome "bin\java.exe"
    if (-not (Test-Path $javaExe)) {
        return $false
    }
    $javacExe = Join-Path $JavaHome "bin\javac.exe"
    if (-not (Test-Path $javacExe)) {
        return $false
    }
    $releaseFile = Join-Path $JavaHome "release"
    if (Test-Path $releaseFile) {
        $release = Get-Content -Raw -Encoding utf8 $releaseFile
        if ($release -match 'JAVA_VERSION="(\d+)') {
            return [int]$Matches[1] -ge 17
        }
    }
    $versionOutput = Get-JavaVersionOutput $javaExe
    if ($versionOutput -match 'version "(\d+)') {
        return [int]$Matches[1] -ge 17
    }
    return $false
}

$candidates = @()
if (-not [string]::IsNullOrWhiteSpace($PreferredJavaHome)) {
    $candidates += $PreferredJavaHome
}
if (-not [string]::IsNullOrWhiteSpace($env:JAVA_HOME)) {
    $candidates += $env:JAVA_HOME
}
# Discover installed JDKs instead of pinning a user's IDE extension version.
$fallback = @()
foreach ($programRoot in @($env:ProgramFiles, "D:\Program Files")) {
    if ([string]::IsNullOrWhiteSpace($programRoot)) { continue }
    foreach ($vendor in @("Java", "Eclipse Adoptium", "Microsoft")) {
        $fallback += Get-ChildItem -Directory -Path (Join-Path $programRoot $vendor) -ErrorAction SilentlyContinue |
            Select-Object -ExpandProperty FullName
    }
    $fallback += Get-ChildItem -Directory -Path (Join-Path $programRoot "JetBrains\*\jbr") -ErrorAction SilentlyContinue |
        Select-Object -ExpandProperty FullName
}
if ($env:USERPROFILE) {
    $fallback += Get-ChildItem -Directory -Path "$env:USERPROFILE\.vscode\extensions\redhat.java-*\jre\*" -ErrorAction SilentlyContinue |
        Select-Object -ExpandProperty FullName
    $fallback += Get-ChildItem -Directory -Path "$env:USERPROFILE\.jdks\*" -ErrorAction SilentlyContinue |
        Select-Object -ExpandProperty FullName
}
# Prefer the project's Java 17 baseline among automatic choices; explicit overrides retain priority.
$candidates += $fallback | Where-Object { Test-JavaHome17Plus $_ } | Sort-Object -Unique -Property {
    $release = Get-Content -Raw -Encoding utf8 (Join-Path $_ "release") -ErrorAction SilentlyContinue
    if ($release -match 'JAVA_VERSION="(\d+)') { [int]$Matches[1] } else { [int]::MaxValue }
}

foreach ($candidate in $candidates) {
    if (Test-JavaHome17Plus -JavaHome $candidate) {
        $env:JAVA_HOME = $candidate
        $env:PATH = (Join-Path $candidate "bin") + ";" + $env:PATH
        Write-Host "JAVA_HOME=$env:JAVA_HOME"
        Write-Host (Get-JavaVersionOutput (Join-Path $candidate "bin\java.exe"))
        return
    }
}

throw "JDK 17+ is required for Spring Boot 3. Set FOOD_JAVA_HOME to a JDK 17+ directory."

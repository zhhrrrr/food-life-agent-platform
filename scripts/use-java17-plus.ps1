param(
    [string]$PreferredJavaHome = $env:FOOD_JAVA_HOME
)

$ErrorActionPreference = "Stop"

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
    $versionOutput = (& $javaExe -version 2>&1 | Out-String)
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
$candidates += @(
    "C:\Users\zhr\.vscode\extensions\redhat.java-1.56.0-win32-x64\jre\21.0.12.1-win32-x86_64",
    "D:\Program Files\Java\jdk-25.0.2",
    "D:\Program Files\Java\jdk-21",
    "D:\Program Files\Java\jdk-17",
    "C:\Program Files\Java\jdk-21",
    "C:\Program Files\Java\jdk-17"
)

foreach ($candidate in $candidates) {
    if (Test-JavaHome17Plus -JavaHome $candidate) {
        $env:JAVA_HOME = $candidate
        $env:PATH = (Join-Path $candidate "bin") + ";" + $env:PATH
        Write-Host "JAVA_HOME=$env:JAVA_HOME"
        & (Join-Path $candidate "bin\java.exe") -version
        return
    }
}

throw "JDK 17+ is required for Spring Boot 3. Set FOOD_JAVA_HOME to a JDK 17+ directory."

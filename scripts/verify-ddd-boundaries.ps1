param(
    [string]$Root = (Resolve-Path "$PSScriptRoot\..").Path
)

$ErrorActionPreference = "Stop"

function Assert-NoPatternInJava {
    param(
        [string]$Directory,
        [string[]]$Patterns,
        [string]$Message
    )

    if (-not (Test-Path $Directory)) {
        return
    }

    $files = Get-ChildItem -Path $Directory -Recurse -Filter "*.java" -File
    foreach ($file in $files) {
        $content = Get-Content -Raw -Path $file.FullName
        foreach ($pattern in $Patterns) {
            if ($content -match $pattern) {
                throw "$Message pattern=$pattern file=$($file.FullName)"
            }
        }
    }
}

function Assert-NoPatternInFile {
    param(
        [string]$Path,
        [string[]]$Patterns,
        [string]$Message
    )

    if (-not (Test-Path $Path)) {
        return
    }

    $content = Get-Content -Raw -Path $Path
    foreach ($pattern in $Patterns) {
        if ($content -match $pattern) {
            throw "$Message pattern=$pattern file=$Path"
        }
    }
}

$services = @(
    "food-user-service",
    "food-business-service",
    "food-trade-service"
)

foreach ($service in $services) {
    $serviceShortName = $service -replace "-service$", ""
    $serviceRoot = Join-Path $Root $service
    $domainJava = Join-Path $serviceRoot "$serviceShortName-domain\src\main\java"
    $apiJava = Join-Path $serviceRoot "$serviceShortName-api\src\main\java"
    $typesJava = Join-Path $serviceRoot "$serviceShortName-types\src\main\java"
    $infrastructureJava = Join-Path $serviceRoot "$serviceShortName-infrastructure\src\main\java"

    Assert-NoPatternInJava `
        -Directory $domainJava `
        -Patterns @(
            "import com\.foodlife\..*\.infrastructure\.",
            "import com\.foodlife\..*\.trigger\.",
            "import com\.foodlife\..*\.app\."
        ) `
        -Message "$service domain layer must not depend on outer layers"

    Assert-NoPatternInJava `
        -Directory $apiJava `
        -Patterns @(
            "import com\.foodlife\..*\.domain\.",
            "import com\.foodlife\..*\.infrastructure\.",
            "import com\.foodlife\..*\.trigger\.",
            "import com\.foodlife\..*\.app\."
        ) `
        -Message "$service api layer must remain contract-only"

    Assert-NoPatternInJava `
        -Directory $typesJava `
        -Patterns @(
            "import com\.foodlife\..*\.domain\.",
            "import com\.foodlife\..*\.infrastructure\.",
            "import com\.foodlife\..*\.trigger\.",
            "import com\.foodlife\..*\.app\."
        ) `
        -Message "$service types layer must remain common and dependency-light"

    Assert-NoPatternInJava `
        -Directory $infrastructureJava `
        -Patterns @(
            "import com\.foodlife\..*\.trigger\.",
            "import com\.foodlife\..*\.app\."
        ) `
        -Message "$service infrastructure layer must not depend on trigger or app"

    Assert-NoPatternInFile `
        -Path (Join-Path $serviceRoot "$serviceShortName-domain\pom.xml") `
        -Patterns @(
            "<artifactId>$serviceShortName-infrastructure</artifactId>",
            "<artifactId>$serviceShortName-trigger</artifactId>",
            "<artifactId>$serviceShortName-app</artifactId>"
        ) `
        -Message "$service domain pom must not depend on outer modules"

    Assert-NoPatternInFile `
        -Path (Join-Path $serviceRoot "$serviceShortName-api\pom.xml") `
        -Patterns @(
            "<artifactId>$serviceShortName-domain</artifactId>",
            "<artifactId>$serviceShortName-infrastructure</artifactId>",
            "<artifactId>$serviceShortName-trigger</artifactId>",
            "<artifactId>$serviceShortName-app</artifactId>"
        ) `
        -Message "$service api pom must not depend on implementation modules"
}

Write-Host "OK DDD boundaries verified."

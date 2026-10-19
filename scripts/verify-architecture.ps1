param(
    [string]$ProjectRoot = (Split-Path -Parent $PSScriptRoot)
)

$ErrorActionPreference = "Stop"

function Get-ProjectFileContent {
    param([string]$RelativePath)

    $path = Join-Path $ProjectRoot $RelativePath
    if (-not (Test-Path -LiteralPath $path)) {
        throw "Missing required architecture file: $RelativePath"
    }
    return Get-Content -Raw -LiteralPath $path
}

function Assert-DoesNotMatch {
    param(
        [string]$Content,
        [string]$Pattern,
        [string]$Message
    )

    if ($Content -match $Pattern) {
        throw $Message
    }
}

$settings = Get-ProjectFileContent "settings.gradle.kts"
foreach ($module in @(":core:contract", ":domain", ":data", ":presentation")) {
    if ($settings -notmatch [regex]::Escape("include(`"$module`")")) {
        throw "Required architecture module is not included: $module"
    }
}

$expectedDependencies = @{
    "domain/build.gradle.kts" = @(":core:contract")
    "data/build.gradle.kts" = @(":core:contract", ":domain")
    "presentation/build.gradle.kts" = @(":domain")
}

foreach ($buildFile in $expectedDependencies.Keys) {
    $content = Get-ProjectFileContent $buildFile
    foreach ($dependency in $expectedDependencies[$buildFile]) {
        if ($content -notmatch [regex]::Escape("project(`"$dependency`")")) {
            throw "$buildFile must depend on $dependency"
        }
    }
    if ($content -match 'project\(":(app|data|presentation)"\)') {
        throw "$buildFile has a forbidden inward module dependency"
    }
}

$sourceRoots = @(
    "core/contract/src/main",
    "domain/src/main",
    "data/src/main",
    "presentation/src/main",
    "app/src/main/java"
)
$sourceFiles = foreach ($sourceRoot in $sourceRoots) {
    $path = Join-Path $ProjectRoot $sourceRoot
    if (Test-Path -LiteralPath $path) {
        Get-ChildItem -LiteralPath $path -Recurse -File -Filter "*.kt"
    }
}

$ioPattern = '(?m)^\s*import\s+(java\.io|java\.nio\.file|android\.provider\.MediaStore|android\.content\.ContentResolver|androidx\.documentfile)|\b(java\.(io|nio\.file)|FileInputStream|FileOutputStream|ContentResolver|MediaStore|DocumentsContract|DocumentFile)\b'
foreach ($file in $sourceFiles) {
    $content = Get-Content -Raw -LiteralPath $file.FullName
    Assert-DoesNotMatch -Content $content -Pattern $ioPattern `
        -Message "F02 must not contain real file I/O: $($file.FullName)"
}

$domainFiles = Get-ChildItem -LiteralPath (Join-Path $ProjectRoot "domain/src/main") -Recurse -File -Filter "*.kt"
foreach ($file in $domainFiles) {
    $content = Get-Content -Raw -LiteralPath $file.FullName
    Assert-DoesNotMatch -Content $content -Pattern '(?m)^\s*import\s+(android|androidx\.compose|androidx\.room)' `
        -Message "Domain must not import Android, Compose, or Room: $($file.FullName)"
}

$presentationFiles = Get-ChildItem -LiteralPath (Join-Path $ProjectRoot "presentation/src/main") -Recurse -File -Filter "*.kt"
foreach ($file in $presentationFiles) {
    $content = Get-Content -Raw -LiteralPath $file.FullName
    Assert-DoesNotMatch -Content $content -Pattern '(?m)^\s*import\s+com\.galleryorganizer\.contract' `
        -Message "Presentation must not import port contracts directly: $($file.FullName)"
}

$useCases = Get-ProjectFileContent "domain/src/main/kotlin/com/galleryorganizer/domain/OrganizerUseCases.kt"
foreach ($type in @("Scanner", "Analyzer", "AlbumEditor")) {
    $match = [regex]::Match(
        $useCases,
        "(?s)interface\s+$type\b.*?(?=\ninterface|\z)"
    )
    if (-not $match.Success) {
        throw "Missing domain use-case boundary: $type"
    }
    Assert-DoesNotMatch -Content $match.Value -Pattern '\bPermanentDeleter\b' `
        -Message "$type must not depend on PermanentDeleter"
}

Write-Output "Architecture verification passed."

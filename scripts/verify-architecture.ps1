param(
    [string]$ProjectRoot = (Split-Path -Parent $PSScriptRoot),
    [ValidateSet("F02", "PostSpike")]
    [string]$IoPolicyStage = "F02"
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

$androidLibraryModules = @("data/build.gradle.kts", "presentation/build.gradle.kts")
foreach ($buildFile in $androidLibraryModules) {
    $content = Get-ProjectFileContent $buildFile
    if ($content -notmatch [regex]::Escape("alias(libs.plugins.android.library)")) {
        throw "$buildFile must be an Android library module"
    }
}

$domainBuild = Get-ProjectFileContent "domain/build.gradle.kts"
if ($domainBuild -notmatch [regex]::Escape("alias(libs.plugins.kotlin.jvm)")) {
    throw "domain/build.gradle.kts must remain a Kotlin JVM module"
}

$contractBuild = Get-ProjectFileContent "core/contract/build.gradle.kts"
if ($contractBuild -match 'project\(":' -or
    $contractBuild -notmatch [regex]::Escape("alias(libs.plugins.kotlin.jvm)")) {
    throw "core:contract must be a dependency-free Kotlin JVM module"
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

$ioPattern = '(?m)^\s*import\s+(java\.io|java\.nio\.file|android\.provider\.MediaStore|android\.content\.ContentResolver|androidx\.documentfile)|\bjava\.(io|nio\.file)\.|\b(?:android\.provider\.)?MediaStore\.|\b(?:android\.content\.)?ContentResolver\.|\b(?:androidx\.documentfile\.)?DocumentFile\.|\bDocumentsContract\.'
$ioAllowedRoots = @{
    F02 = @()
    PostSpike = @(
        "data/src/main/kotlin/com/galleryorganizer/data/mediastore/",
        "data/src/main/kotlin/com/galleryorganizer/data/saf/",
        "data/src/main/kotlin/com/galleryorganizer/data/filesystem/",
        "spikes/"
    )
}
foreach ($file in $sourceFiles) {
    $content = Get-Content -Raw -LiteralPath $file.FullName
    $relativePath = $file.FullName.Substring($ProjectRoot.Length).TrimStart('\').Replace('\', '/')
    $isAllowedIoLocation = $ioAllowedRoots[$IoPolicyStage] | Where-Object {
        $relativePath.StartsWith($_, [System.StringComparison]::OrdinalIgnoreCase)
    }
    if (-not $isAllowedIoLocation) {
        Assert-DoesNotMatch -Content $content -Pattern $ioPattern `
            -Message "$IoPolicyStage must not contain real file I/O: $relativePath"
    }
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
foreach ($type in @("Scanner", "Analyzer", "AlbumEditor", "SourceCleanup")) {
    $match = [regex]::Match(
        $useCases,
        "(?s)interface\s+$type\b.*?(?=\ninterface|\z)"
    )
    if (-not $match.Success) {
        throw "Missing domain use-case boundary: $type"
    }
    Assert-DoesNotMatch -Content $match.Value `
        -Pattern '\b(SourceReader|WorkspaceGateway|PermanentDeleter|ManifestStore)\b' `
        -Message "$type must not expose a port dependency"
}

Write-Output "Architecture verification passed."

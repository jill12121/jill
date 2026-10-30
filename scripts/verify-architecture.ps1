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
foreach ($module in @(
    ":core:contract",
    ":domain",
    ":data",
    ":presentation",
    ":spikes:s02-fixture-contract",
    ":spikes:s02-fixture-helper",
    ":spikes:s05-thumbnail",
    ":spikes:s05-thumbnail-harness"
)) {
    if ($settings -notmatch [regex]::Escape("include(`"$module`")")) {
        throw "Required architecture module is not included: $module"
    }
}

$appBuild = Get-ProjectFileContent "app/build.gradle.kts"
if ($appBuild -match 'project\(":spikes:') {
    throw "Production app must not depend on spike modules"
}
foreach ($buildFile in @(
    "core/contract/build.gradle.kts",
    "domain/build.gradle.kts",
    "data/build.gradle.kts",
    "presentation/build.gradle.kts"
)) {
    if ((Get-ProjectFileContent $buildFile) -match 'project\(":spikes:') {
        throw "Production modules must not depend on spike modules"
    }
}
$probeSuiteBuild = Get-ProjectFileContent "spikes/probe-suite/build.gradle.kts"
if ($probeSuiteBuild -match 'project\(":spikes:s02-fixture-helper"\)') {
    throw "Probe suite must not depend on the external fixture helper APK"
}
$helperBuild = Get-ProjectFileContent "spikes/s02-fixture-helper/build.gradle.kts"
if (
    $helperBuild -notmatch [regex]::Escape("alias(libs.plugins.android.application)") -or
    $helperBuild -notmatch 'applicationId\s*=\s*"com\.galleryorganizer\.spikes\.s02\.fixturehelper"'
) {
    throw "S02 fixture helper must remain an independently installed application"
}
$probeApplicationId = [regex]::Match(
    $probeSuiteBuild,
    'applicationId\s*=\s*"([^"]+)"'
)
$helperApplicationId = [regex]::Match(
    $helperBuild,
    'applicationId\s*=\s*"([^"]+)"'
)
if (
    -not $probeApplicationId.Success -or
    -not $helperApplicationId.Success -or
    $probeApplicationId.Groups[1].Value -ne "com.galleryorganizer.spikes.suite" -or
    $helperApplicationId.Groups[1].Value -ne "com.galleryorganizer.spikes.s02.fixturehelper" -or
    $probeApplicationId.Groups[1].Value -eq $helperApplicationId.Groups[1].Value
) {
    throw "Probe suite and fixture helper must use distinct application IDs"
}
$helperManifest = Get-ProjectFileContent "spikes/s02-fixture-helper/src/main/AndroidManifest.xml"
$probeManifest = Get-ProjectFileContent "spikes/probe-suite/src/main/AndroidManifest.xml"
if ($helperManifest -match 'sharedUserId' -or $probeManifest -match 'sharedUserId') {
    throw "Probe suite and S02 fixture helper must not share an Android UID"
}
$s02Manifest = Get-ProjectFileContent "spikes/s02-mediastore/src/main/AndroidManifest.xml"
$androidNamespace = "http://schemas.android.com/apk/res/android"
$helperManifestXml = [xml]$helperManifest
$s02ManifestXml = [xml]$s02Manifest
$helperPermissions = @($helperManifestXml.manifest.'uses-permission') | Where-Object { $null -ne $_ }
if (@($helperPermissions).Count -ne 0) {
    throw "S02 fixture helper must not request permissions"
}
$s02ProbeActivity = @($s02ManifestXml.manifest.application.activity) | Where-Object {
    $_.GetAttribute("name", $androidNamespace) -eq ".S02ProbeActivity"
}
if (
    -not $s02ProbeActivity -or
    $s02ProbeActivity.GetAttribute("exported", $androidNamespace) -ne "false" -or
    $s02ProbeActivity.HasAttribute("permission", $androidNamespace)
) {
    throw "S02 probe activity must remain non-exported for clipboard token handoff"
}
$s05Build = Get-ProjectFileContent "spikes/s05-thumbnail/build.gradle.kts"
if (
    $s05Build -notmatch [regex]::Escape("alias(libs.plugins.kotlin.jvm)") -or
    $s05Build -match 'alias\(libs\.plugins\.android\.' -or
    $s05Build -match 'project\(":'
) {
    throw "S05.1 contract module must remain dependency-free Kotlin/JVM"
}
$s05HarnessBuild = Get-ProjectFileContent "spikes/s05-thumbnail-harness/build.gradle.kts"
if (
    $s05HarnessBuild -notmatch [regex]::Escape("alias(libs.plugins.android.application)") -or
    $s05HarnessBuild -notmatch 'applicationId\s*=\s*"com\.galleryorganizer\.spikes\.s05\.harness"' -or
    $s05HarnessBuild -notmatch [regex]::Escape('project(":spikes:s05-thumbnail")') -or
    $s05HarnessBuild -match 'project\(":(?!spikes:s05-thumbnail")'
) {
    throw "S05 harness must remain an isolated application depending only on the S05 contract"
}
$s05HarnessManifest = Get-ProjectFileContent (
    "spikes/s05-thumbnail-harness/src/main/AndroidManifest.xml"
)
if (
    $s05HarnessManifest -match '<uses-permission' -or
    $s05HarnessManifest -match 'sharedUserId'
) {
    throw "S05 harness must not request permissions or share an Android UID"
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
    Assert-DoesNotMatch -Content $content `
        -Pattern '\bFake(SourceReader|WorkspaceGateway|ManifestStore|PermanentDeleter)\b|(?m)^\s*import\s+com\.galleryorganizer\.data\.fake' `
        -Message "Production source must not depend on test fakes: $relativePath"
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
    Assert-DoesNotMatch -Content $content `
        -Pattern '(?m)^\s*import\s+(android\.content\.(ContentResolver|Intent)|android\.net\.Uri|android\.provider\.MediaStore|androidx\.activity\.result|androidx\.documentfile)' `
        -Message "F05 presentation shell must not invoke Android storage capabilities: $($file.FullName)"
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

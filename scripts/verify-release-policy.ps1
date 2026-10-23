param(
    [string]$ProjectRoot = (Split-Path -Parent $PSScriptRoot),
    [string]$MergedManifestPath,
    [string]$DependencyReportPath,
    [string]$ReleaseArtifactPath
)

$ErrorActionPreference = "Stop"

function Assert-ReleaseManifestPolicy {
    param([string]$Path)

    if (-not (Test-Path -LiteralPath $Path)) {
        throw "Missing release merged manifest: $Path"
    }

    [xml]$manifest = Get-Content -Raw -LiteralPath $Path
    $androidNamespace = "http://schemas.android.com/apk/res/android"
    $namespaceManager = New-Object System.Xml.XmlNamespaceManager($manifest.NameTable)
    $namespaceManager.AddNamespace("android", $androidNamespace)

    $forbiddenPermissions = @(
        "android.permission.INTERNET",
        "android.permission.MANAGE_EXTERNAL_STORAGE"
    )
    $permissions = @(
        $manifest.SelectNodes("/manifest/uses-permission", $namespaceManager) |
            ForEach-Object { $_.GetAttribute("name", $androidNamespace) }
    )
    $forbiddenPermission = $permissions |
        Where-Object { $_ -in $forbiddenPermissions } |
        Select-Object -First 1
    if ($forbiddenPermission) {
        throw "Forbidden release manifest permission: $forbiddenPermission"
    }

    $application = $manifest.SelectSingleNode("/manifest/application")
    if ($null -eq $application) {
        throw "Release merged manifest has no application element"
    }
    if ($application.GetAttribute("allowBackup", $androidNamespace) -ne "false") {
        throw "Release merged manifest android:allowBackup must be false"
    }
}

function Assert-ReleaseDependencyPolicy {
    param([string]$Report)

    $forbiddenPatterns = [ordered]@{
        "Retrofit" = 'com\.squareup\.retrofit2:'
        "OkHttp" = 'com\.squareup\.okhttp3:'
        "Ktor Client" = 'io\.ktor:ktor-client'
        "Volley" = 'com\.android\.volley:volley'
        "Fuel" = 'com\.github\.kittinunf\.fuel:'
        "Kotlin Coroutines Test" = 'org\.jetbrains\.kotlinx:kotlinx-coroutines-test:'
        "Room Testing" = 'androidx\.room:room-testing:'
        "Robolectric" = 'org\.robolectric:robolectric:'
        "JUnit" = 'junit:junit:'
        "Hilt Android Testing" = 'com\.google\.dagger:hilt-android-testing:'
        "Test Fixtures" = ':[^:\s]+-test-fixtures:'
        "Fake Artifact" = '(?im)(?:project\s+:|com\.galleryorganizer:)[^:\s]*fake[^:\s]*'
    }

    foreach ($entry in $forbiddenPatterns.GetEnumerator()) {
        if ($Report -match $entry.Value) {
            throw "Forbidden release runtime dependency: $($entry.Key)"
        }
    }
}

function Assert-ReleaseSourcePolicy {
    param([string]$Root)

    $productionRoots = @(
        "app/src/main",
        "data/src/main",
        "presentation/src/main"
    )
    $forbiddenFakePattern = (
        '(?m)^\s*import\s+com\.galleryorganizer\.data\.fake(?:\.|$)' +
        '|\bFake(SourceReader|WorkspaceGateway|ManifestStore|PermanentDeleter)\b'
    )

    foreach ($relativeRoot in $productionRoots) {
        $sourceRoot = Join-Path $Root $relativeRoot
        if (-not (Test-Path -LiteralPath $sourceRoot)) {
            continue
        }

        $sourceFiles = Get-ChildItem -Path $sourceRoot -File -Recurse |
            Where-Object { $_.Extension -in @(".kt", ".java") }
        foreach ($sourceFile in $sourceFiles) {
            $content = Get-Content -Raw -LiteralPath $sourceFile.FullName
            if ($content -match $forbiddenFakePattern) {
                throw "Forbidden release source Fake reference: $($sourceFile.FullName)"
            }
        }
    }
}

function Assert-ReleaseArtifactPolicy {
    param([string]$Path)
    if (-not (Test-Path -LiteralPath $Path)) {
        throw "Missing release APK/AAB: $Path"
    }
    $text = [Text.Encoding]::ASCII.GetString([IO.File]::ReadAllBytes($Path))
    if ($text -match '(?i)(com[./]galleryorganizer[./]spikes|:spikes:)') {
        throw "Forbidden spike package or module reference in release artifact"
    }
}

$dependencyReport = $null
if ([string]::IsNullOrWhiteSpace($MergedManifestPath) -or
    [string]::IsNullOrWhiteSpace($DependencyReportPath)) {
    $runningOnWindows = [System.Runtime.InteropServices.RuntimeInformation]::IsOSPlatform(
        [System.Runtime.InteropServices.OSPlatform]::Windows
    )
    $gradlePath = Join-Path $ProjectRoot $(if ($runningOnWindows) { "gradlew.bat" } else { "gradlew" })
    if (-not (Test-Path -LiteralPath $gradlePath)) {
        throw "Missing Gradle Wrapper: $gradlePath"
    }

    $dependencyOutput = & $gradlePath `
        --no-daemon `
        --no-watch-fs `
        --max-workers=1 `
        :app:processReleaseMainManifest `
        :app:assembleRelease `
        :app:dependencies `
        --configuration releaseRuntimeClasspath 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to generate release policy inputs:`n$($dependencyOutput -join [Environment]::NewLine)"
    }
    $dependencyReport = $dependencyOutput -join [Environment]::NewLine

    $manifestCandidates = @(
        Get-ChildItem -Path (Join-Path $ProjectRoot "app/build/intermediates") `
            -Filter "AndroidManifest.xml" -File -Recurse |
            Where-Object {
                $_.FullName -match '[\\/]merged_manifest[s]?[\\/]' -and
                $_.FullName -match '[\\/]release[\\/]'
            }
    )
    if ($manifestCandidates.Count -eq 0) {
        throw "Release merged manifest was not generated"
    }
    $MergedManifestPath = $manifestCandidates |
        Sort-Object LastWriteTimeUtc -Descending |
        Select-Object -First 1 -ExpandProperty FullName
    $ReleaseArtifactPath = Get-ChildItem -Path (Join-Path $ProjectRoot "app/build/outputs") `
        -Include "*.apk", "*.aab" -File -Recurse |
        Where-Object { $_.FullName -match '[\\/]release[\\/]' } |
        Sort-Object LastWriteTimeUtc -Descending |
        Select-Object -First 1 -ExpandProperty FullName
}
else {
    if (-not (Test-Path -LiteralPath $DependencyReportPath)) {
        throw "Missing release dependency report: $DependencyReportPath"
    }
    $dependencyReport = Get-Content -Raw -LiteralPath $DependencyReportPath
}

Assert-ReleaseManifestPolicy -Path $MergedManifestPath
Assert-ReleaseDependencyPolicy -Report $dependencyReport
Assert-ReleaseSourcePolicy -Root $ProjectRoot
Assert-ReleaseArtifactPolicy -Path $ReleaseArtifactPath

Write-Output "Release manifest and runtime dependency policy verification passed."

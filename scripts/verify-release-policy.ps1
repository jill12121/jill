param(
    [string]$ProjectRoot = (Split-Path -Parent $PSScriptRoot),
    [string]$MergedManifestPath,
    [string]$DependencyReportPath
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
    }

    foreach ($entry in $forbiddenPatterns.GetEnumerator()) {
        if ($Report -match $entry.Value) {
            throw "Forbidden release runtime dependency: $($entry.Key)"
        }
    }
}

$dependencyReport = $null
if ([string]::IsNullOrWhiteSpace($MergedManifestPath) -or
    [string]::IsNullOrWhiteSpace($DependencyReportPath)) {
    $isWindows = [System.Environment]::OSVersion.Platform -eq "Win32NT"
    $gradlePath = Join-Path $ProjectRoot $(if ($isWindows) { "gradlew.bat" } else { "gradlew" })
    if (-not (Test-Path -LiteralPath $gradlePath)) {
        throw "Missing Gradle Wrapper: $gradlePath"
    }

    $dependencyOutput = & $gradlePath `
        --no-daemon `
        --no-watch-fs `
        --max-workers=1 `
        :app:processReleaseMainManifest `
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
}
else {
    if (-not (Test-Path -LiteralPath $DependencyReportPath)) {
        throw "Missing release dependency report: $DependencyReportPath"
    }
    $dependencyReport = Get-Content -Raw -LiteralPath $DependencyReportPath
}

Assert-ReleaseManifestPolicy -Path $MergedManifestPath
Assert-ReleaseDependencyPolicy -Report $dependencyReport

Write-Output "Release manifest and runtime dependency policy verification passed."

$ErrorActionPreference = "Stop"

$verifyScript = Join-Path $PSScriptRoot "verify-release-policy.ps1"
$fixtureRoot = Join-Path ([System.IO.Path]::GetTempPath()) (
    "gallery-organizer-release-policy-" + [guid]::NewGuid()
)
$manifestPath = Join-Path $fixtureRoot "AndroidManifest.xml"
$dependencyReportPath = Join-Path $fixtureRoot "releaseRuntimeClasspath.txt"

function Write-ValidFixture {
    @'
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.READ_MEDIA_IMAGES" />
    <application android:allowBackup="false" />
</manifest>
'@ | Set-Content -NoNewline -LiteralPath $manifestPath

    @'
releaseRuntimeClasspath - Runtime classpath of release.
+--- androidx.activity:activity-compose:1.10.0
\--- com.google.dagger:hilt-android:2.52
'@ | Set-Content -NoNewline -LiteralPath $dependencyReportPath
}

function Assert-VerificationFails {
    param([string]$ExpectedMessage)

    $failure = $null
    try {
        & $verifyScript `
            -ProjectRoot $fixtureRoot `
            -MergedManifestPath $manifestPath `
            -DependencyReportPath $dependencyReportPath
    }
    catch {
        $failure = $_.Exception.Message
    }

    if ($null -eq $failure) {
        throw "Expected release policy verification to fail."
    }
    if ($failure -notmatch [regex]::Escape($ExpectedMessage)) {
        throw "Expected '$ExpectedMessage', got: $failure"
    }
}

New-Item -ItemType Directory -Force -Path $fixtureRoot | Out-Null

try {
    Write-ValidFixture
    & $verifyScript `
        -ProjectRoot $fixtureRoot `
        -MergedManifestPath $manifestPath `
        -DependencyReportPath $dependencyReportPath

    $manifest = Get-Content -Raw -LiteralPath $manifestPath
    $manifest = $manifest -replace '<application', (
        '<uses-permission android:name="android.permission.INTERNET" />' +
        [Environment]::NewLine +
        '    <application'
    )
    Set-Content -NoNewline -LiteralPath $manifestPath -Value $manifest
    Assert-VerificationFails -ExpectedMessage "Forbidden release manifest permission"

    Write-ValidFixture
    $manifest = Get-Content -Raw -LiteralPath $manifestPath
    $manifest = $manifest -replace 'android:allowBackup="false"', 'android:allowBackup="true"'
    Set-Content -NoNewline -LiteralPath $manifestPath -Value $manifest
    Assert-VerificationFails -ExpectedMessage "android:allowBackup must be false"

    Write-ValidFixture
    Add-Content -LiteralPath $dependencyReportPath `
        -Value "`n\--- com.squareup.okhttp3:okhttp:4.12.0"
    Assert-VerificationFails -ExpectedMessage "Forbidden release runtime dependency: OkHttp"

    $forbiddenTestDependencies = @(
        @{
            Coordinate = "org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0"
            PolicyName = "Kotlin Coroutines Test"
        },
        @{ Coordinate = "androidx.room:room-testing:2.6.1"; PolicyName = "Room Testing" },
        @{ Coordinate = "org.robolectric:robolectric:4.14.1"; PolicyName = "Robolectric" },
        @{ Coordinate = "junit:junit:4.13.2"; PolicyName = "JUnit" },
        @{
            Coordinate = "com.google.dagger:hilt-android-testing:2.52"
            PolicyName = "Hilt Android Testing"
        },
        @{ Coordinate = "com.example:metadata-test-fixtures:1.0"; PolicyName = "Test Fixtures" },
        @{ Coordinate = "com.galleryorganizer:data-fake:1.0"; PolicyName = "Fake Artifact" }
    )
    foreach ($dependency in $forbiddenTestDependencies) {
        Write-ValidFixture
        Add-Content -LiteralPath $dependencyReportPath `
            -Value "`n\--- $($dependency.Coordinate)"
        Assert-VerificationFails `
            -ExpectedMessage "Forbidden release runtime dependency: $($dependency.PolicyName)"
    }

    Write-ValidFixture
    $fakeSourcePath = Join-Path $fixtureRoot (
        "data/src/main/kotlin/com/galleryorganizer/data/ReleaseBindings.kt"
    )
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $fakeSourcePath) |
        Out-Null
    @'
package com.galleryorganizer.data

import com.galleryorganizer.data.fake.FakeWorkspaceGateway
'@ | Set-Content -NoNewline -LiteralPath $fakeSourcePath
    Assert-VerificationFails -ExpectedMessage "Forbidden release source Fake reference"

    Write-Output "Release policy guard tests passed."
}
finally {
    Remove-Item -Recurse -Force -LiteralPath $fixtureRoot -ErrorAction SilentlyContinue
}

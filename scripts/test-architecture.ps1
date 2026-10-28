$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
$verifyScript = Join-Path $PSScriptRoot "verify-architecture.ps1"
$fixtureRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("gallery-organizer-architecture-" + [guid]::NewGuid())

function Assert-VerificationFails {
    param([string]$ExpectedMessage)

    $failure = $null
    try {
        & $verifyScript -ProjectRoot $fixtureRoot
    }
    catch {
        $failure = $_.Exception.Message
    }

    if ($null -eq $failure) {
        throw "Expected architecture verification to fail."
    }
    if ($failure -notmatch [regex]::Escape($ExpectedMessage)) {
        throw "Expected '$ExpectedMessage', got: $failure"
    }
}

try {
    New-Item -ItemType Directory -Force -Path $fixtureRoot | Out-Null
    $fixtureFiles = @(
        Get-Item -LiteralPath (Join-Path $projectRoot "settings.gradle.kts")
        Get-ChildItem -LiteralPath (Join-Path $projectRoot "core") -Recurse -File
        Get-ChildItem -LiteralPath (Join-Path $projectRoot "domain") -Recurse -File
        Get-ChildItem -LiteralPath (Join-Path $projectRoot "data") -Recurse -File
        Get-ChildItem -LiteralPath (Join-Path $projectRoot "presentation") -Recurse -File
        Get-ChildItem -LiteralPath (Join-Path $projectRoot "app/src/main/java") -Recurse -File
        Get-Item -LiteralPath (Join-Path $projectRoot "app/build.gradle.kts")
        Get-Item -LiteralPath (Join-Path $projectRoot "spikes/probe-suite/build.gradle.kts")
        Get-Item -LiteralPath (Join-Path $projectRoot "spikes/probe-suite/src/main/AndroidManifest.xml")
        Get-Item -LiteralPath (Join-Path $projectRoot "spikes/s02-fixture-helper/build.gradle.kts")
        Get-Item -LiteralPath (Join-Path $projectRoot "spikes/s02-fixture-helper/src/main/AndroidManifest.xml")
        Get-Item -LiteralPath (Join-Path $projectRoot "spikes/s05-thumbnail/build.gradle.kts")
        Get-Item -LiteralPath (Join-Path $projectRoot "spikes/s05-thumbnail-harness/build.gradle.kts")
        Get-Item -LiteralPath (Join-Path $projectRoot "spikes/s05-thumbnail-harness/src/main/AndroidManifest.xml")
    ) | Where-Object { $_.FullName -notmatch '[\\/]+build[\\/]' }
    foreach ($file in $fixtureFiles) {
        $relativePath = $file.FullName.Substring($projectRoot.Length).TrimStart('\')
        $destination = Join-Path $fixtureRoot $relativePath
        New-Item -ItemType Directory -Force -Path (Split-Path -Parent $destination) | Out-Null
        Copy-Item -LiteralPath $file.FullName -Destination $destination
    }

    & $verifyScript -ProjectRoot $fixtureRoot

    $domainPath = Join-Path $fixtureRoot "domain/src/main/kotlin/com/galleryorganizer/domain/OrganizerUseCases.kt"
    Add-Content -LiteralPath $domainPath -Value "`nimport android.os.Bundle"
    Assert-VerificationFails -ExpectedMessage "Domain must not import Android, Compose, or Room"

    Copy-Item -Force -LiteralPath `
        (Join-Path $projectRoot "domain/src/main/kotlin/com/galleryorganizer/domain/OrganizerUseCases.kt") `
        -Destination $domainPath
    Add-Content -LiteralPath $domainPath -Value "`nval unsafe = java.io.File(`"unsafe`")"
    Assert-VerificationFails -ExpectedMessage "F02 must not contain real file I/O"

    Copy-Item -Force -LiteralPath `
        (Join-Path $projectRoot "domain/src/main/kotlin/com/galleryorganizer/domain/OrganizerUseCases.kt") `
        -Destination $domainPath
    $presentationPath = Join-Path $fixtureRoot "presentation/src/main/kotlin/com/galleryorganizer/presentation/AppScreen.kt"
    Add-Content -LiteralPath $presentationPath -Value "`nimport com.galleryorganizer.contract.SourceReader"
    Assert-VerificationFails -ExpectedMessage "Presentation must not import port contracts directly"

    Copy-Item -Force -LiteralPath `
        (Join-Path $projectRoot "presentation/src/main/kotlin/com/galleryorganizer/presentation/AppScreen.kt") `
        -Destination $presentationPath
    $useCases = Get-Content -Raw -LiteralPath $domainPath
    $useCases = $useCases -replace 'interface Scanner \{', "interface Scanner {`n    val sourceReader: SourceReader"
    Set-Content -NoNewline -LiteralPath $domainPath -Value $useCases
    Assert-VerificationFails -ExpectedMessage "Scanner must not expose a port dependency"

    Copy-Item -Force -LiteralPath `
        (Join-Path $projectRoot "domain/src/main/kotlin/com/galleryorganizer/domain/OrganizerUseCases.kt") `
        -Destination $domainPath
    $appBindingsPath = Join-Path $fixtureRoot "app/src/main/java/com/galleryorganizer/app/di/AppBindingsModule.kt"
    Add-Content -LiteralPath $appBindingsPath `
        -Value "`nval leakedFake: FakeSourceReader? = null"
    Assert-VerificationFails -ExpectedMessage "Production source must not depend on test fakes"
    Copy-Item -Force -LiteralPath `
        (Join-Path $projectRoot "app/src/main/java/com/galleryorganizer/app/di/AppBindingsModule.kt") `
        -Destination $appBindingsPath

    $appBuildPath = Join-Path $fixtureRoot "app/build.gradle.kts"
    Add-Content -LiteralPath $appBuildPath `
        -Value "`ndependencies { implementation(project(`":spikes:s02-fixture-helper`")) }"
    Assert-VerificationFails -ExpectedMessage "Production app must not depend on spike modules"
    Copy-Item -Force -LiteralPath (Join-Path $projectRoot "app/build.gradle.kts") `
        -Destination $appBuildPath

    $dataBuildPath = Join-Path $fixtureRoot "data/build.gradle.kts"
    Add-Content -LiteralPath $dataBuildPath `
        -Value "`ndependencies { implementation(project(`":spikes:s02-mediastore`")) }"
    Assert-VerificationFails -ExpectedMessage "Production modules must not depend on spike modules"
    Copy-Item -Force -LiteralPath (Join-Path $projectRoot "data/build.gradle.kts") `
        -Destination $dataBuildPath

    $helperApplicationId = "com.galleryorganizer.spikes.s02.fixturehelper"
    $probeSuiteBuildPath = Join-Path $fixtureRoot "spikes/probe-suite/build.gradle.kts"
    $probeSuiteBuild = Get-Content -Raw -LiteralPath $probeSuiteBuildPath
    $probeSuiteBuild = $probeSuiteBuild -replace `
        'applicationId\s*=\s*"com\.galleryorganizer\.spikes\.suite"', `
        "applicationId = `"$helperApplicationId`""
    Set-Content -NoNewline -LiteralPath $probeSuiteBuildPath -Value $probeSuiteBuild
    Assert-VerificationFails -ExpectedMessage "Probe suite and fixture helper must use distinct application IDs"
    Copy-Item -Force -LiteralPath `
        (Join-Path $projectRoot "spikes/probe-suite/build.gradle.kts") `
        -Destination $probeSuiteBuildPath

    Add-Content -LiteralPath $probeSuiteBuildPath `
        -Value "`ndependencies { implementation(project(`":spikes:s02-fixture-helper`")) }"
    Assert-VerificationFails `
        -ExpectedMessage "Probe suite must not depend on the external fixture helper APK"
    Copy-Item -Force -LiteralPath `
        (Join-Path $projectRoot "spikes/probe-suite/build.gradle.kts") `
        -Destination $probeSuiteBuildPath

    $s05BuildPath = Join-Path $fixtureRoot "spikes/s05-thumbnail/build.gradle.kts"
    Add-Content -LiteralPath $s05BuildPath `
        -Value "`ndependencies { implementation(project(`":data`")) }"
    Assert-VerificationFails `
        -ExpectedMessage "S05.1 contract module must remain dependency-free Kotlin/JVM"
    Copy-Item -Force -LiteralPath `
        (Join-Path $projectRoot "spikes/s05-thumbnail/build.gradle.kts") `
        -Destination $s05BuildPath

    $s05HarnessManifestPath = Join-Path $fixtureRoot (
        "spikes/s05-thumbnail-harness/src/main/AndroidManifest.xml"
    )
    $s05HarnessManifest = Get-Content -Raw -LiteralPath $s05HarnessManifestPath
    $s05HarnessManifest = $s05HarnessManifest -replace `
        '<manifest xmlns:android="http://schemas.android.com/apk/res/android">', `
        '$0<uses-permission android:name="android.permission.READ_MEDIA_IMAGES" />'
    Set-Content -NoNewline -LiteralPath $s05HarnessManifestPath -Value $s05HarnessManifest
    Assert-VerificationFails `
        -ExpectedMessage "S05 harness must not request permissions or share an Android UID"

    Write-Output "Architecture guard tests passed."
}
finally {
    Remove-Item -Recurse -Force -LiteralPath $fixtureRoot -ErrorAction SilentlyContinue
}

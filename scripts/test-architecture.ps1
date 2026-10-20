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

    Write-Output "Architecture guard tests passed."
}
finally {
    Remove-Item -Recurse -Force -LiteralPath $fixtureRoot -ErrorAction SilentlyContinue
}

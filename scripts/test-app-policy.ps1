$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
$verifyScript = Join-Path $PSScriptRoot "verify-app-policy.ps1"

function Assert-VerificationFails {
    param(
        [string]$FixtureRoot,
        [string]$ExpectedMessage
    )

    $failure = $null
    try {
        & $verifyScript -ProjectRoot $FixtureRoot
    }
    catch {
        $failure = $_.Exception.Message
    }

    if ($null -eq $failure) {
        throw "Expected policy verification to fail."
    }
    if ($failure -notmatch [regex]::Escape($ExpectedMessage)) {
        throw "Expected '$ExpectedMessage', got: $failure"
    }
}

$fixtureRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("gallery-organizer-policy-" + [guid]::NewGuid())
New-Item -ItemType Directory -Force -Path `
    (Join-Path $fixtureRoot "app/src/main"), `
    (Join-Path $fixtureRoot "app/src/main/res/xml"), `
    (Join-Path $fixtureRoot "gradle") | Out-Null
Copy-Item -LiteralPath (Join-Path $projectRoot "app/src/main/AndroidManifest.xml") `
    -Destination (Join-Path $fixtureRoot "app/src/main/AndroidManifest.xml")
Copy-Item -LiteralPath (Join-Path $projectRoot "gradle/libs.versions.toml") `
    -Destination (Join-Path $fixtureRoot "gradle/libs.versions.toml")
Copy-Item -LiteralPath (Join-Path $projectRoot "app/build.gradle.kts") `
    -Destination (Join-Path $fixtureRoot "app/build.gradle.kts")
Copy-Item -LiteralPath (Join-Path $projectRoot "app/src/main/res/xml/data_extraction_rules.xml") `
    -Destination (Join-Path $fixtureRoot "app/src/main/res/xml/data_extraction_rules.xml")
Copy-Item -LiteralPath (Join-Path $projectRoot "app/src/main/res/xml/backup_rules.xml") `
    -Destination (Join-Path $fixtureRoot "app/src/main/res/xml/backup_rules.xml")

try {
    & $verifyScript -ProjectRoot $fixtureRoot

    $manifestPath = Join-Path $fixtureRoot "app/src/main/AndroidManifest.xml"
    $manifest = Get-Content -Raw -LiteralPath $manifestPath
    $replacement = "<uses-permission android:name=`"android.permission.INTERNET`" />`n    <application"
    $manifest = $manifest -replace '<application', $replacement
    Set-Content -NoNewline -LiteralPath $manifestPath -Value $manifest
    Assert-VerificationFails -FixtureRoot $fixtureRoot -ExpectedMessage "Forbidden manifest permission"

    Copy-Item -Force -LiteralPath (Join-Path $projectRoot "app/src/main/AndroidManifest.xml") -Destination $manifestPath
    $catalogPath = Join-Path $fixtureRoot "gradle/libs.versions.toml"
    Add-Content -LiteralPath $catalogPath -Value "`nnetwork-fixture = { module = `"com.squareup.retrofit2:retrofit`", version = `"2.11.0`" }"
    Assert-VerificationFails -FixtureRoot $fixtureRoot -ExpectedMessage "Forbidden network dependency token"

    Copy-Item -Force -LiteralPath (Join-Path $projectRoot "gradle/libs.versions.toml") -Destination $catalogPath
    $catalog = Get-Content -Raw -LiteralPath $catalogPath
    Set-Content -NoNewline -LiteralPath $catalogPath -Value ($catalog -replace 'minSdk = "30"', 'minSdk = "29"')
    Assert-VerificationFails -FixtureRoot $fixtureRoot -ExpectedMessage "minSdk must remain 30"

    Copy-Item -Force -LiteralPath (Join-Path $projectRoot "gradle/libs.versions.toml") -Destination $catalogPath
    $backupRulesPath = Join-Path $fixtureRoot "app/src/main/res/xml/backup_rules.xml"
    $backupRules = Get-Content -Raw -LiteralPath $backupRulesPath
    Set-Content -NoNewline -LiteralPath $backupRulesPath -Value ($backupRules -replace 'path="."', 'path="files"')
    Assert-VerificationFails -FixtureRoot $fixtureRoot -ExpectedMessage "Backup rules must exclude the app root"

    Write-Output "App policy guard tests passed."
}
finally {
    Remove-Item -Recurse -Force -LiteralPath $fixtureRoot -ErrorAction SilentlyContinue
}

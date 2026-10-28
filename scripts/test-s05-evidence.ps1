$ErrorActionPreference = "Stop"

$verifyScript = Join-Path $PSScriptRoot "verify-s05-evidence.ps1"
$fixtureRoot = Join-Path ([System.IO.Path]::GetTempPath()) (
    "gallery-organizer-s05-evidence-" + [guid]::NewGuid()
)

function New-EvidenceJson {
    param(
        [string]$Fingerprint = ("1" * 64),
        [string]$Decision = "PASS",
        [int]$FailedFetches = 0,
        [bool]$OomObserved = $false,
        [int]$Schema = 2,
        [int]$FrameSamples = 360,
        [int]$FetchSamples = 637
    )

    [ordered]@{
        schema = $Schema
        deviceManufacturer = "iQOO"
        deviceModel = "V2304A"
        deviceApiLevel = 34
        deviceRelease = "14"
        deviceSecurityPatch = "2024-06-01"
        deviceFingerprintSha256 = $Fingerprint
        storage = "filesDir/s05-thumbnail-fixtures"
        fixtureCount = 96
        fixtureEdgePx = 256
        fixtureSeed = 520
        fixtureBytes = 123456
        fixtureSha256 = ("a" * 64)
        frameSamples = $FrameSamples
        fetchSamples = $FetchSamples
        failedFetches = $FailedFetches
        peakHeapBytes = 33554432
        frameP95Millis = 16
        fetchP95Millis = 6
        oomObserved = $OomObserved
        decision = $Decision
        violations = ""
    } | ConvertTo-Json
}

function Write-EvidenceRow {
    param([string]$Row, [string]$Json)

    Set-Content -NoNewline -LiteralPath (Join-Path $fixtureRoot "$Row.json") -Value $Json
}

function Remove-EvidenceRow {
    param([string]$Row)

    Remove-Item -Force -LiteralPath (Join-Path $fixtureRoot "$Row.json")
}

function Invoke-Verifier {
    (& $verifyScript -EvidenceRoot $fixtureRoot) -join "`n"
}

function Assert-VerificationFails {
    param([string]$ExpectedMessage)

    $failure = $null
    try {
        Invoke-Verifier | Out-Null
    }
    catch {
        $failure = $_.Exception.Message
    }

    if ($null -eq $failure) {
        throw "Expected S05 evidence verification to fail."
    }
    if ($failure -notmatch [regex]::Escape($ExpectedMessage)) {
        throw "Expected '$ExpectedMessage', got: $failure"
    }
}

function Assert-VerificationReports {
    param([string]$ExpectedText)

    $output = Invoke-Verifier
    if ($output -notmatch [regex]::Escape($ExpectedText)) {
        throw "Expected '$ExpectedText', got: $output"
    }
}

try {
    New-Item -ItemType Directory -Force -Path $fixtureRoot | Out-Null

    Assert-VerificationReports -ExpectedText "S05 device matrix: PENDING (0/4 rows"

    Write-EvidenceRow -Row "android11" -Json (New-EvidenceJson)
    Assert-VerificationReports -ExpectedText "android11: PASS"
    Assert-VerificationReports -ExpectedText "PENDING (1/4 rows"

    Copy-Item -Force -LiteralPath (Join-Path $fixtureRoot "android11.json") `
        -Destination (Join-Path $fixtureRoot "oem-a.json")
    Assert-VerificationFails -ExpectedMessage "must bind distinct physical devices"
    Remove-EvidenceRow -Row "oem-a"

    Write-EvidenceRow -Row "android11" -Json (New-EvidenceJson -Schema 1)
    Assert-VerificationFails -ExpectedMessage "must use harness schema 2"

    Write-EvidenceRow -Row "android11" -Json (New-EvidenceJson -Fingerprint "raw-fingerprint")
    Assert-VerificationFails -ExpectedMessage "SHA-256 build fingerprint hash"

    Write-EvidenceRow -Row "android11" -Json (New-EvidenceJson -FailedFetches 1)
    Assert-VerificationFails -ExpectedMessage "claims PASS with failed fetches"

    Write-EvidenceRow -Row "android11" -Json (New-EvidenceJson -OomObserved $true)
    Assert-VerificationFails -ExpectedMessage "claims PASS with an observed OOM"

    Write-EvidenceRow -Row "android11" -Json (New-EvidenceJson -FrameSamples 12)
    Assert-VerificationFails -ExpectedMessage "claims PASS without 300 frame samples"

    $leaked = (New-EvidenceJson) -replace '"schema"\s*:\s*2', '"schema": 2, "androidId": "deadbeef"'
    Write-EvidenceRow -Row "android11" -Json $leaked
    Assert-VerificationFails -ExpectedMessage "contains forbidden field 'androidId'"

    $leaked = (New-EvidenceJson) -replace '"schema"\s*:\s*2', `
        '"schema": 2, "note": "brand/model/device:14/UQ1A:user/release-keys"'
    Write-EvidenceRow -Row "android11" -Json $leaked
    Assert-VerificationFails -ExpectedMessage "leaks a raw build fingerprint"

    Write-EvidenceRow -Row "android11" -Json (New-EvidenceJson -Fingerprint ("2" * 64))
    Write-EvidenceRow -Row "target" -Json (New-EvidenceJson -Fingerprint ("3" * 64))
    Write-EvidenceRow -Row "oem-a" -Json (New-EvidenceJson -Fingerprint ("4" * 64))
    Write-EvidenceRow -Row "oem-b" -Json (
        New-EvidenceJson -Fingerprint ("5" * 64) -Decision "FAIL" -FailedFetches 2
    )
    Assert-VerificationReports -ExpectedText "S05 device matrix: FAIL (4/4 rows)"

    Write-EvidenceRow -Row "oem-b" -Json (New-EvidenceJson -Fingerprint ("5" * 64))
    Assert-VerificationReports -ExpectedText "S05 device matrix: GO (4/4 rows)"

    Write-Output "S05 evidence guard tests passed."
}
finally {
    Remove-Item -Recurse -Force -LiteralPath $fixtureRoot -ErrorAction SilentlyContinue
}

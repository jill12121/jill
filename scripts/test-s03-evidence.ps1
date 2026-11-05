$ErrorActionPreference = "Stop"

$verifyScript = Join-Path $PSScriptRoot "verify-s03-evidence.ps1"
$fixtureRoot = Join-Path ([System.IO.Path]::GetTempPath()) (
    "gallery-organizer-s03-evidence-" + [guid]::NewGuid()
)

function New-EvidenceJson {
    param(
        [string]$Fingerprint = ("1" * 64),
        [bool]$GmsCoreInstalled = $true,
        [string]$ModelDelivery = "BUNDLED",
        [bool]$InternetPermissionDeclared = $false,
        [int]$Schema = 4,
        [int]$Matched = 200,
        [long]$P95Millis = 40,
        [string]$Decision = "PASS",
        [string]$Violations = ""
    )
    $evidence = [ordered]@{
        schema = $Schema
        modelDelivery = $ModelDelivery
        internetPermissionDeclared = $InternetPermissionDeclared
        corpusVersion = $(if ($Schema -eq 4) { "real-font-paired-v2" } else { "pixel-5x7-v1" })
        corpusStorage = "filesDir/s03-ocr-fixtures"
        corpusSampleCount = 100
        corpusBytes = $(if ($Schema -eq 4) { 420093 } else { 12345 })
        corpusSha256 = $(if ($Schema -eq 4) {
            "80243d0a8efbe735be2ac244d2f55b25a9f3046a0a3443c8396c734ab78f8a16"
        } else { "a" * 64 })
        deviceManufacturer = "Example"
        deviceModel = "Midrange"
        deviceApiLevel = 34
        deviceRelease = "14"
        deviceSecurityPatch = "2026-01-01"
        gmsCoreInstalled = $GmsCoreInstalled
        deviceFingerprintSha256 = $Fingerprint
        sampleCount = 100
        expectedTokenCount = 200
        recognizedTokenCount = 200
        matchedTokenCount = $Matched
        failedSampleCount = 0
        precisionPermille = $Matched * 5
        recallPermille = $Matched * 5
        p95Millis = $P95Millis
        runCompleted = $true
        decision = $Decision
        violations = $Violations
    }
    if ($Schema -ge 3) {
        $numericMatched = [math]::Max(0, $Matched - 100)
        $evidence.labelMatchedCount = [math]::Min(100, $Matched)
        $evidence.numericMatchedCount = $numericMatched
        $evidence.bothMatchedSampleCount = $numericMatched
        $evidence.labelOnlySampleCount = $evidence.labelMatchedCount - $numericMatched
        $evidence.numericOnlySampleCount = 0
        $evidence.neitherMatchedSampleCount = 100 - $evidence.labelMatchedCount
    }
    if ($Schema -eq 4) {
        $evidence.corpusPairCount = 50
        $evidence.corpusFontName = "Noto Sans Regular"
        $evidence.corpusFontLicense = "SIL Open Font License 1.1"
        $evidence.corpusFontSha256 = (
            "bfb7bb691513f12e734dc346c03a03f784912432d7e3fa8e56efcf906fe86b3d"
        )
        $plainMatched = [math]::Min(50, $numericMatched)
        $paddedMatched = [math]::Max(0, $numericMatched - 50)
        $pairBoth = [math]::Min($plainMatched, $paddedMatched)
        $evidence.plainSampleCount = 50
        $evidence.paddedSampleCount = 50
        $evidence.plainNumericMatchedCount = $plainMatched
        $evidence.paddedNumericMatchedCount = $paddedMatched
        $evidence.completedPairCount = 50
        $evidence.bothNumericMatchedPairCount = $pairBoth
        $evidence.plainOnlyNumericMatchedPairCount = $plainMatched - $pairBoth
        $evidence.paddedOnlyNumericMatchedPairCount = $paddedMatched - $pairBoth
        $evidence.neitherNumericMatchedPairCount = (
            50 - $plainMatched - $paddedMatched + $pairBoth
        )
    }
    $evidence | ConvertTo-Json
}

function Write-Row {
    param([string]$Row, [string]$Json)
    Set-Content -NoNewline -LiteralPath (Join-Path $fixtureRoot "$Row.json") -Value $Json
}
function Invoke-Verifier { (& $verifyScript -EvidenceRoot $fixtureRoot) -join "`n" }
function Assert-Reports {
    param([string]$Text)
    $output = Invoke-Verifier
    if ($output -notmatch [regex]::Escape($Text)) {
        throw "Expected '$Text', got: $output"
    }
}
function Assert-Fails {
    param([string]$Text)
    $failure = $null
    try { Invoke-Verifier | Out-Null } catch { $failure = $_.Exception.Message }
    if ($null -eq $failure -or $failure -notmatch [regex]::Escape($Text)) {
        throw "Expected failure '$Text', got: $failure"
    }
}

try {
    New-Item -ItemType Directory -Force -Path $fixtureRoot | Out-Null
    Assert-Reports "S03 device matrix: PENDING (0/2 rows"

    Write-Row "middle-tier" (New-EvidenceJson)
    Assert-Reports "middle-tier: PASS"

    Write-Row "middle-tier" (New-EvidenceJson -Schema 1)
    Assert-Fails "must use harness schema 2, 3, or 4"
    Write-Row "middle-tier" (New-EvidenceJson -Schema 2)
    Assert-Reports "middle-tier: PASS"
    Write-Row "middle-tier-schema4" (New-EvidenceJson)
    Assert-Reports "paired numeric: plain 50; padded 50"
    Remove-Item -LiteralPath (Join-Path $fixtureRoot "middle-tier-schema4.json")
    Write-Row "middle-tier-schema3" (New-EvidenceJson -Schema 3)
    Assert-Reports "diagnostics: label 100; numeric 100"
    Remove-Item -LiteralPath (Join-Path $fixtureRoot "middle-tier-schema3.json")
    Write-Row "middle-tier" (New-EvidenceJson -InternetPermissionDeclared $true)
    Assert-Fails "must prove bundled offline recognition"
    Write-Row "middle-tier" (New-EvidenceJson -ModelDelivery "REMOTE")
    Assert-Fails "must prove bundled offline recognition"
    Write-Row "middle-tier" (New-EvidenceJson -GmsCoreInstalled $false)
    Assert-Fails "does not match its required GMS role"
    $wrongType = (New-EvidenceJson) -replace '"gmsCoreInstalled"\s*:\s*true', `
        '"gmsCoreInstalled": "true"'
    Write-Row "middle-tier" $wrongType
    Assert-Fails "has invalid boolean field types"
    Write-Row "middle-tier" (New-EvidenceJson -Matched 189)
    Assert-Fails "has an inconsistent decision"
    Write-Row "middle-tier" (
        New-EvidenceJson -Matched 189 -Decision "FAIL" `
            -Violations "PRECISION_BUDGET,RECALL_BUDGET"
    )
    Assert-Reports "S03 device matrix: FAIL (1/2 rows; missing: no-gms)"

    $badDiagnostic = (New-EvidenceJson) -replace `
        '"numericMatchedCount"\s*:\s*100', '"numericMatchedCount": 99'
    Write-Row "middle-tier" $badDiagnostic
    Assert-Fails "has inconsistent diagnostic aggregates"

    $badPair = (New-EvidenceJson) -replace `
        '"paddedNumericMatchedCount"\s*:\s*50', '"paddedNumericMatchedCount": 49'
    Write-Row "middle-tier" $badPair
    Assert-Fails "has inconsistent paired diagnostics"

    $unknown = (New-EvidenceJson) -replace '"schema"\s*:\s*4', `
        '"schema": 4, "recognizedText": "ITEM 007"'
    Write-Row "middle-tier" $unknown
    Assert-Fails "leaks forbidden raw data"

    Write-Row "middle-tier" (New-EvidenceJson -Fingerprint ("2" * 64))
    Write-Row "no-gms" (New-EvidenceJson -Fingerprint ("2" * 64) -GmsCoreInstalled $false)
    Assert-Fails "must bind distinct physical devices"

    Write-Row "no-gms" (
        New-EvidenceJson -Fingerprint ("3" * 64) -GmsCoreInstalled $false `
            -Matched 189 -Decision "FAIL" -Violations "PRECISION_BUDGET,RECALL_BUDGET"
    )
    Assert-Reports "S03 device matrix: FAIL (2/2 rows)"

    Write-Row "no-gms" (
        New-EvidenceJson -Fingerprint ("3" * 64) -GmsCoreInstalled $false
    )
    Assert-Reports "S03 device matrix: GO (2/2 rows)"
    Write-Output "S03 evidence guard tests passed."
}
finally {
    Remove-Item -Recurse -Force -LiteralPath $fixtureRoot -ErrorAction SilentlyContinue
}

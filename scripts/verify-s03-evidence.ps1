param(
    [string]$EvidenceRoot = (
        Join-Path (Split-Path -Parent $PSScriptRoot) "docs/evidence/s03"
    )
)

$ErrorActionPreference = "Stop"

$requiredRows = [ordered]@{
    "middle-tier" = $true
    "no-gms" = $false
}
$baseRequiredFields = @(
    "schema", "modelDelivery", "internetPermissionDeclared",
    "corpusVersion", "corpusStorage", "corpusSampleCount", "corpusBytes",
    "corpusSha256", "deviceManufacturer", "deviceModel", "deviceApiLevel",
    "deviceRelease", "deviceSecurityPatch", "gmsCoreInstalled",
    "deviceFingerprintSha256", "sampleCount", "expectedTokenCount",
    "recognizedTokenCount", "matchedTokenCount", "failedSampleCount",
    "precisionPermille", "recallPermille", "p95Millis", "runCompleted",
    "decision", "violations"
)
$diagnosticFields = @(
    "labelMatchedCount", "numericMatchedCount", "bothMatchedSampleCount",
    "labelOnlySampleCount", "numericOnlySampleCount", "neitherMatchedSampleCount"
)
$realFontCorpusFields = @(
    "corpusPairCount", "corpusFontName", "corpusFontLicense", "corpusFontSha256"
)
$pairedDiagnosticFields = @(
    "plainSampleCount", "paddedSampleCount", "plainNumericMatchedCount",
    "paddedNumericMatchedCount", "completedPairCount",
    "bothNumericMatchedPairCount", "plainOnlyNumericMatchedPairCount",
    "paddedOnlyNumericMatchedPairCount", "neitherNumericMatchedPairCount"
)
$forbiddenFragments = @(
    ":user/", "release-keys", "dev-keys", "test-keys",
    "expectedText", "recognizedText", "rawOcrText"
)

function Get-Rate {
    param([long]$Numerator, [long]$Denominator)
    if ($Denominator -eq 0) { return 0 }
    return [int][math]::Floor(($Numerator * 1000) / $Denominator)
}

function Get-ExpectedClassification {
    param($Evidence)

    $violations = @()
    if (-not $Evidence.runCompleted) { $violations += "RUN_INCOMPLETE" }
    if ($Evidence.sampleCount -lt 100) { $violations += "INSUFFICIENT_SAMPLES" }
    if ($violations.Count -gt 0) {
        return [pscustomobject]@{ Decision = "PENDING"; Violations = $violations -join "," }
    }
    if ($Evidence.failedSampleCount -gt 0) { $violations += "SAMPLE_FAILURE_BUDGET" }
    if ($Evidence.precisionPermille -lt 950) { $violations += "PRECISION_BUDGET" }
    if ($Evidence.recallPermille -lt 950) { $violations += "RECALL_BUDGET" }
    if ($Evidence.p95Millis -gt 750) { $violations += "P95_BUDGET" }
    return [pscustomobject]@{
        Decision = $(if ($violations.Count -eq 0) { "PASS" } else { "FAIL" })
        Violations = $violations -join ","
    }
}

function Read-EvidenceRow {
    param([string]$Row, [string]$Path, [bool]$ExpectedGmsCore)

    $raw = Get-Content -Raw -LiteralPath $Path
    try {
        $evidence = $raw | ConvertFrom-Json
    }
    catch {
        throw "S03 evidence row '$Row' is not valid JSON."
    }
    foreach ($fragment in $forbiddenFragments) {
        if ($raw.Contains($fragment)) {
            throw "S03 evidence row '$Row' leaks forbidden raw data."
        }
    }

    if ($evidence.schema -notin @(2, 3, 4)) {
        throw "S03 evidence row '$Row' must use harness schema 2, 3, or 4."
    }
    $requiredFields = $baseRequiredFields
    if ($evidence.schema -ge 3) {
        $requiredFields += $diagnosticFields
    }
    if ($evidence.schema -eq 4) {
        $requiredFields += $realFontCorpusFields + $pairedDiagnosticFields
    }
    $names = @($evidence.PSObject.Properties.Name)
    foreach ($field in $requiredFields) {
        if ($names -notcontains $field) {
            throw "S03 evidence row '$Row' is missing field '$field'."
        }
    }
    $unknown = @($names | Where-Object { $requiredFields -notcontains $_ })
    if ($unknown.Count -gt 0) {
        throw "S03 evidence row '$Row' contains unknown field '$($unknown[0])'."
    }

    if (
        $evidence.internetPermissionDeclared -isnot [bool] -or
        $evidence.gmsCoreInstalled -isnot [bool] -or
        $evidence.runCompleted -isnot [bool]
    ) {
        throw "S03 evidence row '$Row' has invalid boolean field types."
    }
    if ($evidence.modelDelivery -ne "BUNDLED" -or $evidence.internetPermissionDeclared) {
        throw "S03 evidence row '$Row' must prove bundled offline recognition."
    }
    $oldCorpusValid = (
        $evidence.schema -in @(2, 3) -and
        $evidence.corpusVersion -eq "pixel-5x7-v1" -and
        $evidence.corpusStorage -eq "filesDir/s03-ocr-fixtures" -and
        $evidence.corpusSampleCount -eq 100 -and
        $evidence.corpusBytes -gt 0 -and
        [string]$evidence.corpusSha256 -match '^[0-9a-f]{64}$'
    )
    $realFontCorpusValid = (
        $evidence.schema -eq 4 -and
        $evidence.corpusVersion -eq "real-font-paired-v2" -and
        $evidence.corpusStorage -eq "filesDir/s03-ocr-fixtures" -and
        $evidence.corpusSampleCount -eq 100 -and
        $evidence.corpusBytes -eq 420093 -and
        $evidence.corpusSha256 -eq "80243d0a8efbe735be2ac244d2f55b25a9f3046a0a3443c8396c734ab78f8a16" -and
        $evidence.corpusPairCount -eq 50 -and
        $evidence.corpusFontName -eq "Noto Sans Regular" -and
        $evidence.corpusFontLicense -eq "SIL Open Font License 1.1" -and
        $evidence.corpusFontSha256 -eq "bfb7bb691513f12e734dc346c03a03f784912432d7e3fa8e56efcf906fe86b3d"
    )
    if (-not $oldCorpusValid -and -not $realFontCorpusValid) {
        throw "S03 evidence row '$Row' has invalid corpus provenance."
    }
    foreach ($field in @(
        "deviceManufacturer", "deviceModel", "deviceRelease", "deviceSecurityPatch"
    )) {
        if (
            [string]::IsNullOrWhiteSpace([string]$evidence.$field) -or
            [string]$evidence.$field -eq "unknown"
        ) {
            throw "S03 evidence row '$Row' has invalid device provenance."
        }
    }
    if (
        $evidence.deviceApiLevel -lt 30 -or
        [string]$evidence.deviceFingerprintSha256 -notmatch '^[0-9a-f]{64}$'
    ) {
        throw "S03 evidence row '$Row' has invalid device provenance."
    }
    if ([bool]$evidence.gmsCoreInstalled -ne $ExpectedGmsCore) {
        throw "S03 evidence row '$Row' does not match its required GMS role."
    }

    if (
        $evidence.sampleCount -lt 0 -or $evidence.sampleCount -gt 100 -or
        $evidence.expectedTokenCount -ne ($evidence.sampleCount * 2) -or
        $evidence.recognizedTokenCount -lt 0 -or
        $evidence.matchedTokenCount -lt 0 -or
        $evidence.matchedTokenCount -gt $evidence.expectedTokenCount -or
        $evidence.matchedTokenCount -gt $evidence.recognizedTokenCount -or
        $evidence.failedSampleCount -lt 0 -or
        $evidence.failedSampleCount -gt $evidence.sampleCount -or
        $evidence.p95Millis -lt 0
    ) {
        throw "S03 evidence row '$Row' has invalid aggregate metrics."
    }
    if ($evidence.schema -ge 3) {
        $diagnosticValues = @($diagnosticFields | ForEach-Object { [int]$evidence.$_ })
        if (
            @($diagnosticValues | Where-Object { $_ -lt 0 }).Count -gt 0 -or
            $evidence.labelMatchedCount -ne (
                $evidence.bothMatchedSampleCount + $evidence.labelOnlySampleCount
            ) -or
            $evidence.numericMatchedCount -ne (
                $evidence.bothMatchedSampleCount + $evidence.numericOnlySampleCount
            ) -or
            $evidence.matchedTokenCount -ne (
                $evidence.labelMatchedCount + $evidence.numericMatchedCount
            ) -or
            $evidence.sampleCount -ne (
                $evidence.bothMatchedSampleCount + $evidence.labelOnlySampleCount +
                $evidence.numericOnlySampleCount + $evidence.neitherMatchedSampleCount
            )
        ) {
            throw "S03 evidence row '$Row' has inconsistent diagnostic aggregates."
        }
    }
    if ($evidence.schema -eq 4) {
        $pairedValues = @($pairedDiagnosticFields | ForEach-Object { [int]$evidence.$_ })
        if (
            @($pairedValues | Where-Object { $_ -lt 0 }).Count -gt 0 -or
            $evidence.plainSampleCount -ne 50 -or
            $evidence.paddedSampleCount -ne 50 -or
            $evidence.sampleCount -ne (
                $evidence.plainSampleCount + $evidence.paddedSampleCount
            ) -or
            $evidence.numericMatchedCount -ne (
                $evidence.plainNumericMatchedCount +
                $evidence.paddedNumericMatchedCount
            ) -or
            $evidence.completedPairCount -ne 50 -or
            $evidence.completedPairCount -ne (
                $evidence.bothNumericMatchedPairCount +
                $evidence.plainOnlyNumericMatchedPairCount +
                $evidence.paddedOnlyNumericMatchedPairCount +
                $evidence.neitherNumericMatchedPairCount
            ) -or
            $evidence.plainNumericMatchedCount -ne (
                $evidence.bothNumericMatchedPairCount +
                $evidence.plainOnlyNumericMatchedPairCount
            ) -or
            $evidence.paddedNumericMatchedCount -ne (
                $evidence.bothNumericMatchedPairCount +
                $evidence.paddedOnlyNumericMatchedPairCount
            )
        ) {
            throw "S03 evidence row '$Row' has inconsistent paired diagnostics."
        }
    }
    $precision = Get-Rate $evidence.matchedTokenCount $evidence.recognizedTokenCount
    $recall = Get-Rate $evidence.matchedTokenCount $evidence.expectedTokenCount
    if (
        $evidence.precisionPermille -ne $precision -or
        $evidence.recallPermille -ne $recall
    ) {
        throw "S03 evidence row '$Row' has inconsistent aggregate rates."
    }
    $expected = Get-ExpectedClassification $evidence
    if (
        $evidence.decision -ne $expected.Decision -or
        [string]$evidence.violations -ne $expected.Violations
    ) {
        throw "S03 evidence row '$Row' has an inconsistent decision."
    }

    [pscustomobject]@{
        Row = $Row
        State = [string]$evidence.decision
        Device = "$($evidence.deviceManufacturer) $($evidence.deviceModel) API $($evidence.deviceApiLevel)"
        Fingerprint = [string]$evidence.deviceFingerprintSha256
        Precision = [int]$evidence.precisionPermille
        Recall = [int]$evidence.recallPermille
        P95 = [long]$evidence.p95Millis
        Schema = [int]$evidence.schema
        LabelMatched = $(if ($evidence.schema -ge 3) { [int]$evidence.labelMatchedCount } else { $null })
        NumericMatched = $(if ($evidence.schema -ge 3) { [int]$evidence.numericMatchedCount } else { $null })
        PlainNumericMatched = $(if ($evidence.schema -eq 4) { [int]$evidence.plainNumericMatchedCount } else { $null })
        PaddedNumericMatched = $(if ($evidence.schema -eq 4) { [int]$evidence.paddedNumericMatchedCount } else { $null })
    }
}

$rows = @()
$missing = @()
foreach ($row in $requiredRows.Keys) {
    $paths = @(
        (Join-Path $EvidenceRoot "$row-schema4.json"),
        (Join-Path $EvidenceRoot "$row-schema3.json"),
        (Join-Path $EvidenceRoot "$row.json")
    )
    $path = $paths | Where-Object { Test-Path -LiteralPath $_ } | Select-Object -First 1
    if ($null -eq $path) {
        $missing += $row
        continue
    }
    $rows += Read-EvidenceRow $row $path $requiredRows[$row]
}
$duplicates = $rows | Group-Object Fingerprint | Where-Object Count -gt 1
if ($duplicates) {
    throw "S03 evidence rows must bind distinct physical devices."
}
foreach ($row in $rows) {
    Write-Output ("{0}: {1} ({2}; precision {3}; recall {4}; P95 {5}ms)" -f `
        $row.Row, $row.State, $row.Device, $row.Precision, $row.Recall, $row.P95)
    if ($row.Schema -ge 3) {
        Write-Output ("  diagnostics: label {0}; numeric {1}" -f `
            $row.LabelMatched, $row.NumericMatched)
    }
    if ($row.Schema -eq 4) {
        Write-Output ("  paired numeric: plain {0}; padded {1}" -f `
            $row.PlainNumericMatched, $row.PaddedNumericMatched)
    }
}
if (@($rows | Where-Object State -ne "PASS").Count -gt 0) {
    if ($missing.Count -gt 0) {
        Write-Output ("S03 device matrix: FAIL ({0}/2 rows; missing: {1})" -f `
            $rows.Count, ($missing -join ", "))
    }
    else {
        Write-Output "S03 device matrix: FAIL (2/2 rows)"
    }
}
elseif ($missing.Count -gt 0) {
    Write-Output ("S03 device matrix: PENDING ({0}/2 rows; missing: {1})" -f `
        $rows.Count, ($missing -join ", "))
}
else {
    Write-Output "S03 device matrix: GO (2/2 rows)"
}

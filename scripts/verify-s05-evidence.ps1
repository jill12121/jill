param(
    [string]$EvidenceRoot = (
        Join-Path (Split-Path -Parent $PSScriptRoot) "docs/evidence/s05"
    )
)

$ErrorActionPreference = "Stop"

$requiredRows = [ordered]@{
    "android11" = "Android 11"
    "target" = "current target"
    "oem-a" = "OEM family A"
    "oem-b" = "OEM family B"
}

$requiredFields = @(
    "schema",
    "deviceManufacturer",
    "deviceModel",
    "deviceApiLevel",
    "deviceRelease",
    "deviceSecurityPatch",
    "deviceFingerprintSha256",
    "storage",
    "fixtureCount",
    "fixtureEdgePx",
    "fixtureSeed",
    "fixtureBytes",
    "fixtureSha256",
    "frameSamples",
    "fetchSamples",
    "failedFetches",
    "peakHeapBytes",
    "frameP95Millis",
    "fetchP95Millis",
    "oomObserved",
    "decision",
    "violations"
)

$forbiddenFields = @("fingerprint", "androidId", "serialNo", "imei", "macAddress")
$forbiddenFragments = @(":user/", "release-keys", "dev-keys", "test-keys")

$expectedFixtureCount = 96
$expectedFixtureEdgePx = 256
$expectedFixtureSeed = 520
$minimumFrameSamples = 300
$minimumFetchSamples = 96
$minimumApiLevel = 30

function Read-EvidenceRow {
    param([string]$Row, [string]$Path)

    $raw = Get-Content -Raw -LiteralPath $Path
    $evidence = $null
    try {
        $evidence = $raw | ConvertFrom-Json
    }
    catch {
        throw "S05 evidence row '$Row' is not valid JSON."
    }

    foreach ($fragment in $forbiddenFragments) {
        if ($raw.Contains($fragment)) {
            throw "S05 evidence row '$Row' leaks a raw build fingerprint."
        }
    }

    $names = $evidence.PSObject.Properties.Name
    foreach ($field in $requiredFields) {
        if ($names -notcontains $field) {
            throw "S05 evidence row '$Row' is missing field '$field'."
        }
    }
    foreach ($field in $forbiddenFields) {
        if ($names -contains $field) {
            throw "S05 evidence row '$Row' contains forbidden field '$field'."
        }
    }

    if ($evidence.schema -ne 2) {
        throw "S05 evidence row '$Row' must use harness schema 2."
    }
    foreach ($field in @("deviceManufacturer", "deviceModel", "deviceRelease", "deviceSecurityPatch")) {
        if ([string]::IsNullOrWhiteSpace([string]$evidence.$field)) {
            throw "S05 evidence row '$Row' has an empty '$field'."
        }
    }
    if ($evidence.deviceApiLevel -lt $minimumApiLevel) {
        throw "S05 evidence row '$Row' reports API level below the S05 minimum."
    }
    if ([string]$evidence.deviceFingerprintSha256 -notmatch '^[0-9a-f]{64}$') {
        throw "S05 evidence row '$Row' must expose only a SHA-256 build fingerprint hash."
    }
    if ($evidence.fixtureCount -ne $expectedFixtureCount) {
        throw "S05 evidence row '$Row' must report $expectedFixtureCount fixtures."
    }
    if ($evidence.fixtureEdgePx -ne $expectedFixtureEdgePx) {
        throw "S05 evidence row '$Row' must report $($expectedFixtureEdgePx)px fixture edges."
    }
    if ($evidence.fixtureSeed -ne $expectedFixtureSeed) {
        throw "S05 evidence row '$Row' must report fixture seed $expectedFixtureSeed."
    }
    if ($evidence.decision -notin @("PASS", "FAIL", "PENDING", "INVALID")) {
        throw "S05 evidence row '$Row' has an unknown decision."
    }
    if ($evidence.fetchSamples -lt 0 -or $evidence.frameSamples -lt 0) {
        throw "S05 evidence row '$Row' reports negative sample counts."
    }
    if ($evidence.failedFetches -gt $evidence.fetchSamples) {
        throw "S05 evidence row '$Row' reports more failures than fetch samples."
    }
    if ($evidence.decision -eq "PASS") {
        if ($evidence.oomObserved) {
            throw "S05 evidence row '$Row' claims PASS with an observed OOM."
        }
        if ($evidence.failedFetches -gt 0) {
            throw "S05 evidence row '$Row' claims PASS with failed fetches."
        }
        if ($evidence.frameSamples -lt $minimumFrameSamples) {
            throw "S05 evidence row '$Row' claims PASS without $minimumFrameSamples frame samples."
        }
        if ($evidence.fetchSamples -lt $minimumFetchSamples) {
            throw "S05 evidence row '$Row' claims PASS without $minimumFetchSamples fetch samples."
        }
    }

    [pscustomobject]@{
        Row = $Row
        State = [string]$evidence.decision
        Device = "$($evidence.deviceManufacturer) $($evidence.deviceModel) API $($evidence.deviceApiLevel)"
        Fingerprint = [string]$evidence.deviceFingerprintSha256
        FrameP95Millis = [long]$evidence.frameP95Millis
        FetchP95Millis = [long]$evidence.fetchP95Millis
        PeakHeapBytes = [long]$evidence.peakHeapBytes
    }
}

$rows = @()
$missing = @()
foreach ($row in $requiredRows.Keys) {
    $path = Join-Path $EvidenceRoot "$row.json"
    if (-not (Test-Path -LiteralPath $path)) {
        $missing += $row
        continue
    }
    $rows += Read-EvidenceRow -Row $row -Path $path
}

$duplicates = $rows | Group-Object -Property Fingerprint | Where-Object { $_.Count -gt 1 }
if ($duplicates) {
    $names = ($duplicates | ForEach-Object { $_.Group.Row }) -join ", "
    throw "S05 evidence rows must bind distinct physical devices: $names"
}

foreach ($row in $rows) {
    Write-Output ("{0}: {1} ({2}; frame P95 {3}ms; fetch P95 {4}ms; peak heap {5} bytes)" -f `
        $row.Row, $row.State, $row.Device, $row.FrameP95Millis, $row.FetchP95Millis, $row.PeakHeapBytes)
}

if ($missing.Count -gt 0) {
    Write-Output ("S05 device matrix: PENDING ({0}/{1} rows; missing: {2})" -f `
        $rows.Count, $requiredRows.Count, ($missing -join ", "))
}
elseif (@($rows | Where-Object { $_.State -ne "PASS" }).Count -gt 0) {
    Write-Output ("S05 device matrix: FAIL ({0}/{1} rows)" -f $rows.Count, $requiredRows.Count)
}
else {
    Write-Output ("S05 device matrix: GO ({0}/{1} rows)" -f $rows.Count, $requiredRows.Count)
}

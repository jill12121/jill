param(
    [string]$ProjectRoot = (Split-Path -Parent $PSScriptRoot)
)

$ErrorActionPreference = "Stop"

$manifestPath = Join-Path $ProjectRoot "app/src/main/AndroidManifest.xml"
$catalogPath = Join-Path $ProjectRoot "gradle/libs.versions.toml"
$appBuildPath = Join-Path $ProjectRoot "app/build.gradle.kts"
$dataExtractionRulesPath = Join-Path $ProjectRoot "app/src/main/res/xml/data_extraction_rules.xml"
$backupRulesPath = Join-Path $ProjectRoot "app/src/main/res/xml/backup_rules.xml"

foreach ($path in @(
    $manifestPath,
    $catalogPath,
    $appBuildPath,
    $dataExtractionRulesPath,
    $backupRulesPath
)) {
    if (-not (Test-Path -LiteralPath $path)) {
        throw "Missing required project file: $path"
    }
}

[xml]$manifest = Get-Content -Raw -LiteralPath $manifestPath
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

$foundPermission = $permissions | Where-Object { $_ -in $forbiddenPermissions } | Select-Object -First 1
if ($foundPermission) {
    throw "Forbidden manifest permission: $foundPermission"
}

$application = $manifest.SelectSingleNode("/manifest/application")
if ($application.GetAttribute("allowBackup", $androidNamespace) -ne "false") {
    throw "android:allowBackup must be false"
}
if ($application.GetAttribute("dataExtractionRules", $androidNamespace) -ne "@xml/data_extraction_rules") {
    throw "android:dataExtractionRules must reference @xml/data_extraction_rules"
}
if ($application.GetAttribute("fullBackupContent", $androidNamespace) -ne "@xml/backup_rules") {
    throw "android:fullBackupContent must reference @xml/backup_rules"
}

foreach ($path in @($dataExtractionRulesPath, $backupRulesPath)) {
    [xml]$rules = Get-Content -Raw -LiteralPath $path
    $rootExclusion = $rules.SelectSingleNode("//*[@domain='root' and @path='.']")
    if ($null -eq $rootExclusion) {
        throw "Backup rules must exclude the app root: $path"
    }
}

$catalog = Get-Content -Raw -LiteralPath $catalogPath
$appBuild = Get-Content -Raw -LiteralPath $appBuildPath
if ($catalog -notmatch '(?m)^minSdk\s*=\s*"30"$') {
    throw "minSdk must remain 30"
}

$forbiddenDependencyTokens = @(
    "retrofit",
    "okhttp",
    "ktor-client",
    "volley",
    "fuel"
)

foreach ($token in $forbiddenDependencyTokens) {
    if ($catalog -match [regex]::Escape($token) -or
        $appBuild -match [regex]::Escape($token)) {
        throw "Forbidden network dependency token: $token"
    }
}

Write-Output "App policy verification passed."

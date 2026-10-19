param(
    [string]$SpecRoot = (Join-Path (Split-Path -Parent $PSScriptRoot) "docs/spec")
)

$ErrorActionPreference = "Stop"

$requiredFiles = [ordered]@{
    "README.md" = "25CFEE97DFCE85BB75775099FF967CCAC9F10CBA170A82F7530F2F390AA240C7"
    "01_product_prd_v0.3.1.md" = "3EA3859832EFF5C87F97CBEA6CAAFEA0F3BFF9A62E408D4F202B7E348B9D145F"
    "02_technical_architecture_v0.3.1.md" = "708CE2604808C6C62930896B9763213F42AFF4BD77BC45A4732FA11ED77C938F"
    "03_delivery_plan_v0.3.1.md" = "BDD03E626F492EA9FD74515C9E164A18342A496370437124E082AA3E9353D10E"
    "04_adrs_v0.3.1.md" = "2932BD137B651F635E2714C6966A34E93A839CDD411BD6EB8EA1216D3EAAABC0"
    "05_work_agent_handoff_v0.3.1.yaml" = "9DA9C268177299E617E3A6FC58894263960F7C2F0CF11A14BDE5BCC92B4F8DC8"
    "06_compose_ui_spec_v0.3.1.yaml" = "D689D7C6B8E67DFBEB7F8A44804AC09D80140A4023526F561AA4408FA0293756"
    "07_contract_tables_v0.3.1.md" = "B87136B206736D405BD5A6A298C915D7C8BC1F25147BB5AAAF959CFAA6571B85"
    "08_execution_playbook_v0.3.1.md" = "A510D590B853FF7C288D7F78891FD6DB8AF32521476ED5D3E60F53DA3EDF6B99"
}

foreach ($fileName in $requiredFiles.Keys) {
    $path = Join-Path $SpecRoot $fileName
    if (-not (Test-Path -LiteralPath $path)) {
        throw "Missing baseline specification: $fileName"
    }
    $actualHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $path).Hash
    if ($actualHash -ne $requiredFiles[$fileName]) {
        throw "Baseline specification hash mismatch: $fileName"
    }
}

Write-Output "Baseline specification verification passed."

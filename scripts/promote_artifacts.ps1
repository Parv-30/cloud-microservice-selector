param(
    [Parameter(Mandatory = $true)]
    [string]$SourceDir
)

$ErrorActionPreference = "Stop"

function Assert-True($condition, $message) {
    if (-not $condition) { throw $message }
}

$root = "d:\Cloud Project 2"
$targetDir = Join-Path $root "ml\artifacts"
$requiredFiles = @("model.onnx", "model_config.json", "tokenizer.json")
$expectedLabels = @(
    "add_v1","add_v2","add_v3",
    "divide_v1","divide_v2","divide_v3",
    "multiply_v1","multiply_v2","multiply_v3",
    "subtract_v1","subtract_v2","subtract_v3"
)

$resolvedSource = (Resolve-Path $SourceDir).Path
Assert-True (Test-Path $resolvedSource) "SourceDir not found: $SourceDir"
New-Item -ItemType Directory -Path $targetDir -Force | Out-Null
$sameDir = [string]::Equals(
    ($resolvedSource.TrimEnd('\\')),
    ((Resolve-Path $targetDir).Path.TrimEnd('\\')),
    [System.StringComparison]::OrdinalIgnoreCase
)

foreach ($f in $requiredFiles) {
    Assert-True (Test-Path (Join-Path $resolvedSource $f)) "Missing required artifact: $f"
}

$configPath = Join-Path $resolvedSource "model_config.json"
$config = Get-Content $configPath -Raw | ConvertFrom-Json

Assert-True ($null -ne $config.max_len) "model_config.json missing max_len"
Assert-True ($null -ne $config.id2label) "model_config.json missing id2label"

$actual = @($config.id2label.PSObject.Properties | ForEach-Object { $_.Value }) | Sort-Object
$expected = $expectedLabels | Sort-Object
Assert-True (($actual.Count -eq 12) -and (@(Compare-Object $actual $expected).Count -eq 0)) "model_config id2label must contain exactly expected 12 labels"

if (-not $sameDir) {
    Copy-Item (Join-Path $resolvedSource "model.onnx") (Join-Path $targetDir "model.onnx") -Force
    Copy-Item (Join-Path $resolvedSource "model_config.json") (Join-Path $targetDir "model_config.json") -Force
    Copy-Item (Join-Path $resolvedSource "tokenizer.json") (Join-Path $targetDir "tokenizer.json") -Force
}

Write-Host "Artifacts promoted to $targetDir"

Set-Location $root
& docker compose up -d --build router-agent | Out-Host
Start-Sleep -Seconds 6
& docker compose ps | Out-Host

& "$root\scripts\smoke_test.ps1" | Out-Host
Write-Host "Promotion and validation complete."

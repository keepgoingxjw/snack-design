param([string]$DataPath = "")
$ErrorActionPreference = "Stop"
$backend = Split-Path -Parent $PSScriptRoot
if ([string]::IsNullOrWhiteSpace($DataPath)) {
    $DataPath = if ($env:OCR_DATA_PATH) { $env:OCR_DATA_PATH } else { "data/tessdata" }
}
if (-not [IO.Path]::IsPathRooted($DataPath)) { $DataPath = Join-Path $backend $DataPath }
New-Item -ItemType Directory -Force -Path $DataPath | Out-Null
# Official tessdata_fast 4.1.0, Apache-2.0. Data files only, not executables or a generative model.
$models = @{
    "chi_sim" = "a5fcb6f0db1e1d6d8522f39db4e848f05984669172e584e8d76b6b3141e1f730"
}
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
foreach ($language in $models.Keys) {
    $file = Join-Path $DataPath ($language + ".traineddata")
    if (Test-Path -LiteralPath $file) {
        if ((Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash.ToLowerInvariant() -ne $models[$language]) {
            throw "Existing OCR language file has a different checksum; not overwritten: $file"
        }
        Write-Output "OCR language ready: $language"
        continue
    }
    $temporary = $file + ".download"
    try {
        $url = "https://raw.githubusercontent.com/tesseract-ocr/tessdata_fast/4.1.0/$language.traineddata?download=1"
        Invoke-WebRequest -UseBasicParsing -Uri $url -OutFile $temporary -TimeoutSec 120
        if ((Get-FileHash -LiteralPath $temporary -Algorithm SHA256).Hash.ToLowerInvariant() -ne $models[$language]) {
            throw "OCR language checksum mismatch: $language"
        }
        Move-Item -LiteralPath $temporary -Destination $file
        Write-Output "OCR language installed: $language"
    } finally {
        if (Test-Path -LiteralPath $temporary) { Remove-Item -LiteralPath $temporary -Force }
    }
}
Write-Output "Traditional OCR ready. No DeepSeek key or cloud AI call required."

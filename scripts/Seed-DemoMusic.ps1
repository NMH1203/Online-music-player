[CmdletBinding()]
param([switch]$VerifyOnly, [switch]$DownloadOnly, [int]$Limit = 0)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
if ($VerifyOnly -and $DownloadOnly) { throw 'Choose either VerifyOnly or DownloadOnly.' }
if ($Limit -lt 0) { throw 'Limit must be zero (all tracks) or positive.' }
$projectDirectory = Split-Path -Parent $PSScriptRoot
$pythonPath = Join-Path $projectDirectory '.venv/Scripts/python.exe'
Push-Location $projectDirectory
try {
    if (-not (Test-Path -LiteralPath $pythonPath)) {
        & python -m venv --without-pip .venv
        if ($LASTEXITCODE -ne 0) { throw 'Install Python 3.12 with pip, then retry.' }
    }
    & $pythonPath -c 'import minio, mutagen, psycopg' 2>$null
    if ($LASTEXITCODE -ne 0) {
        & python -m pip --python $pythonPath install -r scripts/requirements-seed.txt
        if ($LASTEXITCODE -ne 0) { throw 'Dependency installation failed. Requires Python pip 22.3 or newer.' }
    }
    if (-not $DownloadOnly -and -not $VerifyOnly) {
        & docker compose up -d db
        if ($LASTEXITCODE -ne 0) { throw 'PostgreSQL startup failed.' }
        & docker compose run --rm migrate
        if ($LASTEXITCODE -ne 0) { throw 'Database migration failed.' }
    }
    $seedArguments = @('scripts/seed_music.py')
    if ($VerifyOnly) { $seedArguments += '--verify-only' }
    if ($DownloadOnly) { $seedArguments += '--download-only' }
    if ($Limit -gt 0) { $seedArguments += @('--limit', [string]$Limit) }
    & $pythonPath @seedArguments
    if ($LASTEXITCODE -ne 0) { throw 'Music seed/verification failed; see the error above.' }
    if (-not $DownloadOnly) {
        $readerArguments = @('scripts/setup_storage_reader.py')
        if ($VerifyOnly) { $readerArguments += '--verify-only' }
        & $pythonPath @readerArguments
        if ($LASTEXITCODE -ne 0) { throw 'Backend storage account setup/verification failed.' }
    }
}
finally { Pop-Location }

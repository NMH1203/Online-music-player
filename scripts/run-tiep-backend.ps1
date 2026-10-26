param([switch]$Demo)
$ErrorActionPreference = 'Stop'
$repoPath = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
if (-not $env:JAVA_HOME) {
    $studioJdk = Join-Path $env:ProgramFiles 'Android\Android Studio\jbr'
    if (Test-Path -LiteralPath $studioJdk) { $env:JAVA_HOME = $studioJdk }
}
if (-not $env:AUTH_JWT_SECRET) {
    $runtimePath = Join-Path $repoPath 'runtime'
    New-Item -ItemType Directory -Path $runtimePath -Force | Out-Null
    $secretPath = Join-Path $runtimePath 'auth-jwt-secret.local'
    if (-not (Test-Path -LiteralPath $secretPath)) {
        $secretBytes = New-Object byte[] 48
        $random = [System.Security.Cryptography.RandomNumberGenerator]::Create()
        try { $random.GetBytes($secretBytes) } finally { $random.Dispose() }
        [System.IO.File]::WriteAllText($secretPath, [Convert]::ToBase64String($secretBytes))
    }
    $env:AUTH_JWT_SECRET = [System.IO.File]::ReadAllText($secretPath).Trim()
}
$storageEnv = Join-Path $repoPath 'storage\backend.env'
if (Test-Path -LiteralPath $storageEnv) {
    Get-Content -LiteralPath $storageEnv | ForEach-Object {
        if ($_ -match '^(MINIO_ENDPOINT|MINIO_REGION|MINIO_BUCKET|MINIO_ACCESS_KEY|MINIO_SECRET_KEY)=(.*)$') {
            [Environment]::SetEnvironmentVariable($Matches[1], $Matches[2], 'Process')
        }
    }
}
if (-not $env:MINIO_ACCESS_KEY -or -not $env:MINIO_SECRET_KEY) {
    throw 'Configure MinIO using documents/minio-demo-setup.md, or set MINIO_ACCESS_KEY and MINIO_SECRET_KEY in this terminal.'
}
$env:DEMO_ENABLED = if ($Demo) { 'true' } else { 'false' }
Write-Host 'Starting backend. PostgreSQL and repository migrations must already be available.'
Write-Host ("Mock payment and reset enabled: " + $env:DEMO_ENABLED)
Push-Location -LiteralPath $repoPath
try {
    & (Join-Path $repoPath 'android\gradlew.bat') -p (Join-Path $repoPath 'backend') bootRun
    if ($LASTEXITCODE -ne 0) { throw "Backend exited with code $LASTEXITCODE" }
} finally { Pop-Location }

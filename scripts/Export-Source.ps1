param([string]$OutputPath = (Join-Path $env:TEMP 'online-music-player-source.zip'))
$ErrorActionPreference = 'Stop'
$repoPath = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$destination = [IO.Path]::GetFullPath($OutputPath)
if (Test-Path -LiteralPath $destination) { throw "Output already exists: $destination" }
# Include tracked source plus new source not ignored by Git. Never include local runtime/credentials.
$files = @(git -C $repoPath -c core.quotePath=false ls-files --cached --others --exclude-standard)
if ($LASTEXITCODE -ne 0) { throw 'Cannot enumerate source files' }
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [IO.Compression.ZipFile]::Open($destination, [IO.Compression.ZipArchiveMode]::Create)
try {
    foreach ($relative in ($files | Sort-Object -Unique)) {
        if ($relative -match '(^|/)(\.git|\.idea|\.gradle|build|runtime|\.venv)(/|$)' -or
            $relative -match '(^|/)(\.env($|\.(?!example$))|local\.properties$|backend\.env$|minio\.license$)' -or
            $relative -match '\.(jks|keystore|local)$') { continue }
        $absolute = [IO.Path]::GetFullPath((Join-Path $repoPath $relative))
        if (-not $absolute.StartsWith($repoPath + '\', [StringComparison]::OrdinalIgnoreCase)) { throw 'Path outside repository' }
        if (-not (Test-Path -LiteralPath $absolute -PathType Leaf)) { continue }
        if ((Get-Item -LiteralPath $absolute).Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'Refusing to package a linked file' }
        [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($archive, $absolute, $relative) | Out-Null
    }
} finally { $archive.Dispose() }
Write-Host "Source archive: $destination"

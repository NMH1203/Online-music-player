[CmdletBinding()]
param(
    [string]$Audio,
    [string]$Directory,
    [string]$Manifest,
    [string]$Title,
    [string]$Artist,
    [string]$ArtistBio,
    [string]$ArtistImage,
    [string]$Album,
    [string]$ReleaseDate,
    [string]$AlbumCover,
    [string]$Genre = "pop",
    [string]$GenreName,
    [double]$Popularity = 50
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$projectDirectory = Split-Path -Parent $PSScriptRoot
$pythonPath = Join-Path $projectDirectory '.venv/Scripts/python.exe'
if (-not (Test-Path -LiteralPath $pythonPath)) {
    $pythonPath = 'python'
}

$scriptArgs = @((Join-Path $PSScriptRoot 'import_custom_media.py'))
if ($Audio)       { $scriptArgs += @('--audio', $Audio) }
if ($Directory)   { $scriptArgs += @('--directory', $Directory) }
if ($Manifest)    { $scriptArgs += @('--manifest', $Manifest) }
if ($Title)       { $scriptArgs += @('--title', $Title) }
if ($Artist)      { $scriptArgs += @('--artist', $Artist) }
if ($ArtistBio)   { $scriptArgs += @('--artist-bio', $ArtistBio) }
if ($ArtistImage) { $scriptArgs += @('--artist-image', $ArtistImage) }
if ($Album)       { $scriptArgs += @('--album', $Album) }
if ($ReleaseDate) { $scriptArgs += @('--release-date', $ReleaseDate) }
if ($AlbumCover)  { $scriptArgs += @('--album-cover', $AlbumCover) }
if ($Genre)       { $scriptArgs += @('--genre', $Genre) }
if ($GenreName)   { $scriptArgs += @('--genre-name', $GenreName) }
$scriptArgs += @('--popularity', [string]$Popularity)

Push-Location $projectDirectory
try {
    $env:PYTHONIOENCODING = 'utf-8'
    & $pythonPath @scriptArgs
    if ($LASTEXITCODE -ne 0) {
        throw "Import failed with exit code $LASTEXITCODE"
    }
}
finally {
    Pop-Location
}

[CmdletBinding()]
param(
    [string]$LicensePath,
    [switch]$PrepareOnly,
    [string]$SecureCredentialFile,
    [switch]$SecureStorageDirectory
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$projectDirectory = Split-Path -Parent $PSScriptRoot
$storageDirectory = Join-Path $projectDirectory 'storage'
$environmentPath = Join-Path $storageDirectory '.env'
$backendEnvironmentPath = Join-Path $storageDirectory 'backend.env'
$storedLicensePath = Join-Path $storageDirectory 'minio.license'
$composePath = Join-Path $projectDirectory 'compose.minio.yaml'

function Set-PrivateFileAcl([string]$Path) {
    $owner = [System.Security.Principal.WindowsIdentity]::GetCurrent().User
    $acl = New-Object System.Security.AccessControl.FileSecurity
    $acl.SetOwner($owner)
    $acl.SetAccessRuleProtection($true, $false)
    foreach ($sidValue in @($owner.Value, 'S-1-5-18', 'S-1-5-32-544')) {
        $sid = New-Object System.Security.Principal.SecurityIdentifier($sidValue)
        $rule = New-Object System.Security.AccessControl.FileSystemAccessRule(
            $sid, [System.Security.AccessControl.FileSystemRights]::FullControl,
            [System.Security.AccessControl.AccessControlType]::Allow)
        $acl.AddAccessRule($rule)
    }
    # Set-Acl can request SeSecurityPrivilege on an already protected file.
    # The filesystem API writes this DACL without touching the audit ACL.
    if ([System.IO.File].GetMethods() | Where-Object Name -eq 'SetAccessControl') {
        [System.IO.File]::SetAccessControl($Path, $acl)
    }
    else {
        [System.IO.FileSystemAclExtensions]::SetAccessControl([System.IO.FileInfo]::new($Path), $acl)
    }
}

function Set-PrivateDirectoryAcl([string]$Path) {
    $owner = [System.Security.Principal.WindowsIdentity]::GetCurrent().User
    $acl = New-Object System.Security.AccessControl.DirectorySecurity
    $acl.SetOwner($owner)
    $acl.SetAccessRuleProtection($true, $false)
    $inheritance = [System.Security.AccessControl.InheritanceFlags]::ContainerInherit -bor [System.Security.AccessControl.InheritanceFlags]::ObjectInherit
    foreach ($sidValue in @($owner.Value, 'S-1-5-18', 'S-1-5-32-544')) {
        $sid = New-Object System.Security.Principal.SecurityIdentifier($sidValue)
        $rule = New-Object System.Security.AccessControl.FileSystemAccessRule(
            $sid, [System.Security.AccessControl.FileSystemRights]::FullControl,
            $inheritance, [System.Security.AccessControl.PropagationFlags]::None,
            [System.Security.AccessControl.AccessControlType]::Allow)
        $acl.AddAccessRule($rule)
    }
    if ([System.IO.Directory].GetMethods() | Where-Object Name -eq 'SetAccessControl') {
        [System.IO.Directory]::SetAccessControl($Path, $acl)
    }
    else {
        [System.IO.FileSystemAclExtensions]::SetAccessControl([System.IO.DirectoryInfo]::new($Path), $acl)
    }
}

if ($SecureStorageDirectory) {
    if ($LicensePath -or $PrepareOnly -or $SecureCredentialFile) { throw 'Use SecureStorageDirectory by itself.' }
    New-Item -ItemType Directory -Path $storageDirectory -Force | Out-Null
    Set-PrivateDirectoryAcl $storageDirectory
    return
}

if ($SecureCredentialFile) {
    if ($LicensePath -or $PrepareOnly) { throw 'Use SecureCredentialFile by itself.' }
    $targetPath = [System.IO.Path]::GetFullPath($SecureCredentialFile)
    if ($targetPath -notin @([System.IO.Path]::GetFullPath($environmentPath), [System.IO.Path]::GetFullPath($backendEnvironmentPath))) {
        throw 'SecureCredentialFile must be storage/.env or storage/backend.env.'
    }
    if (-not (Test-Path -LiteralPath $targetPath -PathType Leaf)) { throw 'Credential file does not exist.' }
    Set-PrivateDirectoryAcl $storageDirectory
    Set-PrivateFileAcl $targetPath
    return
}

function New-RandomHex([int]$ByteCount) {
    $randomBytes = New-Object byte[] $ByteCount
    $generator = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try { $generator.GetBytes($randomBytes) }
    finally { $generator.Dispose() }
    return ([System.BitConverter]::ToString($randomBytes)).Replace('-', '').ToLowerInvariant()
}

function Read-Settings([string]$Path) {
    $settings = @{}
    foreach ($line in [System.IO.File]::ReadAllLines($Path)) {
        if ($line -match '^\s*(?:#.*)?$') { continue }
        if ($line -notmatch '^([A-Z][A-Z0-9_]*)=(.*)$') {
            throw 'Invalid storage/.env format. Use one KEY=value per line, without spaces around =.'
        }
        $key = $Matches[1]
        $value = $Matches[2].Trim()
        if ($settings.ContainsKey($key)) { throw "Duplicate setting: $key" }
        if ($value.Length -ge 2 -and (($value.StartsWith("'") -and $value.EndsWith("'")) -or ($value.StartsWith('"') -and $value.EndsWith('"')))) {
            $value = $value.Substring(1, $value.Length - 2)
        }
        $settings[$key] = $value
    }
    return $settings
}

New-Item -ItemType Directory -Path $storageDirectory -Force | Out-Null
Set-PrivateDirectoryAcl $storageDirectory

if ($LicensePath) {
    $sourceLicense = Get-Item -LiteralPath $LicensePath -ErrorAction Stop
    if ($sourceLicense.PSIsContainer -or $sourceLicense.Length -eq 0) {
        throw 'LicensePath must point to a nonempty MinIO AIStor license file.'
    }
    if ($sourceLicense.FullName -ne $storedLicensePath) {
        Copy-Item -LiteralPath $sourceLicense.FullName -Destination $storedLicensePath -Force
    }
}
if (-not (Test-Path -LiteralPath $storedLicensePath -PathType Leaf)) {
    throw 'AIStor needs your license file. Run: .\scripts\Setup-Minio.ps1 -LicensePath D:\minio.license'
}
if ((Get-Item -LiteralPath $storedLicensePath).Length -eq 0) {
    throw 'storage/minio.license is empty. Supply a valid AIStor license using -LicensePath.'
}

if (-not (Test-Path -LiteralPath $environmentPath)) {
    $rootUser = 'music-admin-' + (New-RandomHex 8)
    $rootPassword = New-RandomHex 32
    $configuration = @(
        '# Local credentials generated by Setup-Minio.ps1. Do not commit this file.'
        "MINIO_ROOT_USER=$rootUser"
        "MINIO_ROOT_PASSWORD=$rootPassword"
        'MINIO_ENDPOINT=http://127.0.0.1:9000'
        'MINIO_REGION=us-east-1'
        'MINIO_BUCKET=music-demo'
        'MINIO_API_PORT=9000'
        'MINIO_CONSOLE_PORT=9001'
    )
    $stream = [System.IO.File]::Open($environmentPath, [System.IO.FileMode]::CreateNew, [System.IO.FileAccess]::Write, [System.IO.FileShare]::None)
    $stream.Dispose()
    try {
        Set-PrivateFileAcl $environmentPath
        [System.IO.File]::WriteAllLines($environmentPath, $configuration, (New-Object System.Text.UTF8Encoding($false)))
    }
    catch {
        Remove-Item -LiteralPath $environmentPath -Force -ErrorAction SilentlyContinue
        throw
    }
    Write-Host "Generated credentials in $environmentPath"
}

# Reapply permissions on every run, including credential files created earlier.
Set-PrivateFileAcl $environmentPath
if (Test-Path -LiteralPath $backendEnvironmentPath -PathType Leaf) {
    Set-PrivateFileAcl $backendEnvironmentPath
}

$settings = Read-Settings $environmentPath
foreach ($requiredKey in @('MINIO_ROOT_USER', 'MINIO_ROOT_PASSWORD', 'MINIO_ENDPOINT', 'MINIO_REGION', 'MINIO_BUCKET', 'MINIO_API_PORT', 'MINIO_CONSOLE_PORT')) {
    if (-not $settings.ContainsKey($requiredKey) -or [string]::IsNullOrWhiteSpace($settings[$requiredKey])) {
        throw "Missing or empty $requiredKey in storage/.env."
    }
}
if ($settings.MINIO_ROOT_USER.Length -lt 3 -or $settings.MINIO_ROOT_PASSWORD.Length -lt 8) {
    throw 'MINIO_ROOT_USER requires at least 3 characters and MINIO_ROOT_PASSWORD at least 8.'
}
if ($settings.MINIO_ROOT_USER -eq 'minioadmin' -and $settings.MINIO_ROOT_PASSWORD -eq 'minioadmin') {
    throw 'Replace the public default credentials in storage/.env with a unique username and password.'
}
foreach ($portKey in @('MINIO_API_PORT', 'MINIO_CONSOLE_PORT')) {
    $portNumber = 0
    if (-not [int]::TryParse($settings[$portKey], [ref]$portNumber) -or $portNumber -lt 1 -or $portNumber -gt 65535) {
        throw "$portKey must be a port number from 1 to 65535."
    }
}
if ($settings.MINIO_API_PORT -eq $settings.MINIO_CONSOLE_PORT) {
    throw 'MINIO_API_PORT and MINIO_CONSOLE_PORT must be different.'
}
if ($settings.MINIO_ENDPOINT -ne ('http://127.0.0.1:' + $settings.MINIO_API_PORT)) {
    throw 'Set MINIO_ENDPOINT to http://127.0.0.1:<MINIO_API_PORT> for this local Docker deployment.'
}

Write-Host "Configuration ready: $environmentPath"
Write-Host "AIStor license: $storedLicensePath"
if ($PrepareOnly) { return }

Get-Command docker -ErrorAction Stop | Out-Null
& docker compose version --short
if ($LASTEXITCODE -ne 0) { throw 'Docker Compose is unavailable. Start Docker Desktop and install/enable Compose.' }

# Quiet config validation never prints resolved credentials.
$composeArguments = @('compose', '--env-file', $environmentPath, '-f', $composePath)
& docker @composeArguments config --quiet
if ($LASTEXITCODE -ne 0) { throw 'The MinIO Compose configuration is invalid.' }
& docker @composeArguments up -d --wait --wait-timeout 180
if ($LASTEXITCODE -ne 0) {
    throw 'MinIO did not become healthy. Check Docker Desktop container logs for ports, license validity, or startup errors.'
}

Write-Host ('S3 API: http://127.0.0.1:' + $settings.MINIO_API_PORT)
Write-Host ('Console: http://localhost:' + $settings.MINIO_CONSOLE_PORT)
Write-Host "Read MINIO_ROOT_USER and MINIO_ROOT_PASSWORD in $environmentPath to sign in."

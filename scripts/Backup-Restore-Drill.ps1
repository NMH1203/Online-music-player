# T31: backup -> restore into a scratch database -> compare fingerprints.
# Run from the repo root:  .\scripts\Backup-Restore-Drill.ps1
# Needs: docker compose up -d (db healthy). Does NOT touch the live database.
$ErrorActionPreference = 'Stop'
$Db = 'music_player'; $User = 'music_app'; $Scratch = 'music_player_restore_test'
$Stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$Dump = ".\backups\music-player-$Stamp.dump"
New-Item -ItemType Directory -Force .\backups | Out-Null

function Get-Fingerprint($Name) {
  Get-Content -Raw -Encoding utf8 .\database\checks\state_fingerprint.sql |
    docker compose exec -T db psql -At -v ON_ERROR_STOP=1 -U $User -d $Name
}

Write-Host '1/5 Fingerprint of live DB'
$before = Get-Fingerprint $Db

Write-Host '2/5 Backup (pg_dump custom format)'
docker compose exec db pg_dump -U $User -d $Db -Fc -f /tmp/drill.dump
docker compose cp db:/tmp/drill.dump $Dump
$sha = (Get-FileHash $Dump -Algorithm SHA256).Hash
Write-Host "    $Dump  sha256=$sha"

Write-Host '3/5 Restore into scratch DB'
docker compose exec db psql -U $User -d postgres -c "DROP DATABASE IF EXISTS $Scratch"
docker compose exec db psql -U $User -d postgres -c "CREATE DATABASE $Scratch"
docker compose exec db pg_restore -U $User -d $Scratch --no-owner --exit-on-error /tmp/drill.dump

Write-Host '4/5 Compare fingerprints'
$after = Get-Fingerprint $Scratch
$diff = Compare-Object $before $after
docker compose exec db psql -U $User -d postgres -c "DROP DATABASE $Scratch"

Write-Host '5/5 Result'
if ($diff) { $diff | Format-Table; throw 'RESTORE DRILL FAILED: fingerprints differ' }
Write-Host "RESTORE DRILL PASSED  ($(Get-Date -Format s))  dump=$Dump  sha256=$sha" -ForegroundColor Green
Write-Host 'Reminder: this dump has no MinIO audio objects; back up the music-demo bucket separately.'

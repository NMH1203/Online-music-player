# Keeps `adb reverse tcp:8080 tcp:8080` on every connected phone, so a USB debug build reaches
# the backend at 127.0.0.1:8080 again after re-plugging the cable or an adb restart.
# Run from the repo root and leave the window open:  .\scripts\Watch-AdbReverse.ps1
param([int]$Port = 8080)

$adb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
if (-not (Test-Path -LiteralPath $adb)) { $adb = 'adb' }
Write-Host "Watching for phones; forwarding device tcp:$Port to this PC. Ctrl+C to stop."

while ($true) {
    $serials = & $adb devices | Select-String '^(\S+)\s+device$' | ForEach-Object { $_.Matches[0].Groups[1].Value }
    foreach ($serial in $serials) {
        if (-not ((& $adb -s $serial reverse --list) -match "tcp:$Port\s+tcp:$Port")) {
            & $adb -s $serial reverse "tcp:$Port" "tcp:$Port" | Out-Null
            Write-Host "$(Get-Date -Format HH:mm:ss) $serial -> tcp:$Port"
        }
    }
    Start-Sleep -Seconds 3
}

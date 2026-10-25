# T29 — Library and Playlist Regression Report

**Owner:** Nam
**Branch:** `T29_library-playlist-regression_Nam`
**Base:** pushed T21 (`5e0a1122ea0b731658e3ab98a95db1a967fde049`)
**Date:** 2026-10-24

## Automated results

| Area | Check | Result |
|---|---|---|
| Playlist quota | Free at 3 playlists cannot create another; Premium (`max_playlists = null`) remains unlimited | PASS |
| Playlist quota | A Free create attempt at the cap returns `409 playlist_limit_reached` and does not insert a row | PASS |
| Ownership | Another user receives `404 not_found` for playlist read, update, delete, track add, and track remove; no write is issued | PASS |
| Playlist tracks | Duplicate add is protected by the unique-key conflict clause; repeated remove is a no-op; timestamps change only for actual inserts/deletes | PASS |
| Favorites | Repeated favorite add/remove stays scoped to the requesting user | PASS |
| Android error handling | `401`, `404`, the playlist-limit `409`, an unrelated/malformed `409`, and `503` map to the intended failure categories | PASS |
| Android retry UI | On Pixel 7 API 35 with the backend unavailable, Library renders and exposes Retry after network failure | PASS |
| Android build | Debug APK assembles | PASS |

The backend tests use a stub `JdbcTemplate`; they do not connect to or modify a developer database. Android error-classification tests run on the local JVM. The instrumented test uses the Pixel 7 emulator with the backend unavailable. It caught and verified a fix for a zero-height navigation host when the mini-player is hidden. The Android test tasks also include the pre-existing template tests.

## Commands and outcome

```powershell
.\android\gradlew.bat -p .\backend test --no-daemon
.\android\gradlew.bat -p .\android testDebugUnitTest assembleDebug --no-daemon --offline --console=plain
.\android\gradlew.bat -p .\android connectedDebugAndroidTest --no-daemon --offline --console=plain
```

All commands completed with `BUILD SUCCESSFUL`. The backend suite ran 5 regression checks. Android local tests ran 4 new checks plus 1 existing template test; the Pixel 7 instrumented suite ran 1 Library retry check plus 1 existing context test. The debug APK assembled successfully.

## Manual follow-up: live backend and signed-in data separation

Two-user API behavior was not run end-to-end. The app/backend authentication integration is still a dependency, so authenticated `/me/...` requests cannot yet be exercised as two real users in this local run.

After authentication is integrated and the backend has a test account/catalog:

1. Sign in as user A, load Library, favorite a track, create a playlist, and add the track twice. Confirm the track appears once.
2. Remove the track twice and remove a favorite twice. Confirm the app remains stable and refreshes the final state.
3. On Pixel 7 API 35, a backend-unavailable Library request displayed Retry. With the backend and authentication configured, restore the connection and tap Retry; confirm Library data loads.
4. Sign in as user B. Confirm A's playlist is absent from B's Library. If a request is made with A's playlist ID, confirm the API returns `404` and exposes no playlist data.
5. For a Free account, create three playlists and verify the next create is blocked. For Premium, verify a playlist can still be created beyond that count.

The automated ownership and quota tests cover the service decisions without a live PostgreSQL/auth stack; the steps above are still needed to validate the full app-to-API experience.

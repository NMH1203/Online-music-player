# Online Music Player

An Android music streaming app built as the USTH Mobile App final project (team of 7, 3 weeks).
It covers the core of a Spotify-like experience on infrastructure the team runs itself:
sign in, browse and search a licensed demo catalog, stream with background playback,
like songs and albums, build playlists, see listening history and recommendations, and a
Free / Premium model with test ads and a **simulated** upgrade (no real payments).

## Features

- **Account** – register, sign in, sign out, session restore; edit display name and bio.
- **Home** – greeting, Recently played, For you (recommendations), New releases, Featured artists.
- **Search** – songs, artists and albums by name; recent searches kept on the device.
- **Player** – Now Playing, mini player, queue (jump to, remove, clear), seek, next/previous;
  keeps playing in the background with media notification, lock-screen and headset controls.
- **Library** – Liked songs, Liked albums, My playlists; create, rename and delete playlists, add and remove songs.
- **Listening history** – grouped by day; a listen counts after `min(30 s, 50 % of the track)`.
- **Recommendations** – genre 45 %, artist affinity 30 %, popularity 15 %, recency 10 %, with a cold-start mix for new users.
- **Free vs Premium** – Free: up to 3 playlists, 7 days of history, an app-open ad and an audio ad between songs.
  Premium: no ads, unlimited playlists, full history. Upgrade and reset are simulated on the server.

## Tech stack

| Part | Technology |
|---|---|
| Android app | Java, XML Views, Navigation, Retrofit/OkHttp, Media3 ExoPlayer + `MediaSessionService`, Glide |
| Backend | Java 17+, Spring Boot (modular monolith, REST/JSON), JWT HS256 access tokens + rotating refresh tokens |
| Database | PostgreSQL 16, schema managed by Flyway migrations |
| Media storage | MinIO (private bucket); the backend proxies audio with HTTP Range |

UI design: [Figma file](https://www.figma.com/design/LkFwEdETR7fe6nKQ3YpEVx/Sans-titre--Copy-?m=draw) – screen-by-screen status in [documents/ui-design-figma.md](documents/ui-design-figma.md).

## Repository layout

```text
Online-music-player/
├── android/        Android Studio project (module app) + Gradle wrapper used for everything
├── backend/        Spring Boot API              → see backend/README.md (API reference)
├── database/       Flyway migrations, SQL checks, demo catalog → see database/README.md
├── scripts/        PowerShell helpers: MinIO setup, music seeding, backend runner, backup drill
├── storage/        Example storage config; generated credentials/license live here (git-ignored)
├── documents/      Architecture, flows, database design, test reports, UI design notes
└── compose.yaml    PostgreSQL + Flyway + MinIO + Backend API
```

## Prerequisites

- Windows 10/11 with PowerShell (scripts are PowerShell; other OSes need the equivalent commands).
- Docker Desktop with Linux containers.
- JDK 17+ (Android Studio's bundled JBR works).
- Android Studio with SDK Platform 35, and a phone or emulator on Android 8.0 (API 26) or newer.
- Python 3.12 (only for seeding the demo music) and a valid MinIO AIStor license file.

## Quick start

Run everything from the repository root.

```powershell
# 1. First time only: copy the MinIO license, generate storage/.env + root .env, and seed 50 demo MP3s
.\scripts\Setup-Minio.ps1 -LicensePath 'D:\minio.license'
.\scripts\Seed-DemoMusic.ps1

# 2. Start the entire stack (PostgreSQL + Flyway migrations + MinIO + Backend on http://127.0.0.1:8080)
docker compose up -d
```

Stop everything with `docker compose down` (keeps database and MinIO volumes intact).
Check the backend with `curl http://127.0.0.1:8080/home`. Details and options are in
[database/README.md](database/README.md) and [backend/README.md](backend/README.md).

## Run the Android app

1. Open the **`android/`** folder (not the repository root) in Android Studio and let Gradle sync.
2. Point the app at your backend. The URL is a build property and **must end with `/`**:
   - Emulator: the default `http://10.0.2.2:8080/` works.
   - USB phone: run `adb reverse tcp:8080 tcp:8080` and build with `-PmusicApiUrl=http://127.0.0.1:8080/`
     (or set `musicApiUrl=http://127.0.0.1:8080/` in `android/gradle.properties`).
     The tunnel is lost when the cable is re-plugged or adb restarts; keep `.\scripts\Watch-AdbReverse.ps1`
     running in a terminal and it re-creates the tunnel automatically every time a phone connects.
   - Phone on the same Wi-Fi: use the PC's LAN address, e.g. `-PmusicApiUrl=http://192.168.1.20:8080/`
     (the backend must listen on that address – see backend/README.md).
3. Run the **app** configuration, or from PowerShell:

```powershell
cd android
.\gradlew.bat :app:installDebug "-PmusicApiUrl=http://127.0.0.1:8080/"
```

Create an account in the app. Free accounts see the test ads; use **Profile → View Premium** to
try the simulated upgrade and **Manage plan** to switch back to Free.

## Tests

```powershell
.\android\gradlew.bat -p backend test                       # backend unit + integration (embedded PostgreSQL)
cd android
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug          # Android unit tests and lint
.\gradlew.bat :app:connectedDebugAndroidTest                 # device tests (uninstalls the app afterwards)
```

## Documentation

- [Project brief](documents/online-music-player-project-brief.md) (Vietnamese)
- [Architecture and flows](documents/mobile-system-architecture-and-flows.md) (Vietnamese)
- [Database design](documents/database-design.md) (Vietnamese)
- [UI design status vs Figma](documents/ui-design-figma.md)
- [Premium, ads and session details](documents/tiep-premium-ads.md) (Vietnamese)
- Test reports: [documents/test-reports](documents/test-reports)

## Licensing and secrets

Only music the team may stream is used; sources and required credits are listed in
[database/demo/ATTRIBUTION.md](database/demo/ATTRIBUTION.md). Never commit `.env`,
`storage/.env`, `storage/backend.env`, `storage/minio.license`, `runtime/` or keystores.

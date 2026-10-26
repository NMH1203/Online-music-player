# Backend API

Spring Boot modular monolith serving the Android app: accounts and sessions, catalog and
search, private audio streaming from MinIO, library and playlists, listening history,
recommendations, Free/Premium entitlements, test ads and a simulated payment.

Modules live under `src/main/java/vn/edu/usth/online_music_player/backend/`:
`auth`, `catalog`, `playback`, `library`, `playlists`, `history`, `recommendation`,
`profile`, `subscriptions`, `common` (error envelope, current-user helpers).

> `catalog-api/` is a separate catalog-only Spring Boot project. It is not part of this Gradle build
> and the Android app does not call it.

## Run

Commands run from the repository root.

### With Docker (recommended)

```powershell
docker compose up -d          # starts db, migrate, minio and backend on http://127.0.0.1:8080
docker compose ps             # all services should be healthy (migrate Exited 0)
docker compose down           # stops all containers
```

To allow a phone on the same Wi-Fi LAN to reach the Dockerized backend, set `BACKEND_BIND_ADDRESS=0.0.0.0`
in the root `.env` (see `.env.example`) and run `docker compose up -d`.

### Local development (`bootRun`)

Requirements: JDK 17+, PostgreSQL with migrations applied and MinIO running (`docker compose up -d db minio`,
see [../database/README.md](../database/README.md)). Reuses the Gradle wrapper in `android/`.

```powershell
.\scripts\run-tiep-backend.ps1 -Demo          # loads secrets, then bootRun; -Demo enables /demo/* endpoints
```

Or by hand:

```powershell
.\android\gradlew.bat -p .\backend bootRun    # dev run
.\android\gradlew.bat -p .\backend bootJar    # build/libs/*.jar for java -jar
```

## Configuration

All settings come from environment variables (`src/main/resources/application.yml`).

| Variable | Default | Notes |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://127.0.0.1:5432/music_player` | |
| `DB_USERNAME` / `DB_PASSWORD` | `music_app` / `music_local_dev_only` | local development values only |
| `SERVER_ADDRESS` | `127.0.0.1` | use `0.0.0.0` to accept phones on the LAN or inside Docker |
| `SERVER_PORT` | `8080` | |
| `AUTH_JWT_SECRET` | *(none)* | **required**, at least 32 bytes; `run-tiep-backend.ps1` creates one in `runtime/` |
| `DEMO_ENABLED` | `false` | `true` enables simulated payment and plan reset |
| `MINIO_ENDPOINT` | `http://127.0.0.1:9000` | |
| `MINIO_REGION` | `us-east-1` | |
| `MINIO_BUCKET` | `music-demo` | |
| `MINIO_ACCESS_KEY` / `MINIO_SECRET_KEY` | *(none)* | read-only credentials, generated into `storage/backend.env` by the seed script |

Spring Boot does not read `storage/backend.env` by itself; the runner script loads it into the
process environment. In Android Studio, add the variables to the backend Run Configuration instead.
Never put credentials in project files or the APK.

## Authentication

- `POST /auth/register` or `/auth/login` returns `{ accessToken, refreshToken, userId, expiresIn }`.
- Send `Authorization: Bearer <accessToken>` on protected routes. Access tokens are HS256 JWTs valid
  for 15 minutes; the filter also checks issuer, audience and that the session family is still active.
- `POST /auth/refresh` rotates the refresh token (7-day lifetime, stored only as a SHA-256 hash).
  Reusing a rotated token revokes the whole session family and returns `401`.
- Passwords: 8–72 characters and at most 72 bytes UTF-8, hashed with BCrypt.
- The user is always taken from the token; no endpoint accepts a user id from the client.

## Errors

Every error uses the same envelope:

```json
{ "code": "playlist_limit_reached", "message": "…", "details": { }, "request_id": "…" }
```

`400` invalid input, `401` missing/expired session, `403` demo endpoints disabled, `404` not found or
not owned by you, `409` conflicts (duplicate email, playlist limit, idempotency key reused),
`416` unsatisfiable range.

## Endpoints

🔒 = requires a bearer token. Catalog routes are public; `/home` adds personal sections when a valid token is sent.

### Auth

| Method | Path | Body / notes |
|---|---|---|
| POST | `/auth/register` | `{ email, password, displayName }` |
| POST | `/auth/login` | `{ email, password }` |
| POST | `/auth/refresh` | `{ refreshToken }` |
| POST | `/auth/logout` | `{ refreshToken }`; safe to repeat |

### Catalog and search

| Method | Path | Notes |
|---|---|---|
| GET | `/home` | `tracks` (popular), `albums` (new releases), `artists`, plus `recentlyPlayed` and `forYou` when signed in |
| GET | `/search?q=` | tracks, artists and albums matched by name; no filters |
| GET | `/albums/{id}` | album with its tracks |
| GET | `/artists/{id}` | artist with tracks and albums |
| GET | `/albums/{id}/cover` | image bytes from MinIO, `404` if none |
| GET | `/artists/{id}/image` | image bytes from MinIO, `404` if none |
| GET | `/tracks?query=` | published tracks for the playlist song picker |

Catalog items carry `coverUrl` (a server-relative path such as `/albums/{id}/cover`) or `null`.

### Playback and listening

| Method | Path | Notes |
|---|---|---|
| GET | 🔒 `/tracks/{id}/playback` | title, artist/album ids and names, duration, MIME type, `streamUrl`, `coverUrl`, attribution |
| GET | 🔒 `/tracks/{id}/stream` | proxies the private MinIO object; `200` full, `206` for one `Range`, `416` + `Content-Range: bytes */size` for a bad range |
| POST | 🔒 `/listening-events` | `{ clientEventId, trackId, sourceContext, sourcePlaylistId?, startedAt, endedAt, listenedSeconds }` → `{ clientEventId, countedAsPlay }` |
| GET | 🔒 `/recommendations?limit=20&currentTrackId=` | tracks with a `reason` (`genre`, `artist`, `popular`, `new`) |

Listening events are idempotent per `clientEventId`; a listen counts as a play when
`listenedSeconds >= min(30, ceil(duration × 0.5))`, which also raises the track's popularity.
Recommendations score genre 45 %, artist affinity 30 %, popularity 15 % and recency 10 %, exclude the
current track and the last 20 played, and use 60 % popular / 40 % new (max 2 per artist) for users with
fewer than 5 counted plays.

### Library and playlists

| Method | Path | Notes |
|---|---|---|
| GET / PUT / DELETE | 🔒 `/me/library/tracks[/{trackId}]` | liked songs; PUT and DELETE are safe to repeat |
| GET / PUT / DELETE | 🔒 `/me/library/albums[/{albumId}]` | liked albums |
| GET | 🔒 `/me/playlists` | your playlists with track counts |
| GET | 🔒 `/me/playlists/quota` | `{ playlistCount, maxPlaylists }`; `maxPlaylists = null` means unlimited |
| POST | 🔒 `/me/playlists` | `{ name, description }`; Free accounts get `409 playlist_limit_reached` after 3 |
| GET / PUT / DELETE | 🔒 `/me/playlists/{id}` | read, rename/describe, delete an owned playlist |
| POST / DELETE | 🔒 `/me/playlists/{id}/tracks/{trackId}` | add (once, at the end) or remove a song |

Playlist creation is serialized per account so parallel requests cannot bypass the quota.
Playlists owned by someone else are reported as `404`.

### Profile, history and plans

| Method | Path | Notes |
|---|---|---|
| GET / PATCH | 🔒 `/me/profile` | `{ id, email, displayName, bio }`; PATCH `{ displayName (1–100), bio (≤500) }` |
| GET | 🔒 `/me/stats` | `{ validPlays, minutesListened, likedTracks, likedAlbums, playlists }` |
| GET | 🔒 `/me/history?page=0` | 50 per page, snake_case fields; Free accounts only see the last 7 days (older rows are kept, just not returned) |
| GET | `/plans` | active plans |
| GET | 🔒 `/me/entitlements` (alias `/me/subscription`) | `planCode, planName, adFree, maxPlaylists, historyDays, startsAt, expiresAt, demoEnabled` |
| GET | 🔒 `/me/ads/policy` | `{ showAppOpen, playBetweenTracks }` |
| POST | 🔒 `/demo/payments` | `{ idempotencyKey (UUID), outcome: "success" \| "failed" }`; only when `DEMO_ENABLED=true`; amount is always 0 |
| POST | 🔒 `/demo/reset-plan` | back to Free for repeating the demo; playlists are kept |

The client never decides the plan: every Premium benefit is checked on the server.
Repeating a payment with the same key returns the original result; a different user or outcome
with that key gets `409`.

## Tests

```powershell
.\android\gradlew.bat -p .\backend test
```

Integration tests start a temporary embedded PostgreSQL 16 and apply all migrations; they never touch
the shared database.

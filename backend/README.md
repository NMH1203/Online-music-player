# Backend API — Library, playlists, and playback

This Spring Boot module contains the T06 library/playlist foundation and the T08 private
audio playback proxy. It uses the PostgreSQL schema and Flyway migrations already
maintained in `../database/migrations`; Compose remains the sole migration runner.

## Requirements and commands

- JDK 17 or newer.
- The Gradle 9.6.0 wrapper in `../android`.
- PostgreSQL started from the repository root with `docker compose up -d`.

From the repository root, build or run the service:

```powershell
.\android\gradlew.bat -p .\backend bootJar
.\android\gradlew.bat -p .\backend bootRun
```

The service listens on `127.0.0.1:8080`. Database settings can be overridden with
`DB_URL`, `DB_USERNAME`, and `DB_PASSWORD`. Defaults match the local development values
in `compose.yaml`; use a private password in a shared or non-local environment.

## Current-user integration

Every `/me/...` endpoint resolves the user UUID from the authenticated Java `Principal`.
The API never accepts a user id in a request body or path. The authentication task (T04)
must set the principal name/JWT subject to the UUID in `users.id`. Until that authentication
filter is integrated, requests without an authenticated principal receive `401`.

## Endpoints in this foundation

| Method | Path | Behavior |
|---|---|---|
| GET | `/me/library/tracks` | List the current user's favorite tracks |
| PUT | `/me/library/tracks/{trackId}` | Favorite a track; repeating the request is safe |
| DELETE | `/me/library/tracks/{trackId}` | Remove a favorite; repeating the request is safe |
| GET | `/me/library/albums` | List the current user's favorite albums |
| PUT | `/me/library/albums/{albumId}` | Favorite an album; repeating the request is safe |
| DELETE | `/me/library/albums/{albumId}` | Remove an album favorite |
| GET | `/me/playlists` | List the current user's playlists |
| POST | `/me/playlists` | Create a private playlist |
| GET | `/me/playlists/{playlistId}` | Read an owned playlist and its tracks |
| POST | `/me/playlists/{playlistId}/tracks/{trackId}` | Add a track once, at the end |
| DELETE | `/me/playlists/{playlistId}/tracks/{trackId}` | Remove a track from an owned playlist |
| GET | `/tracks/{trackId}/playback` | Read published-track metadata and its backend stream route |
| GET | `/tracks/{trackId}/stream` | Proxy the private MinIO audio object; supports one byte range per request |

Playlist rename/delete and the Android playlist flow remain for T20. Playlist quota is
handled by the separate entitlement task. The database primary keys and `ON CONFLICT`
writes prevent duplicate favorites and duplicate tracks in a playlist.

Errors use the shared `code`, `message`, `details`, and `request_id` response envelope.

Playback requires an authenticated principal with a UUID subject, as do the `/me/...`
routes. The auth task must install that principal before the player can stream. The
playback response intentionally omits the bucket and object key. The stream returns
`200 OK` for a full read, `206 Partial Content` for a valid single `Range`, and `416`
with `Content-Range: bytes */<size>` for an unsatisfiable/malformed range. Storage
credentials are server-only and are read from environment variables; Spring Boot does
not load `storage/backend.env` by itself.

For a local PowerShell run, load the ignored reader credentials into this terminal's
process environment and start the backend (the command does not print the values):

```powershell
Get-Content .\storage\backend.env | Where-Object { $_ -match '^[A-Z0-9_]+=' } | ForEach-Object {
  $name, $value = $_ -split '=', 2
  [Environment]::SetEnvironmentVariable($name, $value, 'Process')
}
.\android\gradlew.bat -p .\backend bootRun
```

In Android Studio, add `MINIO_ENDPOINT`, `MINIO_REGION`, `MINIO_BUCKET`,
`MINIO_ACCESS_KEY`, and `MINIO_SECRET_KEY` to the backend Run Configuration's
Environment variables. Do not add the credentials to project files or APK settings.

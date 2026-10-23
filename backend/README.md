# Backend API — Library and playlist foundation

This is the Spring Boot backend module for T06. It uses the PostgreSQL schema and Flyway
migrations already maintained in `../database/migrations`; Compose remains the sole
migration runner in this task.

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
| PUT | `/me/playlists/{playlistId}` | Rename an owned playlist and update its description |
| DELETE | `/me/playlists/{playlistId}` | Delete an owned playlist and its tracks |
| POST | `/me/playlists/{playlistId}/tracks/{trackId}` | Add a track once, at the end |
| DELETE | `/me/playlists/{playlistId}/tracks/{trackId}` | Remove a track from an owned playlist |
| GET | `/tracks?query=...` | Search published track metadata for playlist selection |

Playlist quota is handled by the separate entitlement task. The database primary keys and
`ON CONFLICT` writes prevent duplicate favorites and duplicate tracks in a playlist. The
track catalog response intentionally contains metadata only; audio streaming and Android
playback remain in the playback tasks.

Errors use the shared `code`, `message`, `details`, and `request_id` response envelope.

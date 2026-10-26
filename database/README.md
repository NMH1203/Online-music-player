# Database and media storage

PostgreSQL 16 holds accounts, catalog metadata, library, playlists, listening history and
subscriptions. Audio files and cover images live in a private MinIO bucket; the database
stores only their object keys. Full design notes: [../documents/database-design.md](../documents/database-design.md) (Vietnamese).

```text
database/
├── migrations/   Flyway migrations V1–V9 – the only source of the schema
├── checks/       SQL self-checks (each prints PASS and rolls back)
└── demo/         Demo catalog: metadata-only fixture, 50-track manifest, hashes, attribution
```

All commands below run from the repository root in PowerShell.

## PostgreSQL

```powershell
docker compose up -d          # starts db, then the one-shot `migrate` container runs Flyway
docker compose ps -a          # db: healthy, migrate: Exited (0)
docker compose logs migrate
```

Defaults: database `music_player`, user `music_app`, password `music_local_dev_only`, bound to
`127.0.0.1:5432`. Override them with a root `.env` **before the first start** (`POSTGRES_DB`,
`POSTGRES_USER`, `POSTGRES_PASSWORD`, `DB_PORT`, `DB_BIND_ADDRESS`); changing the password later
does not change it inside an existing volume.

Apply new migrations:

```powershell
docker compose up -d db
docker compose run --rm migrate
```

Never edit a migration that has already been applied to a shared database – add the next version.

| Version | Change |
|---|---|
| V1 | Core schema |
| V2 | Seed Free/Premium plans and 5 genres |
| V3 | Every user has a profile |
| V4–V5 | Album/track status columns |
| V6 | Every user has an active subscription |
| V7–V8 | Audio metadata, required for published tracks |
| V9 | Search, history and catalog indexes |

### Tables

| Area | Tables |
|---|---|
| Accounts | `users`, `user_profiles`, `user_sessions` (refresh-token hashes, rotation, revocation) |
| Catalog | `artists`, `albums`, `tracks`, `genres`, `track_genres` |
| Library | `library_tracks`, `library_albums`, `playlists`, `playlist_tracks` |
| Listening | `listening_history` (`client_event_id` unique, `counted_as_play`) |
| Plans | `plans`, `subscriptions`, `payment_events` |

### Checks and quick queries

```powershell
docker compose exec db psql -U music_app -d music_player -c "SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank;"
Get-Content -Raw -Encoding utf8 .\database\checks\smoke.sql | docker compose exec -T db psql -v ON_ERROR_STOP=1 -U music_app -d music_player
```

The other files in `checks/` run the same way and must print PASS.

### Metadata-only fixture

`demo/seed_catalog.sql` adds 10 artists, 10 albums and 50 **draft** tracks with placeholder object
keys (no audio). It is for query testing only – do not publish these tracks.

```powershell
Get-Content -Raw -Encoding utf8 .\database\demo\seed_catalog.sql | docker compose exec -T db psql -v ON_ERROR_STOP=1 -U music_app -d music_player
```

## MinIO and demo music

Needs Docker, Python 3.12 with pip, Internet access and a valid **MinIO AIStor license** file.

```powershell
.\scripts\Setup-Minio.ps1 -LicensePath 'D:\minio.license'   # copies the license, writes storage/.env, waits for health
.\scripts\Seed-DemoMusic.ps1                                 # downloads, verifies, uploads and publishes 50 MP3s
```

- S3 API: `http://127.0.0.1:9000`; console: <http://localhost:9001> (log in with `MINIO_ROOT_USER` /
  `MINIO_ROOT_PASSWORD` from `storage/.env`).
- The seed script creates the private bucket `music-demo`, checks every file against
  `demo/audio-lock.json`, writes published tracks with audio metadata, and creates read-only backend
  credentials in `storage/backend.env`.
- Options: `-DownloadOnly` (cache files only), `-VerifyOnly` (check what is already seeded), `-Limit 3`.
- Music sources and the credits that must be shown are in [demo/ATTRIBUTION.md](demo/ATTRIBUTION.md).
  Covers are not seeded, so the app shows placeholders.

To add your own **licensed** songs, use `scripts/Import-CustomMedia.ps1`
(`-Audio`, `-Title`, `-Artist`, `-Album`, optional `-AlbumCover`, `-ArtistImage`, `-Genre`, or `-Directory` / `-Manifest` for many files).

## Stop, back up and restore

```powershell
docker compose down          # stops all containers; keeps postgres and minio volumes
docker compose stop          # stops running containers without removing them
```

Named volumes (`music-player_postgres_data` and `music-player-storage_minio_data`) are preserved across
`docker compose down` and created automatically on a new machine on first `docker compose up -d`.
`docker compose down -v` deletes the volumes (all database rows and all audio) – only use it to start from scratch.

Back up before each rehearsal and demo, and copy the files to another disk or machine:

```powershell
docker compose exec db pg_dump -U music_app -d music_player -Fc -f /tmp/music-player.dump
docker compose cp db:/tmp/music-player.dump .\music-player.dump
```

A database dump does not include the audio; back up the MinIO volume separately.
`scripts/Backup-Restore-Drill.ps1` dumps the database, restores it into a scratch database and compares
fingerprints without touching the live one – see [../documents/test-reports/T31-backup-restore.md](../documents/test-reports/T31-backup-restore.md).

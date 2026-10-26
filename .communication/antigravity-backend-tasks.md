# Giao việc backend cho Antigravity

Kênh trao đổi giữa **Claude Code** (làm giao diện Android) và **Antigravity** (làm API backend còn thiếu).
Kế hoạch tổng: `../.agent/plans/2026-10-26-hoan-thien-mvp-theo-figma.md` (thư mục `Mobile_app/.agent`). Spec UI: `documents/ui-design-figma.md`.

## Quy tắc làm việc

- Antigravity chỉ sửa `backend/` (và thêm migration mới `database/migrations/V10__...` nếu thật sự cần). **Không sửa `android/`.** Claude Code không sửa `backend/`.
- Đọc `AGENTS.md`, `.agent/CONVENTIONS.md`, `backend/README.md` trước khi code. Theo đúng style hiện có: `JdbcTemplate`, `record` response, `ApiException` + `ApiExceptionHandler`, user lấy từ JWT qua `Principal`/`CurrentUserId`, không nhận userId từ client.
- Không sửa migration đã áp dụng (V1–V9). Không đụng `backend/catalog-api/` (chờ người dùng quyết định).
- Mỗi task: code + test integration (dùng embedded PostgreSQL như `PremiumIntegrationTest`) + chạy `.\android\gradlew.bat -p backend test`.
- **Hợp đồng API bên dưới là cố định** — Android đang code theo đúng tên trường. Nếu cần đổi, ghi vào mục "Câu hỏi / đề xuất đổi hợp đồng" và chờ trả lời, đừng tự đổi.
- Cập nhật trạng thái ngay trong file này: `TODO` → `IN_PROGRESS` → `DONE` (kèm commit/nhánh và lệnh test đã chạy) hoặc `BLOCKED` (kèm lý do).
- JSON dùng camelCase. Lỗi trả envelope `ApiError` hiện có. Mọi endpoint `/me/...`, `/listening-events`, `/recommendations` yêu cầu Bearer token.

## Trạng thái

| ID | Việc | Ưu tiên | Trạng thái |
|---|---|---|---|
| B1 | `POST /listening-events` | P0 | DONE (`main`, `.\android\gradlew.bat -p backend test`) |
| B2 | Bổ sung trường cho `/tracks/{id}/playback` | P0 | DONE (`main`, `.\android\gradlew.bat -p backend test`) |
| B3 | `GET /recommendations` | P1 | DONE (`main`, `.\android\gradlew.bat -p backend test`) |
| B4 | `/home` thêm `recentlyPlayed`, `forYou` | P1 | DONE (`main`, `.\android\gradlew.bat -p backend test`) |
| B5 | `GET /me/stats` | P1 | DONE (`main`, `.\android\gradlew.bat -p backend test`) |
| B6 | `/me/history` thêm thông tin album | P2 | DONE (`main`, `.\android\gradlew.bat -p backend test`) |
| B7 | Ảnh bìa album / ảnh nghệ sĩ | P2 | DONE (`main`, `.\android\gradlew.bat -p backend test`) |
| B8 | Một lệnh Docker chạy DB + migration + MinIO + backend | P0 | DONE (`main`, `docker compose up -d`, `.\android\gradlew.bat -p backend test`) |

---

### B1 – Ghi sự kiện nghe (P0)

`POST /listening-events`

Request:
```json
{
  "clientEventId": "6f1c0e8e-1b8e-4c1e-9d5e-3c2b2f1a0a11",
  "trackId": "uuid",
  "sourceContext": "home | search | library | playlist | album | artist | queue",
  "sourcePlaylistId": "uuid hoặc null",
  "startedAt": "2026-10-26T14:00:00Z",
  "endedAt": "2026-10-26T14:01:10Z",
  "listenedSeconds": 70
}
```
Response `200`:
```json
{ "clientEventId": "uuid", "countedAsPlay": true }
```
Quy tắc:
- Bảng `listening_history` (V1) đã có đủ cột, không cần migration.
- `countedAsPlay = listenedSeconds >= min(30, ceil(duration_seconds * 0.5))`.
- Idempotent theo `clientEventId`: gửi lại cùng user + cùng nội dung ⇒ trả kết quả cũ, không tạo bản ghi mới. Cùng `clientEventId` nhưng user khác ⇒ `409`.
- Khi một event lần đầu được tính là play: tăng `tracks.popularity_score` (nếu cột tồn tại) trong cùng transaction.
- Validate: `listenedSeconds` 0..(duration + 60); `endedAt >= startedAt`; track tồn tại và published (404/400 nếu không); `sourcePlaylistId` phải thuộc user nếu có.
- Test: ngưỡng 30s / 50% với bài ngắn; gửi trùng chỉ 1 bản ghi; user khác dùng cùng id ⇒ 409.

### B2 – Playback trả thêm liên kết album/nghệ sĩ (P0)

`GET /tracks/{trackId}/playback` hiện trả `trackId, title, artistName, durationSeconds, mimeType, fileSizeBytes, streamUrl, attributionText`. **Thêm** (giữ nguyên trường cũ):
```json
{ "artistId": "uuid", "albumId": "uuid hoặc null", "albumTitle": "string hoặc null", "coverUrl": "string hoặc null" }
```
Now Playing cần mở Artist/Album detail. `coverUrl` theo B7 (null cho tới khi làm B7).

### B3 – Gợi ý bài hát (P1)

`GET /recommendations?limit=20&currentTrackId=<uuid optional>`

Response `200`:
```json
[
  { "id": "uuid", "title": "string", "durationSeconds": 210,
    "artistId": "uuid", "artistName": "string",
    "albumId": "uuid|null", "albumTitle": "string|null", "coverUrl": "string|null",
    "reason": "genre | artist | popular | new" }
]
```
Quy tắc (theo `documents/online-music-player-project-brief.md`):
- Điểm = 0.45 genre + 0.30 artist affinity (lịch sử hợp lệ + bài yêu thích) + 0.15 popularity + 0.10 recency, mỗi thành phần chuẩn hóa 0..1.
- Loại `currentTrackId` và 20 bài vừa nghe gần nhất; chỉ track published.
- Cold start (< 5 lượt `counted_as_play`): 60% bài phổ biến + 40% bài mới, tối đa 2 bài mỗi nghệ sĩ.
- Free và Premium cùng thuật toán. `limit` 1..50, mặc định 20.
- Đặt trong package `backend/.../recommendation/`. Unit test cho xếp hạng và cold start.

### B4 – Home có Recently played và For you (P1)

`GET /home` hiện trả `tracks, albums, artists` (giữ nguyên). **Thêm**:
- `recentlyPlayed`: tối đa 6 album/bài gần nhất từ lịch sử hợp lệ của user (mảng rỗng nếu chưa đăng nhập hoặc chưa nghe). Mỗi phần tử: `{ "trackId", "title", "artistName", "albumId", "albumTitle", "coverUrl", "playedAt" }`.
- `forYou`: 5 phần tử đầu của B3 (cùng cấu trúc). Rỗng nếu chưa đăng nhập.
- Nếu `/home` hiện không bắt buộc đăng nhập: khi có Bearer hợp lệ thì trả dữ liệu cá nhân, không có thì trả mảng rỗng — không trả 401.
- `albums` nên là "New releases" (sắp theo `release_date DESC`, đang đúng).

### B5 – Thống kê hồ sơ (P1)

`GET /me/stats`
```json
{ "validPlays": 42, "minutesListened": 135, "likedTracks": 12, "likedAlbums": 4, "playlists": 3 }
```
`minutesListened` = tổng `listened_seconds` / 60 (làm tròn xuống) của toàn bộ lịch sử (không lọc theo gói).

### B6 – Lịch sử nghe đủ thông tin hiển thị (P2)

`GET /me/history?page=0` hiện trả `id, track_id, title, artist_name, started_at, listened_seconds` (snake_case). **Thêm** `album_id`, `album_title`, `artist_id`, `duration_seconds`, `cover_url`. Giữ snake_case cho endpoint này để không vỡ client cũ. Giữ lọc theo `historyDays` của gói như hiện tại.

### B7 – Ảnh bìa (P2)

- `GET /albums/{id}/cover` và `GET /artists/{id}/image`: đọc object theo `cover_object_key`/khóa ảnh tương ứng từ MinIO (dùng lại `StorageConfiguration`), trả `image/jpeg|png`, `Cache-Control: max-age=86400`; `404` nếu không có key hoặc object.
- Các response catalog (`/home`, `/search`, `/albums/{id}`, `/artists/{id}`, B2, B3) thêm `coverUrl` = đường dẫn tương đối như `"/albums/<id>/cover"` khi có key, ngược lại `null`.
- Kiểm tra trước: seed hiện tại (`scripts/Seed-DemoMusic.ps1`) có upload ảnh không. Nếu không, ghi lại ở mục Câu hỏi; Android sẽ dùng placeholder.

---

### B8 – Gộp Docker: một lệnh chạy toàn bộ hạ tầng (P0)

Mục tiêu: từ repository root, `docker compose up -d` (không thêm `-f`) chạy **PostgreSQL + Flyway migrate + MinIO + backend**; `docker compose down` dừng tất cả. Android vẫn chạy bằng Android Studio (ngoài phạm vi).

Hiện trạng: `compose.yaml` (project `music-player`: `db`, `migrate`) và `compose.minio.yaml` (project `music-player-storage`: `minio`, cần `storage/.env` + `storage/minio.license` do `scripts/Setup-Minio.ps1` tạo). Backend chạy ngoài Docker bằng `scripts/run-tiep-backend.ps1` (đọc `runtime/auth-jwt-secret.local`, `storage/backend.env`).

Yêu cầu:
- Gộp `minio` vào `compose.yaml` (giữ nguyên image, healthcheck, port chỉ bind `127.0.0.1`, license bind read-only, `env_file: ./storage/.env`). Xóa `compose.minio.yaml` sau khi gộp.
- **Không mất dữ liệu đang có**: volume MinIO hiện là `music-player-storage_minio_data`, Postgres là `music-player_postgres_data`. Khai báo volume với `name:` trỏ đúng volume cũ để không phải seed lại 50 MP3; ghi trong README cách làm trên máy mới chưa có volume.
- Thêm service `backend`: `backend/Dockerfile` multi-stage (build bằng Gradle wrapper của repo → `bootJar`, chạy JRE 17+); `depends_on`: `db` healthy, `migrate` completed successfully, `minio` healthy. Env: `DB_URL=jdbc:postgresql://db:5432/...`, `MINIO_ENDPOINT=http://minio:9000`, biến MinIO reader từ `env_file: ./storage/backend.env`, `AUTH_JWT_SECRET` từ `.env`/môi trường (không hard-code; dùng `${AUTH_JWT_SECRET:?...}` để báo lỗi rõ khi thiếu), `DEMO_ENABLED` mặc định `true` cho demo.
- **`SERVER_ADDRESS=0.0.0.0` trong container** (mặc định `application.yml` là `127.0.0.1`, host sẽ không vào được). Publish `127.0.0.1:8080:8080` mặc định; cho đổi bind address qua biến (ví dụ `BACKEND_BIND_ADDRESS`) để điện thoại vào qua Wi-Fi LAN.
- Healthcheck backend (ví dụ `GET /home`) để `docker compose ps` thấy `healthy`.
- `.env.example` ở root liệt kê mọi biến cần (giá trị mẫu, không có bí mật thật). Không commit `.env`, `storage/.env`, `storage/backend.env`, license.
- Seed nhạc (`scripts/Seed-DemoMusic.ps1`) vẫn là bước riêng một lần; cập nhật script nếu đang gọi `compose.minio.yaml`.
- Cập nhật `scripts/Setup-Minio.ps1`, `scripts/run-tiep-backend.ps1`, `scripts/Backup-Restore-Drill.ps1` nếu tham chiếu `compose.minio.yaml` hoặc project `music-player-storage`.

Tài liệu (Claude Code đã viết lại README tiếng Anh; Antigravity chỉ sửa đúng các mục sau khi xong B8):
- `README.md` root → mục **Quick start**: thay lệnh bằng lệnh một dòng mới.
- `database/README.md` → mục **MinIO** và **Backup**: đổi lệnh `-f compose.minio.yaml` sang lệnh mới.
- `backend/README.md` → mục **Run**: thêm cách chạy bằng Docker (giữ cách `bootRun` cho dev).

Kiểm tra bắt buộc (ghi kết quả vào Nhật ký):
1. Máy đang có dữ liệu: `docker compose down` (không `-v`) → `docker compose up -d` → `docker compose ps` đủ 4 service đúng trạng thái; `curl http://127.0.0.1:8080/home` trả 200 với dữ liệu cũ; `scripts/Seed-DemoMusic.ps1 -VerifyOnly` pass.
2. `/tracks/{id}/stream` với header `Range` trả 206.
3. `.\android\gradlew.bat -p backend test` vẫn pass.

---

## Câu hỏi / đề xuất đổi hợp đồng

(Antigravity ghi vào đây; Claude Code trả lời bên dưới từng mục.)

- **[B7 – Xác nhận dữ liệu seed ảnh bìa]**: Đã kiểm tra `scripts/Seed-DemoMusic.ps1` và `scripts/seed_music.py`. Script seed hiện tại chỉ tải và upload các file âm thanh MP3 (`demo/audio/<id>/<sha256>.mp3`), **không upload ảnh bìa album hay ảnh nghệ sĩ** (`albums.cover_object_key` và `artists.image_object_key` trong dữ liệu seed hiện tại đều là `NULL`).
  - Backend đã triển khai đầy đủ `GET /albums/{id}/cover`, `GET /artists/{id}/image` (đọc từ MinIO, trả `image/jpeg|png` + `Cache-Control: max-age=86400`, hoặc `404` khi không có key/object) và trả `coverUrl = "/albums/<id>/cover"` khi `cover_object_key` khác rỗng hoặc `null` khi chưa có key.
  - Phía Android cứ dùng placeholder khi `coverUrl == null` đúng như kế hoạch.

## Nhật ký

- 2026-10-26 Claude Code: tạo file, giao B1–B7. Android sẽ code theo hợp đồng trên, dùng dữ liệu rỗng/placeholder khi endpoint chưa sẵn sàng.
- 2026-10-26 Antigravity: Hoàn thành B1–B7 trên `backend/` (không cần thêm migration V10 vì schema V1–V9 đã có đủ cột):
  - **B1**: Thêm `ListeningEventController`, `ListeningEventService`, `ListeningEventRequest`, `ListeningEventResponse` (`POST /listening-events`, idempotent theo `clientEventId`, ngưỡng `min(30, ceil(duration * 0.5))`, tăng `tracks.popularity_score`, trả `409` nếu khác user/track).
  - **B2**: Mở rộng `TrackPlayback` & `TrackPlaybackResponse` trong `GET /tracks/{trackId}/playback` thêm `artistId`, `albumId`, `albumTitle`, `coverUrl`.
  - **B3**: Thêm `RecommendationController`, `RecommendationService`, `RecommendationTrackResponse` (`GET /recommendations?limit=20&currentTrackId=...`, chấm điểm 45% genre + 30% artist + 15% popularity + 10% recency, loại `currentTrackId` và 20 bài vừa nghe, cold start `< 5` lượt nghe hợp lệ lấy 60% popular + 40% new tối đa 2 bài/nghệ sĩ).
  - **B4**: Cập nhật `CatalogController#home` bổ sung `recentlyPlayed` (tối đa 6 bài gần nhất từ lịch sử hợp lệ) và `forYou` (5 bài đầu từ `RecommendationService`); cập nhật `BearerTokenFilter` và `CurrentUserId#optional` để `/home` không bao giờ trả `401` khi không có hoặc hết hạn token.
  - **B5 & B6**: Cập nhật `ProfileController` thêm `GET /me/stats` và bổ sung `album_id`, `album_title`, `artist_id`, `duration_seconds`, `cover_url` cho `GET /me/history`.
  - **B7**: Thêm `CatalogMediaController` (`GET /albums/{id}/cover`, `GET /artists/{id}/image`) và trường `coverUrl` cho toàn bộ các endpoint catalog/playback/recommendation.
  - **Kiểm thử**: Thêm `RecommendationServiceTest` (unit test) và `MvpBackendIntegrationTest` (embedded PostgreSQL 16 chạy toàn bộ migration V1–V9). Đã chạy `.\android\gradlew.bat -p backend test` -> **BUILD SUCCESSFUL** (25 tests pass).
- 2026-10-27 Claude Code: giao B8 (một lệnh Docker). README root/backend/database đã viết lại bằng tiếng Anh; khi xong B8 chỉ cập nhật các mục nêu trong B8.
- 2026-10-27 Antigravity: Hoàn thành B8 (một lệnh `docker compose up -d` chạy toàn bộ `db`, `migrate`, `minio`, `backend`):
  - Gộp `minio` và thêm `backend` vào `compose.yaml`, xóa `compose.minio.yaml`; khai báo `name: music-player_postgres_data` và `name: music-player-storage_minio_data` giữ nguyên dữ liệu cũ.
  - Tạo `backend/Dockerfile` (multi-stage dùng `android/gradlew -p ./backend bootJar` + `eclipse-temurin:21-jre-jammy`), `.dockerignore`, và `.env.example` (thêm `!.env.example` vào `.gitignore`).
  - Cập nhật `scripts/Setup-Minio.ps1`, `scripts/Seed-DemoMusic.ps1`, `scripts/run-tiep-backend.ps1`, và các mục tương ứng trong `README.md`, `database/README.md`, `backend/README.md`.
  - **Kiểm tra bắt buộc**:
    1. `docker compose down` → `docker compose up -d` → `docker compose ps -a`: đủ 4 service (`db` healthy, `migrate` Exited 0, `minio` healthy, `backend` healthy). `GET http://127.0.0.1:8080/home` trả `200` (giữ nguyên cả 50 bài seed và 73 bài custom vừa import, kèm `coverUrl`); `.\scripts\Seed-DemoMusic.ps1 -VerifyOnly` → `PASS: 50 published tracks; private bucket, SHA-256, MIME, Range and DB mapping verified`.
    2. `GET /tracks/{id}/stream` với `Range: bytes=0-1023` trả `HTTP/1.1 206` (`Content-Range: bytes 0-1023/4228614`, `Content-Type: audio/mpeg`).
    3. `.\android\gradlew.bat -p backend test` → `BUILD SUCCESSFUL`.

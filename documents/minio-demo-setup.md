# MinIO AIStor, metadata mapping và nhạc demo

Các file trong hướng dẫn này nằm tại **D:\Online-music-player**. Docker Desktop chạy Linux containers. Python 3.12 có pip dùng để kiểm tra MP3 và seed; không cần FFmpeg. Cấu hình PostgreSQL vẫn ở `compose.yaml`; MinIO dùng `compose.minio.yaml`, project riêng `music-player-storage`.

## 1. Vì sao image đã tải nhưng MinIO chưa chạy?

Ảnh Docker Desktop là màn hình **Images**: image là gói phần mềm, chưa chứa cấu hình của container. `MINIO_ROOT_USER` và `MINIO_ROOT_PASSWORD` phải được truyền vào **container khi tạo/chạy**. Đặt `$env:...` ở PowerShell không tự truyền vào container nếu thiếu `environment`, `env_file` hoặc `docker run -e`.

Hai container `gifted_hoover` và `sharp_jemison` được tạo trước đó đã dừng. Log kiểm tra của `gifted_hoover` chỉ in hướng dẫn sử dụng `minio`, vì chưa có lệnh **`server /data`**. `Exited (0)` ở trường hợp này nghĩa là chương trình in help xong, chưa phải server đang phục vụ.

Image đã tải là **quay.io/minio/aistor/minio:RELEASE.2026-09-19T17-05-25Z**. Đây là AIStor, cần license. File bạn cung cấp là **D:\minio.license**. Script sao chép vào `storage/minio.license`, mount chỉ đọc vào container. Theo [tài liệu AIStor](https://docs.min.io/aistor/installation/container/install/), gói Free cũng dùng file license.

## 2. Cài và chạy bằng PowerShell

```powershell
Set-Location D:\Online-music-player
Set-ExecutionPolicy -Scope Process Bypass
.\scripts\Setup-Minio.ps1 -LicensePath D:\minio.license
```

Script tạo `storage/.env` khi chưa có, tự sinh username/password ngẫu nhiên, rồi chạy Compose và chờ healthcheck. Chạy lại giữ nguyên tài khoản và volume. Có thể đóng PowerShell sau khi chạy xong.

- Mở **[Console](http://localhost:9001)** để quản lý bucket và đăng nhập.
- Mở file **`storage/.env` trong editor**; dùng giá trị `MINIO_ROOT_USER` / `MINIO_ROOT_PASSWORD` để đăng nhập. Không có mật khẩu cố định trong tài liệu.
- **9000 là S3 API**, **9001 là Console**; các cổng chỉ bind vào máy này.
- Dữ liệu nằm trong volume `music-player-storage_minio_data`.

```powershell
docker compose --env-file storage/.env -f compose.minio.yaml ps
docker compose --env-file storage/.env -f compose.minio.yaml logs --tail 50 minio
Invoke-WebRequest http://127.0.0.1:9000/minio/health/live
```

Trạng thái cần đạt: `healthy`, HTTP 200. Có thể dừng/chạy lại:

```powershell
docker compose --env-file storage/.env -f compose.minio.yaml stop
docker compose --env-file storage/.env -f compose.minio.yaml up -d --wait
```

Muốn đổi tài khoản root: sửa hai giá trị trong `storage/.env`, chạy lại `Setup-Minio.ps1`. Compose tạo lại container với cấu hình mới và giữ volume. Không dùng `down -v` nếu muốn giữ nhạc. File `.env`, license, credential backend và audio cache đã được ignore trong Git.

Nếu muốn thao tác Docker Desktop: bấm **Run** của image → **Optional settings** → **Environment variables**, thêm tên biến và giá trị thành hai cột riêng; port mapping 9000/9001, volume `/data`, license mount `/minio.license` cũng phải đủ. Cần có command `minio server /data --console-address :9001 --license /minio.license`; nếu hộp Run không có trường command, dùng script Compose ở trên. Chỉ sửa environment trong terminal của container đang chạy không đổi cấu hình server; phải tạo lại container với cấu hình đúng.

## 3. Seed 50 bản nhạc thật

```powershell
Set-Location D:\Online-music-player
.\scripts\Seed-DemoMusic.ps1
```

Lần đầu tải khoảng **233 MiB** từ Incompetech. Script thực hiện:

1. Tạo Python `.venv`, cài các dependency đã ghim phiên bản.
2. Khởi động PostgreSQL và chạy Flyway đến **V8**.
3. Đọc `database/demo/sources.json`; chỉ nhận nguồn đã duyệt và license cho phép.
4. Tải MP3 nguyên bản, kiểm SHA-256 theo `audio-lock.json`; đo thời lượng, bitrate, kích thước bằng Mutagen.
5. Giữ `source_url`, `license_url`, `license_code` và `attribution_text` từ manifest để ghi vào metadata bài nhạc.
6. Upload vào bucket **private** `music-demo`; đặt `Content-Type: audio/mpeg` và metadata SHA/license/source.
7. Đọc lại toàn bộ object, so checksum, kiểm HTTP Range trả **206** và chặn truy cập anonymous.
8. Sau khi toàn bộ audio đạt kiểm tra, upsert track `published` và `track_audio_metadata` trong **cùng một transaction**. V8 yêu cầu mọi track published có metadata tại cuối transaction.
9. Tạo tài khoản backend chỉ có quyền đọc audio, kiểm tra đọc thành công và ghi bị từ chối.

Chạy lại không tạo thêm bản ghi hoặc thêm object trùng. Nếu tải/upload dở, chạy lại để tiếp tục từ cache. Khi checksum khác bản đã duyệt, script dừng; cần kiểm tra thay đổi nguồn, không tự chấp nhận file mới. Không có thao tác tự xóa bài hoặc playlist cũ. Nếu lỗi trước commit, DB không publish batch đó; object chưa được tham chiếu có thể còn trong bucket để tái sử dụng lần sau.

```powershell
# Kiểm lại object, DB mapping và quyền backend, không seed thêm:
.\scripts\Seed-DemoMusic.ps1 -VerifyOnly

# Chỉ tải/cache, chưa ghi MinIO hoặc DB:
.\scripts\Seed-DemoMusic.ps1 -DownloadOnly

# Chạy thử một phần bộ dữ liệu:
.\scripts\Seed-DemoMusic.ps1 -Limit 3
```

`runtime/demo-music/verification.json` lưu kết quả kiểm tra gần nhất. `database/demo/seed_catalog.sql` cũ vẫn là fixture metadata **draft**, không có audio. Không đổi các track đó thành published. Bộ mới dùng ISRC/UUID ổn định và key khác nên có thể cùng tồn tại; tổng số track trong DB có thể lớn hơn 50, nhưng bộ demo có 50 track có metadata nguồn.

## 4. Nguồn và điều kiện ghi công

Nguồn và giấy phép được lưu bằng liên kết trong `database/demo/sources.json` và metadata bài nhạc; script không tải hoặc lưu HTML của các trang này. Các trường `source_url`, `license_url`, `license_code` và `attribution_text` vẫn được giữ để tra cứu và hiển thị credit.

Bộ seed có **50 bản thu thật của Kevin MacLeod**, 10 bài mỗi thể loại Pop/Rock/Jazz/Classical/Electronica, 10 tuyển tập do ứng dụng đặt tên “Demo Compilation”. Các tuyển tập này không phải album chính thức. Chỉ có **1 nghệ sĩ thật**; không tạo tác giả giả. Quy tắc recommendation tối đa 2 bài/nghệ sĩ sẽ giới hạn kết quả trên bộ này; cần bổ sung nghệ sĩ có license đã xác minh để kiểm thử độ đa dạng.

[Incompetech FAQ](https://incompetech.com/music/royalty-free/faq.html) cho phép streaming khi ghi công; [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/) cho phép phân phối, kể cả mục đích thương mại, với điều kiện của giấy phép. Giữ MP3 nguyên bản, không áp dụng DRM hoặc điều khoản ngăn người nhận thực hiện các quyền CC BY. Nhạc có bản quyền được cấp phép, không gọi là “không bản quyền”.

**Khi tích hợp Android, hiển thị Music credits hoặc Thông tin bài hát dễ tìm:** tên bài, Kevin MacLeod, nguồn, link CC BY 4.0, và “Original MP3 recording; no changes.” Nội dung có sẵn ở `track_audio_metadata.attribution_text` và [ATTRIBUTION.md](../database/demo/ATTRIBUTION.md). Chỉ lưu credit trong DB/repo không thay thế credit hiển thị cho người nghe. Backend và giao diện credits chưa được triển khai trong repo hiện tại.

## 5. Mapping metadata

| Dữ liệu | PostgreSQL | MinIO / kiểm tra |
|---|---|---|
| Endpoint | Cấu hình môi trường, không lưu trong từng bài | `http://127.0.0.1:9000` trên Windows |
| Bucket | `track_audio_metadata.bucket_name` | `music-demo` |
| Object key | `tracks.audio_object_key` | `demo/audio/incompetech-<isrc>/<sha256>.mp3` |
| MIME | `tracks.mime_type` | HTTP `Content-Type: audio/mpeg` |
| Dung lượng | `tracks.file_size_bytes` | `stat_object.size` bằng số byte file |
| Thời lượng | `tracks.duration_seconds` | Làm tròn lên giây từ MP3 thực tế |
| Bitrate | `tracks.bitrate_kbps` | Đọc MP3, đổi bit/s sang kbit/s |
| SHA-256 | `track_audio_metadata.sha256` | `x-amz-meta-sha256`; kiểm lại từ GET toàn bộ bytes |
| ETag | `track_audio_metadata.etag` | ETag S3; không coi là SHA hoặc MD5 của file |
| License / nguồn | `license_code`, `license_url`, `source_url` | `x-amz-meta-license`, `x-amz-meta-source-url` |
| Ghi công | `attribution_text` | Backend trả về để UI hiển thị |
| Thời điểm kiểm | `verified_at` | Sau kiểm tra bytes và Range |
| Cover | `tracks.cover_object_key` | NULL; UI dùng ảnh mặc định, không lấy ảnh chưa xác minh quyền |

V7 thêm bảng `track_audio_metadata` một-một với `tracks`; V8 bắt buộc track `published` có bản ghi metadata tại cuối transaction. Khi publish track mới hoặc chuyển draft sang published, ghi metadata trong cùng transaction; không xóa hoặc chuyển metadata khiến track published bị thiếu audio. Không sửa migration cũ. Không lưu URL ký tạm thời, đường dẫn Windows hoặc credential vào catalog. Key là đường dẫn **bên trong bucket**, không gồm tên bucket hoặc dấu `/` đầu tiên.

Truy vấn backend:

```sql
SELECT t.id, t.title, t.duration_seconds, t.mime_type, t.file_size_bytes,
       t.audio_object_key, m.bucket_name, m.sha256, m.license_code,
       m.license_url, m.source_url, m.attribution_text
FROM tracks t
JOIN track_audio_metadata m ON m.track_id = t.id
WHERE t.status = 'published';
```

## 6. Tích hợp Spring Boot và Android

Seed tạo `storage/backend.env` chứa `MINIO_ENDPOINT`, `MINIO_REGION`, `MINIO_BUCKET`, `MINIO_ACCESS_KEY`, `MINIO_SECRET_KEY`. Đây là tài khoản chỉ đọc `demo/audio/*`; root chỉ dùng cho setup/seed. Đưa các biến này vào Run Configuration của backend; Spring Boot không tự đọc file `.env`. Mẫu binding nằm ở `storage/application-storage.example.yaml`; nhóm cần triển khai `@ConfigurationProperties(prefix="storage")` và `MinioClient` tương ứng.

Backend chạy trên Windows dùng `127.0.0.1:9000`. Nếu sau này backend chạy trong Docker, nối vào network `music-player-storage_default` và dùng **`http://minio:9000`**. Android gọi API backend, không nhận access key/secret key và không gọi cổng Console. Credential không nhúng vào APK.

Luồng stream: xác thực user → lấy track published + metadata → `getObject(bucket, objectKey)` bằng tài khoản backend → trả `Content-Type`, độ dài và audio. Khi request có `Range`, xác thực range, truyền offset/length sang MinIO, trả 206 cùng `Content-Range`/`Accept-Ranges`; range sai trả 416. Bộ seed đã kiểm Range ở MinIO; HTTP proxy backend và playback Android cần được nhóm kiểm khi có implementation.

## 7. Kiểm tra SQL và xử lý lỗi

Chạy sau khi Flyway đã áp dụng V1–V8:

```powershell
Get-Content -Raw -Encoding utf8 database/checks/audio_metadata.sql |
    docker compose exec -T db psql -v ON_ERROR_STOP=1 -U music_app -d music_player
Get-Content -Raw -Encoding utf8 database/checks/published_audio_metadata.sql |
    docker compose exec -T db psql -v ON_ERROR_STOP=1 -U music_app -d music_player
docker compose exec -T db psql -U music_app -d music_player -c "SELECT count(*) FROM tracks t JOIN track_audio_metadata m ON m.track_id=t.id WHERE t.status='published';"
```

| Hiện tượng | Cách xử lý |
|---|---|
| MinIO in help rồi Exited (0) | Thiếu `server /data`; chạy `Setup-Minio.ps1` |
| License invalid/expired | Xem log AIStor; cấp file license hợp lệ bằng `-LicensePath` |
| Không đăng nhập được Console | Đọc credential trong `storage/.env`; sau khi sửa phải chạy lại setup để tạo lại container |
| Port 9000/9001 đã dùng | Sửa `MINIO_API_PORT`, `MINIO_CONSOLE_PORT` và endpoint tương ứng trong `storage/.env`, sửa `MINIO_ENDPOINT` trong `storage/backend.env` nếu đã có; chạy lại setup và kiểm tra reader |
| File `minio.exe` không chạy trên Windows | Không cần file đó cho cách cài Docker; dùng image hiện có |
| Database authentication failed | Đặt thông số DB hiện tại trong `.env` ở project root; thay `.env` không đổi password của volume PostgreSQL đã tồn tại |
| Thiếu `track_audio_metadata` | `docker compose run --rm migrate` để áp dụng V7 |
| Không publish được track vì thiếu metadata | Ghi `track_audio_metadata` cùng transaction trước khi commit; với dữ liệu cũ, sửa track published thiếu metadata trước khi chạy V8 |
| MP3/hash mismatch | Không publish; so nguồn/lock và cache, xem thay đổi trước khi cập nhật |
| HTTP 403 khi mở audio trực tiếp | Bucket private đang hoạt động; dùng backend hoặc tài khoản đọc để phát |

Sao lưu PostgreSQL cùng volume MinIO và audio cache trong `runtime/demo-music`. Có thể tải lại bộ audio từ nguồn bằng script; bản sao local hữu ích khi demo không có mạng.

# Kết quả kiểm tra MinIO và seed demo

Kiểm tra trên máy local của dự án ngày **16/10/2026**, tại `D:\MobileAppPrj`.

Cập nhật quy trình ngày **18/10/2026**: nguồn và giấy phép chỉ lưu liên kết cùng `license_code` và `attribution_text`; script không tải/lưu HTML. Các kết quả kiểm tra bên dưới vẫn ghi nhận lần chạy ngày 16/10/2026, không phải một lần kiểm tra runtime mới.

| Hạng mục | Kết quả |
|---|---|
| AIStor | Container `music-player-storage-minio-1` healthy, dùng image RELEASE.2026-09-19T17-05-25Z và license người dùng cung cấp |
| Flyway | V1–V7 hợp lệ; nâng DB từ V6 lên V7 thành công; lần sau báo up to date |
| SQL checks | `smoke.sql`, `user_profile_relationship.sql`, `active_subscription.sql`, `audio_metadata.sql`: PASS và ROLLBACK |
| Catalog | 50 track published, 1 nghệ sĩ thật, 10 tuyển tập demo, 5 thể loại × 10 bài |
| Audio | 50 MP3, tổng 244.420.985 byte, hash khóa trong `database/demo/audio-lock.json` |
| MinIO | 50 object trong bucket private `music-demo`; mỗi object đọc lại khớp SHA-256, size và Content-Type |
| Tua audio | GET Range 1024 byte đầu trả 206 và khớp file gốc trên cả 50 bài |
| Quyền truy cập | Anonymous object HEAD trả 403; backend đọc thành công, PUT và ListBucket bị AccessDenied |
| Seed lặp | Chạy script seed lần hai thành công, vẫn 50 track và 50 object |
| Lệnh kiểm tra | `scripts/Seed-DemoMusic.ps1 -VerifyOnly`: PASS, gồm kiểm tra tài khoản backend |
| Metadata nguồn/giấy phép | Mỗi bài có `source_url`, `license_url`, `license_code` và `attribution_text` |
| Bí mật | `storage/.env`, `storage/backend.env`, `storage/minio.license` được Git ignore |

Các báo cáo chi tiết gần nhất: `runtime/demo-music/verification.json` và `runtime/storage-reader-check.json`. Các file này không chứa secret, nhưng runtime được ignore để tránh commit audio/cache.

Phạm vi đã kiểm là PostgreSQL và MinIO. Repo chưa có backend stream hoặc Android playback/credits hoàn chỉnh; chưa kiểm luồng nghe từ app. Bộ nhạc hiện có một nghệ sĩ, nên chưa đủ kiểm recommendation đa nghệ sĩ theo mục tiêu thiết kế ban đầu. Điều kiện hiển thị credit khi tích hợp app được mô tả trong [hướng dẫn](minio-demo-setup.md) và [ghi công](../database/demo/ATTRIBUTION.md).

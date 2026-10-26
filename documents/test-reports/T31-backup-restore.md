# T31 - Biên bản backup/restore
- Ngày giờ / người chạy: 2026-10-25 / Admin
- Commit: d0b2e7b
- Dump: backups\music-player-20261025-213...dump   sha256: 2D1A1C164DB981CB14B66827850325812CC726F75C92FD7D0EEB6A500AD0B953
- Rebuild từ DB rỗng V1-V9: đạt
- Seed chạy 2 lần, số dòng không đổi: đạt
- Fingerprint trước/sau restore giống nhau: đạt
- Test phá dữ liệu (2d) làm fingerprint đổi: đạt (Cơ chế sinh mã MD5 thay đổi chính xác, cập nhật thành công UPDATE 10 bản ghi nghệ sĩ)
- Lưu ý: dump không gồm audio MinIO (bucket music-demo), cần backup riêng.

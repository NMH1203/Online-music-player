# PostgreSQL bằng Docker

## 1. Lệnh cần chạy

Cài Docker Desktop, bật Linux containers (Windows dùng WSL 2), mở Docker Desktop.
Hạ tầng mới và migration **V1–V8** nằm ở **D:\MobileAppPrj**; source Android vẫn ở
**D:\Online-music-player**. Hai bản Compose có thể dùng cùng project name `music-player`,
nên chạy DB từ MobileAppPrj để dùng đúng migration mới. Xem thêm
[MinIO, metadata mapping và seed 50 MP3 CC BY 4.0](documents/minio-demo-setup.md).

Mở PowerShell ở thư mục này, chạy:

```powershell
Set-Location D:\MobileAppPrj
docker compose up -d
```

Lần đầu Docker tải hai image. Compose tạo PostgreSQL 16, chờ DB nhận kết nối,
rồi container `migrate` chạy Flyway tạo bảng và seed 2 gói + 5 thể loại.
`-d` = chạy nền; đóng terminal thì database vẫn chạy.
Migration chạy sau khi DB khởi động, nên chỉ bắt đầu code/test khi kiểm tra thành công:

```sh
docker compose logs migrate
docker compose ps -a
```

DB mới áp dụng **8 migration**; DB hiện có chỉ áp dụng phần thiếu hoặc báo `up to date`.
`db` phải healthy; `migrate` **Exited (0)** là bình thường: đã chạy xong công việc.
Nếu migrate Exited khác 0, đọc log và sửa nguyên nhân trước khi dùng schema.
Khi có migration mới, chạy `docker compose up -d db`, rồi `docker compose run --rm migrate`.

| Migration | Nội dung |
|---|---|
| V1 | Schema nghiệp vụ |
| V2 | Seed Free/Premium và 5 thể loại |
| V3 | Bắt buộc user có profile tại cuối transaction |
| V4 | Album status active / archived |
| V5 | Đồng bộ độ dài status album/track |
| V6 | Bắt buộc đúng một subscription active cho mỗi user tại cuối transaction |
| V7 | Bảng track_audio_metadata: bucket, SHA-256, ETag, nguồn và giấy phép audio |
| V8 | Track `published` phải có metadata audio tại cuối transaction |

Nếu DB cũ có track `published` thiếu `track_audio_metadata`, V8 sẽ dừng. Bổ sung metadata thật hoặc đưa track chưa có audio về `draft` trước khi chạy lại migration.

Docker **image** là gói PostgreSQL/Flyway đã đóng sẵn; **container** là tiến trình chạy
image; **volume** là nơi giữ dữ liệu qua các lần dừng/tạo lại container.
**Compose** đọc `compose.yaml` để dựng các thành phần cùng cấu hình bằng một lệnh.
Không cần viết Dockerfile để chạy image PostgreSQL có sẵn.

## 2. Backend hoặc DBeaver kết nối thế nào?

Mặc định dành cho development local:

Khi tự host từ bản clone, copy `.env.example` thành `.env` và đặt
`POSTGRES_PASSWORD` riêng **trước lần chạy Docker đầu tiên**. Mật khẩu mặc định
dưới đây chỉ dành cho development local.

| Trường | Giá trị |
|---|---|
| Host | `localhost` |
| Port | `5432` |
| Database | `music_player` |
| Username | `music_app` |
| Password | `music_local_dev_only` |
| JDBC URL | `jdbc:postgresql://localhost:5432/music_player` |

Spring Boot chạy `java -jar` trên cùng máy:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/music_player
spring.datasource.username=music_app
spring.datasource.password=${DB_PASSWORD}
spring.jpa.hibernate.ddl-auto=validate
```

Đặt biến `DB_PASSWORD` bằng password DB trước khi chạy backend. Không dùng Hibernate
`create`/`create-drop` vì schema thuộc Flyway. Đừng commit mật khẩu máy dùng chung.

SQL trong `database/migrations` là nguồn migration duy nhất.
Khi khởi tạo backend, đóng gói **cùng các file này, nguyên nội dung** vào
`src/main/resources/db/migration/` và bật Flyway. Compose và Spring Boot cùng dùng
`flyway_schema_history`, nên migration đã chạy sẽ được kiểm tra và bỏ qua.
Không sửa migration V1–V8 sau khi đã chạy trên DB dùng chung; thêm version tiếp theo cho thay đổi.
Chưa có backend source trong repo hiện tại nên chưa thể cấu hình build đóng gói tự động.

Nếu backend sau này cũng chạy trong Compose, host trong JDBC là `db`, port `5432`,
không phải `localhost`. Android gọi backend `http://IP_MAY_LEADER:8080`,
**không kết nối PostgreSQL trực tiếp**.

## 3. Nhóm dùng chung DB của leader

Lệnh trên host DB trên máy chạy Docker; không tự tạo server Internet hay địa chỉ public.
Nhóm có hai cách: mỗi người chạy một DB local; hoặc các backend trên máy thành viên
kết nối DB của leader qua LAN. Để dùng LAN, trước lần khởi động đầu:

1. Tạo hoặc sửa `D:\MobileAppPrj\.env`, dùng `POSTGRES_DB=music_player`,
   `POSTGRES_USER=music_app`, `POSTGRES_PASSWORD=<password riêng>` và `DB_PORT=5432`;
   không ghi đè thông số DB hiện có nếu chưa đổi tương ứng trong PostgreSQL.
2. Đặt password riêng và `DB_BIND_ADDRESS=0.0.0.0`.
3. Leader chạy `docker compose up -d`; cho phép TCP cổng DB trong Windows Firewall
   trên mạng Private, giới hạn cho subnet/IP của nhóm.
4. Lấy IPv4 Wi-Fi của leader bằng `ipconfig`. Ví dụ `192.168.1.20` thì JDBC của
   thành viên là `jdbc:postgresql://192.168.1.20:5432/music_player`.

Các máy phải cùng LAN, leader bật máy và Docker; Wi-Fi phải cho thiết bị giao tiếp.
Không mở cổng DB ra Internet. Nếu chỉ backend leader kết nối DB, giữ bind mặc định
`127.0.0.1`, chỉ mở cổng backend `8080` cho điện thoại/nhóm.

Đã có PostgreSQL Windows service chiếm 5432? Đặt `DB_PORT=5433` trong `.env`,
backend trên máy host dùng port 5433; kết nối nội bộ Docker vẫn dùng 5432.
`POSTGRES_*` chỉ khởi tạo tài khoản/database khi volume rỗng. Đổi password trong
`.env` sau khi đã tạo volume không tự đổi password trong DB; cần dùng SQL
`ALTER ROLE` và đồng bộ cấu hình kết nối.

## 4. Lệnh thường dùng

```sh
# Kiểm tra container / đọc log
docker compose ps -a
docker compose logs db migrate

# Vào PostgreSQL (mặc định)
docker compose exec db psql -U music_app -d music_player
# Trong psql: \dt để xem bảng, \q để thoát

# Dừng, giữ dữ liệu
docker compose stop
# Gỡ container/network, vẫn giữ volume và dữ liệu
docker compose down
# Chạy lại
docker compose up -d
```

**Chỉ khi muốn xóa sạch DB test:** `docker compose down -v` xóa cả volume dữ liệu.
Lần chạy tiếp theo tạo DB mới và chạy lại mọi migration.

## 5. Seed nhạc thật và fixture metadata

Để nạp audio thật, dùng image AIStor đã tải và file license hợp lệ:

```powershell
Set-Location D:\MobileAppPrj
Set-ExecutionPolicy -Scope Process Bypass
.\scripts\Setup-Minio.ps1 -LicensePath D:\minio.license
.\scripts\Seed-DemoMusic.ps1
```

Setup tạo root credential trong `storage/.env`; dùng chúng đăng nhập
[Console](http://localhost:9001). Seed tải **50 MP3 Kevin MacLeod theo CC BY 4.0**,
đo metadata thật, kiểm SHA-256 và HTTP Range ở MinIO, rồi ghi track `published`
cùng `track_audio_metadata` trong một transaction. V8 kiểm tra quan hệ này tại cuối transaction.
`tracks.audio_object_key` ghép với `track_audio_metadata.bucket_name` xác định object;
bảng mới còn lưu hash, ETag, nguồn, license và attribution. Bucket `music-demo` là private.
Script tạo tài khoản backend chỉ đọc trong `storage/backend.env`; backend và playback
Android chưa được triển khai trong phần cấu hình này. Xem [hướng dẫn đầy đủ](documents/minio-demo-setup.md)
về cài đặt, kiểm tra, mapping và credit phải hiển thị cho người nghe.

**Fixture metadata cũ là bộ riêng chỉ dùng cho code/test:**

V1 tạo schema, V2 seed Free/Premium và thể loại. Catalog fixture tách riêng,
không tự nạp để tránh biến metadata giả thành nhạc được phép stream.

PowerShell, với tài khoản/database mặc định:

```powershell
Get-Content -Raw database/demo/seed_catalog.sql | docker compose exec -T db psql -v ON_ERROR_STOP=1 -U music_app -d music_player
```

Bash:

```sh
docker compose exec -T db psql -v ON_ERROR_STOP=1 -U music_app -d music_player < database/demo/seed_catalog.sql
```

Fixture tạo 10 nghệ sĩ, 10 album, 50 track và gắn 5 thể loại; chạy lại không nhân đôi.
Track ở trạng thái `draft`: object key, size và duration là placeholder, chưa có audio.
Giữ fixture ở draft; bộ seed audio thật ở trên dùng ID/key riêng và có thể cùng tồn tại.
Không chuyển 50 track placeholder sang published. Tổng số track DB có thể lớn hơn 50.
Không seed tài khoản có mật khẩu giả; đăng ký qua backend để tạo hash hợp lệ,
profile và subscription Free trong cùng transaction.

## 6. Quy tắc backend phải thực hiện

- Partial unique index cùng constraint trigger V6 yêu cầu **đúng một** subscription
  active cho mỗi user tại cuối transaction. Register phải tạo Free và profile cùng transaction.
- Nâng cấp/reset: khóa row `users` bằng `SELECT ... FOR UPDATE` để tuần tự hóa thao tác
  của cùng user; kiểm tra idempotency key và user sở hữu event; đóng subscription cũ,
  tạo subscription mới rồi ghi payment trong một transaction. Unique idempotency
  ngăn ghi trùng; backend xử lý retry/unique conflict và trả kết quả trước đó.
- Subscription hết hạn không tự đổi status vì thời gian trôi qua. Backend kiểm tra
  `starts_at <= now()` và `expires_at IS NULL OR expires_at > now()` khi lấy entitlement,
  rồi đóng Premium hết hạn và tạo Free trong transaction (lazy hoặc scheduled).
- Free tạo tối đa 3 playlist: khóa row user, đọc entitlement hợp lệ và đếm/tạo playlist
  trong cùng transaction để tránh hai request cùng vượt quota.
- History Free lọc 7 ngày khi truy vấn, không xóa lịch sử cũ. Premium xem toàn bộ.
- `counted_as_play` do backend tính theo `LEAST(30, CEIL(duration_seconds * 0.5))`.
  DB kiểm tra thời lượng không âm nhưng không biết client có tua hay không.
  Mỗi event phải gắn user xác thực, không tin user ID do client gửi.
- Favorites dùng khóa ghép và `ON CONFLICT DO NOTHING`. Đổi thứ tự playlist có thể
  dùng `SET CONSTRAINTS ALL DEFERRED` trong transaction để hoán đổi position.
- Status/role bổ sung dùng chữ thường theo SQL; Spring Boot cần map enum tương ứng.
  Độ dài varchar được chọn ở migration vì tài liệu mới chỉ quy định loại dữ liệu.
- Soft delete user; archive track. FK chặn xóa track có lịch sử/library/playlist.
- Khi tạo hoặc chuyển track sang `published`, ghi `track_audio_metadata` trong cùng transaction.
  V8 kiểm tra tại cuối transaction và chặn xóa/chuyển metadata khiến track published bị thiếu audio.

## 7. Backup trên Windows

Tránh redirect dump binary qua PowerShell. Tạo file trong container rồi copy ra:

```sh
docker compose exec db pg_dump -U music_app -d music_player -Fc -f /tmp/music-player.dump
docker compose cp db:/tmp/music-player.dump ./music-player.dump
```

Chép file sang máy/ổ khác và sao lưu cả object MinIO. Restore thử vào DB tạm:

```sh
docker compose exec db createdb -U music_app music_restore_test
docker compose cp ./music-player.dump db:/tmp/restore.dump
docker compose exec db pg_restore -U music_app -d music_restore_test --no-owner --exit-on-error /tmp/restore.dump
```

Nếu đổi DB/user trong `.env`, thay chúng trong các lệnh psql/backup tương ứng.

## 8. Kiểm tra SQL

Bash:

```sh
docker compose exec -T db psql -U music_app -d music_player < database/checks/smoke.sql
```

PowerShell, chạy sau V1–V8:

```powershell
Get-Content -Raw -Encoding utf8 database/checks/smoke.sql | docker compose exec -T db psql -v ON_ERROR_STOP=1 -U music_app -d music_player
Get-Content -Raw -Encoding utf8 database/checks/user_profile_relationship.sql | docker compose exec -T db psql -v ON_ERROR_STOP=1 -U music_app -d music_player
Get-Content -Raw -Encoding utf8 database/checks/active_subscription.sql | docker compose exec -T db psql -v ON_ERROR_STOP=1 -U music_app -d music_player
Get-Content -Raw -Encoding utf8 database/checks/audio_metadata.sql | docker compose exec -T db psql -v ON_ERROR_STOP=1 -U music_app -d music_player
Get-Content -Raw -Encoding utf8 database/checks/published_audio_metadata.sql | docker compose exec -T db psql -v ON_ERROR_STOP=1 -U music_app -d music_player
```

Kiểm tra entitlement seed, email không phân biệt hoa thường, profile/subscription bắt buộc,
retry favorite/history/payment, cascade playlist, giữ lịch sử và constraint metadata audio,
bao gồm yêu cầu metadata cho track published.
Các bản ghi kiểm tra
được rollback; không thay thế integration test cho logic Java chưa triển khai.

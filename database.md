# PostgreSQL bằng Docker

## 1. Lệnh cần chạy

Cài Docker Desktop, bật Linux containers (Windows dùng WSL 2), mở Docker Desktop.
Mở PowerShell/terminal ở thư mục repo có `compose.yaml`, chạy:

```sh
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

Log phải có `Successfully applied 2 migrations` (lần sau có thể là `up to date`).
`db` phải healthy; `migrate` **Exited (0)** là bình thường: đã chạy xong công việc.
Nếu migrate Exited khác 0, đọc log và sửa nguyên nhân trước khi dùng schema.
Chạy lại `docker compose up -d` để khởi động và áp dụng migration mới.

Docker **image** là gói PostgreSQL/Flyway đã đóng sẵn; **container** là tiến trình chạy
image; **volume** là nơi giữ dữ liệu qua các lần dừng/tạo lại container.
**Compose** đọc `compose.yaml` để dựng các thành phần cùng cấu hình bằng một lệnh.
Không cần viết Dockerfile để chạy image PostgreSQL có sẵn.

## 2. Backend hoặc DBeaver kết nối thế nào?

Mặc định dành cho development local:

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
Không sửa V1/V2 sau khi đã chạy trên DB dùng chung; thêm `V3__...sql` cho thay đổi.
Chưa có backend source trong repo hiện tại nên chưa thể cấu hình build đóng gói tự động.

Nếu backend sau này cũng chạy trong Compose, host trong JDBC là `db`, port `5432`,
không phải `localhost`. Android gọi backend `http://IP_MAY_LEADER:8080`,
**không kết nối PostgreSQL trực tiếp**.

## 3. Nhóm dùng chung DB của leader

Lệnh trên host DB trên máy chạy Docker; không tự tạo server Internet hay địa chỉ public.
Nhóm có hai cách: mỗi người chạy một DB local; hoặc các backend trên máy thành viên
kết nối DB của leader qua LAN. Để dùng LAN, trước lần khởi động đầu:

1. Copy `.env.example` thành `.env` (PowerShell: `Copy-Item .env.example .env`).
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

## 5. Seed metadata để code/test

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
Nhóm cần chuẩn bị nhạc hợp pháp, upload MinIO, cập nhật thông số thực và đặt
`status='published', published_at=now()` trước khi test playback/Home/recommendation.
Không seed tài khoản có mật khẩu giả; đăng ký qua backend để tạo hash hợp lệ,
profile và subscription Free trong cùng transaction.

## 6. Quy tắc backend phải thực hiện

- Partial unique index ngăn **hơn một** subscription active; không đảm bảo user luôn
  có một subscription. Register phải tạo Free và profile cùng transaction.
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

PowerShell:

```powershell
Get-Content -Raw database/checks/smoke.sql | docker compose exec -T db psql -U music_app -d music_player
```

Kiểm tra entitlement seed, email không phân biệt hoa thường, subscription active,
retry favorite/history/payment, cascade playlist và giữ lịch sử. Các bản ghi kiểm tra
được rollback; không thay thế integration test cho logic Java chưa triển khai.

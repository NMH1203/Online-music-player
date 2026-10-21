# Online Music Player

Repository này chứa ứng dụng Android giao diện mẫu, backend Spring Boot cho thư viện cơ bản, cấu hình PostgreSQL/Flyway và công cụ chuẩn bị nhạc demo trên MinIO. Auth, tích hợp Android với API và playback vẫn đang chờ các nhiệm vụ tiếp theo. Các lệnh bên dưới dành cho PowerShell trên Windows và chạy từ chính repository này.

## Hiện trạng

| Thành phần | Đã có trong repository |
|---|---|
| Android | Một Activity, thanh điều hướng và bốn màn hình Home, Search, Library, Profile. Nội dung các màn hình hiện là placeholder. |
| Dữ liệu | Docker Compose chạy PostgreSQL 16.13 và Flyway 11.20.2; migration V1–V8, SQL kiểm tra và fixture catalog. |
| Lưu trữ nhạc | Compose MinIO AIStor riêng, script thiết lập và script tải/seed 50 MP3 demo có thông tin giấy phép. Cần license AIStor hợp lệ để chạy. |
| Backend | `backend/` có API yêu thích track/album và nền tảng playlist; T04 sẽ kết nối principal xác thực vào API. |
| Tích hợp | Android chưa gọi API, chưa có đăng nhập, tìm kiếm thực hoặc playback. |

Hai giá trị BASE_URL trong cấu hình Android hiện là địa chỉ mẫu và chưa được mã nguồn sử dụng. Manifest đã khai báo quyền Internet, notification và foreground service, nhưng ứng dụng chưa có media playback service. Tính năng dự kiến được mô tả trong [phạm vi dự án](documents/online-music-player-project-brief.md) và [thiết kế kiến trúc/API](documents/mobile-system-architecture-and-flows.md); đó chưa phải chức năng đã triển khai.

## Cấu trúc chính

~~~text
Online-music-player/
├── android/                 Dự án Android Studio, module app và Gradle Wrapper
├── compose.yaml             PostgreSQL + Flyway
├── compose.minio.yaml       MinIO AIStor (Compose project riêng)
├── database/
│   ├── migrations/          Migration V1–V8, nguồn schema đang dùng
│   ├── checks/              Các bài kiểm tra SQL
│   └── demo/                Fixture draft, manifest 50 bài, hash và ghi công
├── scripts/                 Thiết lập MinIO, seed và kiểm tra nhạc demo
├── storage/                 Mẫu cấu hình backend; credential/license tạo cục bộ
└── documents/               Tài liệu thiết kế và hướng dẫn chi tiết
~~~

Các file **.env**, **storage/.env**, **storage/backend.env**, **storage/minio.license**, môi trường Python và cache audio không được commit.

## Backend API — Library foundation (T06)

Backend source nằm trong `backend/`. Cần JDK 17 trở lên và PostgreSQL local từ Compose.
Từ repository root, chạy database rồi build/chạy backend bằng Gradle Wrapper của dự án:

~~~powershell
docker compose up -d
.\android\gradlew.bat -p .\backend bootJar
.\android\gradlew.bat -p .\backend bootRun
~~~

API mặc định nghe tại `127.0.0.1:8080`. Có thể đặt `DB_URL`, `DB_USERNAME` và
`DB_PASSWORD` trong môi trường trước khi chạy. Backend dùng các migration tại
`database/migrations`; Compose vẫn là nơi thực thi Flyway. Danh sách endpoint và ranh
giới tích hợp JWT được ghi trong [backend README](backend/README.md). API `/me/...`
chỉ hoạt động sau khi T04 cấu hình principal có tên là UUID của user hiện tại.

## Chạy ứng dụng Android

Cần Android Studio, Android SDK Platform **35**, JDK tương thích Android Gradle Plugin **9.4.1** và thiết bị/emulator từ Android **8.0 (API 26)**. Dự án dùng Gradle Wrapper **9.6.0**, compileSdk/targetSdk **35**, minSdk **26** và Java source/target **11**. Không cần khởi động DB hoặc MinIO để xem bốn màn hình mẫu.

1. Trong Android Studio, mở thư mục **D:\Online-music-player\android** (không mở thư mục gốc repository làm Gradle project).
2. Cài SDK Platform 35, chọn Gradle JDK phù hợp, chờ Gradle sync. Android Studio thường tự tạo **android/local.properties** với đường dẫn SDK của máy.
3. Chọn module **app**, thiết bị/emulator và nhấn **Run**.

Build APK debug bằng PowerShell:

~~~powershell
Set-Location D:\Online-music-player\android
.\gradlew.bat :app:assembleDebug
~~~

APK sau khi build thành công nằm tại **D:\Online-music-player\android\app\build\outputs\apk\debug\app-debug.apk**. Có thể cài lên thiết bị đã kết nối bằng **.\gradlew.bat :app:installDebug**. Hai test Java hiện có là test mẫu của dự án, chưa kiểm thử các tính năng nghe nhạc.

Cấu hình release hiện tham chiếu **android/app/proguard-rules.pro** nhưng file đó chưa tồn tại; hướng dẫn trên chỉ dành cho bản debug.

## Chạy PostgreSQL và migration

Cần Docker Desktop đang chạy Linux containers. Mọi lệnh trong phần này chạy từ **D:\Online-music-player**:

~~~powershell
Set-Location D:\Online-music-player
docker compose config --quiet
docker compose up -d
docker compose ps -a
docker compose logs migrate
~~~

Mặc định Compose tạo database **music_player**, user **music_app**, mật khẩu local development **music_local_dev_only**, bind tại **127.0.0.1:5432**. Có thể tạo file **.env** ở root trước lần khởi tạo DB đầu tiên để đặt **POSTGRES_PASSWORD** riêng; repository hiện không có **.env.example**. Nếu đổi database hoặc user, sửa các lệnh psql bên dưới tương ứng. **DB_PORT** đổi cổng host, còn cổng trong Docker vẫn là 5432.

Compose này dùng tên project **music-player**. Nếu máy đã chạy Compose cùng tên từ **D:\MobileAppPrj**, hai thư mục có thể dùng chung container và volume; kiểm tra DB đang có trước khi đổi cấu hình hoặc seed dữ liệu.

Chờ **db** healthy và **migrate** kết thúc với **Exited (0)**. V1 tạo schema, V2 seed gói Free/Premium và thể loại, V3–V6 bổ sung trạng thái và ràng buộc dữ liệu, V7–V8 quản lý metadata audio cho track published. Khi có migration mới:

~~~powershell
docker compose up -d db
docker compose run --rm migrate
~~~

Không sửa nội dung migration đã áp dụng trên DB dùng chung; thêm version mới. Đổi **POSTGRES_PASSWORD** trong **.env** sau khi volume đã khởi tạo không tự đổi mật khẩu trong PostgreSQL.

Kiểm tra nhanh schema:

~~~powershell
docker compose exec db psql -U music_app -d music_player -c "SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank;"
Get-Content -Raw -Encoding utf8 .\database\checks\smoke.sql | docker compose exec -T db psql -v ON_ERROR_STOP=1 -U music_app -d music_player
~~~

Các bài kiểm tra khác nằm trong [database/checks](database/checks). Kết quả mong đợi của SQL check là thông báo PASS và ROLLBACK.

### Fixture catalog chỉ có metadata

Nếu cần dữ liệu mẫu cho việc viết truy vấn hoặc kiểm tra DB:

~~~powershell
Get-Content -Raw -Encoding utf8 .\database\demo\seed_catalog.sql | docker compose exec -T db psql -v ON_ERROR_STOP=1 -U music_app -d music_player
~~~

Fixture này tạo **10 nghệ sĩ, 10 album và 50 track draft**; object key, kích thước và thời lượng là placeholder, không có MP3 tương ứng. Không chuyển các track này sang published. Bộ nhạc có audio thật ở phần kế tiếp dùng bản ghi riêng và có thể cùng tồn tại với fixture.

## Chạy MinIO và seed nhạc demo

Phần này cần Docker Desktop, **Python 3.12 với pip**, Internet để tải MP3 và **file license MinIO AIStor hợp lệ**. Ví dụ dưới đây dùng **D:\minio.license**; thay bằng đường dẫn file license thực tế của máy:

~~~powershell
Set-Location D:\Online-music-player
.\scripts\Setup-Minio.ps1 -LicensePath 'D:\minio.license'
.\scripts\Seed-DemoMusic.ps1
~~~

Nếu PowerShell chặn chạy script, có thể dùng **Set-ExecutionPolicy -Scope Process Bypass** trong chính phiên PowerShell đó rồi chạy lại. **Setup-Minio.ps1** sao chép license vào **storage/minio.license**, tạo credential ngẫu nhiên trong **storage/.env** và chờ MinIO healthy. S3 API mặc định ở **http://127.0.0.1:9000**; Console ở [http://localhost:9001](http://localhost:9001). Dùng **MINIO_ROOT_USER** và **MINIO_ROOT_PASSWORD** trong **storage/.env** để đăng nhập Console.

**Seed-DemoMusic.ps1** tạo môi trường Python cục bộ, chạy DB/migration khi cần, tải và kiểm tra 50 MP3 được liệt kê trong **database/demo/sources.json** cùng hash khóa trong **audio-lock.json**, nạp bucket private **music-demo** và ghi track published kèm metadata audio vào DB. Script cũng tạo credential đọc audio cho backend tương lai trong **storage/backend.env**. Việc có script và manifest không có nghĩa là dữ liệu đã được seed trên máy đang dùng.

Một số chế độ hỗ trợ:

~~~powershell
.\scripts\Seed-DemoMusic.ps1 -DownloadOnly
.\scripts\Seed-DemoMusic.ps1 -VerifyOnly
.\scripts\Seed-DemoMusic.ps1 -Limit 3
~~~

**-DownloadOnly** chỉ tải/cache; **-VerifyOnly** kiểm tra dữ liệu đã seed; **-Limit** chạy với số bài giới hạn. Nguồn và yêu cầu ghi công nằm trong [ATTRIBUTION.md](database/demo/ATTRIBUTION.md). Khi có giao diện phát nhạc, phải hiển thị credit cho người nghe. Android hiện chưa thể phát các bài này vì chưa có backend stream hoặc playback.

## Dừng và sao lưu dữ liệu

Từ root repository, dừng các dịch vụ mà vẫn giữ volume:

~~~powershell
docker compose stop
docker compose --env-file storage/.env -f compose.minio.yaml stop
~~~

**docker compose down -v** xóa volume PostgreSQL của Compose chính. Tương tự, **down -v** với **compose.minio.yaml** xóa volume audio MinIO; chỉ dùng khi muốn bỏ dữ liệu.

Sao lưu database bằng dump binary trên PowerShell:

~~~powershell
docker compose exec db pg_dump -U music_app -d music_player -Fc -f /tmp/music-player.dump
docker compose cp db:/tmp/music-player.dump .\music-player.dump
~~~

Dump DB không chứa các object audio trong MinIO; cần sao lưu MinIO riêng. Nếu đã đổi database/user trong **.env**, thay tên trong các lệnh trên.

## Tài liệu trong repository

- [Thiết kế database](documents/database-design.md)
- [Kiến trúc và luồng dự kiến](documents/mobile-system-architecture-and-flows.md)
- [Hướng dẫn MinIO và mapping metadata](documents/minio-demo-setup.md)
- [Kết quả kiểm tra MinIO/seed đã ghi nhận](documents/minio-demo-verification.md)
- [Cấu hình Android](android/app/build.gradle.kts)
- [Compose PostgreSQL](compose.yaml) và [Compose MinIO](compose.minio.yaml)

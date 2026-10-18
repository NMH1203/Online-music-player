# Online Music Player — Hướng dẫn cài đặt và chạy

Ứng dụng nghe nhạc trực tuyến dành cho Android, phục vụ đồ án Mobile App của nhóm 6 thành viên. Phạm vi dự kiến gồm tài khoản, tìm kiếm, phát nhạc nền, gợi ý, thư viện, playlist, lịch sử, hồ sơ và Free/Premium với thanh toán giả lập.

Các lệnh bên dưới dùng **PowerShell trên Windows**. Cấu hình PostgreSQL/MinIO, migration **V1–V8** và bộ seed **50 MP3 được cấp phép CC BY 4.0** nằm tại **D:\MobileAppPrj**; mã nguồn Android vẫn nằm tại **D:\Online-music-player**. Backend và playback Android chưa được triển khai trong phần cấu hình này.

Hướng dẫn đầy đủ: [Cài MinIO, mapping metadata và seed nhạc demo](documents/minio-demo-setup.md).

## 1. Kiến trúc và thư mục

Theo thiết kế, Android gọi Spring Boot; backend truy vấn PostgreSQL và proxy audio từ MinIO, hỗ trợ HTTP Range.

~~~text
Android → Spring Boot :8080 → PostgreSQL :5432
                            → MinIO S3 API :9000
                              Console :9001
~~~

~~~text
D:\
├── MobileAppPrj\                              # Chạy DB, MinIO và seed tại đây
│   ├── compose.yaml
│   ├── compose.minio.yaml
│   ├── database\migrations\                  # V1–V8
│   ├── database\demo\                        # Fixture draft + manifest nhạc thật
│   ├── scripts\Setup-Minio.ps1
│   ├── scripts\Seed-DemoMusic.ps1
│   ├── storage\
│   └── documents\minio-demo-setup.md
└── Online-music-player\                      # Root project mở bằng Android Studio
    ├── app\
    ├── gradle\
    ├── gradle.properties
    ├── gradlew.bat
    └── build.gradle.kts
~~~

## 2. Chuẩn bị môi trường

| Công cụ | Yêu cầu |
|---|---|
| Android Studio | Bản tương thích Android Gradle Plugin 9.4 |
| JDK chạy Gradle | Tối thiểu 17; dùng JDK tương thích đi kèm IDE |
| SDK Platform | API **37**, theo compileSdk/targetSdk |
| SDK Build Tools | **36.0.0**, mặc định của AGP 9.4 |
| Platform-Tools | adb để cài APK và đọc log |
| Emulator / điện thoại | Android **8.0 / API 26** trở lên |
| Docker Desktop | Linux containers; Windows dùng WSL 2; mở Docker Desktop trước khi chạy DB |
| Internet | Tải Gradle, dependency, SDK và Docker images |
| DBeaver / pgAdmin | Tùy chọn, xem dữ liệu PostgreSQL |
| Python | Python 3.12 và pip để chạy seed MP3 |
| MinIO AIStor | Image đã có; cần file license hợp lệ cho Setup-Minio.ps1 |
| Backend JAR | Cần cho luồng ứng dụng hoàn chỉnh sau khi nhóm triển khai |

Cấu hình khóa trong repo: **AGP 9.4.1**, **Gradle Wrapper 9.6.0**, application ID **vn.edu.usth.online_music_player**, PostgreSQL image **postgres:16.13-bookworm**, Flyway image **redgate/flyway:11.20.2**.

Java source/target compatibility là **11**, nhưng đây là mức biên dịch source, không phải JDK để chạy Gradle. Theo [bảng tương thích AGP 9.4](https://developer.android.com/build/releases/agp-9-4-0-release-notes), AGP 9.4 yêu cầu Gradle 9.6.0, JDK tối thiểu 17 và hỗ trợ API 37. Dùng Wrapper, không cần cài Gradle toàn cục.

~~~powershell
Set-Location D:\Online-music-player
java -version
docker --version
docker compose version
~~~

## 3. Chạy PostgreSQL và migration

### 3.1. Cấu hình

Mặc định compose.yaml:

| Trường | Giá trị |
|---|---|
| Host trên Windows | localhost / 127.0.0.1 |
| Port | 5432 |
| Database | music_player |
| User | music_app |
| Password development | music_local_dev_only |
| JDBC trên Windows | jdbc:postgresql://localhost:5432/music_player |
| Host từ container cùng Compose | db:5432 |

Chạy DB từ **D:\MobileAppPrj** để dùng đủ V1–V8. Hai thư mục có thể cùng dùng Compose project name `music-player`; không coi chúng là hai DB độc lập. Mỗi người tự host nên tạo **`.env` riêng trước lần khởi tạo đầu tiên** từ file mẫu đã commit; nếu đã có DB, dùng đúng thông số hiện tại:

~~~powershell
Set-Location D:\MobileAppPrj
Copy-Item .env.example .env
# Mở .env và thay POSTGRES_PASSWORD bằng mật khẩu riêng trước khi chạy Docker.
~~~

Chỉ copy khi `.env` chưa tồn tại; không ghi đè cấu hình của DB đã khởi tạo. `.env` thực tế, credential MinIO và runtime đã được ignore; chỉ commit `.env.example` không chứa secret.

Cổng 5432 bị chiếm: đặt **DB_PORT=5433**, đổi JDBC trên Windows sang localhost:5433; cổng nội bộ Docker vẫn 5432.

### 3.2. Khởi động và kiểm tra

~~~powershell
Set-Location D:\MobileAppPrj
docker compose config --quiet
docker compose up -d
docker compose ps -a
docker compose logs migrate
~~~

Mong đợi **db healthy**, **migrate Exited (0)**. Migrate là container chạy một lần; Exited (0) nghĩa là thành công. DB mới áp dụng **8 migration**; DB cũ chỉ áp dụng phần thiếu hoặc báo đã cập nhật.

| Migration | Nội dung |
|---|---|
| V1 | Schema nghiệp vụ |
| V2 | Seed Free/Premium và 5 thể loại |
| V3 | Bắt buộc user có profile tại cuối transaction |
| V4 | Thêm album status active / archived |
| V5 | Đồng bộ độ dài status của album và track |
| V6 | Bắt buộc mỗi user có đúng một subscription active ở cuối transaction |
| V7 | Metadata object MinIO, SHA-256 và nguồn/giấy phép audio |
| V8 | Track `published` phải có `track_audio_metadata` tại cuối transaction |

~~~powershell
docker compose exec db psql -U music_app -d music_player -c "SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank;"
docker compose exec db psql -U music_app -d music_player -c "SELECT code, ad_free, max_playlists, history_days FROM plans ORDER BY code;"
docker compose exec db psql -U music_app -d music_player -c "SELECT code, name FROM genres ORDER BY code;"
~~~

Nếu đổi DB/user, thay music_player/music_app trong các lệnh. Khi có migration mới:

~~~powershell
docker compose up -d db
docker compose run --rm migrate
~~~

Giữ database/migrations là nguồn schema. Không sửa migration đã áp dụng trên DB dùng chung; thêm version tiếp theo.

### 3.3. Seed metadata

~~~powershell
Get-Content -Raw -Encoding utf8 .\database\demo\seed_catalog.sql | docker compose exec -T db psql -v ON_ERROR_STOP=1 -U music_app -d music_player
docker compose exec db psql -U music_app -d music_player -c "SELECT (SELECT count(*) FROM artists) AS artists, (SELECT count(*) FROM albums) AS albums, (SELECT count(*) FROM tracks) AS tracks;"
docker compose exec db psql -U music_app -d music_player -c "SELECT title, status, audio_object_key FROM tracks ORDER BY title LIMIT 5;"
~~~

Fixture tạo **10 nghệ sĩ, 10 album, 50 track draft**, gắn 5 thể loại; chạy lại không nhân đôi fixture. Đây là metadata với thông số placeholder, chưa có audio; không chuyển fixture này sang published. Bộ nhạc thật riêng dùng `Seed-DemoMusic.ps1` ở mục 5.1 và có thể cùng tồn tại với fixture.

### 3.4. Kiểm tra SQL

Chạy trên DB local/test đã áp dụng V1–V8:

~~~powershell
Get-Content -Raw -Encoding utf8 .\database\checks\smoke.sql | docker compose exec -T db psql -v ON_ERROR_STOP=1 -U music_app -d music_player
Get-Content -Raw -Encoding utf8 .\database\checks\user_profile_relationship.sql | docker compose exec -T db psql -v ON_ERROR_STOP=1 -U music_app -d music_player
Get-Content -Raw -Encoding utf8 .\database\checks\active_subscription.sql | docker compose exec -T db psql -v ON_ERROR_STOP=1 -U music_app -d music_player
Get-Content -Raw -Encoding utf8 .\database\checks\audio_metadata.sql | docker compose exec -T db psql -v ON_ERROR_STOP=1 -U music_app -d music_player
Get-Content -Raw -Encoding utf8 .\database\checks\published_audio_metadata.sql | docker compose exec -T db psql -v ON_ERROR_STOP=1 -U music_app -d music_player
~~~

Mong đợi **NOTICE: PASS: ...**, **ROLLBACK**, không có ERROR.

## 4. Build và chạy Android

### 4.1. Android Studio

1. **Open → D:\Online-music-player**, chờ sync Gradle.
2. Trong **SDK Manager**, cài Platform API 37, Build Tools 36.0.0, Platform-Tools và Emulator nếu cần.
3. **Settings → Build, Execution, Deployment → Build Tools → Gradle**: chọn Gradle JDK tương thích, tối thiểu 17.
4. Kiểm tra **local.properties** ở root; IDE thường tự tạo SDK path của máy. Không copy SDK path thành viên khác.

Ví dụ local.properties, thay username thực tế:

~~~properties
sdk.dir=C:/Users/<WINDOWS_USER>/AppData/Local/Android/Sdk
~~~

### 4.2. Bổ sung resource thiếu trước khi build

activity_main.xml hiện tham chiếu:

- **@navigation/nav_graph**: cần app/src/main/res/navigation/nav_graph.xml và Fragment đích.
- **@menu/bottom_nav_menu**: cần app/src/main/res/menu/bottom_nav_menu.xml; ID menu phải khớp destination trong graph.

Hai resource chưa có. Nhóm cần bổ sung để chạy điều hướng. Nếu chỉ mở màn hình tạm, phải sửa layout và MainActivity đồng bộ để bỏ tham chiếu.

**Lệnh build chỉ thành công sau khi project đủ resource và môi trường hợp lệ.**

### 4.3. Chạy từ IDE

1. **Device Manager**: tạo và bật emulator API 26 trở lên; hoặc dùng điện thoại Android 8.0 trở lên.
2. Điện thoại thật: bật **Developer options → USB debugging**, cắm USB, xác nhận quyền debug; cài driver hãng nếu Windows chưa nhận máy.
3. Chọn module **app**, variant **debug**, thiết bị đích → **Run**.
4. IDE build, cài APK và mở MainActivity. Lỗi build xem **Build Output**; crash xem **Logcat** theo package vn.edu.usth.online_music_player.


### 4.4. Build APK bằng PowerShell

Nếu terminal chưa dùng đúng JDK, đặt đường dẫn JDK thực tế. Ví dụ Android Studio cài mặc định:

~~~powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
Set-Location D:\Online-music-player
java -version
.\gradlew.bat --version
.\gradlew.bat :app:assembleDebug
~~~

APK khi build thành công: **D:\Online-music-player\app\build\outputs\apk\debug\app-debug.apk**.

Cài lên thiết bị đang kết nối:

~~~powershell
.\gradlew.bat :app:installDebug
~~~

Hoặc dùng adb; thay SDK path theo máy:

~~~powershell
$taskSdkPath = 'C:\Users\<WINDOWS_USER>\AppData\Local\Android\Sdk'
& "$taskSdkPath\platform-tools\adb.exe" devices
& "$taskSdkPath\platform-tools\adb.exe" install -r .\app\build\outputs\apk\debug\app-debug.apk
& "$taskSdkPath\platform-tools\adb.exe" shell am start -n vn.edu.usth.online_music_player/.MainActivity
~~~

devices cần hiển thị **device**. Nếu unauthorized, xác nhận trên điện thoại. Nhiều thiết bị: thêm `-s <SERIAL>`.

Kiểm tra sau khi project build được:

~~~powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:lintDebug
# Cần emulator/điện thoại:
.\gradlew.bat :app:connectedDebugAndroidTest
~~~

## 5. MinIO và backend

### 5.1. MinIO Docker và seed nhạc demo

Image AIStor đã tải cần file license hợp lệ và command `server /data`. Chạy tại **D:\MobileAppPrj**:

~~~powershell
Set-Location D:\MobileAppPrj
Set-ExecutionPolicy -Scope Process Bypass
.\scripts\Setup-Minio.ps1 -LicensePath D:\minio.license
.\scripts\Seed-DemoMusic.ps1
~~~

Setup tự tạo `storage/.env` chứa `MINIO_ROOT_USER` / `MINIO_ROOT_PASSWORD`, giữ tài khoản và volume khi chạy lại. Mở [Console local](http://localhost:9001), đọc hai giá trị trong file này để đăng nhập. **9000 là S3 API**, **9001 là Console**. Credential được đặt khi tạo container, không đặt trên image trong danh sách Images.

Seed tải **50 MP3 thật của Kevin MacLeod theo CC BY 4.0**, lưu vào bucket private `music-demo`, kiểm SHA-256, MIME, kích thước và HTTP Range, rồi ghi track `published` cùng `track_audio_metadata` trong một transaction. V8 kiểm tra metadata tại cuối transaction; fixture draft cũ vẫn giữ nguyên. Script cũng tạo credential backend chỉ đọc trong `storage/backend.env`.

Xem [hướng dẫn cài đặt, mapping metadata, kiểm tra và ghi công](documents/minio-demo-setup.md). Khi tích hợp ứng dụng, phải hiển thị credit cho người nghe; backend và giao diện playback/credits vẫn cần triển khai.

### 5.2. Backend Spring Boot

Backend cần REST API, PostgreSQL/Flyway, MinIO adapter và HTTP Range. Đóng gói **V1–V8 nguyên nội dung** từ D:\MobileAppPrj\database\migrations vào src/main/resources/db/migration/ hoặc dùng cùng nguồn SQL trong build. Compose/backend cùng kiểm tra flyway_schema_history.

Cấu hình datasource mẫu trong application.properties:

~~~properties
server.port=8080
spring.datasource.url=jdbc:postgresql://localhost:5432/music_player
spring.datasource.username=music_app
spring.datasource.password=${DB_PASSWORD}
spring.jpa.hibernate.ddl-auto=validate
spring.flyway.enabled=true
~~~

Nếu dùng JPA, giữ Hibernate **validate**, Flyway quản lý schema. Nếu backend chạy trong Compose, datasource dùng **db:5432**.

Cấu hình MinIO cho backend có trong `storage/backend.env` và mẫu `storage/application-storage.example.yaml`; cần triển khai binding `storage` và MinioClient trong Spring Boot. JWT HS256, Actuator, REST API và HTTP Range proxy vẫn cần implementation. Android không nhận credential storage.

Khi nhóm đã build executable JAR, mở PowerShell riêng. Ví dụ file nhóm cung cấp tên backend\music-player-backend.jar:

~~~powershell
Set-Location D:\Online-music-player
$env:DB_PASSWORD = 'music_local_dev_only'
java -jar .\backend\music-player-backend.jar --server.port=8080 --server.address=0.0.0.0
~~~

**Đường dẫn JAR là ví dụ, chưa tồn tại.** Thay đường dẫn/password, thêm cấu hình storage/JWT đúng backend. JDK phải phù hợp Java/Spring Boot nhóm chọn. Bind 0.0.0.0 cho phép LAN; chỉ test host/emulator có thể bind local.

Khi backend đã bật Actuator:

~~~powershell
Invoke-RestMethod http://localhost:8080/actuator/health
~~~

Mong đợi **status: UP**. Actuator chưa được thêm thì endpoint không tự có. Health UP chưa đủ chứng minh streaming; kiểm tra log DB/MinIO và phát thử.

### 5.3. Contract và quy tắc tích hợp

Endpoint đề xuất trong tài liệu:

| Luồng | API chính |
|---|---|
| Auth | POST /auth/register, /auth/login, /auth/refresh, /auth/logout |
| Catalog | GET /home, /search?q=, /tracks/{id}, /albums/{id}, /artists/{id} |
| Playback | GET /tracks/{id}/playback, /tracks/{id}/stream |
| Library/playlist | /me/library/tracks/{id}, /me/library/albums/{id}, /me/playlists và endpoint chi tiết |
| History | POST /listening-events, GET /me/history |
| Profile/gợi ý | GET/PATCH /me/profile, GET /me/stats, GET /recommendations |
| Subscription | GET /me/entitlements, POST /demo/payments, POST /demo/reset-plan |


## 6. Android kết nối backend local

Chỉ áp dụng khi có backend và code network. **BuildConfig.BASE_URL đã khai báo nhưng chưa có API client sử dụng.**

### 6.1. Địa chỉ theo thiết bị

| Nơi chạy app | BASE_URL |
|---|---|
| Android Emulator chuẩn | http://10.0.2.2:8080/ |
| Điện thoại cùng Wi-Fi | `http://<IPV4_SERVER>:8080/`, ví dụ `http://192.168.1.20:8080/` |
| USB + adb reverse | http://127.0.0.1:8080/ sau khi tạo reverse port |

Emulator dùng **10.0.2.2** truy cập host theo [hướng dẫn Android Developers](https://developer.android.com/develop/ui/views/layout/webapps/access-local-server). localhost thông thường là chính thiết bị.

Chạy **ipconfig** lấy IPv4 Wi-Fi/Ethernet server. Thiết bị cùng LAN; backend lắng nghe địa chỉ LAN; cho phép TCP **8080** trong Windows Firewall mạng Private. DB/MinIO giữ localhost nếu chỉ backend cùng máy sử dụng.

Qua USB, với adb trong PATH:

~~~powershell
adb reverse tcp:8080 tcp:8080
~~~

Nếu chưa có PATH, gọi adb bằng đường dẫn mục 5.4; nhiều thiết bị thêm `-s <SERIAL>`.

### 6.2. Đổi BASE_URL debug

app/build.gradle.kts hiện khai báo debug **https://dev.api.musicplayer.com/**, release **https://api.musicplayer.com/**. Repo không chứng minh domain này có API hoạt động.

Thay dòng trong block debug hiện có, ví dụ emulator:

~~~kotlin
buildConfigField("String", "BASE_URL", "\"http://10.0.2.2:8080/\"")
~~~

Điện thoại thật dùng IP LAN. Giữ dấu **/** cuối URL cho Retrofit, sync/build lại. API client cần sử dụng BuildConfig.BASE_URL; chỉ đổi biến không tự tạo kết nối. Nếu backend có prefix /api/, đồng bộ base URL/relative endpoint theo contract.

### 6.3. Internet permission và HTTP debug

Manifest hiện chưa có Internet permission. Thêm trước application trong app/src/main/AndroidManifest.xml khi triển khai network:

~~~xml
<uses-permission android:name="android.permission.INTERNET" />
~~~

Tạo **app/src/debug/AndroidManifest.xml** để test HTTP LAN:

~~~xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application android:usesCleartextTraffic="true" />
</manifest>
~~~

Android mặc định chặn HTTP cleartext với app target API 28 trở lên theo [Network security configuration](https://developer.android.com/privacy-and-security/security-config). Release cần HTTPS. Quyền foreground service/media playback và Media3 service cần bổ sung khi triển khai phát nền.

## 7. Thứ tự chạy toàn hệ thống và demo

Sau khi hoàn thiện phần thiếu:

1. **Docker Desktop → PostgreSQL → Flyway** tại MobileAppPrj: healthy, V1–V8 thành công.
2. **MinIO**: chạy Setup-Minio.ps1 và Seed-DemoMusic.ps1; 50 audio CC BY 4.0, object key khớp DB.
3. **Backend**: DB/MinIO kết nối được, health UP, API trên 8080.
4. **Android**: resource đầy đủ, BASE_URL/quyền network đúng, build debug và chạy.
5. **Đăng ký tài khoản và kiểm thử xuyên suốt**.

Checklist mục tiêu demo:

- Đăng ký/đăng nhập, profile Free mặc định.
- Home/tìm kiếm có catalog published, chọn bài phát audio thật.
- Play/pause/tua/chuyển bài/queue/mini player đồng bộ; Range hợp lệ trả **206 Partial Content**.
- Phát khi chuyển app/khóa màn hình, điều khiển notification.
- Favorite bài/album, tạo playlist/thêm bài và kiểm tra dữ liệu khi mở lại.
- History idempotent theo client_event_id; ngưỡng lượt nghe **min(30 giây, 50% duration)**, không cộng thời gian tua.
- Free: tối đa 3 playlist, history 7 ngày, quảng cáo thử nghiệm giữa bài.
- Premium giả lập: bỏ quảng cáo, playlist không giới hạn, toàn bộ history; reset Free để demo lại.
- Gợi ý cho người dùng mới/đã nghe; Free/Premium cùng cơ chế.

## 8. Dừng, chạy lại, backup

Chạy tại D:\MobileAppPrj:

~~~powershell
docker compose ps -a
docker compose logs db migrate
# Dừng, giữ dữ liệu:
docker compose stop
# Chạy lại DB và migration:
docker compose up -d db
docker compose run --rm migrate
# Gỡ container/network, giữ named volume:
docker compose down
~~~

MinIO: `docker compose --env-file storage/.env -f compose.minio.yaml stop`. Backend chạy foreground: **Ctrl+C** ở terminal tương ứng. Dừng app bằng IDE/thiết bị.

**Chỉ khi chủ động muốn xóa sạch DB test**:

~~~powershell
docker compose down -v
~~~

down -v xóa volume PostgreSQL và dữ liệu; lần sau phải tạo schema/seed/tài khoản lại. Volume MinIO thuộc Compose riêng `compose.minio.yaml`; không dùng `down -v` trên Compose đó khi muốn giữ audio.

Backup binary an toàn trên PowerShell: dump trong container rồi copy ra.

~~~powershell
docker compose exec db pg_dump -U music_app -d music_player -Fc -f /tmp/music-player.dump
docker compose cp db:/tmp/music-player.dump .\music-player.dump
~~~

Chép dump sang ổ/máy khác; backup cả MinIO theo phương thức bản triển khai đang dùng. DB dump không chứa audio. Không commit dump chứa dữ liệu tài khoản.

Restore thử vào DB test mới:

~~~powershell
docker compose exec db createdb -U music_app music_restore_test
docker compose cp .\music-player.dump db:/tmp/restore.dump
docker compose exec db pg_restore -U music_app -d music_restore_test --no-owner --exit-on-error /tmp/restore.dump
~~~

Tên music_restore_test phải chưa tồn tại hoặc dùng tên khác. Kiểm tra bảng/dữ liệu và mở audio từ backup trước demo.

## 9. Lỗi thường gặp

| Triệu chứng | Xử lý |
|---|---|
| Thiếu gradlew.bat / module app | Build từ D:\Online-music-player |
| Gradle báo JDK sai | Kiểm tra Gradle JDK, JAVA_HOME, java -version; tối thiểu 17 |
| SDK location not found | Kiểm tra SDK Manager và local.properties |
| Thiếu android-37 | Cài Platform API 37, dùng IDE/SDK Manager tương thích |
| Không tải plugin/dependency | Kiểm tra Internet/proxy/Offline mode, google() và mavenCentral() |
| Thiếu nav_graph / bottom_nav_menu | Bổ sung resource/Fragment mục 5.2; source hiện đang thiếu |
| Build yêu cầu bật AndroidX | Nếu gặp, thêm android.useAndroidX=true vào gradle.properties rồi sync |
| App crash | Đọc Logcat, kiểm tra destination class/layout/theme và stack trace |
| Docker daemon chưa chạy | Mở Docker Desktop, chờ engine, kiểm tra Linux containers |
| Cổng 5432 bị chiếm | DB_PORT=5433, chạy lại Compose và đổi JDBC trên Windows |
| migrate Exited (0) | Bình thường, migration thành công |
| Flyway lỗi / migrate khác 0 | Đọc log migrate, kiểm tra checksum/schema/quyền/đường dẫn SQL |
| Đổi .env password mà DB từ chối | POSTGRES_* chỉ khởi tạo lúc volume rỗng; ALTER ROLE đổi DB password rồi đồng bộ config |
| User thiếu profile | Tạo user/profile cùng transaction theo V3 |
| Android không gọi API | Kiểm tra backend/BASE_URL/quyền Internet/IP/bind/firewall/Wi-Fi |
| CLEARTEXT ... not permitted | Debug manifest mục 7.3, build/cài lại |
| Health trả 404 | Backend chưa có/bật Actuator hoặc path khác; repo chưa có backend |
| Có metadata nhưng không phát | Kiểm tra draft/published, audio object/bucket/credential/thông số |
| Không tua được | Kiểm tra Range, Content-Range/Content-Length, byte range từ MinIO |
| adb không nhận máy | USB debugging, quyền RSA, driver, adb devices |

Nếu đổi POSTGRES_* sau khi volume đã có dữ liệu, các biến này không tự đổi user/database/password hiện hữu. Không xóa volume để xử lý nếu cần giữ dữ liệu.

### Database dùng chung qua LAN

Nếu thành viên chạy backend riêng nhưng dùng DB leader: đặt **DB_BIND_ADDRESS=0.0.0.0** trên leader, password riêng, chạy lại Compose; chỉ mở cổng DB cho IP/subnet nhóm trên mạng Private. JDBC thành viên: `jdbc:postgresql://<IP_LEADER>:<DB_PORT>/music_player`.

Nếu nhóm chỉ gọi backend leader, giữ DB/MinIO localhost, chỉ mở 8080. Không mở DB ra Internet; leader phải bật máy/Docker khi nhóm dùng.

## 10. Tài liệu nguồn

- [Phạm vi dự án](../Online-music-player/documents/online-music-player-project-brief.md).
- [Kiến trúc, luồng và API đề xuất](../Online-music-player/documents/mobile-system-architecture-and-flows.md).
- [Thiết kế database](../Online-music-player/documents/database-design.md).
- [Hướng dẫn database hiện tại](database.md).
- [Compose PostgreSQL](compose.yaml).
- [MinIO, metadata mapping và seed nhạc CC BY 4.0](documents/minio-demo-setup.md).
- [Android build](../Online-music-player/app/build.gradle.kts).
- [Version catalog](../Online-music-player/gradle/libs.versions.toml).
- [Gradle Wrapper](../Online-music-player/gradle/wrapper/gradle-wrapper.properties).

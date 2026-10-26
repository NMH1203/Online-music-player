# Phần Tiệp: Premium, Ads và Auth/Profile

## Phạm vi triển khai

| Mã | Chức năng |
|---|---|
| T07 | Plan, entitlement, subscription và thanh toán giả lập idempotent |
| T13 | Profile Free/Premium, quyền lợi và paywall Android |
| T17 | Refresh/logout, token expiry, cập nhật tên/bio và trạng thái lỗi |
| T22 | Quảng cáo mở app và audio ad giữa hai bài cho Free |
| T23 | Nâng cấp Premium 30 ngày, ngày bắt đầu/hết hạn và reset về Free |
| T30 | Kiểm thử API, dữ liệu, session và bộ kiểm thử Android |
| T36 | Kịch bản demo lặp hai vòng, hướng dẫn và báo cáo kiểm thử |

T17 được giao cho Tiệp trong bảng Projects. Nhánh gốc chưa có T04/T10, nên bổ sung đăng ký/đăng nhập tối thiểu vào backend chính và Profile để có thể dùng toàn bộ luồng.

Dùng schema V1–V8 hiện có. Không sửa migration của Hành, không thêm bảng và không sửa dữ liệu nhóm. Backend chính ở `backend/`; `backend/catalog-api/` là module catalog riêng đã tồn tại trước thay đổi này.

## Quyền lợi

| Quyền lợi | Free | Premium |
|---|---|---|
| Quảng cáo | Khi mở app và giữa hai bài | Không |
| Playlist | Tạo tối đa 3 | Không giới hạn |
| Lịch sử nghe | 7 ngày gần nhất | Toàn bộ, phân trang 50 dòng |

Backend là nguồn quyền quyết định. Cache Android chỉ phục vụ hiển thị.
Premium demo có thời hạn 30 ngày. Hết hạn sẽ áp dụng giới hạn Free ngay ở quota;
lần đọc entitlement tiếp theo đóng subscription hết hạn và tạo Free trong cùng transaction.
Reset giữ các playlist cũ, nhưng chặn tạo mới nếu tài khoản Free đã có từ 3 playlist trở lên.

## Chạy trên máy phát triển

1. Mở thư mục `android/` bằng Android Studio. Dùng Android SDK 35, JDK đi kèm Android Studio.
2. Khởi động PostgreSQL và áp dụng migration theo README gốc:
   `docker compose up -d`.
3. Cấu hình MinIO theo `documents/minio-demo-setup.md`. Backend hiện có yêu cầu credential MinIO lúc khởi động.
4. Chạy từ thư mục repo:

```powershell
.\scripts\run-tiep-backend.ps1 -Demo
```

Script nạp `storage/backend.env` nếu có và tạo JWT secret ngẫu nhiên tại
`runtime/auth-jwt-secret.local` (đã được Git bỏ qua). Không hiển thị secret.
Có thể đặt `AUTH_JWT_SECRET` từ môi trường trước khi chạy để dùng secret riêng.
Không dùng secret trong báo cáo kiểm thử cho môi trường thực.

Mặc định API bind `127.0.0.1:8080`. Android emulator gọi `http://10.0.2.2:8080/`.
Để kiểm thử điện thoại qua USB, dùng `adb reverse tcp:8080 tcp:8080` và build với:

```powershell
cd android
.\gradlew.bat :app:assembleDebug -PmusicApiUrl=http://127.0.0.1:8080/
```

URL phải có dấu `/` cuối. Đối với thiết bị qua LAN, dùng địa chỉ LAN của máy chủ và
đặt `SERVER_ADDRESS` phù hợp. Bản release vẫn dùng HTTPS URL cấu hình gốc.

`DEMO_ENABLED` mặc định là false. Chỉ `-Demo`/môi trường `DEMO_ENABLED=true` bật hai endpoint thanh toán và reset.
Không có thanh toán thật, thông tin thẻ hoặc SDK quảng cáo bên ngoài.
APK debug: `android/app/build/outputs/apk/debug/app-debug.apk`.

## API

Mọi API `/me/...`, `/demo/...` và playback dùng `Authorization: Bearer <accessToken>`.
Người dùng được lấy từ JWT đã xác minh; client không gửi user id để đổi quyền.

| Method | Endpoint | Nội dung |
|---|---|---|
| POST | /auth/register | email, password (8–72 ký tự và tối đa 72 byte UTF-8), displayName |
| POST | /auth/login | email, password |
| POST | /auth/refresh | refreshToken |
| POST | /auth/logout | refreshToken; lặp lại an toàn |
| GET | /plans | Danh sách gói đang bật |
| GET | /me/profile | id, email, displayName, bio |
| PATCH | /me/profile | displayName (1–100), bio (tối đa 500) |
| GET | /me/entitlements | subscriptionId, planCode, planName, adFree, maxPlaylists, historyDays, startsAt, expiresAt, demoEnabled |
| GET | /me/subscription | Cùng cấu trúc entitlement |
| GET | /me/ads/policy | showAppOpen, playBetweenTracks |
| GET | /me/history?page=0 | Lịch sử đã được lọc theo gói trên server |
| POST | /demo/payments | idempotencyKey UUID, outcome: success hoặc failed |
| POST | /demo/reset-plan | Không có body; reset người đang đăng nhập |

Ví dụ yêu cầu thanh toán giả lập:

```json
{"idempotencyKey":"4fd420c5-af42-4cae-839f-bda15d1b7eef","outcome":"success"}
```

Android lưu key của giao dịch chưa nhận kết quả để thử lại sau lỗi mạng/khởi động lại.
Cùng key, cùng user và cùng outcome trả lại payment cũ. Đổi user/outcome trả 409.
Gửi lại payment cũ sau reset không kích hoạt Premium lần nữa.
Nhiều yêu cầu song song dùng advisory lock và user row lock, chỉ tạo một payment.
Failure không nâng cấp. Amount luôn 0 theo constraint hiện có.

## Phiên đăng nhập

- Mật khẩu dùng BCrypt cost 12. JWT HS256 hết hạn sau 15 phút, xác minh issuer/audience và family còn hoạt động.
- Refresh token 32 byte ngẫu nhiên, hết hạn 7 ngày; DB chỉ giữ SHA-256 hash.
- Refresh xoay vòng token. Dùng lại token đã xoay sẽ thu hồi cả family và trả 401.
- Android giữ access token trong RAM; refresh token dùng AES-GCM với khóa Android Keystore.
- 401 đồng thời chỉ có một luồng refresh; các yêu cầu còn lại dùng access token mới.
- Yêu cầu của tài khoản cũ không được gửi lại với token của tài khoản mới.
- Logout xóa phiên cục bộ và dừng player. Yêu cầu thu hồi được lưu mã hóa trước khi gửi; khi mất mạng sẽ thử lại lúc restore/login.
- Dữ liệu token và hàng đợi thu hồi bị loại khỏi cloud backup/device transfer.
- Sửa tên trống/độ dài không hợp lệ bị chặn cả UI và server.

## Quảng cáo

App-open là dialog quảng cáo mô phỏng, một lần cho tài khoản Free trong mỗi lần chạy Activity ViewModel mới.
Việc xoay màn hình không hiển thị quảng cáo lần nữa.

Audio ad là lời giới thiệu tiếng Anh do giọng đọc Windows tạo cục bộ, lưu tại
`res/raw/demo_audio_ad.wav`. Player kiểm tra entitlement khi bài kết thúc hoặc người dùng chọn bài kế tiếp.
Free chèn ad trước bài tiếp theo; Premium đi thẳng tới bài tiếp theo.
Không tự ngắt một bài đang phát khi đổi Premium về Free.
Nâng cấp khi đang phát ad sẽ chuyển ngay sang bài kế tiếp.
Quảng cáo dùng cùng ExoPlayer/MediaSession nên giữ audio focus và điều khiển phát nền.
Khi không xác minh được gói do mạng lỗi, dừng ở ranh giới bài và cho thử lại bằng nút Tiếp.

## Demo hai vòng

1. Mở Profile, tạo tài khoản rồi đăng nhập. Kiểm tra Free: quảng cáo, 3 playlist, 7 ngày lịch sử.
2. Chỉnh sửa tên/bio. Rời Profile và quay lại để xác nhận dữ liệu được lưu.
3. Tạo 3 playlist; thử playlist thứ 4 phải bị chặn.
4. Phát queue ít nhất hai bài đã seed trên MinIO: nghe hết bài đầu rồi mới có ad.
5. Profile → Khám phá Premium → Mô phỏng thất bại: vẫn Free.
6. Chọn Kích hoạt mô phỏng: thấy Premium, ngày bắt đầu/hết hạn, không quảng cáo, tạo được playlist thứ 4.
7. Kiểm tra lịch sử cũ hơn 7 ngày nếu tài khoản có dữ liệu; backend không xóa lịch sử khi đổi gói.
8. Reset về Free: playlist cũ còn, tạo mới bị chặn nếu vượt quota; bài đang nghe không bị cắt.
9. Lặp bước 5–8 lần thứ hai.
10. Đăng xuất: player dừng, thư viện yêu cầu đăng nhập. Mở app lại kiểm tra restore và refresh.
11. Thử mất mạng, bấm lại cùng lựa chọn thanh toán khi mạng hồi phục; không tạo subscription trùng.
12. Thử xoay màn hình khi tải hồ sơ/nâng cấp; không phát sinh lần thanh toán mới.

Phát nhạc thực tế cần backend, database, MinIO, track published và thiết bị/emulator hoạt động.
Không đánh dấu đạt các bước thiết bị nếu chỉ mới build APK.

## Kiểm thử

```powershell
.\android\gradlew.bat -p backend test bootJar
cd android
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest
# Chỉ chạy khi adb devices có thiết bị online:
.\gradlew.bat :app:connectedDebugAndroidTest
```

Backend tests dùng PostgreSQL 16 tạm thời riêng và nạp V1–V8; không dùng DB nhóm.
Xem kết quả và giới hạn xác minh trong `documents/test-reports/T30-T36-premium-ads.md`.

Nguồn yêu cầu: [bảng Projects](https://docs.google.com/spreadsheets/d/1DIqD4eDws3rVoa_hYOMlRwDsPTEUH-4qu16o3hA6OO0/edit?gid=2099048865#gid=2099048865).
Tham khảo kỹ thuật: [Media3 ExoPlayer](https://developer.android.com/reference/androidx/media3/exoplayer/ExoPlayer),
[Spring Security JWT](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html),
[PostgreSQL nhúng cho test](https://github.com/zonkyio/embedded-postgres).

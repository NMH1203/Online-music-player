# T30/T36 — Premium, Ads và Session
Ngày kiểm tra: 26/10/2026.

## Kết quả đã chạy

- Backend: 17 test qua, 0 lỗi, 0 bỏ qua.
  - 12 test mới trong PremiumIntegrationTest trên PostgreSQL 16.13 riêng; nạp nguyên V1–V8.
  - 5 test Library/Playlist có sẵn tiếp tục qua.
- Spring Boot khởi động với HTTP server thật trên cổng ngẫu nhiên; POST /auth/register trả 200 và access token.
- Android: build APK debug thành công; 5 unit test có sẵn qua.
- Bộ Android instrumentation gồm 9 test mới đã được biên dịch thành APK test.
- Android lint: 0 error. Các cảnh báo còn lại gồm chuỗi chưa tách resource, API/resource có sẵn và cảnh báo style; chưa coi đây là bản release.
- Kiểm tra cú pháp script chạy backend và git diff whitespace đã qua.

## Ma trận backend

| Trường hợp | Kết quả |
|---|---|
| Free → Premium → Free hai vòng | PASS |
| Gửi lại payment, kể cả sau reset | PASS; không tạo subscription mới |
| 6 request đồng thời cùng idempotency key | PASS; chỉ một payment |
| Cùng key khác tài khoản/outcome | PASS; 409 |
| Thanh toán failed và demo bị tắt | PASS; không cấp Premium |
| Premium hết hạn | PASS; quota Free ngay, entitlement tạo Free hợp lệ |
| Reset với 4 playlist | PASS; giữ playlist cũ và chặn tạo mới |
| History Free 7 ngày / Premium toàn bộ | PASS; lọc tại server |
| Refresh rotation và phát hiện replay | PASS; thu hồi cả family |
| Logout lặp lại | PASS; thu hồi access/refresh |
| Sai password, hết hạn refresh | PASS |
| Profile validation, auth 401, ad policy | PASS |
| Khởi động ứng dụng Spring Boot thực | PASS |

## Android instrumentation đã viết, chưa chạy được

BoundaryAdsTest (5): ad sau khi bài kết thúc; Premium bỏ ad; đổi về Free không cắt bài;
lỗi mạng và thử lại; nâng cấp khi đang phát ad.

SessionManagerTest (4): Keystore lưu mã hóa và restore; 401 đồng thời chỉ refresh một lần;
refresh lỗi tạm thời/401; logout và chặn retry với phiên khác.

Môi trường có Android Studio, SDK 35 và AVD API 37, nhưng thiếu Android Emulator hypervisor driver.
Emulator tăng tốc báo không khởi động được; thử chế độ phần mềm cũng không xuất hiện thiết bị online trong adb.
Không cài driver hoặc thay cấu hình hệ thống. Chưa có kết quả PASS cho instrumentation, UI, audio focus,
phát nền, xoay màn hình hoặc kiểm thử hai thiết bị.

## Cách hoàn tất nghiệm thu thiết bị

Kết nối điện thoại bằng USB (USB debugging) hoặc dùng emulator đã có hypervisor, rồi chạy:

```powershell
cd android
.\gradlew.bat :app:connectedDebugAndroidTest
```

Sau đó thực hiện hai vòng demo trong ../tiep-premium-ads.md với PostgreSQL/MinIO thật,
nhạc published đã seed và backend bật DEMO_ENABLED. Kiểm tra riêng phát nền, notification,
tai nghe, mất mạng, expiry và xoay màn hình trên hai thiết bị. Không thay đổi trạng thái bảng deadline tự động.

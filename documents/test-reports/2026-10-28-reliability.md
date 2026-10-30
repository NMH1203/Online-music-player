# Báo cáo sửa lỗi và bổ sung độ tin cậy — 28/10/2026

Đã sửa trên nhánh `codex/reliability-fixes`, dựa trên `main` ở commit `50c13df`.
Mã nguồn sửa lỗi và báo cáo được quản lý trên nhánh `codex/reliability-fixes`; việc gộp vào `main` thực hiện qua Pull Request.

## Kết quả theo nhận xét

| Nhận xét | Thay đổi |
|---|---|
| Mất lượt nghe khi lỗi mạng | Thêm ListeningOutbox: lưu sự kiện đã kết thúc vào SharedPreferences trước khi gửi. Giữ nguyên clientEventId và dữ liệu khi retry; server xử lý idempotency. Retry lỗi mạng, 408, 429, 5xx; giữ sự kiện khi 401/403. Các lỗi dữ liệu 4xx khác được bỏ để không chặn hàng đợi. |
| Đổi tài khoản có thể gửi nhầm lượt nghe | Mỗi sự kiện gắn userId cục bộ lúc bắt đầu nghe. Chỉ gửi cho tài khoản tương ứng; request giữ generation của phiên và interceptor từ chối khi đã đổi tài khoản. userId cục bộ không được gửi để quyết định danh tính server. |
| sourceContext luôn queue | Gắn nguồn màn hình vào MediaItem extras; playlist truyền context=playlist và sourcePlaylistId. Nguồn được chụp lúc yêu cầu phát, giữ qua việc tải metadata và xin quyền notification. |
| Kẹt ở cuối bài khi entitlement lỗi | Thêm timeout 3 giây trên luồng giao diện và request. Nếu thất bại: dùng cache Premium được server xác minh trong 15 phút và chưa hết hạn; nếu không có thì dùng Free và chèn audio ad cục bộ. Callback cũ không chuyển bài lần hai. |
| Home mất cá nhân hóa với token hết hạn | Header có token sai/hết hạn trả 401, cho Android Authenticator refresh. Home không có Authorization vẫn trả 200 cho khách. |
| MinIO thiếu file báo lỗi muộn | Mở GetObject trước khi đặt header thành công. NoSuchKey/NoSuchObject trả 404; lỗi kho lưu trữ trả 503. Đọc stream đồng bộ trong try-with-resources, giữ Range 200/206/416; đóng input kể cả client ngắt kết nối. |
| Tăng popularity bằng nhiều eventId | Khóa theo user trước khi ghi; giới hạn 120 sự kiện mới/phút/tài khoản; chặn thời gian tương lai quá 120 giây và thời lượng nghe vượt khoảng thời gian khai báo (dung sai 2 giây). Khoảng nghe chồng với một lượt đã tính không tăng popularity lần nữa. Retry eventId cũ vẫn trả kết quả cũ kể cả chạm rate limit. |
| Tìm kiếm % / _ / backslash | Escape ký tự wildcard trong cả /search và /tracks?query=. Cả hai giới hạn từ khóa đã trim ở 100 ký tự. |
| Like bài draft | Chỉ cho thêm bài published vào yêu thích; thư viện cũng lọc những bài sau đó bị bỏ publish. |
| Ảnh WebP và bộ nhớ | Trả image/webp cho WebP, image/gif cho GIF; giới hạn đọc 5 MiB + 1 byte và trả 413 khi vượt giới hạn. Lỗi kho ảnh phân biệt 404/503. |
| Profile thiếu dòng DB | Trả 404 profile_not_found thay cho lỗi queryForObject 500. Bỏ @Min dư trên page, giữ kiểm tra khoảng giá trị thủ công. |
| Release gọi domain giả | Bỏ URL hardcode. Build release cần -PmusicReleaseApiUrl=https://YOUR_BACKEND/; chỉ chấp nhận HTTPS kết thúc bằng dấu /. Debug vẫn dùng HTTP cho phát triển. |
| Zip chứa cấu hình cục bộ | Thêm scripts/Export-Source.ps1: chọn file theo Git và loại thêm .env, local.properties, backend.env, license, runtime, keystore, build, IDE/cache. Không ghi đè zip sẵn có. |

## Kiểm thử thực sự đã chạy

| Kiểm tra | Kết quả |
|---|---|
| Backend `test bootJar` | 33 tests, 0 failures, 0 errors, 0 skipped; build JAR thành công. |
| Android `testDebugUnitTest` | 24 tests, 0 failures, 0 errors, 0 skipped. |
| Android `assembleDebug` | Thành công; APK: android/app/build/outputs/apk/debug/app-debug.apk. |
| Android `assembleDebugAndroidTest` | Biên dịch/đóng gói test thiết bị thành công. Chưa chạy trên thiết bị. |
| Android `lintDebug` | Thành công, không có lỗi chặn build; vẫn có cảnh báo lint. |
| Release thiếu URL | preReleaseBuild thất bại đúng dự kiến với thông báo cần musicReleaseApiUrl; configuration cache lưu được. |
| Release có URL HTTPS | preReleaseBuild chạy thành công với URL kiểm thử example.invalid. Đây chỉ là kiểm tra cấu hình, không chứng minh có server production hoặc APK release hoàn chỉnh. |
| Zip nguồn | Xuất thử thành công; kiểm tra không có .env, local.properties, backend.env, license, build/runtime; có .env.example và ListeningOutbox.java. |
| Git diff --check | Đạt. |

Backend gồm CatalogControllerTest (3), MvpBackendIntegrationTest (10), PlaybackDisconnectTest (1), LibraryPlaylistRegressionTest (5), RecommendationServiceTest (2), PremiumIntegrationTest (12).

Test mới bao phủ: gửi lại đúng payload/ID sau lỗi mạng và khởi tạo lại queue; tách tài khoản; phân loại lỗi HTTP retry; giới hạn batch; giữ source playlist qua JSON; cache quảng cáo hết hạn/cũ; Home token hết hạn và refresh; stream 404/503/200/206/416 và đóng input khi client ngắt; tìm kiếm wildcard; like draft; MIME WebP/ảnh quá lớn; future listen/thời lượng giả/overlap/rate limit/idempotency.

Test thiết bị BoundaryAdsTest đã cập nhật cho Free fallback và Premium cache. `adb devices` không có thiết bị kết nối, vì vậy chưa xác nhận hành vi Media3/UI/Keystore thực tế trên điện thoại hay emulator. Kết quả unit test không thay thế kiểm thử này.

PostgreSQL của test là instance tạm riêng; không sửa dữ liệu DB nhóm. Không thay đổi migration V1–V9.

## Giới hạn và lựa chọn thiết kế

- Retry hoạt động khi tiến trình app còn chạy; đợi tăng dần 5–300 giây khi lỗi. Nếu app bị hệ điều hành dừng, sự kiện đã lưu sẽ được gửi sau lần mở app và khôi phục/đăng nhập đúng tài khoản tiếp theo. Chưa thêm WorkManager để đánh thức app đã tắt.
- Chỉ lượt nghe đã kết thúc/chuyển bài/dừng service được lưu. Nếu tiến trình bị kill đột ngột ngay giữa bài, đoạn nghe đang diễn ra chưa có checkpoint bền vững. Nếu bộ nhớ máy đầy và không ghi được queue, app ghi log lỗi.
- Không có chức năng tải nhạc offline mới. Cơ chế quảng cáo không chặn chuyển bài vì entitlement; audio chưa có sẵn vẫn cần kết nối đến backend.
- Quyền Premium cache chỉ ảnh hưởng quảng cáo trên thiết bị, không cấp thêm quyền playlist/history ở backend. Cache bị xóa khi đổi tài khoản; không lưu lâu dài qua restart.
- Chống popularity giả mới là giảm lạm dụng. Client vẫn có thể bịa các khoảng nghe quá khứ không chồng nhau hoặc dùng nhiều tài khoản. Muốn chống gian lận production cần xác minh phiên stream phía server và hạn mức tại gateway.
- Stream đồng bộ giữ một servlet thread trong suốt phiên truyền; phù hợp đồ án nhưng cần kiểm thử tải nếu triển khai nhiều người nghe. Lỗi mạng phát sinh sau khi đã bắt đầu gửi audio vẫn có thể làm stream bị ngắt.
- Giữ mô hình xác thực hiện tại và kiểm tra DB theo request để logout có hiệu lực ngay. Chưa chuyển toàn bộ ứng dụng sang Spring Security hay thêm cache xác thực.
- Giới hạn ảnh chỉ chặn lượng đọc vào RAM; chưa có pipeline resize ảnh. Một request tối đa đọc khoảng 5 MiB, nhiều request đồng thời vẫn cần giới hạn ở tầng triển khai.
- Helper zip loại các đường dẫn cấu hình đã biết, không thay thế việc rà bí mật hardcode trong mã nguồn. Các zip cũ không được sửa/xóa tự động.
- Chưa cấu hình endpoint production thật hoặc triển khai server. Không có thanh toán thật.

## Chạy kiểm tra lại

Từ thư mục C:\Users\Admin\Desktop\mobile:

```powershell
.\android\gradlew.bat -p backend test bootJar
.\android\gradlew.bat -p android :app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest :app:lintDebug
# Khi đã có thiết bị Android:
.\android\gradlew.bat -p android :app:connectedDebugAndroidTest
# Đặt URL backend production của bạn khi build release:
.\android\gradlew.bat -p android :app:assembleRelease -PmusicReleaseApiUrl=https://YOUR_BACKEND/
# Xuất bản sao chỉ chứa source (đường dẫn zip chưa tồn tại):
.\scripts\Export-Source.ps1 -OutputPath "$env:TEMP\music-source.zip"
```

## Kịch bản nghiệm thu trên thiết bị còn cần chạy

1. Nghe đủ ngưỡng một bài, ngắt mạng trước khi chuyển bài, bật mạng lại: lịch sử có đúng một lượt.
2. Tạo lượt nghe chưa gửi ở tài khoản A, đăng xuất và vào B: không có sự kiện A gửi dưới phiên B. Đăng nhập lại A để gửi nốt.
3. Với queue có audio local/cached, cho API entitlement lỗi/treo: sau tối đa khoảng 3 giây fallback sang quảng cáo Free hoặc bài tiếp theo với cache Premium hợp lệ.
4. Trong lúc đợi entitlement, bấm Pause hoặc đổi bài: callback cũ không tự bật lại playback/chuyển bài lần hai.
5. Làm access token hết hạn, vào Home: refresh thành công thì các phần cá nhân hóa được tải lại; refresh bị thu hồi thì yêu cầu đăng nhập.
6. Thử stream một bài có bản ghi DB nhưng object MinIO thiếu: API trả 404; mô phỏng storage không hoạt động trả 503.

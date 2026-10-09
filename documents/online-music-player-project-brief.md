# Mục tiêu dự án ứng dụng nghe nhạc trực tuyến

**Dự án:** Ứng dụng nghe nhạc trực tuyến trên thiết bị di động  
**Nhóm thực hiện:** 7 thành viên  
**Thời gian:** 3 tuần  
**Trạng thái:** Bản nháp để thống nhất phạm vi

## Tổng quan

Đây là đồ án cuối kỳ môn Mobile App của nhóm 7 thành viên, được thực hiện trong 3 tuần. Sản phẩm là một ứng dụng nghe nhạc trực tuyến dành riêng cho Android và tập trung vào tệp âm thanh, với trải nghiệm cốt lõi gần giống Spotify: người dùng có thể tìm kiếm và phát nhạc, nhận gợi ý bài liên quan, xem lịch sử nghe, quản lý thư viện cá nhân và sử dụng trang hồ sơ. Backend, cơ sở dữ liệu và nơi lưu tệp âm thanh sẽ do nhóm tự triển khai và vận hành. Ứng dụng có hai gói Free và Premium; việc nâng cấp Premium được mô phỏng để phục vụ demo và không xử lý tiền thật. Mục tiêu của dự án là hoàn thành một luồng sử dụng xuyên suốt và ổn định để trình diễn, không phải tái tạo toàn bộ Spotify.

## Mục tiêu

- Xây dựng ứng dụng mobile có thể phát nhạc trực tuyến ổn định, hỗ trợ play, pause, tua, chuyển bài, hàng chờ và tiếp tục phát khi người dùng rời ứng dụng hoặc khóa màn hình.
- Hoàn thiện các luồng chính gồm đăng nhập, khám phá nhạc, tìm kiếm, thư viện cá nhân, lịch sử nghe và hồ sơ người dùng.
- Xây dựng chức năng gợi ý bài hát liên quan bằng metadata và dữ liệu nghe, không phụ thuộc vào mô hình machine learning phức tạp.
- Triển khai backend, cơ sở dữ liệu và nơi lưu tệp âm thanh trên hạ tầng do nhóm tự quản lý.
- Xây dựng cơ chế phân quyền Free và Premium ở backend, kết hợp luồng thanh toán giả lập để chứng minh khả năng mở khóa quyền lợi theo gói.
- Hoàn thành bản demo ổn định vào cuối tuần thứ ba, trong đó mọi luồng bắt buộc hoạt động từ ứng dụng đến server.

## Chức năng cốt lõi

- **Tài khoản và hồ sơ:** Đăng ký, đăng nhập, đăng xuất, xem và cập nhật tên, xem gói hiện tại cùng các thống kê nghe nhạc cơ bản. MVP không có chức năng tải ảnh đại diện hoặc nội dung từ người dùng.
- **Trang chủ:** Hiển thị lịch sử nghe gần đây, bài hát hoặc album được gợi ý, bản phát hành mới và các nhóm như “Vì bạn đã nghe”.
- **Tìm kiếm:** Nhập một phần hoặc toàn bộ tên bài hát, nghệ sĩ hoặc album để nhận kết quả phù hợp. MVP không có bộ lọc nâng cao.
- **Trình phát nhạc:** Có màn hình đang phát và mini player; từ trang nghe nhạc người dùng có thể play, pause, tua, chuyển bài, yêu thích bài hát, mở thông tin album, mở thông tin nghệ sĩ, thêm bài vào playlist và quản lý hàng chờ. Media3 `MediaSessionService` giữ ExoPlayer để tiếp tục phát nền và cung cấp điều khiển qua media notification, màn hình khóa và tai nghe.
- **Gợi ý liên quan:** Chấm điểm theo 45% độ phù hợp thể loại, 30% mức yêu thích nghệ sĩ từ lịch sử/favorite, 15% độ phổ biến và 10% độ mới. Loại bài đang phát cùng 20 bài vừa nghe; MVP không phụ thuộc mood. Tài khoản có dưới 5 lượt nghe hợp lệ nhận 60% bài phổ biến và 40% bài mới, tối đa 2 bài mỗi nghệ sĩ.
- **Lịch sử nghe:** Lưu bài hát, thời điểm nghe và thời lượng nghe thực tế. Một phiên chỉ được tính là một lượt nghe khi đạt `min(30 giây, 50% thời lượng bài)`; thời gian tua nhảy không được cộng và mỗi `client_event_id` chỉ được tính một lần.
- **Thư viện của tôi:** Gồm Bài hát yêu thích, Album yêu thích và Playlist của tôi. Khi đang nghe, người dùng nhấn tim bài hát hoặc mở album và nhấn tim album; hệ thống tự thêm nội dung vào đúng nhóm trong Thư viện. Sau khi tạo playlist, người dùng được đưa tới chi tiết playlist để thêm bài hoặc chọn một bài đã có và chuyển sang luồng nghe nhạc.

## Mô hình Free và Premium

### Gói Free

- Nghe nhạc, tìm kiếm, yêu thích bài hát và nhận gợi ý liên quan.
- Tạo tối đa 3 playlist cá nhân.
- Xem lịch sử nghe trong 7 ngày gần nhất.
- Hiển thị quảng cáo giới thiệu khi vào ứng dụng và quảng cáo âm thanh thử nghiệm sau khi mỗi bài hát kết thúc, trước khi phát bài tiếp theo. Quảng cáo không được cắt ngang bài đang phát.

### Gói Premium

- Không hiển thị quảng cáo.
- Tạo playlist không giới hạn và xem toàn bộ lịch sử nghe.
- Nhận cùng cơ chế gợi ý bài hát như tài khoản Free; Premium khác Free ở quảng cáo, giới hạn playlist và thời gian xem lịch sử.

### Thanh toán giả lập

- Người dùng mở màn hình chọn gói từ Profile và chọn nâng cấp Premium.
- Ứng dụng hiển thị màn hình xác nhận giao dịch mô phỏng, không yêu cầu thẻ và không trừ tiền.
- Backend tạo một giao dịch thử nghiệm, kích hoạt Premium trong thời hạn mô phỏng và trả về quyền lợi mới cho ứng dụng.
- Profile hiển thị trạng thái gói, ngày bắt đầu và ngày hết hạn. Nhóm cần có chức năng đưa tài khoản về Free để lặp lại kịch bản demo.
- Đây không phải tích hợp Google Play Billing và không được sử dụng như cơ chế thanh toán thật.

## Hướng kỹ thuật

- Ứng dụng mobile chỉ giao tiếp với backend API, không kết nối trực tiếp tới cơ sở dữ liệu.
- Ứng dụng Android dùng Hilt để cung cấp dependency cho Activity, Fragment, ViewModel, `MediaSessionService`, API client và repository; ưu tiên constructor injection để các thành phần dễ thay thế khi kiểm thử.
- Access token chỉ được giữ trong RAM. Refresh token được mã hóa bằng khóa AES-GCM lưu trong Android Keystore; Preferences DataStore chỉ lưu cài đặt đơn giản. MVP không dùng Room hoặc cache metadata; hàng đợi phát chỉ tồn tại trong `MediaSessionService` của phiên chạy hiện tại.
- Backend cấp access token JWT ký bằng HS256, hết hạn sau 15 phút. Refresh token là chuỗi ngẫu nhiên hết hạn sau 7 ngày; backend chỉ lưu hash, cấp token mới và vô hiệu hóa token cũ sau mỗi lần refresh, đồng thời thu hồi token khi đăng xuất hoặc đổi mật khẩu.
- PostgreSQL lưu người dùng, metadata bài hát, playlist, thư viện và lịch sử nghe.
- Trong MVP, tệp âm thanh và ảnh bìa catalog được nhóm chuẩn bị sẵn trong MinIO chạy trực tiếp trên server; database chỉ lưu metadata và object key. Người dùng không tải tệp lên hệ thống. Spring Boot đọc object và proxy HTTP Range về Android.
- MVP triển khai trên một máy Windows của nhóm. Spring Boot chạy bằng `java -jar`, PostgreSQL 16 chạy bằng Windows service và MinIO chạy bằng file thực thi; điện thoại kết nối qua Wi-Fi LAN tới cổng `8080` trong debug build.
- Nhóm kiểm tra thủ công bằng `/actuator/health`, dùng log console/file và sao lưu PostgreSQL cùng dữ liệu MinIO sang ổ đĩa khác hoặc máy thành viên trước buổi chạy thử và demo.
- Dịch vụ phát nhạc cần hỗ trợ HTTP Range để người dùng tua bài mà không phải tải lại toàn bộ tệp.
- Thuật toán gợi ý ở phiên bản đầu là content based với trọng số: thể loại 45%, nghệ sĩ 30%, độ phổ biến 15% và độ mới 10%. Kết quả loại bài đang phát cùng 20 bài vừa nghe. Free và Premium dùng cùng một thuật toán gợi ý.
- Backend tính `counted_as_play = true` khi thời gian nghe tích lũy đạt `min(30 giây, 50% thời lượng bài)`. Tua về phía trước không làm tăng thời gian nghe; nghe lại từ đầu tạo phiên mới với `client_event_id` mới.
- Backend là nguồn quyết định quyền Free hoặc Premium; ứng dụng Android không được tự gán trạng thái Premium ở phía client.
- Quảng cáo trong đồ án sử dụng banner/âm thanh thử nghiệm hoặc placeholder, không tích hợp mạng quảng cáo production. Backend quyết định entitlement `ad_free`; ứng dụng chỉ chèn quảng cáo sau khi bài hiện tại kết thúc khi `ad_free = false`.
- Các bảng dữ liệu dự kiến gồm `users`, `artists`, `albums`, `tracks`, `genres`, `track_genres`, `playlists`, `playlist_tracks`, `library_tracks`, `library_albums`, `listening_history`, `plans`, `subscriptions` và `payment_events`.

## Phạm vi

**Trong phạm vi:** Ứng dụng Android, phát nhạc nền bằng foreground `MediaSessionService`, media notification, điều khiển màn hình khóa/tai nghe, tài khoản người dùng, catalog nhạc, streaming âm thanh, tìm kiếm, hàng chờ, gợi ý liên quan, thư viện, playlist, lịch sử, profile, phân quyền Free/Premium, quảng cáo thử nghiệm, thanh toán giả lập, backend API, database và dữ liệu demo.

**Ngoài phạm vi:** Video âm nhạc, nhiều cấp độ gợi ý theo gói, bộ lọc tìm kiếm nâng cao, cache dữ liệu cục bộ, khôi phục hàng đợi sau khi tiến trình bị hủy, upload nội dung hoặc ảnh đại diện từ người dùng, hiệu ứng animation tùy chỉnh, xử lý tiền thật, Google Play Billing production, quảng cáo production, tải nhạc ngoại tuyến, mạng xã hội hoàn chỉnh, đồng bộ nhiều thiết bị theo thời gian thực, mô hình AI phân tích âm thanh và khả năng phục vụ số lượng người dùng lớn như một sản phẩm thương mại.

## Các mốc thực hiện

1. **Cuối tuần 1:** Chốt công nghệ, dựng backend và database, nạp dữ liệu mẫu, hoàn thành đăng nhập và phát được một bài từ server trên thiết bị thật.
2. **Cuối tuần 2:** Hoàn thành tìm kiếm, hàng chờ, thư viện, playlist, lịch sử nghe, phiên bản đầu của chức năng gợi ý và mô hình dữ liệu Free/Premium.
3. **Cuối tuần 3:** Hoàn thành profile, paywall, thanh toán giả lập, quảng cáo thử nghiệm, kiểm thử tích hợp, sửa lỗi, triển khai server và chuẩn bị kịch bản demo. Không thêm tính năng ngoài phạm vi trong tuần cuối.

## Tiêu chí hoàn thành

- Người dùng có thể đăng ký hoặc đăng nhập, tìm bài hát và nghe nhạc từ server.
- Player hoạt động ổn định với play, pause, tua, chuyển bài và hàng chờ; nhạc tiếp tục phát khi chuyển ứng dụng hoặc khóa màn hình, đồng thời có thể điều khiển từ media notification, màn hình khóa và tai nghe.
- Người dùng có thể yêu thích bài hát hoặc album, tạo playlist, thêm bài hát vào playlist, xem thư viện và lịch sử nghe.
- Màn hình gợi ý trả về các bài phù hợp theo metadata hoặc lịch sử và có xử lý trường hợp người dùng mới.
- Profile hiển thị đúng thông tin và thống kê cơ bản từ dữ liệu thực tế.
- Tài khoản Free nhìn thấy quảng cáo giới thiệu và nghe quảng cáo âm thanh giữa hai bài liên tiếp; tài khoản Premium không hiển thị hoặc phát quảng cáo.
- Luồng nâng cấp giả lập tạo giao dịch thử nghiệm ở backend, cập nhật Premium và mở đúng quyền lợi mà không xử lý tiền thật.
- Backend, database và nơi lưu nhạc chạy trên hạ tầng tự host với bộ dữ liệu demo hợp pháp.
- Luồng trình diễn chính hoạt động trên ít nhất hai thiết bị kiểm thử mà không có lỗi nghiêm trọng.

## Rủi ro và biện pháp giảm thiểu

- **Bản quyền âm thanh:** Chỉ sử dụng nhạc do nhóm sở hữu hoặc có giấy phép cho phép phân phối và phát trực tuyến.
- **Streaming và phát nền:** HTTP Range, tua, chuyển bài, `MediaSessionService`, media notification và điều khiển khi khóa màn hình phải được thử nghiệm trên thiết bị thật trong tuần đầu.
- **Tích hợp nhóm:** Bảy thành viên cần thống nhất API contract, quy tắc Git và nhánh tích hợp ngay từ đầu để tránh xung đột vào tuần cuối.
- **Cold start của gợi ý:** Tài khoản có dưới 5 lượt nghe hợp lệ dùng danh sách gồm 60% bài phổ biến và 40% bản phát hành mới, tối đa 2 bài mỗi nghệ sĩ. Dataset demo seed khoảng 50 bài hợp pháp, 10 nghệ sĩ, 10 album, 5 thể loại và `popularity_score` ban đầu.
- **Hạ tầng tự host:** Kiểm tra health, dung lượng và streaming trước mỗi buổi demo; sao lưu thủ công sang đích khác và chuẩn bị dữ liệu demo cục bộ nếu mạng gặp sự cố.
- **Bảo mật phân quyền:** Không tin trạng thái Premium do client gửi lên; mọi API có quyền lợi Premium phải kiểm tra subscription ở backend.
- **Trải nghiệm quảng cáo:** Quảng cáo âm thanh chỉ bắt đầu sau khi bài hiện tại kết thúc và phải hoàn tất hoặc hết thời hạn an toàn trước khi chuyển bài; không được cắt ngang bài hát đang phát.
- **Thanh toán giả lập:** Endpoint kích hoạt Premium chỉ được dùng trong môi trường demo và phải được tắt hoặc bảo vệ nếu dự án tiếp tục phát triển.
- **Vận hành MVP:** Chạy trực tiếp trên Windows, kiểm tra Actuator health, xem log console/file và backup thủ công trước demo.

## Những việc chưa làm trong phiên bản này

- Không xây dựng hệ thống video.
- Không phát triển machine learning phân tích trực tiếp tệp âm thanh.
- Không xử lý tiền thật, không tích hợp Google Play Billing production và không phát hành quảng cáo production.
- Không tải về và phân phối lại nội dung từ Spotify, YouTube hoặc nguồn không cho phép.
- Không xây dựng cache dữ liệu cục bộ, upload từ người dùng, nhiều cấp độ gợi ý theo gói hoặc hiệu ứng animation tùy chỉnh trong MVP.

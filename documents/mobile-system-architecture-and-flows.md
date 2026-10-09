# Kiến trúc hệ thống và các luồng chính cho Online Music Player

**Trạng thái:** Kiến trúc MVP đã được chốt cho giai đoạn triển khai  
**Phạm vi:** Ứng dụng Android, backend tự quản lý, PostgreSQL và MinIO  
**Cơ sở:** `online-music-player-project-brief.md` và `database-design.md`  
**Đối tượng đọc:** Nhóm phát triển, người review đồ án và thành viên mới

## 1. Mục tiêu của tài liệu

Tài liệu này đề xuất một cấu trúc đủ rõ để nhóm 7 người có thể phát triển song song trong 3 tuần mà vẫn giữ được một luồng demo xuyên suốt. Kiến trúc ưu tiên:

- Hoàn thành và kiểm thử được luồng đăng nhập → tìm/chọn bài → phát nhạc → ghi nhận lịch sử.
- Tách giao diện, nghiệp vụ và hạ tầng để thay đổi framework hoặc thư viện không làm lan truyền lỗi.
- Giữ backend đơn giản để triển khai: một ứng dụng backend dạng modular monolith, không tách microservice trong phiên bản đồ án.
- Để backend quyết định mọi quyền lợi Free/Premium; client chỉ hiển thị trạng thái nhận từ server.
- Không để từng màn hình sở hữu trực tiếp audio engine hoặc queue của phiên phát nhạc.
- Giữ tìm kiếm ở mức nhập tên bài hát, nghệ sĩ hoặc album và loại bỏ bộ lọc nâng cao để bảo đảm hoàn thành trong 3 tuần.

## 2. Giả định và quyết định nền tảng

Repository hiện chưa có mã nguồn. Framework mobile đã chốt là Kotlin Native phát triển bằng Android Studio; backend đã chốt Java với Spring Boot. Cấu trúc bên dưới vì vậy cụ thể cho Android và Spring Boot nhưng vẫn giữ các ranh giới nghiệp vụ độc lập với UI framework.

| Nội dung | Lựa chọn | Trạng thái |
|---|---|---|
| Mobile | Kotlin Native trên Android; feature-first, một chiều dữ liệu | Đã chốt: phát triển bằng Android Studio |
| Android UI | XML Views với Activity/Fragment | Đã chốt; không dùng Jetpack Compose trong MVP |
| Backend | Java + Spring Boot; modular monolith, REST/JSON | Đã chốt |
| Database | PostgreSQL 16 + Flyway | Đã chốt |
| Audio storage | MinIO chạy trực tiếp trên server | Đã chốt cho MVP |
| Streaming | Spring Boot proxy, hỗ trợ HTTP Range và trả `206 Partial Content` | Đã chốt; signed URL để sau |
| Authentication | JWT HS256 15 phút + opaque refresh token 7 ngày | Đã chốt; lưu hash, rotation mỗi lần refresh và thu hồi khi logout/đổi mật khẩu |
| Local data | Android Keystore + Preferences DataStore | Đã chốt; access token trong RAM, refresh token được mã hóa, không dùng Room hoặc cache metadata |
| State management | AndroidX ViewModel + StateFlow | Đã chốt; XML Views collect bằng `repeatOnLifecycle` |
| Dependency injection | Hilt | Đã chốt; dùng cho Activity, Fragment, ViewModel, Service và các dependency dùng chung |
| Playback | AndroidX Media3 ExoPlayer trong `MediaSessionService` | Đã chốt; hỗ trợ foreground service và phát nền |
| Recommendation | Content-based có trọng số từ metadata và lịch sử | Đã chốt; cold start dưới 5 lượt hợp lệ, seed 50 track |
| Triển khai MVP | Một máy Windows của nhóm | Đã chốt; Spring Boot, PostgreSQL và MinIO khởi động riêng |

Trong MVP, ExoPlayer và `MediaSession` nằm trong foreground `MediaSessionService` để tiếp tục phát khi người dùng chuyển ứng dụng hoặc khóa màn hình. Android UI kết nối qua `MediaController`; hệ thống cung cấp media notification và chuyển lệnh từ màn hình khóa hoặc tai nghe tới session.

## 3. Kiến trúc tổng quan

### 3.1. Lựa chọn kiến trúc

- **Mobile:** feature-first kết hợp ranh giới Presentation → Application → Domain; Data và Platform là adapter hướng vào các contract ổn định.
- **Backend:** modular monolith. Mỗi module nghiệp vụ có controller, use case, domain rule và repository riêng nhưng cùng chạy trong một tiến trình triển khai.
- **Dữ liệu:** PostgreSQL lưu metadata và trạng thái nghiệp vụ; MinIO lưu tệp âm thanh và ảnh.
- **Giao tiếp:** mobile chỉ dùng API của backend. Spring Boot truy cập database và MinIO, sau đó proxy byte range về ứng dụng.

Lựa chọn modular monolith phù hợp hơn microservices vì nhóm chỉ có 3 tuần, một sản phẩm, một database chính và một quy trình triển khai. Module vẫn cần tách rõ để có thể tách dịch vụ sau này nếu sản phẩm tiếp tục phát triển.

### 3.2. C4 System Context

Câu hỏi của sơ đồ: Ai sử dụng hệ thống và hệ thống mang lại kết quả gì?

```mermaid
C4Context
    title System Context - Online Music Player

    Person(listener, "Người nghe", "Tìm kiếm, phát nhạc, quản lý thư viện và gói sử dụng")
    Person(operator, "Nhóm vận hành", "Nạp dữ liệu demo, theo dõi hệ thống và reset tài khoản demo")

    System(musicSystem, "Online Music Player", "Ứng dụng Android và backend tự host cho trải nghiệm nghe nhạc trực tuyến")

    Rel(listener, musicSystem, "Sử dụng", "Ứng dụng Android")
    Rel(operator, musicSystem, "Quản trị dữ liệu và theo dõi", "Công cụ nội bộ/API bảo vệ")
```

### 3.3. C4 Container

Câu hỏi của sơ đồ: Những đơn vị có thể chạy hoặc lưu trữ độc lập nào tạo thành hệ thống?

```mermaid
C4Container
    title Container Diagram - Online Music Player

    Person(listener, "Người nghe", "Sử dụng ứng dụng trên Android")

    System_Boundary(system, "Online Music Player") {
        Container(mobile, "Android App", "Kotlin Native / XML Views / Media3", "Activity, Fragment, điều hướng, trạng thái và phát nhạc nền bằng MediaSessionService")
        Container(api, "Backend API", "Java / Spring Boot", "Xác thực, catalog, thư viện, lịch sử, gợi ý và entitlement")
        ContainerDb(db, "PostgreSQL", "PostgreSQL 16", "Tài khoản, metadata, playlist, lịch sử và subscription")
        ContainerDb(storage, "MinIO", "S3-compatible object storage", "Tệp âm thanh và ảnh bìa catalog do nhóm chuẩn bị")
    }

    Rel(listener, mobile, "Tương tác và nghe nhạc")
    Rel(mobile, api, "Gọi API nghiệp vụ", "JSON/HTTP(S)")
    Rel(mobile, api, "Nhận luồng âm thanh", "HTTP(S) Range")
    Rel(api, db, "Đọc/ghi dữ liệu", "SQL")
    Rel(api, storage, "Đọc object theo storage key", "S3 API")
```

Không biểu diễn state manager, HTTP client hay thư viện dùng chung như các container vì chúng không phải đơn vị triển khai độc lập.

### 3.4. C4 Deployment cho MVP

Câu hỏi của sơ đồ: Các container được đặt ở đâu và được vận hành như thế nào trong bản demo?

```mermaid
C4Deployment
    title Deployment Diagram - Online Music Player MVP

    Deployment_Node(phone, "Thiết bị Android", "Android device", "Chạy ứng dụng và MediaSessionService") {
        Container(mobileRuntime, "Android App", "Kotlin / XML Views / Media3", "Giao diện, trạng thái và phát nhạc")
    }

    Deployment_Node(server, "Máy Windows của nhóm", "Windows 10 hoặc 11", "Chạy Spring Boot, PostgreSQL và MinIO") {
        Container(apiRuntime, "Backend API", "Java / Spring Boot", "Chạy bằng java -jar trên cổng 8080")
        ContainerDb(postgresRuntime, "PostgreSQL", "PostgreSQL 16 Windows service", "Dữ liệu nghiệp vụ, chỉ localhost")
        ContainerDb(minioRuntime, "MinIO", "MinIO executable", "Audio và ảnh, chỉ localhost")
    }

    Deployment_Node(backupTarget, "Thư mục sao lưu", "Ổ đĩa khác hoặc máy thành viên", "Tạo bản sao trước buổi demo") {
        ContainerDb(backupFiles, "Backup files", "pg_dump + MinIO copy", "Khôi phục database và media")
    }

    Rel(mobileRuntime, apiRuntime, "Gọi API và stream trong mạng LAN", "HTTP debug")
    Rel(apiRuntime, postgresRuntime, "Đọc và ghi", "JDBC localhost")
    Rel(apiRuntime, minioRuntime, "Đọc object", "S3 API localhost")
    Rel(postgresRuntime, backupFiles, "Sao lưu trước demo", "pg_dump")
    Rel(minioRuntime, backupFiles, "Sao chép trước demo", "File copy")
```

Windows Firewall chỉ mở cổng Spring Boot `8080` trong mạng riêng để điện thoại kiểm thử truy cập. PostgreSQL và MinIO bind vào localhost. Android chỉ cho phép HTTP LAN trong debug build; nếu đưa hệ thống ra Internet hoặc phát hành thật thì phải bổ sung HTTPS.

## 4. Cấu trúc ứng dụng mobile

### 4.1. Cấu trúc thư mục đề xuất

```text
mobile/
├── app/                         # Bootstrap Hilt, navigation và app lifecycle
├── core/
│   ├── config/                  # Biến môi trường và build configuration
│   ├── network/                 # HTTP client, auth interceptor, DTO lỗi chuẩn
│   ├── storage/                 # Secure storage và Preferences DataStore
│   ├── playback/                # ExoPlayer, MediaSessionService, MediaController và queue
│   ├── design_system/           # Theme và UI component dùng chung
│   └── testing/                 # Fake, fixture và test helper
├── features/
│   ├── auth/
│   ├── home/
│   ├── search/
│   ├── player/
│   ├── library/
│   ├── playlists/
│   ├── history/
│   ├── profile/
│   └── subscription/
└── shared/
    ├── models/                  # Kiểu thật sự dùng chung, giữ ở mức tối thiểu
    └── utilities/               # Hàm thuần dùng chung, không chứa nghiệp vụ
```

Mỗi feature chỉ tạo đủ các lớp mà feature đó cần:

```text
feature/
├── presentation/                # Screen, component, view state và event
├── application/                 # Use case/controller điều phối luồng
├── domain/                      # Entity, value object, rule và repository contract
└── data/                        # API DTO, mapper và repository implementation
```

Không bắt buộc tạo bốn thư mục rỗng cho feature rất nhỏ. Mục tiêu là giữ ranh giới, không phải tăng số lượng file.

### 4.2. Quy tắc dependency trên mobile

```mermaid
flowchart LR
    Bootstrap[App bootstrap và DI]
    Presentation[Presentation\nScreen + View State]
    Application[Application\nUse cases + Controllers]
    Domain[Domain\nRules + Repository contracts]
    Data[Data adapters\nAPI + Mapper]
    Platform[Platform adapters\nAudio + Secure Storage]

    Bootstrap --> Presentation
    Bootstrap --> Data
    Bootstrap --> Platform
    Presentation --> Application
    Application --> Domain
    Data -. triển khai contract .-> Domain
    Platform -. triển khai contract .-> Domain
```

Các quy tắc bắt buộc:

1. Screen không gọi HTTP client hoặc nguồn lưu trữ trực tiếp.
2. DTO của API không đi thẳng vào UI; chuyển sang model phù hợp trước khi hiển thị.
3. Domain không import framework UI, HTTP client hay SDK audio.
4. Playback session thuộc `core/playback`, không thuộc vòng đời của màn hình Player.
5. Mini player và màn hình Now Playing quan sát cùng một `PlaybackState` duy nhất.
6. Token phải nằm trong secure storage; không ghi access token hoặc refresh token vào log.
7. Mỗi trạng thái bất đồng bộ phải biểu diễn được loading, success, empty và error.

### 4.3. Trạng thái player tối thiểu

Player nên có state machine riêng thay vì nhiều cờ boolean độc lập:

```mermaid
stateDiagram-v2
    [*] --> Idle
    Idle --> Loading: Chọn bài
    Loading --> Playing: Đủ dữ liệu buffer
    Loading --> Error: Không tải được
    Playing --> Paused: Pause
    Paused --> Playing: Resume
    Playing --> Buffering: Thiếu buffer
    Buffering --> Playing: Buffer phục hồi
    Buffering --> Error: Hết retry
    Playing --> Completed: Hết bài
    state EntitlementCheck <<choice>>
    Completed --> EntitlementCheck: Có bài tiếp theo
    Completed --> Idle: Hết hàng chờ
    EntitlementCheck --> Advertising: Free / ad_free = false
    EntitlementCheck --> Loading: Premium / ad_free = true
    Advertising --> Loading: Quảng cáo kết thúc hoặc timeout an toàn
    Error --> Loading: Retry
    Error --> Idle: Hủy
```

## 5. Cấu trúc backend

### 5.1. Modular monolith theo nghiệp vụ

```text
backend/
├── bootstrap/                   # Khởi tạo server, DI và route registration
├── modules/
│   ├── auth/                    # Đăng ký, đăng nhập, refresh, revoke session
│   ├── profiles/                # Hồ sơ và thống kê cơ bản
│   ├── catalog/                 # Track, artist, album, genre
│   ├── search/                  # Tìm kiếm theo tên bài hát, nghệ sĩ hoặc album
│   ├── playback/                # Metadata phát và HTTP Range streaming
│   ├── library/                 # Favorite tracks
│   ├── playlists/               # Playlist và giới hạn theo gói
│   ├── history/                 # Listening event và lịch sử
│   ├── recommendations/         # Content-based ranking và cold start
│   └── subscriptions/           # Plan, entitlement và payment demo
├── shared/
│   ├── database/                # Connection, transaction và migration
│   ├── object_storage/          # MinIO S3 adapter
│   ├── http/                    # Error envelope, middleware, pagination
│   ├── security/                # Token, password hash và authorization
│   └── config/                  # Cấu hình và secret từ môi trường
└── tests/
    ├── unit/
    ├── integration/
    └── contract/
```

### 5.2. Ranh giới module

- Module không truy cập bảng của module khác bằng câu SQL rải rác. Giao tiếp qua service/use case công khai hoặc query được định nghĩa rõ.
- `subscriptions` là nguồn quyết định entitlement: `ad_free`, `max_playlists` và `history_days`.
- `playlists` phải hỏi entitlement trước khi tạo playlist mới; không tin số lượng hoặc gói do client gửi lên.
- `history` nhận `client_event_id` để việc gửi lại không tạo lượt nghe trùng.
- `playback` chỉ phát các track có trạng thái cho phép và phải hỗ trợ header `Range`.
- `recommendations` đọc dữ liệu catalog/history nhưng không được làm chậm endpoint phát nhạc.

## 6. User flow

Các sơ đồ trong mục này chỉ thể hiện thao tác của người dùng và phản hồi nhìn thấy trên frontend. Chi tiết API, database và storage nằm trong System Flow.

Quy ước ký pháp được dùng thống nhất trong sơ đồ:

| Hình | Mermaid | Ý nghĩa |
|---|---|---|
| Oval/bo tròn | `([Bắt đầu hoặc kết thúc])` | Điểm bắt đầu hoặc kết thúc luồng |
| Hình chữ nhật | `[Chức năng hoặc màn hình]` | Một hành động xử lý, chức năng hoặc màn hình |
| Hình bình hành | `[/Dữ liệu vào hoặc ra/]` | Người dùng nhập dữ liệu hoặc hệ thống xuất dữ liệu |
| Hình thoi | `{Điều kiện?}` | Điểm quyết định; mỗi nhánh ra phải có nhãn rõ ràng |
| Mũi tên | `A --> B` | Thứ tự di chuyển giữa các bước |



### 6.1. Vào Home và điều hướng giữa bốn mục chính

Câu hỏi của sơ đồ: Sau khi mở ứng dụng, người dùng vào Home mặc định rồi chuyển sang Search, Thư viện hoặc Profile bằng cách nào?

```mermaid
flowchart TD
    Start([Bắt đầu: mở ứng dụng]) --> Session{Phiên đăng nhập còn hợp lệ?}
    Session -- Không --> AuthScreen[Hiển thị màn hình đăng nhập hoặc đăng ký]
    AuthScreen --> Credentials[/Nhập thông tin tài khoản/]
    Credentials --> SubmitAuth[Gửi yêu cầu xác thực]
    SubmitAuth --> AuthResult{Xác thực thành công?}
    AuthResult -- Không --> AuthError[Hiển thị lỗi và cho thử lại]
    AuthError --> Credentials
    AuthResult -- Có --> Plan
    Session -- Có --> Plan{Tài khoản đang dùng gói nào?}

    Plan -- Free --> IntroAd[Hiển thị quảng cáo giới thiệu]
    IntroAd --> Home[Hiển thị Home - trang chính]
    Plan -- Premium --> Home

    Home --> HomeAction{Người dùng muốn làm gì?}
    HomeAction -- Sử dụng nội dung Home --> HomeFlow([Tiếp tục ở nhánh Home])
    HomeAction -- Chuyển mục --> Navigation{Người dùng chọn mục nào trên thanh điều hướng?}
    Navigation -- Home --> Home
    Navigation -- Search --> Search[Đi đến Tìm kiếm]
    Navigation -- Thư viện --> Library[Đi đến Thư viện của tôi]
    Navigation -- Profile --> Profile[Đi đến Profile]

    Search --> SearchFlow([Tiếp tục ở nhánh Search])
    Library --> LibraryFlow([Tiếp tục ở nhánh Thư viện])
    Profile --> ProfileFlow([Tiếp tục ở nhánh Profile])
```

### 6.2. Nhánh Home và Search

Câu hỏi của sơ đồ: Từ Home là trang chính, người dùng xem nội dung hoặc chuyển sang Search để chọn bài như thế nào?

```mermaid
flowchart TD
    Start([Bắt đầu tại Home - trang chính]) --> Home[Hiển thị Home]
    Home --> HomeContent[/Lịch sử đã nghe, gợi ý bài hát hoặc album, bản phát hành mới/]
    HomeContent --> HomeChoice{Người dùng muốn làm gì?}
    HomeChoice -- Lịch sử --> History[Hiển thị lịch sử nghe]
    HomeChoice -- Gợi ý --> Recommendations[Hiển thị danh sách gợi ý]
    HomeChoice -- Bản phát hành mới --> NewReleases[Hiển thị bản phát hành mới]
    HomeChoice -- Mở Search --> Search[Hiển thị màn hình tìm kiếm]
    History --> SelectTrack[Chọn bài hát]
    Recommendations --> SelectTrack
    NewReleases --> SelectTrack

    Search --> Query[/Nhập tên bài hát, nghệ sĩ hoặc album/]
    Query --> SubmitSearch[Thực hiện tìm kiếm]
    SubmitSearch --> Results[/Hiển thị kết quả tìm kiếm/]
    Results --> SelectTrack

    SelectTrack --> Playback([Chuyển sang luồng nghe nhạc])
```

### 6.3. Thư viện của tôi

Câu hỏi của sơ đồ: Người dùng xem bài hát yêu thích, album yêu thích và quản lý playlist cá nhân như thế nào?

```mermaid
flowchart TD
    Start([Mở Thư viện của tôi]) --> Library[Hiển thị Bài hát yêu thích, Album yêu thích và Playlist của tôi]
    Library --> Action{Người dùng muốn làm gì?}

    Action -- Xem Bài hát yêu thích --> FavoriteTracks[Hiển thị danh sách bài hát yêu thích]
    FavoriteTracks --> TrackChoice{Chọn một bài để nghe?}
    TrackChoice -- Có --> Playback([Chuyển sang luồng nghe nhạc])
    TrackChoice -- Không --> Library

    Action -- Xem Album yêu thích --> FavoriteAlbums[Hiển thị danh sách album yêu thích]
    FavoriteAlbums --> AlbumChoice{Chọn một album?}
    AlbumChoice -- Có --> AlbumDetail[Hiển thị chi tiết album và danh sách bài hát]
    AlbumChoice -- Không --> Library
    AlbumDetail --> SelectAlbumTrack{Chọn một bài để nghe?}
    SelectAlbumTrack -- Có --> Playback
    SelectAlbumTrack -- Không --> Library

    Action -- Xem Playlist của tôi --> Playlists[Hiển thị danh sách playlist]
    Playlists --> PlaylistAction{Chọn thao tác nào?}
    PlaylistAction -- Mở playlist --> PlaylistDetail[Hiển thị chi tiết playlist]
    PlaylistAction -- Tạo playlist --> PlaylistQuota{Còn quyền tạo playlist?}
    PlaylistQuota -- Có --> PlaylistName[/Nhập tên playlist/]
    PlaylistName --> CreatePlaylist[Tạo playlist]
    CreatePlaylist --> Success[/Hiển thị playlist đã tạo/]
    Success --> PlaylistDetail
    PlaylistQuota -- Không --> Paywall[Hiển thị giới hạn và màn hình nâng cấp]

    PlaylistDetail --> PlaylistDetailAction{Người dùng muốn làm gì trong playlist?}
    PlaylistDetailAction -- Chọn bài để nghe --> SelectPlaylistTrack[Chọn một bài hát trong playlist]
    SelectPlaylistTrack --> Playback
    PlaylistDetailAction -- Thêm bài hát --> AddPlaylistTrack[Chọn bài hát để thêm vào playlist]
    AddPlaylistTrack --> TrackAdded[/Hiển thị bài hát đã được thêm/]
    TrackAdded --> PlaylistDetail
    PlaylistDetailAction -- Quay lại Thư viện --> Finish([Hoàn tất thao tác thư viện])
    Paywall --> Finish
```

Trong giao diện, tên đúng của mục này là **Thư viện của tôi**, gồm ba nhóm độc lập: **Bài hát yêu thích**, **Album yêu thích** và **Playlist của tôi**. Sau khi tạo playlist, ứng dụng mở ngay chi tiết playlist; người dùng có thể thêm bài hát hoặc chọn một bài đã có để chuyển sang luồng nghe nhạc. Luồng này dùng để xem nội dung đã lưu, mở nội dung để nghe và quản lý playlist; thao tác thêm yêu thích nằm trong luồng nghe nhạc. Người dùng chỉ tạo **playlist**; **album** là bản phát hành thuộc catalog do hệ thống quản lý.

### 6.4. Nghe nhạc và quảng cáo theo gói

Câu hỏi của sơ đồ: Từ trang nghe nhạc, người dùng chuyển bài, yêu thích nội dung, xem thông tin và tiếp tục điều khiển khi ứng dụng chạy nền như thế nào?

```mermaid
flowchart TD
    Start([Bắt đầu tại trang nghe nhạc]) --> NowPlaying[Hiển thị bài đang phát và các nút điều khiển]
    NowPlaying --> PlayerAction{Người dùng thực hiện thao tác nào?}

    PlayerAction -- Play, pause hoặc tua --> Control[Điều khiển phát nhạc]
    Control --> NowPlaying
    PlayerAction -- Chuyển bài --> ChangeTrack{Chọn bài trước hay bài tiếp theo?}
    ChangeTrack -- Bài trước --> PreviousTrack[Phát bài trước]
    ChangeTrack -- Bài tiếp theo --> NextTrack[Phát bài tiếp theo]
    PreviousTrack --> NowPlaying
    NextTrack --> NowPlaying
    PlayerAction -- Nhấn tim bài hát --> FavoriteTrack[Tự động thêm bài hát vào Bài hát yêu thích]
    FavoriteTrack --> TrackSaved[/Hiển thị tim bài hát đã bật/]
    TrackSaved --> NowPlaying
    PlayerAction -- Mở thông tin album --> AlbumDetail[Hiển thị thông tin album và danh sách bài hát]
    AlbumDetail --> AlbumAction{Người dùng muốn làm gì với album?}
    AlbumAction -- Nhấn tim album --> FavoriteAlbum[Tự động thêm album vào Album yêu thích]
    FavoriteAlbum --> AlbumSaved[/Hiển thị tim album đã bật/]
    AlbumSaved --> AlbumDetail
    AlbumAction -- Chọn bài trong album --> SelectAlbumTrack[Chọn bài hát]
    SelectAlbumTrack --> PlayAlbumTrack[Phát bài hát đã chọn]
    PlayAlbumTrack --> NowPlaying
    AlbumAction -- Quay lại Player --> NowPlaying
    PlayerAction -- Mở thông tin nghệ sĩ --> ArtistDetail[Hiển thị thông tin nghệ sĩ và các bài hát liên quan]
    ArtistDetail --> ArtistAction{Người dùng muốn làm gì với nghệ sĩ?}
    ArtistAction -- Chọn bài của nghệ sĩ --> SelectArtistTrack[Chọn bài hát]
    SelectArtistTrack --> PlayArtistTrack[Phát bài hát đã chọn]
    PlayArtistTrack --> NowPlaying
    ArtistAction -- Quay lại Player --> NowPlaying
    PlayerAction -- Thêm vào playlist --> AddPlaylist[Chọn và thêm vào playlist]
    AddPlaylist --> NowPlaying
    PlayerAction -- Rời ứng dụng hoặc khóa màn hình --> BackgroundPlayback[MediaSessionService tiếp tục phát nhạc]
    BackgroundPlayback --> BackgroundAction{Người dùng thực hiện thao tác nào?}
    BackgroundAction -- Điều khiển từ notification, màn hình khóa hoặc tai nghe --> BackgroundControl[Play, pause hoặc chuyển bài]
    BackgroundControl --> BackgroundPlayback
    BackgroundAction -- Mở lại ứng dụng --> NowPlaying
    BackgroundAction -- Dừng phát --> Stop
    PlayerAction -- Dừng nghe --> Stop([Kết thúc phiên nghe])
    PlayerAction -- Bài hát kết thúc --> HasNext{Còn bài tiếp theo?}

    HasNext -- Không --> Finish([Kết thúc hàng chờ])
    HasNext -- Có --> Plan{Tài khoản đang dùng gói nào?}
    Plan -- Free --> AudioAd[Phát quảng cáo âm thanh thử nghiệm]
    AudioAd --> Next[Phát bài tiếp theo]
    Plan -- Premium --> Next
    Next --> NowPlaying
```

Luồng này bắt đầu khi người dùng đã ở trang nghe nhạc. “Tác giả” trong giao diện âm nhạc được chuẩn hóa thành **nghệ sĩ** để thống nhất với catalog và bảng `artists`. Khi người dùng rời ứng dụng hoặc khóa màn hình, `MediaSessionService` tiếp tục sở hữu player và queue; UI kết nối lại qua `MediaController` khi mở ứng dụng. Khi người dùng nhấn tim bài hát hoặc album, ứng dụng gửi yêu cầu lưu ngay và nội dung tự xuất hiện trong đúng nhóm của **Thư viện của tôi**. Quảng cáo âm thanh chỉ bắt đầu sau khi bài hiện tại đã kết thúc và không được chen ngang bài đang phát.

### 6.5. Profile và nâng cấp Premium

Câu hỏi của sơ đồ: Người dùng xem gói hiện tại và nâng cấp từ Free lên Premium như thế nào?

```mermaid
flowchart TD
    Start([Mở Profile]) --> Profile[Hiển thị hồ sơ và gói hiện tại]
    Profile --> Plan{Tài khoản đang dùng gói nào?}
    Plan -- Premium --> Benefits[/Hiển thị quyền lợi Premium và không quảng cáo/]
    Benefits --> Finish([Hoàn tất])

    Plan -- Free --> FreeInfo[/Hiển thị quyền lợi Free và giới thiệu Premium/]
    FreeInfo --> UpgradeChoice{Người dùng muốn nâng cấp?}
    UpgradeChoice -- Không --> Finish
    UpgradeChoice -- Có --> Confirm[Hiển thị xác nhận giao dịch giả lập]
    Confirm --> ConfirmChoice{Người dùng xác nhận?}
    ConfirmChoice -- Không --> FreeInfo
    ConfirmChoice -- Có --> Upgrade[Thực hiện nâng cấp giả lập]
    Upgrade --> PremiumResult[/Hiển thị Premium đã được kích hoạt/]
    PremiumResult --> Finish
```

Luồng chỉnh sửa hồ sơ và reset tài khoản demo nên được vẽ riêng nếu cần triển khai chi tiết; chúng không nằm trên kịch bản nghe nhạc chính.

## 7. System flow

### 7.1. Đăng nhập và khôi phục phiên

Câu hỏi của sơ đồ: Token được cấp, lưu và làm mới như thế nào?

```mermaid
sequenceDiagram
    actor User as Người dùng
    participant App as Android App
    participant Secure as Secure Storage
    participant API as Backend API
    participant DB as PostgreSQL

    User->>App: Nhập email và mật khẩu
    App->>API: POST /auth/login
    API->>DB: Kiểm tra user và password hash
    DB-->>API: User + trạng thái tài khoản
    alt Hợp lệ
        API->>DB: Tạo user_session với refresh token hash, hạn 7 ngày
        API-->>App: Access token + refresh token
        App->>App: Giữ access token JWT HS256 trong RAM, hạn 15 phút
        App->>Secure: Mã hóa và lưu refresh token
        App-->>User: Mở Home
    else Không hợp lệ
        API-->>App: 401 + mã lỗi chuẩn
        App-->>User: Hiển thị lỗi có thể hành động
    end

    Note over App,API: Khi access token hết hạn
    App->>Secure: Đọc refresh token
    App->>API: POST /auth/refresh
    API->>DB: Kiểm tra session chưa hết hạn/chưa bị thu hồi
    API->>DB: Thu hồi token cũ, tạo token mới cùng family
    API-->>App: Access token + refresh token mới hoặc 401
    App->>Secure: Thay refresh token cũ bằng token mới
```

### 7.2. Phát nhạc và ghi nhận lịch sử

Câu hỏi của sơ đồ: Một thao tác Play đi qua mobile, backend, storage và history như thế nào?

```mermaid
sequenceDiagram
    actor User as Người dùng
    participant UI as Mobile UI
    participant Player as Playback Controller
    participant Service as MediaSessionService
    participant Ad as Demo Audio Ad
    participant API as Backend API
    participant DB as PostgreSQL
    participant Storage as MinIO

    User->>UI: Chọn bài hát
    UI->>Player: play(trackId, sourceContext)
    Player->>API: GET /tracks/{id}/playback
    API->>DB: Kiểm tra track, subscription và quyền truy cập
    DB-->>API: Metadata + audio_object_key + ad_free
    API-->>Player: Metadata phát và entitlement hợp lệ
    Player->>Service: Gửi MediaItem, queue và lệnh phát qua MediaController
    Service->>API: GET /tracks/{id}/stream + Range
    API->>Storage: Đọc byte range theo object key
    Storage-->>API: Audio bytes
    API-->>Service: 206 Partial Content
    Service-->>Player: PlaybackState + progress từ MediaSession
    Player-->>UI: Cập nhật mini player/Now Playing

    alt Nghe thực tế đạt min(30 giây, 50% thời lượng) hoặc kết thúc phiên
        Player->>API: POST /listening-events với client_event_id
        API->>DB: Ghi idempotent listening_history
        DB-->>API: Đã ghi hoặc đã tồn tại
        API-->>Player: 202 Accepted
    end

    alt Bài hát kết thúc và hàng chờ còn bài
        alt ad_free = false (Free)
            Player->>Service: playDemoAd()
            Service->>Ad: Đọc quảng cáo âm thanh thử nghiệm
            Ad-->>Service: Audio quảng cáo
            Service-->>Player: AdCompleted hoặc safe timeout
            Player->>Service: playNext()
        else ad_free = true (Premium)
            Player->>Service: playNext()
        end
    end
```

`MediaSessionService` sở hữu ExoPlayer, MediaSession và queue; Activity/Fragment chỉ gửi lệnh qua `MediaController`. Nếu mạng gián đoạn, service được retry có giới hạn. Khi người dùng gạt ứng dụng khỏi recent tasks, service vẫn tiếp tục nếu playback đang hoạt động; Force stop sẽ dừng service. Chỉ ghi `counted_as_play = true` khi thời gian nghe thực tế tích lũy đạt `min(30 giây, 50% thời lượng bài)`; thời gian tua nhảy không được cộng và mỗi `client_event_id` chỉ được tính một lần. Quảng cáo demo không được chặn hàng chờ vô thời hạn.

### 7.3. Nâng cấp Premium giả lập

Câu hỏi của sơ đồ: Backend làm thế nào để giao dịch demo chỉ tạo một subscription hợp lệ?

```mermaid
sequenceDiagram
    actor User as Người dùng
    participant App as Android App
    participant API as Backend API
    participant DB as PostgreSQL

    User->>App: Chọn nâng cấp Premium
    App->>API: POST /demo/payments (idempotency_key, plan_code)
    API->>DB: Bắt đầu transaction
    API->>DB: Khóa/kiểm tra payment_event theo idempotency_key
    alt Yêu cầu đã xử lý
        DB-->>API: Payment và subscription hiện có
    else Yêu cầu mới
        API->>DB: Kết thúc subscription đang active
        API->>DB: Tạo subscription Premium
        API->>DB: Tạo payment_event thành công
    end
    API->>DB: Commit transaction
    API-->>App: Subscription + entitlement mới
    App-->>User: Cập nhật Profile và ẩn quảng cáo
```

Endpoint demo phải bị tắt hoặc được bảo vệ nếu hệ thống được sử dụng ngoài môi trường đồ án.

### 7.4. Gợi ý bài hát

```mermaid
sequenceDiagram
    participant App as Android App
    participant API as Backend API
    participant Rec as Recommendation Module
    participant DB as PostgreSQL

    App->>API: GET /recommendations?context=home
    API->>Rec: recommend(userId, context)
    Rec->>DB: Đọc lịch sử hợp lệ, favorite, genre, artist và bài gần đây
    alt Người dùng có đủ lịch sử
        Rec->>Rec: Điểm = genre 45% + artist 30% + popularity 15% + recency 10%
    else Cold start
        Rec->>DB: Lấy 60% bài phổ biến + 40% bài mới từ dữ liệu seed
        Rec->>Rec: Giới hạn tối đa 2 bài mỗi nghệ sĩ
    end
    Rec->>Rec: Loại bài hiện tại và 20 bài vừa nghe
    Rec-->>API: Danh sách trackId + reason
    API->>DB: Nạp metadata hiển thị
    API-->>App: Danh sách gợi ý
```

## 8. API boundary tối thiểu

| Nhóm | Endpoint gợi ý | Trách nhiệm |
|---|---|---|
| Auth | `POST /auth/register`, `POST /auth/login`, `POST /auth/refresh`, `POST /auth/logout` | Phiên đăng nhập và token |
| Home/catalog | `GET /home`, `GET /tracks/{id}`, `GET /albums/{id}`, `GET /artists/{id}` | Metadata bài hát, album và nghệ sĩ cho màn hình khám phá và Player |
| Search | `GET /search?q=` | Tìm bài hát, nghệ sĩ và album theo tên, có phân trang; không có tham số lọc |
| Playback | `GET /tracks/{id}/playback`, `GET /tracks/{id}/stream` | Trả metadata, entitlement và hỗ trợ HTTP Range |
| History | `POST /listening-events`, `GET /me/history` | Ghi idempotent và giới hạn theo gói |
| Library | `GET/PUT/DELETE /me/library/tracks/{id}`, `GET/PUT/DELETE /me/library/albums/{id}` | Bài hát yêu thích và album yêu thích |
| Playlists | `GET/POST /me/playlists`, `PUT/DELETE /me/playlists/{id}`, `POST/DELETE /me/playlists/{id}/tracks/{trackId}` | Quản lý playlist, quota và các bài hát trong playlist |
| Profile | `GET/PATCH /me/profile`, `GET /me/stats` | Hồ sơ và thống kê |
| Subscription | `GET /me/entitlements`, `POST /demo/payments`, `POST /demo/reset-plan` | Quyền lợi và demo Premium |
| Recommendation | `GET /recommendations` | Danh sách track kèm lý do đơn giản |

Mọi response lỗi nên có cùng envelope, ví dụ `code`, `message`, `details`, `request_id`. Client phân loại tối thiểu: lỗi người dùng có thể sửa, lỗi có thể retry, lỗi hết phiên và lỗi hệ thống.

## 9. Dữ liệu và đồng bộ

- PostgreSQL là nguồn sự thật cho tài khoản, catalog, bài hát yêu thích, album yêu thích, playlist, lịch sử và subscription.
- Mobile không cache metadata trong MVP; dữ liệu màn hình được lấy lại từ API khi cần. Queue chỉ tồn tại trong `MediaSessionService` của phiên chạy hiện tại.
- Audio không lưu trong database và không hỗ trợ tải offline trong phạm vi đồ án.
- `audio_object_key` không được trả như đường dẫn filesystem nội bộ.
- Mọi listening event có `client_event_id`; backend xử lý idempotent.
- Việc nâng cấp/reset plan phải đóng subscription cũ và tạo subscription mới trong cùng transaction.
- Lịch sử Free được giới hạn ở tầng query; không xóa dữ liệu cũ chỉ vì người dùng đang dùng Free.

## 10. Bảo mật và khả năng phục hồi

- Hash mật khẩu bằng thuật toán phù hợp như Argon2id hoặc bcrypt; không mã hóa hai chiều mật khẩu.
- Chỉ lưu hash của refresh token trong database.
- Access token là JWT ký bằng HS256, có thời hạn 15 phút; khóa ký chỉ nằm trong cấu hình bí mật của backend và không được commit vào repository.
- Refresh token là chuỗi ngẫu nhiên không mang dữ liệu nghiệp vụ, có thời hạn 7 ngày và được rotation sau mỗi lần sử dụng. Backend giữ bản ghi token cũ ở trạng thái thu hồi để phát hiện sử dụng lại và thu hồi cả token family.
- Thu hồi refresh token khi người dùng đăng xuất hoặc đổi mật khẩu.
- Kiểm tra quyền ở backend cho mọi thao tác Premium và quota playlist.
- Validate ID, phân trang, MIME type và Range header tại ranh giới HTTP.
- Giới hạn kích thước request và rate-limit các endpoint auth, search và payment demo.
- Gắn `request_id` cho mỗi request để nối log mobile/backend khi demo lỗi.
- Retry chỉ áp dụng cho lỗi mạng tạm thời và phải có giới hạn/backoff; không retry vô hạn.
- Không retry tự động thao tác ghi nếu chưa có idempotency key.
- Có dữ liệu demo cục bộ và kịch bản dự phòng nếu hạ tầng self-host mất mạng.

### 10.1. Vận hành MVP

- Một thành viên chạy PostgreSQL 16, MinIO và file JAR Spring Boot trực tiếp trên máy Windows.
- Nhóm có thể tạo một script PowerShell đơn giản để khởi động và kiểm tra ba tiến trình, nhưng vẫn có thể chạy từng lệnh thủ công khi mới học.
- Dùng Spring Boot Actuator `/actuator/health` để kiểm tra bằng trình duyệt.
- Spring Boot ghi log ra console và một file rolling đơn giản. Log có `request_id` nhưng không chứa token, mật khẩu hay dữ liệu riêng tư.
- Trước mỗi buổi demo, kiểm tra health endpoint, dung lượng đĩa, phát thử một bài và tua bằng HTTP Range trên điện thoại thật.

### 10.2. Sao lưu và khôi phục

- Trước buổi chạy thử và buổi demo, chạy `pg_dump` và sao chép thư mục dữ liệu MinIO sang ổ đĩa khác hoặc máy của một thành viên.
- Giữ ít nhất một bản backup đã kiểm tra; không coi bản sao trên cùng ổ đĩa là backup duy nhất.
- Khôi phục thử database và mở thử một tệp âm thanh từ backup trước buổi demo cuối.

## 11. Kiểm thử theo ranh giới

| Cấp | Nội dung ưu tiên |
|---|---|
| Unit | Quy tắc tính lượt nghe, entitlement, quota playlist, ranking recommendation và player state machine |
| Mobile integration | Repository ↔ API mapping, refresh token, queue và đồng bộ mini player/Now Playing |
| Backend integration | PostgreSQL transaction, idempotency, HTTP Range và MinIO adapter |
| Contract | Request/response cho auth, playback, history và subscription |
| UI/E2E | Đăng nhập → tìm bài → phát → yêu thích; Free → Premium; lỗi mạng khi đang phát |
| Device | Phát nền, khóa màn hình, media notification, nút tai nghe, tua, chuyển bài, hàng chờ và chuyển mạng trên ít nhất hai thiết bị |

## 12. Chia việc gợi ý cho 7 thành viên

| Vai trò chính | Phạm vi |
|---|---|
| 1. Mobile foundation | Bootstrap, navigation, design system, network và auth session |
| 2. Mobile playback | ExoPlayer, MediaSessionService, MediaController, queue, notification, mini player và Now Playing |
| 3. Mobile discovery | Home, search, catalog và recommendation UI |
| 4. Mobile library | Favorite, playlist, history, profile và paywall UI |
| 5. Backend identity | Auth, profile, session, middleware và error contract |
| 6. Backend media | Catalog, search, streaming Range, storage và recommendation |
| 7. Backend data/QA | Migration, subscription, payment demo, seed, deploy và integration test |

Mỗi phạm vi vẫn cần ít nhất một người review chéo. API contract phải được chốt sớm để mobile có thể dùng mock server trong khi backend đang hoàn thiện.

## 13. Thứ tự triển khai giảm rủi ro

1. Chốt stack, API error contract và cách quản lý token.
2. Dựng database migration, seed một tài khoản và một track hợp pháp.
3. Chạy được đăng nhập và phát một track bằng HTTP Range trên thiết bị thật.
4. Ổn định playback state, queue, foreground MediaSessionService, media notification và ghi listening event idempotent.
5. Hoàn thiện Home, Search, Library, Playlist và History.
6. Thêm recommendation content-based và cold start.
7. Thêm Profile, entitlement, quảng cáo placeholder và nâng cấp Premium giả lập.
8. Kiểm thử xuyên suốt, triển khai server và chuẩn bị dữ liệu/kịch bản demo.

## 14. Điều kiện chấp nhận kiến trúc

- Mobile không truy cập PostgreSQL hoặc MinIO bằng credential nội bộ.
- Screen không sở hữu trực tiếp audio engine hoặc queue và không gọi HTTP trực tiếp.
- Backend kiểm tra entitlement thay vì tin cờ Premium từ client.
- Streaming hỗ trợ seek bằng HTTP Range và được kiểm thử trên thiết bị thật.
- Playback tiếp tục khi chuyển ứng dụng hoặc khóa màn hình và có thể điều khiển qua notification, màn hình khóa hoặc tai nghe.
- History và payment demo có idempotency.
- Search trả bài hát, nghệ sĩ và album theo tên, không có bộ lọc nâng cao.
- Giao diện dùng chuyển màn hình mặc định, không triển khai animation tùy chỉnh.
- Sơ đồ và API contract được cập nhật khi ranh giới module thay đổi.

## 15. Tổng kết quyết định kiến trúc

### 15.1. Quyết định đã chốt

- **Mobile:** Kotlin Native, phát triển bằng Android Studio.
- **Android UI:** XML Views với Activity/Fragment; không dùng Jetpack Compose trong MVP.
- **State management:** AndroidX ViewModel + StateFlow; Fragment collect state bằng `repeatOnLifecycle`.
- **Dependency injection:** Hilt; dùng constructor injection cho repository/use case và Android entry point cho Activity, Fragment, ViewModel cùng `MediaSessionService`.
- **Lưu trữ cục bộ:** access token chỉ giữ trong RAM; refresh token được mã hóa bằng khóa AES-GCM nằm trong Android Keystore; Preferences DataStore lưu cài đặt đơn giản. Không dùng Room hoặc cache metadata; queue không được khôi phục sau khi tiến trình bị hủy.
- **Xác thực:** access token là JWT HS256 hết hạn sau 15 phút; refresh token ngẫu nhiên hết hạn sau 7 ngày, backend chỉ lưu hash, rotation sau mỗi lần refresh và thu hồi khi logout hoặc đổi mật khẩu.
- **Lượt nghe:** một phiên được tính khi thời gian nghe thực tế đạt `min(30 giây, 50% thời lượng bài)`; không cộng thời gian tua và chống ghi trùng bằng `client_event_id`.
- **Công thức gợi ý MVP:** 45% độ phù hợp thể loại, 30% mức yêu thích nghệ sĩ từ lịch sử/favorite, 15% độ phổ biến và 10% độ mới; loại bài đang phát cùng 20 bài vừa nghe. Free và Premium dùng cùng một thuật toán.
- **Tìm kiếm MVP:** tìm bài hát, nghệ sĩ và album theo tên; không có bộ lọc nâng cao.
- **Giao diện MVP:** dùng hành vi chuyển màn hình mặc định của Android; không triển khai animation tùy chỉnh.
- **Cold start và dữ liệu seed:** dưới 5 lượt nghe hợp lệ dùng 60% bài phổ biến và 40% bài mới, tối đa 2 bài mỗi nghệ sĩ. Seed khoảng 50 track hợp pháp, 10 nghệ sĩ, 10 album, 5 thể loại và `popularity_score` ban đầu.
- **Triển khai MVP:** một máy Windows của nhóm chạy Spring Boot, PostgreSQL 16 và MinIO. Điện thoại kết nối qua Wi-Fi LAN tới cổng `8080` trong debug build.
- **Vận hành:** kiểm tra thủ công bằng Actuator health, log console/file và backup PostgreSQL cùng thư mục MinIO trước buổi chạy thử/demo.
- **Backend:** Java với Spring Boot, triển khai dạng modular monolith và cung cấp REST/JSON API.
- **Database:** PostgreSQL 16; schema được quản lý bằng Flyway migration.
- **Lưu tệp MVP:** dùng MinIO chạy trực tiếp trên server.
- **Streaming MVP:** Spring Boot xác thực quyền, đọc object từ MinIO và proxy HTTP Range về Android; signed URL để giai đoạn sau.
- **Player MVP:** AndroidX Media3 ExoPlayer; ExoPlayer, queue và MediaSession nằm trong foreground `MediaSessionService`; UI điều khiển qua `MediaController`.
- **Playback MVP:** hỗ trợ phát nền, media notification và điều khiển từ màn hình khóa hoặc tai nghe.

### 15.2. Trạng thái

Không còn quyết định kiến trúc mở trong phạm vi phân tích MVP hiện tại. Phiên bản dependency cụ thể, lệnh build/test và đường dẫn triển khai sẽ được ghi khi khởi tạo mã nguồn và máy chủ thực tế.

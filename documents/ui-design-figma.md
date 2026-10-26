# Thiết kế giao diện theo Figma

Nguồn: [Figma – Online Music Player](https://www.figma.com/design/LkFwEdETR7fe6nKQ3YpEVx/Sans-titre--Copy-?m=draw) (file key `LkFwEdETR7fe6nKQ3YpEVx`, Page 1).
Đọc ngày 2026-10-26. Khi Figma đổi, cập nhật bảng màn hình bên dưới; node id dùng để mở đúng frame bằng `?node-id=<id>` (thay `:` bằng `-`).

## 1. Nguyên tắc chung

- Khung thiết kế 390×844 (Android), một theme tối duy nhất, font Inter, line-height 1.35.
- Bố cục mỗi màn hình: status bar 32 → nội dung (padding ngang 20, dọc 12) → khu điều khiển cố định 128 (mini player 64 + bottom navigation 64) → vùng gesture 20.
- Màn hình con không có tab (Now Playing, Queue, Sign in…) ẩn bottom navigation.
- Mỗi màn hình dữ liệu có đủ trạng thái: populated, empty, load error (đúng `CONVENTIONS.md`).
- Không animation tùy chỉnh (giữ quyết định kiến trúc); chỉ chuyển màn hình mặc định.
- Ảnh bìa/ảnh nghệ sĩ trong Figma là ảnh minh họa; app dùng ảnh từ backend khi có, ngược lại dùng placeholder `surface_secondary` bo góc.

## 2. Design token

Các màu này đã có trong `android/app/src/main/res/values/colors.xml` và khớp Figma.

| Token Android | Hex | Dùng cho |
|---|---|---|
| `background_primary` | `#101113` | Nền màn hình, nền bottom nav |
| `surface_primary` | `#1B1D21` | Card album, banner Premium, ô nhập |
| `surface_secondary` | `#25282D` | Mini player, avatar, placeholder ảnh |
| `border_default` | `#34373D` | Viền nút outline, thanh cuộn, track seekbar |
| `text_primary` | `#F5F6F7` | Tiêu đề, tên bài |
| `text_secondary` | `#A4A8B1` | Nghệ sĩ, thời lượng, “See all”, tab chưa chọn |
| `text_muted` | `#727782` | Chú thích, phiên bản app |
| `accent_primary` | `#C2F66B` | Tab đang chọn, tim đã bật, nút play lớn, tiến độ phát |
| `accent_content` | `#17210C` | Icon/chữ trên nền accent |
| `error` / `error_container` | `#FF9D9D` / `#362326` | Lỗi, nút “Remove” trong Queue |

Typography (Inter):

| Vai trò | Cỡ / độ đậm | Ví dụ |
|---|---|---|
| Tiêu đề màn hình | 28–32 Bold | “Your library”, “Profile”, “Welcome back.” |
| Tiêu đề Home / Now Playing | 24–28 Bold | “Find your night sound.”, “Night Drive” |
| Tiêu đề section | 16 Regular | “Recently played”, “For you” |
| Tên bài trong danh sách | 14 Regular | Song row |
| Phụ đề, “See all” | 12 Regular `text_secondary` | “Orion Vale · 3:58” |
| Tên trong card / mini player | 12–13 SemiBold | Album card, mini player |
| Nhãn bottom nav | 11 (SemiBold khi chọn) | Home/Search/Library/Profile |

Kích thước & bo góc: card 12dp, artwork trong card 6–8dp, avatar/nghệ sĩ tròn; song row cao 62 với artwork 48; album card ngang cao 64 với artwork 52; mini player cao 60, bo 12, có thanh tiến độ 2dp màu accent ở đáy; icon bottom nav 22; nút chính (Sign in, View Premium) nền `text_primary` chữ tối, bo 12, cao ~48; nút play lớn Now Playing tròn ~72 nền accent.

Icon: bộ Lucide outline (house, search, library, user-round, play, pause, chevron, heart, list-plus, crown, skip-back/forward, list-music…). Thêm dưới dạng vector drawable vào `res/drawable/ic_*.xml`.

## 3. Danh sách màn hình và đối chiếu với Android

Trạng thái: ✅ đã có và gần đúng thiết kế · 🟡 có chức năng nhưng UI chưa theo Figma hoặc thiếu trạng thái · ❌ chưa có.

| Nhóm | Frame Figma (node id) | Android hiện tại | Trạng thái |
|---|---|---|---|
| Auth | Sign in `32:54`, Sign in – error `32:95` | `ui/auth/SignInFragment` | ✅ |
| Auth | Create account `32:143`, validation `32:189` | `ui/auth/CreateAccountFragment` | ✅ |
| Auth | Session expired (modal) `2028:1072` | Dialog trong `MainActivity` (theo `SessionManager.expired()`) | 🟡 nút dạng chữ, Figma là nút trắng |
| Home | Home `32:237` | `ui/home/HomeFragment` | ✅ |
| Search | Recent searches `32:344`, results `32:417`, no results `32:496`, load error `32:544` | `ui/search/SearchFragment` | ✅ |
| Library | Populated `32:592`, empty `32:683` | `ui/library/LibraryFragment` | ✅ |
| Playlist | My playlists – list `32:733` | `ui/playlist/MyPlaylistsFragment` | ✅ |
| Playlist | Create playlist – name form `32:803` | `ui/playlist/CreatePlaylistFragment` | ✅ |
| Playlist | Playlist detail populated `32:853`, just created `32:941` | `ui/playlist/PlaylistDetailFragment` | ✅ |
| Playlist | Add songs to playlist `32:1001` | `ui/playlist/PlaylistTrackPickerFragment` | ✅ |
| Playlist | Playlist limit – Free `32:1088` | `ui/playlist/PlaylistLimitSheet` | ✅ |
| Player | Now Playing playing `32:1167`, loading `32:1221`, buffering `32:1278`, error `32:1332` | `ui/player/NowPlayingFragment` | ✅ |
| Player | Add to playlist – chooser `32:1390` | `ui/player/AddToPlaylistSheet` | ✅ (chưa biết trước bài đã có trong playlist) |
| Player | Queue populated `2028:912`, empty `2028:1018` | `ui/player/QueueFragment` | ✅ (chưa kéo thả sắp xếp) |
| Profile | Profile – Free `32:1482`, Premium `2028:3` | `ui/profile/ProfileFragment` | ✅ |
| History | Premium `2028:117`, Free limit `2028:291`, empty `2028:429`, load error `2028:476` | `ui/history/HistoryFragment` | ✅ (không hiện bài cũ bị khóa: server đã lọc) |
| Catalog | Album detail populated `2028:536`, error `2028:687` | `ui/album/AlbumDetailFragment` | ✅ |
| Catalog | Artist detail populated `2028:749`, error `2028:850` | `ui/artist/ArtistDetailFragment` | ✅ |
| Lỗi chung | Network error banner `2028:1180`, full page `2028:1311` | Mỗi màn có trạng thái lỗi + Retry (`view_state`) | 🟡 chưa có banner "You're offline" chung |
| Entitlement | Premium required `2028:1355`, plan expired `2028:1515`, verification error `2028:1639` | Playlist limit sheet; lỗi xác minh là toast | 🟡 chưa có 3 sheet này |
| Thành phần | Section “Reusable components” `2029:2` | — | Tham chiếu khi tạo layout dùng chung |
| Icon app | Adaptive background `2039:981`, foreground `2039:982` | `ic_launcher_background/foreground/monochrome.xml` | ✅ |
| Mini player | “Persistent controls” trên mọi tab | `view_mini_player.xml` theo Figma | ✅ |

## 4. Thành phần dùng chung trên Android

Đã tạo (M3) trong `res/layout/`; style chữ/nút trong `res/values/styles.xml`; icon Lucide trong `res/drawable/ic_*.xml`. Khi dựng màn mới, `<include>` hoặc inflate các layout này thay vì tạo layout riêng:

- `item_song_row` – artwork 48 + tên + “Nghệ sĩ · thời lượng” + nút hành động (play/tim/…).
- `item_album_card` – card ngang 64 (Home: Recently played, New releases) và card vuông (Library: Liked albums).
- `item_artist_circle` – ảnh tròn 48 + tên.
- `view_section_header` – tiêu đề 16 + “See all”.
- `view_mini_player` – restyle: artwork, tên, nghệ sĩ, pause/play, chevron-up mở Now Playing, thanh tiến độ 2dp.
- `view_state` – một layout cho loading / empty / error có nút “Try again”.

## 5. Nội dung chữ

Figma dùng tiếng Anh (“Your library”, “See all”, “Create playlist”…). Chuỗi đặt trong `strings.xml`; nếu nhóm muốn giao diện tiếng Việt thì chỉ cần dịch file chuỗi, không đổi layout.

Các mục trong Profile như Notifications, Help & support, Privacy & terms không có chức năng trong phạm vi MVP; xem quyết định trong ExecPlan.

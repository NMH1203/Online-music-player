package vn.edu.usth.online_music_player.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class PlaylistRequest {
    private String name;
    private Boolean isPrivate;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Boolean getIsPrivate() { return isPrivate; }
    public void setIsPrivate(Boolean isPrivate) { this.isPrivate = isPrivate; }
        // 1. Thêm bài hát vào playlist (Có kiểm tra quyền sở hữu và tránh trùng lặp)
    public boolean addTrackToPlaylist(Long userId, Long playlistId, Long trackId) {
        // Kiểm tra xem playlist có thuộc về user này không (Ownership)
        String checkOwner = "SELECT COUNT(*) FROM playlists WHERE id = ? AND user_id = ?";
        Integer isOwner = jdbcTemplate.queryForObject(checkOwner, Integer.class, playlistId, userId);
        if (isOwner == null || isOwner == 0) return false;

        // Kiểm tra tiêu chí nghiệm thu: Không trùng bài trong playlist
        String checkDuplicate = "SELECT COUNT(*) FROM playlist_tracks WHERE playlist_id = ? AND track_id = ?";
        Integer isDuplicate = jdbcTemplate.queryForObject(checkDuplicate, Integer.class, playlistId, trackId);
        if (isDuplicate != null && isDuplicate > 0) return true; // Đã có rồi thì báo thành công (Idempotent)

        String insertSql = "INSERT INTO playlist_tracks (playlist_id, track_id, created_at) VALUES (?, ?, ?)";
        jdbcTemplate.update(insertSql, playlistId, trackId, LocalDateTime.now());
        return true;
    }

    // 2. Xóa bài hát khỏi playlist
    public boolean removeTrackFromPlaylist(Long userId, Long playlistId, Long trackId) {
        String checkOwner = "SELECT COUNT(*) FROM playlists WHERE id = ? AND user_id = ?";
        Integer isOwner = jdbcTemplate.queryForObject(checkOwner, Integer.class, playlistId, userId);
        if (isOwner == null || isOwner == 0) return false;

        String deleteSql = "DELETE FROM playlist_tracks WHERE playlist_id = ? AND track_id = ?";
        jdbcTemplate.update(deleteSql, playlistId, trackId);
        return true;
    }

    // 3. Logic xử lý Yêu thích bài hát / Album (Favorites)
    public void toggleFavorite(Long userId, Long targetId, String type) {
        String tableName = type.equalsIgnoreCase("ALBUM") ? "favorite_albums" : "favorite_tracks";
        String idColumn = type.equalsIgnoreCase("ALBUM") ? "album_id" : "track_id";

        // Kiểm tra xem đã bấm yêu thích chưa
        String checkSql = String.format("SELECT COUNT(*) FROM %s WHERE user_id = ? AND %s = ?", tableName, idColumn);
        Integer count = jdbcTemplate.queryForObject(checkSql, Integer.class, userId, targetId);

        if (count != null && count > 0) {
            // Nếu đã yêu thích -> Bấm lại tức là Hủy yêu thích
            String deleteSql = String.format("DELETE FROM %s WHERE user_id = ? AND %s = ?", tableName, idColumn);
            jdbcTemplate.update(deleteSql, userId, targetId);
        } else {
            // Nếu chưa yêu thích -> Tiến hành thêm vào bảng thư viện cá nhân
            String insertSql = String.format("INSERT INTO %s (user_id, %s, created_at) VALUES (?, ?, ?)", tableName, idColumn);
            jdbcTemplate.update(insertSql, userId, targetId, LocalDateTime.now());
        }
    }

    // 4. Lấy danh sách các bài hát đã thích của User
    public List<Map<String, Object>> getFavoriteTracks(Long userId) {
        String sql = "SELECT t.id, t.name, t.duration, ft.created_at FROM favorite_tracks ft " +
                     "JOIN tracks t ON ft.track_id = t.id WHERE ft.user_id = ? ORDER BY ft.created_at DESC";
        return jdbcTemplate.queryForList(sql, userId);
    }

}

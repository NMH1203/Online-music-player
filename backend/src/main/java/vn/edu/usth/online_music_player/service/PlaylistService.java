package vn.edu.usth.online_music_player.service;

import vn.edu.usth.online_music_player.dto.PlaylistRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class PlaylistService {
    private final JdbcTemplate jdbcTemplate;

    public PlaylistService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, Object> createPlaylist(Long userId, PlaylistRequest request) {
        String sql = "INSERT INTO playlists (user_id, name, is_private, created_at, updated_at) VALUES (?, ?, ?, ?, ?) RETURNING id, name, is_private";
        LocalDateTime now = LocalDateTime.now();
        Boolean isPrivate = request.getIsPrivate() != null ? request.getIsPrivate() : false;
        return jdbcTemplate.queryForMap(sql, userId, request.getName().trim(), isPrivate, now, now);
    }

    public List<Map<String, Object>> getPlaylistsByUserId(Long userId) {
        String sql = "SELECT id, name, is_private, created_at FROM playlists WHERE user_id = ? ORDER BY created_at DESC";
        return jdbcTemplate.queryForList(sql, userId);
    }
}

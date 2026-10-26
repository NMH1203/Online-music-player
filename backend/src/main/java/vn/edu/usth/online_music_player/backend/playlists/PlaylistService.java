package vn.edu.usth.online_music_player.backend.playlists;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.usth.online_music_player.backend.common.ApiException;

@Service
public class PlaylistService {
    private static final RowMapper<PlaylistSummaryResponse> SUMMARY_ROW_MAPPER =
            PlaylistService::mapSummary;
    private static final RowMapper<PlaylistTrackResponse> TRACK_ROW_MAPPER =
            PlaylistService::mapTrack;

    private final JdbcTemplate jdbcTemplate;

    public PlaylistService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<PlaylistSummaryResponse> listPlaylists(UUID userId) {
        return jdbcTemplate.query(
                """
                SELECT p.id, p.name, p.description, COUNT(pt.track_id) AS track_count,
                       p.created_at, p.updated_at
                FROM playlists p
                LEFT JOIN playlist_tracks pt ON pt.playlist_id = p.id
                WHERE p.user_id = ?
                GROUP BY p.id
                ORDER BY p.updated_at DESC, p.id
                """,
                SUMMARY_ROW_MAPPER,
                userId);
    }

    public PlaylistQuotaResponse getPlaylistQuota(UUID userId) {
        Integer maxPlaylists = findMaxPlaylists(userId);
        long playlistCount = countPlaylists(userId);
        return new PlaylistQuotaResponse(
                playlistCount,
                maxPlaylists,
                maxPlaylists == null || playlistCount < maxPlaylists);
    }

    public PlaylistDetailResponse getPlaylist(UUID userId, UUID playlistId) {
        PlaylistSummaryResponse playlist = findOwnedPlaylist(userId, playlistId);
        List<PlaylistTrackResponse> tracks = listTracks(playlistId);
        return new PlaylistDetailResponse(
                playlist.id(),
                playlist.name(),
                playlist.description(),
                tracks,
                playlist.createdAt(),
                playlist.updatedAt());
    }

    @Transactional
    public PlaylistSummaryResponse createPlaylist(UUID userId, CreatePlaylistRequest request) {
        // Serialize playlist creation for one account so concurrent requests cannot exceed
        // the plan's quota after both observe the same count.
        lockUser(userId);
        PlaylistQuotaResponse quota = getPlaylistQuota(userId);
        if (!quota.canCreatePlaylist()) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "playlist_limit_reached",
                    "Gói hiện tại đã đạt giới hạn playlist.",
                    Map.of(
                            "playlistCount", quota.playlistCount(),
                            "maxPlaylists", quota.maxPlaylists(),
                            "requiresPremium", true));
        }

        UUID playlistId = UUID.randomUUID();
        String description = request.description();
        if (description != null) {
            description = description.isBlank() ? null : description.trim();
        }

        jdbcTemplate.update(
                "INSERT INTO playlists (id, user_id, name, description) VALUES (?, ?, ?, ?)",
                playlistId,
                userId,
                request.name().trim(),
                description);
        return findOwnedPlaylist(userId, playlistId);
    }

    private Integer findMaxPlaylists(UUID userId) {
        List<PlanLimit> plans = jdbcTemplate.query(
                """
                SELECT CASE WHEN (s.expires_at IS NOT NULL AND s.expires_at <= now())
                            OR s.starts_at > now() OR NOT p.active
                       THEN free.max_playlists ELSE p.max_playlists END AS max_playlists
                FROM subscriptions s
                JOIN plans p ON p.id = s.plan_id
                JOIN plans free ON free.code = 'FREE'
                WHERE s.user_id = ? AND s.status = 'active'
                """,
                (resultSet, rowNumber) ->
                        new PlanLimit((Integer) resultSet.getObject("max_playlists")),
                userId);
        if (plans.isEmpty()) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "active_subscription_required",
                    "Tài khoản chưa có gói sử dụng đang hoạt động.");
        }
        return plans.get(0).maxPlaylists();
    }

    private long countPlaylists(UUID userId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM playlists WHERE user_id = ?", Long.class, userId);
        return count == null ? 0 : count;
    }

    private void lockUser(UUID userId) {
        List<UUID> users = jdbcTemplate.query(
                "SELECT id FROM users WHERE id = ? FOR UPDATE",
                (resultSet, rowNumber) -> resultSet.getObject("id", UUID.class),
                userId);
        if (users.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "user_not_found", "Không tìm thấy tài khoản.");
        }
    }

    @Transactional
    public PlaylistSummaryResponse updatePlaylist(
            UUID userId, UUID playlistId, UpdatePlaylistRequest request) {
        lockOwnedPlaylist(userId, playlistId);
        String description = request.description();
        if (description != null) {
            description = description.isBlank() ? null : description.trim();
        }
        jdbcTemplate.update(
                "UPDATE playlists SET name = ?, description = ? WHERE user_id = ? AND id = ?",
                request.name().trim(),
                description,
                userId,
                playlistId);
        return findOwnedPlaylist(userId, playlistId);
    }

    @Transactional
    public void deletePlaylist(UUID userId, UUID playlistId) {
        lockOwnedPlaylist(userId, playlistId);
        jdbcTemplate.update(
                "DELETE FROM playlists WHERE user_id = ? AND id = ?", userId, playlistId);
    }

    @Transactional
    public void addTrack(UUID userId, UUID playlistId, UUID trackId) {
        lockOwnedPlaylist(userId, playlistId);
        if (!exists(
                "SELECT EXISTS (SELECT 1 FROM tracks WHERE id = ? AND status = 'published')",
                trackId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "not_found", "Không tìm thấy bài hát.");
        }

        Integer nextPosition = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(position), -1) + 1 FROM playlist_tracks WHERE playlist_id = ?",
                Integer.class,
                playlistId);
        int inserted = jdbcTemplate.update(
                """
                INSERT INTO playlist_tracks (playlist_id, track_id, position)
                VALUES (?, ?, ?)
                ON CONFLICT (playlist_id, track_id) DO NOTHING
                """,
                playlistId,
                trackId,
                nextPosition == null ? 0 : nextPosition);
        if (inserted > 0) {
            touchPlaylist(userId, playlistId);
        }
    }

    @Transactional
    public void removeTrack(UUID userId, UUID playlistId, UUID trackId) {
        lockOwnedPlaylist(userId, playlistId);
        int deleted = jdbcTemplate.update(
                "DELETE FROM playlist_tracks WHERE playlist_id = ? AND track_id = ?",
                playlistId,
                trackId);
        if (deleted > 0) {
            touchPlaylist(userId, playlistId);
        }
    }

    private PlaylistSummaryResponse findOwnedPlaylist(UUID userId, UUID playlistId) {
        List<PlaylistSummaryResponse> rows = jdbcTemplate.query(
                """
                SELECT p.id, p.name, p.description, COUNT(pt.track_id) AS track_count,
                       p.created_at, p.updated_at
                FROM playlists p
                LEFT JOIN playlist_tracks pt ON pt.playlist_id = p.id
                WHERE p.user_id = ? AND p.id = ?
                GROUP BY p.id
                """,
                SUMMARY_ROW_MAPPER,
                userId,
                playlistId);
        if (rows.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "not_found", "Không tìm thấy playlist.");
        }
        return rows.get(0);
    }

    private List<PlaylistTrackResponse> listTracks(UUID playlistId) {
        return jdbcTemplate.query(
                """
                SELECT t.id, t.title, t.duration_seconds, artist.name AS artist_name,
                       pt.position, pt.added_at
                FROM playlist_tracks pt
                JOIN tracks t ON t.id = pt.track_id
                JOIN artists artist ON artist.id = t.primary_artist_id
                WHERE pt.playlist_id = ?
                ORDER BY pt.position, t.id
                """,
                TRACK_ROW_MAPPER,
                playlistId);
    }

    private void lockOwnedPlaylist(UUID userId, UUID playlistId) {
        List<UUID> rows = jdbcTemplate.query(
                "SELECT id FROM playlists WHERE user_id = ? AND id = ? FOR UPDATE",
                (resultSet, rowNumber) -> resultSet.getObject("id", UUID.class),
                userId,
                playlistId);
        if (rows.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "not_found", "Không tìm thấy playlist.");
        }
    }

    private void touchPlaylist(UUID userId, UUID playlistId) {
        jdbcTemplate.update(
                "UPDATE playlists SET updated_at = now() WHERE user_id = ? AND id = ?",
                userId,
                playlistId);
    }

    private boolean exists(String sql, UUID id) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(sql, Boolean.class, id));
    }

    private static PlaylistSummaryResponse mapSummary(ResultSet resultSet, int rowNumber)
            throws SQLException {
        return new PlaylistSummaryResponse(
                resultSet.getObject("id", UUID.class),
                resultSet.getString("name"),
                resultSet.getString("description"),
                resultSet.getLong("track_count"),
                toInstant(resultSet.getObject("created_at", OffsetDateTime.class)),
                toInstant(resultSet.getObject("updated_at", OffsetDateTime.class)));
    }

    private static PlaylistTrackResponse mapTrack(ResultSet resultSet, int rowNumber)
            throws SQLException {
        return new PlaylistTrackResponse(
                resultSet.getObject("id", UUID.class),
                resultSet.getString("title"),
                resultSet.getInt("duration_seconds"),
                resultSet.getString("artist_name"),
                resultSet.getInt("position"),
                toInstant(resultSet.getObject("added_at", OffsetDateTime.class)));
    }

    private static Instant toInstant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }

    private record PlanLimit(Integer maxPlaylists) {}
}

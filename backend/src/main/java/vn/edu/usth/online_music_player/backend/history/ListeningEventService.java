package vn.edu.usth.online_music_player.backend.history;

import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.usth.online_music_player.backend.common.ApiException;

@Service
public class ListeningEventService {
    private final JdbcTemplate jdbcTemplate;

    public ListeningEventService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public ListeningEventResponse recordEvent(UUID userId, ListeningEventRequest request) {
        if (request == null
                || request.clientEventId() == null
                || request.trackId() == null
                || request.sourceContext() == null
                || request.sourceContext().isBlank()
                || request.startedAt() == null
                || request.listenedSeconds() == null) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "validation_failed", "Dữ liệu sự kiện nghe không hợp lệ.");
        }

        ExistingEvent existing = findExisting(request.clientEventId());
        if (existing != null) {
            return resolveExisting(userId, request, existing);
        }

        if (request.endedAt() != null && request.endedAt().isBefore(request.startedAt())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "invalid_time_range",
                    "Thời điểm kết thúc phải lớn hơn hoặc bằng thời điểm bắt đầu.");
        }

        List<TrackMeta> tracks = jdbcTemplate.query(
                "SELECT duration_seconds, status FROM tracks WHERE id = ?",
                (rs, rowNum) -> new TrackMeta(rs.getInt("duration_seconds"), rs.getString("status")),
                request.trackId());
        if (tracks.isEmpty() || !"published".equals(tracks.get(0).status())) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND, "track_not_found", "Không tìm thấy bài hát có thể phát.");
        }

        int durationSeconds = tracks.get(0).durationSeconds();
        int listenedSeconds = request.listenedSeconds();
        if (listenedSeconds < 0 || listenedSeconds > durationSeconds + 60) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "invalid_listened_seconds",
                    "Thời lượng nghe nằm ngoài phạm vi hợp lệ.");
        }

        if (request.sourcePlaylistId() != null) {
            List<Integer> playlists = jdbcTemplate.query(
                    "SELECT 1 FROM playlists WHERE id = ? AND user_id = ?",
                    (rs, rowNum) -> 1,
                    request.sourcePlaylistId(),
                    userId);
            if (playlists.isEmpty()) {
                throw new ApiException(
                        HttpStatus.NOT_FOUND,
                        "playlist_not_found",
                        "Không tìm thấy danh sách phát thuộc người dùng.");
            }
        }

        int threshold = Math.min(30, (int) Math.ceil(durationSeconds * 0.5));
        boolean countedAsPlay = listenedSeconds >= threshold;

        int inserted = jdbcTemplate.update(
                """
                INSERT INTO listening_history (
                    client_event_id, user_id, track_id, source_playlist_id,
                    source_context, started_at, ended_at, listened_seconds, counted_as_play
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (client_event_id) DO NOTHING
                """,
                request.clientEventId(),
                userId,
                request.trackId(),
                request.sourcePlaylistId(),
                request.sourceContext().trim(),
                Timestamp.from(request.startedAt()),
                request.endedAt() == null ? null : Timestamp.from(request.endedAt()),
                listenedSeconds,
                countedAsPlay);

        if (inserted == 1) {
            if (countedAsPlay) {
                jdbcTemplate.update(
                        "UPDATE tracks SET popularity_score = popularity_score + 1 WHERE id = ?",
                        request.trackId());
            }
            return new ListeningEventResponse(request.clientEventId(), countedAsPlay);
        }

        ExistingEvent concurrent = findExisting(request.clientEventId());
        if (concurrent != null) {
            return resolveExisting(userId, request, concurrent);
        }
        throw new ApiException(
                HttpStatus.CONFLICT, "idempotency_conflict", "clientEventId đã được sử dụng.");
    }

    private ExistingEvent findExisting(UUID clientEventId) {
        List<ExistingEvent> rows = jdbcTemplate.query(
                """
                SELECT user_id, track_id, counted_as_play
                FROM listening_history
                WHERE client_event_id = ?
                """,
                (rs, rowNum) -> new ExistingEvent(
                        rs.getObject("user_id", UUID.class),
                        rs.getObject("track_id", UUID.class),
                        rs.getBoolean("counted_as_play")),
                clientEventId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private ListeningEventResponse resolveExisting(
            UUID userId, ListeningEventRequest request, ExistingEvent existing) {
        if (!existing.userId().equals(userId) || !existing.trackId().equals(request.trackId())) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "idempotency_conflict", "clientEventId đã được sử dụng.");
        }
        return new ListeningEventResponse(request.clientEventId(), existing.countedAsPlay());
    }

    private record TrackMeta(int durationSeconds, String status) {}

    private record ExistingEvent(UUID userId, UUID trackId, boolean countedAsPlay) {}
}

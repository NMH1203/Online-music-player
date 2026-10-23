package vn.edu.usth.online_music_player.backend.playback;

import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import vn.edu.usth.online_music_player.backend.common.ApiException;

@Service
public class PlaybackService {
    private final JdbcTemplate jdbcTemplate;

    public PlaybackService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public TrackPlayback requirePlayableTrack(UUID trackId) {
        List<TrackPlayback> matches = jdbcTemplate.query(
                        """
                        SELECT t.id, t.title, artist.name AS artist_name, t.duration_seconds,
                               t.mime_type, t.file_size_bytes, metadata.bucket_name,
                               t.audio_object_key, metadata.attribution_text
                        FROM tracks t
                        JOIN artists artist ON artist.id = t.primary_artist_id
                        JOIN track_audio_metadata metadata ON metadata.track_id = t.id
                        WHERE t.id = ? AND t.status = 'published'
                        """,
                        (resultSet, rowNumber) -> new TrackPlayback(
                                resultSet.getObject("id", UUID.class),
                                resultSet.getString("title"),
                                resultSet.getString("artist_name"),
                                resultSet.getInt("duration_seconds"),
                                resultSet.getString("mime_type"),
                                resultSet.getLong("file_size_bytes"),
                                resultSet.getString("bucket_name"),
                                resultSet.getString("audio_object_key"),
                                resultSet.getString("attribution_text")),
                        trackId);
        if (matches.isEmpty()) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND, "track_not_found", "Không tìm thấy bài hát có thể phát.");
        }
        return matches.get(0);
    }
}

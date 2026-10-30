package vn.edu.usth.online_music_player.backend.catalog;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import vn.edu.usth.online_music_player.backend.common.ApiException;

@Service
public class TrackCatalogService {
    private static final RowMapper<TrackCatalogResponse> TRACK_ROW_MAPPER =
            TrackCatalogService::mapTrack;
    private final JdbcTemplate jdbcTemplate;

    public TrackCatalogService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<TrackCatalogResponse> listPublishedTracks(String query) {
        String search = SearchTerm.normalize(query);
        if (search.length() > 100) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "invalid_query", "Từ khóa tìm kiếm tối đa 100 ký tự.");
        }

        if (search.isEmpty()) {
            return jdbcTemplate.query(
                    """
                    SELECT t.id, t.title, t.duration_seconds, artist.name AS artist_name,
                           album.title AS album_title
                    FROM tracks t
                    JOIN artists artist ON artist.id = t.primary_artist_id
                    LEFT JOIN albums album ON album.id = t.album_id
                    WHERE t.status = 'published'
                    ORDER BY t.popularity_score DESC, t.published_at DESC, t.id
                    LIMIT 100
                    """,
                    TRACK_ROW_MAPPER);
        }

        String pattern = SearchTerm.pattern(search);
        return jdbcTemplate.query(
                """
                SELECT t.id, t.title, t.duration_seconds, artist.name AS artist_name,
                       album.title AS album_title
                FROM tracks t
                JOIN artists artist ON artist.id = t.primary_artist_id
                LEFT JOIN albums album ON album.id = t.album_id
                WHERE t.status = 'published'
                  AND (t.title ILIKE ? OR artist.name ILIKE ? OR album.title ILIKE ?)
                ORDER BY t.popularity_score DESC, t.published_at DESC, t.id
                LIMIT 100
                """,
                TRACK_ROW_MAPPER,
                pattern,
                pattern,
                pattern);
    }

    private static TrackCatalogResponse mapTrack(ResultSet resultSet, int rowNumber)
            throws SQLException {
        return new TrackCatalogResponse(
                resultSet.getObject("id", UUID.class),
                resultSet.getString("title"),
                resultSet.getInt("duration_seconds"),
                resultSet.getString("artist_name"),
                resultSet.getString("album_title"));
    }
}

package vn.edu.usth.online_music_player.backend.library;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.usth.online_music_player.backend.common.ApiException;

@Service
public class LibraryService {
    private static final RowMapper<FavoriteTrackResponse> TRACK_ROW_MAPPER =
            LibraryService::mapTrack;
    private static final RowMapper<FavoriteAlbumResponse> ALBUM_ROW_MAPPER =
            LibraryService::mapAlbum;

    private final JdbcTemplate jdbcTemplate;

    public LibraryService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<FavoriteTrackResponse> listFavoriteTracks(UUID userId) {
        return jdbcTemplate.query(
                """
                SELECT t.id, t.title, t.duration_seconds, t.primary_artist_id AS artist_id,
                       artist.name AS artist_name, t.album_id AS album_id,
                       album.title AS album_title, favorite.added_at
                FROM library_tracks favorite
                JOIN tracks t ON t.id = favorite.track_id
                JOIN artists artist ON artist.id = t.primary_artist_id
                LEFT JOIN albums album ON album.id = t.album_id
                WHERE favorite.user_id = ?
                ORDER BY favorite.added_at DESC, t.id
                """,
                TRACK_ROW_MAPPER,
                userId);
    }

    public List<FavoriteAlbumResponse> listFavoriteAlbums(UUID userId) {
        return jdbcTemplate.query(
                """
                SELECT album.id, album.title, album.primary_artist_id AS artist_id,
                       artist.name AS artist_name, album.release_date, favorite.added_at
                FROM library_albums favorite
                JOIN albums album ON album.id = favorite.album_id
                JOIN artists artist ON artist.id = album.primary_artist_id
                WHERE favorite.user_id = ?
                ORDER BY favorite.added_at DESC, album.id
                """,
                ALBUM_ROW_MAPPER,
                userId);
    }

    @Transactional
    public void addFavoriteTrack(UUID userId, UUID trackId) {
        if (!exists("SELECT EXISTS (SELECT 1 FROM tracks WHERE id = ?)", trackId)) {
            throw notFound("Không tìm thấy bài hát.");
        }
        jdbcTemplate.update(
                "INSERT INTO library_tracks (user_id, track_id) VALUES (?, ?) "
                        + "ON CONFLICT (user_id, track_id) DO NOTHING",
                userId,
                trackId);
    }

    @Transactional
    public void removeFavoriteTrack(UUID userId, UUID trackId) {
        jdbcTemplate.update(
                "DELETE FROM library_tracks WHERE user_id = ? AND track_id = ?",
                userId,
                trackId);
    }

    @Transactional
    public void addFavoriteAlbum(UUID userId, UUID albumId) {
        if (!exists("SELECT EXISTS (SELECT 1 FROM albums WHERE id = ?)", albumId)) {
            throw notFound("Không tìm thấy album.");
        }
        jdbcTemplate.update(
                "INSERT INTO library_albums (user_id, album_id) VALUES (?, ?) "
                        + "ON CONFLICT (user_id, album_id) DO NOTHING",
                userId,
                albumId);
    }

    @Transactional
    public void removeFavoriteAlbum(UUID userId, UUID albumId) {
        jdbcTemplate.update(
                "DELETE FROM library_albums WHERE user_id = ? AND album_id = ?",
                userId,
                albumId);
    }

    private boolean exists(String sql, UUID id) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(sql, Boolean.class, id));
    }

    private ApiException notFound(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, "not_found", message);
    }

    private static FavoriteTrackResponse mapTrack(ResultSet resultSet, int rowNumber)
            throws SQLException {
        OffsetDateTime addedAt = resultSet.getObject("added_at", OffsetDateTime.class);
        return new FavoriteTrackResponse(
                resultSet.getObject("id", UUID.class),
                resultSet.getString("title"),
                resultSet.getInt("duration_seconds"),
                resultSet.getObject("artist_id", UUID.class),
                resultSet.getString("artist_name"),
                resultSet.getObject("album_id", UUID.class),
                resultSet.getString("album_title"),
                toInstant(addedAt));
    }

    private static FavoriteAlbumResponse mapAlbum(ResultSet resultSet, int rowNumber)
            throws SQLException {
        OffsetDateTime addedAt = resultSet.getObject("added_at", OffsetDateTime.class);
        return new FavoriteAlbumResponse(
                resultSet.getObject("id", UUID.class),
                resultSet.getString("title"),
                resultSet.getObject("artist_id", UUID.class),
                resultSet.getString("artist_name"),
                resultSet.getObject("release_date", java.time.LocalDate.class),
                toInstant(addedAt));
    }

    private static Instant toInstant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }
}

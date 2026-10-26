package vn.edu.usth.online_music_player.backend.catalog;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.usth.online_music_player.backend.common.ApiException;
import vn.edu.usth.online_music_player.backend.common.CurrentUserId;
import vn.edu.usth.online_music_player.backend.recommendation.RecommendationService;

@RestController
public class CatalogController {
    private final JdbcTemplate db;
    private final RecommendationService recommendationService;
    private final CurrentUserId currentUserId;

    public CatalogController(JdbcTemplate db) {
        this(db, null, new CurrentUserId());
    }

    @Autowired
    public CatalogController(
            JdbcTemplate db,
            RecommendationService recommendationService,
            CurrentUserId currentUserId) {
        this.db = db;
        this.recommendationService = recommendationService;
        this.currentUserId = currentUserId;
    }

    public Map<String, Object> home() {
        return home(null);
    }

    @GetMapping("/home")
    public Map<String, Object> home(Principal principal) {
        var tracks = db.queryForList("""
            SELECT t.id, t.title,
                   t.duration_seconds AS "durationSeconds",
                   t.status = 'published' AS playable,
                   ar.id AS "artistId", ar.name AS "artistName",
                   al.id AS "albumId", al.title AS "albumTitle",
                   CASE
                       WHEN al.id IS NOT NULL AND al.cover_object_key IS NOT NULL AND btrim(al.cover_object_key) <> ''
                           THEN '/albums/' || al.id || '/cover'
                       ELSE NULL
                   END AS "coverUrl"
            FROM tracks t
            JOIN artists ar ON ar.id = t.primary_artist_id
            LEFT JOIN albums al ON al.id = t.album_id
            WHERE t.status <> 'archived'
            ORDER BY t.popularity_score DESC, t.title
            LIMIT 10
            """);

        var albums = db.queryForList("""
            SELECT al.id, al.title,
                   al.release_date::text AS "releaseDate",
                   ar.id AS "artistId", ar.name AS "artistName",
                   CASE
                       WHEN al.cover_object_key IS NOT NULL AND btrim(al.cover_object_key) <> ''
                           THEN '/albums/' || al.id || '/cover'
                       ELSE NULL
                   END AS "coverUrl"
            FROM albums al
            JOIN artists ar ON ar.id = al.primary_artist_id
            ORDER BY al.release_date DESC NULLS LAST, al.title
            LIMIT 10
            """);

        var artists = db.queryForList("""
            SELECT id, name, bio,
                   CASE
                       WHEN image_object_key IS NOT NULL AND btrim(image_object_key) <> ''
                           THEN '/artists/' || id || '/image'
                       ELSE NULL
                   END AS "coverUrl",
                   CASE
                       WHEN image_object_key IS NOT NULL AND btrim(image_object_key) <> ''
                           THEN '/artists/' || id || '/image'
                       ELSE NULL
                   END AS "imageUrl"
            FROM artists
            ORDER BY name
            LIMIT 10
            """);

        UUID userId = currentUserId == null ? null : currentUserId.optional(principal);
        List<?> recentlyPlayed = List.of();
        List<?> forYou = List.of();
        if (userId != null) {
            recentlyPlayed = db.queryForList("""
                SELECT recent.track_id AS "trackId",
                       t.title,
                       ar.name AS "artistName",
                       al.id AS "albumId",
                       al.title AS "albumTitle",
                       CASE
                           WHEN al.id IS NOT NULL AND al.cover_object_key IS NOT NULL AND btrim(al.cover_object_key) <> ''
                               THEN '/albums/' || al.id || '/cover'
                           ELSE NULL
                       END AS "coverUrl",
                       recent.played_at AS "playedAt"
                FROM (
                    SELECT h.track_id, MAX(h.started_at) AS played_at
                    FROM listening_history h
                    WHERE h.user_id = ? AND h.counted_as_play = true
                    GROUP BY h.track_id
                    ORDER BY MAX(h.started_at) DESC
                    LIMIT 6
                ) recent
                JOIN tracks t ON t.id = recent.track_id
                JOIN artists ar ON ar.id = t.primary_artist_id
                LEFT JOIN albums al ON al.id = t.album_id
                ORDER BY recent.played_at DESC
                """, userId);
            if (recommendationService != null) {
                forYou = recommendationService.recommend(userId, 5, null);
            }
        }

        return Map.of(
                "tracks", tracks,
                "albums", albums,
                "artists", artists,
                "recentlyPlayed", recentlyPlayed,
                "forYou", forYou
        );
    }

    @GetMapping("/search")
    public Map<String, Object> search(
            @RequestParam(name = "q", defaultValue = "") String q) {
        String term = q.trim();

        if (term.isBlank()) {
            return Map.of(
                    "tracks", List.of(),
                    "albums", List.of(),
                    "artists", List.of()
            );
        }

        String pattern = "%" + term + "%";

        var tracks = db.queryForList("""
            SELECT t.id, t.title,
                   t.duration_seconds AS "durationSeconds",
                   t.status = 'published' AS playable,
                   ar.id AS "artistId", ar.name AS "artistName",
                   al.id AS "albumId", al.title AS "albumTitle",
                   CASE
                       WHEN al.id IS NOT NULL AND al.cover_object_key IS NOT NULL AND btrim(al.cover_object_key) <> ''
                           THEN '/albums/' || al.id || '/cover'
                       ELSE NULL
                   END AS "coverUrl"
            FROM tracks t
            JOIN artists ar ON ar.id = t.primary_artist_id
            LEFT JOIN albums al ON al.id = t.album_id
            WHERE t.status <> 'archived'
              AND t.title ILIKE ?
            ORDER BY t.popularity_score DESC, t.title
            LIMIT 20
            """, pattern);

        var albums = db.queryForList("""
            SELECT al.id, al.title,
                   al.release_date::text AS "releaseDate",
                   ar.id AS "artistId", ar.name AS "artistName",
                   CASE
                       WHEN al.cover_object_key IS NOT NULL AND btrim(al.cover_object_key) <> ''
                           THEN '/albums/' || al.id || '/cover'
                       ELSE NULL
                   END AS "coverUrl"
            FROM albums al
            JOIN artists ar ON ar.id = al.primary_artist_id
            WHERE al.title ILIKE ?
            ORDER BY al.title
            LIMIT 20
            """, pattern);

        var artists = db.queryForList("""
            SELECT id, name, bio,
                   CASE
                       WHEN image_object_key IS NOT NULL AND btrim(image_object_key) <> ''
                           THEN '/artists/' || id || '/image'
                       ELSE NULL
                   END AS "coverUrl",
                   CASE
                       WHEN image_object_key IS NOT NULL AND btrim(image_object_key) <> ''
                           THEN '/artists/' || id || '/image'
                       ELSE NULL
                   END AS "imageUrl"
            FROM artists
            WHERE name ILIKE ?
            ORDER BY name
            LIMIT 20
            """, pattern);

        return Map.of(
                "tracks", tracks,
                "albums", albums,
                "artists", artists
        );
    }

    @GetMapping("/artists/{id}")
    public Map<String, Object> artist(@PathVariable("id") UUID id) {
        var artistRows = db.queryForList("""
            SELECT id, name, bio,
                   CASE
                       WHEN image_object_key IS NOT NULL AND btrim(image_object_key) <> ''
                           THEN '/artists/' || id || '/image'
                       ELSE NULL
                   END AS "coverUrl",
                   CASE
                       WHEN image_object_key IS NOT NULL AND btrim(image_object_key) <> ''
                           THEN '/artists/' || id || '/image'
                       ELSE NULL
                   END AS "imageUrl"
            FROM artists
            WHERE id = ?
            """, id);

        if (artistRows.isEmpty()) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND, "artist_not_found", "Artist not found");
        }

        var albums = db.queryForList("""
            SELECT id, title, release_date::text AS "releaseDate",
                   CASE
                       WHEN cover_object_key IS NOT NULL AND btrim(cover_object_key) <> ''
                           THEN '/albums/' || id || '/cover'
                       ELSE NULL
                   END AS "coverUrl"
            FROM albums
            WHERE primary_artist_id = ?
            ORDER BY release_date DESC NULLS LAST, title
            """, id);

        var tracks = db.queryForList("""
            SELECT t.id, t.title,
                   t.duration_seconds AS "durationSeconds",
                   t.status = 'published' AS playable,
                   ar.id AS "artistId", ar.name AS "artistName",
                   al.id AS "albumId", al.title AS "albumTitle",
                   CASE
                       WHEN al.id IS NOT NULL AND al.cover_object_key IS NOT NULL AND btrim(al.cover_object_key) <> ''
                           THEN '/albums/' || al.id || '/cover'
                       ELSE NULL
                   END AS "coverUrl"
            FROM tracks t
            JOIN artists ar ON ar.id = t.primary_artist_id
            LEFT JOIN albums al ON al.id = t.album_id
            WHERE t.primary_artist_id = ?
              AND t.status <> 'archived'
            ORDER BY t.popularity_score DESC, t.title
            """, id);

        return Map.of(
                "artist", artistRows.get(0),
                "albums", albums,
                "tracks", tracks
        );
    }

    @GetMapping("/albums/{id}")
    public Map<String, Object> album(@PathVariable("id") UUID id) {
        var albumRows = db.queryForList("""
            SELECT al.id, al.title,
                   al.release_date::text AS "releaseDate",
                   ar.id AS "artistId", ar.name AS "artistName",
                   CASE
                       WHEN al.cover_object_key IS NOT NULL AND btrim(al.cover_object_key) <> ''
                           THEN '/albums/' || al.id || '/cover'
                       ELSE NULL
                   END AS "coverUrl"
            FROM albums al
            JOIN artists ar ON ar.id = al.primary_artist_id
            WHERE al.id = ?
            """, id);

        if (albumRows.isEmpty()) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND, "album_not_found", "Album not found");
        }

        var tracks = db.queryForList("""
            SELECT t.id, t.title,
                   t.duration_seconds AS "durationSeconds",
                   t.status = 'published' AS playable,
                   ar.id AS "artistId", ar.name AS "artistName",
                   al.id AS "albumId", al.title AS "albumTitle",
                   CASE
                       WHEN al.id IS NOT NULL AND al.cover_object_key IS NOT NULL AND btrim(al.cover_object_key) <> ''
                           THEN '/albums/' || al.id || '/cover'
                       ELSE NULL
                   END AS "coverUrl"
            FROM tracks t
            JOIN artists ar ON ar.id = t.primary_artist_id
            LEFT JOIN albums al ON al.id = t.album_id
            WHERE t.album_id = ?
              AND t.status <> 'archived'
            ORDER BY t.title
            """, id);

        return Map.of(
                "album", albumRows.get(0),
                "tracks", tracks
        );
    }
}

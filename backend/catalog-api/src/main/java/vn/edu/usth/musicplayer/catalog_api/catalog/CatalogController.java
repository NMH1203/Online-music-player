package vn.edu.usth.musicplayer.catalog_api.catalog;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
public class CatalogController {
    private final JdbcTemplate db;

    public CatalogController(JdbcTemplate db) {
        this.db = db;
    }

    @GetMapping("/home")
    public Map<String, Object> home() {
        var tracks = db.queryForList("""
            SELECT t.id, t.title,
                   t.duration_seconds AS "durationSeconds",
                   t.status = 'published' AS playable,
                   ar.id AS "artistId", ar.name AS "artistName",
                   al.id AS "albumId", al.title AS "albumTitle"
            FROM tracks t
            JOIN artists ar ON ar.id = t.primary_artist_id
            LEFT JOIN albums al ON al.id = t.album_id
            WHERE t.status <> 'archived'
            ORDER BY t.popularity_score DESC, t.title
            LIMIT 10
            """);

        var albums = db.queryForList("""
            SELECT al.id, al.title,
                   al.release_date AS "releaseDate",
                   ar.id AS "artistId", ar.name AS "artistName"
            FROM albums al
            JOIN artists ar ON ar.id = al.primary_artist_id
            ORDER BY al.release_date DESC NULLS LAST, al.title
            LIMIT 10
            """);

        var artists = db.queryForList("""
            SELECT id, name, bio
            FROM artists
            ORDER BY name
            LIMIT 10
            """);

        return Map.of(
                "tracks", tracks,
                "albums", albums,
                "artists", artists
        );
    }

    @GetMapping("/search")
    public Map<String, Object> search(
            @RequestParam(defaultValue = "") String q) {
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
                   al.id AS "albumId", al.title AS "albumTitle"
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
                   al.release_date AS "releaseDate",
                   ar.id AS "artistId", ar.name AS "artistName"
            FROM albums al
            JOIN artists ar ON ar.id = al.primary_artist_id
            WHERE al.title ILIKE ?
            ORDER BY al.title
            LIMIT 20
            """, pattern);

        var artists = db.queryForList("""
            SELECT id, name, bio
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
    public Map<String, Object> artist(@PathVariable UUID id) {
        var artistRows = db.queryForList("""
            SELECT id, name, bio
            FROM artists
            WHERE id = ?
            """, id);

        if (artistRows.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Artist not found");
        }

        var albums = db.queryForList("""
            SELECT id, title, release_date AS "releaseDate"
            FROM albums
            WHERE primary_artist_id = ?
            ORDER BY release_date DESC NULLS LAST, title
            """, id);

        var tracks = db.queryForList("""
            SELECT id, title,
                   duration_seconds AS "durationSeconds",
                   status = 'published' AS playable
            FROM tracks
            WHERE primary_artist_id = ?
              AND status <> 'archived'
            ORDER BY popularity_score DESC, title
            """, id);

        return Map.of(
                "artist", artistRows.get(0),
                "albums", albums,
                "tracks", tracks
        );
    }

    @GetMapping("/albums/{id}")
    public Map<String, Object> album(@PathVariable UUID id) {
        var albumRows = db.queryForList("""
            SELECT al.id, al.title,
                   al.release_date AS "releaseDate",
                   ar.id AS "artistId", ar.name AS "artistName"
            FROM albums al
            JOIN artists ar ON ar.id = al.primary_artist_id
            WHERE al.id = ?
            """, id);

        if (albumRows.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Album not found");
        }

        var tracks = db.queryForList("""
            SELECT id, title,
                   duration_seconds AS "durationSeconds",
                   status = 'published' AS playable
            FROM tracks
            WHERE album_id = ?
              AND status <> 'archived'
            ORDER BY title
            """, id);

        return Map.of(
                "album", albumRows.get(0),
                "tracks", tracks
        );
    }
}
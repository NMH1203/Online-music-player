package vn.edu.usth.online_music_player.backend.catalog;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.edu.usth.online_music_player.backend.common.ApiException;

public class CatalogControllerTest {
    private static final UUID ARTIST_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID ALBUM_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");

    @Test
    public void blankSearchReturnsEmptyListsWithoutQueryingDatabase() {
        QueueJdbcTemplate db = new QueueJdbcTemplate();
        CatalogController controller = new CatalogController(db);

        Map<String, Object> response = controller.search("   ");

        assertEquals(List.of(), response.get("tracks"));
        assertEquals(List.of(), response.get("albums"));
        assertEquals(List.of(), response.get("artists"));
        assertEquals(0, db.queryCalls);
    }

    @Test
    public void homeReturnsTracksAlbumsAndArtists() {
        QueueJdbcTemplate db = new QueueJdbcTemplate();
        db.queuedResults.add(List.of(Map.of("title", "Track 1")));
        db.queuedResults.add(List.of(Map.of("title", "Album 1")));
        db.queuedResults.add(List.of(Map.of("name", "Artist 1")));
        CatalogController controller = new CatalogController(db);

        Map<String, Object> response = controller.home();

        assertEquals(1, ((List<?>) response.get("tracks")).size());
        assertEquals(1, ((List<?>) response.get("albums")).size());
        assertEquals(1, ((List<?>) response.get("artists")).size());
        assertEquals(3, db.queryCalls);
    }

    @Test
    public void artistDetailAndAlbumDetailReturn404WhenMissing() {
        QueueJdbcTemplate db = new QueueJdbcTemplate();
        db.queuedResults.add(List.of());
        db.queuedResults.add(List.of());
        CatalogController controller = new CatalogController(db);

        try {
            controller.artist(ARTIST_ID);
            fail("Expected ApiException for missing artist");
        } catch (ApiException e) {
            assertEquals(HttpStatus.NOT_FOUND, e.status());
            assertTrue(e.code().contains("not_found"));
        }

        try {
            controller.album(ALBUM_ID);
            fail("Expected ApiException for missing album");
        } catch (ApiException e) {
            assertEquals(HttpStatus.NOT_FOUND, e.status());
            assertTrue(e.code().contains("not_found"));
        }
    }

    private static final class QueueJdbcTemplate extends JdbcTemplate {
        final Deque<List<Map<String, Object>>> queuedResults = new ArrayDeque<>();
        int queryCalls = 0;

        @Override
        public List<Map<String, Object>> queryForList(String sql) {
            queryCalls++;
            return queuedResults.isEmpty() ? List.of() : queuedResults.removeFirst();
        }

        @Override
        public List<Map<String, Object>> queryForList(String sql, Object... args) {
            queryCalls++;
            return queuedResults.isEmpty() ? List.of() : queuedResults.removeFirst();
        }
    }
}

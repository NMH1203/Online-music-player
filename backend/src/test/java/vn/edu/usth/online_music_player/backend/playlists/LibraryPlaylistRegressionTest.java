package vn.edu.usth.online_music_player.backend.playlists;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import org.junit.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import vn.edu.usth.online_music_player.backend.common.ApiException;
import vn.edu.usth.online_music_player.backend.library.LibraryService;

/** Regression checks for private-library behavior without connecting to a developer database. */
public class LibraryPlaylistRegressionTest {
    private static final UUID OWNER_ID = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
    private static final UUID OTHER_USER_ID = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb");
    private static final UUID PLAYLIST_ID = UUID.fromString("cccccccc-cccc-4ccc-8ccc-cccccccccccc");
    private static final UUID TRACK_ID = UUID.fromString("dddddddd-dddd-4ddd-8ddd-dddddddddddd");

    @Test
    public void freeQuotaStopsAtThreeAndPremiumQuotaIsUnlimited() {
        StubJdbcTemplate freeDatabase = new StubJdbcTemplate();
        freeDatabase.maxPlaylists = 3;
        freeDatabase.playlistCount = 3;
        PlaylistQuotaResponse free = new PlaylistService(freeDatabase).getPlaylistQuota(OWNER_ID);

        assertEquals(3L, free.playlistCount());
        assertEquals(Integer.valueOf(3), free.maxPlaylists());
        assertFalse(free.canCreatePlaylist());

        StubJdbcTemplate premiumDatabase = new StubJdbcTemplate();
        premiumDatabase.maxPlaylists = null;
        premiumDatabase.playlistCount = 25;
        PlaylistQuotaResponse premium =
                new PlaylistService(premiumDatabase).getPlaylistQuota(OWNER_ID);

        assertEquals(25L, premium.playlistCount());
        assertNull(premium.maxPlaylists());
        assertTrue(premium.canCreatePlaylist());
    }

    @Test
    public void freePlaylistCreationAtQuotaReturnsConflictWithoutWriting() {
        StubJdbcTemplate database = new StubJdbcTemplate();
        database.maxPlaylists = 3;
        database.playlistCount = 3;

        ApiException failure = expectApiException(() -> new PlaylistService(database)
                .createPlaylist(OWNER_ID, new CreatePlaylistRequest("Another", null)));

        assertEquals(HttpStatus.CONFLICT, failure.status());
        assertEquals("playlist_limit_reached", failure.code());
        assertEquals(3, failure.details().get("maxPlaylists"));
        assertFalse(database.updates.stream().anyMatch(sql -> sql.startsWith("INSERT INTO playlists")));
    }

    @Test
    public void anotherUsersPlaylistIsNotReadableOrMutable() {
        StubJdbcTemplate database = new StubJdbcTemplate();
        database.playlistOwnedByRequestingUser = false;
        PlaylistService playlists = new PlaylistService(database);

        assertNotFound(() -> playlists.getPlaylist(OTHER_USER_ID, PLAYLIST_ID));
        assertNotFound(() -> playlists.updatePlaylist(
                OTHER_USER_ID, PLAYLIST_ID, new UpdatePlaylistRequest("Changed", null)));
        assertNotFound(() -> playlists.deletePlaylist(OTHER_USER_ID, PLAYLIST_ID));
        assertNotFound(() -> playlists.addTrack(OTHER_USER_ID, PLAYLIST_ID, TRACK_ID));
        assertNotFound(() -> playlists.removeTrack(OTHER_USER_ID, PLAYLIST_ID, TRACK_ID));

        assertTrue("Unauthorized requests must stop before issuing writes", database.updates.isEmpty());
        assertTrue(database.queries.stream().anyMatch(call ->
                call.sql.contains("WHERE p.user_id = ? AND p.id = ?")
                        && call.arguments.equals(List.of(OTHER_USER_ID, PLAYLIST_ID))));
        assertTrue(database.queries.stream().anyMatch(call ->
                call.sql.contains("WHERE user_id = ? AND id = ? FOR UPDATE")
                        && call.arguments.equals(List.of(OTHER_USER_ID, PLAYLIST_ID))));
    }

    @Test
    public void duplicatePlaylistTrackAddAndRepeatedRemovalAreStable() {
        StubJdbcTemplate database = new StubJdbcTemplate();
        database.playlistOwnedByRequestingUser = true;
        database.trackExists = true;
        database.writeResults.add(1); // First insert succeeds.
        database.writeResults.add(0); // The unique key rejects the duplicate.
        database.writeResults.add(1); // First remove deletes one row.
        database.writeResults.add(0); // Repeating the remove is a no-op.
        PlaylistService playlists = new PlaylistService(database);

        playlists.addTrack(OWNER_ID, PLAYLIST_ID, TRACK_ID);
        playlists.addTrack(OWNER_ID, PLAYLIST_ID, TRACK_ID);
        playlists.removeTrack(OWNER_ID, PLAYLIST_ID, TRACK_ID);
        playlists.removeTrack(OWNER_ID, PLAYLIST_ID, TRACK_ID);

        assertEquals(2, database.updates.stream()
                .filter(sql -> sql.startsWith("INSERT INTO playlist_tracks"))
                .count());
        assertEquals(2, database.updates.stream()
                .filter(sql -> sql.startsWith("DELETE FROM playlist_tracks"))
                .count());
        assertEquals("Only a real insert and a real delete should update playlist timestamps", 2,
                database.updates.stream()
                        .filter(sql -> sql.startsWith("UPDATE playlists SET updated_at"))
                        .count());
        assertTrue(database.updates.stream().anyMatch(sql ->
                sql.contains("ON CONFLICT (playlist_id, track_id) DO NOTHING")));
    }

    @Test
    public void repeatedFavoriteAddAndRemoveStayScopedToTheCurrentUser() {
        StubJdbcTemplate database = new StubJdbcTemplate();
        database.trackExists = true;
        LibraryService library = new LibraryService(database);

        library.addFavoriteTrack(OWNER_ID, TRACK_ID);
        library.addFavoriteTrack(OWNER_ID, TRACK_ID);
        library.removeFavoriteTrack(OWNER_ID, TRACK_ID);
        library.removeFavoriteTrack(OWNER_ID, TRACK_ID);

        assertEquals(2, database.updates.stream()
                .filter(sql -> sql.startsWith("INSERT INTO library_tracks"))
                .count());
        assertEquals(2, database.updates.stream()
                .filter(sql -> sql.startsWith("DELETE FROM library_tracks"))
                .count());
        assertTrue(database.updates.stream().anyMatch(sql ->
                sql.contains("ON CONFLICT (user_id, track_id) DO NOTHING")));
        assertEquals(4, database.writeArguments.size());
        assertTrue(database.writeArguments.stream().allMatch(arguments ->
                arguments.equals(List.of(OWNER_ID, TRACK_ID))));
        assertTrue(database.updates.stream().anyMatch(sql ->
                sql.startsWith("DELETE FROM library_tracks WHERE user_id = ? AND track_id = ?")));
    }

    private static void assertNotFound(Runnable request) {
        ApiException failure = expectApiException(request);
        assertEquals(HttpStatus.NOT_FOUND, failure.status());
        assertEquals("not_found", failure.code());
    }

    private static ApiException expectApiException(Runnable request) {
        try {
            request.run();
            fail("Expected an API exception");
            throw new AssertionError("unreachable");
        } catch (ApiException expected) {
            return expected;
        }
    }

    private static final class StubJdbcTemplate extends JdbcTemplate {
        private final List<QueryCall> queries = new ArrayList<>();
        private final List<String> updates = new ArrayList<>();
        private final List<List<Object>> writeArguments = new ArrayList<>();
        private final Deque<Integer> writeResults = new ArrayDeque<>();
        private Integer maxPlaylists = 3;
        private long playlistCount;
        private boolean playlistOwnedByRequestingUser;
        private boolean trackExists;

        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            List<Object> arguments = List.of(args);
            queries.add(new QueryCall(sql, arguments));
            try {
                if (sql.contains("FROM users") && sql.contains("FOR UPDATE")) {
                    return List.of(rowMapper.mapRow(resultSet("id", OWNER_ID), 0));
                }
                if (sql.contains("FROM subscriptions s")) {
                    return List.of(rowMapper.mapRow(resultSet("max_playlists", maxPlaylists), 0));
                }
                if (sql.contains("FROM playlists p") && sql.contains("p.user_id = ? AND p.id = ?")) {
                    if (!playlistOwnedByRequestingUser) {
                        return List.of();
                    }
                    return List.of(rowMapper.mapRow(resultSet("id", PLAYLIST_ID), 0));
                }
                if (sql.contains("FROM playlists") && sql.contains("FOR UPDATE")) {
                    if (!playlistOwnedByRequestingUser) {
                        return List.of();
                    }
                    return List.of(rowMapper.mapRow(resultSet("id", PLAYLIST_ID), 0));
                }
                throw new AssertionError("Unexpected query: " + sql);
            } catch (SQLException exception) {
                throw new AssertionError(exception);
            }
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            if (sql.contains("COUNT(*) FROM playlists")) {
                return (T) Long.valueOf(playlistCount);
            }
            if (sql.contains("EXISTS (SELECT 1 FROM tracks")) {
                return (T) Boolean.valueOf(trackExists);
            }
            if (sql.contains("COALESCE(MAX(position)")) {
                return (T) Integer.valueOf(0);
            }
            throw new AssertionError("Unexpected queryForObject: " + sql);
        }

        @Override
        public int update(String sql, Object... args) {
            updates.add(sql);
            writeArguments.add(List.of(args));
            if (sql.startsWith("UPDATE playlists SET updated_at")) {
                return 1;
            }
            return writeResults.isEmpty() ? 1 : writeResults.removeFirst();
        }

        private static ResultSet resultSet(String column, Object value) {
            InvocationHandler handler = (proxy, method, arguments) -> {
                if (method.getName().equals("getObject") && arguments != null
                        && arguments.length >= 1 && column.equals(arguments[0])) {
                    return value;
                }
                if (method.getReturnType().equals(boolean.class)) {
                    return false;
                }
                if (method.getReturnType().equals(int.class)) {
                    return 0;
                }
                if (method.getReturnType().equals(long.class)) {
                    return 0L;
                }
                return null;
            };
            return (ResultSet) Proxy.newProxyInstance(
                    ResultSet.class.getClassLoader(), new Class<?>[]{ResultSet.class}, handler);
        }
    }

    private record QueryCall(String sql, List<Object> arguments) {}
}

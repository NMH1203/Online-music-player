package vn.edu.usth.online_music_player.backend.history;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MinioClient;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import okhttp3.Headers;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import vn.edu.usth.online_music_player.backend.auth.AuthController;
import vn.edu.usth.online_music_player.backend.auth.AuthService;
import vn.edu.usth.online_music_player.backend.auth.BearerTokenFilter;
import vn.edu.usth.online_music_player.backend.auth.TokenService;
import vn.edu.usth.online_music_player.backend.catalog.CatalogController;
import vn.edu.usth.online_music_player.backend.catalog.CatalogMediaController;
import vn.edu.usth.online_music_player.backend.common.ApiExceptionHandler;
import vn.edu.usth.online_music_player.backend.common.CurrentUserId;
import vn.edu.usth.online_music_player.backend.library.LibraryController;
import vn.edu.usth.online_music_player.backend.library.LibraryService;
import vn.edu.usth.online_music_player.backend.playback.PlaybackController;
import vn.edu.usth.online_music_player.backend.playback.PlaybackService;
import vn.edu.usth.online_music_player.backend.playback.StorageProperties;
import vn.edu.usth.online_music_player.backend.playback.TrackPlaybackResponse;
import vn.edu.usth.online_music_player.backend.playlists.CreatePlaylistRequest;
import vn.edu.usth.online_music_player.backend.playlists.PlaylistService;
import vn.edu.usth.online_music_player.backend.profile.ProfileController;
import vn.edu.usth.online_music_player.backend.recommendation.RecommendationController;
import vn.edu.usth.online_music_player.backend.recommendation.RecommendationService;
import vn.edu.usth.online_music_player.backend.subscriptions.SubscriptionService;

/** End-to-end integration tests for B1-B7 against real PostgreSQL 16 and all Flyway migrations. */
public class MvpBackendIntegrationTest {
    private static EmbeddedPostgres postgres;
    private static AnnotationConfigApplicationContext context;
    private static DataSource source;
    private static JdbcTemplate db;

    private AuthService auth;
    private ListeningEventService listeningEvents;
    private PlaybackService playbackService;
    private RecommendationService recommendationService;
    private MockMvc http;
    private StubMinioClient storage;

    private UUID artist1;
    private UUID artist2;
    private UUID albumWithCover;
    private UUID albumWithoutCover;
    private UUID genreJazz;
    private UUID genreRock;
    private UUID shortTrack; // 40s
    private UUID longTrack;  // 120s

    @EnableTransactionManagement
    static class Config {
        @Bean DataSource dataSource() { return source; }
        @Bean JdbcTemplate jdbcTemplate(DataSource ds) { return new JdbcTemplate(ds); }
        @Bean PlatformTransactionManager transactionManager(DataSource ds) { return new DataSourceTransactionManager(ds); }
        @Bean TokenService tokenService() { return new TokenService("test-only-32-byte-secret-never-use-in-production"); }
        @Bean AuthService authService(JdbcTemplate jdbc, TokenService tokens) { return new AuthService(jdbc, tokens); }
        @Bean SubscriptionService subscriptions(JdbcTemplate jdbc) { return new SubscriptionService(jdbc, true); }
        @Bean PlaylistService playlists(JdbcTemplate jdbc) { return new PlaylistService(jdbc); }
        @Bean LibraryService libraryService(JdbcTemplate jdbc) { return new LibraryService(jdbc); }
        @Bean ListeningEventService listeningEventService(JdbcTemplate jdbc) { return new ListeningEventService(jdbc); }
        @Bean PlaybackService playbackService(JdbcTemplate jdbc) { return new PlaybackService(jdbc); }
        @Bean RecommendationService recommendationService(JdbcTemplate jdbc) { return new RecommendationService(jdbc); }
    }

    @BeforeClass
    public static void start() throws Exception {
        postgres = EmbeddedPostgres.builder().setPort(0).start();
        source = postgres.getPostgresDatabase();
        db = new JdbcTemplate(source);
        try (var files = Files.list(Path.of("../database/migrations"))) {
            for (Path path : files.filter(p -> p.toString().endsWith(".sql")).sorted().toList()) {
                try (var connection = source.getConnection();
                        var statement = connection.createStatement()) {
                    statement.execute(Files.readString(path));
                }
            }
        }
        context = new AnnotationConfigApplicationContext(Config.class);
    }

    @AfterClass
    public static void stop() throws Exception {
        if (context != null) context.close();
        if (postgres != null) postgres.close();
    }

    @Before
    public void setup() {
        db.execute("TRUNCATE users, artists, albums, tracks, playlists, listening_history CASCADE");
        auth = context.getBean(AuthService.class);
        listeningEvents = context.getBean(ListeningEventService.class);
        playbackService = context.getBean(PlaybackService.class);
        recommendationService = context.getBean(RecommendationService.class);
        SubscriptionService subscriptions = context.getBean(SubscriptionService.class);
        LibraryService libraryService = context.getBean(LibraryService.class);
        CurrentUserId currentUserId = new CurrentUserId();

        StorageProperties storageProperties = new StorageProperties();
        storageProperties.setBucket("music-demo");
        storageProperties.setEndpoint("http://127.0.0.1:9000");
        storageProperties.setRegion("us-east-1");
        storageProperties.setAccessKey("test");
        storageProperties.setSecretKey("test");

        MinioClient stubMinio = storage = new StubMinioClient();

        http = MockMvcBuilders.standaloneSetup(
                        new AuthController(auth),
                        new ListeningEventController(listeningEvents, currentUserId),
                        new PlaybackController(playbackService, currentUserId, stubMinio),
                        new RecommendationController(recommendationService, currentUserId),
                        new CatalogController(db, recommendationService, currentUserId),
                        new CatalogMediaController(db, stubMinio, storageProperties),
                        new ProfileController(db, currentUserId, subscriptions),
                        new LibraryController(libraryService, currentUserId))
                .setControllerAdvice(new ApiExceptionHandler())
                .addFilters(new BearerTokenFilter(context.getBean(TokenService.class), auth))
                .build();

        seedTestCatalog();
    }

    private void seedTestCatalog() {
        artist1 = UUID.randomUUID();
        artist2 = UUID.randomUUID();
        albumWithCover = UUID.randomUUID();
        albumWithoutCover = UUID.randomUUID();

        db.update(
                "INSERT INTO artists(id, name, slug, bio, image_object_key) VALUES (?, 'Artist One', 'artist-one', 'Bio 1', 'demo/images/artist1.png')",
                artist1);
        db.update(
                "INSERT INTO artists(id, name, slug, bio, image_object_key) VALUES (?, 'Artist Two', 'artist-two', 'Bio 2', NULL)",
                artist2);

        db.update(
                "INSERT INTO albums(id, primary_artist_id, title, slug, release_date, cover_object_key) VALUES (?, ?, 'Album Covered', 'album-covered', '2026-10-01', 'demo/covers/album1.jpg')",
                albumWithCover,
                artist1);
        db.update(
                "INSERT INTO albums(id, primary_artist_id, title, slug, release_date, cover_object_key) VALUES (?, ?, 'Album Plain', 'album-plain', '2026-09-01', NULL)",
                albumWithoutCover,
                artist2);

        genreJazz = db.queryForObject("SELECT id FROM genres WHERE code = 'jazz'", UUID.class);
        genreRock = db.queryForObject("SELECT id FROM genres WHERE code = 'rock'", UUID.class);

        shortTrack = insertPublishedTrack("Short Jazz 40s", artist1, albumWithCover, 40, 10.0, genreJazz);
        longTrack = insertPublishedTrack("Long Rock 120s", artist2, albumWithoutCover, 120, 20.0, genreRock);
    }

    private UUID insertPublishedTrack(
            String title, UUID artistId, UUID albumId, int duration, double pop, UUID genreId) {
        UUID id = UUID.randomUUID();
        String key = "demo/audio/" + id + ".mp3";
        db.update(
                """
                INSERT INTO tracks(id, primary_artist_id, album_id, title, duration_seconds,
                                   audio_object_key, file_size_bytes, mime_type, bitrate_kbps,
                                   status, popularity_score, published_at)
                VALUES (?, ?, ?, ?, ?, ?, 102400, 'audio/mpeg', 128, 'draft', ?, now())
                """,
                id,
                artistId,
                albumId,
                title,
                duration,
                key,
                pop);
        db.update(
                """
                INSERT INTO track_audio_metadata(track_id, bucket_name, sha256, etag,
                                                 license_code, license_url, source_url,
                                                 attribution_text, verified_at)
                VALUES (?, 'music-demo', ?, 'etag-1', 'CC-BY-4.0',
                        'https://creativecommons.org/licenses/by/4.0/',
                        'https://incompetech.com/music/royalty-free/mp3-royaltyfree/test.mp3',
                        'Test Attribution', now())
                """,
                id,
                "a".repeat(64));
        db.update("UPDATE tracks SET status = 'published', published_at = now() WHERE id = ?", id);
        if (genreId != null) {
            db.update("INSERT INTO track_genres(track_id, genre_id) VALUES (?, ?)", id, genreId);
        }
        return id;
    }

    private AuthService.Session registerUser(String name) {
        return auth.register(name + "@example.test", "Password123!", name);
    }

    @Test
    public void b1ListeningEventsThresholdsIdempotencyAndConflict() throws Exception {
        var user1 = registerUser("listener1");
        var user2 = registerUser("listener2");
        String bearer1 = "Bearer " + user1.accessToken();
        String bearer2 = "Bearer " + user2.accessToken();

        // Short track is 40s -> threshold = min(30, ceil(20)) = 20s.
        UUID eventBelowShort = UUID.randomUUID();
        var resBelow = http.perform(post("/listening-events")
                        .header("Authorization", bearer1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "clientEventId": "%s",
                                  "trackId": "%s",
                                  "sourceContext": "home",
                                  "startedAt": "2026-10-26T14:00:00Z",
                                  "endedAt": "2026-10-26T14:00:19Z",
                                  "listenedSeconds": 19
                                }
                                """.formatted(eventBelowShort, shortTrack)))
                .andReturn().getResponse();
        assertEquals(200, resBelow.getStatus());
        assertTrue(resBelow.getContentAsString().contains("\"countedAsPlay\":false"));

        // 20s on 40s track -> countedAsPlay = true and increments popularity_score by 1 (10 -> 11).
        UUID eventCountedShort = UUID.randomUUID();
        String bodyCounted = """
                {
                  "clientEventId": "%s",
                  "trackId": "%s",
                  "sourceContext": "home",
                  "startedAt": "2026-10-26T14:01:00Z",
                  "endedAt": "2026-10-26T14:01:20Z",
                  "listenedSeconds": 20
                }
                """.formatted(eventCountedShort, shortTrack);
        var resCounted = http.perform(post("/listening-events")
                        .header("Authorization", bearer1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyCounted))
                .andReturn().getResponse();
        assertEquals(200, resCounted.getStatus());
        assertTrue(resCounted.getContentAsString().contains("\"countedAsPlay\":true"));

        // Sending the exact same clientEventId again from user1 is idempotent and does not double-increment popularity.
        var resDuplicate = http.perform(post("/listening-events")
                        .header("Authorization", bearer1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyCounted))
                .andReturn().getResponse();
        assertEquals(200, resDuplicate.getStatus());
        assertTrue(resDuplicate.getContentAsString().contains("\"countedAsPlay\":true"));

        Double popAfter = db.queryForObject(
                "SELECT popularity_score FROM tracks WHERE id = ?", Double.class, shortTrack);
        assertEquals(Double.valueOf(11.0), popAfter);
        Long historyRows = db.queryForObject(
                "SELECT count(*) FROM listening_history WHERE client_event_id = ?",
                Long.class,
                eventCountedShort);
        assertEquals(Long.valueOf(1L), historyRows);

        // Another user reusing the same clientEventId -> 409 Conflict.
        var resOtherUser = http.perform(post("/listening-events")
                        .header("Authorization", bearer2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyCounted))
                .andReturn().getResponse();
        assertEquals(409, resOtherUser.getStatus());

        // Long track is 120s -> threshold = min(30, 60) = 30s. 29s is false, 30s is true.
        var res29 = http.perform(post("/listening-events")
                        .header("Authorization", bearer1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "clientEventId": "%s",
                                  "trackId": "%s",
                                  "sourceContext": "search",
                                  "startedAt": "2026-10-26T14:02:00Z",
                                  "endedAt": "2026-10-26T14:02:29Z",
                                  "listenedSeconds": 29
                                }
                                """.formatted(UUID.randomUUID(), longTrack)))
                .andReturn().getResponse();
        assertEquals(200, res29.getStatus());
        assertTrue(res29.getContentAsString().contains("\"countedAsPlay\":false"));

        var res30 = http.perform(post("/listening-events")
                        .header("Authorization", bearer1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "clientEventId": "%s",
                                  "trackId": "%s",
                                  "sourceContext": "search",
                                  "startedAt": "2026-10-26T14:03:00Z",
                                  "endedAt": "2026-10-26T14:03:30Z",
                                  "listenedSeconds": 30
                                }
                                """.formatted(UUID.randomUUID(), longTrack)))
                .andReturn().getResponse();
        assertEquals(200, res30.getStatus());
        assertTrue(res30.getContentAsString().contains("\"countedAsPlay\":true"));
    }

    @Test
    public void b2PlaybackAndB7CoverAndArtistImageEndpoints() throws Exception {
        var user = registerUser("playbackuser");
        String bearer = "Bearer " + user.accessToken();

        var playbackCovered = http.perform(get("/tracks/" + shortTrack + "/playback")
                        .header("Authorization", bearer))
                .andReturn().getResponse();
        assertEquals(200, playbackCovered.getStatus());
        String coveredBody = playbackCovered.getContentAsString();
        assertTrue(coveredBody.contains("\"artistId\":\"" + artist1 + "\""));
        assertTrue(coveredBody.contains("\"albumId\":\"" + albumWithCover + "\""));
        assertTrue(coveredBody.contains("\"albumTitle\":\"Album Covered\""));
        assertTrue(coveredBody.contains("\"coverUrl\":\"/albums/" + albumWithCover + "/cover\""));

        var playbackPlain = http.perform(get("/tracks/" + longTrack + "/playback")
                        .header("Authorization", bearer))
                .andReturn().getResponse();
        assertEquals(200, playbackPlain.getStatus());
        String plainBody = playbackPlain.getContentAsString();
        assertTrue(plainBody.contains("\"artistId\":\"" + artist2 + "\""));
        assertTrue(plainBody.contains("\"albumId\":\"" + albumWithoutCover + "\""));
        assertTrue(plainBody.contains("\"coverUrl\":null"));

        // B7: /albums/{id}/cover & /artists/{id}/image
        var coverOk = http.perform(get("/albums/" + albumWithCover + "/cover"))
                .andReturn().getResponse();
        assertEquals(200, coverOk.getStatus());
        assertEquals("image/jpeg", coverOk.getContentType());
        assertEquals("max-age=86400", coverOk.getHeader("Cache-Control"));

        var coverMissing = http.perform(get("/albums/" + albumWithoutCover + "/cover"))
                .andReturn().getResponse();
        assertEquals(404, coverMissing.getStatus());

        var artistImgOk = http.perform(get("/artists/" + artist1 + "/image"))
                .andReturn().getResponse();
        assertEquals(200, artistImgOk.getStatus());
        assertEquals("image/png", artistImgOk.getContentType());
        assertEquals("max-age=86400", artistImgOk.getHeader("Cache-Control"));

        var artistImgMissing = http.perform(get("/artists/" + artist2 + "/image"))
                .andReturn().getResponse();
        assertEquals(404, artistImgMissing.getStatus());
    }

    @Test
    public void b3RecommendationsB4HomeB5StatsAndB6History() throws Exception {
        var user = registerUser("mvpuser");
        String bearer = "Bearer " + user.accessToken();

        // Add more candidate tracks so recommendations remain populated after excluding played tracks
        UUID recJazz1 = insertPublishedTrack("Jazz Gem 1", artist1, albumWithCover, 150, 90.0, genreJazz);
        UUID recJazz2 = insertPublishedTrack("Jazz Gem 2", artist1, albumWithCover, 160, 80.0, genreJazz);
        UUID recRock1 = insertPublishedTrack("Rock Anthem", artist2, albumWithoutCover, 170, 50.0, genreRock);

        // Anonymous Home stays public; an invalid supplied token must trigger refresh.
        var homeAnon = http.perform(get("/home")).andReturn().getResponse();
        assertEquals(200, homeAnon.getStatus());
        assertTrue(homeAnon.getContentAsString().contains("\"recentlyPlayed\":[]"));
        assertTrue(homeAnon.getContentAsString().contains("\"forYou\":[]"));

        var homeBadBearer = http.perform(get("/home").header("Authorization", "Bearer invalid-token"))
                .andReturn().getResponse();
        assertEquals(401, homeBadBearer.getStatus());
        assertTrue(homeBadBearer.getContentAsString().contains("invalid_session"));

        // Cold start recommendations (< 5 valid plays)
        var coldRecs = http.perform(get("/recommendations?limit=5&currentTrackId=" + shortTrack)
                        .header("Authorization", bearer))
                .andReturn().getResponse();
        assertEquals(200, coldRecs.getStatus());
        String coldRecsJson = coldRecs.getContentAsString();
        assertFalse("currentTrackId must be excluded", coldRecsJson.contains(shortTrack.toString()));
        assertTrue(coldRecsJson.contains(recJazz1.toString()));

        // Record 1 valid play (60s) and 1 short non-counted play (15s) -> total listened = 75s = 1 minute
        listeningEvents.recordEvent(user.userId(), new ListeningEventRequest(
                UUID.randomUUID(),
                shortTrack,
                "home",
                null,
                java.time.Instant.parse("2026-10-26T10:00:00Z"),
                java.time.Instant.parse("2026-10-26T10:01:00Z"),
                60));
        listeningEvents.recordEvent(user.userId(), new ListeningEventRequest(
                UUID.randomUUID(),
                longTrack,
                "home",
                null,
                java.time.Instant.parse("2026-10-26T10:02:00Z"),
                java.time.Instant.parse("2026-10-26T10:02:15Z"),
                15));

        // Like 1 track, 1 album, and create 1 playlist
        http.perform(put("/me/library/tracks/" + shortTrack).header("Authorization", bearer));
        http.perform(put("/me/library/albums/" + albumWithCover).header("Authorization", bearer));
        context.getBean(PlaylistService.class)
                .createPlaylist(user.userId(), new CreatePlaylistRequest("My Favorites", null));

        // B5: GET /me/stats
        var statsRes = http.perform(get("/me/stats").header("Authorization", bearer))
                .andReturn().getResponse();
        assertEquals(200, statsRes.getStatus());
        String statsJson = statsRes.getContentAsString();
        assertTrue(statsJson.contains("\"validPlays\":1"));
        assertTrue(statsJson.contains("\"minutesListened\":1"));
        assertTrue(statsJson.contains("\"likedTracks\":1"));
        assertTrue(statsJson.contains("\"likedAlbums\":1"));
        assertTrue(statsJson.contains("\"playlists\":1"));

        // B6: GET /me/history includes album_id, album_title, artist_id, duration_seconds, cover_url
        var historyRes = http.perform(get("/me/history").header("Authorization", bearer))
                .andReturn().getResponse();
        assertEquals(200, historyRes.getStatus());
        String historyJson = historyRes.getContentAsString();
        assertTrue(historyJson.contains("\"album_id\":\"" + albumWithCover + "\""));
        assertTrue(historyJson.contains("\"album_title\":\"Album Covered\""));
        assertTrue(historyJson.contains("\"artist_id\":\"" + artist1 + "\""));
        assertTrue(historyJson.contains("\"duration_seconds\":40"));
        assertTrue(historyJson.contains("\"cover_url\":\"/albums/" + albumWithCover + "/cover\""));

        // B4: Authenticated GET /home includes recentlyPlayed and forYou
        var homeAuth = http.perform(get("/home").header("Authorization", bearer))
                .andReturn().getResponse();
        assertEquals(200, homeAuth.getStatus());
        String homeAuthJson = homeAuth.getContentAsString();
        assertTrue(homeAuthJson.contains("\"recentlyPlayed\":[{\""));
        assertTrue(homeAuthJson.contains("\"trackId\":\"" + shortTrack + "\""));
        assertTrue(homeAuthJson.contains("\"forYou\":[{\""));
    }


    @Test public void streamMapsStorageErrorsBeforeSuccessHeadersAndClosesStream() throws Exception {
        var user = registerUser("stream-check");
        String bearer = "Bearer " + user.accessToken();
        storage.failure = "missing";
        var missing = http.perform(get("/tracks/" + shortTrack + "/stream").header("Authorization", bearer)).andReturn().getResponse();
        assertEquals(404, missing.getStatus());
        assertTrue(missing.getContentAsString().contains("audio_not_found"));
        storage.failure = "offline";
        assertEquals(503, http.perform(get("/tracks/" + shortTrack + "/stream").header("Authorization", bearer)).andReturn().getResponse().getStatus());
        storage.failure = null;
        storage.size = 4;
        var partial = http.perform(get("/tracks/" + shortTrack + "/stream").header("Authorization", bearer).header("Range", "bytes=0-3")).andReturn().getResponse();
        assertEquals(206, partial.getStatus());
        assertEquals("bytes 0-3/102400", partial.getHeader("Content-Range"));
        assertEquals(4, partial.getContentAsByteArray().length);
        assertTrue(storage.closed);
        storage.size = 102400;
        var full = http.perform(get("/tracks/" + shortTrack + "/stream").header("Authorization", bearer)).andReturn().getResponse();
        assertEquals(200, full.getStatus());
        assertEquals(102400, full.getContentAsByteArray().length);
        assertEquals(416, http.perform(get("/tracks/" + shortTrack + "/stream").header("Authorization", bearer).header("Range", "bytes=200000-")).andReturn().getResponse().getStatus());
    }

    @Test public void searchTreatsWildcardCharactersLiterallyAndRejectsLongQueries() throws Exception {
        db.update("UPDATE tracks SET title = ? WHERE id = ?", "100%_\\mix", shortTrack);
        var catalog = new CatalogController(db);
        assertEquals(1, ((List<?>) catalog.search("%").get("tracks")).size());
        assertEquals(1, ((List<?>) catalog.search("_").get("tracks")).size());
        assertEquals(1, ((List<?>) catalog.search("\\").get("tracks")).size());
        assertEquals(0, ((List<?>) catalog.search("%_").get("albums")).size());
        var trackCatalog = new vn.edu.usth.online_music_player.backend.catalog.TrackCatalogService(db);
        assertEquals(1, trackCatalog.listPublishedTracks("%").size());
        assertEquals(1, trackCatalog.listPublishedTracks("_").size());
        assertEquals(1, trackCatalog.listPublishedTracks("\\").size());
        assertEquals(400, http.perform(get("/search").param("q", "x".repeat(101))).andReturn().getResponse().getStatus());
    }

    @Test public void unpublishedTracksCannotBeLikedOrShownInFavorites() throws Exception {
        var user = registerUser("like-check");
        String bearer = "Bearer " + user.accessToken();
        assertEquals(204, http.perform(put("/me/library/tracks/" + shortTrack).header("Authorization", bearer)).andReturn().getResponse().getStatus());
        db.update("UPDATE tracks SET status = 'draft' WHERE id = ?", shortTrack);
        assertEquals(404, http.perform(put("/me/library/tracks/" + shortTrack).header("Authorization", bearer)).andReturn().getResponse().getStatus());
        assertTrue(context.getBean(LibraryService.class).listFavoriteTracks(user.userId()).isEmpty());
    }

    @Test public void webpAndLargeImageResponsesAreCorrect() throws Exception {
        db.update("UPDATE albums SET cover_object_key = 'cover.webp' WHERE id = ?", albumWithCover);
        var image = http.perform(get("/albums/" + albumWithCover + "/cover")).andReturn().getResponse();
        assertEquals("image/webp", image.getContentType());
        assertTrue(storage.closed);
        storage.size = 5 * 1024 * 1024 + 100;
        assertEquals(413, http.perform(get("/albums/" + albumWithCover + "/cover")).andReturn().getResponse().getStatus());
        assertTrue(storage.closed);
    }

    @Test public void listeningRejectsFutureAndInflatedTimeAndDoesNotDoubleCountOverlaps() {
        var user = registerUser("listen-check");
        var now = java.time.Instant.now();
        var first = new ListeningEventRequest(UUID.randomUUID(), longTrack, "album", null, now.minusSeconds(60), now, 30);
        assertTrue(listeningEvents.recordEvent(user.userId(), first).countedAsPlay());
        assertTrue(listeningEvents.recordEvent(user.userId(), first).countedAsPlay());
        var duplicateInterval = new ListeningEventRequest(UUID.randomUUID(), longTrack, "album", null, now.minusSeconds(50), now, 30);
        assertFalse(listeningEvents.recordEvent(user.userId(), duplicateInterval).countedAsPlay());
        assertEquals(21.0, db.queryForObject("SELECT popularity_score FROM tracks WHERE id = ?", Double.class, longTrack), 0.001);
        try {
            listeningEvents.recordEvent(user.userId(), new ListeningEventRequest(UUID.randomUUID(), longTrack, "queue", null, now.plusSeconds(3600), now.plusSeconds(3660), 30));
            org.junit.Assert.fail("Future listen accepted");
        } catch (vn.edu.usth.online_music_player.backend.common.ApiException e) { assertEquals("future_listen", e.code()); }
        try {
            listeningEvents.recordEvent(user.userId(), new ListeningEventRequest(UUID.randomUUID(), longTrack, "queue", null, now.minusSeconds(1), now, 60));
            org.junit.Assert.fail("Impossible duration accepted");
        } catch (vn.edu.usth.online_music_player.backend.common.ApiException e) { assertEquals("invalid_listened_seconds", e.code()); }
    }

    @Test public void rateLimitStillAcceptsAnAlreadyAcknowledgedEvent() {
        var user = registerUser("rate-check");
        var now = java.time.Instant.now();
        var first = new ListeningEventRequest(UUID.randomUUID(), longTrack, "queue", null, now.minusSeconds(60), now, 0);
        listeningEvents.recordEvent(user.userId(), first);
        db.update("""
            INSERT INTO listening_history(client_event_id, user_id, track_id, source_context, started_at, ended_at, listened_seconds, counted_as_play)
            SELECT gen_random_uuid(), ?, ?, 'queue', now() - interval '1 second', now(), 0, false FROM generate_series(1,119)
            """, user.userId(), longTrack);
        assertFalse(listeningEvents.recordEvent(user.userId(), first).countedAsPlay());
        try {
            listeningEvents.recordEvent(user.userId(), new ListeningEventRequest(UUID.randomUUID(), longTrack, "queue", null, now.minusSeconds(60), now, 0));
            org.junit.Assert.fail("Rate limit missing");
        } catch (vn.edu.usth.online_music_player.backend.common.ApiException e) { assertEquals(429, e.status().value()); }
    }


    @Test public void expiredHomeTokenRefreshesAndRestoresPersonalSections() throws Exception {
        var user = registerUser("home-refresh");
        var original = com.nimbusds.jwt.SignedJWT.parse(user.accessToken());
        var claims = new com.nimbusds.jwt.JWTClaimsSet.Builder(original.getJWTClaimsSet())
                .expirationTime(java.util.Date.from(java.time.Instant.now().minusSeconds(60))).build();
        var expired = new com.nimbusds.jwt.SignedJWT(original.getHeader(), claims);
        expired.sign(new com.nimbusds.jose.crypto.MACSigner("test-only-32-byte-secret-never-use-in-production"));
        assertEquals(401, http.perform(get("/home").header("Authorization", "Bearer " + expired.serialize())).andReturn().getResponse().getStatus());
        var refreshed = auth.refresh(user.refreshToken());
        assertEquals(200, http.perform(get("/home").header("Authorization", "Bearer " + refreshed.accessToken())).andReturn().getResponse().getStatus());
        assertEquals(200, http.perform(get("/home")).andReturn().getResponse().getStatus());
    }
    private static final class StubMinioClient extends MinioClient {
        String failure;
        int size = 15;
        boolean closed;
        StubMinioClient() {
            super(MinioClient.builder()
                    .endpoint("http://127.0.0.1:9000")
                    .credentials("test", "test")
                    .build());
        }

        @Override
        public GetObjectResponse getObject(GetObjectArgs args) throws io.minio.errors.ErrorResponseException {
            if ("missing".equals(failure)) throw new io.minio.errors.ErrorResponseException(
                    new io.minio.messages.ErrorResponse("NoSuchKey", "Missing", args.bucket(), args.object(), "resource", "request", "host"), null, null);
            if ("offline".equals(failure)) throw new java.io.UncheckedIOException(new java.io.IOException("Storage offline"));
            byte[] fakeImage = new byte[size];
            return new GetObjectResponse(
                    Headers.of("Content-Length", String.valueOf(fakeImage.length)),
                    args.bucket(),
                    args.region(),
                    args.object(),
                    new ByteArrayInputStream(fakeImage) {
                        @Override public void close() throws java.io.IOException { closed = true; super.close(); }
                    });
        }
    }
}

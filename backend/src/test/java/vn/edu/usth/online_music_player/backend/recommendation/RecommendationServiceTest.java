package vn.edu.usth.online_music_player.backend.recommendation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.Test;
import vn.edu.usth.online_music_player.backend.recommendation.RecommendationService.CandidateTrack;

public class RecommendationServiceTest {
    private static final UUID ARTIST_A = UUID.fromString("10000000-0000-4000-8000-000000000001");
    private static final UUID ARTIST_B = UUID.fromString("10000000-0000-4000-8000-000000000002");
    private static final UUID ARTIST_C = UUID.fromString("10000000-0000-4000-8000-000000000003");
    private static final UUID GENRE_POP = UUID.fromString("20000000-0000-4000-8000-000000000001");
    private static final UUID GENRE_JAZZ = UUID.fromString("20000000-0000-4000-8000-000000000002");

    @Test
    public void coldStartCapsTwoTracksPerArtistAndMixesPopularAndNew() {
        List<CandidateTrack> candidates = new ArrayList<>();
        // Artist A has 4 very popular tracks, but cold start must cap Artist A at 2 tracks.
        for (int i = 1; i <= 4; i++) {
            candidates.add(new CandidateTrack(
                    UUID.randomUUID(),
                    "Popular A" + i,
                    180,
                    ARTIST_A,
                    "Artist A",
                    null,
                    null,
                    null,
                    100.0 - i,
                    Instant.parse("2026-01-0" + i + "T00:00:00Z"),
                    Set.of(GENRE_POP)));
        }
        // Artist B has 2 newer tracks.
        for (int i = 1; i <= 2; i++) {
            candidates.add(new CandidateTrack(
                    UUID.randomUUID(),
                    "New B" + i,
                    200,
                    ARTIST_B,
                    "Artist B",
                    null,
                    null,
                    null,
                    10.0 + i,
                    Instant.parse("2026-10-2" + i + "T00:00:00Z"),
                    Set.of(GENRE_JAZZ)));
        }
        // Artist C has 2 medium tracks.
        for (int i = 1; i <= 2; i++) {
            candidates.add(new CandidateTrack(
                    UUID.randomUUID(),
                    "Track C" + i,
                    190,
                    ARTIST_C,
                    "Artist C",
                    null,
                    null,
                    null,
                    50.0 + i,
                    Instant.parse("2026-06-1" + i + "T00:00:00Z"),
                    Set.of(GENRE_POP)));
        }

        List<RecommendationTrackResponse> results =
                RecommendationService.rankColdStart(candidates, 5);

        assertEquals(5, results.size());
        Map<UUID, Integer> countByArtist = new HashMap<>();
        for (RecommendationTrackResponse item : results) {
            countByArtist.merge(item.artistId(), 1, Integer::sum);
        }
        for (int count : countByArtist.values()) {
            assertTrue("Cold start must allow at most 2 tracks per artist", count <= 2);
        }
        assertTrue(results.stream().anyMatch(r -> "popular".equals(r.reason())));
        assertTrue(results.stream().anyMatch(r -> "new".equals(r.reason())));
    }

    @Test
    public void personalizedRankingWeightsGenre45Artist30Popular15Recency10() {
        CandidateTrack genreMatch = new CandidateTrack(
                UUID.fromString("30000000-0000-4000-8000-000000000001"),
                "Genre Favorite",
                180,
                ARTIST_C,
                "Artist C",
                null,
                null,
                null,
                10.0,
                Instant.parse("2026-01-01T00:00:00Z"),
                Set.of(GENRE_JAZZ));

        CandidateTrack artistMatch = new CandidateTrack(
                UUID.fromString("30000000-0000-4000-8000-000000000002"),
                "Artist Favorite",
                180,
                ARTIST_A,
                "Artist A",
                null,
                null,
                null,
                10.0,
                Instant.parse("2026-01-01T00:00:00Z"),
                Set.of(GENRE_POP));

        CandidateTrack popularAndNewOnly = new CandidateTrack(
                UUID.fromString("30000000-0000-4000-8000-000000000003"),
                "Chart Hit",
                180,
                ARTIST_B,
                "Artist B",
                null,
                null,
                null,
                100.0,
                Instant.parse("2026-10-25T00:00:00Z"),
                Set.of(GENRE_POP));

        List<RecommendationTrackResponse> ranked = RecommendationService.rankPersonalized(
                List.of(popularAndNewOnly, artistMatch, genreMatch),
                Map.of(GENRE_JAZZ, 10L),
                Map.of(ARTIST_A, 10L),
                3);

        assertEquals(3, ranked.size());
        // Genre match gets 0.45 -> highest
        assertEquals(genreMatch.id(), ranked.get(0).id());
        assertEquals("genre", ranked.get(0).reason());
        // Artist match gets 0.30 -> second highest
        assertEquals(artistMatch.id(), ranked.get(1).id());
        assertEquals("artist", ranked.get(1).reason());
        // Popular + New gets 0.15 + 0.10 = 0.25 -> third
        assertEquals(popularAndNewOnly.id(), ranked.get(2).id());
        assertEquals("popular", ranked.get(2).reason());
    }
}

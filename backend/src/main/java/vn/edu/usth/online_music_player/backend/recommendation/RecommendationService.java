package vn.edu.usth.online_music_player.backend.recommendation;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import vn.edu.usth.online_music_player.backend.common.ApiException;

@Service
public class RecommendationService {
    static final int COLD_START_PLAY_THRESHOLD = 5;
    static final int COLD_START_MAX_PER_ARTIST = 2;
    private final JdbcTemplate jdbcTemplate;

    public RecommendationService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<RecommendationTrackResponse> recommend(
            UUID userId, int limit, UUID currentTrackId) {
        if (limit < 1 || limit > 50) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "invalid_limit",
                    "Giới hạn gợi ý phải nằm trong khoảng từ 1 đến 50.");
        }

        Long validPlaysObj = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM listening_history WHERE user_id = ? AND counted_as_play = true",
                Long.class,
                userId);
        long validPlays = validPlaysObj == null ? 0L : validPlaysObj;

        List<UUID> recentTrackIds = jdbcTemplate.query(
                """
                SELECT track_id
                FROM listening_history
                WHERE user_id = ?
                ORDER BY started_at DESC, id DESC
                LIMIT 20
                """,
                (rs, rowNum) -> rs.getObject("track_id", UUID.class),
                userId);

        Set<UUID> excluded = new HashSet<>(recentTrackIds);
        if (currentTrackId != null) {
            excluded.add(currentTrackId);
        }

        List<CandidateTrack> allPublished = jdbcTemplate.query(
                """
                SELECT t.id, t.title, t.duration_seconds,
                       ar.id AS artist_id, ar.name AS artist_name,
                       al.id AS album_id, al.title AS album_title,
                       al.cover_object_key AS album_cover_key,
                       t.popularity_score,
                       COALESCE(t.published_at, t.created_at) AS effective_published_at,
                       COALESCE(
                           (SELECT string_agg(tg.genre_id::text, ',')
                            FROM track_genres tg
                            WHERE tg.track_id = t.id),
                           ''
                       ) AS genre_ids
                FROM tracks t
                JOIN artists ar ON ar.id = t.primary_artist_id
                LEFT JOIN albums al ON al.id = t.album_id
                WHERE t.status = 'published'
                ORDER BY t.popularity_score DESC, t.published_at DESC NULLS LAST, t.id ASC
                """,
                (rs, rowNum) -> {
                    UUID albumId = rs.getObject("album_id", UUID.class);
                    String coverKey = rs.getString("album_cover_key");
                    String coverUrl =
                            albumId != null && coverKey != null && !coverKey.isBlank()
                                    ? "/albums/" + albumId + "/cover"
                                    : null;
                    Timestamp publishedTs = rs.getTimestamp("effective_published_at");
                    Instant publishedAt = publishedTs == null ? Instant.EPOCH : publishedTs.toInstant();
                    String genreCsv = rs.getString("genre_ids");
                    Set<UUID> genres = new HashSet<>();
                    if (genreCsv != null && !genreCsv.isBlank()) {
                        for (String part : genreCsv.split(",")) {
                            if (!part.isBlank()) {
                                genres.add(UUID.fromString(part.trim()));
                            }
                        }
                    }
                    return new CandidateTrack(
                            rs.getObject("id", UUID.class),
                            rs.getString("title"),
                            rs.getInt("duration_seconds"),
                            rs.getObject("artist_id", UUID.class),
                            rs.getString("artist_name"),
                            albumId,
                            rs.getString("album_title"),
                            coverUrl,
                            rs.getDouble("popularity_score"),
                            publishedAt,
                            genres);
                });

        List<CandidateTrack> candidates = new ArrayList<>();
        for (CandidateTrack track : allPublished) {
            if (!excluded.contains(track.id())) {
                candidates.add(track);
            }
        }

        if (validPlays < COLD_START_PLAY_THRESHOLD) {
            return rankColdStart(candidates, limit);
        }

        Map<UUID, Long> genreCounts = new HashMap<>();
        jdbcTemplate.query(
                """
                SELECT tg.genre_id, count(*) AS weight
                FROM (
                    SELECT track_id FROM listening_history WHERE user_id = ? AND counted_as_play = true
                    UNION ALL
                    SELECT track_id FROM library_tracks WHERE user_id = ?
                ) interactions
                JOIN track_genres tg ON tg.track_id = interactions.track_id
                GROUP BY tg.genre_id
                """,
                rs -> {
                    genreCounts.put(rs.getObject("genre_id", UUID.class), rs.getLong("weight"));
                },
                userId,
                userId);

        Map<UUID, Long> artistCounts = new HashMap<>();
        jdbcTemplate.query(
                """
                SELECT t.primary_artist_id AS artist_id, count(*) AS weight
                FROM (
                    SELECT track_id FROM listening_history WHERE user_id = ? AND counted_as_play = true
                    UNION ALL
                    SELECT track_id FROM library_tracks WHERE user_id = ?
                ) interactions
                JOIN tracks t ON t.id = interactions.track_id
                GROUP BY t.primary_artist_id
                """,
                rs -> {
                    artistCounts.put(rs.getObject("artist_id", UUID.class), rs.getLong("weight"));
                },
                userId,
                userId);

        return rankPersonalized(candidates, genreCounts, artistCounts, limit);
    }

    static List<RecommendationTrackResponse> rankColdStart(
            List<CandidateTrack> candidates, int limit) {
        if (candidates.isEmpty() || limit <= 0) {
            return List.of();
        }

        int popularTarget = (int) Math.round(limit * 0.60);
        int newTarget = Math.max(0, limit - popularTarget);

        List<CandidateTrack> byPopular = new ArrayList<>(candidates);
        byPopular.sort(
                Comparator.comparingDouble(CandidateTrack::popularityScore)
                        .reversed()
                        .thenComparing(CandidateTrack::publishedAt, Comparator.reverseOrder())
                        .thenComparing(CandidateTrack::title, Comparator.nullsLast(String::compareTo)));

        List<CandidateTrack> byNew = new ArrayList<>(candidates);
        byNew.sort(
                Comparator.comparing(CandidateTrack::publishedAt, Comparator.reverseOrder())
                        .thenComparing(
                                Comparator.comparingDouble(CandidateTrack::popularityScore).reversed())
                        .thenComparing(CandidateTrack::title, Comparator.nullsLast(String::compareTo)));

        List<RecommendationTrackResponse> results = new ArrayList<>();
        Set<UUID> chosenIds = new HashSet<>();
        Map<UUID, Integer> artistCounts = new HashMap<>();

        int popularAdded = 0;
        for (CandidateTrack track : byPopular) {
            if (popularAdded >= popularTarget || results.size() >= limit) {
                break;
            }
            int artistCount = artistCounts.getOrDefault(track.artistId(), 0);
            if (artistCount >= COLD_START_MAX_PER_ARTIST) {
                continue;
            }
            results.add(toResponse(track, "popular"));
            chosenIds.add(track.id());
            artistCounts.put(track.artistId(), artistCount + 1);
            popularAdded++;
        }

        int newAdded = 0;
        for (CandidateTrack track : byNew) {
            if (newAdded >= newTarget || results.size() >= limit) {
                break;
            }
            if (chosenIds.contains(track.id())) {
                continue;
            }
            int artistCount = artistCounts.getOrDefault(track.artistId(), 0);
            if (artistCount >= COLD_START_MAX_PER_ARTIST) {
                continue;
            }
            results.add(toResponse(track, "new"));
            chosenIds.add(track.id());
            artistCounts.put(track.artistId(), artistCount + 1);
            newAdded++;
        }

        for (CandidateTrack track : byPopular) {
            if (results.size() >= limit) {
                break;
            }
            if (chosenIds.contains(track.id())) {
                continue;
            }
            int artistCount = artistCounts.getOrDefault(track.artistId(), 0);
            if (artistCount >= COLD_START_MAX_PER_ARTIST) {
                continue;
            }
            results.add(toResponse(track, "popular"));
            chosenIds.add(track.id());
            artistCounts.put(track.artistId(), artistCount + 1);
        }

        return results;
    }

    static List<RecommendationTrackResponse> rankPersonalized(
            List<CandidateTrack> candidates,
            Map<UUID, Long> genreWeights,
            Map<UUID, Long> artistWeights,
            int limit) {
        if (candidates.isEmpty() || limit <= 0) {
            return List.of();
        }

        long maxGenreWeight =
                genreWeights.values().stream().mapToLong(Long::longValue).max().orElse(0L);
        long maxArtistWeight =
                artistWeights.values().stream().mapToLong(Long::longValue).max().orElse(0L);

        double minPop =
                candidates.stream().mapToDouble(CandidateTrack::popularityScore).min().orElse(0.0);
        double maxPop =
                candidates.stream().mapToDouble(CandidateTrack::popularityScore).max().orElse(0.0);

        long minEpochMillis =
                candidates.stream()
                        .mapToLong(c -> c.publishedAt().toEpochMilli())
                        .min()
                        .orElse(0L);
        long maxEpochMillis =
                candidates.stream()
                        .mapToLong(c -> c.publishedAt().toEpochMilli())
                        .max()
                        .orElse(0L);

        record ScoredCandidate(CandidateTrack track, double score, String reason) {}

        List<ScoredCandidate> scored = new ArrayList<>(candidates.size());
        for (CandidateTrack track : candidates) {
            double genreScore = 0.0;
            if (maxGenreWeight > 0 && !track.genreIds().isEmpty()) {
                for (UUID genreId : track.genreIds()) {
                    long w = genreWeights.getOrDefault(genreId, 0L);
                    genreScore = Math.max(genreScore, (double) w / maxGenreWeight);
                }
            }

            double artistScore =
                    maxArtistWeight > 0
                            ? (double) artistWeights.getOrDefault(track.artistId(), 0L)
                                    / maxArtistWeight
                            : 0.0;

            double popScore;
            if (maxPop > minPop) {
                popScore = (track.popularityScore() - minPop) / (maxPop - minPop);
            } else {
                popScore = maxPop > 0 ? 1.0 : 0.0;
            }

            double recencyScore;
            if (maxEpochMillis > minEpochMillis) {
                recencyScore =
                        (double) (track.publishedAt().toEpochMilli() - minEpochMillis)
                                / (maxEpochMillis - minEpochMillis);
            } else {
                recencyScore = 1.0;
            }

            double weightedGenre = 0.45 * genreScore;
            double weightedArtist = 0.30 * artistScore;
            double weightedPop = 0.15 * popScore;
            double weightedRecency = 0.10 * recencyScore;

            double totalScore =
                    weightedGenre + weightedArtist + weightedPop + weightedRecency;

            String reason = "popular";
            double bestComponent = weightedPop;
            if (weightedRecency > bestComponent) {
                bestComponent = weightedRecency;
                reason = "new";
            }
            if (weightedArtist >= bestComponent && weightedArtist > 0) {
                bestComponent = weightedArtist;
                reason = "artist";
            }
            if (weightedGenre >= bestComponent && weightedGenre > 0) {
                reason = "genre";
            }

            scored.add(new ScoredCandidate(track, totalScore, reason));
        }

        scored.sort(
                Comparator.comparingDouble(ScoredCandidate::score)
                        .reversed()
                        .thenComparing(
                                s -> s.track().popularityScore(), Comparator.reverseOrder())
                        .thenComparing(
                                s -> s.track().publishedAt(), Comparator.reverseOrder())
                        .thenComparing(s -> s.track().id()));

        List<RecommendationTrackResponse> results = new ArrayList<>();
        for (int i = 0; i < Math.min(limit, scored.size()); i++) {
            ScoredCandidate item = scored.get(i);
            results.add(toResponse(item.track(), item.reason()));
        }
        return results;
    }

    private static RecommendationTrackResponse toResponse(CandidateTrack track, String reason) {
        return new RecommendationTrackResponse(
                track.id(),
                track.title(),
                track.durationSeconds(),
                track.artistId(),
                track.artistName(),
                track.albumId(),
                track.albumTitle(),
                track.coverUrl(),
                reason);
    }

    record CandidateTrack(
            UUID id,
            String title,
            int durationSeconds,
            UUID artistId,
            String artistName,
            UUID albumId,
            String albumTitle,
            String coverUrl,
            double popularityScore,
            Instant publishedAt,
            Set<UUID> genreIds) {}
}

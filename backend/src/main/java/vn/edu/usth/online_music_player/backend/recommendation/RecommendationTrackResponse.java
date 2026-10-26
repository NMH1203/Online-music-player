package vn.edu.usth.online_music_player.backend.recommendation;

import java.util.UUID;

public record RecommendationTrackResponse(
        UUID id,
        String title,
        int durationSeconds,
        UUID artistId,
        String artistName,
        UUID albumId,
        String albumTitle,
        String coverUrl,
        String reason) {}

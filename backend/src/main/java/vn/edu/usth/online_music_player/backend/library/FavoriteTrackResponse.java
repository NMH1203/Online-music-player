package vn.edu.usth.online_music_player.backend.library;

import java.time.Instant;
import java.util.UUID;

public record FavoriteTrackResponse(
        UUID id,
        String title,
        int durationSeconds,
        UUID artistId,
        String artistName,
        UUID albumId,
        String albumTitle,
        Instant addedAt) {}

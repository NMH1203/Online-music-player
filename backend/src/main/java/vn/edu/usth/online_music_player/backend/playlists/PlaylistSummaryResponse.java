package vn.edu.usth.online_music_player.backend.playlists;

import java.time.Instant;
import java.util.UUID;

public record PlaylistSummaryResponse(
        UUID id,
        String name,
        String description,
        long trackCount,
        Instant createdAt,
        Instant updatedAt) {}

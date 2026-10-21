package vn.edu.usth.online_music_player.backend.playlists;

import java.time.Instant;
import java.util.UUID;

public record PlaylistTrackResponse(
        UUID id,
        String title,
        int durationSeconds,
        String artistName,
        int position,
        Instant addedAt) {}

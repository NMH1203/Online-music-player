package vn.edu.usth.online_music_player.backend.playlists;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PlaylistDetailResponse(
        UUID id,
        String name,
        String description,
        List<PlaylistTrackResponse> tracks,
        Instant createdAt,
        Instant updatedAt) {}

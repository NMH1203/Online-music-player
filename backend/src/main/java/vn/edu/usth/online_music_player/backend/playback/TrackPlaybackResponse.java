package vn.edu.usth.online_music_player.backend.playback;

import java.util.UUID;

public record TrackPlaybackResponse(
        UUID trackId,
        String title,
        String artistName,
        int durationSeconds,
        String mimeType,
        long fileSizeBytes,
        String streamUrl,
        String attributionText) {}

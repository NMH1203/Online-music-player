package vn.edu.usth.online_music_player.backend.playback;

import java.util.UUID;

/** Internal track and object metadata; storage identifiers are never serialized to the app. */
record TrackPlayback(
        UUID id,
        String title,
        String artistName,
        int durationSeconds,
        String mimeType,
        long fileSizeBytes,
        String bucketName,
        String objectKey,
        String attributionText) {}
